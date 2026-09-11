package com.reecedunn.espeak;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.TextView;

import com.reecedunn.espeak.preference.ImportVoicePreference;
import com.reecedunn.espeak.preference.SeekBarPreference;
import com.reecedunn.espeak.preference.SpeakPunctuationPreference;
import com.reecedunn.espeak.preference.VoiceVariantPreference;

import java.util.List;

public class TtsSettingsActivity extends PreferenceActivity {

    private static Context storageContext;
    private static final String TAG = TtsSettingsActivity.class.getSimpleName();

    private static final String PREF_VOICE_PARAMETERS = "espeak_voice_parameters";

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts_settings);

        TextView settingsTitle = (TextView) findViewById(R.id.settings_title);
        if (settingsTitle != null) {
            settingsTitle.setText(getApplicationInfo().loadLabel(getPackageManager()));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        {
            PreferenceManager preferenceManager = getPreferenceManager();
            preferenceManager.setStorageDeviceProtected ();
        }

        storageContext = EspeakApp.getStorageContext();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storageContext);
        final SharedPreferences.Editor editor = prefs.edit();

        String pitch = prefs.getString(VoiceSettings.PREF_PITCH, null);
        if (pitch == null) {
            pitch = prefs.getString(VoiceSettings.PREF_DEFAULT_PITCH, "100");
            int pitchValue = Integer.parseInt(pitch) / 2;
            editor.putString(VoiceSettings.PREF_PITCH, Integer.toString(pitchValue));
        }

        String rate = prefs.getString(VoiceSettings.PREF_RATE, null);
        if (rate == null) {
            SpeechSynthesis engine = new SpeechSynthesis(storageContext, null);
            int defaultValue = engine.Rate.getDefaultValue();
            int maxValue = engine.Rate.getMaxValue();

            rate = prefs.getString(VoiceSettings.PREF_DEFAULT_RATE, "100");
            int rateValue = (Integer.parseInt(rate) / 100) * defaultValue;
            if (rateValue < defaultValue) rateValue = defaultValue;
            if (rateValue > maxValue) rateValue = maxValue;
            editor.putString(VoiceSettings.PREF_RATE, Integer.toString(rateValue));
        }

        String variant = prefs.getString(VoiceSettings.PREF_VARIANT, null);
        if (variant == null) {
            String gender = prefs.getString(VoiceSettings.PREF_DEFAULT_GENDER, "0");
            if (gender.equals("2")) {
                editor.putString(VoiceSettings.PREF_VARIANT, VoiceVariant.FEMALE);
            } else {
                editor.putString(VoiceSettings.PREF_VARIANT, VoiceVariant.MALE);
            }
        }

        if (!prefs.contains(VoiceSettings.PREF_UNICODE_NORMALIZATION)) {
            editor.putBoolean(VoiceSettings.PREF_UNICODE_NORMALIZATION, true);
        }

        if (!prefs.contains(VoiceSettings.PREF_EMOJI_ENABLED)) {
            editor.putBoolean(VoiceSettings.PREF_EMOJI_ENABLED, true);
        }

        editor.commit();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
        {
            getFragmentManager().beginTransaction().replace(
                    R.id.preference_container,
                    new PrefsEspeakFragment()).commit();
        }
        else
        {
            addPreferencesFromResource(R.xml.preferences);
            createPreferences(TtsSettingsActivity.this, getPreferenceScreen());
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return PrefsEspeakFragment.class.getName().equals(fragmentName);
    }

    public static class PrefsEspeakFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);
            createPreferences(getActivity(), getPreferenceScreen());
        }
    }

    public static class InfoPreference extends Preference {
        public InfoPreference(Context context) {
            super(context);
            setSelectable(false);
        }
        
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);

            view.setClickable(false);
            view.setFocusable(false);
            view.setBackgroundResource(0);

            view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClickable(false);
                    info.setFocusable(false);
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        info.removeAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
                    }
                }
            });
        }
    }

    private static Preference createImportVoicePreference(Context context) {
        final String title = context.getString(R.string.import_voice_title);

        final ImportVoicePreference pref = new ImportVoicePreference(context);
        pref.setTitle(title);
        pref.setDialogTitle(title);
        pref.setOnPreferenceChangeListener(mOnPreferenceChanged);
        pref.setDescription(R.string.import_voice_description);
        return pref;
    }

    private static Preference createVoiceVariantPreference(Context context, VoiceSettings settings, int titleRes) {
        final String title = context.getString(titleRes);

        final VoiceVariantPreference pref = new VoiceVariantPreference(context);
        pref.setTitle(title);
        pref.setDialogTitle(title);
        pref.setOnPreferenceChangeListener(mOnPreferenceChanged);
        pref.setPersistent(true);
        pref.setVoiceVariant(settings.getVoiceVariant());
        return pref;
    }

    private static Preference createSpeakPunctuationPreference(Context context, VoiceSettings settings, int titleRes) {
        final String title = context.getString(titleRes);

        final SpeakPunctuationPreference pref = new SpeakPunctuationPreference(context);
        pref.setTitle(title);
        pref.setDialogTitle(title);
        pref.setOnPreferenceChangeListener(mOnPreferenceChanged);
        pref.setPersistent(true);
        pref.setVoiceSettings(settings);
        return pref;
    }

    private static Preference createUnicodeNormalizationPreference(Context context) {
        final CheckBoxPreference pref = new CheckBoxPreference(context);
        pref.setTitle(R.string.setting_unicode_normalization);
        pref.setSummary(R.string.setting_unicode_normalization_summary);
        pref.setKey(VoiceSettings.PREF_UNICODE_NORMALIZATION);
        pref.setPersistent(false);

        if (storageContext == null) {
            storageContext = EspeakApp.getStorageContext();
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storageContext);
        final boolean currentValue = prefs.getBoolean(VoiceSettings.PREF_UNICODE_NORMALIZATION, true);
        pref.setChecked(currentValue);

        pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final boolean isChecked = (Boolean) newValue;
                prefs.edit().putBoolean(VoiceSettings.PREF_UNICODE_NORMALIZATION, isChecked).apply();
                return true;
            }
        });

        return pref;
    }

    private static Preference createEmojiFilterPreference(Context context) {
        final CheckBoxPreference pref = new CheckBoxPreference(context);
        pref.setTitle(R.string.setting_emoji_filter_title);
        pref.setSummary(R.string.setting_emoji_filter_summary);
        pref.setKey(VoiceSettings.PREF_EMOJI_ENABLED);
        pref.setPersistent(false);

        if (storageContext == null) {
            storageContext = EspeakApp.getStorageContext();
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storageContext);
        final boolean currentValue = prefs.getBoolean(VoiceSettings.PREF_EMOJI_ENABLED, true);
        pref.setChecked(currentValue);

        pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final boolean isChecked = (Boolean) newValue;
                prefs.edit().putBoolean(VoiceSettings.PREF_EMOJI_ENABLED, isChecked).apply();
                return true;
            }
        });

        return pref;
    }

    private static Preference createEmojiInfoPreference(Context context) {
        final InfoPreference pref = new InfoPreference(context);
        pref.setTitle(R.string.setting_emoji_info_title);
        pref.setSummary(R.string.setting_emoji_info_summary);
        pref.setPersistent(false);
        return pref;
    }

    private static SeekBarPreference.Parameter voiceParameter(Context context,
                                                              SpeechSynthesis.Parameter parameter,
                                                              String key, int titleRes) {
        final String formatter;
        switch (parameter.getUnitType())
        {
            case Percentage:
                formatter = context.getString(R.string.formatter_percentage);
                break;
            case WordsPerMinute:
                formatter = context.getString(R.string.formatter_wpm);
                break;
            default:
                throw new IllegalStateException("Unsupported unit type for the parameter.");
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storageContext);
        final String value = prefs.getString(key, null);
        final int current = (value == null) ? parameter.getDefaultValue() : Integer.parseInt(value);

        final SeekBarPreference.Parameter voiceParam = new SeekBarPreference.Parameter(
                key,
                context.getString(titleRes),
                parameter.getMinValue(),
                parameter.getMaxValue(),
                parameter.getDefaultValue(),
                current,
                formatter);

        if (VoiceSettings.PREF_RATE.equals(key)) {
            voiceParam.enableRateBoost(prefs.getBoolean(VoiceSettings.PREF_RATE_BOOST, false));
        }

        return voiceParam;
    }

    private static SeekBarPreference newSeekBarPreference(Context context, String key, String title) {
        final SeekBarPreference pref = new SeekBarPreference(context);
        pref.setTitle(title);
        pref.setDialogTitle(title);
        pref.setKey(key);
        pref.setOnPreferenceChangeListener(mOnPreferenceChanged);
        pref.setPersistent(true);
        return pref;
    }

    private static Preference createSeekBarPreference(Context context,
                                                      SpeechSynthesis.Parameter parameter,
                                                      String key, int titleRes) {
        final SeekBarPreference pref = newSeekBarPreference(context, key, context.getString(titleRes));
        pref.addParameter(voiceParameter(context, parameter, key, titleRes));
        pref.setSummary(pref.buildSummary());
        return pref;
    }

    private static Preference createVoiceParamsPreference(Context context,
                                                          SpeechSynthesis engine,
                                                          int titleRes) {
        final SeekBarPreference pref = newSeekBarPreference(context, PREF_VOICE_PARAMETERS,
                context.getString(titleRes));
        pref.addParameter(voiceParameter(context, engine.Rate, VoiceSettings.PREF_RATE, R.string.setting_default_rate));
        pref.addParameter(voiceParameter(context, engine.Pitch, VoiceSettings.PREF_PITCH, R.string.setting_default_pitch));
        pref.addParameter(voiceParameter(context, engine.PitchRange, VoiceSettings.PREF_PITCH_RANGE, R.string.espeak_pitch_range));
        pref.addParameter(voiceParameter(context, engine.Volume, VoiceSettings.PREF_VOLUME, R.string.espeak_volume));
        pref.setSummary(pref.buildSummary());
        return pref;
    }

    private static void createPreferences(final Context context, final PreferenceGroup group) {
        final Context storage = storageContext;
        final Handler handler = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean isWatch = context.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_WATCH);

                final SpeechSynthesis engine = new SpeechSynthesis(storage, null);
                final List<Voice> voices = engine.getAvailableVoices();

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isGone(context)) {
                            return;
                        }
                        addPreferences(context, group, engine, voices, isWatch);
                    }
                });
            }
        }, "espeak-settings-load").start();
    }

    private static boolean isGone(Context context) {
        if (!(context instanceof Activity)) {
            return false;
        }
        final Activity activity = (Activity) context;
        if (activity.isFinishing()) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && activity.isDestroyed();
    }

    private static void addPreferences(Context context, PreferenceGroup group,
                                       SpeechSynthesis engine, List<Voice> voices,
                                       boolean isWatch) {
        VoiceSettings settings = new VoiceSettings(PreferenceManager.getDefaultSharedPreferences(storageContext), engine);

        if (!isWatch) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                group.addPreference(createImportVoicePreference(context));
            }
        }
        
        group.addPreference(createVoiceVariantPreference(context, settings, R.string.espeak_variant));
        group.addPreference(createSpeakPunctuationPreference(context, settings, R.string.espeak_speak_punctuation));
        group.addPreference(createUnicodeNormalizationPreference(context));
        group.addPreference(createEmojiFilterPreference(context));
        group.addPreference(createEmojiInfoPreference(context));

        if (isWatch) {
            group.addPreference(createSeekBarPreference(context, engine.Rate, VoiceSettings.PREF_RATE, R.string.setting_default_rate));
            group.addPreference(createSeekBarPreference(context, engine.Pitch, VoiceSettings.PREF_PITCH, R.string.setting_default_pitch));
            group.addPreference(createSeekBarPreference(context, engine.PitchRange, VoiceSettings.PREF_PITCH_RANGE, R.string.espeak_pitch_range));
            group.addPreference(createSeekBarPreference(context, engine.Volume, VoiceSettings.PREF_VOLUME, R.string.espeak_volume));
        } else {
            group.addPreference(createVoiceParamsPreference(context, engine, R.string.espeak_voice_settings));
        }
    }

    private static final OnPreferenceChangeListener mOnPreferenceChanged =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue instanceof String) {
                        String summary = "";
                        if (preference instanceof ListPreference) {
                            final ListPreference listPreference = (ListPreference) preference;
                            final int index = listPreference.findIndexOfValue((String) newValue);
                            final CharSequence[] entries = listPreference.getEntries();

                            if (index >= 0 && index < entries.length) {
                                summary = entries[index].toString();
                            }
                        } else {
                            summary = (String)newValue;
                        }
                        preference.setSummary(summary);
                    }
                    return true;
                }
            };
}