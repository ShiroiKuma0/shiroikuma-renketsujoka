package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

import com.trianguloy.urlchecker.utilities.generics.GenericPref.BoolPref;
import com.trianguloy.urlchecker.utilities.generics.GenericPref.IntPref;
import com.trianguloy.urlchecker.utilities.generics.GenericPref.StringPref;

import java.util.ArrayList;
import java.util.List;

/**
 * Every attribute the 白い熊 連結浄化 UI page exposes, with its black-yellow default.
 *
 * <p>One class holds the whole fork look so it is auditable in one place, and so export/import has a
 * single list to walk ({@link #all(Context)}). Nothing here touches upstream's own preferences —
 * turning {@link #ENABLED} off leaves stock chrome exactly as upstream draws it.
 */
public class ShiroikumaUi {

    /** The house palette. */
    public static final int BLACK = 0xFF000000;
    public static final int YELLOW = 0xFFFFFF00;
    /**
     * The secondary tone. Everything structural or interactive is {@link #YELLOW}; this is only for
     * de-emphasis — summaries, readouts, hints, subtitles and the unfilled half of a control — where
     * having two weights of yellow is what makes a screen readable at a glance rather than a wall of
     * one colour (白い熊, 2026-08-12).
     */
    public static final int YELLOW_DIM = 0xFFC8C800;
    /** Used for "not set yet" states, which must read as a problem rather than as chrome. */
    public static final int RED = 0xFFFF4040;

    private static final String P = "shiroikuma_ui_";

    /* ------------------- master ------------------- */

    /** When off, none of this is applied and upstream's own look comes back untouched. */
    public static BoolPref ENABLED(Context c) {
        return new BoolPref(P + "enabled", true, c);
    }

    /* ------------------- theme ------------------- */

    public static IntPref BACKGROUND(Context c) {
        return new IntPref(P + "background", BLACK, c);
    }

    public static IntPref SURFACE(Context c) {
        return new IntPref(P + "surface", BLACK, c);
    }

    /* ------------------- body text ------------------- */

    /** Font file name inside {@link Fonts}, or "" for the system typeface. */
    public static StringPref BODY_FONT(Context c) {
        return new StringPref(P + "bodyFont", "", c);
    }

    public static IntPref BODY_SIZE(Context c) {
        return new IntPref(P + "bodySize", 16, c);
    }

    /** 0–4; mapped to a real weight on API 28+, thresholded to normal/bold below it. */
    public static IntPref BODY_WEIGHT(Context c) {
        return new IntPref(P + "bodyWeight", 2, c);
    }

    public static IntPref BODY_COLOR(Context c) {
        return new IntPref(P + "bodyColor", YELLOW, c);
    }

    public static IntPref SECONDARY_COLOR(Context c) {
        return new IntPref(P + "secondaryColor", YELLOW_DIM, c);
    }

    /* ------------------- headings ------------------- */

    public static StringPref HEADING_FONT(Context c) {
        return new StringPref(P + "headingFont", "", c);
    }

    public static IntPref HEADING_SIZE(Context c) {
        return new IntPref(P + "headingSize", 20, c);
    }

    public static IntPref HEADING_WEIGHT(Context c) {
        return new IntPref(P + "headingWeight", 4, c);
    }

    public static IntPref HEADING_COLOR(Context c) {
        return new IntPref(P + "headingColor", YELLOW, c);
    }

    /** Word-width underline under a heading. 0 removes it. */
    public static IntPref HEADING_UNDERLINE(Context c) {
        return new IntPref(P + "headingUnderline", 3, c);
    }

    public static IntPref HEADING_UNDERLINE_COLOR(Context c) {
        return new IntPref(P + "headingUnderlineColor", YELLOW, c);
    }

    /* ------------------- borders & shape ------------------- */

    /** 0 removes borders entirely. */
    public static IntPref BORDER(Context c) {
        return new IntPref(P + "border", 2, c);
    }

    public static IntPref BORDER_COLOR(Context c) {
        return new IntPref(P + "borderColor", YELLOW, c);
    }

    /** 0 is a hard rectangle. */
    public static IntPref CORNER(Context c) {
        return new IntPref(P + "corner", 8, c);
    }

    /* ------------------- rows & spacing ------------------- */

    /** Vertical padding inside a row. Tight by default — the whole point of the page. */
    public static IntPref ROW_PADDING(Context c) {
        return new IntPref(P + "rowPadding", 5, c);
    }

    /** How much each nesting level indents. Heading sits at one step, its rows at two. */
    public static IntPref INDENT(Context c) {
        return new IntPref(P + "indent", 36, c);
    }

    /** The hairline between top-level groups. 0 removes it. */
    public static IntPref SEPARATOR(Context c) {
        return new IntPref(P + "separator", 1, c);
    }

    public static IntPref SEPARATOR_COLOR(Context c) {
        return new IntPref(P + "separatorColor", YELLOW, c);
    }

    /* ------------------- buttons ------------------- */

    public static IntPref BUTTON_BG(Context c) {
        return new IntPref(P + "buttonBg", BLACK, c);
    }

    public static IntPref BUTTON_TEXT(Context c) {
        return new IntPref(P + "buttonText", YELLOW, c);
    }

    /** Pill buttons at high values; 0 is a hard rectangle. */
    public static IntPref BUTTON_CORNER(Context c) {
        return new IntPref(P + "buttonCorner", 24, c);
    }

    public static IntPref BUTTON_BORDER(Context c) {
        return new IntPref(P + "buttonBorder", 2, c);
    }

    /* ------------------- the whole set ------------------- */

    /** A settable attribute, as export/import and "reset all" see it. */
    public static class Attr {
        public final String key;
        public final Object pref;

        Attr(String key, Object pref) {
            this.key = key;
            this.pref = pref;
        }
    }

    /**
     * Every attribute above, in page order. Export/import walks this, so a new attribute is backed
     * up the moment it is added here — there is no second list to keep in step.
     */
    public static List<Attr> all(Context c) {
        var list = new ArrayList<Attr>();
        list.add(new Attr("enabled", ENABLED(c)));
        list.add(new Attr("background", BACKGROUND(c)));
        list.add(new Attr("surface", SURFACE(c)));
        list.add(new Attr("bodyFont", BODY_FONT(c)));
        list.add(new Attr("bodySize", BODY_SIZE(c)));
        list.add(new Attr("bodyWeight", BODY_WEIGHT(c)));
        list.add(new Attr("bodyColor", BODY_COLOR(c)));
        list.add(new Attr("secondaryColor", SECONDARY_COLOR(c)));
        list.add(new Attr("headingFont", HEADING_FONT(c)));
        list.add(new Attr("headingSize", HEADING_SIZE(c)));
        list.add(new Attr("headingWeight", HEADING_WEIGHT(c)));
        list.add(new Attr("headingColor", HEADING_COLOR(c)));
        list.add(new Attr("headingUnderline", HEADING_UNDERLINE(c)));
        list.add(new Attr("headingUnderlineColor", HEADING_UNDERLINE_COLOR(c)));
        list.add(new Attr("border", BORDER(c)));
        list.add(new Attr("borderColor", BORDER_COLOR(c)));
        list.add(new Attr("corner", CORNER(c)));
        list.add(new Attr("rowPadding", ROW_PADDING(c)));
        list.add(new Attr("indent", INDENT(c)));
        list.add(new Attr("separator", SEPARATOR(c)));
        list.add(new Attr("separatorColor", SEPARATOR_COLOR(c)));
        list.add(new Attr("buttonBg", BUTTON_BG(c)));
        list.add(new Attr("buttonText", BUTTON_TEXT(c)));
        list.add(new Attr("buttonCorner", BUTTON_CORNER(c)));
        list.add(new Attr("buttonBorder", BUTTON_BORDER(c)));
        return list;
    }

    /* ------------------- applying it ------------------- */

    /** dp → px, the only unit conversion this package needs. */
    public static int dp(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    /**
     * Turn a 0–4 weight slider into a typeface. Real weights need API 28; below that the scale
     * collapses to normal/bold at the midpoint, which is the honest approximation.
     */
    public static Typeface weighted(Typeface base, int weight) {
        if (base == null) base = Typeface.DEFAULT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 0..4 -> 100, 300, 400, 600, 800
            int[] weights = {100, 300, 400, 600, 800};
            int w = weights[Math.max(0, Math.min(4, weight))];
            return Typeface.create(base, w, false);
        }
        return Typeface.create(base, weight >= 3 ? Typeface.BOLD : Typeface.NORMAL);
    }

    /** Apply the body text attributes to a view. */
    public static void body(Context c, TextView view) {
        view.setTextColor(BODY_COLOR(c).get());
        view.setTextSize(BODY_SIZE(c).get());
        view.setTypeface(weighted(Fonts.typeface(c, BODY_FONT(c).get()), BODY_WEIGHT(c).get()));
    }

    /** The bordered, rounded box the fork uses for panels and buttons alike. */
    public static GradientDrawable box(Context c, int fill, int borderPx, int borderColor, int cornerDp) {
        var box = new GradientDrawable();
        box.setColor(fill);
        box.setCornerRadius(dp(c, cornerDp));
        if (borderPx > 0) box.setStroke(borderPx, borderColor);
        return box;
    }

    /** The standard fork panel: surface fill, current border and corner. */
    public static void panel(Context c, View view) {
        view.setBackground(box(c, SURFACE(c).get(), dp(c, BORDER(c).get()), BORDER_COLOR(c).get(),
                CORNER(c).get()));
    }
}
