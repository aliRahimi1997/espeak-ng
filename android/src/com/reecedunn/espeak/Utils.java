package com.reecedunn.espeak;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static void openTelegram(Context context) {
        Intent telegramIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=r2998"));
        PackageManager pm = context.getPackageManager();

        List<ResolveInfo> activities = pm.queryIntentActivities(telegramIntent, 0);

        if (activities.isEmpty()) {
            try {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/r2998"));
                context.startActivity(webIntent);
            } catch (Exception e) {
                Toast.makeText(context, context.getString(R.string.no_telegram_app), Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (activities.size() == 1) {
            ResolveInfo info = activities.get(0);
            telegramIntent.setClassName(info.activityInfo.packageName, info.activityInfo.name);
            context.startActivity(telegramIntent);
            return;
        }

        List<Object> items = new ArrayList<>(activities);
        items.add("Cancel");

        ArrayAdapter<Object> adapter = new ArrayAdapter<Object>(context, android.R.layout.select_dialog_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView textView = (TextView) super.getView(position, convertView, parent);
                Object item = getItem(position);

                if (item instanceof ResolveInfo) {
                    ResolveInfo info = (ResolveInfo) item;
                    textView.setText(info.loadLabel(pm));
                    
                    int iconSize = (int) (24 * context.getResources().getDisplayMetrics().density);
                    android.graphics.drawable.Drawable icon = info.loadIcon(pm);
                    icon.setBounds(0, 0, iconSize, iconSize);
                    textView.setCompoundDrawables(icon, null, null, null);
                    textView.setCompoundDrawablePadding((int) (12 * context.getResources().getDisplayMetrics().density));
                } else if (item instanceof String) {
                    textView.setText((String) item);
                    textView.setCompoundDrawables(null, null, null, null);
                }

                return textView;
            }
        };

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.chooser_telegram))
                .setAdapter(adapter, (dialogInterface, i) -> {
                    Object selectedItem = items.get(i);
                    if (selectedItem instanceof ResolveInfo) {
                        ResolveInfo info = (ResolveInfo) selectedItem;
                        Intent targetIntent = new Intent(telegramIntent);
                        targetIntent.setClassName(info.activityInfo.packageName, info.activityInfo.name);
                        context.startActivity(targetIntent);
                    } else {
                        dialogInterface.dismiss();
                    }
                })
                .create();

        dialog.show();
    }
}
