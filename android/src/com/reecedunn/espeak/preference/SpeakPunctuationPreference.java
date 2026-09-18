package com.reecedunn.espeak.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;

import com.reecedunn.espeak.R;
import com.reecedunn.espeak.SpeechSynthesis;
import com.reecedunn.espeak.VoiceSettings;

public class SpeakPunctuationPreference extends DialogPreference {
    private RadioButton mAll;
    private RadioButton mSome;
    private RadioButton mCustom;
    private RadioButton mNone;
    private EditText mPunctuationCharacters;

    private VoiceSettings mSettings;

    public SpeakPunctuationPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setDialogLayoutResource(R.layout.speak_punctuation_preference);
        setLayoutResource(R.layout.information_view);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    public SpeakPunctuationPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpeakPunctuationPreference(Context context) {
        this(context, null);
    }

    public void setVoiceSettings(VoiceSettings settings) {
        mSettings = settings;
        onDataChanged(mSettings.getPunctuationLevel(), mSettings.getPunctuationCharacters());
    }

    private void onDataChanged(int level, String characters) {
        switch (level) {
            case SpeechSynthesis.PUNCT_ALL:
                callChangeListener(getContext().getText(R.string.punctuation_all));
                break;
            case SpeechSynthesis.PUNCT_SOME:
                callChangeListener(getContext().getText(R.string.punctuation_some));
                break;
            case SpeechSynthesis.PUNCT_CUSTOM:
                if (characters == null || characters.isEmpty()) {
                    callChangeListener(getContext().getText(R.string.punctuation_none));
                } else {
                    callChangeListener(String.format(getContext().getText(R.string.punctuation_custom_fmt).toString(), characters));
                }
                break;
            case SpeechSynthesis.PUNCT_NONE:
                callChangeListener(getContext().getText(R.string.punctuation_none));
                break;
        }
    }

    @Override
    protected View onCreateDialogView() {
        View root = super.onCreateDialogView();
        mAll = (RadioButton)root.findViewById(R.id.all);
        mSome = (RadioButton)root.findViewById(R.id.some);
        mCustom = (RadioButton)root.findViewById(R.id.custom);
        mNone = (RadioButton)root.findViewById(R.id.none);
        mPunctuationCharacters = (EditText)root.findViewById(R.id.punctuation_characters);

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isCustom = mCustom.isChecked();
                mPunctuationCharacters.setEnabled(isCustom);
                mPunctuationCharacters.setFocusable(isCustom);
                mPunctuationCharacters.setFocusableInTouchMode(isCustom);
            }
        };

        mAll.setOnClickListener(listener);
        mSome.setOnClickListener(listener);
        mCustom.setOnClickListener(listener);
        mNone.setOnClickListener(listener);

        return root;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        switch (mSettings.getPunctuationLevel()) {
            case SpeechSynthesis.PUNCT_ALL:
                mAll.setChecked(true);
                break;
            case SpeechSynthesis.PUNCT_SOME:
                mSome.setChecked(true);
                break;
            case SpeechSynthesis.PUNCT_CUSTOM:
                mCustom.setChecked(true);
                break;
            case SpeechSynthesis.PUNCT_NONE:
                mNone.setChecked(true);
                break;
        }

        boolean isCustom = mCustom.isChecked();
        mPunctuationCharacters.setEnabled(isCustom);
        mPunctuationCharacters.setFocusable(isCustom);
        mPunctuationCharacters.setFocusableInTouchMode(isCustom);
        
        mPunctuationCharacters.setText(mSettings.getPunctuationCharacters());
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                Editable text = mPunctuationCharacters.getText();
                String characters = null;
                int level;
                if (text != null) {
                    characters = text.toString();
                }

                if (mNone.isChecked()) {
                    level = SpeechSynthesis.PUNCT_NONE;
                } else if (mSome.isChecked()) {
                    level = SpeechSynthesis.PUNCT_SOME;
                } else if (mAll.isChecked()) {
                    level = SpeechSynthesis.PUNCT_ALL;
                } else if (mCustom.isChecked()) {
                    level = SpeechSynthesis.PUNCT_CUSTOM;
                } else {
                    level = (characters == null || characters.isEmpty()) ? SpeechSynthesis.PUNCT_NONE : SpeechSynthesis.PUNCT_CUSTOM;
                }

                onDataChanged(level, characters);

                if (shouldCommit()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        PreferenceManager preferenceManager = getPreferenceManager();
                        preferenceManager.setStorageDeviceProtected();
                    }
                    SharedPreferences.Editor editor = getEditor();
                    if (editor != null) {
                        editor.putString(VoiceSettings.PREF_PUNCTUATION_CHARACTERS, characters);
                        editor.putString(VoiceSettings.PREF_PUNCTUATION_LEVEL, Integer.toString(level));
                        editor.commit();
                    }
                }
                break;
        }
        super.onClick(dialog, which);
    }
}