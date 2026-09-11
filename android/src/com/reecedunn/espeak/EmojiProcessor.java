package com.reecedunn.espeak;

import java.text.BreakIterator;
import java.util.BitSet;

public final class EmojiProcessor {

    private static final BitSet EMOJI_BITSET = new BitSet(Character.MAX_CODE_POINT + 1);

    static {
        // ۱. محدوده‌های اصلی و پایه
        addRange(0x1F600, 0x1F64F);
        addRange(0x1F300, 0x1F5FF);
        addRange(0x1F680, 0x1F6FF);
        addRange(0x1F780, 0x1F7FF);
        addRange(0x1F900, 0x1F9FF);
        addRange(0x1FA70, 0x1FAFF);
        addRange(0x1FEB0, 0x1FEFF);
        addRange(0x2600, 0x26FF);
        addRange(0x2700, 0x27BF);
        addRange(0x1F1E6, 0x1F1FF);
        addRange(0x1F170, 0x1F189);
        addRange(0x1F200, 0x1F2FF);
        addRange(0x1F000, 0x1F02F);
        addRange(0x1F0A0, 0x1F0FF);
        addRange(0x2800, 0x28FF);

        // ۲. محدوده‌های تکمیلی، هندسی و ایمن اضافه شده
        addRange(0x2044, 0x20BF);   // نمادهای پولی و نقطه‌گذاری
        addRange(0x2190, 0x21FF);   // پیکان‌ها و فلش‌ها
        addRange(0x25A0, 0x25FF);   // اشکال هندسی
        addRange(0x2B00, 0x2BFF);   // نمادهای متفرقه و پیکان‌ها
        addRange(0x1F18E, 0x1F19A); // حروف و نمادهای محصور در کادر
        addRange(0x3030, 0x303F);   // نمادهای CJK
        addRange(0x2100, 0x214F);   // نمادهای شبیه به حرف (بدون کاراکترهای C1)
        addRange(0x2460, 0x24FF);   // حروف و اعداد محصور
        addRange(0x2200, 0x22FF);   // عملگرهای ریاضی
        addRange(0x2A00, 0x2AFF);   // عملگرهای ریاضی تکمیلی
        addRange(0x2300, 0x23FF);   // نمادهای فنی متفرقه
        addRange(0x2934, 0x2935);   // پیکان‌های خمیده مکمل

        // ۳. کاراکترهای تگ (Tag Characters) برای پرچم‌های زیربخش
        addRange(0xE0020, 0xE007F);

        // ۴. کاراکترهای تکی استثنایی
        int[] singlePoints = {
            0x203C, // علامت تعجب دوتایی
            0x207B, // منفی زبرنوشت
            0x3297, // دکمه تبریک ژاپنی
            0x3299  // دکمه راز ژاپنی
        };
        
        for (int cp : singlePoints) {
            EMOJI_BITSET.set(cp);
        }

        // ۵. کاراکترهای اتصال‌دهنده و انتخاب‌گر سبک (نامرئی)
        EMOJI_BITSET.set(0x200D); // اتصال‌دهنده با عرض صفر (ZWJ)
        EMOJI_BITSET.set(0xFE0E); // انتخاب‌گر سبک متن
        EMOJI_BITSET.set(0xFE0F); // انتخاب‌گر سبک ایموجی
        EMOJI_BITSET.set(0x20E3); // کادر محصورکننده کلیدها
    }

    private static void addRange(int start, int end) {
        EMOJI_BITSET.set(start, end + 1);
    }

    private EmojiProcessor() {
    }

    public static boolean isEmoji(int codePoint) {
        if (codePoint < 0 || codePoint >= EMOJI_BITSET.size()) {
            return false;
        }
        // استثنای محدوده بریل
        if (codePoint >= 0x2800 && codePoint <= 0x28FF) {
            return true;
        }
        return EMOJI_BITSET.get(codePoint);
    }

    public static boolean hasEmoji(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isEmoji(cp)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    public static String removeEmojis(String text) {
        if (text == null || text.isEmpty()) return text;

        String result = removeEmojisWithBreakIterator(text);
        if (!result.equals(text)) {
            return result;
        }

        return removeEmojisByCodePoint(text);
    }

    private static String removeEmojisWithBreakIterator(String text) {
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);

        StringBuilder result = new StringBuilder(text.length());
        boolean hasEmojiFound = false;

        int start = graphemes.first();
        for (int end = graphemes.next(); end != BreakIterator.DONE; start = end, end = graphemes.next()) {
            String cluster = text.substring(start, end);
            boolean hasEmojiInCluster = false;

            for (int i = 0; i < cluster.length(); ) {
                int cp = cluster.codePointAt(i);
                if (isEmoji(cp)) {
                    hasEmojiInCluster = true;
                    hasEmojiFound = true;
                    break;
                }
                i += Character.charCount(cp);
            }

            if (!hasEmojiInCluster) {
                result.append(cluster);
            }
        }

        return hasEmojiFound ? result.toString() : text;
    }

    private static String removeEmojisByCodePoint(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (!isEmoji(cp)) {
                result.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return result.toString();
    }
}