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
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.*;
import android.os.*;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import net.sf.aria2.util.CloseableHandler;
import net.sf.aria2.util.SimpleResultReceiver;
import org.jraf.android.backport.switchwidget.TwoStatePreference;

import java.util.ArrayList;
import java.util.List;

import static android.os.Build.VERSION_CODES.*;

public final class MainActivity extends PreferenceActivity {
    private Iterable<Header> headers;
    private String lastChosenFragment;
    private ServiceControl serviceControl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean offerUpNav;

        // at some point after API 11 framework begins to throw, when you use old style..
        if (Build.VERSION.SDK_INT < HONEYCOMB) {
            initLegacyPrefs();
            offerUpNav = false;
        } else
            offerUpNav = isOfferingUpNav();

        final LinearLayout toolbarContainer = (LinearLayout) View.inflate(this, R.layout.activity_prefs, null);

        // transplant content view children (created for us by framework)
        final ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
        final int contentChildrenCount = root.getChildCount();
        final List<View> childViews = new ArrayList<>(contentChildrenCount);

        for (int i = 0; i < contentChildrenCount; ++i)
            childViews.add(root.getChildAt(i));

        root.removeAllViews();

        for (View childView:childViews)
            toolbarContainer.addView(childView);

        root.addView(toolbarContainer);

        // initialize toolbar
        final Toolbar toolbar = (Toolbar) toolbarContainer.findViewById(R.id.toolbar);
        toolbar.setTitle(getTitle());

        if (offerUpNav) {
            toolbar.setLogo(null);
            toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
            toolbar.setNavigationOnClickListener(v -> goUp());
        } else {
            toolbar.setNavigationIcon(null);
            toolbar.setLogo(R.drawable.aria2_logo);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Select the displayed fragment in the headers:
        // This should be done by Android, it is a bug fix
        if(headers != null)
            reselectFragment();
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        lastChosenFragment = fragment.getClass().getName();
    }

    @TargetApi(HONEYCOMB)
    private void reselectFragment() {
        final Intent intent = getIntent();

        if (intent != null && intent.hasExtra(Config.EXTRA_FROM_NF)) {
            final String displayedFragment = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
            if (displayedFragment != null) {
                final FragmentManager fm = getFragmentManager();
                fm.executePendingTransactions();

                if (displayedFragment.equals(lastChosenFragment))
                    return;

                final Header headerToSelect = onGetInitialHeader();

                startPreferencePanel(
                        displayedFragment,
                        headerToSelect.fragmentArguments,
                        headerToSelect.titleRes,
                        headerToSelect.title,
                        null, 0);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    @TargetApi(HONEYCOMB)
    public Header onGetInitialHeader() {
        final Intent intent = getIntent();

        final String chosenHeader;
        if (headers != null && intent != null && (chosenHeader = intent.getStringExtra(EXTRA_SHOW_FRAGMENT)) != null) {
            for (Header h:headers) {
                if (chosenHeader.equals(h.fragment)) {
                    return h;
                }
            }
        }

        return super.onGetInitialHeader();
    }

    @TargetApi(HONEYCOMB)
    private boolean isOfferingUpNav() {
        // we have the Service pass extra parameter in notification Intent, because someone was too lazy
        // to document internal workings of the PreferenceActivity
        return (getIntent().hasExtra(Config.EXTRA_FROM_NF) || onIsHidingHeaders()) && !onIsMultiPane();
    }

    private void goUp() {
        // this is valid exactly as long as iiner workings of PreferenceActivity remain same
        final Intent parentIntent = new Intent(this, MainActivity.class);
        // This would return us unresolved version instead  :(
        //final Intent parentIntent = NavUtils.getParentActivityIntent(this);


        // once again we have to resort to double-checking here, because someone was too lazy... you know, right?
        // note: using addNextIntentWithParentStack (and thus addParentStack) results in hanging for some reason
        // (confirmed on JellyBean). This is correct replacement, according to documentation :(
        if (NavUtils.shouldUpRecreateTask(this, parentIntent) || getIntent().hasExtra(Config.EXTRA_FROM_NF)) {
            TaskStackBuilder.create(this)
                    .addNextIntent(parentIntent)
                    .startActivities();
        } else
            NavUtils.navigateUpFromSameTask(this);
    }

    @SuppressWarnings("deprecation")
    private void initLegacyPrefs() {
        addPreferencesFromResource(R.xml.preferences_aria2);
        addPreferencesFromResource(R.xml.preferences_client);
        addPreferencesFromResource(R.xml.preferences_misc);
        serviceControl = new ServiceControl(this);
        serviceControl.init(getPreferenceScreen());
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
    @TargetApi(HONEYCOMB)
    public void onBuildHeaders(List<Header> headers) {
        loadHeadersFromResource(R.xml.headers, headers);

        this.headers = headers;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (Build.VERSION.SDK_INT < HONEYCOMB)
            serviceControl.start();
    }

    @Override
    protected void onStop() {
        if (Build.VERSION.SDK_INT < HONEYCOMB)
            serviceControl.stop();

        super.onStop();
    }

    @TargetApi(HONEYCOMB)
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

    @TargetApi(HONEYCOMB)
    public static class FrontendPreferences  extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences_client);
        }
    }

    @TargetApi(HONEYCOMB)
    public static class MiscPreferences extends PreferenceFragment {
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
