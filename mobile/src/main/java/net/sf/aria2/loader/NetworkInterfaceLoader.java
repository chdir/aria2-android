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
package net.sf.aria2.loader;

import android.annotation.TargetApi;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import net.sf.aria2.R;
import net.sf.aria2.util.InterfaceUtil;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class NetworkInterfaceLoader extends AsyncTaskLoader<Bundle> implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final String strategyPrefName;
    private final String ifacePrefName;

    private BroadcastReceiver receiver;
    private Bundle results;

    public NetworkInterfaceLoader(Context context) {
        super(context.getApplicationContext());

        strategyPrefName = context.getString(R.string.network_choice_strategy_pref);
        ifacePrefName = context.getString(R.string.network_interface_pref);
    }

    @Override
    protected void onStartLoading() {
        forceLoad();

        PreferenceManager.getDefaultSharedPreferences(getContext())
                .registerOnSharedPreferenceChangeListener(this);

        if (receiver == null) {
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
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
    public void deliverResult(Bundle data) {
        if (isReset())
            return;

        Bundle oldData = results;
        results = data;

        if (isStarted())
            super.deliverResult(data);
    }

    @Override
    public Bundle loadInBackground() {
        final Context ctx = getContext();

        if (ctx == null)
            return null;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        final String strategyPref = prefs.getString(strategyPrefName, "");

        if ("1".equals(strategyPref)) {
            return analyzePreferred(ctx);
        } else if ("2".equals(strategyPref)) {
            return analyzeActiveNetwork(ctx);
        } else {
            return Bundle.EMPTY;
        }
    }

    private Bundle analyzePreferred(Context ctx) {
        final Bundle result = new Bundle();

        final ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);

        final NetworkInfo network = cm.getActiveNetworkInfo();

        if (network == null) {
            result.putString(ctx.getString(R.string.network_interface_pref), ctx.getString(R.string.no_connection));

            return result;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        final String ifacePref = prefs.getString(ifacePrefName, "");

        NetworkInterface iface = InterfaceUtil.resolveInterfaceByName(ifacePref);

        String resolved = null;

        if (iface == null) {
            if (TextUtils.isEmpty(ifacePref)) {
                resolved = composeHint(ctx);
            }

            if (TextUtils.isEmpty(resolved)) {
                final String err = ctx.getString(R.string.unable_to_get_if_info);

                resolved = TextUtils.isEmpty(ifacePref) ? err : ifacePref + ": " + err;
            }
        } else {
            final String addr = InterfaceUtil.getInterfaceAddress(iface);

            resolved = ifacePref + ": " + (addr == null ? ctx.getString(R.string.no_address) : addr);
        }

        result.putString(ctx.getString(R.string.network_interface_pref), resolved);

        return result;
    }

    private String composeHint(Context ctx) {
        Enumeration<NetworkInterface> interfaces;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();

            if (interfaces == null) {
                return null;
            }

            final StringBuilder builder = new StringBuilder();

            while (interfaces.hasMoreElements()) {
                NetworkInterface ifc = interfaces.nextElement();

                if (!ifc.isLoopback()) {
                    builder.append(ifc.getName());
                    builder.append(", ");
                }
            }

            if (builder.length() != 0) {
                builder.delete(builder.length() - 2, builder.length());
                builder.insert(0, ctx.getString(R.string.if_sel_header));
                return builder.toString();
            }
        } catch (SocketException e) {
            // ok
        }

        return null;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private Bundle analyzeActiveNetwork(Context context) {
        final Bundle result = new Bundle();

        final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        final Network network = cm.getActiveNetwork();

        if (network == null) {
            result.putString(context.getString(R.string.network_interface_pref), context.getString(R.string.no_connection));

            return result;
        }

        final LinkProperties properties = cm.getLinkProperties(network);

        final NetworkInfo amm = cm.getNetworkInfo(network);

        final String interfaceName = properties.getInterfaceName();

        final NetworkInterface iface = InterfaceUtil.resolveInterfaceByName(interfaceName);

        if (iface == null) {
            String explanation = amm.getReason();

            if (TextUtils.isEmpty(explanation)) {
                final String err = context.getString(R.string.unable_to_get_if_info);

                explanation = TextUtils.isEmpty(interfaceName) ? err: interfaceName + ": " + err;
            }

            result.putString(context.getString(R.string.network_interface_pref), explanation);

            return result;
        }

        final NetworkCapabilities netcaps = cm.getNetworkCapabilities(network);

        String netDesc = amm.getTypeName();
        final String subtupe = amm.getSubtypeName();
        if (!TextUtils.isEmpty(subtupe)) {
            netDesc = interfaceName + ": " + netDesc + ' ' + subtupe +
                    " ↑ " + netcaps.getLinkUpstreamBandwidthKbps() + " Kbps" +
                    " ↓ " + netcaps.getLinkDownstreamBandwidthKbps() + " Kbps";
        }

        result.putString(context.getString(R.string.network_interface_pref), netDesc);

        return result;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (ifacePrefName.equals(key) || strategyPrefName.equals(key))
            onContentChanged();
    }

    private final class DataObserverReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            onContentChanged();
        }
    }
}
