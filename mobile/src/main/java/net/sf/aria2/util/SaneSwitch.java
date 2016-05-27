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
package net.sf.aria2.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.CompoundButton;

import net.sf.aria2.R;

import org.jraf.android.backport.switchwidget.Switch;
import org.jraf.android.backport.switchwidget.SwitchPreference;

/**
 * Work around for the fact, that system widget does not allow handling onClick at all
 */
public class SaneSwitch extends SwitchPreference {
    public SaneSwitch(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context,  attrs, defStyleAttr);
    }

    /**
     * Construct a new SwitchPreference with the given style options.
     *
     * @param context
     *            The Context that will style this preference
     * @param attrs
     *            Style attributes that differ from the default
     * @param defStyle
     *            Theme attribute defining the default style options
     */
    public SaneSwitch(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Construct a new SwitchPreference with the given style options.
     *
     * @param context
     *            The Context that will style this preference
     * @param attrs
     *            Style attributes that differ from the default
     */
    public SaneSwitch(Context context, AttributeSet attrs) {
        this(context, attrs, org.jraf.android.backport.switchwidget.R.attr.asb_switchPreferenceStyle);
    }

    /**
     * Construct a new SwitchPreference with default style options.
     *
     * @param context
     *            The Context that will style this preference
     */
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

        Switch checkableView = (Switch) view.findViewById(R.id.switchWidget);

        if (checkableView != null) {
            checkableView.setFocusableInTouchMode(false);
            checkableView.setFocusable(false);
            checkableView.setClickable(false);
        }
    }
}
