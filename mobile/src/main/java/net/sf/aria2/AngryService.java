package net.sf.aria2;

import android.content.Context;
import android.content.ContextWrapper;

/**
 * This class takes care to tell user, that he is wrong, and explains how and why
 */
abstract class AngryService extends ContextWrapper {
    public AngryService(Context base) {
        super(base);
    }

    abstract void annoyUser(String reason);
}
