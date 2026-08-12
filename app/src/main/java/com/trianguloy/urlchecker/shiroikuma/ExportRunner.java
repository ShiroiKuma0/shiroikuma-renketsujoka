package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;
import android.net.Uri;

import java.util.List;

/**
 * The one path that writes a backup, shared by the Export / Import panel and the 保存復元
 * automation service. Neither duplicates export logic; both call this.
 *
 * <p>Writes atomically: the archive goes to {@code <final-name>.part} and is renamed into place
 * only once it is closed and complete. Any failure, cancel or timeout deletes the partial on the
 * way out, so a run that does not finish leaves the directory exactly as it found it.
 */
public class ExportRunner {

    /** What happened, in the shape both callers need. */
    public static class Result {
        /** Final display name on success, else null. */
        public final String name;
        public final long bytes;
        public final int categories;
        /** null on success; otherwise the short reason for an {@code ERROR:} reply. */
        public final String error;

        private Result(String name, long bytes, int categories, String error) {
            this.name = name;
            this.bytes = bytes;
            this.categories = categories;
            this.error = error;
        }

        public boolean ok() {
            return error == null;
        }

        static Result failure(String reason) {
            return new Result(null, 0, 0, reason);
        }
    }

    /**
     * Run an export into the configured backup directory.
     *
     * @param categoryIds which categories to write; must be non-empty
     * @param progress    per-category progress, or null
     */
    public static Result run(Context cntx, List<String> categoryIds, Backups.Progress progress) {
        if (categoryIds == null || categoryIds.isEmpty()) return Result.failure("no categories selected");
        if (BackupDirectory.get(cntx) == null) return Result.failure("no-directory");

        var finalName = BackupDirectory.newExportName();
        Uri partial = BackupDirectory.createPartial(cntx);
        if (partial == null) return Result.failure("no-storage-access");

        int written;
        try (var out = cntx.getContentResolver().openOutputStream(partial)) {
            if (out == null) {
                BackupDirectory.deletePartial(cntx, partial);
                return Result.failure("no-storage-access");
            }
            written = Backups.writeTo(cntx, categoryIds, out, progress);
        } catch (Backups.CancelledException e) {
            BackupDirectory.deletePartial(cntx, partial);
            return Result.failure("cancelled");
        } catch (Exception e) {
            BackupDirectory.deletePartial(cntx, partial);
            var message = e.getMessage();
            return Result.failure(message == null ? e.getClass().getSimpleName() : message);
        }

        // A cancel that landed after the last boundary still must not leave a backup behind.
        if (Backups.isCancelled()) {
            BackupDirectory.deletePartial(cntx, partial);
            return Result.failure("cancelled");
        }

        long bytes = sizeOf(cntx, partial);
        var renamed = BackupDirectory.finishPartial(cntx, partial, finalName);
        if (renamed == null) {
            BackupDirectory.deletePartial(cntx, partial);
            return Result.failure("could not finalise the archive");
        }
        return new Result(renamed, bytes, written, null);
    }

    /** The real byte length of the written document — the caller cannot stat it for us. */
    private static long sizeOf(Context cntx, Uri uri) {
        try (var cursor = cntx.getContentResolver().query(uri,
                new String[]{android.provider.DocumentsContract.Document.COLUMN_SIZE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0);
        } catch (Exception ignored) {
            // fall through to the stream count below
        }
        try (var in = cntx.getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            long total = 0;
            var chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) total += read;
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    /** The absolute-ish path to report back. SAF gives us a document uri, which is what we have. */
    public static String reportPath(Context cntx, String name) {
        var label = BackupDirectory.label(cntx);
        return label.isEmpty() ? name : "/" + label + "/" + name;
    }
}
