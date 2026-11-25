package com.atespin.borc;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // localStorage: kayıtlar telefonda kalır
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        // Düzenleme paneli açıksa önce onu kapat, değilse uygulamadan çık.
        web.evaluateJavascript("window.__back?window.__back():false", v -> {
            if (!"true".equals(v)) MainActivity.super.onBackPressed();
        });
    }

    /** Sayfanın fiyatları çekmesi için küçük köprü. Yalnızca fiyat kaynağına istek atar. */
    private class Bridge {
        @JavascriptInterface
        public void get(final String url, final String id) {
            new Thread(() -> {
                boolean ok = false;
                String body;
                if (url == null || !url.startsWith("https://finans.truncgil.com/")) {
                    body = "izin verilmeyen adres";
                } else {
                    HttpURLConnection c = null;
                    try {
                        c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(15000);
                        c.setUseCaches(false);
                        c.setRequestProperty("User-Agent", "BorcBakiye/1.1 (Android)");
                        c.setRequestProperty("Accept", "application/json");
                        int code = c.getResponseCode();
                        if (code >= 200 && code < 300) {
                            InputStream in = c.getInputStream();
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                            in.close();
                            body = out.toString("UTF-8");
                            ok = true;
                        } else {
                            body = "HTTP " + code;
                        }
                    } catch (Exception e) {
                        body = String.valueOf(e.getMessage());
                    } finally {
                        if (c != null) c.disconnect();
                    }
                }
                final String js = "window.__res(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(body) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }
    }
}
