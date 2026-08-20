package com.ezcall.onetoone;

import android.app.Application;

public class EzCallApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppSettings.applyLanguage(this);
        EzCallTelecomManager.initialize(this);
    }
}
