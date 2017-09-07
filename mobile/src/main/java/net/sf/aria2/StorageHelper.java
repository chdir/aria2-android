package net.sf.aria2;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.PreferenceManager;
import android.system.Os;
import android.system.StructStat;
import android.text.TextUtils;

import java.io.File;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION_CODES.HONEYCOMB;

@TargetApi(HONEYCOMB)
final class StorageHelper {
    private StorageHelper() {}

    static boolean checkPermission(Fragment fragment) {
        if (Build.VERSION.SDK_INT < 23) {
            // runtime permissions not supported
            return false;
        }

        final Context context = fragment.getActivity();
        final String prefName = context.getString(R.string.download_dir_pref);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String dDir = prefs.getString(prefName, "");

        try {
            if (Build.VERSION.SDK_INT >= 24) {
                if (!TextUtils.isEmpty(dDir)) {
                    final File file = new File(dDir);
                    final StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                    final StorageVolume sv = sm.getStorageVolume(file);
                    if (sv == null || !Environment.MEDIA_MOUNTED.equals(sv.getState())) {
                        return false;
                    }
                }
            } else {
                // use old-fashioned stat() check
                try {
                    final File primaryStorage = Environment.getExternalStorageDirectory();
                    if (primaryStorage != null) {
                        final StructStat dirStat = Os.stat(dDir);
                        final StructStat primaryStat = Os.stat(primaryStorage.getAbsolutePath());
                        if (dirStat.st_dev != primaryStat.st_dev) {
                            // the directory is not on primary external storage, bail
                            return false;
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (context.checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                fragment.requestPermissions(new String[] { WRITE_EXTERNAL_STORAGE }, R.id.req_file_permission);
                return true;
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        return false;
    }
}
