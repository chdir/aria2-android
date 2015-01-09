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
import android.content.*;
import android.os.*;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.widget.Toast;
import net.sf.aria2.util.CloseableHandler;
import net.sf.aria2.util.SimpleResultReceiver;
import org.jraf.android.backport.switchwidget.TwoStatePreference;

import java.util.List;

public final class MainActivity extends PreferenceActivity {
    private ServiceControl serviceControl;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // at some point after API 11 framework begins to throw, when you use old style..
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            addPreferencesFromResource(R.xml.preferences_aria2);
            addPreferencesFromResource(R.xml.preferences_client);
            addPreferencesFromResource(R.xml.preferences_misc);
            serviceControl = new ServiceControl(this);
            serviceControl.init(getPreferenceScreen());
        }
    }

    @Override
    public boolean onIsMultiPane() {
        // Thank you for being so full of shit, dear Android developers -
        // https://stackoverflow.com/q/18138642
        return getResources().getBoolean(R.bool.use_multipane);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return true; // SURE
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.headers, target);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
            serviceControl.start();
    }

    @Override
    protected void onStop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
            serviceControl.stop();

        super.onStop();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class Aria2Preferences extends PreferenceFragment {
        private ServiceControl serviceControl;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences_aria2);
            serviceControl = new ServiceControl(getActivity());
            serviceControl.init(getPreferenceScreen());
        }

        @Override
        public void onStart() {
            super.onStart();

            serviceControl.start();
        }

        @Override
        public void onStop() {
            serviceControl.stop();

            super.onStop();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class FrontendPreferences  extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences_client);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class MiscPreferences  extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences_misc);
        }
    }
}

final class ServiceControl extends ContextWrapper implements ServiceConnection {
    private TwoStatePreference pref;
    private CloseableHandler uiThreadHandler;
    private Intent sericeMoniker;
    private IAria2 serviceLink;
    private ResultReceiver backLink;
    private ConfigBuilder builder;

    public ServiceControl(Context base) {
        super(base);
    }

    public void init(PreferenceScreen screen) {
        sericeMoniker = new Intent(getApplicationContext(), Aria2Service.class);

        builder = new ConfigBuilder(this);

        pref = (TwoStatePreference) screen.findPreference(getString(R.string.service_enable_pref));
    }

    public void start() {
        uiThreadHandler = new CloseableHandler();

        backLink = new SimpleResultReceiver<Boolean>(uiThreadHandler) {
            @Override
            protected void receiveResult(Boolean started) {
                pref.setChecked(started);

                setPrefEnabled(true);
            }
        };

        pref.setOnPreferenceClickListener(this::changeAriaServiceState);

        bindService(sericeMoniker, this, Context.BIND_AUTO_CREATE);
    }

    public void stop() {
        uiThreadHandler.close();

        unbindService(this);

        backLink = null;
        serviceLink = null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        pref.setEnabled(true);

        serviceLink = IAria2.Stub.asInterface(service);

        try {
            serviceLink.setResultReceiver(backLink);

            pref.setChecked(serviceLink.isRunning());
        } catch (RemoteException e) {
            // likely service process dying right after binding;
            // pretty much equals to knowledge, that aria2 isn't running
            e.printStackTrace();
            pref.setEnabled(false);
            pref.setChecked(false);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (serviceLink != null) {
            // if we managed to obtain binder once without fail, we may as well try again
            serviceLink = null;

            if (!bindService(sericeMoniker, this, Context.BIND_AUTO_CREATE))
                bailOutBecauseOfBindingFailure();
        } else
            bailOutBecauseOfBindingFailure();
    }

    private boolean changeAriaServiceState(Preference p) {
        if (p.isEnabled()) {
            pref.setEnabled(false);
            uiThreadHandler.postDelayed(() -> pref.setEnabled(true), 4000);

            if (pref.isChecked()) {
                try {
                    serviceLink.askToStop();
                } catch (RemoteException e) {
                    // likely service process dying during the call
                    // let's hope, that onSerivceDisconnected will fix it for us
                    e.printStackTrace();

                    setPrefEnabled(false);
                }
            } else {
                if (startService(builder.constructServiceCommand(new Intent(sericeMoniker))) == null)
                    setPrefEnabled(true);
            }
        }

        return true;
    }

    private void setPrefEnabled(boolean enabled) {
        pref.setEnabled(enabled);
        uiThreadHandler.removeCallbacksAndMessages(null);
    }

    private void bailOutBecauseOfBindingFailure() {
        // we can't really do anything without ability to bind to the service
        Toast.makeText(this, "Failed to start Aria2 remote service", Toast.LENGTH_LONG).show();

        throw new IllegalStateException("Failed to start Aria2 remote service");
    }
}
