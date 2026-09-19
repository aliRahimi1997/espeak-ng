package com.reecedunn.espeak;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.util.Log;
import android.util.Pair;

import com.reecedunn.espeak.SpeechSynthesis.SynthReadyCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@SuppressLint("NewApi")
public class TtsService extends TextToSpeechService {
    private static final String TAG = TtsService.class.getSimpleName();
    private static Context storageContext;
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private SpeechSynthesis mEngine;
    private SynthesisCallback mCallback;

    private String mSynthText;
    private int mSynthTextOffset;
    private UnicodeNormalization.Result mSynthNormalization;
    private int mSynthTextCodePoints;
    private int mAnchorCodePoint;
    private int mAnchorOffset;

    private List<Voice> mAllVoices = new ArrayList<Voice>();
    private final Map<String, Voice> mAvailableVoices = new HashMap<String, Voice>();
    protected Voice mMatchingVoice = null;

    private SharedPreferences mPreferences;

    @Override
    public void onCreate() {
        storageContext = EspeakApp.getStorageContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            storageContext.moveSharedPreferencesFrom(this, this.getPackageName() + "_preferences");
        mPreferences = PreferenceManager.getDefaultSharedPreferences(storageContext);
        
        if (!CheckVoiceData.hasBaseResources(storageContext)
                || CheckVoiceData.canUpgradeResources(storageContext)) {
            CheckVoiceData.extractVoiceData(storageContext);
        }

        if (!mPreferences.contains(VoiceSettings.PREF_UNICODE_NORMALIZATION)) {
            mPreferences.edit().putBoolean(VoiceSettings.PREF_UNICODE_NORMALIZATION, true).apply();
        }

        if (!mPreferences.contains(VoiceSettings.PREF_EMOJI_ENABLED)) {
            mPreferences.edit().putBoolean(VoiceSettings.PREF_EMOJI_ENABLED, true).apply();
        }

        initializeTtsEngine();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void initializeTtsEngine() {
        if (mEngine != null) {
            mEngine.stop();
            mEngine = null;
        }

        mEngine = new SpeechSynthesis(storageContext, mSynthCallback);
        mMatchingVoice = null;
        List<Voice> voices = mEngine.getAvailableVoices();
        synchronized (mAvailableVoices) {
            mAllVoices = new ArrayList<Voice>(voices);
            if (DEBUG) Log.i(TAG, "initializeTtsEngine(): loaded voices=" + mAllVoices.size());
        }
        rebuildAvailableVoices();
    }

    @Override
    protected String[] onGetLanguage() {
        final Voice voice;
        synchronized (mAvailableVoices) {
            voice = mMatchingVoice;
        }
        if (voice == null) {
            return new String[] { "eng", "GBR", "" };
        }
        return new String[] {
            voice.locale.getISO3Language(),
            voice.locale.getISO3Country(),
            voice.locale.getVariant()
        };
    }

    private Pair<Voice, Integer> findVoice(String language, String country, String variant) {
        if (!CheckVoiceData.hasBaseResources(storageContext)) {
            return new Pair<>(null, TextToSpeech.LANG_MISSING_DATA);
        }

        final Locale query = new Locale(language, country, variant);

        Voice languageVoice = null;
        Voice countryVoice = null;

        synchronized (mAvailableVoices) {
            for (Voice voice : mAvailableVoices.values()) {
                switch (voice.match(query)) {
                    case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                        return new Pair<>(voice, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE);
                    case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                        countryVoice = voice;
                    case TextToSpeech.LANG_AVAILABLE:
                        languageVoice = voice;
                        break;
                }
            }
        }

        if (languageVoice == null) {
            return new Pair<>(null, TextToSpeech.LANG_NOT_SUPPORTED);
        } else if (countryVoice == null) {
            return new Pair<>(languageVoice, TextToSpeech.LANG_AVAILABLE);
        } else {
            return new Pair<>(countryVoice, TextToSpeech.LANG_COUNTRY_AVAILABLE);
        }
    }

    private Pair<Voice, Integer> getDefaultVoiceFor(String language, String country, String variant) {
        final Pair<Voice, Integer> match = findVoice(language, country, variant);
        switch (match.second) {
            case TextToSpeech.LANG_AVAILABLE:
                if (language.equals("fr") || language.equals("fra")) {
                    return new Pair<>(findVoice(language, "FRA", "").first, match.second);
                }
                if (language.equals("pt") || language.equals("por")) {
                    return new Pair<>(findVoice(language, "PRT", "").first, match.second);
                }
                return new Pair<>(findVoice(language, "", "").first, match.second);
            case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                if ((language.equals("vi") || language.equals("vie")) && (country.equals("VN") || country.equals("VNM"))) {
                    return new Pair<>(findVoice(language, country, "hue").first, match.second);
                }
                return new Pair<>(findVoice(language, country, "").first, match.second);
            default:
                return match;
        }
    }

    @Override
    protected int onIsLanguageAvailable(String language, String country, String variant) {
        final int result = findVoice(language, country, variant).second;
        if (result == TextToSpeech.LANG_NOT_SUPPORTED) {
            synchronized (mAvailableVoices) {
                if (!mAvailableVoices.isEmpty()) {
                    return TextToSpeech.LANG_AVAILABLE;
                }
            }
        }
        return result;
    }

    @Override
    protected int onLoadLanguage(String language, String country, String variant) {
        final Pair<Voice, Integer> match = getDefaultVoiceFor(language, country, variant);
        if (match.first != null) {
            synchronized (mAvailableVoices) {
                mMatchingVoice = match.first;
            }
            return match.second;
        }
        if (match.second == TextToSpeech.LANG_NOT_SUPPORTED) {
            synchronized (mAvailableVoices) {
                if (mMatchingVoice != null) {
                    return TextToSpeech.LANG_AVAILABLE;
                }
                if (!mAvailableVoices.isEmpty()) {
                    mMatchingVoice = mAvailableVoices.values().iterator().next();
                    return TextToSpeech.LANG_AVAILABLE;
                }
            }
        }
        return match.second;
    }

    @Override
    protected Set<String> onGetFeaturesForLanguage(String lang, String country, String variant) {
        return new HashSet<String>();
    }

    @Override
    public String onGetDefaultVoiceNameFor(String language, String country, String variant) {
        final Voice match = getDefaultVoiceFor(language, country, variant).first;
        return (match == null) ? null : match.name;
    }

    @Override
    public List<android.speech.tts.Voice> onGetVoices() {
        rebuildAvailableVoices();
        List<android.speech.tts.Voice> voices = new ArrayList<android.speech.tts.Voice>();
        synchronized (mAvailableVoices) {
            for (Voice voice : mAvailableVoices.values()) {
                int quality = android.speech.tts.Voice.QUALITY_NORMAL;
                int latency = android.speech.tts.Voice.LATENCY_VERY_LOW;
                Locale locale = new Locale(voice.locale.getISO3Language(), voice.locale.getISO3Country(), voice.locale.getVariant());
                Set<String> features = onGetFeaturesForLanguage(locale.getLanguage(), locale.getCountry(), locale.getVariant());
                voices.add(new android.speech.tts.Voice(voice.name, voice.locale, quality, latency, false, features));
            }
        }
        return voices;
    }

    @Override
    public int onIsValidVoiceName(String name) {
        synchronized (mAvailableVoices) {
            Voice voice = mAvailableVoices.get(name);
            return (voice == null) ? TextToSpeech.ERROR : TextToSpeech.SUCCESS;
        }
    }

    @Override
    public int onLoadVoice(String name) {
        synchronized (mAvailableVoices) {
            Voice voice = mAvailableVoices.get(name);
            if (voice == null) {
                return TextToSpeech.ERROR;
            }
            mMatchingVoice = voice;
            return TextToSpeech.SUCCESS;
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "Received stop request.");

        mEngine.stop();
    }

    @SuppressWarnings("deprecation")
    private String getRequestString(SynthesisRequest request) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return request.getCharSequenceText().toString();
        } else {
            return request.getText();
        }
    }

    protected int selectLanguageWithFallback(String language, String country, String variant) {
        final int result = onLoadLanguage(language, country, variant);
        switch (result) {
            case TextToSpeech.LANG_MISSING_DATA:
                return TextToSpeech.ERROR;
            case TextToSpeech.LANG_NOT_SUPPORTED:
                synchronized (mAvailableVoices) {
                    if (mMatchingVoice != null) {
                        return TextToSpeech.SUCCESS;
                    }
                    if (!mAvailableVoices.isEmpty()) {
                        mMatchingVoice = mAvailableVoices.values().iterator().next();
                        return TextToSpeech.SUCCESS;
                    }
                }
                return TextToSpeech.ERROR;
        }
        return TextToSpeech.SUCCESS;
    }

    private int selectVoice(SynthesisRequest request) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final String name = request.getVoiceName();
            if (name != null && !name.isEmpty()
                    && onLoadVoice(name) == TextToSpeech.SUCCESS) {
                return TextToSpeech.SUCCESS;
            }
        }
        return selectLanguageWithFallback(request.getLanguage(), request.getCountry(), request.getVariant());
    }

    private void reportError(SynthesisCallback callback, int errorCode) {
        callback.error(errorCode);
        callback.done();
    }

    private int codePointToOffset(int codePointIndex) {
        if (codePointIndex <= 0) {
            return 0;
        }
        if (codePointIndex >= mSynthTextCodePoints) {
            return mSynthText.length();
        }
        if (codePointIndex < mAnchorCodePoint) {
            mAnchorCodePoint = 0;
            mAnchorOffset = 0;
        }
        mAnchorOffset = mSynthText.offsetByCodePoints(
                mAnchorOffset, codePointIndex - mAnchorCodePoint);
        mAnchorCodePoint = codePointIndex;
        return mAnchorOffset;
    }

    private String filterPersianDates(String text) {
        if (text == null || text.isEmpty()) return text;
        if (text.trim().equals("\u0648")) {
            return "\u0648\u0627\u0648";
        }
        text = text.replaceAll("(?<=[0-9\\u0660-\\u0669\\u06F0-\\u06F9])-(?=[0-9\\u0660-\\u0669\\u06F0-\\u06F9])", " ");
        text = text.replaceAll("(?<=[a-zA-Z\\u0600-\\u06FF])-(?=[0-9\\u0660-\\u0669\\u06F0-\\u06F9])", " ");
        text = text.replaceAll("(?<=[0-9\\u0660-\\u0669\\u06F0-\\u06F9])-(?=[a-zA-Z\\u0600-\\u06FF])", " ");
        text = text.replaceAll("(?<=[a-zA-Z\\u0600-\\u06FF])-(?![0-9\\u0660-\\u0669\\u06F0-\\u06F9a-zA-Z\\u0600-\\u06FF])", "");
        text = text.replaceAll("(?<=\\s|^)\u200C|\u200C(?=\\s|$)", "");
        return text;
    }

    @Override
    protected synchronized void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        if (selectVoice(request) == TextToSpeech.ERROR) {
            reportError(callback, CheckVoiceData.hasBaseResources(storageContext)
                    ? TextToSpeech.ERROR_SERVICE : TextToSpeech.ERROR_NOT_INSTALLED_YET);
            return;
        }

        final Voice voice;
        synchronized (mAvailableVoices) {
            voice = mMatchingVoice;
        }
        if (voice == null) {
            reportError(callback, TextToSpeech.ERROR_SERVICE);
            return;
        }

        String text = getRequestString(request);
        if (text == null) {
            reportError(callback, TextToSpeech.ERROR_INVALID_REQUEST);
            return;
        }

text = text.replaceAll("[\\s\u200C]*[\u2800-\u28FF]+[\\s\u200C]*", " ");
        if (DEBUG) {
            Log.i(TAG, "Received synthesis request: {language=\"" + voice.name + "\"}");

            final Bundle params = request.getParams();
            for (String key : params.keySet()) {
                Log.v(TAG,
                        "Synthesis request contained param {" + key + ", " + params.get(key) + "}");
            }
        }
        
        if (voice.name != null && voice.name.startsWith("fa")) {
             text = filterPersianDates(text);
        }

        int textOffset = 0;
        if (text.startsWith("<?xml"))
        {
            final int terminator = text.indexOf("?>");
            if (terminator >= 0)
            {
                final int declarationEnd = terminator + 2;
                textOffset = declarationEnd;
                while (textOffset < text.length() && text.charAt(textOffset) <= ' ') {
                    textOffset++;
                }
                text = text.substring(declarationEnd).trim();
            }
        }

        final VoiceSettings settings = new VoiceSettings(PreferenceManager.getDefaultSharedPreferences(storageContext), mEngine);

        final boolean isSsml = text.startsWith("<speak");

        final boolean speakDigits = settings.isSpeakDigitsEnabled() && !isSsml;
        if (speakDigits) {
            text = spaceSeparateDigits(text);
        }

        UnicodeNormalization.Result normalization = null;
        if (settings.isUnicodeNormalizationEnabled()) {
            normalization = UnicodeNormalization.normalize(text);
            if (normalization != null) {
                text = normalization.text;
                if (speakDigits) {
                    text = spaceSeparateDigits(text);
                    normalization = null;
                }
            }
        }

        if (!settings.isEmojiReadingEnabled()) {
            String filteredText = EmojiProcessor.removeEmojis(text);
            if (filteredText != null && !filteredText.equals(text)) {
                text = filteredText;
                normalization = null;
            }
        }

        // اصلاح هوشمند صفر زمان (مثل تبدیل 01:24 به 1:24 بدون آسیب به شماره تلفن)
        text = text.replaceAll("(?<!\\d)0+(?=\\d:)", "");

        if (!isSsml) {
            int punctLevel = settings.getPunctuationLevel();
            boolean isCustomValid = (punctLevel == SpeechSynthesis.PUNCT_CUSTOM) && 
                                    (settings.getPunctuationCharacters() != null && !settings.getPunctuationCharacters().trim().isEmpty());

            String prosodyChars = ".,!?;،؛؟"; 
            String regexAllPunct;
            
            if (punctLevel == SpeechSynthesis.PUNCT_NONE) {
                regexAllPunct = "[\\p{P}\\p{S}&&[^" + prosodyChars + "']]";
                text = text.replaceAll(regexAllPunct, " ");
            } else if (punctLevel == SpeechSynthesis.PUNCT_SOME) {
                text = text.replaceAll("[\"()\\[\\]{}\\-«»]", " ");
            } else if (punctLevel == SpeechSynthesis.PUNCT_CUSTOM) {
                if (!isCustomValid) {
                    regexAllPunct = "[\\p{P}\\p{S}&&[^" + prosodyChars + "']]";
                } else {
                    String customChars = settings.getPunctuationCharacters();
                    String escaped = customChars.replaceAll("([\\\\\\[\\]\\^\\-&])", "\\\\$1");
                    regexAllPunct = "[\\p{P}\\p{S}&&[^" + prosodyChars + escaped + "]]";
                }
                text = text.replaceAll(regexAllPunct, " ");
            }
        }

        mSynthText = text;
        mSynthTextOffset = textOffset;
        mSynthNormalization = normalization;
        mSynthTextCodePoints = text.codePointCount(0, text.length());
        mAnchorCodePoint = 0;
        mAnchorOffset = 0;

        mCallback = callback;
        mCallback.start(mEngine.getSampleRate(), mEngine.getAudioFormat(), mEngine.getChannelCount());

        mEngine.setVoice(voice, settings.getVoiceVariant());

        int rate = settings.getRate();
        int rateScale = request.getSpeechRate();
        if (rateScale <= 0) {
            rateScale = 100;
        }
        rate = (int)(((long)rate * rateScale) / 100);
        mEngine.Rate.setValue(rate);
        mEngine.Pitch.setValue(settings.getPitch(), request.getPitch());
        mEngine.PitchRange.setValue(settings.getPitchRange());
        mEngine.Volume.setValue(settings.getVolume());
        
        // نگاشت سطح سفارشی (3) به سطح معتبر برای موتور (2) جهت جلوگیری از کرش یا ریست شدن
        int enginePunctLevel = settings.getPunctuationLevel();
        if (enginePunctLevel == SpeechSynthesis.PUNCT_CUSTOM) {
            enginePunctLevel = SpeechSynthesis.PUNCT_SOME; 
        }
        mEngine.Punctuation.setValue(enginePunctLevel);
        
        mEngine.setPunctuationCharacters(settings.getPunctuationCharacters());
        mEngine.synthesize(text, isSsml);
    }

    protected void rebuildAvailableVoices() {
        synchronized (mAvailableVoices) {
            mAvailableVoices.clear();
            List<Voice> voices = mAllVoices;
            for (Voice voice : voices) {
                mAvailableVoices.put(voice.name, voice);
            }
            if (DEBUG) {
                Log.i(TAG, "Rebuilt voices: exposed=" + mAvailableVoices.size());
            }
            if (mMatchingVoice != null && !mAvailableVoices.containsKey(mMatchingVoice.name)) {
                mMatchingVoice = null;
            }
        }
    }

    static String spaceSeparateDigits(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        final int len = text.length();
        StringBuilder out = new StringBuilder(len * 2);
        boolean prevWasDigit = false;
        for (int i = 0; i < len; ) {
            final int c = text.codePointAt(i);
            final int charCount = Character.charCount(c);
            final boolean isDigit = Character.isDigit(c);
            if (isDigit && prevWasDigit) {
                out.append(' ');
            }
            out.appendCodePoint(c);
            prevWasDigit = isDigit;
            i += charCount;
        }
        return out.toString();
    }

    private final SpeechSynthesis.SynthReadyCallback mSynthCallback = new SynthReadyCallback() {
        @Override
        public void onSynthDataReady(byte[] audioData) {
            if ((audioData == null) || (audioData.length == 0)) {
                onSynthDataComplete();
                return;
            }

            final int maxBytesToCopy = mCallback.getMaxBufferSize();

            int offset = 0;

            while (offset < audioData.length) {
                final int bytesToWrite = Math.min(maxBytesToCopy, (audioData.length - offset));
                if (mCallback.audioAvailable(audioData, offset, bytesToWrite)
                        != TextToSpeech.SUCCESS) {
                    mEngine.stop();
                    return;
                }
                offset += bytesToWrite;
            }
        }

        @Override
        public void onSynthDataComplete() {
            mCallback.done();
        }

        @Override
        public void onSynthWordBoundary(int textPosition, int textLength, int markerInFrames) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || mSynthText == null) {
                return;
            }

            final int wordStart = textPosition - 1;
            int start = codePointToOffset(wordStart);
            int end = codePointToOffset(wordStart + Math.max(textLength, 0));
            if (mSynthNormalization != null) {
                start = mSynthNormalization.toOriginalOffset(start);
                end = mSynthNormalization.toOriginalOffset(end);
            }
            if (end <= start) {
                return;
            }

            mCallback.rangeStart(markerInFrames, mSynthTextOffset + start, mSynthTextOffset + end);
        }
    };
}