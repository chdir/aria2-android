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

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Config extends ArrayList<String> implements Parcelable {
    private static final String EXTRA_NAME = BuildConfig.APPLICATION_ID + ".config";

    public static final String EXTRA_INTERACTIVE = "interactive";

    public Config() {
        super(16);
        addAll(Arrays.asList(
                "-q", "--enable-rpc",
                "--rpc-save-upload-metadata=true",
                "--save-session-interval=10"));
    }

    @SuppressWarnings("unchecked")
    public Config(List list) {
        super(list);
    }

    public Intent putInto(Intent container) {
        return container.putExtra(EXTRA_NAME, (Parcelable) this);
    }

    @SuppressWarnings("unchecked")
    public static Config from(Intent container) {
        return container.getParcelableExtra(Config.EXTRA_NAME);
    }

    public void setProcessname(String processname) {
        add(0, processname);
    }

    public void setSessionPath(File sessionFile) {
        final String fileName = sessionFile.getAbsolutePath();

        add("-d");
        add(sessionFile.getParent());

        add("--save-session");
        add(fileName);

        if (sessionFile.exists()) {
            add("-i");
            add(fileName);
        }
    }

    public void setRPCSecret(String secret) {
        add("--rpc-secret");
        add(secret);
    }

    @Override
    public int describeContents() {
        return -1;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeArray(toArray());
    }

    public static Parcelable.Creator<Config> CREATOR = new Creator<Config>() {
        @Override
        public Config createFromParcel(Parcel source) {
            return new Config(Arrays.asList(source.readArray(getClass().getClassLoader())));
        }

        @Override
        public Config[] newArray(int size) {
            return new Config[size];
        }
    };
}
