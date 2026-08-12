package com.trianguloy.urlchecker.utilities.wrappers;

import android.app.Activity;

import com.trianguloy.urlchecker.utilities.methods.JavaUtils.Consumer;

/**
 * A wrapper around {@link android.app.ProgressDialog} with more useful functions
 * TODO: allow to cancel
 */
public class ProgressDialog extends android.app.ProgressDialog {

    /**
     * Usage:
     * <pre>
     *     ProgressDialog.run(cntx, R.string.text, progress -> {
     *         // do things
     *     });
     * </pre>
     */
    public static void run(Activity context, int title, Consumer<ProgressDialog> consumer) {
        new ProgressDialog(context, title, consumer);
    }

    private final Activity cntx;

    /** Constructs and shows the dialog */
    private ProgressDialog(Activity context, int title, Consumer<ProgressDialog> consumer) {
        super(context);
        cntx = context;
        setTitle(title); // can't be changed later
        setMessage(""); // otherwise the message view can't be changed later
        setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL); // with spinner, indeterminate mode can't be changed
        setCancelable(false); // disable cancelable by default
        setCanceledOnTouchOutside(false);

        // show & start
        show();
        // A ProgressDialog is not built through AlertDialog.Builder, so SkDialog never sees it —
        // style it directly. Posted as well as called, because the message view is created lazily
        // and may not exist yet at the moment show() returns.
        com.trianguloy.urlchecker.shiroikuma.SkDialog.style(this, context);
        if (getWindow() != null) {
            getWindow().getDecorView().post(
                    () -> com.trianguloy.urlchecker.shiroikuma.SkDialog.style(this, context));
        }
        new Thread(() -> {
            try {
                consumer.accept(this);
            } finally {
                dismiss();
            }
        }).start();
    }

    /** progress++ */
    public void increaseProgress() {
        setProgress(getProgress() + 1);
    }

    /** sets max value and resets to 0 */
    @Override
    public void setMax(int max) {
        super.setMax(max);
        setProgress(0);
    }

    /** Changes the message from any thread */
    @Override
    public void setMessage(CharSequence message) {
        cntx.runOnUiThread(() -> {
            super.setMessage(message);
            com.trianguloy.urlchecker.shiroikuma.SkDialog.style(this, cntx);
        });
    }
}
