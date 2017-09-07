package net.sf.aria2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class PublicReceiver extends BroadcastReceiver {
    public static final String INTENT_START_SERVICE = "net.sf.aria2.service.START_SERVICE";
    public static final String INTENT_STOP_SERVICE = "net.sf.aria2.service.STOP_SERVICE";
    public static final String INTENT_RESTART_SERVICE = "net.sf.aria2.service.RESTART_SERVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        final ConfigBuilder builder = new ConfigBuilder(context);

        switch ((action == null ? "" : action)) {
            case INTENT_START_SERVICE:
                try {
                    final Intent serviceIntent = builder
                            .constructServiceCommand(new Intent(context, Aria2Service.class))
                            .setAction(INTENT_START_SERVICE);

                    builder.startForegroundCompat(serviceIntent);
                } catch (Exception e) {
                    // nothing can be done…
                }
                break;
            case INTENT_RESTART_SERVICE:
                try {
                    final Intent serviceIntent = builder
                            .constructServiceCommand(new Intent(context, Aria2Service.class))
                            .setAction(INTENT_RESTART_SERVICE);

                    builder.startForegroundCompat(serviceIntent);
                } catch (Exception e) {
                    // nothing can be done…
                }
                break;
            case INTENT_STOP_SERVICE:
                final Intent serviceIntent = new Intent(context, Aria2Service.class)
                        .setAction(INTENT_STOP_SERVICE);

                builder.stopServiceCompat(serviceIntent);
        }
    }
}
