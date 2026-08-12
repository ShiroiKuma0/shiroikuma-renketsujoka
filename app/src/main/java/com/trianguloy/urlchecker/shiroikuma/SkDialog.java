package com.trianguloy.urlchecker.shiroikuma;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.view.ContextThemeWrapper;

import com.trianguloy.urlchecker.R;

/**
 * The house AlertDialog builder — a drop-in for {@code new AlertDialog.Builder(context)}.
 *
 * <p>A dialog is its own window, so neither the Application-level restyle (which only sees
 * Activities) nor a screen's view tree ever reaches it. They came out in the platform's own grey
 * with white text and blue buttons.
 *
 * <p>Fixed in the builder rather than at each of the app's two dozen call sites, so a dialog added
 * later — by us, or by upstream on a rebase — is styled by construction. Two mechanisms, because
 * this device has ignored every theme attribute the fork has relied on so far:
 *
 * <ol>
 *   <li>the builder is constructed on a {@link ContextThemeWrapper} carrying the house dialog theme;
 *   <li>{@link #create()} attaches a show-listener that paints the window and walks the decor,
 *       which works regardless of what the OEM skin does with the theme.
 * </ol>
 *
 * <p>Turning the master switch off leaves the platform dialog exactly as it was.
 */
public class SkDialog extends AlertDialog.Builder {

    private final Context base;

    public SkDialog(Context cntx) {
        super(themed(cntx));
        this.base = cntx;
    }

    private static Context themed(Context cntx) {
        if (!ShiroikumaUi.ENABLED(cntx).get()) return cntx;
        return new ContextThemeWrapper(cntx, R.style.ShiroikumaAlertDialog);
    }

    /**
     * {@code Builder.show()} routes through here, so overriding this alone covers both entry points.
     */
    @Override
    public AlertDialog create() {
        var dialog = super.create();
        if (!ShiroikumaUi.ENABLED(base).get()) return dialog;

        dialog.setOnShowListener(shown -> {
            style(dialog, base);
            // The action buttons exist only once the dialog is shown, so they are set here rather
            // than by the walk, which runs before they are created.
            int yellow = ShiroikumaUi.BUTTON_TEXT(base).get();
            for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                    AlertDialog.BUTTON_NEUTRAL}) {
                var button = dialog.getButton(which);
                if (button != null) {
                    button.setTextColor(yellow);
                    button.setAllCaps(false);
                    button.setTypeface(ShiroikumaUi.weighted(
                            Fonts.typeface(base, ShiroikumaUi.BODY_FONT(base).get()),
                            ShiroikumaUi.BODY_WEIGHT(base).get()));
                }
            }
        });
        return dialog;
    }

    /**
     * Paint ANY dialog, including ones this builder did not create — {@code ProgressDialog} is its
     * own class and never passes through an {@code AlertDialog.Builder}, so it calls this directly.
     *
     * <p>Safe to call repeatedly, and a no-op when the master switch is off.
     */
    public static void style(Dialog dialog, Context base) {
        if (!ShiroikumaUi.ENABLED(base).get()) return;
        var window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(ShiroikumaUi.box(base,
                ShiroikumaUi.SURFACE(base).get(),
                Math.max(ShiroikumaUi.dp(base, 2),
                        ShiroikumaUi.dp(base, ShiroikumaUi.BORDER(base).get())),
                ShiroikumaUi.BORDER_COLOR(base).get(),
                ShiroikumaUi.CORNER(base).get()));
        // flatButtons: a dialog's actions are borderless by convention, and boxing them would put
        // a row of pills along the bottom.
        ShiroikumaApp.styleTree(base, window.getDecorView(), true);
    }
}
