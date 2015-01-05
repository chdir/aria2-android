package net.sf.aria2;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.support.v4.app.NotificationCompat;
import net.sf.aria2.util.SimpleResultReceiver;

import java.io.IOException;
import java.io.InputStream;
import java.lang.Process;
import java.util.*;

public final class Aria2Service extends Service {
    private Binder link;
    private Handler bgThreadHandler;
    private HandlerThread reusableThread;

    private int bindingCounter;

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
        if (lastInvocation.isRunning())
            throw new IllegalStateException("Can not start aria2: running instance already exists!");

        lastInvocation = new AriaRunnable(Config.from(intent), SimpleResultReceiver.from(intent));
        bgThreadHandler.post(lastInvocation);

        updateNf();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
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
                .setSmallIcon(R.drawable.ic_launcher)
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

    private final class Binder extends IAria2.Stub {
        @Override
        public void askToStop() {
            lastInvocation.stop();
        }

        @Override
        public boolean isRunning() throws RemoteException {
            return Aria2Service.this.isRunning();
        }
    }

    private final class AriaRunnable implements Runnable {
        private final Config properties;
        private final SimpleResultReceiver<Boolean> backLink;

        private volatile Process proc;

        public AriaRunnable(Config properties, SimpleResultReceiver<Boolean> backLink) {
            this.properties = properties;
            this.backLink = backLink;
        }

        public void run() {
            try (InputStream iStream = proc.getInputStream()) {
                final ProcessBuilder pBuilder  = new ProcessBuilder()
                        .redirectErrorStream(true)
                        .command(properties);

                List<String> args = pBuilder.command();

                args.add("--stop-with-process=" + android.os.Process.myPid());

                proc = pBuilder.start();

                backLink.send(true);

                if (iStream.read() != -1)
                    throw new IllegalStateException("Aria2 isn't supposed to print anything in daemon mode..");
            }
            catch (IOException ignore) {}
            finally {
                try {
                    proc.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    proc = null;
                    backLink.send(false);
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
