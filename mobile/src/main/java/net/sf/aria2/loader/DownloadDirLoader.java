package net.sf.aria2.loader;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.EnvironmentCompat;
import android.text.TextUtils;
import net.sf.aria2.R;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class DownloadDirLoader extends AsyncTaskLoader<Long> implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final String prefName;

    private BroadcastReceiver receiver;
    private Long freeSpace;

    public DownloadDirLoader(Context context) {
        super(context.getApplicationContext());

        prefName = context.getString(R.string.download_dir_pref);
    }

    @Override
    protected void onStartLoading() {
        forceLoad();

        PreferenceManager.getDefaultSharedPreferences(getContext())
                .registerOnSharedPreferenceChangeListener(this);

        if (receiver == null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            filter.addAction(Intent.ACTION_MEDIA_REMOVED);
            filter.addDataScheme("file");
            getContext().registerReceiver(receiver = new DataObserverReceiver(), filter);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void onStopLoading() {
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .unregisterOnSharedPreferenceChangeListener(this);

        cancelLoad();
    }

    @Override
    protected void onReset() {
        if (receiver != null) {
            getContext().unregisterReceiver(receiver);
            receiver = null;
        }

        super.onReset();
    }

    @Override
    public void deliverResult(Long data) {
        if (isReset())
            return;

        Long oldData = data;
        freeSpace = data;

        if (isStarted())
            super.deliverResult(data);
    }

    @Override
    @SuppressLint({"ApplySharedPref"}) // we are in background thread and want value to be propagated for real
    public Long loadInBackground() {
        final Context ctx = getContext();

        if (ctx == null)
            return null;

        final File dir;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        final String dDir = prefs.getString(prefName, "");

        if (TextUtils.isEmpty(dDir)) {
            dir = new File(deriveDownloadDir(), "Aria2Download");

            prefs.edit().putString(prefName, dir.getAbsolutePath()).commit();
        } else
            dir = new File(dDir);

        return freeSpace = checkAccess(dir) ? getSpace(dir) : -1L;
    }

    @SuppressWarnings("deprecation")
    private static long getSpace(File dir)
    {
        StatFs statFs = new StatFs(dir.getAbsolutePath());
        final long free;
        if (Build.VERSION.SDK_INT >= 18) {
            free = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
        } else {
            free = statFs.getAvailableBlocks() * statFs.getBlockSize();
        }

        return free;
    }

    private File deriveDownloadDir() {
        File[] externalDirs = ContextCompat.getExternalFilesDirs(getContext(), Environment.DIRECTORY_MUSIC);
        if (externalDirs.length > 1 && externalDirs[1] != null) {
            File aria2Dir;
            for (int i = 1; i < externalDirs.length + 1; i++) {
                aria2Dir = externalDirs[externalDirs.length - i].getParentFile();
                if (checkAccess(aria2Dir))
                    return aria2Dir;
            }
        }

        File externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(externalDir)) && checkAccess(externalDir))
            return externalDir;
        else
            return getContext().getFilesDir();
    }

    private boolean checkAccess(File dir) {
        if (!dir.exists() && !dir.mkdirs())
            return false;

        final File tmptest = new File(dir, UUID.randomUUID().toString());
        try {
            return tmptest.createNewFile() && tmptest.delete();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (prefName.equals(key))
            onContentChanged();
    }

    private class DataObserverReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            onContentChanged();
        }
    }
}
