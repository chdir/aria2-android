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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.os.Process;
import android.support.annotation.Nullable;
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

    static final String EXTRA_NOTIFICATION = "net.sf.aria2.extra.NF";

    static final String ACTION_TOAST = "net.sf.aria2.action.TOAST";
    static final String EXTRA_TEXT = "net.sf.aria2.extra.TEXT";

    static final String ACTION_NF_STOPPED = "net.sf.aria2.action.NOTIFY";
    static final String EXTRA_EXIT_CODE = "net.sf.aria2.extra.EC";
    static final String EXTRA_DID_WORK = "net.sf.aria2.extra.WORKED";
    static final String EXTRA_KILLED_FORCEFULLY = "net.sf.aria2.extra.KILL";

    private Notification persistentNf;
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

        persistentNf = intent.getParcelableExtra(EXTRA_NOTIFICATION);

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

    private void reportNoNetwork() {
        // no binding check, because onStartCommand is called first
        final Intent toastIntent = new Intent(ACTION_TOAST)
                .setClassName(getPackageName(), "net.sf.aria2.PrivateReceiver")
                .putExtra(EXTRA_TEXT, getText(R.string.will_start_later));

        sendBroadcast(toastIntent);
    }

    private void updateNf() {
        if (bindingCounter == 0) {
            if (isRunning()) {
                startForeground(-1, persistentNf);

                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

                nm.cancel(R.id.nf_status);
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
                final TermExec pBuilder = new TermExec(properties.toCommand());

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
                    final Intent nfIntent = new Intent(ACTION_NF_STOPPED)
                            .setClassName(getPackageName(), "net.sf.aria2.PrivateReceiver")
                            .putExtra(EXTRA_EXIT_CODE, resultCode)
                            .putExtra(EXTRA_DID_WORK, didSomeWork())
                            .putExtra(EXTRA_KILLED_FORCEFULLY, killedForcefully);
                    sendBroadcast(nfIntent);
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
            try (FileChannel fc = new ParcelFileDescriptor.AutoCloseInputStream(ptmx).getChannel()) {
                final ByteBuffer lastLines = ByteBuffer.allocate(2048).order(ByteOrder.nativeOrder());

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
                    String errHeader = null;

                    try  {
                        int slurped;
                        do {
                            slurped = fc.read(lastLines);

                            if (lastLines.position() == lastLines.limit() || slurped == -1) {
                                if (errHeader == null) {
                                    errHeader = new String(lastLines.array(), lastLines.arrayOffset(), lastLines.position());
                                }

                                Log.v(Config.TAG, new String(lastLines.array(), 0, lastLines.position()));
                            }

                            if (lastLines.position() == lastLines.limit()) {
                                lastLines.clear();
                            }
                        }
                        while (slurped != -1);
                    } finally {
                        if (errHeader == null) {
                            errHeader = new String(lastLines.array(), 0, lastLines.position());
                        }

                        if (showMumblings || (System.currentTimeMillis() - startupTime < 400)) {
                            // https://stackoverflow.com/questions/21165802
                            final String trimmedHeader = errHeader.replaceAll("(?m)(^ *| +(?= |$))", "")
                                    .replaceAll("(?m)^$([\r\n]+?)(^$[\r\n]+?^)+", "$1").trim();

                            if (!TextUtils.isEmpty(trimmedHeader)) {
                                int linebreak = 0;
                                do {
                                    linebreak = trimmedHeader.indexOf('\n', linebreak + 1);

                                    if (linebreak == -1) break;
                                } while (linebreak < 10);

                                int startCutoff = Math.min(linebreak == -1 ? trimmedHeader.length() : linebreak, 200);

                                int endCutoff = trimmedHeader.lastIndexOf('\n');

                                String finalText = trimmedHeader.substring(0, startCutoff);

                                if (endCutoff != -1 && endCutoff > startCutoff && trimmedHeader.length() - endCutoff > 10) {
                                    endCutoff = Math.max(trimmedHeader.length()- 200, endCutoff);

                                    finalText = finalText + '…' + trimmedHeader.substring(endCutoff);
                                }

                                final Intent finalIntent = new Intent(Aria2Service.ACTION_TOAST)
                                        .setClassName(getPackageName(), "net.sf.aria2.PrivateReceiver")
                                        .putExtra(Aria2Service.EXTRA_TEXT, finalText);

                                sendBroadcast(finalIntent);
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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