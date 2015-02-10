/*
 * aria2 - The high speed download utility (Android port)
 *
 * Copyright © 2015 Alexander Rvachev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * In addition, as a special exception, the copyright holders give
 * permission to link the code of portions of this program with the
 * OpenSSL library under certain conditions as described in each
 * individual source file, and distribute linked combinations
 * including the two.
 * You must obey the GNU General Public License in all respects
 * for all of the code used other than OpenSSL.  If you modify
 * file(s) with this exception, you may extend this exception to your
 * version of the file(s), but you are not obligated to do so.  If you
 * do not wish to do so, delete this exception statement from your
 * version.  If you delete this exception statement from all source
 * files in the program, then also delete it here.
 */
package net.sf.aria2;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.preference.PreferenceActivity;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.Toast;
import net.sf.aria2.util.SimpleResultReceiver;

import java.io.IOException;
import java.lang.Process;
import java.util.*;

public final class Aria2Service extends Service {
    private Binder link;
    private Handler bgThreadHandler;
    private HandlerThread reusableThread;

    private int bindingCounter;
    private ResultReceiver backLink;

    private BroadcastReceiver receiver;

    private AriaRunnable lastInvocation;

    @Override
    public void onCreate() {
        super.onCreate();

        link = new Binder();

        reusableThread = new HandlerThread("Aria2 handler thread");
        reusableThread.start();

        bgThreadHandler = new Handler(reusableThread.getLooper());
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (isRunning())
            throw new IllegalStateException("Can not start aria2: running instance already exists!");

        if (intent == null) {
            Log.e("ALERT", "Deaf for good");
            // TODO handle being restarted correctly
            return START_NOT_STICKY;
        }

        unregisterOldReceiver();

        final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        final NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni != null && ni.isConnectedOrConnecting())
            startAria2(Config.from(intent));
        else {
            if (intent.hasExtra(Config.EXTRA_INTERACTIVE))
                Toast.makeText(getApplicationContext(),
                        getText(R.string.will_start_later), Toast.LENGTH_LONG).show();
            else {
                receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent.hasExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY) &&
                                intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false))
                            return;

                        startAria2(Config.from(intent));

                        unregisterReceiver(this);
                        receiver = null;
                    }
                };

                registerReceiver(receiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void unregisterOldReceiver() {
        if (receiver != null)
            try {
                unregisterReceiver(receiver);
            } catch (Throwable t) {
                // fuck you, Dianne and your bunch
            }
    }

    private void startAria2(Config config) {
        lastInvocation = new AriaRunnable(config);
        bgThreadHandler.post(lastInvocation);
    }

    @Override
    public void onDestroy() {
        unregisterOldReceiver();

        if (isRunning()) {
            // order the child process to quit
            lastInvocation.stop();
        }
        // not using quitSafely, because it would cause the process to hang
        reusableThread.quit();

        updateNf();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        bindingCounter++;

        updateNf();

        return link;
    }

    @Override
    public void onRebind(Intent intent) {
        bindingCounter++;

        updateNf();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        bindingCounter--;

        updateNf();

        return true;
    }

    private boolean isRunning() {
        return lastInvocation != null && lastInvocation.isRunning();
    }

    private void sendResult(boolean state) {
        if (backLink == null)
            return;

        Bundle b = new Bundle();
        b.putSerializable(SimpleResultReceiver.OBJ, state);
        backLink.send(0, b);
    }

    private Notification createNf() {
        @SuppressLint("InlinedApi")
        final Intent resultIntent = new Intent(getApplicationContext(), MainActivity.class)
                .putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, "net.sf.aria2.MainActivity$Aria2Preferences")
                .putExtra(Config.EXTRA_FROM_NF, true);

        // note: using addParentStack results in hanging for some reason (confirmed on JellyBean)
        // there is only one activity in stack to handle up and back navigation differently
        final TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext())
                .addNextIntent(resultIntent);
        final PendingIntent contentIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_nf_icon)
                .setTicker("Aria2 is running")
                .setContentTitle("Aria2 is running")
                .setContentText("Touch to open settings")
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNf() {
        if (bindingCounter == 0) {
            if (isRunning())
                startForeground(-1, createNf());
        } else stopForeground(true);
    }

    private final class Binder extends IAria2.Stub {
        @Override
        public void askToStop() {
            lastInvocation.stop();
        }

        @Override
        public void setResultReceiver(ResultReceiver backLink) {
            Aria2Service.this.backLink = backLink;
        }

        @Override
        public boolean isRunning() throws RemoteException {
            return Aria2Service.this.isRunning();
        }
    }

    private final class AriaRunnable implements Runnable {
        private final Config properties;

        private volatile Process proc;

        public AriaRunnable(Config properties) {
            this.properties = properties;
        }

        public void run() {
            try {
                final ProcessBuilder pBuilder = new ProcessBuilder()
                        .redirectErrorStream(true)
                        .command(properties);

                pBuilder.environment().put("HOME", getFilesDir().getAbsolutePath());

                pBuilder.command().add("--stop-with-process=" + android.os.Process.myPid());

                proc = pBuilder.start();

                try (Scanner iStream = new Scanner(proc.getInputStream()).useDelimiter("\\A")) {
                    sendResult(true);

                    final String errText;
                    if (iStream.hasNext() && !(errText = iStream.next()).isEmpty())
                        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(),
                                errText, Toast.LENGTH_LONG).show());
                }
            }
            catch (IOException tooBad) {
                Log.e(Config.TAG, tooBad.getLocalizedMessage());
            }
            finally {
                try {
                    int r = proc.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    proc = null;

                    stopSelf();

                    sendResult(false);

                    lastInvocation = null;
                }
            }
        }

        boolean isRunning() {
            return proc != null;
        }

        public void stop() {
            proc.destroy();
        }
    }
}
