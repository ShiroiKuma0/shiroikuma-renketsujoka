package com.trianguloy.urlchecker.shiroikuma;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The font catalogue behind the 白い熊 連結浄化 UI page: the built-in families plus any {@code .ttf}
 * / {@code .otf} imported through the document picker.
 *
 * <p>An imported font is copied into the app's own storage the moment it is picked, so the choice
 * keeps working when the source file is moved or deleted — the same snapshot-on-pick rule the
 * sister apps use for icons.
 */
public class Fonts {

    /** Built-in choices, stored as these sentinel names rather than file names. */
    public static final String SYSTEM = "";
    private static final String SANS = "!sans";
    private static final String SERIF = "!serif";
    private static final String MONO = "!mono";

    private static final String DIR = "shiroikuma_fonts";

    private static final Map<String, Typeface> CACHE = new HashMap<>();

    /** A font as the picker lists it. */
    public static class Option {
        public final String id;
        public final String name;

        Option(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** Where imported fonts live. */
    public static File dir(Context cntx) {
        var dir = new File(cntx.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** The built-ins first, then every imported file, alphabetically. */
    public static List<Option> available(Context cntx) {
        var options = new ArrayList<Option>();
        options.add(new Option(SYSTEM, "System"));
        options.add(new Option(SANS, "Sans"));
        options.add(new Option(SERIF, "Serif"));
        options.add(new Option(MONO, "Monospace"));

        var files = dir(cntx).listFiles();
        if (files != null) {
            var names = new ArrayList<String>();
            for (var file : files) if (file.isFile()) names.add(file.getName());
            java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            for (var name : names) options.add(new Option(name, stripExtension(name)));
        }
        return options;
    }

    /** Resolve an id to a typeface. Unknown or unloadable ids fall back to the system font. */
    public static Typeface typeface(Context cntx, String id) {
        if (id == null || id.equals(SYSTEM)) return Typeface.DEFAULT;
        if (id.equals(SANS)) return Typeface.SANS_SERIF;
        if (id.equals(SERIF)) return Typeface.SERIF;
        if (id.equals(MONO)) return Typeface.MONOSPACE;

        var cached = CACHE.get(id);
        if (cached != null) return cached;
        try {
            var file = new File(dir(cntx), id);
            if (!file.isFile()) return Typeface.DEFAULT;
            var typeface = Typeface.createFromFile(file);
            CACHE.put(id, typeface);
            return typeface;
        } catch (Exception e) {
            // A corrupt or unsupported file must not take the settings page down with it.
            return Typeface.DEFAULT;
        }
    }

    /**
     * Copy a picked font into app storage.
     *
     * @return the id to store, or null if it could not be read.
     */
    public static String importFont(Context cntx, Uri uri, String displayName) {
        var name = sanitize(displayName);
        if (name == null) return null;
        var target = new File(dir(cntx), name);
        try (InputStream in = cntx.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) return null;
            var buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        } catch (IOException | SecurityException e) {
            target.delete();
            return null;
        }
        // Reject anything the font loader won't take, rather than storing a file that silently
        // renders as the system face forever after.
        try {
            Typeface.createFromFile(target);
        } catch (Exception e) {
            target.delete();
            return null;
        }
        CACHE.remove(name);
        return name;
    }

    /** Delete an imported font. Built-ins are not removable. */
    public static boolean remove(Context cntx, String id) {
        if (id == null || id.startsWith("!") || id.equals(SYSTEM)) return false;
        CACHE.remove(id);
        return new File(dir(cntx), id).delete();
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Keep the file name to something that cannot escape the fonts directory. */
    private static String sanitize(String name) {
        if (name == null) return null;
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return null;
        var lower = name.toLowerCase(java.util.Locale.US);
        if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) name = name + ".ttf";
        return name;
    }
}
