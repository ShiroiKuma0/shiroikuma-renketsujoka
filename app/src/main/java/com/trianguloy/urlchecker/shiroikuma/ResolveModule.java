package com.trianguloy.urlchecker.shiroikuma;

import static com.trianguloy.urlchecker.utilities.methods.AndroidUtils.MARKER;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.activities.ModulesActivity;
import com.trianguloy.urlchecker.dialogs.MainDialog;
import com.trianguloy.urlchecker.modules.AModuleConfig;
import com.trianguloy.urlchecker.modules.AModuleData;
import com.trianguloy.urlchecker.modules.AModuleDialog;
import com.trianguloy.urlchecker.modules.AutomationRules;
import com.trianguloy.urlchecker.url.UrlData;
import com.trianguloy.urlchecker.utilities.generics.GenericPref.BoolPref;
import com.trianguloy.urlchecker.utilities.generics.GenericPref.IntPref;
import com.trianguloy.urlchecker.utilities.methods.AndroidUtils;
import com.trianguloy.urlchecker.utilities.methods.HttpUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a link here, on the phone, and replaces it with where it actually goes — tracking
 * stripped off.
 *
 * <p>The fork's replacement for the Unshortener module, which sends every link to unshorten.me and
 * shows whatever that service says back. All the work is in {@link LinkResolver}; this class is the
 * button, the preferences and the wiring into the url pipeline.
 *
 * <p>Turning "resolve automatically" on is what makes the app open the destination rather than the
 * redirector: replacing the url re-runs the whole module pipeline over the new one, so Clear URL
 * gets a second pass at it with its own catalogue, and the Open module opens the result.
 */
public class ResolveModule extends AModuleData {

    public static final String ID = "skResolve";

    public static BoolPref AUTO_PREF(Context cntx) {
        return new BoolPref("sk_resolve_auto", false, cntx);
    }

    public static BoolPref NETWORK_PREF(Context cntx) {
        return new BoolPref("sk_resolve_network", true, cntx);
    }

    public static BoolPref META_PREF(Context cntx) {
        return new BoolPref("sk_resolve_meta", true, cntx);
    }

    public static BoolPref STRIP_PREF(Context cntx) {
        return new BoolPref("sk_resolve_strip", true, cntx);
    }

    public static IntPref HOPS_PREF(Context cntx) {
        return new IntPref("sk_resolve_hops", 10, cntx);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getName() {
        return R.string.sk_resolve_name;
    }

    @Override
    public AModuleDialog getDialog(MainDialog cntx) {
        return new ResolveDialog(cntx);
    }

    @Override
    public AModuleConfig getConfig(ModulesActivity cntx) {
        return new ResolveConfig(cntx);
    }

    @Override
    public List<AutomationRules.Automation<AModuleDialog>> getAutomations() {
        return (List<AutomationRules.Automation<AModuleDialog>>) (List<?>) ResolveDialog.AUTOMATIONS;
    }
}

class ResolveConfig extends AModuleConfig {

    public ResolveConfig(ModulesActivity cntx) {
        super(cntx);
    }

    @Override
    public int getLayoutId() {
        return R.layout.config_resolve;
    }

    @Override
    public void onInitialize(View views) {
        var cntx = getActivity();
        ResolveModule.AUTO_PREF(cntx).attachToSwitch(views.findViewById(R.id.auto));
        ResolveModule.NETWORK_PREF(cntx).attachToSwitch(views.findViewById(R.id.network));
        ResolveModule.META_PREF(cntx).attachToSwitch(views.findViewById(R.id.meta));
        ResolveModule.STRIP_PREF(cntx).attachToSwitch(views.findViewById(R.id.strip));
        // 0 is not a sensible hop count, so it is free to stand for "field left empty"
        ResolveModule.HOPS_PREF(cntx).attachToEditText(views.findViewById(R.id.hops), 0);
    }
}

class ResolveDialog extends AModuleDialog {

    static final List<AutomationRules.Automation<ResolveDialog>> AUTOMATIONS = List.of(
            new AutomationRules.Automation<>("resolve", R.string.sk_auto_resolve, dialog ->
                    dialog.resolve(dialog.getUrlData().disableUpdates))
    );

    /** Marks a url this module produced, so the automatic pass cannot chase its own tail. */
    private static final String RESOLVED = "skResolve.resolved";

    private final BoolPref auto;
    private final BoolPref network;
    private final BoolPref meta;
    private final BoolPref strip;
    private final IntPref hops;

    private Button resolve;
    private TextView info;
    private TextView destination;

    private Thread thread = null;

    public ResolveDialog(MainDialog dialog) {
        super(dialog);
        auto = ResolveModule.AUTO_PREF(dialog);
        network = ResolveModule.NETWORK_PREF(dialog);
        meta = ResolveModule.META_PREF(dialog);
        strip = ResolveModule.STRIP_PREF(dialog);
        hops = ResolveModule.HOPS_PREF(dialog);
    }

    @Override
    public int getLayoutId() {
        return R.layout.dialog_resolve;
    }

    @Override
    public void onInitialize(View views) {
        resolve = views.findViewById(R.id.resolve);
        resolve.setText(R.string.sk_resolve_resolve);
        resolve.setOnClickListener(v -> resolve(false));

        info = views.findViewById(R.id.info);
        destination = views.findViewById(R.id.destination);
        destination.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public void onPrepareUrl(UrlData urlData) {
        // a new url arrived; whatever is still running is about the old one
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    @Override
    public void onDisplayUrl(UrlData urlData) {
        resolve.setEnabled(true);
        AndroidUtils.setHideableText(info, null);
        AndroidUtils.setHideableText(destination, null);
        AndroidUtils.clearRoundedColor(info);

        // resolve on sight, unless this url is the one we just produced
        if (auto.get() && urlData.getData(RESOLVED) == null && !urlData.disableUpdates) {
            resolve(false);
        }
    }

    /** Starts a resolve in the background. */
    private void resolve(boolean disableUpdates) {
        resolve.setEnabled(false);
        AndroidUtils.setHideableText(info, getActivity().getString(R.string.sk_resolve_resolving));
        AndroidUtils.setHideableText(destination, null);
        AndroidUtils.clearRoundedColor(info);

        var url = getUrl();
        var options = new LinkResolver.Options(
                network.get(),
                meta.get(),
                strip.get(),
                Math.max(1, hops.get()),
                HttpUtils.CONNECT_TIMEOUT,
                HttpUtils.CONNECT_TIMEOUT
        );

        thread = new Thread(() -> {
            var result = LinkResolver.resolve(url, options);
            if (Thread.currentThread().isInterrupted()) return;
            getActivity().runOnUiThread(() -> show(result, disableUpdates));
        });
        thread.start();
    }

    /** Reports what the resolve found, and applies it unless updates are off. */
    private void show(LinkResolver.Result result, boolean disableUpdates) {
        resolve.setEnabled(true);

        if (!result.changed()) {
            // nothing to do: either it was already the destination, or the network refused
            if (result.error() != null) {
                AndroidUtils.setHideableText(info,
                        getActivity().getString(R.string.sk_resolve_error, result.error()));
                AndroidUtils.setRoundedColor(R.color.warning, info);
            } else {
                AndroidUtils.setHideableText(info, getActivity().getString(R.string.sk_resolve_same));
            }
            return;
        }

        AndroidUtils.setHideableText(info, summary(result));
        AndroidUtils.setRoundedColor(R.color.good, info);

        if (disableUpdates) {
            // can't replace the url, so offer it instead
            AndroidUtils.setHideableText(destination, AndroidUtils.underlineUrl(
                    getActivity().getString(R.string.sk_resolve_to, MARKER), result.url(), this::apply));
        } else {
            apply(result.url());
        }
    }

    private void apply(String url) {
        setUrl(new UrlData(url).putData(RESOLVED, RESOLVED));
    }

    /** One line per thing that happened, so it is clear what the url was put through. */
    private String summary(LinkResolver.Result result) {
        var lines = new ArrayList<String>();

        if (result.unwrapped()) lines.add(getActivity().getString(R.string.sk_resolve_unwrapped));
        if (result.hops() > 0) {
            lines.add(getActivity().getResources()
                    .getQuantityString(R.plurals.sk_resolve_followed, result.hops(), result.hops()));
        }
        if (result.stripped()) lines.add(getActivity().getString(R.string.sk_resolve_stripped));
        if (result.maxedOut()) lines.add(getActivity().getString(R.string.sk_resolve_maxed));
        if (result.error() != null) {
            lines.add(getActivity().getString(R.string.sk_resolve_error, result.error()));
        }

        var text = new StringBuilder();
        for (var line : lines) {
            if (text.length() > 0) text.append('\n');
            text.append(line);
        }
        return text.toString();
    }
}
