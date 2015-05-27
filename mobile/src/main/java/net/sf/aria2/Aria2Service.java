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
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceActivity;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import jackpal.androidterm.TermExec;
import jackpal.androidterm.libtermexec.v1.ITerminal;
import net.sf.aria2.util.SimpleResultReceiver;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.*;

public final class Aria2Service extends Service {
    private static final String TAG = "aria2service";

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

        reusableThread = new HandlerThread("aria2 handler thread");
        reusableThread.start();

        bgThreadHandler = new Handler(reusableThread.getLooper());
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (isRunning())
            throw new IllegalStateException("Can not start aria2: running instance already exists!");

        if (intent == null) {
            stopSelf();

            return START_NOT_STICKY;
        }

        unregisterOldReceiver();

        final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        final NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni != null && ni.isConnectedOrConnecting())
            startAria2(Config.from(intent));
        else {
            if (intent.hasExtra(Config.EXTRA_INTERACTIVE))
                reportNoNetwork();
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
                .setTicker("aria2 is running")
                .setContentTitle("aria2 is running")
                .setContentText("Touch to open settings")
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .build();
    }

    private Notification createStoppedNf(int code, boolean someTimeElapsed) {
        final ExitCode ec = ExitCode.from(code);

        final String title = someTimeElapsed
                             ? getString(R.string.aria2_has_stopped)
                             : getString(R.string.aria2_has_failed_to_start);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_nf_icon)
                .setTicker(title)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false);

        final String errText = ec.getDesc(getResources());

        if (ec.isSuccess() && someTimeElapsed) {
            builder.setContentText(errText);
        } else {
            if (someTimeElapsed) {
                builder.setContentText(getString(R.string.there_may_have_been_issues));

                if (lastInvocation.killedForcefully)
                    builder.setNumber(ec.getCode());
                else {
                    builder.setContentInfo('#' + ec.name())
                            .setSubText(getString(R.string.expand_nf_to_see_details))
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(
                                    getString(R.string.explanation, errText)));
                }
            } else if (!ec.isSuccess()) {
                builder.setContentInfo('#' + ec.name())
                        .setContentText(Character.toUpperCase(errText.charAt(0))
                                + errText.substring(1));
            }
        }

        return builder.build();
    }

    private void reportNoNetwork() {
        // no binding check, because onStartCommand is called first
        // TODO use
        Toast.makeText(getApplicationContext(),
                getText(R.string.will_start_later), Toast.LENGTH_LONG).show();
    }

    private void updateNf() {
        if (bindingCounter == 0) {
            if (isRunning()) {
                startForeground(-1, createNf());

                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

                nm.cancel(R.id.nf_stopped);
            }
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
        private final boolean delegateDisplay;

        // accessed from the bg thread only
        private long startupTime;

        // accessed from the main thread only
        private boolean killedForcefully;
        private boolean warnedOnce;

        // accessed from both
        private volatile int pid;

        public AriaRunnable(Config properties) {
            this.properties = properties;

            delegateDisplay = properties.useATE;
        }

        public void run() {
            startupTime = System.currentTimeMillis();

            final File aria2dir = getFilesDir();
            final File ptmxFile = new File("/dev/ptmx");

            try (ParcelFileDescriptor ptmx = ParcelFileDescriptor.open(ptmxFile, ParcelFileDescriptor.MODE_READ_WRITE)) {
                final TermExec pBuilder = new TermExec(properties);

                pBuilder.environment().put("HOME", aria2dir.getAbsolutePath());

                pBuilder.command().add("--stop-with-process=" + android.os.Process.myPid());

                Log.i(TAG, Arrays.toString(pBuilder.command().toArray()));

                pid = pBuilder.start(ptmx);

                if (pid <= 1)
                    return;

                sendResult(true);

                final Thread slurper = new Thread(new ProcessOutputHandler(getApplicationContext(), ptmx,
                        delegateDisplay, properties.showOutput));

                slurper.start();

                final int resultCode = TermExec.waitFor(pid);

                slurper.interrupt();

                sendResult(false);

                if (properties.showStoppedNf) {
                    final Notification n = createStoppedNf(resultCode, didSomeWork());

                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(R.id.nf_stopped, n);
                }
            }
            catch (IOException tooBad) {
                Log.e(Config.TAG, tooBad.getLocalizedMessage());
            } finally {
                pid = -1;

                stopSelf();

                lastInvocation = null;
            }
        }

        private boolean didSomeWork() {
            return System.currentTimeMillis() - startupTime > 500;
        }

        boolean isRunning() {
            return pid > 1;
        }

        void stop() {
            if (pid > 1) {
                if (!warnedOnce) {
                    warnedOnce = true;

                    Process.sendSignal(pid, 2); // SIGINT
                } else {
                    killedForcefully = true;

                    pid = -1;

                    Process.sendSignal(pid, Process.SIGNAL_KILL);
                }
            }
        }
    }
}

class ProcessOutputHandler extends ContextWrapper implements Runnable {
    private final boolean delegateDisplay;
    private final boolean showMumblings;
    private final ParcelFileDescriptor ptmx;

    ProcessOutputHandler(Context ctx, ParcelFileDescriptor ptmx,
                         boolean delegateDisplay, boolean showMumblings) {
        super(ctx);

        this.delegateDisplay = delegateDisplay;
        this.showMumblings = showMumblings;
        this.ptmx = ptmx;
    }

    @Override
    public void run() {
        long startupTime = System.currentTimeMillis();

        try {
            try {
                try {
                    final TermConnection conn;

                    synchronized (ptmx) {
                        if (delegateDisplay && (conn = rebind()) != null) {
                            try (Closeable c = () -> unbindService(conn)) {
                                ptmx.wait(); // wait until the service disconnects or aria2 process dies
                            }
                        }
                    }
                } finally {
                    final ByteBuffer lastLines = ByteBuffer.allocate(1024).order(ByteOrder.nativeOrder());
                    try (FileChannel fc = new ParcelFileDescriptor.AutoCloseInputStream(ptmx).getChannel()) {
                        int slurped;
                        do {
                            slurped = fc.read(lastLines);

                            if (lastLines.position() == lastLines.limit() || slurped == -1)
                                Log.v(Config.TAG, new String(lastLines.array(), 0, lastLines.position()));

                            if (lastLines.position() == lastLines.limit())
                                lastLines.clear();
                        }
                        while (slurped != -1);
                    } finally {
                        final String errText = new String(lastLines.array(), 0, lastLines.position());

                        Log.d(Config.TAG, errText);

                        if (showMumblings || (System.currentTimeMillis() - startupTime < 400)) {
                            // https://stackoverflow.com/questions/21165802
                            final String trimmedText = errText.replaceAll("(?m)(^ *| +(?= |$))", "")
                                    .replaceAll("(?m)^$([\r\n]+?)(^$[\r\n]+?^)+", "$1");

                            if (!TextUtils.isEmpty(trimmedText)) {
                                final Handler niceUiThreadHandler = new Handler(Looper.getMainLooper());

                                final String finalText = trimmedText.length() > 300
                                        ? '…' + trimmedText.substring(trimmedText.length() - 299, trimmedText.length())
                                        : trimmedText;

                                niceUiThreadHandler.post(() -> Toast.makeText(getApplicationContext(), finalText, Toast.LENGTH_LONG).show());
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ptmx.close();
            }
        } catch (IOException ignore) {}
    }

    private TermConnection rebind() {
        final PackageManager pm = getPackageManager();

        final Intent i = new Intent()
                .setAction(TermExec.SERVICE_ACTION_V1);

        final ResolveInfo ri = pm.resolveService(i, 0);

        if (ri == null || ri.serviceInfo == null)
            return null;

        final ComponentName component = new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);

        i.setComponent(component);

        final TermConnection connection = new TermConnection(ptmx);

        // BIND_AUTO_CREATE ensures, that target Service won't die, when we unbind
        // (see also https://stackoverflow.com/q/10676204)
        //
        // Context.BIND_WAIVE_PRIORITY may be used to prevent unnecessary priority gains
        // (see also https://stackoverflow.com/q/6645193)
        boolean bindingInitiated = bindService(i, connection, Context.BIND_AUTO_CREATE);

        if (bindingInitiated) {
            try {
                ptmx.wait(5000); // wait until the connection have been successfully made

                return connection;
            } catch (InterruptedException e) {
                return null;
            }
        }
        else return null;
    }

    private static class TermConnection implements ServiceConnection {
        private final ParcelFileDescriptor ptmx;

        private boolean lastConnectionMade;
        private boolean closed;

        private TermConnection(ParcelFileDescriptor ptmx) {
            this.ptmx = ptmx;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                ITerminal it = ITerminal.Stub.asInterface(service);

                it.startSession(ptmx, new ResultReceiver(new Handler(Looper.getMainLooper())) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        release();
                    }
                });

                if (!lastConnectionMade) {
                    synchronized (ptmx) {
                        ptmx.notify(); // indicate, that first connection was successful
                    }
                }

                lastConnectionMade = true;
            } catch (Exception e) {
                release();
            }
        }

        // would likely happen when Android decides to kill spawned Terminal Service
        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (lastConnectionMade)
                lastConnectionMade = false;
            else
                release();
        }

        private void release() {
            if (closed)
                return;

            synchronized (ptmx) {
                ptmx.notify();
            }

            closed = true;
        }
    }
}