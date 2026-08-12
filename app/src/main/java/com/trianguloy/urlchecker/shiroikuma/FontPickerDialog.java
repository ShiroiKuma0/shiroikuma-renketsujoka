package com.trianguloy.urlchecker.shiroikuma;

import android.app.AlertDialog;
import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/**
 * The font picker: a black-yellow list of every available font, EACH ROW RENDERED IN ITS OWN
 * GLYPHS, so the choice is made by looking rather than by reading a name. A neutral button opens
 * the document picker to import a new {@code .ttf} / {@code .otf}.
 */
public class FontPickerDialog {

    public interface OnPick {
        void onPick(String id);
    }

    public interface OnImport {
        void onImport();
    }

    public static void show(Context cntx, String title, String current, OnPick onPick, OnImport onImport) {
        var fonts = Fonts.available(cntx);
        var density = cntx.getResources().getDisplayMetrics().density;

        var adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return fonts.size();
            }

            @Override
            public Object getItem(int position) {
                return fonts.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                var option = fonts.get(position);
                var view = convertView instanceof TextView ? (TextView) convertView : new TextView(cntx);
                boolean selected = option.id.equals(current == null ? "" : current);
                view.setText((selected ? "✓  " : "") + option.name);
                // The point of the picker: the row wears the font it offers.
                view.setTypeface(Fonts.typeface(cntx, option.id));
                view.setTextColor(ShiroikumaUi.YELLOW);
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                int padH = Math.round(20 * density);
                int padV = Math.round(10 * density);
                view.setPadding(padH, padV, padH, padV);
                return view;
            }
        };

        new SkDialog(cntx)
                .setTitle(title)
                .setAdapter(adapter, (dialog, which) -> onPick.onPick(fonts.get(which).id))
                .setNeutralButton(cntx.getString(com.trianguloy.urlchecker.R.string.sk_fontImport),
                        (dialog, which) -> onImport.onImport())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
