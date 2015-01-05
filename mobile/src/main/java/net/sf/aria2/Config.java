package net.sf.aria2;

import android.content.Intent;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

final class Config extends ArrayList<String> {
    private static final String EXTRA_NAME = BuildConfig.APPLICATION_ID + ".config";

    public Config() {
        super(7);
        addAll(Arrays.asList(
                "-q", "-D", "--enable-rpc",
                "--rpc-save-upload-metadata=true",
                "--save-session-interval=10"));
    }

    public Intent putInto(Intent container) {
        return container.putExtra(EXTRA_NAME, this);
    }

    @SuppressWarnings("unchecked")
    public static Config from(Intent container) {
        return (Config) container.getSerializableExtra(Config.EXTRA_NAME);
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
        add("--rpc-secret=");
        add(secret);
    }
}
