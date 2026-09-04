package com.trianguloy.urlchecker.shiroikuma;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * <p>It sits <i>alongside</i> {@link StateExportReceiver}; it does not replace it.
 *
 * <h3>Why a provider and not the broadcast receiver next to it</h3>
 *
 * <p><b>A broadcast cannot tell you who sent it.</b> v1's answer to that was the shared secret. Take
 * the secret away and a receiver has no idea who is asking — and the caller supplies the destination
 * an export is written into, so "no idea who is asking" would mean any app on the phone can harvest
 * every sister app's data. A provider gets the caller's identity from the framework; see
 * {@link AutomationCallers} for what is actually checked.
 *
 * <p><b>And a list needs a synchronous answer.</b> 応用管理 draws a row per installed app before any
 * export exists; a broadcast round trip per app to fill a list is the wrong shape.
 *
 * <h3>What does NOT happen here</h3>
 *
 * The payload. {@code call()} validates, starts a foreground service and returns. The bytes go
 * through a file descriptor the caller opened, and the terminal answer comes back on the broadcast
 * the family already proved on EMUI.
 *
 * <h3>Why a descriptor and not a path</h3>
 *
 * Because a backup is not a stable directory while it is being assembled. 応用管理 writes into a
 * temporary path and renames on commit; it encrypts and checksums <b>per file it knows about</b>. A
 * file this app dropped into that directory itself would be renamed out from under it, would sit in
 * plaintext inside an encrypted backup, and would be unverified rather than verified-and-failing. A
 * descriptor is also a capability that <b>expires when it is closed</b>.
 *
 * <p>It also means this app needs no all-files access for the automation path — which matters here
 * more than most, since a link cleaner has no business asking for it and therefore never had it.
 *
 * <h3>{@code import} exists ONLY here</h3>
 *
 * It never gets a broadcast action. An import overwrites this app's data, and the §1 receiver is
 * {@code exported="true"} with no permission — an import there would let any app on the phone wipe
 * any sister app.
 */
public class AutomationProvider extends ContentProvider {

    public static final String METHOD_DESCRIBE = "describe";
    public static final String METHOD_EXPORT = "export";
    public static final String METHOD_IMPORT = "import";
    public static final String METHOD_CANCEL = "cancel";

    public static final String KEY_RESULT = "result";
    public static final String KEY_FD = "fd";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_JOB_ID = "job_id";
    public static final String KEY_ITEMS = "items";
    public static final String KEY_REPLY_ACTION = "reply_action";
    public static final String KEY_REPLY_PACKAGE = "reply_package";
    public static final String KEY_PROGRESS_ACTION = "progress_action";

    /** This app's archive format. Bumped when an older build could no longer read what we write. */
    public static final int FORMAT = Backups.VERSION;

    /**
     * The oldest archive this build can still read.
     *
     * <p>Version skew has a direction: old data into a newer app is normally fine, because an app
     * migrates its own storage; newer data into an older app is not. This field is what lets a
     * caller refuse the second case at discovery time, before anything is streamed.
     */
    public static final int MIN_FORMAT_READABLE = 1;

    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * Every method answers a {@link Bundle} with {@link #KEY_RESULT} — {@code OK…} or
     * {@code ERROR:…}, the same vocabulary the broadcast contract uses, so a caller has one grammar
     * to parse rather than two.
     *
     * <p>A refusal is returned, <b>never thrown</b>: an exception across a binder reaches the caller
     * as a {@code RuntimeException} carrying our stack trace, which tells 白い熊 nothing and tells a
     * misbehaving caller rather more than it should.
     */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        var cntx = getContext();
        if (cntx == null) return answer("ERROR:not ready");

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        var stranger = AutomationCallers.verify(cntx, getCallingPackage());
        if (stranger != null) return answer(stranger);

        // Then this app's own switches — a token is ignored unless this app asks for one.
        var closed = AutomationAuth.refuse(cntx, extras == null ? null : extras.getString(KEY_TOKEN));
        if (closed != null) return answer(closed);

        if (METHOD_DESCRIBE.equals(method)) return answer(describe(cntx));
        if (METHOD_EXPORT.equals(method)) return start(cntx, extras, false);
        if (METHOD_IMPORT.equals(method)) return start(cntx, extras, true);
        if (METHOD_CANCEL.equals(method)) {
            AutomationJobs.cancel(extras == null ? null : extras.getString(KEY_JOB_ID));
            return answer("OK:cancelled");
        }
        return answer("ERROR:unknown method: " + method);
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * <p>Returned from the call rather than written into the archive, deliberately: 応用管理 must
     * draw a row before an export exists, and at restore must judge compatibility <b>before</b>
     * streaming into an app that would reject it — which it cannot do if the header is buried inside
     * an encrypted archive.
     */
    private String describe(Context cntx) {
        try {
            @SuppressWarnings("deprecation")
            var version = cntx.getPackageManager().getPackageInfo(cntx.getPackageName(), 0);
            var contains = new JSONArray();
            for (var category : Backups.categories()) contains.put(category.label);

            var header = new JSONObject();
            header.put("app_id", cntx.getPackageName());
            header.put("version_code", version.versionCode);
            header.put("version_name", version.versionName == null ? "" : version.versionName);
            header.put("format", FORMAT);
            header.put("min_format_readable", MIN_FORMAT_READABLE);
            // This app writes defaults lazily and its import merges per key, so an archive may land
            // in a freshly installed copy that has never been opened.
            header.put("requires_launch_first", false);
            header.put("contains", contains);
            return "OK:" + header;
        } catch (Exception e) {
            return "ERROR:" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * <p>The descriptor is <b>duplicated</b> before it leaves this method. The one in {@code extras}
     * belongs to the binder transaction and is closed when {@code call()} returns; a service reading
     * it afterwards would find it shut. That is a bug you only see under load, so it is not left to
     * the service to remember.
     */
    private Bundle start(Context cntx, Bundle extras, boolean importing) {
        var fd = extras == null ? null : (ParcelFileDescriptor) extras.getParcelable(KEY_FD);
        if (fd == null) return answer("ERROR:no descriptor");
        ParcelFileDescriptor dup;
        try {
            dup = fd.dup();
        } catch (Exception e) {
            return answer("ERROR:descriptor unusable");
        }
        var jobId = AutomationJobs.begin();
        var started = AutomationDataService.start(cntx, jobId, dup, importing, extras);
        if (started != null) {
            AutomationJobs.finish(jobId);
            try {
                dup.close();
            } catch (Exception ignored) {
                // nothing left to do; the caller still gets the refusal below
            }
            return answer(started);
        }
        return answer("OK:" + jobId);
    }

    private static Bundle answer(String result) {
        var bundle = new Bundle();
        bundle.putString(KEY_RESULT, result);
        return bundle;
    }

    // A provider that is only ever call()ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than
    // "wrong door".
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        throw new UnsupportedOperationException("automation is call() only");
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("automation is call() only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("automation is call() only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("automation is call() only");
    }
}
