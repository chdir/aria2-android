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

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

public final class PrivateReceiver extends BroadcastReceiver {
    private Toast lastShown;

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action;
        switch ((action = intent.getAction()) == null ? "" : action) {
            case Aria2Service.ACTION_TOAST:
                final String text =  intent.getStringExtra(Aria2Service.EXTRA_TEXT);

                if (!TextUtils.isEmpty(text.trim())) {
                    if (lastShown != null) {
                        lastShown.cancel();
                    }

                    lastShown = Toast.makeText(context, text, Toast.LENGTH_LONG);
                    lastShown.show();
                }

                break;

            case Aria2Service.ACTION_NF_STOPPED:
                final int code = intent.getIntExtra(Aria2Service.EXTRA_EXIT_CODE, -1);
                final boolean worked = intent.getBooleanExtra(Aria2Service.EXTRA_DID_WORK, false);
                final boolean killed = intent.getBooleanExtra(Aria2Service.EXTRA_KILLED_FORCEFULLY, false);

                if (code != -1) {
                    final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                    nm.notify(R.id.nf_status, NfBuilder.createStoppedNf(context, code, worked, killed));
                }

                break;
        }
    }
}
