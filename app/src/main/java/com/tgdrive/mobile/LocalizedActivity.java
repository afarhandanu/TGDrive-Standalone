package com.tgdrive.mobile;

import android.app.Activity;
import android.content.Context;

public abstract class LocalizedActivity extends Activity {
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(L10n.wrap(base)); }
    protected final String t(String source) { return L10n.text(this, source); }
    protected final String message(String source) { return L10n.message(this, source); }
}
