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
