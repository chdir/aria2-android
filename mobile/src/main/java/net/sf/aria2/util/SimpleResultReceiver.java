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
package net.sf.aria2.util;

import android.content.Intent;
import android.os.*;

import java.io.Serializable;

public abstract class SimpleResultReceiver<X extends Serializable> extends ResultReceiver {
    public final static String OBJ = "obj";

    public SimpleResultReceiver(Handler handler) {
        super(new Handler());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        receiveResult((X) resultData.get(OBJ));
    }

    public Intent stuffInto(Intent container) {
        container.putExtra(OBJ, this);
        return container;
    }

    public static ResultReceiver from(Intent container) {
        return container.getParcelableExtra(OBJ);
    }

    protected abstract void receiveResult(X result);

    public void send(X thing) {
        final Bundle container = new Bundle();
        container.putSerializable(OBJ, thing);
        send(0, container);
    }
}
