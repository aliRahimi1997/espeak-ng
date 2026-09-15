package com.reecedunn.espeak;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

public class EspeakApp extends Application {

    private static Context storageContext;

    @Override
    public void onCreate() {
        super.onCreate();
        Context appContext = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            EspeakApp.storageContext = appContext.createDeviceProtectedStorageContext();
        } else {
            EspeakApp.storageContext = appContext;
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        Locale systemLocale = getSystemLocale();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(systemLocale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(systemLocale));
        }

        Context localized = base.createConfigurationContext(config);
        super.attachBaseContext(localized);
    }
    private static Locale getSystemLocale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList list = LocaleList.getDefault();
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }
        }
        return Locale.getDefault();
    }

    public static Context getStorageContext() {
        return EspeakApp.storageContext;
    }
}