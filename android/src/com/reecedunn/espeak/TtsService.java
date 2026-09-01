/*
 * Copyright (C) 2022 Beka Gozalishvili
 * Copyright (C) 2012-2015 Reece H. Dunn
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This file implements the Android Text-to-Speech engine for eSpeak.
 *
 * Android Version: 4.0 (Ice Cream Sandwich)
 * API Version: 14
 */

package com.reecedunn.espeak;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
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

/**
 * Implements the eSpeak engine as a {@link TextToSpeechService}.
 *
 * @author msclrhd@gmail.com (Reece H. Dunn)
 * @author alanv@google.com (Alan Viverette)
 */
@SuppressLint("NewApi")
public class TtsService extends TextToSpeechService {
    public static final String ESPEAK_INITIALIZED = "com.reecedunn.espeak.ESPEAK_INITIALIZED";

    private static final String TAG = TtsService.class.getSimpleName();
    private static Context storageContext;
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private SpeechSynthesis mEngine;
    private SynthesisCallback mCallback;

    /** Text handed to eSpeak for the current request. */
    private String mSynthText;
    /** Where {@link #mSynthText} starts within the text the caller supplied. */
    private int mSynthTextOffset;
    /**
     * Offset map back to the caller's text when {@link #mSynthText} is a
     * normalized copy of it, or null when they are the same string.
     */
    private UnicodeNormalization.Result mSynthNormalization;
    /** Number of code points in {@link #mSynthText}. */
    private int mSynthTextCodePoints;
    /** Anchor for incremental code point to UTF-16 index conversion. */
    private int mAnchorCodePoint;
    private int mAnchorOffset;

    private final Map<String, Voice> mAvailableVoices = new HashMap<String, Voice>();

    protected Voice mMatchingVoice = null;

    @Override
    public void onCreate() {
        storageContext = EspeakApp.getStorageContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            storageContext.moveSharedPreferencesFrom(this, this.getPackageName() + "_preferences");

        if (!CheckVoiceData.hasBaseResources(storageContext)
                || CheckVoiceData.canUpgradeResources(storageContext)) {
            CheckVoiceData.extractVoiceData(storageContext);
        }

        initializeTtsEngine();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * Sets up the native eSpeak engine.
     */
    private void initializeTtsEngine() {
        if (mEngine != null) {
            mEngine.stop();
            mEngine = null;
        }

        mEngine = new SpeechSynthesis(storageContext, mSynthCallback);

        mAvailableVoices.clear();

        for (Voice voice : mEngine.getAvailableVoices()) {
            mAvailableVoices.put(voice.name, voice);
        }

        final Intent intent = new Intent(ESPEAK_INITIALIZED);

        sendBroadcast(intent);
    }

    @Override
    protected String[] onGetLanguage() {
        // This is used to specify the language requested from GetSampleText.

        if (mMatchingVoice == null) {
            return new String[] { "eng", "GBR", "" };
        }

        return new String[] {
                mMatchingVoice.locale.getISO3Language(),
                mMatchingVoice.locale.getISO3Country(),
                mMatchingVoice.locale.getVariant()
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
        return findVoice(language, country, variant).second;
    }

    @Override
    protected int onLoadLanguage(String language, String country, String variant) {
        final Pair<Voice, Integer> match = getDefaultVoiceFor(language, country, variant);
        if (match.first != null) {
            mMatchingVoice = match.first;
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
        List<android.speech.tts.Voice> voices = new ArrayList<android.speech.tts.Voice>();
        for (Voice voice : mAvailableVoices.values()) {
            int quality = android.speech.tts.Voice.QUALITY_NORMAL;
            int latency = android.speech.tts.Voice.LATENCY_VERY_LOW;
            Locale locale = new Locale(voice.locale.getISO3Language(), voice.locale.getISO3Country(), voice.locale.getVariant());
            Set<String> features = onGetFeaturesForLanguage(locale.getLanguage(), locale.getCountry(), locale.getVariant());
            voices.add(new android.speech.tts.Voice(voice.name, voice.locale, quality, latency, false, features));
        }
        return voices;
    }

    @Override
    public int onIsValidVoiceName(String name) {
        Voice voice = mAvailableVoices.get(name);
        return (voice == null) ? TextToSpeech.ERROR : TextToSpeech.SUCCESS;
    }

    @Override
    public int onLoadVoice(String name) {
        Voice voice = mAvailableVoices.get(name);
        if (voice == null) {
            return TextToSpeech.ERROR;
        }
        mMatchingVoice = voice;
        return TextToSpeech.SUCCESS;
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
    private int selectVoice(SynthesisRequest request) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final String name = request.getVoiceName();
            if (name != null && !name.isEmpty()) {
                return onLoadVoice(name);
            }
        }

        final int result = onLoadLanguage(request.getLanguage(), request.getCountry(), request.getVariant());
        switch (result) {
            case TextToSpeech.LANG_MISSING_DATA:
            case TextToSpeech.LANG_NOT_SUPPORTED:
                return TextToSpeech.ERROR;
        }
        return TextToSpeech.SUCCESS;
    }

    @Override
    protected synchronized void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        if (mMatchingVoice == null)
            return;

        String text = getRequestString(request);
        if (text == null)
            return;

        if (DEBUG) {
            Log.i(TAG, "Received synthesis request: {language=\"" + mMatchingVoice.name + "\"}");

            final Bundle params = request.getParams();
            for (String key : params.keySet()) {
                Log.v(TAG,
                        "Synthesis request contained param {" + key + ", " + params.get(key) + "}");
            }
        }

        int textOffset = 0;

        if (text.startsWith("<?xml"))
        {
            // eSpeak does not recognise/skip "<?...?>" preprocessing tags,
            // so need to remove these before passing to synthesize. A
            // declaration missing its "?>" is left alone rather than having
            // its first character eaten by a -1 index.
            final int terminator = text.indexOf("?>");
            if (terminator >= 0)
            {
                final int declarationEnd = terminator + 2;

                // Track what was dropped from the front, so that word
                // boundaries can be reported against the text the caller
                // actually passed in.
                textOffset = declarationEnd;
                while (textOffset < text.length() && text.charAt(textOffset) <= ' ') {
                    textOffset++;
                }

                text = text.substring(declarationEnd).trim();
            }
        }

        final VoiceSettings settings = new VoiceSettings(
                PreferenceManager.getDefaultSharedPreferences(storageContext),
                mEngine);

        UnicodeNormalization.Result normalization = null;
        if (settings.isUnicodeNormalizationEnabled()) {
            // NFKC leaves ASCII untouched, so SSML markup passes through
            // unchanged and the "<speak" sniff below still works.
            normalization = UnicodeNormalization.normalize(text);
            if (normalization != null) {
                text = normalization.text;
            }
        }

        mSynthText = text;
        mSynthTextOffset = textOffset;
        mSynthNormalization = normalization;
        mSynthTextCodePoints = text.codePointCount(0, text.length());
        mAnchorCodePoint = 0;
        mAnchorOffset = 0;

        mCallback = callback;
        mCallback.start(
                mEngine.getSampleRate(),
                mEngine.getAudioFormat(),
                mEngine.getChannelCount());

        mEngine.setVoice(mMatchingVoice, settings.getVoiceVariant());

        mEngine.Rate.setValue(settings.getRate(), request.getSpeechRate());
        mEngine.Pitch.setValue(settings.getPitch(), request.getPitch());
        mEngine.PitchRange.setValue(settings.getPitchRange());
        mEngine.Volume.setValue(settings.getVolume());
        mEngine.Punctuation.setValue(settings.getPunctuationLevel());
        mEngine.setPunctuationCharacters(settings.getPunctuationCharacters());
        mEngine.synthesize(text, text.startsWith("<speak"));
    }

    /**
     * Pipes synthesizer output from native eSpeak to an {@link AudioTrack}.
     */
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
                final int bytesToWrite = Math.min(
                        maxBytesToCopy,
                        (audioData.length - offset));
                mCallback.audioAvailable(audioData, offset, bytesToWrite);
                offset += bytesToWrite;
            }
        }

        @Override
        public void onSynthDataComplete() {
            mCallback.done();
        }

        @Override
        public void onSynthWordBoundary(int textPosition, int textLength, int markerInFrames) {
            // rangeStart() is API 26; below that the framework has no way to
            // deliver word boundaries to the caller.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || mSynthText == null) {
                return;
            }

            // eSpeak counts code points from 1, rangeStart() wants 0-based UTF-16
            // indices into the text the caller supplied.
            final int wordStart = textPosition - 1;
            int start = codePointToOffset(wordStart);
            int end = codePointToOffset(wordStart + Math.max(textLength, 0));

            if (mSynthNormalization != null) {
                // The engine spoke normalized text; report the range against
                // the original so highlighting tracks the caller's string.
                start = mSynthNormalization.toOriginalOffset(start);
                end = mSynthNormalization.toOriginalOffset(end);
            }

            if (end <= start) {
                return;
            }

            mCallback.rangeStart(
                    markerInFrames,
                    mSynthTextOffset + start,
                    mSynthTextOffset + end);
        }
    };

    /**
     * Converts a code point index in the text handed to eSpeak into a
     * UTF-16 offset.
     *
     * eSpeak reports word boundaries using Unicode code point positions,
     * while Android's rangeStart() uses UTF-16 offsets.
     */
    private int codePointToOffset(int codePoint) {
        if (codePoint <= 0) {
            return 0;
        }

        if (codePoint >= mSynthTextCodePoints) {
            return mSynthText.length();
        }

        if (codePoint < mAnchorCodePoint) {
            mAnchorCodePoint = 0;
            mAnchorOffset = 0;
        }

        mAnchorOffset = mSynthText.offsetByCodePoints(
                mAnchorOffset,
                codePoint - mAnchorCodePoint);
        mAnchorCodePoint = codePoint;

        return mAnchorOffset;
    }
}