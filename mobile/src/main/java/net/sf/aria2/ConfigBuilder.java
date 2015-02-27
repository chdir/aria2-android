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

        final boolean showOutput = prefs.getBoolean(getString(R.string.show_output_pref), false);
        if (!showOutput) ariaConfig.add("-q");

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
