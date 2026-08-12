package com.trianguloy.urlchecker.shiroikuma;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Build;
import android.widget.AbsSeekBar;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Carries the fork look out of the UI page and across the whole app.
 *
 * <p>Hooking this at the Application level rather than editing each Activity is deliberate: every
 * screen is covered — including the link dialog, which is the one that matters most — and there is
 * no per-activity patch to re-apply on each upstream rebase. Turning the master switch off makes
 * this a no-op and upstream's own styling returns untouched.
 *
 * <p>What it changes is colour, typeface and weight — not layout. Sizes are left as upstream set
 * them, except that body text scales with the size attribute relative to its own value, so a
 * heading upstream drew larger stays larger.
 */
public class ShiroikumaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                // Post it: some screens finish populating their lists after resume, and the walk is
                // cheap enough to run once the frame is settled.
                var root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
                if (root != null) root.post(() -> apply(activity, root));
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    /**
     * Restyle a screen. Our own pages already draw themselves from these attributes and are skipped,
     * so the walk only touches upstream's views.
     */
    private static void apply(Activity activity, View root) {
        if (!ShiroikumaUi.ENABLED(activity).get()) return;
        var name = activity.getClass().getName();
        if (name.endsWith("ShiroikumaUiActivity") || name.endsWith("ExportImportActivity")) return;

        // The link dialog's window background is the yellow-bordered panel from the theme — painting
        // a flat colour over it here would erase the border it exists to draw.
        if (!name.endsWith("MainDialog") && activity.getWindow() != null) {
            activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    ShiroikumaUi.BACKGROUND(activity).get()));
        }
        var actionBar = activity.getActionBar();
        if (actionBar != null) {
            // The theme's actionBarStyle is overridden by the device's own DeviceDefault, so the bar
            // is painted here instead. One place, and every top bar in the app follows.
            actionBar.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    ShiroikumaUi.BACKGROUND(activity).get()));
        }
        walk(activity, root);
    }

    private static void walk(Activity activity, View view) {
        int yellow = ShiroikumaUi.BODY_COLOR(activity).get();

        // Our own launcher icon is already black-yellow; tinting an opaque icon with SRC_IN just
        // floods it into a featureless yellow square.
        if ("sk_logo".equals(view.getTag())) return;

        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) walk(activity, group.getChildAt(i));
            return;
        }
        // ORDER MATTERS: CompoundButton extends Button, and Switch extends CompoundButton, so the
        // narrower type has to be tested first. Testing Button first swallowed every switch — they
        // got a button's pill background and never their own drawables.
        if (view instanceof CompoundButton toggle) {
            // Switch track/thumb and checkbox boxes: the platform tints these from the theme, but a
            // drawable set in a layout overrides that, so tint them here too.
            toggle.setTextColor(yellow);
            toggle.setTypeface(typeface(activity));
            toggle.setButtonTintList(ColorStateList.valueOf(yellow));
            if (toggle instanceof android.widget.Switch sw) styleSwitch(activity, sw, yellow);
            tintCompound(toggle, yellow);
            return;
        }
        if (view instanceof Button button) {
            button.setTextColor(ShiroikumaUi.BUTTON_TEXT(activity).get());
            button.setBackground(ShiroikumaUi.box(activity,
                    ShiroikumaUi.BUTTON_BG(activity).get(),
                    ShiroikumaUi.dp(activity, ShiroikumaUi.BUTTON_BORDER(activity).get()),
                    ShiroikumaUi.BORDER_COLOR(activity).get(),
                    ShiroikumaUi.BUTTON_CORNER(activity).get()));
            button.setTypeface(typeface(activity));
            tintCompound(button, yellow);
            return;
        }
        if (view instanceof AbsSeekBar seek) {
            var tint = ColorStateList.valueOf(yellow);
            seek.setProgressTintList(tint);
            seek.setThumbTintList(tint);
            seek.setProgressBackgroundTintList(
                    ColorStateList.valueOf(ShiroikumaUi.SECONDARY_COLOR(activity).get()));
            return;
        }
        if (view instanceof ProgressBar bar) {
            bar.setProgressTintList(ColorStateList.valueOf(yellow));
            bar.setIndeterminateTintList(ColorStateList.valueOf(yellow));
            return;
        }
        if (view instanceof ImageView image) {
            if (image.getDrawable() == null) {
                // A src-less ImageView in these layouts is a rule — the vertical dividers between a
                // module row's buttons are exactly this, and they came in platform grey.
                view.setBackgroundColor(ShiroikumaUi.SEPARATOR_COLOR(activity).get());
                return;
            }
            tintGlyph(image, yellow);
            return;
        }
        if (view instanceof EditText edit) {
            edit.setTextColor(yellow);
            edit.setHintTextColor(ShiroikumaUi.SECONDARY_COLOR(activity).get());
            edit.setTypeface(typeface(activity));
            tintCompound(edit, yellow);
            return;
        }
        if (view instanceof TextView text) {
            // Links included: upstream drew them in the platform blue, which is the one colour the
            // fork does not have. They stay underlined, so they are still readable as links.
            text.setTextColor(yellow);
            text.setLinkTextColor(yellow);
            text.setTypeface(typeface(activity));
            tintCompound(text, yellow);
            return;
        }
        // A bare thin View is a divider or separator in this app's layouts.
        if (view.getClass() == View.class) {
            int height = view.getHeight();
            int width = view.getWidth();
            boolean hairline = (height > 0 && height <= ShiroikumaUi.dp(activity, 3))
                    || (width > 0 && width <= ShiroikumaUi.dp(activity, 3));
            if (hairline) view.setBackgroundColor(ShiroikumaUi.SEPARATOR_COLOR(activity).get());
        }
    }

    /**
     * A switch in the house style. Tinting the stock drawables cannot express this — both stock
     * shapes are solid — so the thumb and track are built here:
     * ON is a filled yellow dot, OFF is a traced one, and the track is black with a yellow border.
     */
    private static void styleSwitch(Activity activity, android.widget.Switch sw, int yellow) {
        int fill = ShiroikumaUi.SURFACE(activity).get();
        int border = ShiroikumaUi.BORDER_COLOR(activity).get();
        int stroke = Math.max(ShiroikumaUi.dp(activity, 2),
                ShiroikumaUi.dp(activity, ShiroikumaUi.BORDER(activity).get()));
        int thumb = ShiroikumaUi.dp(activity, 20);

        var on = new android.graphics.drawable.GradientDrawable();
        on.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        on.setColor(yellow);
        on.setSize(thumb, thumb);

        var off = new android.graphics.drawable.GradientDrawable();
        off.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        off.setColor(fill);
        off.setStroke(stroke, border);
        off.setSize(thumb, thumb);

        var thumbStates = new android.graphics.drawable.StateListDrawable();
        thumbStates.addState(new int[]{android.R.attr.state_checked}, on);
        thumbStates.addState(android.util.StateSet.WILD_CARD, off);

        int trackHeight = ShiroikumaUi.dp(activity, 22);
        var track = new android.graphics.drawable.GradientDrawable();
        track.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        track.setCornerRadius(trackHeight / 2f);
        track.setColor(fill);
        track.setStroke(stroke, border);
        track.setSize(ShiroikumaUi.dp(activity, 38), trackHeight);

        // The tint lists must go, or they repaint whatever we just built.
        sw.setThumbTintList(null);
        sw.setTrackTintList(null);
        sw.setThumbDrawable(thumbStates);
        sw.setTrackDrawable(track);
    }

    /** Compound drawables (the icon beside a button's or row's text). */
    private static void tintCompound(TextView view, int color) {
        for (var drawable : view.getCompoundDrawables()) {
            if (drawable != null) drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            for (var drawable : view.getCompoundDrawablesRelative()) {
                if (drawable != null) drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    /**
     * Tint every image glyph, launcher icons included (白い熊, 2026-08-12).
     *
     * <p>The Open module lists other apps' icons; those are bitmaps and adaptive icons rather than
     * vectors, and they are tinted too, so nothing on screen escapes the palette. Apps are then told
     * apart by their name and their icon's SHAPE rather than by colour.
     */
    private static void tintGlyph(ImageView image, int color) {
        if (image.getDrawable() == null) return;
        image.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private static android.graphics.Typeface typeface(Activity activity) {
        return ShiroikumaUi.weighted(
                Fonts.typeface(activity, ShiroikumaUi.BODY_FONT(activity).get()),
                ShiroikumaUi.BODY_WEIGHT(activity).get());
    }
}
