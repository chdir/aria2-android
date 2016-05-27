package net.sf.aria2.loader;

import android.annotation.TargetApi;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import net.sf.aria2.BuildConfig;
import net.sf.aria2.R;
import net.sf.aria2.util.WebUiExtractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class FrontendSetupLoader extends AsyncTaskLoader<Bundle> {
    private static final String TAG = "AssetExtraction";

    private final AtomicReference<Thread> lastLoadingThread = new AtomicReference<>();

    private final String prefName;

    private final String errInfoKey;

    private Bundle results;

    public FrontendSetupLoader(Context context) {
        super(context.getApplicationContext());

        prefName = context.getString(R.string.download_dir_pref);

        errInfoKey = context.getString(R.string.error_info);
    }

    @Override
    protected void onStartLoading() {
        if (results != null)
            deliverResult(results);

        if (results == null || takeContentChanged())
            forceLoad();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    public void deliverResult(Bundle data) {
        if (isReset())
            return;

        Bundle oldData = results;
        results = data;

        if (isStarted())
            super.deliverResult(data);
    }

    @Override
    protected boolean onCancelLoad() {
        if (super.onCancelLoad()) {
            final Thread lastThread = lastLoadingThread.getAndSet(null);

            if (lastThread != null) {
                lastThread.interrupt();
            }

            return true;
        }

        return false;
    }

    @Override
    protected Bundle onLoadInBackground() {
        final Thread theThread = Thread.currentThread();

        final Thread oldThread = lastLoadingThread.getAndSet(theThread);

        if (oldThread != null) {
            oldThread.interrupt();
        }

        try {
            return super.onLoadInBackground();
        } finally {
            theThread.interrupt();

            lastLoadingThread.set(null);
        }
    }

    @Override
    public Bundle loadInBackground() {
        final Context ctx = getContext();

        if (ctx == null)
            return null;

        final File dir;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        final String dDir = prefs.getString(prefName, "");

        if (TextUtils.isEmpty(dDir)) {
            final Bundle result = new Bundle(1);
            result.putString(errInfoKey, "Unable to extract WebUi: set aria2 directory first!");
            return result;
        }

        dir = new File(dDir);

        final File uiDir = new File(dir, WebUiExtractor.DIR_NAME);

        if ((!uiDir.mkdirs() && !uiDir.exists() || !uiDir.isDirectory()) || !uiDir.canWrite()) {
            final Bundle result = new Bundle(1);
            result.putString(errInfoKey, "Unable to extract WebUi: can not create 'webui' inside aria2 directory");
            return result;
        }

        final File verFile = new File(uiDir, WebUiExtractor.VER_FILE);

        String version = null;
        if (verFile.length() > 0 && verFile.length() < 1024) {
            try {
                try (Scanner scanner = new Scanner(verFile).useDelimiter("\\Z")) {
                    if (scanner.hasNext()) {
                        version = scanner.next();
                    }
                }
            } catch (IOException ignored) {
                Log.w(TAG, "Failed to read 'version' file");
            }
        }

        if (version != null) {
            final int versionCode;
            try {
                versionCode = Integer.parseInt(version);

                if (versionCode >= BuildConfig.webuiVer) {
                    return null;
                }
            } catch (NumberFormatException nfe) {
                Log.w(TAG, "Failed to parse 'version' file");
            }
        }

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, "Frontend files are being extracted, this may take a while!",Toast.LENGTH_SHORT).show();
            }
        });

        try {
            new WebUiExtractor(ctx.getAssets(), WebUiExtractor.ASSET_DIR, uiDir).copyToTarget();

            try (Writer ps = Channels.newWriter(new FileOutputStream(verFile, false).getChannel(), "UTF-8")) {
                ps.append(String.valueOf(BuildConfig.webuiVer));
            }
        } catch (IOException e) {
            final Bundle result = new Bundle(1);
            final String errMsg = TextUtils.isEmpty(e.getMessage()) ? e.getClass().getName() : e.getMessage();
            result.putString(errInfoKey, "Failed to extract WebUi (" + errMsg + ')');
            return result;
        } finally {
            Thread.interrupted();
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, "Frontend extraction complete",Toast.LENGTH_SHORT).show();
            }
        });

        return null;
    }
}
