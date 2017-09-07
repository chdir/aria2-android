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
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Config implements Parcelable {
    public static final String CONFIG_FILE_NAME = "aria2.txt";

    private static final String EXTRA_NAME = BuildConfig.APPLICATION_ID + ".config";

    // not using PreferenceActivity stuff because it's API stability is gross
    static final String EXTRA_FROM_NF = BuildConfig.APPLICATION_ID + ".no_backstack";
    static final String EXTRA_INTERACTIVE = BuildConfig.APPLICATION_ID + ".interactive";

    static final String TAG = "aria2j";

    private final Set<String> singularOptions = new LinkedHashSet<>(20);

    File sessionDir;
    File sessionFile;
    File configFile;

    String binaryName;

    String networkInterface;

    boolean showStoppedNf;

    boolean useATE;

    boolean showOutput;

    boolean takeWakelock;

    boolean listenAll;

    String secret;

    public Config() {
        Collections.addAll(singularOptions,
                "-c", "--enable-rpc", "--referer=*",
                "--bt-save-metadata=true",
                "--rpc-allow-origin-all=true",
                "--rpc-save-upload-metadata=true",
                "--save-session-interval=10");
    }

    public Config setShowStoppedNf(boolean showStoppedNf) {
        this.showStoppedNf = showStoppedNf;
        return this;
    }

    public Config setUseATE(boolean useATE) {
        this.useATE = useATE;
        return this;
    }

    public Config setShowOutput(boolean showOutput) {
        this.showOutput = showOutput;
        return this;
    }

    public Config setTakeWakelock(boolean takeWakelock) {
        this.takeWakelock = takeWakelock;
        return this;
    }

    public Config setListenAll(boolean listenAll) {
        this.listenAll = listenAll;
        return this;
    }

    public Intent putInto(Intent container) {
        return container.putExtra(EXTRA_NAME, this);
    }

    @SuppressWarnings("unchecked")
    public static Config from(Intent container) {
        return container.getParcelableExtra(Config.EXTRA_NAME);
    }

    public Config setProcessname(String processname) {
        binaryName = processname;
        return this;
    }

    public Config setSessionPath(File sessionFile) {
        final String fileName = sessionFile.getAbsolutePath();

        final File sessionParent = sessionFile.getParentFile();
        //noinspection ResultOfMethodCallIgnored
        sessionParent.mkdirs();

        final File configFile = new File(sessionParent, CONFIG_FILE_NAME);
        //noinspection ResultOfMethodCallIgnored
        try {
            configFile.createNewFile();
        } catch (IOException ignored) {}

        sessionDir = sessionParent;
        this.sessionFile = sessionFile;
        this.configFile = configFile;

        return this;
    }

    public Config setRPCSecret(String secret) {
        this.secret = secret;
        return this;
    }

    public Config setNetworkInterface(String networkInterface) {
        this.networkInterface = networkInterface;
        return this;
    }

    @Override
    public String toString() {
        return Arrays.toString(toCommand());
    }

    public String[] toCommand() {
        final ArrayList<String> options = new ArrayList<>(22);

        options.add(binaryName);
        options.addAll(singularOptions);

        Collections.addAll(options, "-d", sessionDir.getAbsolutePath());
        Collections.addAll(options, "--save-session", sessionFile.getAbsolutePath());
        Collections.addAll(options, "--conf-path", configFile.getAbsolutePath());

        if (sessionFile.exists()) {
            Collections.addAll(options, "-i", sessionFile.getAbsolutePath());
        }

        if (!TextUtils.isEmpty(secret)) {
            Collections.addAll(options, "--rpc-secret", secret);
        }

        if (!useATE) {
            options.add("--show-console-readout=false");

            options.add("--summary-interval=0");

            if (!showOutput) {
                options.add("-q");
            }
        } else {
            options.add("--show-console-readout=true");
        }

        if (showOutput || !useATE) {
            options.add("--enable-color=false");
        }

        if (listenAll) {
            options.add("--rpc-listen-all=true");
        }

        return options.toArray(new String[options.size()]);
    }

    @Override
    public int describeContents() {
        return -1;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(binaryName);
        dest.writeString(secret);
        dest.writeString(sessionFile.getAbsolutePath());
        dest.writeString(networkInterface);
        dest.writeInt(showStoppedNf ? 1 : 0);
        dest.writeInt(useATE ? 1 : 0);
        dest.writeInt(showOutput ? 1 : 0);
        dest.writeInt(takeWakelock ? 1 : 0);
        dest.writeInt(listenAll ? 1 : 0);
    }

    public static final Parcelable.Creator<Config> CREATOR = new Creator<Config>() {
        @Override
        public Config createFromParcel(Parcel source) {
            return new Config()
                    .setProcessname(source.readString())
                    .setRPCSecret(source.readString())
                    .setSessionPath(new File(source.readString()))
                    .setNetworkInterface(source.readString())
                    .setShowStoppedNf(source.readInt() != 0)
                    .setUseATE(source.readInt() != 0)
                    .setShowOutput(source.readInt() != 0)
                    .setTakeWakelock(source.readInt() != 0)
                    .setListenAll(source.readInt() != 0);
        }

        @Override
        public Config[] newArray(int size) {
            return new Config[size];
        }
    };
}
