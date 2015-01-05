package net.sf.aria2.util;

import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;

import java.io.Closeable;

public class CloseableHandler extends Handler implements Closeable {
    private boolean closed;

    @Override
    public void close() {
        removeCallbacksAndMessages(null);
        closed = true;
    }

    @Override
    public boolean sendMessageAtTime(@NonNull Message msg, long uptimeMillis) {
        return !closed && super.sendMessageAtTime(msg, uptimeMillis);
    }
}
