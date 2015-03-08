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

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.EnvironmentCompat;
import android.text.TextUtils;

import java.io.File;

public final class ConfigBuilder extends ContextWrapper {
    private final SharedPreferences prefs;

    private File downloadDir;

    public ConfigBuilder(Context base) {
        super(base);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        final String dDirPrefName = getString(R.string.download_dir_pref);

        String dDir = prefs.getString(dDirPrefName, "");

        if (!TextUtils.isEmpty(dDir))
            downloadDir = new File(dDir);

        if (downloadDir == null || !downloadDir.exists() || !downloadDir.mkdirs()) {
            downloadDir = deriveDownloadDir();
            prefs.edit()
                    .putString(dDirPrefName, downloadDir.getAbsolutePath())
                    .apply();
        }
    }

    public Intent constructServiceCommand(Intent serviceMoniker) {
        final Config ariaConfig = new Config();

        final Intent intent = ariaConfig.putInto(serviceMoniker)
                .putExtra(Config.EXTRA_INTERACTIVE, true);

        String binaryName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? "aria2_PIC" : "aria2";
        binaryName = "lib" + binaryName + "_exec.so";
        binaryName = new File(getApplicationInfo().nativeLibraryDir, binaryName).getAbsolutePath();
        ariaConfig.setProcessname(binaryName);

        final File sessionFile = new File(downloadDir, ".aria2.session.gz");
        ariaConfig.setSessionPath(sessionFile);
        ariaConfig.setRPCSecret(getString(R.string.rpc_secret));

        final boolean showNfs = prefs.getBoolean(getString(R.string.show_nf_stopped_pref), true);
        ariaConfig.setShowStoppedNf(showNfs);

        final boolean useATE = prefs.getBoolean(getString(R.string.use_ate_pref), false);
        ariaConfig.setUseATE(useATE);

        final boolean showOutput = prefs.getBoolean(getString(R.string.show_output_pref), false);
        if (!showOutput) ariaConfig.add("-q");
        ariaConfig.add("--show-console-readout=" + showOutput);

        if (!useATE) {
            ariaConfig.add("--summary-interval=0");
        }

        return intent;
    }

    private File deriveDownloadDir() {
        File aria2Dir;
        File[] externalDirs = ContextCompat.getExternalFilesDirs(this, Environment.DIRECTORY_MUSIC);
        if (externalDirs.length > 1 && externalDirs[1] != null)
            aria2Dir = externalDirs[1].getParentFile();
        else {
            File externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(externalDir)))
                aria2Dir = externalDir;
            else
                aria2Dir = getFilesDir();
        }
        aria2Dir = new File(aria2Dir, "Aria2Download");
        return aria2Dir;
    }
}
