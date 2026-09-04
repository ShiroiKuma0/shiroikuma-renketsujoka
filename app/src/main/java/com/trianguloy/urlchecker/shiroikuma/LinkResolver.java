package com.trianguloy.urlchecker.shiroikuma;

import android.net.Uri;
import android.util.Log;

import com.trianguloy.urlchecker.utilities.methods.UrlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Works out where a link actually goes, on this device, and hands back the destination with the
 * tracking stripped off it.
 *
 * <p>This is the fork's answer to the Unshortener module. That module posts the link to
 * unshorten.me and shows whatever comes back — which is how a raw PostgreSQL
 * "value too long for type character varying(100)" ends up in the dialog: their column is a
 * varchar(100) and a mail-tracking link is routinely longer than that. Handing every link the user
 * opens to a third party to be recorded is also the opposite of what this app is for.
 *
 * <p>The order of work here is deliberate:
 *
 * <ol>
 *     <li><b>Unwrap offline first.</b> Most wrappers carry the destination in the link itself
 *     ({@code ?url=…}, {@code ?redirect_uri=…}, an absolute url sitting in the path). Reading it out
 *     costs no request, and — the point — never tells the tracker the link was opened.</li>
 *     <li><b>Then follow the chain over the network</b>, one hop at a time, refusing to
 *     auto-follow so every hop is visible, unwrapping offline again after each hop.</li>
 *     <li><b>Then strip the tracking parameters</b> from whatever we landed on. This has to be
 *     last: {@code e=}, {@code m=} and friends on the redirector are what makes the redirector
 *     answer, so stripping them before the chain is walked breaks the resolution.</li>
 * </ol>
 *
 * <p>Nothing here touches the UI or a preference; {@link ResolveModule} owns all of that. Nothing
 * here leaves the device either, beyond the requests to the hosts in the chain itself.
 */
public class LinkResolver {

    private static final String TAG = "RESOLVE";

    /** Enough of a browser to satisfy redirectors that refuse the stock java user agent. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36";
    private static final String ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    /** How much of a page body to look at when hunting for a meta-refresh; the head is enough. */
    private static final int MAX_BODY = 96 * 1024;

    /** Cap on offline unwraps of a single url, so a self-referencing wrapper cannot spin. */
    private static final int MAX_UNWRAPS = 8;

    /* ------------------- inputs and outputs ------------------- */

    /** What the caller wants done. */
    public record Options(
            boolean network,
            boolean followMeta,
            boolean strip,
            int maxHops,
            int connectTimeout,
            int readTimeout
    ) {
    }

    /** What was found. */
    public record Result(
            String url,
            List<String> chain,
            int hops,
            boolean unwrapped,
            boolean stripped,
            boolean maxedOut,
            String error
    ) {
        /** Whether the destination differs from the link we were given. */
        public boolean changed() {
            return !chain.isEmpty() && !url.equals(chain.get(0));
        }
    }

    /* ------------------- the resolve ------------------- */

    /**
     * Resolves [startUrl]. Blocking — call it off the main thread. Honours interruption between
     * hops, so a cancelled check stops at the next boundary rather than finishing the chain.
     */
    public static Result resolve(String startUrl, Options options) {
        var chain = new ArrayList<String>();
        var seen = new LinkedHashSet<String>();
        var jar = new HashMap<String, Map<String, String>>();

        var current = startUrl;
        chain.add(current);
        seen.add(current);

        var unwrapped = false;
        var maxedOut = false;
        var hops = 0;
        String error = null;

        // 1. the destination is often already in our hands
        var offline = unwrap(current);
        if (!offline.equals(current)) {
            unwrapped = true;
            current = offline;
            chain.add(current);
            seen.add(current);
        }

        // 2. walk whatever is left
        if (options.network()) {
            while (true) {
                if (hops >= options.maxHops()) {
                    maxedOut = true;
                    break;
                }

                String next;
                try {
                    next = hop(current, jar, options);
                } catch (IOException e) {
                    Log.d(TAG, "hop failed on " + current, e);
                    error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    break;
                } catch (Exception e) {
                    Log.d(TAG, "malformed hop from " + current, e);
                    error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    break;
                }

                // not a redirect: we have arrived
                if (next == null) break;

                var recovered = unwrap(next);
                if (!recovered.equals(next)) {
                    unwrapped = true;
                    next = recovered;
                }

                // a redirect back to something already visited is a loop, not progress
                if (!seen.add(next)) break;

                chain.add(next);
                current = next;
                hops++;

                if (Thread.currentThread().isInterrupted()) break;
            }
        }

        // 3. and only now take the tracking off
        var stripped = false;
        if (options.strip()) {
            var clean = stripTracking(current);
            if (!clean.equals(current)) {
                stripped = true;
                current = clean;
                chain.add(current);
            }
        }

        return new Result(current, chain, hops, unwrapped, stripped, maxedOut, error);
    }

    /**
     * One hop. Returns the absolute url this one redirects to, or null if it redirects nowhere.
     *
     * <p>A GET is used rather than a HEAD: plenty of redirectors answer a HEAD with 405 or with a
     * different Location than they give a browser, and since the body is simply not read for a
     * redirect the two cost the same. When a meta-refresh is being looked for the body is wanted
     * anyway.
     */
    private static String hop(String url, Map<String, Map<String, String>> jar, Options options) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false); // every hop must stay visible
            conn.setConnectTimeout(options.connectTimeout());
            conn.setReadTimeout(options.readTimeout());
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", ACCEPT);
            // deliberately no Accept-Encoding: setting it by hand turns off the transparent gunzip

            var host = conn.getURL().getHost();
            var cookies = cookieHeader(jar, host);
            if (cookies != null) conn.setRequestProperty("Cookie", cookies);

            var code = conn.getResponseCode();
            Log.d(TAG, code + " " + url);
            storeCookies(jar, host, conn);

            if (code >= 300 && code < 400) {
                var location = conn.getHeaderField("Location");
                if (location == null || location.trim().isEmpty()) return null;
                // Location is allowed to be relative
                return new URL(new URL(url), location.trim()).toExternalForm();
            }

            if (options.followMeta() && code >= 200 && code < 300 && isHtml(conn)) {
                var target = findInPage(readLimited(conn, MAX_BODY));
                if (target != null) return new URL(new URL(url), target).toExternalForm();
            }

            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /* ------------------- offline unwrapping ------------------- */

    /** Parameter names that carry the real destination. */
    private static final Set<String> WRAPPERS = Set.of(
            "url", "u", "uri", "to", "dest", "destination", "target", "targeturl",
            "link", "redirect", "redir", "redirect_url", "redirect_uri", "redirecturl",
            "out", "next", "continue", "return", "returnurl", "return_url",
            "goto", "go", "originalurl", "imgurl", "curl", "ref_url", "desturl");

    /**
     * Parameter names that carry what somebody typed, never a destination. Without this a search
     * for a url — {@code duckduckgo.com/?q=https://example.com} — would "resolve" to the thing
     * being searched for.
     */
    private static final Set<String> QUERIES = Set.of(
            "q", "query", "s", "search", "text", "keyword", "keywords", "kw", "p", "wd", "term");

    /** Pulls the destination out of a wrapper link, repeatedly, without any request. */
    public static String unwrap(String url) {
        var current = url;
        for (var i = 0; i < MAX_UNWRAPS; i++) {
            var next = unwrapOnce(current);
            if (next.equals(current)) break;
            current = next;
        }
        return current;
    }

    private static String unwrapOnce(String url) {
        // an absolute url parked in the path: https://wrapper.example/https://real.example/page
        var embedded = pathEmbedded(url);
        if (embedded != null) return embedded;

        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            return url;
        }

        var names = queryNames(uri);
        if (names.isEmpty()) return url;

        // a parameter named for the job wins over anything else
        for (var name : names) {
            if (!WRAPPERS.contains(name.toLowerCase(Locale.ROOT))) continue;
            var candidate = candidate(uri, name);
            if (candidate != null) return candidate;
        }

        // google's /url?q= is the one place a query parameter really is the destination
        if ("/url".equals(uri.getPath())) {
            var candidate = candidate(uri, "q");
            if (candidate != null) return candidate;
        }

        // otherwise any parameter that is plainly a url, preferring the longest of them
        String best = null;
        for (var name : names) {
            if (QUERIES.contains(name.toLowerCase(Locale.ROOT))) continue;
            var candidate = candidate(uri, name);
            if (candidate != null && (best == null || candidate.length() > best.length())) best = candidate;
        }
        return best != null ? best : url;
    }

    /** The value of [name] if it is, or decodes to, an absolute http(s) url. */
    private static String candidate(Uri uri, String name) {
        String raw;
        try {
            raw = uri.getQueryParameter(name);
        } catch (Exception e) {
            return null;
        }
        if (raw == null) return null;

        // Uri already decoded once; a doubly-encoded wrapper needs another pass or two
        var value = raw.trim();
        for (var i = 0; i < 3; i++) {
            if (isAbsolute(value)) return value;
            var decoded = UrlUtils.decode(value);
            if (decoded.equals(value)) break;
            value = decoded.trim();
        }
        return isAbsolute(value) ? value : null;
    }

    /** An absolute url sitting inside the path, rather than in a parameter. */
    private static String pathEmbedded(String url) {
        var scheme = url.indexOf("://");
        if (scheme < 0) return null;
        var pathStart = url.indexOf('/', scheme + 3);
        if (pathStart < 0) return null;

        for (var marker : List.of("/https://", "/http://")) {
            var at = url.indexOf(marker, pathStart);
            if (at < 0) continue;
            var candidate = url.substring(at + 1);
            if (isAbsolute(candidate)) return candidate;
        }
        return null;
    }

    /** Whether [value] is an absolute http(s) url with a plausible host. */
    private static boolean isAbsolute(String value) {
        var lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        try {
            var host = new URL(value).getHost();
            return host != null && host.contains(".");
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> queryNames(Uri uri) {
        try {
            // throws for opaque uris, and Uri.parse is lenient enough to produce plenty of those
            return new ArrayList<>(uri.getQueryParameterNames());
        } catch (Exception e) {
            return List.of();
        }
    }

    /* ------------------- tracking parameters ------------------- */

    /**
     * Whole parameter names that exist only to identify who clicked.
     *
     * <p>This list is a floor, not a replacement for the ClearURLs catalogue the app already
     * carries and can update — the Clear URL module still runs over whatever we hand back. It is
     * here so that this module is useful on its own, and it is kept to names that are
     * unambiguously tracking. Anything a site might route on is left alone on purpose: no
     * {@code id}, no {@code cid}, no {@code ref}, and notably no {@code t}, which is the timestamp
     * on a youtu.be link.
     */
    private static final Set<String> TRACKERS = Set.of(
            // ad networks
            "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "twclid",
            "ttclid", "igshid", "igsh", "yclid", "epik", "rb_clickid", "srsltid",
            // mail blasts
            "mc_cid", "mc_eid", "mkt_tok", "_hsenc", "_hsmi", "hsctatracking",
            "__hssc", "__hstc", "__hsfp", "vero_id", "vero_conv", "elqtrackid", "elqtrack",
            "_openstat", "wickedid", "oly_anon_id", "oly_enc_id", "wt_zmc", "_branch_match_id",
            "_branch_referrer",
            // analytics linkers and campaign ids
            "_ga", "_gl", "s_cid", "cmpid", "campaign_id", "icid", "ncid", "trk", "trkcampaign",
            // social share stamps
            "ref_src", "ref_url", "share_source", "share_medium", "si", "feature",
            "spm", "scm", "guccounter", "guce_referrer", "guce_referrer_sig");

    /** Parameter name prefixes that mark a whole family of tracking parameters. */
    private static final List<String> TRACKER_PREFIXES = List.of(
            "utm_", "pk_", "piwik_", "matomo_", "mtm_", "stm_", "itm_", "at_", "hsa_",
            "vero_", "oly_", "wt_", "wt.", "sc_", "trk_", "__hs", "guce_");

    /**
     * Removes the known tracking parameters from [url]. Works on the raw text so that everything
     * kept keeps its original encoding — re-encoding a url is a good way to break a signed one.
     */
    public static String stripTracking(String url) {
        var hash = url.indexOf('#');
        var fragment = hash < 0 ? "" : url.substring(hash + 1);
        var head = hash < 0 ? url : url.substring(0, hash);

        var mark = head.indexOf('?');
        var path = mark < 0 ? head : head.substring(0, mark);
        var query = mark < 0 ? "" : head.substring(mark + 1);

        var keptQuery = filterParams(query);
        // some sites put their parameters after the # instead; only touch it if it looks like a query
        var keptFragment = fragment.contains("=") ? filterParams(fragment) : fragment;

        var out = new StringBuilder(path);
        if (!keptQuery.isEmpty()) out.append('?').append(keptQuery);
        if (!keptFragment.isEmpty()) out.append('#').append(keptFragment);
        return out.toString();
    }

    private static String filterParams(String query) {
        if (query.isEmpty()) return "";
        var kept = new StringBuilder();
        for (var part : query.split("&")) {
            if (part.isEmpty()) continue;
            var equals = part.indexOf('=');
            var name = (equals < 0 ? part : part.substring(0, equals)).toLowerCase(Locale.ROOT);
            if (isTracker(name)) continue;
            if (kept.length() > 0) kept.append('&');
            kept.append(part);
        }
        return kept.toString();
    }

    private static boolean isTracker(String name) {
        if (TRACKERS.contains(name)) return true;
        for (var prefix : TRACKER_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    /* ------------------- page-level redirects ------------------- */

    private static final Pattern META_REFRESH = Pattern.compile(
            "<meta[^>]*?http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_ATTR = Pattern.compile(
            "content\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern REFRESH_URL = Pattern.compile(
            "url\\s*=\\s*[\"']?([^\"';]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CALL = Pattern.compile(
            "location\\s*\\.\\s*(?:replace|assign)\\s*\\(\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_ASSIGN = Pattern.compile(
            "(?:window|top|self|document)?\\s*\\.?\\s*location(?:\\s*\\.\\s*href)?\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    /**
     * The destination an interstitial page points at without an HTTP redirect: a meta-refresh, or
     * the one-line {@code location = "…"} that "you are being redirected" pages are made of.
     * Heuristic by nature — no javascript is evaluated — which is why the module lets it be
     * switched off.
     */
    private static String findInPage(String body) {
        var meta = META_REFRESH.matcher(body);
        while (meta.find()) {
            var content = CONTENT_ATTR.matcher(meta.group());
            if (!content.find()) continue;
            var target = REFRESH_URL.matcher(content.group(1));
            if (!target.find()) continue;
            var candidate = target.group(1).trim();
            if (!candidate.isEmpty()) return candidate;
        }

        for (var pattern : List.of(JS_CALL, JS_ASSIGN)) {
            var matcher = pattern.matcher(body);
            while (matcher.find()) {
                var candidate = matcher.group(1).trim();
                // a bare fragment or a javascript: url is not a redirect
                if (candidate.startsWith("http://") || candidate.startsWith("https://")
                        || candidate.startsWith("/")) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isHtml(HttpURLConnection conn) {
        var type = conn.getContentType();
        return type != null && type.toLowerCase(Locale.ROOT).contains("html");
    }

    /** Reads at most [maxBytes] of the body. The head of the page is where a refresh lives. */
    private static String readLimited(HttpURLConnection conn, int maxBytes) throws IOException {
        try (var in = conn.getInputStream()) {
            var out = new ByteArrayOutputStream();
            var buffer = new byte[8192];
            int read;
            while (out.size() < maxBytes && (read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        }
    }

    /* ------------------- cookies ------------------- */

    // Some chains only redirect once a consent or session cookie comes back, so cookies are kept
    // for the length of one resolve and thrown away with it. Nothing is persisted, and nothing is
    // sent to a host that did not set it.

    private static String cookieHeader(Map<String, Map<String, String>> jar, String host) {
        var cookies = jar.get(host);
        if (cookies == null || cookies.isEmpty()) return null;

        var header = new StringBuilder();
        for (var cookie : cookies.entrySet()) {
            if (header.length() > 0) header.append("; ");
            header.append(cookie.getKey()).append('=').append(cookie.getValue());
        }
        return header.toString();
    }

    private static void storeCookies(Map<String, Map<String, String>> jar, String host, HttpURLConnection conn) {
        var headers = conn.getHeaderFields();
        if (headers == null) return;

        for (var header : headers.entrySet()) {
            if (header.getKey() == null || !header.getKey().equalsIgnoreCase("Set-Cookie")) continue;
            for (var value : header.getValue()) {
                if (value == null) continue;
                var pair = value.split(";", 2)[0];
                var equals = pair.indexOf('=');
                if (equals <= 0) continue;

                var cookies = jar.get(host);
                if (cookies == null) {
                    cookies = new LinkedHashMap<>();
                    jar.put(host, cookies);
                }
                cookies.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
        }
    }
}
