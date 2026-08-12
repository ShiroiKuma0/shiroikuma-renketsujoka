package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;
import android.graphics.PorterDuff;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;

/**
 * Paints an options menu in the house colours.
 *
 * <p>The popup is a separate window, so the app-wide view walk cannot reach it, and the theme's
 * popup attributes are honoured inconsistently across OEM skins — this device ignored
 * {@code actionBarStyle} outright. Colouring each item's title with a span works regardless of
 * either, so the two together are belt and braces.
 */
public class SkMenu {

    public static void tint(Menu menu, Context cntx) {
        if (menu == null) return;
        int yellow = ShiroikumaUi.BODY_COLOR(cntx).get();
        for (int i = 0; i < menu.size(); i++) {
            var item = menu.getItem(i);
            var title = item.getTitle();
            if (title != null && !(title instanceof SpannableString)) {
                var spanned = new SpannableString(title);
                spanned.setSpan(new ForegroundColorSpan(yellow), 0, spanned.length(), 0);
                item.setTitle(spanned);
            }
            var icon = item.getIcon();
            if (icon != null) icon.mutate().setColorFilter(yellow, PorterDuff.Mode.SRC_IN);
            if (item.hasSubMenu()) tint(item.getSubMenu(), cntx);
        }
    }

    /** Convenience for the common {@code onCreateOptionsMenu} tail. */
    public static boolean tinted(Menu menu, Context cntx) {
        tint(menu, cntx);
        return true;
    }

    private SkMenu() {
    }
}
