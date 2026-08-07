package kmu.starsector;

import kmlib.starsector.testing.StarsectorSettingsFake.SettingsColourSource;
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

    /**
     * Installs the proxy with KMU's strings and a caller-named palette, for a
     * subject that reads a named engine colour rather than a {@code Misc}
     * shade. Every unnamed key still answers with the default, so a caller
     * names only the keys its assertions turn on.
     *
     * @param colourSource the shades the named engine colour keys answer with
     */
    public static void installSettings(SettingsColourSource colourSource) {
        kmlib.starsector.testing.StarsectorSettingsFake.installSettings(KMU_STRINGS, colourSource);
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
        stringsByKey.put(KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_SHOW, "show %s");
        stringsByKey.put(KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_HIDE, "hide %s");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_DETAIL_CONTRIBUTIONS, "score contributions");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_DECIVILISED, "Decivilised");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_UNPOPULATED, "Unpopulated");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CORE_TERRITORY, "core territory");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CORE_MARKER, "(core)");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_NONE, "None");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_STRONGEST, "strongest");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_LISTING_POSITION, "[%s]");
        stringsByKey.put(
            KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_OTHER_MARKETS,
            "Other same-faction markets");
        stringsByKey.put(
            KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_OTHER_MARKETS_TOTAL,
            "(%s markets) - 1 = +%s");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_MILITARY, "Military");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_BONUS, "+%s");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CLAIM, "Claim:");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CONTESTED, "Contested by:");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_NON_TERRITORIAL, "Non-territorial:");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_DOMINATED, "Dominated by:");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_STABILITY, "Stability");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_SIZE, "Size");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_PATROLS, "Patrols");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_HIDDEN, "hidden");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_FIXED, "%s (fixed)");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_RATED, "%s ::");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_COUNTED, "%s /");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_JOINED, "%s %s");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_PENALTY, "%s (-%d%%)");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_PATROL_TIER, "%s: %d");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_PATROL_SMALL, "Small");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_PATROL_MEDIUM, "Medium");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_PATROL_LARGE, "Large");
        return stringsByKey;
    }
}
