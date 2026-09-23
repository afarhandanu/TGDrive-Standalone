package com.tgdrive.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/** Browser for site accounts; credentials never pass through the app UI. */
public class LoginActivity extends Activity {
    private WebView browser;
    private EditText address;
    private String initialHost;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String site = getIntent().getStringExtra("site");
        String initial = "instagram".equals(site) ? "https://www.instagram.com/accounts/login/" :
            "x".equals(site) ? "https://x.com/home" : getIntent().getStringExtra("login_url");
        initialHost = WebSessions.host(initial);
        if (initialHost == null) { Toast.makeText(this, "Gunakan URL HTTPS situs yang valid", Toast.LENGTH_LONG).show(); finish(); return; }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(13);
        address.setText(initial);
        layout.addView(address);
        Button go = new Button(this); go.setText("Buka situs");
        go.setOnClickListener(v -> {
            String target = address.getText().toString().trim();
            if (WebSessions.host(target) == null) { Toast.makeText(this, "Gunakan URL HTTPS yang valid", Toast.LENGTH_SHORT).show(); return; }
            browser.loadUrl(target);
        });
        layout.addView(go);
        Button done = new Button(this); done.setText("Selesai login / kembali");
        done.setOnClickListener(v -> {
            saveCookies();
            if ("x".equals(site) && !WebSessions.hasXSession(this)) {
                Toast.makeText(this, "Sesi X belum terbaca. Selesaikan login hingga beranda terbuka.", Toast.LENGTH_LONG).show();
                return;
            }
            finish();
        });
        layout.addView(done);
        browser = new WebView(this);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
        browser.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                address.setText(url);
                saveCookies();
            }
        });
        layout.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        browser.loadUrl(initial);
    }
    private void saveCookies() {
        if (browser == null) return;
        WebSessions.save(this, initialHost);
        String currentHost = WebSessions.host(browser.getUrl());
        if (currentHost != null && (currentHost.equals(initialHost) ||
            currentHost.endsWith("." + initialHost) ||
            ("x.com".equals(initialHost) && (currentHost.equals("twitter.com") || currentHost.endsWith(".twitter.com")))))
            WebSessions.save(this, currentHost);
        CookieManager.getInstance().flush();
    }
    @Override protected void onPause() { saveCookies(); super.onPause(); }
    @Override protected void onDestroy() { if (browser != null) browser.destroy(); super.onDestroy(); }
}
