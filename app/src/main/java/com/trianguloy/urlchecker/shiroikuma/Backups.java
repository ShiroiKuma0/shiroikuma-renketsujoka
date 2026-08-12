package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;

import com.trianguloy.urlchecker.BuildConfig;
import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.utilities.generics.GenericPref;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Export / import of everything settable in the app, in the sister-app category-ZIP format.
 *
 * <p>The archive holds a {@code manifest.json} plus one {@code <id>.json} per category, exactly as
 * the other 17 apps in the family write it, so 白い熊's one backup directory reads uniformly.
 *
 * <p>The core is headless — {@link #writeTo} takes categories, a stream and a progress callback —
 * so the Export/Import panel and the 保存復元 automation receiver are both thin callers over the
 * same code rather than two implementations that can drift.
 *
 * <p>Every setting in this app, upstream's and ours alike, is a {@link GenericPref} over one
 * SharedPreferences file, so a category is a bucket of keys. Bucketing rather than enumerating
 * means a preference upstream adds on the next rebase is backed up without anyone listing it.
 */
public class Backups {

    public static final String FORMAT = "shiroikuma-category-zip";
    public static final int VERSION = 1;

    /**
     * Keys never exported. The backup directory grant is device-local, and the automation token
     * lives in its own preferences file precisely so it cannot land here — this is belt and braces.
     */
    private static final String[] EXCLUDED = {"shiroikuma_backupDir", "automation_token", "automation_enabled"};

    /** A tickable group in the panel, and an id in {@code items} / {@code LIST_CATEGORIES}. */
    public static class Category {
        public final String id;
        public final int title;
        public final int summary;
        /** Plain label for the automation's LIST_CATEGORIES reply. */
        public final String label;

        Category(String id, int title, int summary, String label) {
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.label = label;
        }
    }

    public static List<Category> categories() {
        var list = new ArrayList<Category>();
        list.add(new Category("ui", R.string.sk_catUiBackup, R.string.sk_catUiBackupSummary, "白い熊 連結浄化 UI"));
        list.add(new Category("patterns", R.string.sk_catPatterns, R.string.sk_catPatternsSummary, "Patterns"));
        list.add(new Category("automations", R.string.sk_catAutomations, R.string.sk_catAutomationsSummary, "Automations"));
        list.add(new Category("cleaning", R.string.sk_catCleaning, R.string.sk_catCleaningSummary, "URL cleaning"));
        list.add(new Category("hosts", R.string.sk_catHosts, R.string.sk_catHostsSummary, "Hosts"));
        list.add(new Category("modules", R.string.sk_catModules, R.string.sk_catModulesSummary, "Modules"));
        list.add(new Category("app", R.string.sk_catApp, R.string.sk_catAppSummary, "App settings"));
        return list;
    }

    public static List<String> allCategoryIds() {
        var ids = new ArrayList<String>();
        for (var category : categories()) ids.add(category.id);
        return ids;
    }

    /** Every category is on by default — this app holds nothing large, derived or re-creatable. */
    public static String listCategoriesReply() {
        var reply = new StringBuilder();
        boolean first = true;
        for (var category : categories()) {
            if (!first) reply.append('\n');
            reply.append(category.id).append('\t').append(category.label);
            first = false;
        }
        return reply.toString();
    }

    /** Which category a preference key belongs to. Every key lands in exactly one. */
    static String categoryOf(String key) {
        var lower = key.toLowerCase(Locale.US);
        if (lower.startsWith("shiroikuma_ui_")) return "ui";
        if (lower.contains("pattern")) return "patterns";
        if (lower.contains("automation")) return "automations";
        if (lower.contains("clearurl")) return "cleaning";
        if (lower.contains("host")) return "hosts";
        if (lower.contains("module") || lower.contains("order") || lower.contains("decoration")) return "modules";
        return "app";
    }

    private static boolean excluded(String key) {
        for (var skip : EXCLUDED) if (skip.equals(key)) return true;
        return false;
    }

    /* ------------------- the headless core ------------------- */

    /** Reports which category is being written, 1-based, as the contract's progress rules require. */
    public interface Progress {
        /**
         * @param categoryId the category id being written right now
         * @param position   1-based position of that category among those being exported
         * @param total      how many categories are being exported
         */
        void onCategory(String categoryId, int position, int total);
    }

    /** Set by a cancel request; the write loop unwinds at the next category boundary. */
    private static volatile boolean cancelled = false;

    public static void requestCancel() {
        cancelled = true;
    }

    public static void clearCancel() {
        cancelled = false;
    }

    public static boolean isCancelled() {
        return cancelled;
    }

    /** Thrown so a cancel unwinds through the same failure path that deletes the partial file. */
    public static class CancelledException extends Exception {
    }

    /**
     * Write the selected categories as a category ZIP. The caller owns the stream and closes it.
     *
     * @return how many categories were written.
     */
    public static int writeTo(Context cntx, List<String> categoryIds, OutputStream out, Progress progress)
            throws Exception {
        // Bucket the preferences once, so a category with no keys still gets an (empty) entry and
        // the archive says plainly that it was included and was empty.
        var buckets = new java.util.LinkedHashMap<String, JSONObject>();
        for (var id : categoryIds) buckets.put(id, new JSONObject());

        for (Map.Entry<String, ?> entry : GenericPref.getPrefs(cntx).getAll().entrySet()) {
            var key = entry.getKey();
            if (excluded(key)) continue;
            var bucket = buckets.get(categoryOf(key));
            if (bucket == null) continue;
            var value = entry.getValue();
            if (value == null) continue;
            var wrapped = new JSONObject();
            wrapped.put("t", typeOf(value));
            wrapped.put("v", String.valueOf(value));
            bucket.put(key, wrapped);
        }

        var zip = new ZipOutputStream(out);
        var manifest = new JSONObject();
        manifest.put("format", FORMAT);
        manifest.put("version", VERSION);
        manifest.put("app", "shiroikuma-renketsujoka");
        manifest.put("appVersion", BuildConfig.VERSION_NAME);
        manifest.put("createdTs", System.currentTimeMillis());
        manifest.put("categories", new JSONArray(categoryIds));
        writeEntry(zip, "manifest.json", manifest.toString(2));

        int position = 0;
        int total = buckets.size();
        for (var bucket : buckets.entrySet()) {
            if (cancelled) throw new CancelledException();
            position++;
            if (progress != null) progress.onCategory(bucket.getKey(), position, total);
            writeEntry(zip, bucket.getKey() + ".json", bucket.getValue().toString(2));
        }
        zip.finish();
        zip.flush();
        return total;
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /**
     * Restore the selected categories from a category ZIP. Merges per key; a category absent from
     * the archive is skipped rather than cleared.
     *
     * @return how many values were restored, or -1 on failure.
     */
    public static int readFrom(Context cntx, InputStream in, List<String> categoryIds) {
        try {
            var zip = new ZipInputStream(in);
            var editor = GenericPref.getPrefs(cntx).edit();
            int restored = 0;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                var name = entry.getName();
                if (!name.endsWith(".json") || name.equals("manifest.json")) continue;
                var id = name.substring(0, name.length() - ".json".length());
                if (!categoryIds.contains(id)) continue;

                var buffer = new ByteArrayOutputStream();
                var chunk = new byte[8192];
                int read;
                while ((read = zip.read(chunk)) > 0) buffer.write(chunk, 0, read);
                var values = new JSONObject(buffer.toString("UTF-8"));

                for (var it = values.keys(); it.hasNext(); ) {
                    var key = it.next();
                    if (excluded(key)) continue;
                    var wrapped = values.getJSONObject(key);
                    var raw = wrapped.getString("v");
                    switch (wrapped.getString("t")) {
                        case "b" -> editor.putBoolean(key, Boolean.parseBoolean(raw));
                        case "i" -> editor.putInt(key, Integer.parseInt(raw));
                        case "l" -> editor.putLong(key, Long.parseLong(raw));
                        case "f" -> editor.putFloat(key, Float.parseFloat(raw));
                        default -> editor.putString(key, raw);
                    }
                    restored++;
                }
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

    /** Human-readable byte size for the automation reply, e.g. {@code 4.6 MB}. */
    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        var units = new String[]{"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.US, value >= 100 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }
}
