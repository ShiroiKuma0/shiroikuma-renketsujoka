package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;

import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.utilities.generics.GenericPref;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Export / import of everything settable in the app, split into the categories the panel lists.
 *
 * <p>Every setting in this app — upstream's and ours alike — is a {@link GenericPref} over one
 * SharedPreferences file, so a backup is that file, bucketed by key into categories and written as
 * JSON. Bucketing rather than enumerating means a setting added upstream on the next rebase is
 * carried by the backup without anyone remembering to list it.
 */
public class Backups {

    /** A tickable group in the Export / Import panel. */
    public static class Category {
        public final String id;
        public final int title;
        public final int summary;

        Category(String id, int title, int summary) {
            this.id = id;
            this.title = title;
            this.summary = summary;
        }
    }

    public static List<Category> categories() {
        var list = new ArrayList<Category>();
        list.add(new Category("ui", R.string.sk_catUiBackup, R.string.sk_catUiBackupSummary));
        list.add(new Category("patterns", R.string.sk_catPatterns, R.string.sk_catPatternsSummary));
        list.add(new Category("automations", R.string.sk_catAutomations, R.string.sk_catAutomationsSummary));
        list.add(new Category("cleaning", R.string.sk_catCleaning, R.string.sk_catCleaningSummary));
        list.add(new Category("hosts", R.string.sk_catHosts, R.string.sk_catHostsSummary));
        list.add(new Category("modules", R.string.sk_catModules, R.string.sk_catModulesSummary));
        list.add(new Category("app", R.string.sk_catApp, R.string.sk_catAppSummary));
        return list;
    }

    /** Which category a preference key belongs to. Every key lands in exactly one. */
    private static String categoryOf(String key) {
        var lower = key.toLowerCase(Locale.US);
        if (lower.startsWith("shiroikuma_ui_")) return "ui";
        if (lower.contains("pattern")) return "patterns";
        if (lower.contains("automation")) return "automations";
        if (lower.contains("clearurl")) return "cleaning";
        if (lower.contains("host")) return "hosts";
        if (lower.contains("module") || lower.contains("order") || lower.contains("decoration")) return "modules";
        return "app";
    }

    /**
     * Write the selected categories to a new export in the backup directory.
     *
     * @return the file name written, or null on failure.
     */
    public static String export(Context cntx, List<String> categoryIds) {
        var uri = BackupDirectory.createExport(cntx);
        if (uri == null) return null;

        var root = new JSONObject();
        try {
            root.put("app", "shiroikuma-renketsujoka");
            root.put("version", 1);
            var values = new JSONObject();
            for (Map.Entry<String, ?> entry : GenericPref.getPrefs(cntx).getAll().entrySet()) {
                if (!categoryIds.contains(categoryOf(entry.getKey()))) continue;
                var value = entry.getValue();
                if (value == null) continue;
                var wrapped = new JSONObject();
                wrapped.put("t", typeOf(value));
                wrapped.put("v", String.valueOf(value));
                values.put(entry.getKey(), wrapped);
            }
            root.put("values", values);
            root.put("categories", new org.json.JSONArray(categoryIds));
        } catch (Exception e) {
            return null;
        }

        try (OutputStream out = cntx.getContentResolver().openOutputStream(uri)) {
            if (out == null) return null;
            out.write(root.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
        // Report the real name the provider gave the document, not the one we asked for.
        var name = BackupDirectory.latestExport(cntx);
        return name == null ? BackupDirectory.newExportName() : name;
    }

    /**
     * Restore the selected categories from a named export.
     *
     * @return how many values were restored, or -1 on failure.
     */
    public static int importFrom(Context cntx, String displayName, List<String> categoryIds) {
        var uri = BackupDirectory.find(cntx, displayName);
        if (uri == null) return -1;

        String text;
        try (InputStream in = cntx.getContentResolver().openInputStream(uri)) {
            if (in == null) return -1;
            var buffer = new ByteArrayOutputStream();
            var chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) buffer.write(chunk, 0, read);
            text = buffer.toString("UTF-8");
        } catch (Exception e) {
            return -1;
        }

        try {
            var root = new JSONObject(text);
            var values = root.getJSONObject("values");
            var editor = GenericPref.getPrefs(cntx).edit();
            int restored = 0;
            for (var it = values.keys(); it.hasNext(); ) {
                var key = it.next();
                if (!categoryIds.contains(categoryOf(key))) continue;
                var wrapped = values.getJSONObject(key);
                var type = wrapped.getString("t");
                var raw = wrapped.getString("v");
                switch (type) {
                    case "b" -> editor.putBoolean(key, Boolean.parseBoolean(raw));
                    case "i" -> editor.putInt(key, Integer.parseInt(raw));
                    case "l" -> editor.putLong(key, Long.parseLong(raw));
                    case "f" -> editor.putFloat(key, Float.parseFloat(raw));
                    default -> editor.putString(key, raw);
                }
                restored++;
            }
            editor.apply();
            return restored;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String typeOf(Object value) {
        if (value instanceof Boolean) return "b";
        if (value instanceof Integer) return "i";
        if (value instanceof Long) return "l";
        if (value instanceof Float) return "f";
        return "s";
    }
}
