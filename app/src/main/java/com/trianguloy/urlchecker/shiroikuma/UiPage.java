package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * The kxkb page grammar, in plain framework views.
 *
 * <p>Every heading is a big bold heading with an underline only as wide as its own text; groups
 * after the first are opened by a full-width hairline; rows indent one step per nesting level and
 * carry tight vertical padding. All four of those are themselves settings, so the page is drawn by
 * the same attributes it edits — change one and the page you are standing on repaints.
 */
public class UiPage {

    /**
     * Marks a view this class has already coloured. The app-wide restyle skips these, so the red
     * "not set" notice, the dim summaries and the live preview keep saying what they mean instead of
     * being flattened to body colour.
     */
    static <T extends View> T mark(T view) {
        view.setTag("sk_styled");
        return view;
    }

    private final Context cntx;
    private final LinearLayout root;
    private boolean firstHeading = true;

    public UiPage(Context cntx, LinearLayout root) {
        this.cntx = cntx;
        this.root = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ShiroikumaUi.BACKGROUND(cntx).get());
    }

    private int indent(int level) {
        return ShiroikumaUi.dp(cntx, ShiroikumaUi.INDENT(cntx).get() * level);
    }

    /* ------------------- headings ------------------- */

    /** A top-level group heading. The first one gets no hairline above it. */
    public void heading(String title) {
        var group = new LinearLayout(cntx);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, ShiroikumaUi.dp(cntx, firstHeading ? 12 : 10), 0, ShiroikumaUi.dp(cntx, 2));

        if (!firstHeading) {
            int thickness = ShiroikumaUi.SEPARATOR(cntx).get();
            if (thickness > 0) {
                var line = new View(cntx);
                line.setBackgroundColor(ShiroikumaUi.SEPARATOR_COLOR(cntx).get());
                group.addView(line, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, thickness));
            }
        }
        firstHeading = false;

        // wrap_content so the underline below can be exactly as wide as the text
        var block = new LinearLayout(cntx);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(indent(1), ShiroikumaUi.dp(cntx, 8), 0, 0);

        var text = new TextView(cntx);
        text.setText(title);
        text.setTextColor(ShiroikumaUi.HEADING_COLOR(cntx).get());
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShiroikumaUi.HEADING_SIZE(cntx).get());
        text.setTypeface(ShiroikumaUi.weighted(
                Fonts.typeface(cntx, ShiroikumaUi.HEADING_FONT(cntx).get()),
                ShiroikumaUi.HEADING_WEIGHT(cntx).get()));
        mark(text);
        block.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        int underline = ShiroikumaUi.HEADING_UNDERLINE(cntx).get();
        if (underline > 0) {
            var rule = new View(cntx);
            rule.setBackgroundColor(ShiroikumaUi.HEADING_UNDERLINE_COLOR(cntx).get());
            var params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ShiroikumaUi.dp(cntx, underline));
            params.topMargin = ShiroikumaUi.dp(cntx, 2);
            block.addView(rule, params);
        }

        var wrapper = new LinearLayout(cntx);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(block, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        group.addView(wrapper);
        root.addView(group);
    }

    /** A sub-heading inside a group: same shape, smaller, one indent step deeper. */
    public void subHeading(String title, int level) {
        var block = new LinearLayout(cntx);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(indent(level), ShiroikumaUi.dp(cntx, 6), 0, 0);

        var text = new TextView(cntx);
        text.setText(title);
        text.setTextColor(ShiroikumaUi.HEADING_COLOR(cntx).get());
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                Math.max(12, ShiroikumaUi.HEADING_SIZE(cntx).get() - 4));
        text.setTypeface(ShiroikumaUi.weighted(
                Fonts.typeface(cntx, ShiroikumaUi.HEADING_FONT(cntx).get()),
                ShiroikumaUi.HEADING_WEIGHT(cntx).get()));
        mark(text);
        block.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        int underline = ShiroikumaUi.HEADING_UNDERLINE(cntx).get();
        if (underline > 0) {
            var rule = new View(cntx);
            rule.setBackgroundColor(ShiroikumaUi.HEADING_UNDERLINE_COLOR(cntx).get());
            var params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, ShiroikumaUi.dp(cntx, underline) / 2));
            params.topMargin = ShiroikumaUi.dp(cntx, 1);
            block.addView(rule, params);
        }

        var wrapper = new LinearLayout(cntx);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(block, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(wrapper);
    }

    /* ------------------- rows ------------------- */

    /** The base row: title (and optional summary) on the left, a widget on the right. */
    public LinearLayout row(int level, String title, String summary, View widget) {
        int pad = ShiroikumaUi.dp(cntx, ShiroikumaUi.ROW_PADDING(cntx).get());
        var row = new LinearLayout(cntx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(indent(level), pad, ShiroikumaUi.dp(cntx, 16), pad);

        var labels = new LinearLayout(cntx);
        labels.setOrientation(LinearLayout.VERTICAL);

        var titleView = new TextView(cntx);
        titleView.setText(title);
        ShiroikumaUi.body(cntx, titleView);
        labels.addView(mark(titleView));

        if (summary != null && !summary.isEmpty()) {
            var summaryView = new TextView(cntx);
            summaryView.setText(summary);
            summaryView.setTextColor(ShiroikumaUi.SECONDARY_COLOR(cntx).get());
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                    Math.max(10, ShiroikumaUi.BODY_SIZE(cntx).get() - 3));
            summaryView.setTypeface(Fonts.typeface(cntx, ShiroikumaUi.BODY_FONT(cntx).get()));
            labels.addView(mark(summaryView));
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (widget != null) {
            row.addView(widget, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        root.addView(row);
        return row;
    }

    /** A tappable row. */
    public LinearLayout clickable(int level, String title, String summary, View widget,
                                  View.OnClickListener onClick) {
        var row = row(level, title, summary, widget);
        row.setOnClickListener(onClick);
        var outValue = new android.util.TypedValue();
        cntx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);
        return row;
    }

    /** A free-standing view at a given depth (previews, buttons rows, notices). */
    public void add(int level, View view) {
        int pad = ShiroikumaUi.dp(cntx, ShiroikumaUi.ROW_PADDING(cntx).get());
        var holder = new LinearLayout(cntx);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(indent(level), pad, ShiroikumaUi.dp(cntx, 16), pad);
        holder.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(holder);
    }

    /* ------------------- widgets ------------------- */

    /** Values that read out as they slide. Minimums of 0 are meant literally — borders can vanish. */
    public interface OnValue {
        void onValue(int value);
    }

    public void slider(int level, String title, int value, int min, int max, String unit, OnValue onValue) {
        var readout = new TextView(cntx);
        readout.setTextColor(ShiroikumaUi.SECONDARY_COLOR(cntx).get());
        readout.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShiroikumaUi.BODY_SIZE(cntx).get());
        readout.setText(value + unit);
        readout.setMinWidth(ShiroikumaUi.dp(cntx, 52));
        readout.setGravity(Gravity.END);

        mark(readout);
        row(level, title, null, readout);

        var seek = new SeekBar(cntx);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, value - min)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                readout.setText((min + progress) + unit);
                if (fromUser) onValue.onValue(min + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        add(level + 1, seek);
    }

    /** A colour row: a swatch on the right that opens the RGBA picker. */
    public void color(int level, String title, int value, ColorPickerDialog.OnColor onColor) {
        var swatch = new View(cntx);
        var size = ShiroikumaUi.dp(cntx, 28);
        swatch.setLayoutParams(new LinearLayout.LayoutParams(size * 2, size));
        swatch.setBackground(ShiroikumaUi.box(cntx, value, ShiroikumaUi.dp(cntx, 1.5f),
                ShiroikumaUi.BORDER_COLOR(cntx).get(), 4));
        clickable(level, title, null, swatch,
                v -> ColorPickerDialog.show(cntx, title, value, onColor));
    }

    /** A font row: the current font's name, rendered in that font. */
    public void font(int level, String title, String value, FontPickerDialog.OnPick onPick,
                     FontPickerDialog.OnImport onImport) {
        var sample = new TextView(cntx);
        sample.setText(nameOf(value));
        sample.setTypeface(Fonts.typeface(cntx, value));
        sample.setTextColor(ShiroikumaUi.SECONDARY_COLOR(cntx).get());
        sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShiroikumaUi.BODY_SIZE(cntx).get());
        mark(sample);
        clickable(level, title, null, sample,
                v -> FontPickerDialog.show(cntx, title, value, onPick, onImport));
    }

    private String nameOf(String id) {
        for (var option : Fonts.available(cntx)) if (option.id.equals(id)) return option.name;
        return "System";
    }

    /** A pill button in the house style, sized by the button attributes. */
    public Button pill(String text, View.OnClickListener onClick) {
        var button = new Button(cntx);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(ShiroikumaUi.BUTTON_TEXT(cntx).get());
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShiroikumaUi.BODY_SIZE(cntx).get());
        button.setTypeface(Fonts.typeface(cntx, ShiroikumaUi.BODY_FONT(cntx).get()));
        button.setBackground(ShiroikumaUi.box(cntx, ShiroikumaUi.BUTTON_BG(cntx).get(),
                ShiroikumaUi.dp(cntx, ShiroikumaUi.BUTTON_BORDER(cntx).get()),
                ShiroikumaUi.BORDER_COLOR(cntx).get(),
                ShiroikumaUi.BUTTON_CORNER(cntx).get()));
        int padH = ShiroikumaUi.dp(cntx, 20);
        int padV = ShiroikumaUi.dp(cntx, 6);
        button.setPadding(padH, padV, padH, padV);
        button.setOnClickListener(onClick);
        return mark(button);
    }

    /** A bordered preview panel showing the attributes as they currently stand. */
    public View previewPanel(String sample) {
        var panel = new LinearLayout(cntx);
        panel.setOrientation(LinearLayout.VERTICAL);
        ShiroikumaUi.panel(cntx, panel);
        int pad = ShiroikumaUi.dp(cntx, 12);
        panel.setPadding(pad, pad, pad, pad);

        // wrap_content block so the underline is exactly as wide as the heading text, as on the page
        var headingBlock = new LinearLayout(cntx);
        headingBlock.setOrientation(LinearLayout.VERTICAL);

        var heading = new TextView(cntx);
        heading.setText(cntx.getString(com.trianguloy.urlchecker.R.string.sk_previewHeading));
        heading.setTextColor(ShiroikumaUi.HEADING_COLOR(cntx).get());
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShiroikumaUi.HEADING_SIZE(cntx).get());
        heading.setTypeface(ShiroikumaUi.weighted(
                Fonts.typeface(cntx, ShiroikumaUi.HEADING_FONT(cntx).get()),
                ShiroikumaUi.HEADING_WEIGHT(cntx).get()));
        mark(heading);
        headingBlock.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        int underline = ShiroikumaUi.HEADING_UNDERLINE(cntx).get();
        if (underline > 0) {
            var rule = new View(cntx);
            rule.setBackgroundColor(ShiroikumaUi.HEADING_UNDERLINE_COLOR(cntx).get());
            headingBlock.addView(rule, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ShiroikumaUi.dp(cntx, underline)));
        }
        panel.addView(headingBlock, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        var body = new TextView(cntx);
        body.setText(sample);
        ShiroikumaUi.body(cntx, body);
        panel.addView(mark(body));

        var secondary = new TextView(cntx);
        secondary.setText(cntx.getString(com.trianguloy.urlchecker.R.string.sk_previewSecondary));
        secondary.setTextColor(ShiroikumaUi.SECONDARY_COLOR(cntx).get());
        secondary.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                Math.max(10, ShiroikumaUi.BODY_SIZE(cntx).get() - 3));
        secondary.setTypeface(Fonts.typeface(cntx, ShiroikumaUi.BODY_FONT(cntx).get()));
        panel.addView(mark(secondary));

        var buttons = new LinearLayout(cntx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, ShiroikumaUi.dp(cntx, 8), 0, 0);
        buttons.addView(pill(cntx.getString(com.trianguloy.urlchecker.R.string.sk_previewButton), v -> {
        }));
        panel.addView(buttons);

        return panel;
    }

    /** A notice line — red when it reports something unset, yellow once it is fine. */
    public TextView notice(int level, String text, boolean problem) {
        var view = new TextView(cntx);
        view.setText(text);
        view.setTextColor(problem ? ShiroikumaUi.RED : ShiroikumaUi.BODY_COLOR(cntx).get());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShiroikumaUi.BODY_SIZE(cntx).get());
        view.setTypeface(Fonts.typeface(cntx, ShiroikumaUi.BODY_FONT(cntx).get()));
        add(level, mark(view));
        return view;
    }
}
