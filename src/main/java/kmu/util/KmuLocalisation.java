package kmu.util;

import com.fs.starfarer.api.Global;

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Objects;

import static kmu.util.KmuValues.hasText;

public final class KmuLocalisation {
    public static final String CATEGORY = "kmu";
    // Missing or invalid localized UI strings should be visible during playtesting.
    public static final String REDACTED = "[REDACTED]";

    public static final String CONDITION_MANAGER_LOCATION = "condition_manager_location";
    public static final String CONDITION_MANAGER_LOCATION_UNKNOWN = "condition_manager_location_unknown";
    public static final String CONDITION_MANAGER_SUMMARY = "condition_manager_summary";
    public static final String CONDITION_MANAGER_SUMMARY_VISIBLE = "condition_manager_summary_visible";
    public static final String CONDITION_MANAGER_SUMMARY_SUPPRESSED = "condition_manager_summary_suppressed";
    public static final String CONDITION_MANAGER_SUMMARY_PRESENT = "condition_manager_summary_present";
    public static final String CONDITION_MANAGER_SUMMARY_HIDDEN = "condition_manager_summary_hidden";
    public static final String CONDITION_MANAGER_SUMMARY_AVAILABLE = "condition_manager_summary_available";
    public static final String CONDITION_MANAGER_SUMMARY_TOTAL = "condition_manager_summary_total";
    public static final String CONDITION_MANAGER_EMPTY = "condition_manager_empty";
    public static final String CONDITION_MANAGER_TOOLTIP_SUPPRESSED_TITLE = "condition_manager_tooltip_suppressed_title";
    public static final String CONDITION_MANAGER_TOOLTIP_SUPPRESSED_BODY = "condition_manager_tooltip_suppressed_body";
    public static final String CONDITION_MANAGER_TOOLTIP_HIDDEN_TITLE = "condition_manager_tooltip_hidden_title";
    public static final String CONDITION_MANAGER_TOOLTIP_HIDDEN_BODY = "condition_manager_tooltip_hidden_body";
    public static final String DIALOG_CLOSE = "dialog_close";

    private KmuLocalisation() {
    }

    public static String get(String key) {
        return get(key, KmuLocalisation::fromSettings);
    }

    static String get(String key, KmuStringSource source) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");

        try {
            String value = source.get(key);
            if (!hasText(value)) {
                return REDACTED;
            }
            return value;
        } catch (RuntimeException exception) {
            return REDACTED;
        }
    }

    public static String format(String key, Object... args) {
        return format(key, KmuLocalisation::fromSettings, args);
    }

    static String format(String key, KmuStringSource source, Object... args) {
        String template = get(key, source);
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (IllegalFormatException exception) {
            return REDACTED;
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
