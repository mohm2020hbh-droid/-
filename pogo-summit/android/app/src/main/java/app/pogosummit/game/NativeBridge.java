package app.pogosummit.game;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;

import java.lang.reflect.Method;

/**
 * JS bridge exposed as {@code window.PogoNative}. Everything is best-effort and exception-safe: a failing haptic or
 * save must never crash the game. Written for Java 8 + android.jar API 23 so it also builds with the legacy aapt/dx
 * toolchain; newer APIs (VibrationEffect ≥ 26) are reached via reflection.
 */
public final class NativeBridge {
    private static final String PREFS = "pogo_summit";
    private final Activity activity;
    private final Vibrator vibrator;
    private final SharedPreferences prefs;

    NativeBridge(Activity activity) {
        this.activity = activity;
        this.vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** One-shot vibration. {@code amp} 1..255 (ignored below API 26). */
    @JavascriptInterface
    public void vibrate(int ms, int amp) {
        if (vibrator == null || ms <= 0) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                Class<?> ve = Class.forName("android.os.VibrationEffect");
                Object effect = ve.getMethod("createOneShot", long.class, int.class).invoke(null, (long) ms, ShellLogic.clampAmplitude(amp));
                Method vib = Vibrator.class.getMethod("vibrate", ve);
                vib.invoke(vibrator, effect);
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Throwable ignored) {
            try { vibrator.vibrate(ms); } catch (Throwable ignored2) { /* no haptics */ }
        }
    }

    /** Comma separated timings (off,on,off,on…) like the Web Vibration API. */
    @JavascriptInterface
    public void vibratePattern(String csv, int amp) {
        if (vibrator == null || csv == null) return;
        try {
            long[] t = ShellLogic.parsePattern(csv);
            if (t == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                Class<?> ve = Class.forName("android.os.VibrationEffect");
                Object effect = ve.getMethod("createWaveform", long[].class, int.class).invoke(null, t, -1);
                Method vib = Vibrator.class.getMethod("vibrate", ve);
                vib.invoke(vibrator, effect);
            } else {
                vibrator.vibrate(t, -1);
            }
        } catch (Throwable ignored) { /* no haptics */ }
    }

    @JavascriptInterface
    public void saveString(String key, String value) {
        try { prefs.edit().putString(key, value).apply(); } catch (Throwable ignored) { /* storage full */ }
    }

    @JavascriptInterface
    public String loadString(String key) {
        try { return prefs.getString(key, null); } catch (Throwable ignored) { return null; }
    }

    /** JSON {top,right,bottom,left} in CSS px (display cutout / system bars), used when CSS env() reports 0. */
    @JavascriptInterface
    public String getInsets() {
        return MainActivity.currentInsetsJson(activity);
    }

    @JavascriptInterface
    public void exitApp() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { activity.finish(); }
        });
    }
}
