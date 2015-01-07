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
