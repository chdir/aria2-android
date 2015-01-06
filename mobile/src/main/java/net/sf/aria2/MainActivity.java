package net.sf.aria2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.EnvironmentCompat;
import android.widget.Toast;
import net.sf.aria2.util.CloseableHandler;
import net.sf.aria2.util.SimpleResultReceiver;
import org.jraf.android.backport.switchwidget.TwoStatePreference;

import java.io.File;

public final class MainActivity extends PreferenceActivity implements ServiceConnection {
    private TwoStatePreference pref;
    private CloseableHandler uiThreadHandler;
    private Intent sericeMoniker;
    private IAria2 serviceLink;
    private ResultReceiver backLink;

    private File downloadDir;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.layout);

        sericeMoniker = new Intent(getApplicationContext(), Aria2Service.class);

        pref = (TwoStatePreference) findPreference(getString(R.string.service_enable_pref));

        EditTextPreference dDir = (EditTextPreference) findPreference(getString(R.string.download_dir_pref));
        String dDirPath = dDir.getText();
        if (!dDirPath.isEmpty())
            downloadDir = new File(dDirPath);

        if (downloadDir == null) {
            downloadDir = deriveDownloadDir();
            dDir.setText(downloadDir.getAbsolutePath());
        }
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
                if (startService(constructServiceCommand(new Intent(sericeMoniker))) == null)
                    setPrefEnabled(true);
            }
        }

        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onStart() {
        super.onStart();

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

    @Override
    protected void onStop() {
        uiThreadHandler.close();

        unbindService(this);

        backLink = null;
        serviceLink = null;

        super.onStop();
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

    private void setPrefEnabled(boolean enabled) {
        pref.setEnabled(enabled);
        uiThreadHandler.removeCallbacksAndMessages(null);
    }

    private void bailOutBecauseOfBindingFailure() {
        // we can't really do anything without ability to bind to the service
        Toast.makeText(this, "Failed to start Aria2 remote service", Toast.LENGTH_LONG).show();
        finish();
    }

    private Intent constructServiceCommand(Intent serviceMoniker) {
        final Config ariaConfig = new Config();

        final Intent intent = ariaConfig.putInto(serviceMoniker);

        String binaryName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? "aria2_PIC" : "aria2";
        binaryName = "lib" + binaryName + "_exec.so";
        binaryName = new File(getApplicationInfo().nativeLibraryDir, binaryName).getAbsolutePath();
        ariaConfig.setProcessname(binaryName);

        downloadDir.mkdirs(); // TODO check for success, check if space is sufficient, make configurable

        final File sessionFile = new File(downloadDir, ".aria2.session.gz");
        ariaConfig.setSessionPath(sessionFile);
        ariaConfig.setRPCSecret(getString(R.string.rpc_secret));

        return intent;
    }
}
