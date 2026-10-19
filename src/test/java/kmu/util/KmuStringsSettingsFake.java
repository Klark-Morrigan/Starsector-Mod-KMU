package kmu.util;

import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake.SettingsColourSource;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake.SettingsStringSource;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake.UiElementSource;
import kmlib.testfixtures.starsector.strings.KmlibStringsSettingsFake;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KMU-side facade over KMLib's {@link StarsectorSettingsFake} that bakes in KMU's localised string
 * map, beside the key classes it answers. KMLib owns the actual {@code SettingsAPI} proxy (single
 * source of truth) and the fetch-or-redact behaviour via
 * {@link kmlib.starsector.strings.StarsectorStrings}; this class supplies the
 * {@link SettingsStringSource} so call sites need not learn about KMLib.
 */
public final class KmuStringsSettingsFake {

    private KmuStringsSettingsFake() {
    }

    public static void installSettings() {
        StarsectorSettingsFake.installSettings(KMU_STRINGS);
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
        StarsectorSettingsFake.buildSettings()
            .answerStrings(KMU_STRINGS)
            .answerColours(colourSource)
            .installSettings();
    }

    /**
     * Installs the proxy with KMU's strings and panels that build one known element, for a subject
     * that makes its own tooltip surface. The surface never reaches the caller, so that element is
     * where an assertion about the attachment is made.
     *
     * @param uiElementSource the element every panel this settings makes hands back
     */
    public static void installSettingsWithUiElements(UiElementSource uiElementSource) {
        StarsectorSettingsFake.buildSettings()
            .answerStrings(KMU_STRINGS)
            .answerUiElements(uiElementSource)
            .installSettings();
    }

    public static void clearSettings() {
        StarsectorSettingsFake.clearSettings();
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
     * Resolves the (category, key) pair against KMU's localisation map, and KMLib's category through KMLib's own
     * fake: KMU's console commands build on KMLib's, which print KMLib's strings. Returns {@code null} for any
     * other category so the proxy stays well-behaved when other code paths probe it.
     */
    private static final SettingsStringSource KMU_STRINGS = (category, key) -> {
        if (!KmuStringKeys.CATEGORY.equals(category)) {
            return KmlibStringsSettingsFake.KMLIB_STRINGS.get(category, key);
        }
        return STRINGS_BY_KEY.get(key);
    };

    private static Map<String, String> buildStringsByKey() {
        var stringsByKey = new LinkedHashMap<String, String>();
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_LOCATION, "Location:");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_LOCATION_UNKNOWN, "Unknown");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_LOCATION_OWNED_BY, "owned by %s");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_LOCATION_SYSTEM_CENTER, "system center");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_SUMMARY, "Conditions:");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_SUMMARY_VISIBLE, "%d visible");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_SUMMARY_SUPPRESSED, "%d suppressed");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_SUMMARY_PRESENT, "%d present");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_SUMMARY_HIDDEN, "%d hidden");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_SUMMARY_AVAILABLE, "%d available");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_SUMMARY_TOTAL, "%d total.");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_GROUP_SEPARATOR, " - ");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_ITEM_SEPARATOR, ", ");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_EMPTY, "No market condition specs are available.");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_SUPPRESSED_TITLE, "Suppressed");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_SUPPRESSED_BODY, "This condition is present on the market, but it's suppressed and has no effect.");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_HIDDEN_TITLE, "Hidden");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_HIDDEN_BODY, "This condition is present on the market, but it's hidden and still applies its effects.");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_TITLE, "Metadata");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_LINE, "%s: %s");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_SOURCE, "source");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_ID, "id");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_ICON, "icon");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_HIDDEN, "hidden");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_SUPPRESSED, "suppressed");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_TRUE, "true");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_FALSE, "false");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_ENTRY_PRESENT, "Present");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_ENTRY_ABSENT, "Absent");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK, "%s: %s");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_ADDED, "Added condition");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_ALREADY_PRESENT, "Already present");
        stringsByKey.put(
            KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_CONDITION_NOT_FOUND,
            "Condition not found");
        stringsByKey.put(
            KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_NOT_OFFERABLE,
            "Not a placeable condition");
        stringsByKey.put(KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_FAILED, "Failed to add condition");
        stringsByKey.put(
            KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_UNKNOWN_CONDITION,
            "unknown condition");
        stringsByKey.put(
            KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_OPENED,
            "Opened Market Condition Manager.");
        stringsByKey.put(
            KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_NO_MARKET_CONTEXT,
            "No active context supports market condition editing.");
        stringsByKey.put(
            KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_UNSUPPORTED_TARGET,
            "Current market does not support market condition editing.");
        stringsByKey.put(
            KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_SWITCHED_OFF,
            "The Market Condition Manager is unfinished and switched off. Switch it on under LunaLib's mod "
                + "settings, on KMU's Features tab, to try it anyway.");
        stringsByKey.put(
            KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_FAILED_TO_OPEN,
            "Failed to open Market Condition Manager");
        stringsByKey.put(
            KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_FAILED_TO_RESOLVE_CONTEXT,
            "Failed to resolve current market context");
        stringsByKey.put(
            KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_FAILED_TO_VALIDATE_TARGET,
            "Failed to validate MCM target");
        stringsByKey.put(KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_FAILURE, "%s.");
        stringsByKey.put(KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_FAILURE_WITH_CAUSE, "%s: %s");
        stringsByKey.put(KmuMarketConditionStringKeys.DIALOG_CLOSE, "Close");
        stringsByKey.put(KmuMapStringKeys.MAP_LAYER_ARRANGE_TITLE, "Arrange Map Layers");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_ARRANGE_HINT,
            "Move a layer with Up and Down, or uncheck its box to take its tab off the bar.");
        stringsByKey.put(KmuMapStringKeys.MAP_LAYER_ARRANGE_HINT_UNCHECK, "uncheck");
        stringsByKey.put(KmuMapStringKeys.MAP_LAYER_ARRANGE_MOVE_UP, "Up");
        stringsByKey.put(KmuMapStringKeys.MAP_LAYER_ARRANGE_MOVE_DOWN, "Down");
        stringsByKey.put(KmuMapStringKeys.MAP_LAYER_ARRANGE_APPLY, "Apply");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE,
            "Shows %s that draw a %s (%s) over the sector map.");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL,
            "This feature is provided by the %s mod.");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_LAYERS,
            "Sector Map Layers");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SUPPLIER,
            "KMU");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SETTINGS_MOD,
            "LunaLib");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_DISABLE,
            "disable this feature");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_SAVE,
            "save your game");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING,
            "%s save game loading will produce an error if %s is disabled without %s %s from a "
                + "save beforehand. To do so, %s in %s mod settings and %s.");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING_LABEL,
            "Warning:");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING_ACTION,
            "uninstalling");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_POLITICAL_MAP,
            "Political Map");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_FACTIONS,
            "Factions");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_ALLIANCES,
            "Alliances");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_CLAIMS,
            "Claims");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_CLAIMS_QUALIFIED,
            "system %s");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_SEPARATOR,
            ", ");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FOOTER_COLLAPSE_FACTIONS,
            "collapse to factions");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FOOTER_EXPAND_SYSTEM_COMPOSITION,
            "expand system composition");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FOOTER_EXPAND_MARKET_STATS,
            "expand market stats");
        stringsByKey.put(
            KmuMapStringKeys.MAP_LAYER_TOOLTIP_FOOTER_EXPAND_PATROL_DETAILS,
            "expand patrol details");
        stringsByKey.put(KmuMapStringKeys.MAP_LAYER_TOOLTIP_FOOTER_WITHHELD, "%d not shown");
        stringsByKey.put(KmuMapStringKeys.MAP_LAYER_TOOLTIP_WITHHELD_ENTRIES, "+ %d more");
        stringsByKey.put(KmuMapStringKeys.OBSERVATION_SPAN_TODAY, "today");
        stringsByKey.put(KmuMapStringKeys.OBSERVATION_SPAN_A_DAY_AGO, "a day ago");
        stringsByKey.put(KmuMapStringKeys.OBSERVATION_SPAN_DAYS_AGO, "%d days ago");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_LAST_SEEN, "last seen %s (%s)");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_DECIVILISED, "Decivilised");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_UNPOPULATED, "Unpopulated");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_CORE_TERRITORY, "core territory");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_CORE_MARKER, "(core)");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_NONE, "None");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_HOLDER, "claim holder");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_LISTING_POSITION, "[%s]");
        stringsByKey.put(
            KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_SIBLING_BONUS,
            "Same-faction market bonus");
        stringsByKey.put(
            KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_SIBLING_WORKING,
            "(%s markets) - 1 =");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_QUALIFIER_SEPARATOR, ", ");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_QUALIFIER_ABANDONED, "abandoned");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_QUALIFIER_DECIVILISED, "decivilised");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_QUALIFIER_UNDISCOVERED, "undiscovered");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_QUALIFIER_HIDDEN, "hidden");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_QUALIFIER_UNLISTED, "unlisted");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_QUALIFIER_NON_TERRITORIAL, "non-territorial");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_QUALIFIER_FRACTION, "(%d/%d)");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_MILITARY, "Military");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_BONUS, "+%s");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_CLAIM, "Claim:");
        stringsByKey.put(
            KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_ALLIED_WITH_HOLDER,
            "Allied with the claim holder:");
        stringsByKey.put(
            KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_FRIENDLY_WITH_CLAIM_HOLDER,
            "Friendly with the claim holder:");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_CONTESTED, "Contested by:");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_PRESENT, "Present:");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_NON_TERRITORIAL, "Non-territorial:");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_DOMINATED, "Dominated by:");
        stringsByKey.put(
            KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_ALLIED_WITH_SYSTEM_HOLDER,
            "Allied with the system holder:");
        stringsByKey.put(
            KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_FRIENDLY_WITH_SYSTEM_HOLDER,
            "Friendly with the system holder:");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_NON_POLITICAL, "Non-political:");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_STABILITY, "Stability");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_SIZE, "Size");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_PATROLS, "Patrols");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_FIXED, "%s (fixed)");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_STATION_MILITARY, "%s (Military)");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_RATED, "%s ::");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_COUNTED, "%s /");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_JOINED, "%s %s");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_PENALTY, "%s (-%d%%)");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_PATROL_TIER, "%s: %d");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_PATROL_SMALL, "Small");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_PATROL_MEDIUM, "Medium");
        stringsByKey.put(KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_PATROL_LARGE, "Large");
        return stringsByKey;
    }
}
