package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import com.trianguloy.urlchecker.utilities.generics.GenericPref.StringPref;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The settable backup directory the Export / Import section works out of.
 *
 * <p>Held as a persisted SAF tree uri, so the choice survives reboots and app updates without the
 * app needing storage permissions. The page queries {@link #latestExport} every time it opens, so
 * you can see at a glance when the last backup was written — and, if none was, that says so in red.
 */
public class BackupDirectory {

    /** File name shape: renketsujoka_20260812-200145.json */
    private static final String PREFIX = "renketsujoka_";
    private static final String SUFFIX = ".json";
    public static final String MIME = "application/json";

    private static StringPref PREF(Context cntx) {
        return new StringPref("shiroikuma_backupDir", "", cntx);
    }

    /** Tree uris need SAF, which is API 21. Below that the section offers nothing to set. */
    public static boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    /** The chosen directory, or null if none is set (or the grant has since been revoked). */
    public static Uri get(Context cntx) {
        if (!supported()) return null;
        var stored = PREF(cntx).get();
        if (stored == null || stored.isEmpty()) return null;
        var uri = Uri.parse(stored);
        // A grant can be revoked from system settings, or the volume can be gone; treat either as unset.
        for (var permission : cntx.getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(uri) && permission.isWritePermission()) return uri;
        }
        return null;
    }

    /** Remember a directory the user picked, taking the persistable grant with it. */
    public static void set(Context cntx, Uri uri, int flags) {
        if (uri == null) {
            PREF(cntx).set("");
            return;
        }
        int keep = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            cntx.getContentResolver().takePersistableUriPermission(uri, keep);
        } catch (SecurityException ignored) {
            // Some providers hand back a non-persistable grant; it still works for this session.
        }
        PREF(cntx).set(uri.toString());
    }

    /** A short human label for the chosen directory. */
    public static String label(Context cntx) {
        var uri = get(cntx);
        if (uri == null) return "";
        try {
            var id = DocumentsContract.getTreeDocumentId(uri);
            int colon = id.lastIndexOf(':');
            var path = colon >= 0 ? id.substring(colon + 1) : id;
            return path.isEmpty() ? id : path;
        } catch (Exception e) {
            return uri.getLastPathSegment() == null ? uri.toString() : uri.getLastPathSegment();
        }
    }

    /** The name of the newest export in the directory, or null if there is none. */
    public static String latestExport(Context cntx) {
        var tree = get(cntx);
        if (tree == null) return null;
        var children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree));
        String newestName = null;
        long newestTime = Long.MIN_VALUE;
        try (var cursor = cntx.getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        }, null, null, null)) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                var name = cursor.getString(0);
                if (name == null || !name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) continue;
                long modified = cursor.isNull(1) ? 0 : cursor.getLong(1);
                // Fall back to the name when the provider reports no timestamp — the name sorts by date.
                if (modified > newestTime || (modified == newestTime && name.compareTo(String.valueOf(newestName)) > 0)) {
                    newestTime = modified;
                    newestName = name;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return newestName;
    }

    /** The file name a new export should take. */
    public static String newExportName() {
        return PREFIX + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + SUFFIX;
    }

    /** True when the name looks like one of our exports. */
    public static boolean isExport(String name) {
        return name != null && name.startsWith(PREFIX) && name.endsWith(SUFFIX);
    }

    /** Create a new export document in the chosen directory. */
    public static Uri createExport(Context cntx) {
        var tree = get(cntx);
        if (tree == null) return null;
        try {
            var parent = DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree));
            return DocumentsContract.createDocument(
                    cntx.getContentResolver(), parent, MIME, newExportName());
        } catch (Exception e) {
            return null;
        }
    }

    /** Resolve a document uri for a file name inside the chosen directory. */
    public static Uri find(Context cntx, String displayName) {
        var tree = get(cntx);
        if (tree == null) return null;
        var children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree));
        try (var cursor = cntx.getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        }, null, null, null)) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                if (displayName.equals(cursor.getString(1))) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
