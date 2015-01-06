package net.sf.aria2;

import android.os.ResultReceiver;

interface IAria2 {
    boolean isRunning();

    void askToStop();

    void setResultReceiver(in ResultReceiver backLink);
}
