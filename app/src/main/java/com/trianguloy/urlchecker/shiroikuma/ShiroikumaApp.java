package com.trianguloy.urlchecker.shiroikuma;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
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

        var background = ShiroikumaUi.BACKGROUND(activity).get();
        if (activity.getWindow() != null) {
            activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
        }
        walk(activity, root);
    }

    private static void walk(Activity activity, View view) {
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) walk(activity, group.getChildAt(i));
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
            return;
        }
        if (view instanceof CompoundButton toggle) {
            toggle.setTextColor(ShiroikumaUi.BODY_COLOR(activity).get());
            toggle.setTypeface(typeface(activity));
            return;
        }
        if (view instanceof EditText edit) {
            edit.setTextColor(ShiroikumaUi.BODY_COLOR(activity).get());
            edit.setHintTextColor(ShiroikumaUi.SECONDARY_COLOR(activity).get());
            edit.setTypeface(typeface(activity));
            return;
        }
        if (view instanceof TextView text) {
            // A clickable TextView is a link in this app; leave those their own colour so they stay
            // recognisable as links rather than melting into body text.
            if (!text.isClickable()) text.setTextColor(ShiroikumaUi.BODY_COLOR(activity).get());
            text.setTypeface(typeface(activity));
        }
    }

    private static android.graphics.Typeface typeface(Activity activity) {
        return ShiroikumaUi.weighted(
                Fonts.typeface(activity, ShiroikumaUi.BODY_FONT(activity).get()),
                ShiroikumaUi.BODY_WEIGHT(activity).get());
    }
}
