package vn.eventplatform.event.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NormalizationUtils {

    private static final Pattern ZERO_WIDTH_PATTERN = Pattern.compile("[\\u200B-\\u200D\\uFEFF\\u00AD]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC_SLUG_PATTERN = Pattern.compile("[^a-z0-9\\s-]");
    private static final Pattern MULTIPLE_HYPHENS_PATTERN = Pattern.compile("-+");

    private NormalizationUtils() {
    }

    public static String cleanDisplayText(String input) {
        if (input == null) {
            return "";
        }
        String stripped = ZERO_WIDTH_PATTERN.matcher(input).replaceAll("");
        String nfc = Normalizer.normalize(stripped, Normalizer.Form.NFC);
        return WHITESPACE_PATTERN.matcher(nfc.trim()).replaceAll(" ");
    }

    public static String normalizeForComparison(String input) {
        if (input == null) {
            return "";
        }
        String stripped = ZERO_WIDTH_PATTERN.matcher(input).replaceAll("");
        String nfkc = Normalizer.normalize(stripped, Normalizer.Form.NFKC);
        return WHITESPACE_PATTERN.matcher(nfkc.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    public static String toSlug(String input) {
        String cleaned = cleanDisplayText(input);
        if (cleaned.isEmpty()) {
            return "item";
        }
        String replacedD = cleaned.replace('đ', 'd').replace('Đ', 'd');
        String nfd = Normalizer.normalize(replacedD, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS_PATTERN.matcher(nfd).replaceAll("");
        String lower = withoutDiacritics.toLowerCase(Locale.ROOT);
        String alphanumeric = NON_ALPHANUMERIC_SLUG_PATTERN.matcher(lower).replaceAll("");
        String hyphenated = WHITESPACE_PATTERN.matcher(alphanumeric.trim()).replaceAll("-");
        String singleHyphens = MULTIPLE_HYPHENS_PATTERN.matcher(hyphenated).replaceAll("-");
        String result = singleHyphens.replaceAll("^-|-$", "");
        return result.isEmpty() ? "item" : result;
    }
}
