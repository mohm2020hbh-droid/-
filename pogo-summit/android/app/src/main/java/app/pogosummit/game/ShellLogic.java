package app.pogosummit.game;

import java.util.Locale;

/**
 * Pure-Java decisions of the Android shell (no android.* imports) so they can be unit-tested on any JVM:
 * which hosts the WebView may reach, how request paths map to bundled files, MIME types, haptic pattern parsing and the
 * safe-area JSON handed to the game.
 */
final class ShellLogic {
    private ShellLogic() {}

    static final String HOST = "appassets.pogosummit.app";
    static final String START_URL = "https://" + HOST + "/index.html";

    /** Only the virtual game origin is reachable — the app never uses the network. */
    static boolean isGameHost(String host) { return HOST.equals(host); }

    /** Maps a URL path to a path inside assets/www, or null when it must be refused. */
    static String normalizePath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) return "/index.html";
        if (path.contains("..") || path.contains("\\") || path.indexOf('\0') >= 0 || !path.startsWith("/")) return null;
        return path;
    }

    static String mime(String p) {
        String s = p.toLowerCase(Locale.US);
        if (s.endsWith(".html")) return "text/html";
        if (s.endsWith(".js")) return "text/javascript";
        if (s.endsWith(".css")) return "text/css";
        if (s.endsWith(".json")) return "application/json";
        if (s.endsWith(".png")) return "image/png";
        if (s.endsWith(".webp")) return "image/webp";
        if (s.endsWith(".svg")) return "image/svg+xml";
        if (s.endsWith(".ogg")) return "audio/ogg";
        if (s.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    static int clampAmplitude(int amp) { return Math.max(1, Math.min(255, amp)); }

    /** "40,60,40" → {0,40,60,40} (leading 0 = start immediately); null if malformed or empty. */
    static long[] parsePattern(String csv) {
        if (csv == null || csv.trim().isEmpty()) return null;
        try {
            String[] parts = csv.split(",");
            long[] t = new long[parts.length + 1];
            for (int i = 0; i < parts.length; i++) {
                long v = Long.parseLong(parts[i].trim());
                if (v < 0 || v > 5000) return null;
                t[i + 1] = v;
            }
            return t;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String insetsJson(int top, int right, int bottom, int left, float density) {
        float d = density <= 0 ? 1f : density;
        return String.format(Locale.US, "{\"top\":%.1f,\"right\":%.1f,\"bottom\":%.1f,\"left\":%.1f}", top / d, right / d, bottom / d, left / d);
    }
}
