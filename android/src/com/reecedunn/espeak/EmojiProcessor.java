package com.reecedunn.espeak;

import java.text.BreakIterator;
import java.util.BitSet;

public final class EmojiProcessor {

    private static final BitSet EMOJI_BITSET = new BitSet(Character.MAX_CODE_POINT + 1);

    static {
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
        addRange(0x2190, 0x21FF);
        addRange(0x25A0, 0x25FF);
        addRange(0x2B00, 0x2BFF);
        addRange(0x1F18E, 0x1F19A);
        addRange(0x2300, 0x23FF);
        addRange(0x2934, 0x2935);
        addRange(0xE0020, 0xE007F);

        int[] singlePoints = {
            0x3297,
            0x3299
        };
        
        for (int cp : singlePoints) {
            EMOJI_BITSET.set(cp);
        }

        EMOJI_BITSET.set(0x200D);
        EMOJI_BITSET.set(0xFE0E);
        EMOJI_BITSET.set(0xFE0F);
        EMOJI_BITSET.set(0x20E3);
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