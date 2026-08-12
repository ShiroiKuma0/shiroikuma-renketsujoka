package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * The token gate for the 保存復元 automation contract.
 *
 * <p>Nothing is reachable until {@link #enabled} is turned on — it defaults to OFF — and every
 * request must additionally carry the token.
 *
 * <p>These two values live in their OWN preferences file, deliberately separate from the app's
 * settings: {@link Backups} exports the app's preferences wholesale, and a token must never travel
 * inside a backup. Keeping it in a different file makes that structural rather than a rule someone
 * has to remember.
 */
public class AutomationAuth {

    /** Device-local, never exported. */
    private static final String PREFS = "shiroikuma_automation";
    private static final String KEY_ENABLED = "automation_enabled";
    private static final String KEY_TOKEN = "automation_token";

    private static SharedPreferences prefs(Context cntx) {
        return cntx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context cntx) {
        return prefs(cntx).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context cntx, boolean enabled) {
        prefs(cntx).edit().putBoolean(KEY_ENABLED, enabled).apply();
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

    /** Constant-time comparison — a token check must not leak its answer through timing. */
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
