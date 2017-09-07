/*
 * aria2 - The high speed download utility (Android port)
 *
 * Copyright © 2017 Alexander Rvachev
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

import static android.Manifest.permission.WAKE_LOCK;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION_CODES.HONEYCOMB;

@TargetApi(HONEYCOMB)
final class PermissionHelper {
    private PermissionHelper() {}

    static boolean checkStoragePermission(Fragment fragment) {
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

    static boolean checkWakelockPermission(Fragment fragment) {
        if (Build.VERSION.SDK_INT < 23) {
            // runtime permissions not supported
            return true;
        }

        final Context context = fragment.getActivity();
        try {
            if (context.checkSelfPermission(WAKE_LOCK) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        return false;
    }
}
