package com.trianguloy.urlchecker.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;

import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.utilities.AndroidSettings;
import com.trianguloy.urlchecker.utilities.methods.AndroidUtils;
import com.trianguloy.urlchecker.utilities.methods.LocaleUtils;

/**
 * The 白い熊 連結浄化 UI page: every setting this fork adds on top of upstream lives here, so the
 * stock settings screen stays recognisably upstream's and our layer is auditable in one place.
 * Reached from Settings. Empty for now — contents to follow.
 */
public class ShiroikumaUiActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidSettings.setTheme(this, false);
        LocaleUtils.setLocale(this);
        setContentView(R.layout.activity_shiroikuma_ui);
        setTitle(R.string.a_shiroikumaUi);
        AndroidUtils.configureUp(this);

        // if this was reloaded, some settings may have changed, so reload the previous one too
        if (AndroidSettings.wasReloaded(this)) AndroidSettings.markForReloading(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // press the 'back' button in the action bar to go back
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
