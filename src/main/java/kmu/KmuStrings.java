package kmu;

import com.fs.starfarer.api.Global;

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Objects;

public final class KmuStrings {
    public static final String CATEGORY = "kmu";

    public static final String CONDITION_PICKER_TITLE = "condition_picker_title";
    public static final String CONDITION_PICKER_SUMMARY = "condition_picker_summary";
    public static final String CONDITION_PICKER_EMPTY = "condition_picker_empty";
    public static final String UNKNOWN_MARKET = "unknown_market";
    public static final String UNKNOWN_STAR_SYSTEM = "unknown_star_system";
    public static final String UNKNOWN_CONSTELLATION = "unknown_constellation";
    public static final String DIALOG_CLOSE = "dialog_close";

    private KmuStrings() {
    }

    public static String get(String key, String fallback) {
        return get(key, fallback, KmuStrings::fromSettings);
    }

    static String get(String key, String fallback, KmuStringSource source) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(source, "source");

        try {
            String value = source.get(key);
            if (value == null || value.trim().isEmpty()) {
                return fallback;
            }
            return value;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static String format(String key, String fallback, Object... args) {
        return format(key, fallback, KmuStrings::fromSettings, args);
    }

    static String format(String key, String fallback, KmuStringSource source, Object... args) {
        String template = get(key, fallback, source);
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (IllegalFormatException exception) {
            try {
                return String.format(Locale.ROOT, fallback, args);
            } catch (IllegalFormatException fallbackException) {
                return fallback;
            }
        }
    }

    private static String fromSettings(String key) {
        return Global.getSettings().getString(CATEGORY, key);
    }

    @FunctionalInterface
    interface KmuStringSource {
        String get(String key);
    }
}
