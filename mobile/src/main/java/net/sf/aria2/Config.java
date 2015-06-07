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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Config extends ArrayList<String> implements Parcelable {
    static final String ACTION_DOWNLOAD_START = "net.sf.aria2.action.STARTED";
    static final String ACTION_DOWNLOADED_OK = "net.sf.aria2.action.DOWNLOADED";
    static final String ACTION_DOWNLOADED_ERR = "net.sf.aria2.action.FAILED";
    static final String ACTION_SEEDING_END = "net.sf.aria2.action.SEEDED";

    public static final String EXTRA_ACTIVE_DS = "net.sf.aria2.extra.ACTIVE";
    public static final String EXTRA_ENDED_DS = "net.sf.aria2.extra.ENDED";
    public static final String EXTRA_SEEDING_DS = "net.sf.aria2.extra.SEEDING";
    public static final String EXTRA_FAILED_DS = "net.sf.aria2.extra.FAILED";

    public static final String EXTRA_FAILED_PATH = "net.sf.aria2.extra.FAIL_URI";

    private static final String EXTRA_NAME = BuildConfig.APPLICATION_ID + ".config";

    // not using PreferenceActivity stuff because it's API stability is gross
    static final String EXTRA_FROM_NF = BuildConfig.APPLICATION_ID + ".no_backstack";
    static final String EXTRA_INTERACTIVE = BuildConfig.APPLICATION_ID + ".interactive";

    static final String TAG = "aria2j";

    boolean showStoppedNf;

    boolean useATE;

    boolean showOutput;

    public Config() {
        super(20);
        addAll(Arrays.asList(
                "-c", "--enable-rpc", "--referer=*",
                "--enable-color=false",
                "--bt-save-metadata=true",
                "--rpc-allow-origin-all=true",
                "--rpc-save-upload-metadata=true",
                "--save-session-interval=10"));
    }

    public Config setShowStoppedNf(boolean showStoppedNf) {
        this.showStoppedNf = showStoppedNf;
        return this;
    }

    public Config setUseATE(boolean useATE) {
        this.useATE = useATE;

        add("--show-console-readout=" + useATE);

        if (!useATE) add("--summary-interval=0");

        return this;
    }

    public Config setShowOutput(boolean showOutput) {
        this.showOutput = showOutput;

        if (!showOutput && !useATE) add("-q");

        return this;
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

    public Config setProcessname(String processname) {
        add(0, processname);
        return this;
    }

    public Config setSessionPath(File sessionFile) {
        final String fileName = sessionFile.getAbsolutePath();

        final File sessionParent = sessionFile.getParentFile();
        //noinspection ResultOfMethodCallIgnored
        sessionParent.mkdirs();

        final File configFile = new File(sessionParent, "aria2.txt");
        //noinspection ResultOfMethodCallIgnored
        try {
            configFile.createNewFile();
        } catch (IOException ignored) {}

        add("-d");
        add(sessionParent.getAbsolutePath());

        add("--save-session");
        add(fileName);

        add("--conf-path");
        add(configFile.getAbsolutePath());

        if (sessionFile.exists()) {
            add("-i");
            add(fileName);
        }

        return this;
    }

    public Config setRPCSecret(String secret) {
        add("--rpc-secret");
        add(secret);
        return this;
    }

    @Override
    public int describeContents() {
        return -1;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeArray(toArray());
        dest.writeValue(showStoppedNf);
        dest.writeValue(useATE);
        dest.writeValue(showOutput);
    }

    public static final Parcelable.Creator<Config> CREATOR = new Creator<Config>() {
        @Override
        public Config createFromParcel(Parcel source) {
            return new Config(Arrays.asList(source.readArray(getClass().getClassLoader())))
                    .setShowStoppedNf((Boolean) source.readValue(null))
                    .setUseATE((Boolean) source.readValue(null))
                    .setShowOutput((Boolean) source.readValue(null));
        }

        @Override
        public Config[] newArray(int size) {
            return new Config[size];
        }
    };
}
