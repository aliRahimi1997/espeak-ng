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
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Spinner;
import android.widget.Toast;

import com.reecedunn.espeak.CheckVoiceData;
import com.reecedunn.espeak.DownloadVoiceData;
import com.reecedunn.espeak.FileListAdapter;
import com.reecedunn.espeak.FileUtils;
import com.reecedunn.espeak.R;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;

public class ImportVoicePreference extends DialogPreference {
    private static final String TAG = "ImportVoicePreference";
    private static final int REQUEST_IMPORT_DICT = 1001;
    private File mRoot;
    private Spinner mDictionaries;
    private Context mContext;
    private boolean mFilePickerOpened = false;

    public ImportVoicePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        setDialogLayoutResource(R.layout.import_voice_preference);
        setLayoutResource(R.layout.information_view);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);

        mRoot = Environment.getExternalStorageDirectory();
    }

    public ImportVoicePreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImportVoicePreference(Context context) {
        this(context, null);
    }

    public void setDescription(int resId) {
        callChangeListener(getContext().getString(resId));
    }

    @Override
    protected View onCreateDialogView() {
        View root = super.onCreateDialogView();
        mDictionaries = (Spinner)root.findViewById(R.id.dictionaries);
        return root;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        File[] dictionaries = mRoot.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return !file.isDirectory() && file.getName().endsWith("_dict");
            }
        });
        if (dictionaries != null) {
            Arrays.sort(dictionaries);
            mDictionaries.setAdapter(new FileListAdapter((Activity) getContext(), dictionaries));
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mFilePickerOpened = true;
            Toast.makeText(getContext(), "Opening file picker...", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Opening file picker");
            
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            try {
                ((Activity) getContext()).startActivityForResult(intent, REQUEST_IMPORT_DICT);
            } catch (Exception e) {
                Log.e(TAG, "Error opening file picker", e);
                Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            return;
        }
        super.onClick(dialog, which);
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "handleActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        Toast.makeText(getContext(), "Activity result: " + requestCode + ", " + resultCode, Toast.LENGTH_SHORT).show();
        
        if (requestCode == REQUEST_IMPORT_DICT) {
            mFilePickerOpened = false;
            
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "RESULT_OK");
                Toast.makeText(getContext(), "File selected, importing...", Toast.LENGTH_SHORT).show();
                
                if (data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        Log.d(TAG, "URI: " + uri.toString());
                        Toast.makeText(getContext(), "URI: " + uri.getLastPathSegment(), Toast.LENGTH_LONG).show();
                        importFile(uri);
                    } else {
                        Toast.makeText(getContext(), "ERROR: URI is null", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(getContext(), "ERROR: Data is null", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getContext(), "File selection cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void importFile(final File file) {
        new AsyncTask<Object, Object, String>() {
            @Override
            protected String doInBackground(Object... objects) {
                try {
                    if (file == null) {
                        return "ERROR: File is null";
                    }
                    
                    if (!file.exists()) {
                        return "ERROR: File does not exist: " + file.getAbsolutePath();
                    }
                    
                    File dataPath = CheckVoiceData.getDataPath(getContext());
                    if (dataPath == null) {
                        return "ERROR: Data path is null";
                    }
                    
                    Toast.makeText(getContext(), "Data path: " + dataPath.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    
                    if (!dataPath.exists()) {
                        if (!dataPath.mkdirs()) {
                            return "ERROR: Failed to create data directory: " + dataPath.getAbsolutePath();
                        }
                    }
                    
                    File destination = new File(dataPath, file.getName());
                    
                    Log.d(TAG, "Source: " + file.getAbsolutePath());
                    Log.d(TAG, "Destination: " + destination.getAbsolutePath());
                    Toast.makeText(getContext(), "Copying to: " + destination.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    
                    if (destination.exists()) {
                        Log.d(TAG, "Destination file exists, deleting...");
                        if (!destination.delete()) {
                            return "ERROR: Failed to delete old file: " + destination.getAbsolutePath();
                        }
                        Log.d(TAG, "Old file deleted successfully");
                    }
                    
                    byte[] data = FileUtils.readBinary(file);
                    FileUtils.write(destination, data);
                    
                    if (destination.exists()) {
                        Log.d(TAG, "File copied successfully, size: " + destination.length() + " bytes");
                        return "SUCCESS: File imported successfully: " + file.getName();
                    } else {
                        return "ERROR: File was not created at destination";
                    }
                } catch (IOException e) {
                    Log.e(TAG, "IOException during import", e);
                    return "ERROR: IOException: " + e.getMessage();
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error during import", e);
                    return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                }
            }

            @Override
            protected void onPostExecute(String result) {
                Log.d(TAG, "Import result: " + result);
                Toast.makeText(getContext(), "Result: " + result, Toast.LENGTH_LONG).show();
                showResultDialog(result);
                
                if (result.startsWith("SUCCESS")) {
                    final Intent intent = new Intent(DownloadVoiceData.BROADCAST_LANGUAGES_UPDATED);
                    getContext().sendBroadcast(intent);
                    
                    try {
                        android.speech.tts.TextToSpeech tts = new android.speech.tts.TextToSpeech(
                            getContext(),
                            status -> {
                                Log.d(TAG, "TTS restarted with status: " + status);
                            }
                        );
                        tts.shutdown();
                    } catch (Exception e) {
                        Log.e(TAG, "Error restarting TTS", e);
                    }
                }
            }
        }.execute();
    }

    private void importFile(final Uri uri) {
        new AsyncTask<Object, Object, String>() {
            @Override
            protected String doInBackground(Object... objects) {
                try {
                    if (uri == null) {
                        return "ERROR: URI is null";
                    }
                    
                    Log.d(TAG, "Importing from URI: " + uri.toString());
                    Toast.makeText(getContext(), "Importing: " + uri.getLastPathSegment(), Toast.LENGTH_LONG).show();
                    
                    String fileName = uri.getLastPathSegment();
                    if (fileName == null) {
                        fileName = "imported_dict.dict";
                    }
                    
                    if (!fileName.endsWith("_dict")) {
                        fileName = fileName + "_dict";
                    }
                    
                    File dataPath = CheckVoiceData.getDataPath(getContext());
                    if (dataPath == null) {
                        return "ERROR: Data path is null";
                    }
                    
                    Toast.makeText(getContext(), "Data path: " + dataPath.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    
                    if (!dataPath.exists()) {
                        if (!dataPath.mkdirs()) {
                            return "ERROR: Failed to create data directory: " + dataPath.getAbsolutePath();
                        }
                    }
                    
                    File destination = new File(dataPath, fileName);
                    
                    Log.d(TAG, "Destination: " + destination.getAbsolutePath());
                    Toast.makeText(getContext(), "Copying to: " + destination.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    
                    if (destination.exists()) {
                        Log.d(TAG, "Destination file exists, deleting...");
                        if (!destination.delete()) {
                            return "ERROR: Failed to delete old file: " + destination.getAbsolutePath();
                        }
                        Log.d(TAG, "Old file deleted successfully");
                    }
                    
                    java.io.InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                    if (inputStream == null) {
                        return "ERROR: Cannot open input stream for URI";
                    }
                    
                    byte[] data = FileUtils.readBinary(inputStream);
                    FileUtils.write(destination, data);
                    
                    if (destination.exists()) {
                        Log.d(TAG, "File copied successfully, size: " + destination.length() + " bytes");
                        return "SUCCESS: File imported successfully: " + fileName;
                    } else {
                        return "ERROR: File was not created at destination";
                    }
                } catch (IOException e) {
                    Log.e(TAG, "IOException during import", e);
                    return "ERROR: IOException: " + e.getMessage();
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException during import", e);
                    return "ERROR: Permission denied: " + e.getMessage();
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error during import", e);
                    return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                }
            }

            @Override
            protected void onPostExecute(String result) {
                Log.d(TAG, "Import result: " + result);
                Toast.makeText(getContext(), "Result: " + result, Toast.LENGTH_LONG).show();
                showResultDialog(result);
                
                if (result.startsWith("SUCCESS")) {
                    final Intent intent = new Intent(DownloadVoiceData.BROADCAST_LANGUAGES_UPDATED);
                    getContext().sendBroadcast(intent);
                    
                    try {
                        android.speech.tts.TextToSpeech tts = new android.speech.tts.TextToSpeech(
                            getContext(),
                            status -> {
                                Log.d(TAG, "TTS restarted with status: " + status);
                            }
                        );
                        tts.shutdown();
                    } catch (Exception e) {
                        Log.e(TAG, "Error restarting TTS", e);
                    }
                }
            }
        }.execute();
    }

    private void showResultDialog(String message) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(message.startsWith("SUCCESS") ? "✓ Import Successful" : "✗ Import Failed");
            builder.setMessage(message);
            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
            builder.setCancelable(true);
            
            AlertDialog dialog = builder.create();
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing result dialog", e);
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }
}