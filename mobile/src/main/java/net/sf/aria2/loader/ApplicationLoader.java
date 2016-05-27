package net.sf.aria2.loader;

import android.annotation.TargetApi;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import net.sf.aria2.R;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ApplicationLoader extends AsyncTaskLoader<Bundle> {
    private BroadcastReceiver receiver;
    private Bundle intents;

    public ApplicationLoader(Context context) {
        super(context.getApplicationContext());
    }

    @Override
    protected void onStartLoading() {
        if (intents != null)
            deliverResult(intents);

        if (intents == null || takeContentChanged())
            forceLoad();

        if (receiver == null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            getContext().registerReceiver(receiver = new DataObserverReceiver(), filter);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void onStopLoading() {
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

        Bundle oldData = intents;
        intents = data;

        if (isStarted())
            super.deliverResult(data);
    }

    @Override
    public Bundle loadInBackground() {
        final Context ctx = getContext();

        if (ctx == null)
            return null;

        final Bundle result = new Bundle();

        final PackageManager pm = ctx.getPackageManager();

        final String transdroidPkg = "org.transdroid.full";
        final String transdroidLitePkg = "org.transdroid.lite";

        final String termPkg = "jackpal.androidterm";

        Intent transdroidIntent = pm.getLaunchIntentForPackage(transdroidPkg);
        if (transdroidIntent == null)
            transdroidIntent = pm.getLaunchIntentForPackage(transdroidLitePkg);

        Intent termIntent = pm.getLaunchIntentForPackage(termPkg);

        result.putParcelable(getContext().getString(R.string.transdroid_app_pref), transdroidIntent);
        result.putParcelable(getContext().getString(R.string.ate_app_pref), termIntent);

        return result;
    }

    private class DataObserverReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            onContentChanged();
        }
    }
}
