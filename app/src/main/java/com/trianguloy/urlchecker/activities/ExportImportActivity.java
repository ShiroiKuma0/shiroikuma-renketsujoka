package com.trianguloy.urlchecker.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.shiroikuma.BackupDirectory;
import com.trianguloy.urlchecker.shiroikuma.Backups;
import com.trianguloy.urlchecker.shiroikuma.ShiroikumaUi;
import com.trianguloy.urlchecker.shiroikuma.UiPage;
import com.trianguloy.urlchecker.utilities.AndroidSettings;
import com.trianguloy.urlchecker.utilities.methods.AndroidUtils;
import com.trianguloy.urlchecker.utilities.methods.LocaleUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Export / Import panel: pick a directory, tick the categories, and back up or restore.
 *
 * <p>Laid out the way the sister apps lay it out — a settable directory and the latest export at the
 * top, the categories beneath, and a pill button row at the bottom with Cancel on the left and
 * Import / Export on the right.
 *
 * <p>The close chain matters: a SUCCESSFUL export or import closes this panel and the UI settings
 * page behind it once the result dialog is acknowledged, so you land back where you started.
 * A FAILURE leaves everything open so the problem can be fixed on the spot.
 */
public class ExportImportActivity extends Activity {

    private static final int REQUEST_PICK_DIR = 0x5C02;

    /** Told back to the UI settings page so it can close too. */
    public static final String RESULT_CLOSE_PARENT = "close_parent";

    private LinearLayout container;
    private final Map<String, CheckBox> categoryBoxes = new LinkedHashMap<>();

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

        setTitle(R.string.sk_exportImport);
        AndroidUtils.configureUp(this);
        build();
    }

    private void rebuild() {
        categoryBoxes.clear();
        container.removeAllViews();
        build();
    }

    private void build() {
        var page = new UiPage(this, container);

        /* ---------- directory ---------- */
        page.heading(getString(R.string.sk_backupDirTitle));
        if (!BackupDirectory.supported()) {
            page.notice(2, getString(R.string.sk_backupUnsupported), true);
            return;
        }
        var directory = BackupDirectory.get(this);
        if (directory == null) {
            page.notice(2, getString(R.string.sk_noBackupDir), true);
        } else {
            page.notice(2, getString(R.string.sk_backupDir, BackupDirectory.label(this)), false);
            var latest = BackupDirectory.latestExport(this);
            page.notice(2, latest == null
                    ? getString(R.string.sk_noExportYet)
                    : getString(R.string.sk_latestExport, latest), latest == null);
        }
        var chooseRow = new LinearLayout(this);
        chooseRow.setOrientation(LinearLayout.HORIZONTAL);
        chooseRow.setGravity(Gravity.START);
        chooseRow.addView(page.pill(getString(R.string.sk_chooseDir), v -> pickDirectory()));
        page.add(2, chooseRow);

        /* ---------- 保存復元 automation (this is a backup feature, so it lives where backup lives) ---------- */
        var automation = new android.widget.Switch(this);
        automation.setChecked(com.trianguloy.urlchecker.shiroikuma.AutomationAuth.enabled(this));
        automation.setOnCheckedChangeListener((button, checked) ->
                com.trianguloy.urlchecker.shiroikuma.AutomationAuth.setEnabled(this, checked));
        page.row(2, getString(R.string.sk_automation), getString(R.string.sk_automationSummary), automation);

        var regenerate = page.pill(getString(R.string.sk_regenerate), v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.sk_regenerate)
                        .setMessage(R.string.sk_regenerateWarning)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            com.trianguloy.urlchecker.shiroikuma.AutomationAuth.regenerate(this);
                            rebuild();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
        page.clickable(2, getString(R.string.sk_token),
                com.trianguloy.urlchecker.shiroikuma.AutomationAuth.abbreviated(this), regenerate, v -> {
                    var clipboard = (android.content.ClipboardManager)
                            getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("token",
                                com.trianguloy.urlchecker.shiroikuma.AutomationAuth.token(this)));
                        android.widget.Toast.makeText(this, R.string.sk_tokenCopied,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });

        /* ---------- categories ---------- */
        page.heading(getString(R.string.sk_categories));
        for (var category : Backups.categories()) {
            var box = new CheckBox(this);
            box.setChecked(true);
            categoryBoxes.put(category.id, box);
            page.row(2, getString(category.title), getString(category.summary), box);
        }

        /* ---------- actions ---------- */
        var actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        var cancel = page.pill(getString(android.R.string.cancel), v -> finish());
        actions.addView(cancel);

        // spacer pushes Import/Export to the right, Cancel stays alone on the left
        var spacer = new View(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        var importButton = page.pill(getString(R.string.sk_import), v -> doImport());
        var params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = ShiroikumaUi.dp(this, 8);
        actions.addView(importButton, params);
        actions.addView(page.pill(getString(R.string.sk_export), v -> doExport()));

        page.add(1, actions);
    }

    /* ------------------- directory ------------------- */

    private void pickDirectory() {
        var intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_DIR);
        } catch (Exception e) {
            info(getString(R.string.sk_exportFailed), false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_DIR) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                BackupDirectory.set(this, data.getData(), data.getFlags());
                rebuild();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /* ------------------- actions ------------------- */

    private java.util.List<String> selected() {
        var ids = new java.util.ArrayList<String>();
        for (var entry : categoryBoxes.entrySet()) {
            if (entry.getValue().isChecked()) ids.add(entry.getKey());
        }
        return ids;
    }

    private void doExport() {
        if (BackupDirectory.get(this) == null) {
            info(getString(R.string.sk_noBackupDir), false);
            return;
        }
        var ids = selected();
        if (ids.isEmpty()) {
            info(getString(R.string.sk_noCategories), false);
            return;
        }
        var result = com.trianguloy.urlchecker.shiroikuma.ExportRunner.run(this, ids, null);
        if (!result.ok()) {
            info(getString(R.string.sk_exportFailed) + " (" + result.error + ")", false);
        } else {
            info(getString(R.string.sk_exportOk, result.name,
                    Backups.humanSize(result.bytes), result.categories), true);
        }
    }

    private void doImport() {
        if (BackupDirectory.get(this) == null) {
            info(getString(R.string.sk_noBackupDir), false);
            return;
        }
        var ids = selected();
        if (ids.isEmpty()) {
            info(getString(R.string.sk_noCategories), false);
            return;
        }
        var latest = BackupDirectory.latestExport(this);
        if (latest == null) {
            info(getString(R.string.sk_noExportYet), false);
            return;
        }
        int restored = -1;
        var source = BackupDirectory.find(this, latest);
        if (source != null) {
            try (var in = getContentResolver().openInputStream(source)) {
                if (in != null) restored = Backups.readFrom(this, in, ids);
            } catch (Exception ignored) {
                // leaves restored at -1, reported as a failure below
            }
        }
        if (restored < 0) {
            info(getString(R.string.sk_importFailed), false);
        } else {
            importedDialog(getString(R.string.sk_importOk, latest, restored));
        }
    }

    /* ------------------- dialogs ------------------- */

    /** The house result dialog: black, yellow text, yellow border. */
    private AlertDialog.Builder houseDialog(String message) {
        var text = new TextView(this);
        text.setText(message);
        ShiroikumaUi.body(this, text);
        int pad = ShiroikumaUi.dp(this, 20);
        text.setPadding(pad, pad, pad, pad);

        var frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setBackground(ShiroikumaUi.box(this, ShiroikumaUi.SURFACE(this).get(),
                Math.max(ShiroikumaUi.dp(this, 2), ShiroikumaUi.dp(this, ShiroikumaUi.BORDER(this).get())),
                ShiroikumaUi.BORDER_COLOR(this).get(), ShiroikumaUi.CORNER(this).get()));
        frame.addView(text);

        return new AlertDialog.Builder(this).setView(frame).setCancelable(false);
    }

    /**
     * Success closes the chain — this panel and the UI settings page behind it — the moment OK is
     * pressed. Failure leaves everything standing so the problem can be fixed here and now.
     */
    private void info(String message, boolean success) {
        houseDialog(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (success) closeChain();
                })
                .show();
    }

    /** The import variant: Restart now applies immediately, Later still closes the chain. */
    private void importedDialog(String message) {
        houseDialog(message)
                .setPositiveButton(R.string.sk_restartNow, (dialog, which) -> {
                    AndroidSettings.markForReloading(this);
                    var intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                    closeChain();
                })
                .setNegativeButton(R.string.sk_later, (dialog, which) -> closeChain())
                .show();
    }

    /** Tell the UI settings page to close too, then close this panel. */
    private void closeChain() {
        var result = new Intent();
        result.putExtra(RESULT_CLOSE_PARENT, true);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
