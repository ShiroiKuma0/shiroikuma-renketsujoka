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

        // The latch lives on Backups, shared with the panel and the §2a data door: they all poll
        // one process-wide cancel flag, so only one of them may be running at a time.
        if (!Backups.claim()) {
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
                // ONE progress sender for both doors, parameterised — see
                // StateExportReceiver.progressReporter for why there is not a second one here.
                var progress = StateExportReceiver.progressReporter(
                        this, progressAction, replyPackage, replyId);

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
                Backups.release();
                stop.run();
            }
        }, "shiroikuma-export").start();

        return START_NOT_STICKY;
    }

    private void replyOnce(AtomicBoolean replied, String action, String pkg, String id, String result) {
        if (!replied.compareAndSet(false, true)) return;
        StateExportReceiver.reply(this, action, pkg, id, result);
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
