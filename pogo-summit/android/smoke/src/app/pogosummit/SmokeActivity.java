package app.pogosummit;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
public class SmokeActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        WebView w = new WebView(this);
        w.getSettings().setJavaScriptEnabled(true);
        w.loadUrl("file:///android_asset/www/index.html");
        setContentView(w);
    }
}
