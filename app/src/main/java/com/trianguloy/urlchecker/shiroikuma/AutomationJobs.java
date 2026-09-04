package com.trianguloy.urlchecker.shiroikuma;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The jobs the data door has started, and the flag each of them watches to stop.
 *
 * <p>What this owns is the mapping from the id a caller was handed to a cancellation it can act on,
 * which must outlive the binder call that created it and be reachable from a service that never saw
 * the caller.
 *
 * <p>The hook exists because this app's export core, {@link Backups}, polls a single process-wide
 * cancel flag it shares with the §1 broadcast path. Rather than teach {@code Backups} about job ids
 * — or teach {@link AutomationProvider} about {@code Backups} — whoever runs a job registers how to
 * stop it, and a cancel for that id runs it.
 */
public class AutomationJobs {

    private static final ConcurrentHashMap<String, Boolean> cancelled = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Runnable> hooks = new ConcurrentHashMap<>();

    public static String begin() {
        var id = UUID.randomUUID().toString();
        cancelled.put(id, false);
        return id;
    }

    /** How to stop job {@code jobId}, registered by whoever is running it. */
    public static void onCancel(String jobId, Runnable hook) {
        if (jobId != null && hook != null) hooks.put(jobId, hook);
    }

    /**
     * Ask a job to stop. A no-op for an id that is finished or was never real.
     *
     * <p>Deliberately silent: a cancel arriving after the work completed is the normal race, not an
     * error, and answering it as one would make every well-behaved caller look broken.
     */
    public static void cancel(String jobId) {
        if (jobId == null) return;
        if (cancelled.replace(jobId, true) == null) return;
        var hook = hooks.get(jobId);
        if (hook != null) hook.run();
    }

    /** Polled at write boundaries — never mid-write, so a cancelled archive is never half a file. */
    public static boolean isCancelled(String jobId) {
        return jobId != null && Boolean.TRUE.equals(cancelled.get(jobId));
    }

    public static void finish(String jobId) {
        if (jobId == null) return;
        cancelled.remove(jobId);
        hooks.remove(jobId);
    }
}
