package kmu.util;

import kmlib.starsector.strings.StarsectorStrings;

/**
 * KMU's localisation entry point. Holds the KMU settings category
 * and the string ids registered in {@code data/strings/strings.json},
 * and exposes thin {@link #get(String)} / {@link #format(String, Object...)}
 * accessors that bind the KMU category so call sites do not have to
 * repeat it.
 *
 * <p>The lookup and fallback behaviour live in KMLib's
 * {@link StarsectorStrings}; this class is a category-bound shortcut,
 * not a parallel implementation.
 */
public final class KmuStrings {
    public static final String CATEGORY = "kmu";

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
    public static final String POLITICAL_MAP_TAB_NO_LAYER = "political_map_tab_no_layer";
    public static final String POLITICAL_MAP_TAB_POLITICAL_MAP = "political_map_tab_political_map";
    public static final String POLITICAL_MAP_CTL_UNINHABITED = "political_map_ctl_uninhabited";
    public static final String POLITICAL_MAP_CTL_NAME_SHORT = "political_map_ctl_name_short";
    public static final String POLITICAL_MAP_CTL_NAME_FULL = "political_map_ctl_name_full";
    public static final String POLITICAL_MAP_CTL_FACTION_NAMES = "political_map_ctl_faction_names";
    public static final String POLITICAL_MAP_CTL_FACTIONS = "political_map_ctl_factions";
    public static final String POLITICAL_MAP_CTL_ALLIANCES = "political_map_ctl_alliances";
    public static final String POLITICAL_MAP_CTL_NON_ALLIED_CAPTION = "political_map_ctl_non_allied_caption";
    public static final String POLITICAL_MAP_CTL_MUTED = "political_map_ctl_muted";
    public static final String POLITICAL_MAP_CTL_DESATURATED = "political_map_ctl_desaturated";
    public static final String POLITICAL_MAP_CTL_FILTER_RECEDE_CAPTION = "political_map_ctl_filter_recede_caption";

    private KmuStrings() {
    }

    /** Looks up {@code key} under the KMU category. See
     *  {@link StarsectorStrings#get(String, String)} for fallback
     *  semantics. */
    public static String get(String key) {
        return StarsectorStrings.get(CATEGORY, key);
    }

    /** Formats {@code key}'s template against {@code args}. See
     *  {@link StarsectorStrings#format(String, String, Object...)} for
     *  fallback semantics. */
    public static String format(String key, Object... args) {
        return StarsectorStrings.format(CATEGORY, key, args);
    }
}
