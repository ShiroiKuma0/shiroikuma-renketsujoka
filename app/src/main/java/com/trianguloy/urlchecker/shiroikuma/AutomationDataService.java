package com.trianguloy.urlchecker.shiroikuma;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import com.trianguloy.urlchecker.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipInputStream;

/**
 * Where a §2a data export or import actually runs.
 *
 * <h3>Why a foreground service and not the provider call</h3>
 *
 * <p>The call returns in milliseconds. Two hard reasons the work cannot be done anywhere cheaper,
 * even for an app whose export is a preferences dump:
 *
 * <ul>
 *     <li><b>A binder call holds the caller.</b> 応用管理 is drawing a list; a synchronous call that
 *     ran long would freeze its UI, report no progress and refuse cancellation.</li>
 *     <li><b>A backgrounded app writing is frozen mid-stream on this phone</b>, which yields a
 *     truncated archive underneath a success reply — the worst possible failure, because it is
 *     indistinguishable from a good backup until the day it is restored.</li>
 * </ul>
 *
 * <h3>The descriptor</h3>
 *
 * <p>Already duplicated by {@link AutomationProvider} before it got here, because the original
 * belongs to the binder transaction and is closed the moment {@code call()} returns. This service
 * owns the copy and closes it in a {@code finally} — leaking one would hold the caller's file open
 * indefinitely, and the caller cannot checksum or encrypt a file that is still open.
 */
public class AutomationDataService extends Service {

    private static final String CHANNEL = "shiroikuma_automation_data";
    private static final int NOTIFICATION_ID = 0x5C05;

    private static final String EXTRA_JOB = "job";
    private static final String EXTRA_IMPORTING = "importing";

    /**
     * The descriptor's way across, because an Intent is the wrong vehicle for one.
     *
     * <p>A {@link ParcelFileDescriptor} in an Intent extra is duplicated by the system on delivery
     * and the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
     * the job id keeps exactly one open descriptor with exactly one owner — this service, which
     * closes it in a {@code finally}.
     */
    private static final ConcurrentHashMap<String, ParcelFileDescriptor> HANDOVER = new ConcurrentHashMap<>();

    /**
     * Start the work.
     *
     * @return null when the service was started; otherwise the {@code ERROR:} string to answer the
     * caller with. A refusal here rather than an exception: the provider owes its caller a
     * {@code result}, and a system that declines a background foreground-service start is a normal
     * outcome to report, not a crash to propagate across a binder.
     */
    static String start(Context cntx, String jobId, ParcelFileDescriptor fd, boolean importing, Bundle extras) {
        HANDOVER.put(jobId, fd);
        var intent = new Intent(cntx, AutomationDataService.class);
        intent.putExtra(EXTRA_JOB, jobId);
        intent.putExtra(EXTRA_IMPORTING, importing);
        if (extras != null) {
            intent.putExtra(AutomationProvider.KEY_ITEMS, extras.getString(AutomationProvider.KEY_ITEMS));
            intent.putExtra(AutomationProvider.KEY_REPLY_ACTION, extras.getString(AutomationProvider.KEY_REPLY_ACTION));
            intent.putExtra(AutomationProvider.KEY_REPLY_PACKAGE, extras.getString(AutomationProvider.KEY_REPLY_PACKAGE));
            intent.putExtra(AutomationProvider.KEY_PROGRESS_ACTION, extras.getString(AutomationProvider.KEY_PROGRESS_ACTION));
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                cntx.startForegroundService(intent);
            } else {
                cntx.startService(intent);
            }
            return null;
        } catch (Exception e) {
            HANDOVER.remove(jobId);
            return "ERROR:could not start data service";
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        var importing = intent != null && intent.getBooleanExtra(EXTRA_IMPORTING, false);
        var jobId = intent == null ? null : intent.getStringExtra(EXTRA_JOB);

        // The descriptor comes OUT of the handover map before anything that can throw. Doing it
        // after startForeground — which can — would leave the caller's file held open in a static
        // map for the life of the process, with nobody left to close it.
        var fd = jobId == null ? null : HANDOVER.remove(jobId);

        // startForeground is owed to the platform the moment startForegroundService() was called,
        // whatever this service then decides to do — and it is owed BEFORE any early return, not
        // only on the path that does the work. Stopping without it does not cancel the promise: the
        // system kills the process with ForegroundServiceDidNotStartInTimeException, so a caller
        // retrying with a stale job id would KILL this app rather than be quietly ignored. Hence it
        // sits above the `fd == null` check, and the handover drain stays above it in turn so a
        // throw here cannot strand the caller's descriptor in a static map.
        try {
            // Must be inside 5 s of the service starting, or the system kills us for that instead.
            startForeground(NOTIFICATION_ID, notification(importing));
        } catch (Exception e) {
            // A background start the system declines. We hold the only copy of the descriptor.
            AutomationJobs.finish(jobId);
            close(fd);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (fd == null) {
            // A stale or replayed job id. Nothing to do, and nothing to answer — the job it names
            // was already run and already replied.
            stop(startId);
            return START_NOT_STICKY;
        }

        var replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION);
        var replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE);
        var progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION);
        var items = intent.getStringExtra(AutomationProvider.KEY_ITEMS);

        // Exactly one terminal answer per job, whatever path got here — a synchronous failure and an
        // asynchronous success must never both fire.
        var replied = new AtomicBoolean(false);

        new Thread(() -> {
            try {
                if (!Backups.claim()) {
                    reply(replied, jobId, replyAction, replyPackage, "ERROR:export already running");
                    return;
                }
                try {
                    Backups.clearCancel();
                    // A cancel for this job id reaches the export core, which polls one
                    // process-wide flag at category boundaries.
                    AutomationJobs.onCancel(jobId, Backups::requestCancel);
                    if (importing) {
                        runImport(fd, replied, jobId, replyAction, replyPackage);
                    } else {
                        runExport(fd, items, progressAction, replied, jobId, replyAction, replyPackage);
                    }
                } finally {
                    Backups.clearCancel();
                    Backups.release();
                }
            } catch (Throwable t) {
                var message = t.getMessage();
                reply(replied, jobId, replyAction, replyPackage,
                        "ERROR:" + (message == null ? t.getClass().getSimpleName() : message));
            } finally {
                // Already closed by the stream wrapper on the normal path; closing twice is safe,
                // and leaking one would hold the caller's file open indefinitely.
                close(fd);
                stop(startId);
            }
        }, "shiroikuma-automation-data").start();

        return START_NOT_STICKY;
    }

    /**
     * Write the archive straight into the caller's descriptor.
     *
     * <p>The bytes are counted as they go rather than stat'ed afterwards: the caller owns the file
     * and we may not be able to see it at all — it can be an anonymous pipe, or a descriptor into a
     * directory this app cannot list.
     */
    private void runExport(ParcelFileDescriptor fd, String items, String progressAction,
                           AtomicBoolean replied, String jobId, String replyAction, String replyPackage)
            throws Exception {
        var categories = resolve(items);
        if (categories == null) {
            reply(replied, jobId, replyAction, replyPackage, "ERROR:unknown category in items: " + items);
            return;
        }

        var written = new long[]{0};
        try (var out = new ParcelFileDescriptor.AutoCloseOutputStream(fd)) {
            var counting = new OutputStream() {
                @Override
                public void write(int b) throws java.io.IOException {
                    out.write(b);
                    written[0]++;
                }

                @Override
                public void write(byte[] b, int off, int len) throws java.io.IOException {
                    out.write(b, off, len);
                    written[0] += len;
                }
            };
            // §3 progress matters here too: a caller's watchdog presumes an app silent for two
            // minutes is dead. ONE sender, shared with the §1 door, correlated by the job id.
            Backups.writeTo(this, categories, counting,
                    StateExportReceiver.progressReporter(this, progressAction, replyPackage, jobId));
        } catch (Backups.CancelledException e) {
            reply(replied, jobId, replyAction, replyPackage, "ERROR:cancelled");
            return;
        }

        if (AutomationJobs.isCancelled(jobId) || Backups.isCancelled()) {
            reply(replied, jobId, replyAction, replyPackage, "ERROR:cancelled");
            return;
        }
        reply(replied, jobId, replyAction, replyPackage,
                "OK:" + written[0] + "|" + categories.size() + " categories");
    }

    /**
     * Read the whole archive before touching anything.
     *
     * <p>That is the right shape here for a reason beyond convenience: a partial read that failed
     * halfway would otherwise import half an archive, and a half-restored app is worse than one that
     * refused.
     */
    private void runImport(ParcelFileDescriptor fd, AtomicBoolean replied, String jobId,
                           String replyAction, String replyPackage) throws Exception {
        // Spooled to disk rather than into a byte array. The caller supplies the descriptor and
        // this app cannot bound what arrives on it — a settings ZIP is kilobytes, but an archive
        // carrying a corpus would be an OOM against a heap we do not control.
        var spool = new File(getCacheDir(), "automation-import-" + jobId + ".zip");
        try {
            long total = 0;
            try (var in = new ParcelFileDescriptor.AutoCloseInputStream(fd);
                 var out = new FileOutputStream(spool)) {
                var chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    out.write(chunk, 0, read);
                    total += read;
                }
            }
            if (total == 0) {
                reply(replied, jobId, replyAction, replyPackage, "ERROR:empty archive");
                return;
            }

            // Every category the archive actually carries, not every category we know about: asking
            // for one the archive lacks is how a restore ends up reporting success over nothing.
            var present = categoriesIn(spool);
            if (present.isEmpty()) {
                reply(replied, jobId, replyAction, replyPackage, "ERROR:archive carries no categories");
                return;
            }

            int restored;
            try (var in = new FileInputStream(spool)) {
                restored = Backups.readFrom(this, in, present);
            }
            if (restored < 0) {
                reply(replied, jobId, replyAction, replyPackage, "ERROR:archive could not be read");
                return;
            }
            // 応用管理 force-stops this app straight after a success. That is deliberate and belongs
            // on its side: a running process writes its cached SharedPreferences back out at orderly
            // shutdown and would silently undo the import that just happened.
            reply(replied, jobId, replyAction, replyPackage, "OK:" + restored + " restored");
        } finally {
            // The caller's data, in our cache. It does not outlive the job.
            spool.delete();
        }
    }

    /** Which of our categories are actually inside the archive. */
    private static List<String> categoriesIn(File archive) throws Exception {
        var known = Backups.allCategoryIds();
        var present = new ArrayList<String>();
        try (var zip = new ZipInputStream(new FileInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                var name = entry.getName();
                if (!name.endsWith(".json") || name.equals("manifest.json")) continue;
                var id = name.substring(0, name.length() - ".json".length());
                if (known.contains(id) && !present.contains(id)) present.add(id);
            }
        }
        return present;
    }

    /** Absent or empty {@code items} means our default set, which for this app is everything. */
    private static List<String> resolve(String items) {
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

    /**
     * The terminal answer, on a fresh broadcast — never a binder.
     *
     * <p>EMUI will not reliably carry a live Binder into another app's manifest receiver, which is
     * why the whole family answers this way. {@code FLAG_INCLUDE_STOPPED_PACKAGES} is what lets a
     * backgrounded caller hear us at all, and on a clean phone the caller may not have been launched
     * even once.
     */
    private void reply(AtomicBoolean replied, String jobId, String action, String pkg, String result) {
        if (!replied.compareAndSet(false, true)) return;
        AutomationJobs.finish(jobId);
        if (action == null || action.isEmpty() || pkg == null || pkg.isEmpty()) return;
        var intent = new Intent(action);
        intent.setPackage(pkg);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.putExtra(AutomationProvider.KEY_JOB_ID, jobId);
        intent.putExtra(AutomationProvider.KEY_RESULT, result);
        try {
            sendBroadcast(intent);
        } catch (Exception ignored) {
            // nothing to fall back to; the caller times the slot out
        }
    }

    private static void close(ParcelFileDescriptor fd) {
        if (fd == null) return;
        try {
            fd.close();
        } catch (Exception ignored) {
            // nothing further to do
        }
    }

    private void stop(int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf(startId);
    }

    private Notification notification(boolean importing) {
        var text = getString(importing ? R.string.sk_importNotification : R.string.sk_exportNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL, getString(R.string.sk_automationDataChannel),
                        NotificationManager.IMPORTANCE_LOW));
            }
            return new Notification.Builder(this, CHANNEL)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(text)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .build();
        }
        return new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();
    }
}
