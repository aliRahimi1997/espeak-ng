package com.reecedunn.espeak;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class EspeakActivity extends Activity {

    private static final String ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        ListView listView = (ListView) findViewById(R.id.options_list);

        List<String> options = new ArrayList<String>();
        options.add(getString(R.string.opt_espeak_settings));
        options.add(getString(R.string.opt_general_tts));
        options.add(getString(R.string.opt_telegram));
        options.add(getString(R.string.opt_email));

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                options
        );
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        startActivity(new Intent(EspeakActivity.this, TtsSettingsActivity.class));
                        break;
                    case 1:
                        launchGeneralTtsSettings();
                        break;
                    case 2:
                        Utils.openTelegram(EspeakActivity.this);
                        break;
                    case 3:
                        sendEmail();
                        break;
                }
            }
        });
    }

    private void launchGeneralTtsSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
            intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, "com.android.settings.TextToSpeechSettings");
            intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS, intent.getExtras());
        } else {
            intent = new Intent(ACTION_TTS_SETTINGS);
        }
        startActivity(intent);
    }

    private void sendEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:ali.c5468@gmail.com"));
        intent.putExtra(Intent.EXTRA_SUBJECT, "eSpeak Support");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Please install an email client.", Toast.LENGTH_SHORT).show();
        }
    }
}