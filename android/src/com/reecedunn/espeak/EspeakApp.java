package com.reecedunn.espeak;

import android.app.Application;
import android.content.Context;
import android.os.Build;

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

    public static Context getStorageContext() {
        return EspeakApp.storageContext;
    }
}