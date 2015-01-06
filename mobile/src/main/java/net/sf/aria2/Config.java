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
