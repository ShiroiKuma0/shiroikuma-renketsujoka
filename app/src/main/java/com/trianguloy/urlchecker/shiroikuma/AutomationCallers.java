package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.Build;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Who is allowed through the automation data door, and how that is decided.
 *
 * <p>A Java port of the family reference, {@code AutomationCallers.kt} in
 * {@code shiroikuma-jiyusagyoban} — the shape is the reference's, the pins are byte-identical.
 *
 * <h3>Why not a token</h3>
 *
 * The token this replaces was a 48-character secret 白い熊 pasted from one app's settings into
 * another's. It cannot survive a wipe, which is fatal for the case the whole family now exists to
 * serve: 応用管理 restoring apps and their data onto a clean phone, where nothing is configured yet.
 *
 * <h3>Why not a {@code shiroikuma.*} prefix</h3>
 *
 * Because that is not an identity. What makes {@code getCallingPackage()} worth anything is that a
 * package name <b>cannot be taken while the real package is installed</b> — package names are not a
 * namespace anyone owns, so any sideloaded app may call itself {@code shiroikuma.evil} and pass a
 * prefix test. Since the caller supplies the file descriptor an export is written into, a prefix
 * check would hand such an app the complete data of every sister app in turn: strictly weaker than
 * the token it replaces.
 *
 * <h3>What is actually checked, in order</h3>
 *
 * <ol>
 *     <li><b>An exact name</b> from {@link #CALLERS}. Never a prefix.</li>
 *     <li><b>The uid agrees.</b> {@code getCallingPackage()} reflects the caller's declared
 *     attribution, and packages sharing a uid are not distinguished by it, so it is confirmed
 *     against the uid the kernel reports — which cannot be borrowed.</li>
 *     <li><b>The signing certificate matches a pinned hash.</b> This is the one that closes the real
 *     gap: <i>whichever caller package is absent from the device is a name anyone can take</i>, and
 *     the clean-phone case this contract exists for is precisely a device where not everything is
 *     installed yet — the moment the assumption is weakest is the moment it is most needed.</li>
 * </ol>
 */
public class AutomationCallers {

    /**
     * The apps allowed to drive this one's data door.
     *
     * <p>応用管理 backs up and restores; 自由作業盤 runs the 保存復元 batch. Nothing else has any
     * business exporting this app's data, and an entry added here is a deliberate act.
     */
    private static final Map<String, String> CALLERS = new LinkedHashMap<>();

    static {
        CALLERS.put("shiroikuma.oyokanri", "9c585f4d118cb97ff653f949a8872875548403b9083ce6b9baa2e8f0c55ac6cc");
        CALLERS.put("shiroikuma.jiyusagyoban", "efd0d352192651593a92288ecdc64fc87262ec8648c24ed8f51a5587d46ac602");
    }

    /**
     * Where those hashes come from, so the next person can re-derive them rather than trust them:
     *
     * <pre>apksigner verify --print-certs &lt;the app's signed release APK&gt; | grep 'SHA-256 digest'</pre>
     *
     * Every app in the family has <b>its own keystore</b> — 42 of them under
     * {@code ~/.android-keystores/} — so there is no shared signing key to compare against and each
     * caller must be pinned by name. That is also why a {@code protectionLevel="signature"}
     * permission was never an option here.
     *
     * <p><b>If a caller's key is ever rotated, its APK stops being able to call and the fix is
     * here.</b> That is the intended failure: a signing key changing without anyone noticing is
     * exactly what a pin exists to catch.
     */
    private static final String HOW_TO_DERIVE_PINS = "apksigner verify --print-certs <apk>";

    /**
     * The check answers a STRING and not a boolean, for the same reason {@link AutomationAuth#refuse}
     * does: a refusal that says only "no" is a refusal nobody can debug from the other side of an
     * IPC boundary. Each of these is a different mistake with a different fix, and the caller shows
     * them to 白い熊 verbatim.
     *
     * @param declared what {@code getCallingPackage()} said
     * @return null when the caller is allowed; otherwise the exact {@code ERROR:} string.
     */
    public static String verify(Context cntx, String declared) {
        if (declared == null || declared.isEmpty()) return "ERROR:caller unknown";
        var pin = CALLERS.get(declared);
        if (pin == null) return "ERROR:caller not permitted: " + declared;

        // The kernel's answer, not the caller's. A package may declare an attribution it does not
        // own; the uid cannot be borrowed.
        String[] real;
        try {
            real = cntx.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        } catch (Exception e) {
            real = null;
        }
        var matched = false;
        if (real != null) for (var name : real) if (declared.equals(name)) matched = true;
        if (!matched) return "ERROR:caller uid mismatch: " + declared;

        var signature = signingSha256(cntx, declared);
        if (signature == null) return "ERROR:caller signature unreadable: " + declared;
        // Constant-time, like the token compare it replaces — the value is a public hash, but the
        // habit is worth keeping and costs nothing.
        if (!MessageDigest.isEqual(signature.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                pin.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return "ERROR:caller signature mismatch: " + declared;
        }
        return null;
    }

    /**
     * The SHA-256 of the caller's current signing certificate, lower-case hex.
     *
     * <p>{@code signingInfo} rather than the deprecated {@code signatures}: a rotated key reports its
     * whole history and we want the certificate actually in force. But this app's {@code minSdk} is
     * <b>19</b>, and {@code GET_SIGNING_CERTIFICATES} is API 28 — on an older device the flag is
     * accepted and {@code signingInfo} comes back null, so WITHOUT the branch below the door would
     * refuse every caller. That failure would never appear on 白い熊's phone and would only surface
     * on an older one. The deprecated array is the correct answer there, not a compromise: before
     * key rotation existed, {@code signatures} WAS the signing certificate.
     *
     * <p>Exactly one signer, or we decline to guess. "Several signers, one of which matches" is a
     * question about key rotation that nothing in this family needs to answer — every app here has
     * one key and has never rotated it.
     */
    @SuppressWarnings("deprecation")
    private static String signingSha256(Context cntx, String pkg) {
        try {
            var pm = cntx.getPackageManager();
            Signature[] certs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                var info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
                certs = info == null ? null : info.getApkContentsSigners();
            } else {
                certs = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures;
            }
            if (certs == null || certs.length != 1) return null;
            var digest = MessageDigest.getInstance("SHA-256").digest(certs[0].toByteArray());
            var hex = new StringBuilder(digest.length * 2);
            for (var b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
