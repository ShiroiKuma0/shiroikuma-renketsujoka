package com.trianguloy.urlchecker.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.Toast;

import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.shiroikuma.Fonts;
import com.trianguloy.urlchecker.shiroikuma.ShiroikumaUi;
import com.trianguloy.urlchecker.shiroikuma.UiPage;
import com.trianguloy.urlchecker.utilities.AndroidSettings;
import com.trianguloy.urlchecker.utilities.methods.AndroidUtils;
import com.trianguloy.urlchecker.utilities.methods.LocaleUtils;
import com.trianguloy.urlchecker.shiroikuma.SkToast;
import com.trianguloy.urlchecker.shiroikuma.SkDialog;

/**
 * The 白い熊 連結浄化 UI page: every attribute this fork adds on top of upstream, grouped, indented
 * and previewed.
 *
 * <p>The page is drawn by the very attributes it edits, so it is its own live preview — every
 * change rebuilds it and you see the new heading size, indent, border or colour immediately. It is
 * reached from the bottom of Settings, or by LONG-PRESSING the Settings cog on the main screen.
 */

public class ShiroikumaUiActivity extends Activity {

    private static final int REQUEST_IMPORT_FONT = 0x5C01;
    private static final int REQUEST_EXPORT_IMPORT = 0x5C03;

    /** Where a freshly imported font is applied — set before launching the document picker. */
    private String fontTarget = null;

    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidSettings.setTheme(this, false);
        LocaleUtils.setLocale(this);

        var scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        setTitle(R.string.a_shiroikumaUi);
        AndroidUtils.configureUp(this);

        build();

        if (AndroidSettings.wasReloaded(this)) AndroidSettings.markForReloading(this);
    }

    /** Rebuild the whole page. Cheap, and the simplest way to make every change previewable. */
    private void rebuild() {
        container.removeAllViews();
        build();
    }

    private void build() {
        var page = new UiPage(this, container);

        /* ---------- Export / Import ---------- */
        page.heading(getString(R.string.sk_catExportImport));
        var backup = com.trianguloy.urlchecker.shiroikuma.BackupDirectory.get(this);
        if (backup == null) {
            page.notice(2, getString(R.string.sk_noBackupDir), true);
        } else {
            page.notice(2, getString(R.string.sk_backupDir, com.trianguloy.urlchecker.shiroikuma.BackupDirectory.label(this)), false);
            var latest = com.trianguloy.urlchecker.shiroikuma.BackupDirectory.latestExport(this);
            page.notice(2, latest == null
                    ? getString(R.string.sk_noExportYet)
                    : getString(R.string.sk_latestExport, latest), latest == null);
        }
        page.clickable(2, getString(R.string.sk_exportImport), getString(R.string.sk_exportImportSummary),
                null, v -> startActivityForResult(
                        new Intent(this, ExportImportActivity.class), REQUEST_EXPORT_IMPORT));

        /* ---------- Theme ---------- */
        page.heading(getString(R.string.sk_catTheme));
        var master = new Switch(this);
        master.setChecked(ShiroikumaUi.ENABLED(this).get());
        master.setOnCheckedChangeListener((button, checked) -> {
            ShiroikumaUi.ENABLED(this).set(checked);
            AndroidSettings.markForReloading(this);
        });
        page.row(2, getString(R.string.sk_enabled), getString(R.string.sk_enabledSummary), master);
        page.color(2, getString(R.string.sk_background), ShiroikumaUi.BACKGROUND(this).get(),
                color -> apply(ShiroikumaUi.BACKGROUND(this), color));
        page.color(2, getString(R.string.sk_surface), ShiroikumaUi.SURFACE(this).get(),
                color -> apply(ShiroikumaUi.SURFACE(this), color));
        page.add(2, page.previewPanel(getString(R.string.sk_previewBody)));

        /* ---------- Text ---------- */
        page.heading(getString(R.string.sk_catText));
        page.font(2, getString(R.string.sk_font), ShiroikumaUi.BODY_FONT(this).get(),
                id -> {
                    ShiroikumaUi.BODY_FONT(this).set(id);
                    rebuild();
                },
                () -> pickFont("body"));
        page.slider(2, getString(R.string.sk_size), ShiroikumaUi.BODY_SIZE(this).get(), 10, 30, "sp",
                value -> apply(ShiroikumaUi.BODY_SIZE(this), value));
        page.slider(2, getString(R.string.sk_weight), ShiroikumaUi.BODY_WEIGHT(this).get(), 0, 4, "",
                value -> apply(ShiroikumaUi.BODY_WEIGHT(this), value));
        page.color(2, getString(R.string.sk_textColor), ShiroikumaUi.BODY_COLOR(this).get(),
                color -> apply(ShiroikumaUi.BODY_COLOR(this), color));
        page.color(2, getString(R.string.sk_secondaryColor), ShiroikumaUi.SECONDARY_COLOR(this).get(),
                color -> apply(ShiroikumaUi.SECONDARY_COLOR(this), color));
        page.add(2, page.previewPanel(getString(R.string.sk_previewBody)));

        /* ---------- Headings ---------- */
        page.heading(getString(R.string.sk_catHeadings));
        page.font(2, getString(R.string.sk_font), ShiroikumaUi.HEADING_FONT(this).get(),
                id -> {
                    ShiroikumaUi.HEADING_FONT(this).set(id);
                    rebuild();
                },
                () -> pickFont("heading"));
        page.slider(2, getString(R.string.sk_size), ShiroikumaUi.HEADING_SIZE(this).get(), 12, 36, "sp",
                value -> apply(ShiroikumaUi.HEADING_SIZE(this), value));
        page.slider(2, getString(R.string.sk_weight), ShiroikumaUi.HEADING_WEIGHT(this).get(), 0, 4, "",
                value -> apply(ShiroikumaUi.HEADING_WEIGHT(this), value));
        page.color(2, getString(R.string.sk_textColor), ShiroikumaUi.HEADING_COLOR(this).get(),
                color -> apply(ShiroikumaUi.HEADING_COLOR(this), color));
        page.subHeading(getString(R.string.sk_subUnderline), 2);
        page.slider(3, getString(R.string.sk_thickness), ShiroikumaUi.HEADING_UNDERLINE(this).get(), 0, 10, "dp",
                value -> apply(ShiroikumaUi.HEADING_UNDERLINE(this), value));
        page.color(3, getString(R.string.sk_color), ShiroikumaUi.HEADING_UNDERLINE_COLOR(this).get(),
                color -> apply(ShiroikumaUi.HEADING_UNDERLINE_COLOR(this), color));

        /* ---------- Borders & shape ---------- */
        page.heading(getString(R.string.sk_catShape));
        page.slider(2, getString(R.string.sk_borderThickness), ShiroikumaUi.BORDER(this).get(), 0, 12, "dp",
                value -> apply(ShiroikumaUi.BORDER(this), value));
        page.color(2, getString(R.string.sk_borderColor), ShiroikumaUi.BORDER_COLOR(this).get(),
                color -> apply(ShiroikumaUi.BORDER_COLOR(this), color));
        page.slider(2, getString(R.string.sk_roundness), ShiroikumaUi.CORNER(this).get(), 0, 32, "dp",
                value -> apply(ShiroikumaUi.CORNER(this), value));
        page.add(2, page.previewPanel(getString(R.string.sk_previewBody)));

        /* ---------- Rows & spacing ---------- */
        page.heading(getString(R.string.sk_catSpacing));
        page.slider(2, getString(R.string.sk_rowPadding), ShiroikumaUi.ROW_PADDING(this).get(), 0, 24, "dp",
                value -> apply(ShiroikumaUi.ROW_PADDING(this), value));
        page.slider(2, getString(R.string.sk_indent), ShiroikumaUi.INDENT(this).get(), 0, 64, "dp",
                value -> apply(ShiroikumaUi.INDENT(this), value));
        page.subHeading(getString(R.string.sk_subSeparator), 2);
        page.slider(3, getString(R.string.sk_thickness), ShiroikumaUi.SEPARATOR(this).get(), 0, 8, "px",
                value -> apply(ShiroikumaUi.SEPARATOR(this), value));
        page.color(3, getString(R.string.sk_color), ShiroikumaUi.SEPARATOR_COLOR(this).get(),
                color -> apply(ShiroikumaUi.SEPARATOR_COLOR(this), color));

        /* ---------- Buttons ---------- */
        page.heading(getString(R.string.sk_catButtons));
        page.color(2, getString(R.string.sk_buttonBg), ShiroikumaUi.BUTTON_BG(this).get(),
                color -> apply(ShiroikumaUi.BUTTON_BG(this), color));
        page.color(2, getString(R.string.sk_buttonText), ShiroikumaUi.BUTTON_TEXT(this).get(),
                color -> apply(ShiroikumaUi.BUTTON_TEXT(this), color));
        page.slider(2, getString(R.string.sk_roundness), ShiroikumaUi.BUTTON_CORNER(this).get(), 0, 32, "dp",
                value -> apply(ShiroikumaUi.BUTTON_CORNER(this), value));
        page.slider(2, getString(R.string.sk_borderThickness), ShiroikumaUi.BUTTON_BORDER(this).get(), 0, 12, "dp",
                value -> apply(ShiroikumaUi.BUTTON_BORDER(this), value));
        page.subHeading(getString(R.string.sk_subSwitches), 2);
        page.slider(3, getString(R.string.sk_switchThumb), ShiroikumaUi.SWITCH_THUMB(this).get(), 8, 40, "dp",
                value -> apply(ShiroikumaUi.SWITCH_THUMB(this), value));
        page.slider(3, getString(R.string.sk_switchTrackWidth), ShiroikumaUi.SWITCH_TRACK_WIDTH(this).get(), 16, 80, "dp",
                value -> apply(ShiroikumaUi.SWITCH_TRACK_WIDTH(this), value));
        page.slider(3, getString(R.string.sk_switchTrackHeight), ShiroikumaUi.SWITCH_TRACK_HEIGHT(this).get(), 8, 48, "dp",
                value -> apply(ShiroikumaUi.SWITCH_TRACK_HEIGHT(this), value));
        page.add(2, page.previewPanel(getString(R.string.sk_previewBody)));
    }

    /** Store a value and repaint the page so the change is visible at once. */
    private <T> void apply(com.trianguloy.urlchecker.utilities.generics.GenericPref<T> pref, T value) {
        pref.set(value);
        rebuild();
    }

    /* ------------------- font import ------------------- */

    private void pickFont(String target) {
        fontTarget = target;
        var intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_FONT);
        } catch (Exception e) {
            SkToast.show(this, R.string.toast_noApp, android.widget.Toast.LENGTH_SHORT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_EXPORT_IMPORT) {
            // A successful export/import closes this page too, so the whole chain unwinds at once.
            if (resultCode == RESULT_OK && data != null
                    && data.getBooleanExtra(ExportImportActivity.RESULT_CLOSE_PARENT, false)) {
                finish();
            } else {
                rebuild();
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_FONT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                var uri = data.getData();
                var name = displayName(uri);
                var id = Fonts.importFont(this, uri, name);
                if (id == null) {
                    SkToast.show(this, R.string.sk_fontImportFailed, android.widget.Toast.LENGTH_LONG);
                } else {
                    if ("heading".equals(fontTarget)) ShiroikumaUi.HEADING_FONT(this).set(id);
                    else ShiroikumaUi.BODY_FONT(this).set(id);
                    rebuild();
                }
            }
            fontTarget = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** Best-effort file name for a picked document; the last path segment is a fine fallback. */
    private String displayName(android.net.Uri uri) {
        try (var cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    var name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
            // fall through to the path segment
        }
        var segment = uri.getLastPathSegment();
        return segment == null ? "font.ttf" : segment;
    }

    /* ------------------- menu ------------------- */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.sk_resetAll);
        com.trianguloy.urlchecker.shiroikuma.SkMenu.tint(menu, this);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        if (item.getItemId() == 1) {
            new SkDialog(this)
                    .setTitle(R.string.sk_resetAll)
                    .setMessage(R.string.sk_resetAllConfirm)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        for (var attr : ShiroikumaUi.all(this)) {
                            ((com.trianguloy.urlchecker.utilities.generics.GenericPref<?>) attr.pref).clear();
                        }
                        rebuild();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
