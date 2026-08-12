package com.trianguloy.urlchecker.modules.companions;

import android.app.Activity;
import android.content.Context;

import com.trianguloy.urlchecker.BuildConfig;
import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.activities.TutorialActivity;
import com.trianguloy.urlchecker.modules.AutomationRules;
import com.trianguloy.urlchecker.utilities.generics.GenericPref;
import com.trianguloy.urlchecker.utilities.generics.GenericPref.StringPref;
import com.trianguloy.urlchecker.utilities.methods.AndroidUtils;

import org.json.JSONObject;


/** Manages the app version, to notify of updates */
public class VersionManager {

    private final StringPref lastVersion;

    public static StringPref LASTVERSION_PREF(Context cntx) {
        return new StringPref("changelog_lastVersion", null, cntx);
    }

    /* ------------------- static ------------------- */

    /** Check if the version must be updated */
    public static void check(Activity cntx) {
        // just call the constructor, it does the check
        new VersionManager(cntx);
    }

    /**
     * Returns true iff [versionCode] is newer than the build running now.
     *
     * <p>Compares the integer versionCode. Upstream parsed the version NAME by pulling every number
     * out of it, which this fork's name breaks outright: `3.5+2026-07-25.15-05.g03a11762+014` yields
     * `[3, 5, 2026, 7, 25, 15, 5, 3, 11762, 14]`, where `3` and `11762` come from the commit SHA and
     * sit in sort-significant positions ahead of the build counter. Two builds would then be ordered
     * by hex digits. The versionCode is the number Android itself orders by and has no such problem.
     *
     * <p>A backup written before this field existed carries no code; treat it as NOT newer rather
     * than guessing, since a wrong "this backup is from a newer version" warning is worse than none.
     */
    public static boolean isVersionCodeNewer(String versionCode) {
        if (versionCode == null || versionCode.isEmpty()) return false;
        try {
            return Integer.parseInt(versionCode.trim()) > BuildConfig.VERSION_CODE;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /* ------------------- instance ------------------- */

    public VersionManager(Activity cntx) {
        lastVersion = LASTVERSION_PREF(cntx);
        if (lastVersion.get() == null) {
            // no previous setting, the app is a new install, mark as seen
            // ... or maybe it was updated from an old version (where the setting was not yet implemented, 2.12 or below)
            // we check by testing the tutorial flag (which should be set if the app was used)
            if (TutorialActivity.DONE(cntx).get()) lastVersion.set("<2.12");
            else markSeen();
        }

        // --- run migrations --- //
        var prefs = GenericPref.getPrefs(cntx);

        // status module auto-check -> automation
        try {
            var regex = prefs.getString("statusCode_autoCheck", "");
            if (!regex.isEmpty()) {
                var automationRules = new AutomationRules(cntx);
                var catalog = automationRules.getCatalog();
                var name = cntx.getString(R.string.mStatus_check);
                if (!catalog.has(name)) {
                    catalog.put(name, new JSONObject()
                            .put("regex", regex)
                            .put("action", "checkStatus")
                    );
                }
                automationRules.save(catalog);
                prefs.edit().remove("statusCode_autoCheck").apply();
            }
        } catch (Exception e) {
            AndroidUtils.assertError("Unable to migrate statusCode_autoCheck to automation", e);
        }
    }

    /** returns true iff the app was updated since last time it was used */
    public boolean wasUpdated() {
        // just check inequality. If the app was downgraded, you probably also want to be notified.
        return !BuildConfig.VERSION_NAME.equals(lastVersion.get());
    }

    /** Marks the current version as seen (wasUpdated will return false until a new update happens) */
    public void markSeen() {
        lastVersion.set(BuildConfig.VERSION_NAME);
    }

}
