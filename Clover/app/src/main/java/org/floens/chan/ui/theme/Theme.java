/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
 */
package org.floens.chan.ui.theme;

import android.content.res.Resources;
import android.content.res.TypedArray;

import org.floens.chan.R;
import org.floens.chan.utils.AndroidUtils;

/**
 * A Theme<br>
 * Used for setting the toolbar color, and passed around {@link org.floens.chan.chan.ChanParser} to give the spans the correct color.<br>
 * Technically should the parser not do UI, but it is important that the spans do not get created on an UI thread for performance.
 */
public class Theme {
    public final String displayName;
    public final String name;
    public final int resValue;
    public final boolean isLightTheme;
    public final ThemeHelper.PrimaryColor primaryColor;

    public int quoteColor;
    public int highlightQuoteColor;
    public int linkColor;
    public int spoilerColor;
    public int inlineQuoteColor;
    public int subjectColor;
    public int nameColor;
    public int idBackgroundLight;
    public int idBackgroundDark;
    public int capcodeColor;

    public Theme(String displayName, String name, int resValue, boolean isLightTheme, ThemeHelper.PrimaryColor primaryColor) {
        this.displayName = displayName;
        this.name = name;
        this.resValue = resValue;
        this.isLightTheme = isLightTheme;
        this.primaryColor = primaryColor;

        resolveSpanColors();
    }

    private void resolveSpanColors() {
        Resources.Theme theme = AndroidUtils.getAppRes().getResources().newTheme();
        theme.applyStyle(R.style.Chan_Theme, true);
        theme.applyStyle(resValue, true);

        TypedArray ta = theme.obtainStyledAttributes(new int[]{
                R.attr.post_quote_color,
                R.attr.post_highlight_quote_color,
                R.attr.post_link_color,
                R.attr.post_spoiler_color,
                R.attr.post_inline_quote_color,
                R.attr.post_subject_color,
                R.attr.post_name_color,
                R.attr.post_id_background_light,
                R.attr.post_id_background_dark,
                R.attr.post_capcode_color
        });

        quoteColor = ta.getColor(0, 0);
        highlightQuoteColor = ta.getColor(1, 0);
        linkColor = ta.getColor(2, 0);
        spoilerColor = ta.getColor(3, 0);
        inlineQuoteColor = ta.getColor(4, 0);
        subjectColor = ta.getColor(5, 0);
        nameColor = ta.getColor(6, 0);
        idBackgroundLight = ta.getColor(7, 0);
        idBackgroundDark = ta.getColor(8, 0);
        capcodeColor = ta.getColor(9, 0);

        ta.recycle();
    }
}
