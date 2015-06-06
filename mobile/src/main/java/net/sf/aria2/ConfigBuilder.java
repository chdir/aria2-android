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
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;

public final class ConfigBuilder extends ContextWrapper {
    private final SharedPreferences prefs;

    public ConfigBuilder(Context base) {
        super(base);

        prefs = PreferenceManager.getDefaultSharedPreferences(base);
    }

    public Intent constructServiceCommand(Intent serviceMoniker) {
        final Config ariaConfig = new Config();

        final Intent intent = ariaConfig.putInto(serviceMoniker)
                .putExtra(Config.EXTRA_INTERACTIVE, true)
                .putExtra(Aria2Service.EXTRA_NOTIFICATION, NfBuilder.createSerivceNf(this));

        final String downloadDir = prefs.getString(getString(R.string.download_dir_pref), "");
        if (TextUtils.isEmpty(downloadDir))
            return null;

        String binaryName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? "aria2_PIC" : "aria2";
        binaryName = "lib" + binaryName + "_exec.so";
        binaryName = new File(getApplicationInfo().nativeLibraryDir, binaryName).getAbsolutePath();

        final File sessionFile = new File(downloadDir, ".aria2.session.gz");

        final boolean showNfs = prefs.getBoolean(getString(R.string.show_nf_stopped_pref), true);

        final boolean showOutput = prefs.getBoolean(getString(R.string.show_output_pref), false);

        final boolean useATE = prefs.getBoolean(getString(R.string.use_ate_pref), false);

        ariaConfig.setSessionPath(sessionFile)
                .setProcessname(binaryName)
                .setRPCSecret(getString(R.string.rpc_secret))
                .setShowStoppedNf(showNfs)
                .setUseATE(useATE)
                .setShowOutput(showOutput);

        return intent;
    }
}
