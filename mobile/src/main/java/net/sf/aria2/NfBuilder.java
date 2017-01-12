package net.sf.aria2;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceActivity;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

final class NfBuilder {
    static Notification createSerivceNf(Context ctx) {
        @SuppressLint("InlinedApi")
        final Intent resultIntent = new Intent(ctx, MainActivity.class)
                .putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, "net.sf.aria2.MainActivity$Aria2Preferences")
                .putExtra(Config.EXTRA_FROM_NF, true);

        // note: using addParentStack results in hanging for some reason (confirmed on JellyBean)
        // there is only one activity in stack to handle up and back navigation differently
        final TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx)
                .addNextIntent(resultIntent);
        final PendingIntent contentIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(ctx)
                .setSmallIcon(R.drawable.ic_nf_icon)
                .setTicker("aria2 is running")
                .setContentTitle("aria2 is running")
                .setContentText("Touch to open settings")
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .build();
    }

    static Notification createStoppedNf(Context ctx,
                                         int exitCode,
                                         boolean someTimeElapsed,
                                         boolean killedForcefully) {
        final ExitCode ec = ExitCode.from(exitCode);

        final String title = someTimeElapsed
                ? ctx.getString(R.string.aria2_has_stopped)
                : ctx.getString(R.string.aria2_has_failed_to_start);

        final Intent i = new Intent(ctx, MainActivity.class);
        final PendingIntent contentIntent = PendingIntent.getActivity(ctx, R.id.req_from_nf, i, PendingIntent.FLAG_UPDATE_CURRENT);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx)
                .setSmallIcon(R.drawable.ic_stat_a)
                .setTicker(title)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(false);

        final String errText = ec.getDesc(ctx.getResources());

        if (ec.isSuccess() && someTimeElapsed) {
            builder.setContentText(errText);
        } else {
            if (someTimeElapsed) {
                builder.setContentText(ctx.getString(R.string.there_may_have_been_issues));

                if (killedForcefully)
                    builder.setNumber(ec.getCode());
                else {
                    builder.setContentInfo('#' + ec.name())
                            .setSubText(ctx.getString(R.string.expand_nf_to_see_details))
                            .setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText(ctx.getString(R.string.explanation, errText)));
                }
            } else if (!ec.isSuccess()) {
                builder.setContentInfo('#' + ec.name())
                        .setContentText(Character.toUpperCase(errText.charAt(0)) + errText.substring(1));
            }
        }

        return builder.build();
    }

    private NfBuilder() {}
}
