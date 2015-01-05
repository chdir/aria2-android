package net.sf.aria2.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import net.sf.aria2.R;
import org.jraf.android.backport.switchwidget.SwitchPreference;

/**
 * Work around for the fact, that system widget does not allow handling onClick at all
 */
public class SaneSwitch extends SwitchPreference {
    public SaneSwitch(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context,  attrs, defStyleAttr);
    }

    public SaneSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SaneSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SaneSwitch(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        if (getOnPreferenceClickListener().onPreferenceClick(this))
            return;

        boolean newValue = !isChecked();

        if (!callChangeListener(newValue)) {
            return;
        }

        setChecked(newValue);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        final View v = view.findViewById(R.id.switchWidget);
        v.setFocusableInTouchMode(false);
        v.setFocusable(false);
        v.setClickable(false);
    }
}
