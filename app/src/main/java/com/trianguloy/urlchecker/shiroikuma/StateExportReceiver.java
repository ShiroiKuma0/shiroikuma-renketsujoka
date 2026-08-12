package com.trianguloy.urlchecker.shiroikuma;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/**
 * The 保存復元 wire contract: 白い熊 自由作業盤 fires a token-gated intent at this app, it exports
 * itself headlessly, and replies with the written path and size.
 *
 * <p>Three actions, all on this one exported receiver, all token-gated:
 * {@code EXPORT_STATE}, {@code LIST_CATEGORIES}, {@code CANCEL_EXPORT}.
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
            if (!AutomationAuth.enabled(cntx)) return;
            if (!AutomationAuth.isTokenValid(cntx, token)) return;
            Backups.requestCancel();
            return;
        }

        // Everything else owes exactly one reply, including the refusals.
        if (!AutomationAuth.enabled(cntx)) {
            reply(cntx, replyAction, replyPackage, replyId, "ERROR:automation disabled");
            return;
        }
        if (!AutomationAuth.isTokenValid(cntx, token)) {
            reply(cntx, replyAction, replyPackage, replyId, "ERROR:bad token");
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
        intent.putExtra("reply_id", replyId);
        intent.putExtra("result", result);
        try {
            cntx.sendBroadcast(intent);
        } catch (Exception ignored) {
            // nothing to fall back to; the caller times the slot out
        }
    }
}
