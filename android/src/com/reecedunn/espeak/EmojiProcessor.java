package com.reecedunn.espeak;

import java.text.BreakIterator;
import java.util.BitSet;

/**
 * Highly optimized functionality to remove emoji characters and sequences
 * from text using a single-pass BitSet lookup and grapheme cluster iteration.
 */
public final class EmojiProcessor {

    // Covers the entire Unicode range (up to U+10FFFF) with ~137 KB memory footprint
    private static final BitSet EMOJI_BITSET = new BitSet(Character.MAX_CODE_POINT + 1);

    static {
        // Unicode Emoji Ranges
        addRange(0x1F600, 0x1F64F); // Emoticons
        addRange(0x1F300, 0x1F5FF); // Misc Symbols and Pictographs
        addRange(0x1F680, 0x1F6FF); // Transport and Map Symbols
        addRange(0x1F780, 0x1F7FF); // Geometric Shapes Extended
        addRange(0x1F900, 0x1F9FF); // Supplemental Symbols and Pictographs
        addRange(0x1FA70, 0x1FAFF); // Symbols and Pictographs Extended-A
        addRange(0x1FEB0, 0x1FEFF); // Symbols and Pictographs Extended-B
        addRange(0x2600, 0x26FF);   // Misc Symbols
        addRange(0x2700, 0x27BF);   // Dingbats
        addRange(0x1F1E6, 0x1F1FF); // Regional Indicator Symbols (Flags)
        addRange(0x1F170, 0x1F189); // Enclosed Alphanumeric Supplement
        addRange(0x1F200, 0x1F2FF); // Enclosed Ideographic Supplement
        addRange(0x1F000, 0x1F02F); // Mahjong Tiles
        addRange(0x1F0A0, 0x1F0FF); // Playing Cards

        // Individual Emoji Code Points (Comprehensive List)
        int[] singlePoints = {
            0x203C, 0x2049, 0x2139, 0x2194, 0x2195, 0x2196, 0x2197, 0x2198, 0x2199,
            0x21A9, 0x21AA, 0x231A, 0x231B, 0x2328, 0x23CF, 0x23E9, 0x23EA, 0x23EB,
            0x23EC, 0x23ED, 0x23EE, 0x23EF, 0x23F0, 0x23F1, 0x23F2, 0x23F3, 0x25A1,
            0x25AA, 0x25AB, 0x25B6, 0x25C0, 0x25FB, 0x25FC, 0x25FD, 0x25FE, 0x260E,
            0x2611, 0x2614, 0x2615, 0x2618, 0x261D, 0x2620, 0x2622, 0x2623, 0x2626,
            0x262A, 0x262E, 0x262F, 0x2638, 0x2639, 0x263A, 0x2640, 0x2642, 0x2648,
            0x2649, 0x264A, 0x264B, 0x264C, 0x264D, 0x264E, 0x264F, 0x2650, 0x2651,
            0x2652, 0x2653, 0x2660, 0x2663, 0x2665, 0x2666, 0x2668, 0x267B, 0x267F,
            0x2692, 0x2693, 0x2694, 0x2696, 0x2697, 0x2699, 0x269B, 0x269C, 0x26A0,
            0x26A1, 0x26AA, 0x26AB, 0x26B0, 0x26B1, 0x26BD, 0x26BE, 0x26C4, 0x26C5,
            0x26CE, 0x26D4, 0x26EA, 0x26F2, 0x26F3, 0x26F5, 0x26FA, 0x26FD, 0x2705,
            0x270A, 0x270B, 0x2728, 0x274C, 0x274E, 0x2753, 0x2754, 0x2755, 0x2757,
            0x2795, 0x2796, 0x2797, 0x27B0, 0x27BF, 0x2B50, 0x2B55, 0x3297, 0x3299,
            0x1F004, 0x1F0CF
        };
        for (int cp : singlePoints) {
            EMOJI_BITSET.set(cp);
        }

        // Keycap Combiners & Variation Selectors
        EMOJI_BITSET.set(0x20E3); // Combining Enclosing Keycap
        EMOJI_BITSET.set(0xFE0F); // Emoji Variation Selector-16
    }

    private static void addRange(int start, int end) {
        EMOJI_BITSET.set(start, end + 1);
    }

    private EmojiProcessor() {
        // Prevent instantiation
    }

    public static boolean isEmoji(int codePoint) {
        if (codePoint < 0 || codePoint >= EMOJI_BITSET.size()) {
            return false;
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

    /**
     * Optimized single-pass emoji removal using BreakIterator graphemes.
     */
    public static String removeEmojis(String text) {
        if (text == null || text.isEmpty()) return text;

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
}