package app.pogosummit.game;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves the bundled game from {@code assets/www} under a virtual HTTPS origin (like androidx WebViewAssetLoader, without
 * the dependency). A real https origin gives the page a secure context (WebAudio, ES modules, storage) — {@code file://}
 * would not. Any request to another host is blocked: the app never touches the network.
 */
final class AssetClient extends WebViewClient {
    static final String HOST = ShellLogic.HOST;
    static final String START_URL = ShellLogic.START_URL;

    /** Where bundled files come from (the APK assets in production, a folder in unit tests). */
    interface AssetSource { InputStream open(String path) throws IOException; }

    private final AssetSource source;

    AssetClient(AssetSource source) { this.source = source; }

    static AssetClient forContext(final Context ctx) {
        return new AssetClient(new AssetSource() {
            @Override public InputStream open(String path) throws IOException { return ctx.getAssets().open(path); }
        });
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri u = request.getUrl();
        if (!ShellLogic.isGameHost(u.getHost())) return blocked();
        String path = ShellLogic.normalizePath(u.getPath());
        if (path == null) return blocked();
        try {
            InputStream in = source.open("www" + path);
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-cache");
            headers.put("Access-Control-Allow-Origin", "*");
            WebResourceResponse r = new WebResourceResponse(ShellLogic.mime(path), "UTF-8", in);
            r.setResponseHeaders(headers);
            return r;
        } catch (IOException e) {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
        }
    }

    /** String variant exists on every API level (the Request variant, API 24, delegates here by default). */
    @SuppressWarnings("deprecation")
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        Uri u = Uri.parse(url);
        return !ShellLogic.isGameHost(u.getHost()); // never navigate away from the game
    }

    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
    }
}
