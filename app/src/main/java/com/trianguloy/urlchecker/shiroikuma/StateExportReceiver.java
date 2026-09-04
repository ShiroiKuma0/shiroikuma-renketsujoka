package com.trianguloy.urlchecker.shiroikuma;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.trianguloy.urlchecker.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The 保存復元 wire contract: 白い熊 自由作業盤 fires an intent at this app, it exports itself
 * headlessly, and replies with the written path and size.
 *
 * <p>Three actions, all on this one exported receiver: {@code EXPORT_STATE},
 * {@code LIST_CATEGORIES}, {@code CANCEL_EXPORT}. All go through {@link AutomationAuth#refuse},
 * which since v2 is open by default and only asks for the token when 白い熊 has said to.
 *
 * <p>This is deliberately the <b>unauthenticated</b> half of the surface: it only ever writes where
 * this app was already configured to write and reports what it did. Everything that moves data
 * through a caller-supplied descriptor lives behind {@link AutomationProvider}, which knows who is
 * calling — and that is why {@code import} exists only there and has no action here.
 *
 * <p>The receiver itself never runs the export — a manifest receiver must reach the end of
 * {@code onReceive} inside Android's broadcast window or the system ANRs and kills the process
 * mid-write. It validates and hands off to {@link StateExportService}, then returns.
 * {@code LIST_CATEGORIES} and {@code CANCEL_EXPORT} are instant and answer here.
 */
public class StateExportReceiver extends BroadcastReceiver {

    public static final String ACTION_EXPORT = ".action.EXPORT_STATE";
    public static final String ACTION_LIST = ".action.LIST_CATEGORIES";
    public static final String ACTION_CANCEL = ".action.CANCEL_EXPORT";

    @Override
    public void onReceive(Context cntx, Intent intent) {
        var action = intent.getAction();
        if (action == null) return;
        var pkg = cntx.getPackageName();

        var token = intent.getStringExtra("token");
        var replyAction = intent.getStringExtra("reply_action");
        var replyPackage = intent.getStringExtra("reply_package");
        var replyId = intent.getStringExtra("reply_id");

        if (action.equals(pkg + ACTION_CANCEL)) {
            // Fire-and-forget, and safe at any time: a cancel arriving when nothing is running, or
            // after the export already finished, is a silent no-op — not an error, not a reply.
            if (AutomationAuth.refuse(cntx, token) != null) return;
            Backups.requestCancel();
            return;
        }

        // Everything else owes exactly one reply, including the refusals. The gate is ONE call —
        // "disabled" and "bad token" written out separately at each entry point is exactly how the
        // two drift apart, and since v2 a token sent to an app that does not ask for one is ignored
        // rather than refused.
        var closed = AutomationAuth.refuse(cntx, token);
        if (closed != null) {
            reply(cntx, replyAction, replyPackage, replyId, closed);
            return;
        }

        if (action.equals(pkg + ACTION_LIST)) {
            reply(cntx, replyAction, replyPackage, replyId, "OK:" + Backups.listCategoriesReply());
            return;
        }

        if (action.equals(pkg + ACTION_EXPORT)) {
            var items = intent.getStringExtra("items");
            var requested = parseItems(items);
            if (requested == null) {
                reply(cntx, replyAction, replyPackage, replyId, "ERROR:unknown category in items: " + items);
                return;
            }
            // This app holds no all-files access — it is a link cleaner and has no business asking
            // for it — so a `path` override cannot be honoured. The contract's sanctioned fallback
            // is to use the configured SAF directory, and to say so plainly when there is none.
            if (BackupDirectory.get(cntx) == null) {
                reply(cntx, replyAction, replyPackage, replyId,
                        intent.getStringExtra("path") != null
                                ? "ERROR:no-storage-access"
                                : "ERROR:no-directory");
                return;
            }

            var service = new Intent(cntx, StateExportService.class);
            service.putExtra("items", join(requested));
            service.putExtra("progress_action", intent.getStringExtra("progress_action"));
            service.putExtra("reply_action", replyAction);
            service.putExtra("reply_package", replyPackage);
            service.putExtra("reply_id", replyId);
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    cntx.startForegroundService(service);
                } else {
                    cntx.startService(service);
                }
            } catch (Exception e) {
                reply(cntx, replyAction, replyPackage, replyId, "ERROR:could not start export service");
            }
        }
    }

    /**
     * Resolve the {@code items} extra.
     *
     * @return the categories to export, or null if any id is unknown. Absent/empty means our
     * default set, which for this app is everything — it holds nothing large or re-creatable.
     */
    private static List<String> parseItems(String items) {
        if (items == null || items.trim().isEmpty()) return Backups.allCategoryIds();
        var known = Backups.allCategoryIds();
        var requested = new ArrayList<String>();
        for (var raw : items.split(",")) {
            var id = raw.trim();
            if (id.isEmpty()) continue;
            if (!known.contains(id)) return null;
            if (!requested.contains(id)) requested.add(id);
        }
        return requested.isEmpty() ? Backups.allCategoryIds() : requested;
    }

    private static String join(List<String> ids) {
        var joined = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) joined.append(',');
            joined.append(ids.get(i));
        }
        return joined.toString();
    }

    /**
     * The reply channel: a fresh broadcast, never a binder.
     *
     * <p>EMUI will not reliably carry a live Binder into another app's manifest receiver, and it
     * severs the ordered-broadcast result channel between third-party apps — so a ResultReceiver,
     * PendingIntent, Messenger or {@code setResultData} cannot be the reply.
     * {@code FLAG_INCLUDE_STOPPED_PACKAGES} is what lets a backgrounded caller hear us at all.
     */
    static void reply(Context cntx, String replyAction, String replyPackage, String replyId, String result) {
        if (replyAction == null || replyPackage == null) return;
        var intent = new Intent(replyAction);
        intent.setPackage(replyPackage);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        // Both extras carry the same correlation id: §1 callers read `reply_id`, the §2a data door
        // hands out a `job_id`, and one reader on the other side should serve both doors.
        intent.putExtra("reply_id", replyId);
        intent.putExtra("job_id", replyId);
        intent.putExtra("result", result);
        try {
            cntx.sendBroadcast(intent);
        } catch (Exception ignored) {
            // nothing to fall back to; the caller times the slot out
        }
    }

    /**
     * §3 progress, shared by BOTH doors — the broadcast one above and the {@link AutomationProvider}
     * data door.
     *
     * <p>One sender, parameterised, rather than one per door: an app silent for two minutes is
     * presumed dead by the caller's watchdog, and two senders drift — the one that drifts being the
     * one nobody watches.
     *
     * <p>Real numbers, never a percentage. This app counts categories, so {@code position} is the
     * POSITION of the one being written and {@code total} is how many are actually being exported.
     */
    static void progress(Context cntx, String action, String replyPackage, String id,
                         String categoryId, int position, int total) {
        if (action == null || replyPackage == null) return;
        var label = categoryId;
        for (var category : Backups.categories()) {
            if (category.id.equals(categoryId)) {
                label = category.label;
                break;
            }
        }
        var intent = new Intent(action);
        intent.setPackage(replyPackage);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.putExtra("reply_id", id);
        intent.putExtra("job_id", id);
        intent.putExtra("app", cntx.getString(R.string.app_name));
        intent.putExtra("item", categoryId);
        intent.putExtra("text", "区分 " + position + "/" + total + " — " + label);
        intent.putExtra("current", (long) position);
        intent.putExtra("total", (long) total);
        intent.putExtra("unit", "区分");
        try {
            cntx.sendBroadcast(intent);
        } catch (Exception ignored) {
            // progress is best-effort; the terminal reply is what matters
        }
    }

    /**
     * The throttle both doors export through: one broadcast per 500 ms, but the FIRST and LAST
     * always go out — the caller keys its highlight off {@code item}, and the final one closes the
     * row.
     *
     * @return null when the caller asked for no progress, which {@link Backups#writeTo} accepts.
     */
    static Backups.Progress progressReporter(Context cntx, String action, String replyPackage, String id) {
        if (action == null || replyPackage == null) return null;
        return new Backups.Progress() {
            private long lastSent = 0;

            @Override
            public void onCategory(String categoryId, int position, int total) {
                long now = System.currentTimeMillis();
                if (position != 1 && position != total && now - lastSent < 500) return;
                lastSent = now;
                progress(cntx, action, replyPackage, id, categoryId, position, total);
            }
        };
    }
}
