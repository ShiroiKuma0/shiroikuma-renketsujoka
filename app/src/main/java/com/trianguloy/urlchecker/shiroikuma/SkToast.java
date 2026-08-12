package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The house toast: black, yellow text, yellow border — rather than the platform's white pill, which
 * is the one thing on screen that ignores the theme entirely.
 *
 * <p>Custom toast views are deprecated from API 30 and are dropped when the app is in the
 * BACKGROUND. Every toast in this app is raised from a screen the user is looking at, so the custom
 * view shows; if the platform ever refuses it, {@link #show} falls back to a plain text toast, which
 * is the same message in the system's own styling rather than no message at all.
 */
public class SkToast {

    public static void show(Context cntx, CharSequence text, int duration) {
        var toast = Toast.makeText(cntx, text, duration);
        try {
            var view = new TextView(cntx);
            view.setText(text);
            view.setTextColor(ShiroikumaUi.BODY_COLOR(cntx).get());
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShiroikumaUi.BODY_SIZE(cntx).get());
            view.setTypeface(ShiroikumaUi.weighted(
                    Fonts.typeface(cntx, ShiroikumaUi.BODY_FONT(cntx).get()),
                    ShiroikumaUi.BODY_WEIGHT(cntx).get()));
            view.setGravity(Gravity.CENTER);
            int padH = ShiroikumaUi.dp(cntx, 20);
            int padV = ShiroikumaUi.dp(cntx, 12);
            view.setPadding(padH, padV, padH, padV);
            view.setBackground(ShiroikumaUi.box(cntx,
                    ShiroikumaUi.SURFACE(cntx).get(),
                    Math.max(ShiroikumaUi.dp(cntx, 2),
                            ShiroikumaUi.dp(cntx, ShiroikumaUi.BORDER(cntx).get())),
                    ShiroikumaUi.BORDER_COLOR(cntx).get(),
                    ShiroikumaUi.BUTTON_CORNER(cntx).get()));
            toast.setView(view);
        } catch (Exception ignored) {
            // the platform refused the custom view; the plain toast above still carries the message
        }
        toast.show();
    }

    public static void show(Context cntx, int textResource, int duration) {
        show(cntx, cntx.getString(textResource), duration);
    }
}
