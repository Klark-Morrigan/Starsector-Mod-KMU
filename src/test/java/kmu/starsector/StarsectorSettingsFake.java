package kmu.starsector;

import kmlib.starsector.testing.StarsectorSettingsFake.SettingsStringSource;
import kmu.util.KmuStrings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KMU-side facade over KMLib's
 * {@link kmlib.starsector.testing.StarsectorSettingsFake} that bakes in
 * KMU's localised string map. KMLib owns the actual {@code SettingsAPI}
 * proxy (single source of truth) and the fetch-or-redact behaviour via
 * {@link kmlib.starsector.strings.StarsectorStrings}; this class
 * supplies the {@link SettingsStringSource} so existing call sites
 * keep working without learning about KMLib.
 */
public final class StarsectorSettingsFake {
    private StarsectorSettingsFake() {
    }

    public static void installSettings() {
        kmlib.starsector.testing.StarsectorSettingsFake.installSettings(KMU_STRINGS);
    }

    public static void clearSettings() {
        kmlib.starsector.testing.StarsectorSettingsFake.clearSettings();
    }

    private static final Map<String, String> STRINGS_BY_KEY = buildStringsByKey();

    /**
     * Resolves the (category, key) pair against KMU's localisation map.
     * Returns {@code null} for any category other than KMU's so the
     * proxy stays well-behaved when other code paths probe it.
     */
    private static final SettingsStringSource KMU_STRINGS = (category, key) -> {
        if (!KmuStrings.CATEGORY.equals(category)) {
            return null;
        }
        return STRINGS_BY_KEY.get(key);
    };

    private static Map<String, String> buildStringsByKey() {
        var stringsByKey = new LinkedHashMap<String, String>();
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_LOCATION, "Location:");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_LOCATION_UNKNOWN, "Unknown");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_SUMMARY, "Conditions:");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_SUMMARY_VISIBLE, "%d visible");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_SUMMARY_SUPPRESSED, "%d suppressed");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_SUMMARY_PRESENT, "%d present");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_SUMMARY_HIDDEN, "%d hidden");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_SUMMARY_AVAILABLE, "%d available");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_SUMMARY_TOTAL, "%d total.");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_EMPTY, "No market condition specs are available.");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_TOOLTIP_SUPPRESSED_TITLE, "Suppressed");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_TOOLTIP_SUPPRESSED_BODY, "This condition is present on the market, but it's suppressed and has no effect.");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_TOOLTIP_HIDDEN_TITLE, "Hidden");
        stringsByKey.put(KmuStrings.CONDITION_MANAGER_TOOLTIP_HIDDEN_BODY, "This condition is present on the market, but it's hidden and still applies its effects.");
        stringsByKey.put(KmuStrings.DIALOG_CLOSE, "Close");
        return stringsByKey;
    }
}
