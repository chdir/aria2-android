package net.sf.aria2.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.EditTextPreference;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.sf.aria2.R;

public class SanePreference extends EditTextPreference implements SharedPreferences.OnSharedPreferenceChangeListener {
    private int mColor = 0;
    private AppCompatDialog mDialog;
    private AppCompatEditText mEditText;

    public SanePreference(Context context) {
        super(context);
        init(context, null);
    }

    public SanePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SanePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SanePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }


    private void init(Context context, AttributeSet attrs) {
        //PrefUtil.setLayoutResource(context, this, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{ R.attr.colorAccent });
        try {
            mColor = a.getColor(0, -1);
        } finally {
            a.recycle();
        }

        mEditText = new AppCompatEditText(context, attrs);
        // Give it an ID so it can be saved/restored
        mEditText.setId(android.R.id.edit);
        mEditText.setEnabled(true);
    }

    @Override
    protected void onAddEditTextToDialogView(@NonNull View dialogView, @NonNull EditText editText) {
        ((ViewGroup) dialogView).addView(editText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onBindDialogView(@NonNull View view) {
        EditText editText = mEditText;
        editText.setText(getText());
        // Initialize cursor to end of text
        if (editText.getText().length() > 0)
            editText.setSelection(editText.length());
        ViewParent oldParent = editText.getParent();
        if (oldParent != view) {
            if (oldParent != null)
                ((ViewGroup) oldParent).removeView(editText);
            onAddEditTextToDialogView(view, editText);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            String value = mEditText.getText().toString();
            if (callChangeListener(value))
                setText(value);
        }
    }

    @Override
    public EditText getEditText() {
        return mEditText;
    }

    @Override
    public Dialog getDialog() {
        return mDialog;
    }

    @Override
    protected void showDialog(Bundle state) {
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(@NonNull DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        SanePreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        SanePreference.this.onClick(dialog, DialogInterface.BUTTON_NEUTRAL);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        SanePreference.this.onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
                        break;
                }
            }
        };

        AlertDialog.Builder mBuilder = new AlertDialog.Builder(getContext())
                .setTitle(getDialogTitle())
                .setIcon(getDialogIcon())
                .setPositiveButton(getPositiveButtonText(), listener)
                .setNegativeButton(getNegativeButtonText(), listener)
                .setOnDismissListener(this);

        @SuppressLint("InflateParams")
        View layout = LayoutInflater.from(getContext()).inflate(R.layout.input_pref, null);
        onBindDialogView(layout);

        ColorStateList editTextColorStateList = createEditTextColorStateList(mEditText.getContext(), mColor);
        mEditText.setSupportBackgroundTintList(editTextColorStateList);
        // TODO: set cursor tint as well?

        TextView message = (TextView) layout.findViewById(android.R.id.message);
        if (getDialogMessage() != null && getDialogMessage().toString().length() > 0) {
            message.setVisibility(View.VISIBLE);
            message.setText(getDialogMessage());
        } else {
            message.setVisibility(View.GONE);
        }
        mBuilder.setView(layout);

        PrefUtil.registerOnActivityDestroyListener(this, this);

        mDialog = mBuilder.create();
        if (state != null)
            mDialog.onRestoreInstanceState(state);
        requestInputMethod(mDialog);

        mDialog.show();
    }

    private static ColorStateList createEditTextColorStateList(@NonNull Context context, @ColorInt int color) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{ R.attr.colorControlNormal });
        try {
            final int normalColor = a.getColor(0, -1);

            int[][] states = new int[3][];
            int[] colors = new int[3];
            int i = 0;
            states[i] = new int[]{-android.R.attr.state_enabled};
            colors[i] = normalColor;
            i++;
            states[i] = new int[]{-android.R.attr.state_pressed, -android.R.attr.state_focused};
            colors[i] = normalColor;
            i++;
            states[i] = new int[]{};
            colors[i] = color;
            return new ColorStateList(states, colors);
        } finally {
            a.recycle();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        PrefUtil.unregisterOnActivityDestroyListener(this, this);
    }

    /**
     * Copied from DialogPreference.java
     */
    private void requestInputMethod(Dialog dialog) {
        Window window = dialog.getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        Dialog dialog = getDialog();
        if (dialog == null || !dialog.isShowing()) {
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.isDialogShowing = true;
        myState.dialogBundle = dialog.onSaveInstanceState();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        if (myState.isDialogShowing) {
            showDialog(myState.dialogBundle);
        }
    }

    // From DialogPreference
    private static class SavedState extends BaseSavedState {
        boolean isDialogShowing;
        Bundle dialogBundle;

        public SavedState(Parcel source) {
            super(source);
            isDialogShowing = source.readInt() == 1;
            dialogBundle = source.readBundle(getClass().getClassLoader());
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(isDialogShowing ? 1 : 0);
            dest.writeBundle(dialogBundle);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    @Override
    protected void onAttachedToHierarchy(@NonNull PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);

        preferenceManager.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onActivityDestroy() {
        if (mDialog != null && mDialog.isShowing())
            mDialog.dismiss();

        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

        super.onActivityDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getKey().equals(key)) {
            setText(sharedPreferences.getString(key, ""));
        }
    }
}
