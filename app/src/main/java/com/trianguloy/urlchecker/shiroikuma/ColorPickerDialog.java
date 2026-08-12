package com.trianguloy.urlchecker.shiroikuma;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The house colour picker: a row of one-click recent-colour swatches, a live preview showing the
 * hex, and four A/R/G/B sliders.
 *
 * <p>Every change applies LIVE, so whatever the colour drives repaints while the slider moves.
 * Cancel reverts to the colour the dialog opened with; OK keeps it and remembers it as a swatch.
 */
public class ColorPickerDialog {

    private static final String PREFS = "shiroikuma_color_picker";
    private static final String KEY_RECENT = "recent";
    private static final int MAX_RECENT = 8;

    /** Receives every intermediate colour while sliding, and the final one on OK. */
    public interface OnColor {
        void onColor(int color);
    }

    public static void show(Context cntx, String title, int initial, OnColor onColor) {
        var density = cntx.getResources().getDisplayMetrics().density;
        int yellow = ShiroikumaUi.YELLOW;

        // Channel values, mutated by the sliders. A one-element array is the plain-Java way to let
        // the listener closures write back.
        final int[] argb = {Color.alpha(initial), Color.red(initial), Color.green(initial), Color.blue(initial)};
        final List<SeekBar> sliders = new ArrayList<>();

        var preview = new TextView(cntx);
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        preview.setMinHeight(Math.round(52 * density));

        var refresh = new Runnable() {
            void run(boolean apply) {
                int color = Color.argb(argb[0], argb[1], argb[2], argb[3]);
                preview.setBackgroundColor(color);
                double luminance = 0.299 * argb[1] + 0.587 * argb[2] + 0.114 * argb[3];
                preview.setTextColor(luminance < 128 || argb[0] < 128 ? Color.WHITE : Color.BLACK);
                preview.setText(String.format(Locale.US, "#%02X%02X%02X%02X",
                        argb[0], argb[1], argb[2], argb[3]));
                if (apply) onColor.onColor(color);
            }

            @Override
            public void run() {
                run(true);
            }
        };

        var layout = new LinearLayout(cntx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * density);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        // One-click recent swatches, seeded with the black-yellow staples so the row is never empty.
        var swatchRow = new LinearLayout(cntx);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(Gravity.CENTER_VERTICAL);
        int swatchSize = Math.round(32 * density);
        int gap = Math.round(6 * density);
        for (int swatch : recent(cntx)) {
            var view = new View(cntx);
            var params = new LinearLayout.LayoutParams(swatchSize, swatchSize);
            params.rightMargin = gap;
            view.setLayoutParams(params);
            var shape = new GradientDrawable();
            shape.setColor(swatch);
            shape.setStroke(Math.round(1.5f * density), yellow);
            shape.setCornerRadius(4 * density);
            view.setBackground(shape);
            view.setOnClickListener(v -> {
                argb[0] = Color.alpha(swatch);
                argb[1] = Color.red(swatch);
                argb[2] = Color.green(swatch);
                argb[3] = Color.blue(swatch);
                for (int i = 0; i < sliders.size() && i < 4; i++) sliders.get(i).setProgress(argb[i]);
                refresh.run(true);
            });
            swatchRow.addView(view);
        }
        addSpaced(layout, swatchRow, density, 18);
        addSpaced(layout, preview, density, 18);

        String[] labels = {"A", "R", "G", "B"};
        for (int channel = 0; channel < 4; channel++) {
            final int index = channel;
            var row = new LinearLayout(cntx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            var label = new TextView(cntx);
            label.setText(labels[channel]);
            label.setTextColor(yellow);
            label.setWidth(Math.round(22 * density));
            row.addView(label);

            var seek = new SeekBar(cntx);
            seek.setMax(255);
            seek.setProgress(argb[channel]);
            seek.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    argb[index] = progress;
                    refresh.run(true);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            sliders.add(seek);
            row.addView(seek);
            addSpaced(layout, row, density, channel == 3 ? 0 : 8);
        }

        refresh.run(false);

        new AlertDialog.Builder(cntx)
                .setTitle(title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int color = Color.argb(argb[0], argb[1], argb[2], argb[3]);
                    onColor.onColor(color);
                    remember(cntx, color);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> onColor.onColor(initial))
                .setOnCancelListener(dialog -> onColor.onColor(initial))
                .show();
    }

    private static void addSpaced(LinearLayout parent, View view, float density, int bottomDp) {
        var params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Math.round(bottomDp * density);
        parent.addView(view, params);
    }

    private static List<Integer> recent(Context cntx) {
        var prefs = cntx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        var result = new ArrayList<Integer>();
        var stored = prefs.getString(KEY_RECENT, null);
        if (stored != null) {
            for (var part : stored.split(",")) {
                try {
                    var value = Integer.valueOf(part);
                    if (!result.contains(value)) result.add(value);
                } catch (NumberFormatException ignored) {
                    // a malformed entry is simply skipped
                }
            }
        }
        for (int seed : new int[]{ShiroikumaUi.BLACK, ShiroikumaUi.YELLOW, ShiroikumaUi.YELLOW_DIM, 0xFFFFFFFF}) {
            if (!result.contains(seed)) result.add(seed);
        }
        return result.subList(0, Math.min(MAX_RECENT, result.size()));
    }

    private static void remember(Context cntx, int color) {
        var prefs = cntx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        var updated = new ArrayList<Integer>();
        updated.add(color);
        for (int previous : recent(cntx)) if (!updated.contains(previous)) updated.add(previous);

        var joined = new StringBuilder();
        for (int i = 0; i < Math.min(MAX_RECENT, updated.size()); i++) {
            if (i > 0) joined.append(',');
            joined.append(updated.get(i));
        }
        prefs.edit().putString(KEY_RECENT, joined.toString()).apply();
    }
}
