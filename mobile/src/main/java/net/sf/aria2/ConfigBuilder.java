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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import net.sf.aria2.util.InterfaceUtil;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.List;

import static net.sf.aria2.Config.CONFIG_FILE_NAME;

public final class ConfigBuilder extends ContextWrapper {
    private final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    private final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

    public ConfigBuilder(Context base) {
        super(base);
    }

    public @NonNull Intent constructServiceCommand(Intent serviceMoniker) throws Exception {
        final Config ariaConfig = new Config();

        final Intent intent = ariaConfig.putInto(serviceMoniker)
                .putExtra(Config.EXTRA_INTERACTIVE, true)
                .putExtra(Aria2Service.EXTRA_NOTIFICATION, NfBuilder.createSerivceNf(this));

        final String downloadDir = prefs.getString(getString(R.string.download_dir_pref), "");
        if (TextUtils.isEmpty(downloadDir))
            throw new Exception(getString(R.string.error_empty_dir));


        final NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) {
            throw new Exception(getString(R.string.will_start_later));
        }

        final String networkInterface = getPreferredInterface();
        if (!TextUtils.isEmpty(networkInterface)) {
            ariaConfig.setNetworkInterface(networkInterface);
        }

        String binaryName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? "aria2_PIC" : "aria2";
        binaryName = "lib" + binaryName + "_exec.so";
        binaryName = new File(getApplicationInfo().nativeLibraryDir, binaryName).getAbsolutePath();

        final File sessionFile = new File(downloadDir, ".aria2.session.gz");

        final boolean showNfs = prefs.getBoolean(getString(R.string.show_nf_stopped_pref), true);

        final boolean showOutput = prefs.getBoolean(getString(R.string.show_output_pref), false);

        final boolean useATE = prefs.getBoolean(getString(R.string.use_ate_pref), false);

        final boolean takeWakelock = prefs.getBoolean(getString(R.string.use_wakelock_pref), false);

        final boolean outsideAccess = prefs.getBoolean(getString(R.string.outside_access_pref), false);

        final String secretToken = prefs.getString(getString(R.string.token_pref), getString(R.string.rpc_secret));

        ariaConfig.setSessionPath(sessionFile)
                .setProcessname(binaryName)
                .setRPCSecret(secretToken)
                .setShowStoppedNf(showNfs)
                .setUseATE(useATE)
                .setShowOutput(showOutput)
                .setListenAll(outsideAccess)
                .setTakeWakelock(takeWakelock);

        return intent;
    }

    public static final int NET_UNSPECIFIED = 0;
    public static final int NET_CUSTOM = 1;
    public static final int NET_ACTIVE = 2;

    private String getPreferredInterface() {
        final int defStrategy = getResources().getInteger(R.integer.network_strategy);

        final int networkConfig = Integer.parseInt(prefs.getString(getString(R.string.network_choice_strategy_pref), String.valueOf(defStrategy)));

        switch (networkConfig) {
            case NET_CUSTOM:
                return getChosenInterface();
            case NET_ACTIVE:
                return getActiveInterface();
            case NET_UNSPECIFIED:
            default:
                return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private String getActiveInterface() {
        final Network network = cm.getActiveNetwork();

        if (network == null) {
            return null;
        }

        final LinkProperties linkInfo = cm.getLinkProperties(network);

        if (linkInfo == null) {
            return null;
        }

        final List<LinkAddress> linkAddress = linkInfo.getLinkAddresses();

        if (linkAddress.isEmpty()) {
            return null;
        }

        final InetAddress address = linkAddress.get(0).getAddress();

        return address.getHostAddress();
    }

    private String getChosenInterface() {
        final String ifName = prefs.getString(getString(R.string.network_interface_pref), null);

        if (TextUtils.isEmpty(ifName)) {
            return null;
        }

        final NetworkInterface resolved = InterfaceUtil.resolveInterfaceByName(ifName);

        if (resolved == null) {
            return ifName;
        }

        return InterfaceUtil.getInterfaceAddress(resolved);
    }

    public void startForegroundCompat(Intent intent) {
        if (Build.VERSION.SDK_INT < 26) {
            startService(intent);
        } else {
            try {
                startService(intent);
            } catch (Throwable t) {
                startForegroundService(intent);
            }
        }
    }

    public void stopServiceCompat(Intent intent) {
        // This is quite hilarious, but documentation of stopService() indicates,
        // that it is not exempt from silly Android 26 background restrictions,
        // so our only recourse is to use the same approach (startServiceForeground) for stopping.
        // In the worst case, this will cause the service to receive our message,
        // promptly stop itself, then either get another start command (and dodge the ANR)
        // or die to ANR like Android's bitch it is.
        startForegroundCompat(intent);
    }
}
