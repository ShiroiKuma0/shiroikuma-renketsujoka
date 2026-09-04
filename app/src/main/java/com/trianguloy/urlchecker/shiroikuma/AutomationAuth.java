package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * The gate for the 保存復元 automation contract, v2.
 *
 * <p>v1 shipped this app closed: automation defaulted to OFF and every request also had to carry a
 * 48-character secret 白い熊 had pasted from here into the caller. That is the wrong shape for where
 * the contract went. <b>A pasted secret cannot survive a wipe</b>, and the case the family now
 * exists to serve is 応用管理 restoring apps <i>and their data</i> onto a clean phone, where nothing
 * has been configured and nobody has pasted anything. A gate that only works once the phone is
 * already set up is no gate for setting the phone up.
 *
 * <p>So the master switch defaults ON, the token is opt-in, and the identity check that actually
 * protects the data door moved to {@link AutomationCallers} — which knows who is calling, something
 * a shared secret never told us.
 *
 * <p>These values live in their OWN preferences file, deliberately separate from the app's
 * settings: {@link Backups} exports the app's preferences wholesale, and a token must never travel
 * inside a backup. Keeping it in a different file makes that structural rather than a rule someone
 * has to remember.
 */
public class AutomationAuth {

    /** Device-local, never exported. */
    private static final String PREFS = "shiroikuma_automation";
    private static final String KEY_ENABLED = "automation_enabled";
    private static final String KEY_REQUIRE_TOKEN = "automation_require_token";
    private static final String KEY_TOKEN = "automation_token";

    private static SharedPreferences prefs(Context cntx) {
        return cntx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * The master switch. <b>Default ON in v2.</b>
     *
     * <p>It stays a switch rather than being removed because it is the only way to close this app
     * off entirely, and a feature that can be turned on but never off is one 白い熊 cannot retreat
     * from. An install that explicitly turned it off in v1 keeps that stored {@code false}; one that
     * never touched it now reads ON, which is the intended migration.
     */
    public static boolean enabled(Context cntx) {
        return prefs(cntx).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context cntx, boolean enabled) {
        prefs(cntx).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Whether a caller must also present the token. <b>Default OFF.</b> */
    public static boolean requireToken(Context cntx) {
        return prefs(cntx).getBoolean(KEY_REQUIRE_TOKEN, false);
    }

    public static void setRequireToken(Context cntx, boolean require) {
        prefs(cntx).edit().putBoolean(KEY_REQUIRE_TOKEN, require).apply();
    }

    /**
     * The whole gate, in ONE function — deliberately, because two checks written out at each entry
     * point is how "disabled" and "bad token" drift apart across forty-two apps.
     *
     * <p><b>A token handed to this app while it does not require one is IGNORED, never an error.</b>
     * Tokens live in task arguments and workspace variables that outlive the setting they were
     * pasted for, and a caller still sending one — because it was configured last year, or because
     * another app on the batch does want one — must be served. Refusing it would turn "白い熊 turned
     * a switch off" into "half the batch mysteriously fails", which is precisely the friction the
     * switch exists to remove.
     *
     * @return null to proceed; otherwise the exact {@code ERROR:} string to answer with.
     */
    public static String refuse(Context cntx, String candidate) {
        if (!enabled(cntx)) return "ERROR:automation disabled";
        if (requireToken(cntx) && !isTokenValid(cntx, candidate)) return "ERROR:bad token";
        return null;
    }

    /** The token, generated on first read so the settings row always has something to show. */
    public static String token(Context cntx) {
        var stored = prefs(cntx).getString(KEY_TOKEN, null);
        if (stored != null && !stored.isEmpty()) return stored;
        return regenerate(cntx);
    }

    /** A fresh 24-byte token. Any copy pasted elsewhere stops working the moment this is called. */
    public static String regenerate(Context cntx) {
        var bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        var hex = new StringBuilder(bytes.length * 2);
        for (var b : bytes) hex.append(String.format("%02x", b));
        var token = hex.toString();
        prefs(cntx).edit().putString(KEY_TOKEN, token).apply();
        return token;
    }

    /**
     * Constant-time comparison — a token check must not leak its answer through timing. Kept for
     * the case where the token IS required; it is simply not consulted when it is not.
     */
    public static boolean isTokenValid(Context cntx, String candidate) {
        if (candidate == null) return false;
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                token(cntx).getBytes(StandardCharsets.UTF_8));
    }

    /** How the token is shown in the settings row: first and last 8 characters. */
    public static String abbreviated(Context cntx) {
        var token = token(cntx);
        if (token.length() <= 20) return token;
        return token.substring(0, 8) + "…" + token.substring(token.length() - 8);
    }
}
