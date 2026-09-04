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
    public static final String MAP_LAYER_TAB_NO_LAYER = "map_layer_tab_no_layer";
    public static final String MAP_LAYER_CTL_COLUMNS_CAPTION = "map_layer_ctl_columns_caption";
    public static final String MAP_LAYER_CTL_FILTER_ROW_TOGGLE = "map_layer_ctl_filter_row_toggle";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE =
        "map_layer_tooltip_filter_row_toggle";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_LAYERS =
        "map_layer_tooltip_filter_row_toggle_layers";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SUPPLIER =
        "map_layer_tooltip_filter_row_toggle_supplier";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL =
        "map_layer_tooltip_filter_row_toggle_uninstall";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_ACTION =
        "map_layer_tooltip_filter_row_toggle_uninstall_action";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING =
        "map_layer_tooltip_filter_row_toggle_uninstall_warning";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_DISABLE =
        "map_layer_tooltip_filter_row_toggle_uninstall_disable";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_SAVE =
        "map_layer_tooltip_filter_row_toggle_uninstall_save";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SETTINGS_MOD =
        "map_layer_tooltip_filter_row_toggle_settings_mod";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_CLAIMS_QUALIFIED =
        "map_layer_tooltip_filter_row_toggle_view_claims_qualified";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_POLITICAL_MAP =
        "map_layer_tooltip_filter_row_toggle_view_political_map";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_FACTIONS =
        "map_layer_tooltip_filter_row_toggle_view_factions";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_ALLIANCES =
        "map_layer_tooltip_filter_row_toggle_view_alliances";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_CLAIMS =
        "map_layer_tooltip_filter_row_toggle_view_claims";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_SEPARATOR =
        "map_layer_tooltip_filter_row_toggle_view_separator";
    public static final String MAP_LAYER_TOOLTIP_FOOTER_COLLAPSE_FACTIONS =
        "map_layer_tooltip_footer_collapse_factions";
    public static final String MAP_LAYER_TOOLTIP_FOOTER_EXPAND_SYSTEM_COMPOSITION =
        "map_layer_tooltip_footer_expand_system_composition";
    public static final String MAP_LAYER_TOOLTIP_FOOTER_EXPAND_MARKET_STATS =
        "map_layer_tooltip_footer_expand_market_stats";
    public static final String MAP_LAYER_TOOLTIP_FOOTER_EXPAND_PATROL_DETAILS =
        "map_layer_tooltip_footer_expand_patrol_details";
    public static final String MAP_LAYER_TOOLTIP_FOOTER_WITHHELD =
        "map_layer_tooltip_footer_withheld";
    public static final String MAP_LAYER_TOOLTIP_WITHHELD_ENTRIES =
        "map_layer_tooltip_withheld_entries";
    public static final String OBSERVATION_SPAN_TODAY = "observation_span_today";
    public static final String OBSERVATION_SPAN_A_DAY_AGO = "observation_span_a_day_ago";
    public static final String OBSERVATION_SPAN_DAYS_AGO = "observation_span_days_ago";
    public static final String POLITICAL_MAP_TAB_POLITICAL_MAP = "political_map_tab_political_map";
    public static final String POLITICAL_MAP_CTL_UNINHABITED = "political_map_ctl_uninhabited";
    public static final String POLITICAL_MAP_CTL_NAME_FULL = "political_map_ctl_name_full";
    public static final String POLITICAL_MAP_CTL_NAME_SHORT = "political_map_ctl_name_short";
    public static final String POLITICAL_MAP_CTL_NAME_NONE = "political_map_ctl_name_none";
    public static final String POLITICAL_MAP_CTL_FACTION_NAMES = "political_map_ctl_faction_names";
    public static final String POLITICAL_MAP_CTL_FACTIONS = "political_map_ctl_factions";
    public static final String POLITICAL_MAP_CTL_ALLIANCES = "political_map_ctl_alliances";
    public static final String POLITICAL_MAP_CTL_CLAIMS = "political_map_ctl_claims";
    public static final String POLITICAL_MAP_CTL_NON_ALLIED_CAPTION = "political_map_ctl_non_allied_caption";
    public static final String POLITICAL_MAP_CTL_MUTED = "political_map_ctl_muted";
    public static final String POLITICAL_MAP_CTL_DESATURATED = "political_map_ctl_desaturated";
    public static final String POLITICAL_MAP_CTL_FILTER_RECEDE_CAPTION = "political_map_ctl_filter_recede_caption";
    public static final String POLITICAL_MAP_CTL_SORT_NAME = "political_map_ctl_sort_name";
    public static final String POLITICAL_MAP_CTL_SORT_DOMINATION = "political_map_ctl_sort_domination";
    public static final String POLITICAL_MAP_CTL_SORT_PRESENCE = "political_map_ctl_sort_presence";
    public static final String POLITICAL_MAP_CTL_SORT_SCORE = "political_map_ctl_sort_score";
    public static final String POLITICAL_MAP_CTL_SORT_MARKET_SIZE = "political_map_ctl_sort_market_size";
    public static final String POLITICAL_MAP_CTL_SORT_CLAIMS = "political_map_ctl_sort_claims";
    public static final String POLITICAL_MAP_CTL_SORT_ATTITUDE = "political_map_ctl_sort_attitude";
    public static final String POLITICAL_MAP_CTL_SORT_ATTITUDE_RANGE_SEPARATOR =
        "political_map_ctl_sort_attitude_range_separator";
    public static final String POLITICAL_MAP_TOOLTIP_DECIVILISED = "political_map_tooltip_decivilised";
    public static final String POLITICAL_MAP_TOOLTIP_UNPOPULATED = "political_map_tooltip_unpopulated";
    public static final String POLITICAL_MAP_TOOLTIP_CORE_TERRITORY = "political_map_tooltip_core_territory";
    public static final String POLITICAL_MAP_TOOLTIP_CORE_MARKER = "political_map_tooltip_core_marker";
    public static final String POLITICAL_MAP_TOOLTIP_CLAIM_NONE = "political_map_tooltip_claim_none";
    public static final String POLITICAL_MAP_TOOLTIP_CLAIM_HOLDER = "political_map_tooltip_claim_holder";
    public static final String POLITICAL_MAP_TOOLTIP_CLAIM_LISTING_POSITION =
        "political_map_tooltip_claim_listing_position";
    public static final String POLITICAL_MAP_TOOLTIP_CLAIM_SIBLING_BONUS =
        "political_map_tooltip_claim_sibling_bonus";
    public static final String POLITICAL_MAP_TOOLTIP_CLAIM_SIBLING_WORKING =
        "political_map_tooltip_claim_sibling_working";
    public static final String POLITICAL_MAP_TOOLTIP_CLAIM_MILITARY = "political_map_tooltip_claim_military";
    public static final String POLITICAL_MAP_TOOLTIP_CLAIM_BONUS = "political_map_tooltip_claim_bonus";
    public static final String POLITICAL_MAP_TOOLTIP_QUALIFIER_SEPARATOR =
        "political_map_tooltip_qualifier_separator";
    public static final String POLITICAL_MAP_TOOLTIP_QUALIFIER_ABANDONED =
        "political_map_tooltip_qualifier_abandoned";
    public static final String POLITICAL_MAP_TOOLTIP_QUALIFIER_DECIVILISED =
        "political_map_tooltip_qualifier_decivilised";
    public static final String POLITICAL_MAP_TOOLTIP_QUALIFIER_UNDISCOVERED =
        "political_map_tooltip_qualifier_undiscovered";
    public static final String POLITICAL_MAP_TOOLTIP_QUALIFIER_HIDDEN =
        "political_map_tooltip_qualifier_hidden";
    public static final String POLITICAL_MAP_TOOLTIP_QUALIFIER_UNLISTED =
        "political_map_tooltip_qualifier_unlisted";
    public static final String POLITICAL_MAP_TOOLTIP_QUALIFIER_NON_TERRITORIAL =
        "political_map_tooltip_qualifier_non_territorial";
    public static final String POLITICAL_MAP_TOOLTIP_QUALIFIER_FRACTION =
        "political_map_tooltip_qualifier_fraction";
    public static final String POLITICAL_MAP_TOOLTIP_LAST_SEEN = "political_map_tooltip_last_seen";
    public static final String POLITICAL_MAP_TOOLTIP_SECTION_CLAIM = "political_map_tooltip_section_claim";
    public static final String POLITICAL_MAP_TOOLTIP_SECTION_ALLIED_WITH_HOLDER =
        "political_map_tooltip_section_allied_with_holder";
    public static final String POLITICAL_MAP_TOOLTIP_SECTION_FRIENDLY_WITH_CLAIM_HOLDER =
        "political_map_tooltip_section_friendly_with_claim_holder";
    public static final String POLITICAL_MAP_TOOLTIP_SECTION_CONTESTED = "political_map_tooltip_section_contested";
    public static final String POLITICAL_MAP_TOOLTIP_SECTION_NON_TERRITORIAL =
        "political_map_tooltip_section_non_territorial";
    public static final String POLITICAL_MAP_TOOLTIP_SECTION_DOMINATED = "political_map_tooltip_section_dominated";
    public static final String POLITICAL_MAP_TOOLTIP_SECTION_ALLIED_WITH_SYSTEM_HOLDER =
        "political_map_tooltip_section_allied_with_system_holder";
    public static final String POLITICAL_MAP_TOOLTIP_SECTION_FRIENDLY_WITH_SYSTEM_HOLDER =
        "political_map_tooltip_section_friendly_with_system_holder";
    public static final String POLITICAL_MAP_TOOLTIP_SECTION_NON_POLITICAL =
        "political_map_tooltip_section_non_political";
    public static final String POLITICAL_MAP_TOOLTIP_FACTOR_STABILITY = "political_map_tooltip_factor_stability";
    public static final String POLITICAL_MAP_TOOLTIP_FACTOR_SIZE = "political_map_tooltip_factor_size";
    public static final String POLITICAL_MAP_TOOLTIP_FACTOR_PATROLS = "political_map_tooltip_factor_patrols";
    public static final String POLITICAL_MAP_TOOLTIP_FACTOR_FIXED = "political_map_tooltip_factor_fixed";
    public static final String POLITICAL_MAP_TOOLTIP_FACTOR_STATION_MILITARY =
        "political_map_tooltip_factor_station_military";
    public static final String POLITICAL_MAP_TOOLTIP_FACTOR_RATED = "political_map_tooltip_factor_rated";
    public static final String POLITICAL_MAP_TOOLTIP_FACTOR_COUNTED = "political_map_tooltip_factor_counted";
    public static final String POLITICAL_MAP_TOOLTIP_FACTOR_JOINED = "political_map_tooltip_factor_joined";
    public static final String POLITICAL_MAP_TOOLTIP_FACTOR_PENALTY = "political_map_tooltip_factor_penalty";
    public static final String POLITICAL_MAP_TOOLTIP_PATROL_TIER = "political_map_tooltip_patrol_tier";
    public static final String POLITICAL_MAP_TOOLTIP_PATROL_SMALL = "political_map_tooltip_patrol_small";
    public static final String POLITICAL_MAP_TOOLTIP_PATROL_MEDIUM = "political_map_tooltip_patrol_medium";
    public static final String POLITICAL_MAP_TOOLTIP_PATROL_LARGE = "political_map_tooltip_patrol_large";

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
