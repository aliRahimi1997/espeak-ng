package com.reecedunn.espeak;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

public class Utils {

    public static void openTelegram(Context context) {
        PackageManager pm = context.getPackageManager();

        try {
            pm.getPackageInfo("org.telegram.messenger", 0);

            Intent telegramIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=r2998"));

            if (telegramIntent.resolveActivity(pm) != null) {
                Intent chooser = Intent.createChooser(telegramIntent, context.getString(R.string.chooser_telegram));
                context.startActivity(chooser);
            } else {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/r2998"));
                context.startActivity(webIntent);
            }

        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(context, context.getString(R.string.no_telegram_app), Toast.LENGTH_LONG).show();

            try {
                Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.telegram.messenger"));
                context.startActivity(marketIntent);
            } catch (Exception ex) {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/r2998"));
                context.startActivity(webIntent);
            }
        }
    }
}