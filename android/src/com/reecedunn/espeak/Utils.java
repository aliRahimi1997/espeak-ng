package com.reecedunn.espeak;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public class Utils {

    public static void openTelegram(Context context) {
        Intent telegramIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=r2998"));
        Intent chooser = Intent.createChooser(telegramIntent, context.getString(R.string.chooser_telegram));

        try {
            context.startActivity(chooser);
        } catch (ActivityNotFoundException e1) {
            Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/r2998"));
            try {
                context.startActivity(webIntent);
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(context, context.getString(R.string.no_telegram_app), Toast.LENGTH_LONG).show();
            }
        }
    }
}
