package net.sf.aria2;

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
import android.support.v4.app.NotificationCompat;
import android.support.v4.net.ConnectivityManagerCompat;
import android.widget.Toast;
import net.sf.aria2.util.SimpleResultReceiver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Process;
import java.nio.CharBuffer;
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning())
            throw new IllegalStateException("Can not start aria2: running instance already exists!");

        unregisterOldReceiver();

        final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        final NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni.isConnectedOrConnecting())
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

    private void updateNf() {
        if (isRunning()) {
            if (bindingCounter == 0)
                startForeground(-1, createNf());
        } else
            stopForeground(true);
    }

    private Notification createNf() {
        final PendingIntent contentIntent = PendingIntent.getActivity(
                getApplicationContext(), 0,
                new Intent(getApplicationContext(), MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_nf_icon)
                .setTicker("Aria2 is running")
                .setContentTitle("Aria2 is running")
                .setContentText("Touch to open settings")
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .build();
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
            updateNf();

            try {
                final ProcessBuilder pBuilder = new ProcessBuilder()
                        .redirectErrorStream(true)
                        .command(properties);

                List<String> args = pBuilder.command();

                args.add("--stop-with-process=" + android.os.Process.myPid());

                proc = pBuilder.start();

                try (Scanner iStream = new Scanner(proc.getInputStream()).useDelimiter("\\A")) {
                    updateNf();

                    sendResult(true);

                    final String errText;
                    if (iStream.hasNext() && !(errText = iStream.next()).isEmpty())
                        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(),
                                errText, Toast.LENGTH_LONG).show());
                }
            }
            catch (IOException ignore) {}
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
