/*
 * Copyright (C) 2013 Reece H. Dunn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reecedunn.espeak.preference;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.Preference;
import android.speech.tts.TextToSpeech;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import com.reecedunn.espeak.DownloadVoiceData;
import com.reecedunn.espeak.FileUtils;
import com.reecedunn.espeak.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class ImportVoicePreference extends Preference {
    private static final String TAG = "ImportVoicePreference";
    private static final int REQUEST_IMPORT_DICT = 1001;

    public ImportVoicePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setLayoutResource(R.layout.information_view);
        setOnPreferenceClickListener(pref -> {
            openFilePicker();
            return true;
        });
    }

    public ImportVoicePreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImportVoicePreference(Context context) {
        this(context, null);
    }

    public void setDescription(int resId) {
        setSummary(getContext().getString(resId));
    }

    private File getDataPath() {
        // مسیر صحیح و قابل نوشتن برای دیکشنری‌ها
        File dataDir = new File(getContext().getFilesDir(), "voices/espeak-ng-data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        Log.d(TAG, "Data path: " + dataDir.getAbsolutePath());
        return dataDir;
    }

    private void openFilePicker() {
        Log.d(TAG, "Opening file picker");
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            ((Activity) getContext()).startActivityForResult(intent, REQUEST_IMPORT_DICT);
        } catch (Exception e) {
            Log.e(TAG, "Error opening file picker", e);
            showResultDialog("ERROR: " + e.getMessage());
        }
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_IMPORT_DICT) return;
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.d(TAG, "File selection cancelled or no data");
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
            showResultDialog("ERROR: URI is null");
            return;
        }

        Log.d(TAG, "URI: " + uri.toString());
        importFile(uri);
    }

    private void importFile(final Uri uri) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                try {
                    // گرفتن نام فایل
                    String fileName = uri.getLastPathSegment();
                    if (fileName == null) fileName = "imported_dict.dict";
                    if (!fileName.endsWith("_dict")) fileName = fileName + "_dict";

                    // مسیر مقصد
                    File dataPath = getDataPath();
                    File destination = new File(dataPath, fileName);

                    // حذف فایل قدیمی
                    if (destination.exists()) {
                        Log.d(TAG, "Deleting old file: " + destination.getAbsolutePath());
                        if (!destination.delete()) {
                            return "ERROR: Failed to delete old file";
                        }
                    }

                    // خواندن فایل انتخابی
                    InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                    if (inputStream == null) {
                        return "ERROR: Cannot open input stream";
                    }

                    byte[] data = FileUtils.readBinary(inputStream);
                    FileUtils.write(destination, data);

                    // کپی دیباگ در حافظه داخلی
                    File debugFile = new File(Environment.getExternalStorageDirectory(), fileName + "_debug");
                    FileUtils.write(debugFile, data);
                    Log.d(TAG, "Debug copy: " + debugFile.getAbsolutePath());

                    return "SUCCESS: " + fileName;
                } catch (IOException e) {
                    Log.e(TAG, "IO Error", e);
                    return "ERROR: " + e.getMessage();
                } catch (SecurityException e) {
                    Log.e(TAG, "Security Error", e);
                    return "ERROR: Permission denied: " + e.getMessage();
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error", e);
                    return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                }
            }

            @Override
            protected void onPostExecute(String result) {
                showResultDialog(result);

                if (result.startsWith("SUCCESS")) {
                    // بروزرسانی TTS
                    getContext().sendBroadcast(new Intent(DownloadVoiceData.BROADCAST_LANGUAGES_UPDATED));

                    // ریستارت TTS
                    try {
                        new TextToSpeech(getContext(), status -> {
                            Log.d(TAG, "TTS restarted with status: " + status);
                        }).shutdown();
                    } catch (Exception e) {
                        Log.e(TAG, "Error restarting TTS", e);
                    }
                }
            }
        }.execute();
    }

    private void showResultDialog(String message) {
        try {
            new AlertDialog.Builder(getContext())
                .setTitle(message.startsWith("SUCCESS") ? "✓ Import Successful" : "✗ Import Failed")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .create()
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing dialog", e);
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }
}