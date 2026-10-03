package app.pogosummit.game;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Landscape-only fullscreen host for the Pogo Summit web game. Immersive-sticky, keep-screen-on, display-cutout aware
 * (short-edges), pauses the game when the app loses focus, and routes the Back button to the in-game menu first.
 */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enableCutout(w);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHorizontalScrollBarEnabled(false);
        web.setVerticalScrollBarEnabled(false);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        web.setWebViewClient(AssetClient.forContext(this));
        web.addJavascriptInterface(new NativeBridge(this), "PogoNative");
        web.setLongClickable(false);
        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { return true; }
        });
        setContentView(web);
        immersive();
        web.loadUrl(AssetClient.START_URL);
    }

    /** Let the game draw under the notch / punch-hole (API 28+; reflection keeps the build at API 23). */
    private void enableCutout(Window w) {
        if (Build.VERSION.SDK_INT < 28) return;
        try {
            WindowManager.LayoutParams lp = w.getAttributes();
            Field f = WindowManager.LayoutParams.class.getField("layoutInDisplayCutoutMode");
            f.setInt(lp, Build.VERSION.SDK_INT >= 30 ? 3 /* ALWAYS */ : 1 /* SHORT_EDGES */);
            w.setAttributes(lp);
        } catch (Throwable ignored) { /* older WebView/devices: harmless */ }
    }

    @SuppressWarnings("deprecation")
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
        else js("window.dispatchEvent(new Event('pogo-pause'))");
    }

    @Override
    protected void onPause() {
        js("window.dispatchEvent(new Event('pogo-pause'))");
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        immersive();
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.removeJavascriptInterface("PogoNative"); web.destroy(); web = null; }
        super.onDestroy();
    }

    /** Back: let the game handle it (close a screen / open pause); exit only if it says "not handled". */
    @Override
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("(window.PogoBack ? window.PogoBack() : false)", new ValueCallback<String>() {
            @Override public void onReceiveValue(String v) { if (!"true".equals(v)) MainActivity.super.onBackPressed(); }
        });
    }

    private void js(String code) {
        if (web != null) web.evaluateJavascript(code, null);
    }

    /** Display-cutout / system-bar insets as CSS px JSON (API 28+ via reflection; zeros otherwise). */
    static String currentInsetsJson(Activity a) {
        float d = 1f;
        try { DisplayMetrics m = a.getResources().getDisplayMetrics(); d = m.density; } catch (Throwable ignored) { /* default */ }
        int top = 0, right = 0, bottom = 0, left = 0;
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                View decor = a.getWindow().getDecorView();
                Object insets = decor.getRootWindowInsets();
                if (insets != null) {
                    Method gdc = insets.getClass().getMethod("getDisplayCutout");
                    Object cutout = gdc.invoke(insets);
                    if (cutout != null) {
                        Class<?> c = cutout.getClass();
                        top = (Integer) c.getMethod("getSafeInsetTop").invoke(cutout);
                        right = (Integer) c.getMethod("getSafeInsetRight").invoke(cutout);
                        bottom = (Integer) c.getMethod("getSafeInsetBottom").invoke(cutout);
                        left = (Integer) c.getMethod("getSafeInsetLeft").invoke(cutout);
                    }
                }
            }
        } catch (Throwable ignored) { /* no cutout info */ }
        return ShellLogic.insetsJson(top, right, bottom, left, d);
    }
}
