package net.sf.aria2;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ApplicationLoader extends AsyncTaskLoader<Bundle> {
    private Bundle intents;

    public ApplicationLoader(Context context) {
        super(context.getApplicationContext());
    }

    @Override
    protected void onStartLoading() {
        forceLoad();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    public void deliverResult(Bundle data) {
        if (isReset())
            return;

        Bundle oldData = data;
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
}
