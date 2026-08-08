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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Spinner;

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
    private static final int REQUEST_IMPORT_DICT = 1001;
    private File mRoot;
    private Spinner mDictionaries;
    private Uri mSelectedFileUri;

    public ImportVoicePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
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
        // Keep the spinner for backward compatibility, but we'll use file picker
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
            // If the user selected a file from the spinner (legacy method), use it
            File selectedFile = (File) mDictionaries.getSelectedItem();
            if (selectedFile != null && selectedFile.exists()) {
                importFile(selectedFile);
                super.onClick(dialog, which);
                return;
            }
            
            // Otherwise, open file picker for modern Android
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            ((Activity) getContext()).startActivityForResult(intent, REQUEST_IMPORT_DICT);
            // Note: result will be handled in onActivityResult
        }
        super.onClick(dialog, which);
    }

    // Call this method from your Activity's onActivityResult
    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMPORT_DICT && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    importFile(uri);
                }
            }
        }
    }

    private void importFile(final File file) {
        new AsyncTask<Object, Object, File>() {
            @Override
            protected File doInBackground(Object... objects) {
                if (file != null) {
                    File destination = new File(CheckVoiceData.getDataPath(getContext()), file.getName());
                    try {
                        byte[] data = FileUtils.readBinary(file);
                        FileUtils.write(destination, data);
                        return file;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(File file) {
                if (file != null) {
                    final Intent intent = new Intent(DownloadVoiceData.BROADCAST_LANGUAGES_UPDATED);
                    getContext().sendBroadcast(intent);
                }
            }
        }.execute();
    }

    private void importFile(final Uri uri) {
        new AsyncTask<Object, Object, File>() {
            @Override
            protected File doInBackground(Object... objects) {
                if (uri != null) {
                    String fileName = uri.getLastPathSegment();
                    if (fileName == null) {
                        fileName = "imported_dict.dict";
                    }
                    // Ensure it ends with _dict
                    if (!fileName.endsWith("_dict")) {
                        fileName = fileName + "_dict";
                    }
                    File destination = new File(CheckVoiceData.getDataPath(getContext()), fileName);
                    try {
                        byte[] data = FileUtils.readBinary(getContext().getContentResolver().openInputStream(uri));
                        FileUtils.write(destination, data);
                        return destination;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(File file) {
                if (file != null) {
                    final Intent intent = new Intent(DownloadVoiceData.BROADCAST_LANGUAGES_UPDATED);
                    getContext().sendBroadcast(intent);
                }
            }
        }.execute();
    }
}