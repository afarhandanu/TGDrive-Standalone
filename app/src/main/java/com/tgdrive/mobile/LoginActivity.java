package com.tgdrive.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Web session remains in the private Android WebView cookie store. */
public class LoginActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        WebView browser = new WebView(this);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        browser.setWebViewClient(new WebViewClient());
        setContentView(browser);
        browser.loadUrl("instagram".equals(getIntent().getStringExtra("site"))
            ? "https://www.instagram.com/accounts/login/" : "https://x.com/i/flow/login");
    }
    @Override protected void onPause() { CookieManager.getInstance().flush(); super.onPause(); }
}
