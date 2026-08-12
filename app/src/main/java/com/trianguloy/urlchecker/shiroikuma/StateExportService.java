package com.trianguloy.urlchecker.shiroikuma;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.trianguloy.urlchecker.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Where the 保存復元 export actually runs.
 *
 * <p>A manifest receiver cannot hold it: {@code goAsync()} does not extend the broadcast window, so
 * an overrun is an ANR against this app and a kill mid-write. The receiver validates and hands off
 * here; this service does the export, reports progress, sends the ONE terminal reply, and stops.
 *
 * <p>This app's export is a preferences dump — it finishes in well under a second and never needs
 * the wakelock and battery-exemption dance a media or database export does. The service is still
 * the right home for it: it is what makes the cancel action meaningful and keeps the shape
 * identical to every other app in the family.
 */
public class StateExportService extends Service {

    private static final String CHANNEL = "shiroikuma_export";
    private static final int NOTIFICATION_ID = 0x5C04;

    /** Guards against two exports at once. Process-local and released in a finally — never persisted:
     * a persisted flag wedges the app for good after a single crash. */
    private static final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must be inside 5 s of the service starting, or the system kills us for it.
        startForeground(NOTIFICATION_ID, notification());

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        var replyAction = intent.getStringExtra("reply_action");
        var replyPackage = intent.getStringExtra("reply_package");
        var replyId = intent.getStringExtra("reply_id");
        var progressAction = intent.getStringExtra("progress_action");
        var items = intent.getStringExtra("items");

        // Exactly one terminal reply per request, whichever path produces it.
        var replied = new AtomicBoolean(false);
        Runnable stop = () -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf();
        };

        if (!running.compareAndSet(false, true)) {
            replyOnce(replied, replyAction, replyPackage, replyId, "ERROR:export already running");
            stop.run();
            return START_NOT_STICKY;
        }

        Backups.clearCancel();
        var categories = items == null || items.isEmpty()
                ? Backups.allCategoryIds()
                : new ArrayList<>(Arrays.asList(items.split(",")));

        new Thread(() -> {
            try {
                var progress = progressAction == null ? null : new Backups.Progress() {
                    private long lastSent = 0;

                    @Override
                    public void onCategory(String categoryId, int position, int total) {
                        // Throttled to one every 500 ms, but the first and last always go out —
                        // the panel keys its highlight off `item`, and the final one closes the row.
                        long now = System.currentTimeMillis();
                        if (position != 1 && position != total && now - lastSent < 500) return;
                        lastSent = now;
                        sendProgress(progressAction, replyPackage, replyId, categoryId, position, total);
                    }
                };

                var result = ExportRunner.run(this, categories, progress);
                if (result.ok()) {
                    replyOnce(replied, replyAction, replyPackage, replyId,
                            "OK:" + ExportRunner.reportPath(this, result.name)
                                    + "|" + result.bytes
                                    + "|" + Backups.humanSize(result.bytes)
                                    + "|" + result.categories + " categories");
                } else {
                    replyOnce(replied, replyAction, replyPackage, replyId, "ERROR:" + result.error);
                }
            } catch (Exception e) {
                var message = e.getMessage();
                replyOnce(replied, replyAction, replyPackage, replyId,
                        "ERROR:" + (message == null ? e.getClass().getSimpleName() : message));
            } finally {
                Backups.clearCancel();
                running.set(false);
                stop.run();
            }
        }, "shiroikuma-export").start();

        return START_NOT_STICKY;
    }

    private void replyOnce(AtomicBoolean replied, String action, String pkg, String id, String result) {
        if (!replied.compareAndSet(false, true)) return;
        StateExportReceiver.reply(this, action, pkg, id, result);
    }

    /**
     * Real numbers, never a percentage. This app counts categories, so {@code current} is the
     * POSITION of the one being written and {@code total} is how many are actually being exported.
     */
    private void sendProgress(String action, String replyPackage, String replyId,
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
        intent.putExtra("reply_id", replyId);
        intent.putExtra("app", getString(R.string.app_name));
        intent.putExtra("item", categoryId);
        intent.putExtra("text", "区分 " + position + "/" + total + " — " + label);
        intent.putExtra("current", (long) position);
        intent.putExtra("total", (long) total);
        intent.putExtra("unit", "区分");
        try {
            sendBroadcast(intent);
        } catch (Exception ignored) {
            // progress is best-effort; the terminal reply is what matters
        }
    }

    private Notification notification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL, getString(R.string.sk_exportNotification),
                        NotificationManager.IMPORTANCE_LOW));
            }
            return new Notification.Builder(this, CHANNEL)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.sk_exportNotification))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .build();
        }
        return new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.sk_exportNotification))
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
    }
}
