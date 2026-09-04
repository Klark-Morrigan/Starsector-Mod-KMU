package kmu.starsector;

import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake.SettingsColourSource;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake.SettingsStringSource;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake.UiElementSource;

import kmu.util.KmuStrings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KMU-side facade over KMLib's
 * {@link kmlib.testfixtures.starsector.settings.StarsectorSettingsFake} that bakes in
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
        kmlib.testfixtures.starsector.settings.StarsectorSettingsFake.installSettings(KMU_STRINGS);
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
        kmlib.testfixtures.starsector.settings.StarsectorSettingsFake.installSettings(KMU_STRINGS, colourSource);
    }

    /**
     * Installs the proxy with KMU's strings and panels that build one known element, for a subject
     * that makes its own tooltip surface. The surface never reaches the caller, so that element is
     * where an assertion about the attachment is made.
     *
     * @param uiElementSource the element every panel this settings makes hands back
     */
    public static void installSettingsWithUiElements(UiElementSource uiElementSource) {
        kmlib.testfixtures.starsector.settings.StarsectorSettingsFake.installSettings(KMU_STRINGS, uiElementSource);
    }

    public static void clearSettings() {
        kmlib.testfixtures.starsector.settings.StarsectorSettingsFake.clearSettings();
    }

    /**
     * The wording this fake answers with, for the walk that holds it against the shipped strings
     * file. Restating that wording here rather than reading the file is what keeps a unit test off
     * the disk, and it is also what lets the two drift apart - so the map is readable, and one walk
     * checks it says what the game says.
     *
     * @return each stubbed key's wording
     */
    public static Map<String, String> readStringsByKey() {
        return Map.copyOf(STRINGS_BY_KEY);
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
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE,
            "Shows %s that draw a %s (%s) over the sector map.");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL,
            "This feature is provided by the %s mod. To %s %s from your save game, %s in %s mod "
                + "settings and %s.");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_LAYERS,
            "Sector Map Layers");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SUPPLIER,
            "KMU");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SETTINGS_MOD,
            "LunaLib");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_DISABLE,
            "disable this feature");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_SAVE,
            "save your game");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING,
            "%s save game loading will produce an error if %s is disabled without %s %s from a "
                + "save beforehand.");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING_LABEL,
            "Warning:");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING_ACTION,
            "uninstalling");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_POLITICAL_MAP,
            "Political Map");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_FACTIONS,
            "Factions");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_ALLIANCES,
            "Alliances");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_CLAIMS,
            "Claims");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_CLAIMS_QUALIFIED,
            "system %s");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_SEPARATOR,
            ", ");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_COLLAPSE_FACTIONS,
            "collapse to factions");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_EXPAND_SYSTEM_COMPOSITION,
            "expand system composition");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_EXPAND_MARKET_STATS,
            "expand market stats");
        stringsByKey.put(
            KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_EXPAND_PATROL_DETAILS,
            "expand patrol details");
        stringsByKey.put(KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_WITHHELD, "%d not shown");
        stringsByKey.put(KmuStrings.MAP_LAYER_TOOLTIP_WITHHELD_ENTRIES, "+ %d more");
        stringsByKey.put(KmuStrings.OBSERVATION_SPAN_TODAY, "today");
        stringsByKey.put(KmuStrings.OBSERVATION_SPAN_A_DAY_AGO, "a day ago");
        stringsByKey.put(KmuStrings.OBSERVATION_SPAN_DAYS_AGO, "%d days ago");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_LAST_SEEN, "last seen %s (%s)");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_DECIVILISED, "Decivilised");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_UNPOPULATED, "Unpopulated");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CORE_TERRITORY, "core territory");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CORE_MARKER, "(core)");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_NONE, "None");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_HOLDER, "claim holder");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_LISTING_POSITION, "[%s]");
        stringsByKey.put(
            KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_SIBLING_BONUS,
            "Same-faction market bonus");
        stringsByKey.put(
            KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_SIBLING_WORKING,
            "(%s markets) - 1 =");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_SEPARATOR, ", ");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_ABANDONED, "abandoned");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_DECIVILISED, "decivilised");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_UNDISCOVERED, "undiscovered");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_HIDDEN, "hidden");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_UNLISTED, "unlisted");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_NON_TERRITORIAL, "non-territorial");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_FRACTION, "(%d/%d)");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_MILITARY, "Military");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_BONUS, "+%s");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CLAIM, "Claim:");
        stringsByKey.put(
            KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_ALLIED_WITH_HOLDER,
            "Allied with the claim holder:");
        stringsByKey.put(
            KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_FRIENDLY_WITH_CLAIM_HOLDER,
            "Friendly with the claim holder:");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CONTESTED, "Contested by:");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_NON_TERRITORIAL, "Non-territorial:");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_DOMINATED, "Dominated by:");
        stringsByKey.put(
            KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_ALLIED_WITH_SYSTEM_HOLDER,
            "Allied with the system holder:");
        stringsByKey.put(
            KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_FRIENDLY_WITH_SYSTEM_HOLDER,
            "Friendly with the system holder:");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_NON_POLITICAL, "Non-political:");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_STABILITY, "Stability");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_SIZE, "Size");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_PATROLS, "Patrols");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_FIXED, "%s (fixed)");
        stringsByKey.put(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_STATION_MILITARY, "%s (Military)");
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
