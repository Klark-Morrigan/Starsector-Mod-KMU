package kmu.util;

/**
 * The IDs of the strings the map-layer framework reads: what its reaches into the game cost when a
 * game release breaks them, the bar-arranging dialog, the layer strip and sidebar captions, the
 * filter row's tick box, the hover box's footer, and the words an observation's age is told in.
 *
 * <p>Read through {@link KmuStringKeys}' accessors, which bind the category.
 */
public final class KmuMapStringKeys {

    /**
     * What a session loses where the renderer binding the map's cursor read stands on stops
     * holding, for the consequence slot of KMLib's compatibility notice. "Won't respond to the
     * cursor" rather than the highlight alone, which understates it - the hover box is keyed to the
     * same resolved cell and goes with it - and rather than "overlays will not work", which
     * overstates it: the overlays still draw, and the sidebar previews are keyed off a row the
     * pointer is over rather than off the cursor's place on the map.
     */
    public static final String COMPATIBILITY_LOST_MAP_CURSOR = "compatibility_lost_map_cursor";

    /** What a Fast Rendering mismatch does not cost, for the notice's optional "No effect" row. */
    public static final String COMPATIBILITY_UNAFFECTED_MAP_CURSOR = "compatibility_unaffected_map_cursor";

    // What each of the map layers' reaches into the game costs when a game release breaks it, and
    // what it does not: one pair per MapLayerGameReach constant, which is where each loss is described.

    /**
     * "May not show", and on either map, because the sector map and the intel screen's are read
     * apart and either can break alone.
     */
    public static final String COMPATIBILITY_LOST_MAP_VIEW = "compatibility_lost_map_view";
    public static final String COMPATIBILITY_UNAFFECTED_MAP_VIEW = "compatibility_unaffected_map_view";

    /** Says the layers stay shown, since a map with no tick box has no way to hide them. */
    public static final String COMPATIBILITY_LOST_FILTER_ROW_TOGGLE = "compatibility_lost_filter_row_toggle";
    public static final String COMPATIBILITY_UNAFFECTED_FILTER_ROW_TOGGLE =
        "compatibility_unaffected_filter_row_toggle";

    public static final String COMPATIBILITY_LOST_ARRANGE_DIALOG = "compatibility_lost_arrange_dialog";
    public static final String COMPATIBILITY_UNAFFECTED_ARRANGE_DIALOG = "compatibility_unaffected_arrange_dialog";
    public static final String COMPATIBILITY_LOST_SCREEN_COVERS = "compatibility_lost_screen_covers";
    public static final String COMPATIBILITY_UNAFFECTED_SCREEN_COVERS = "compatibility_unaffected_screen_covers";
    public static final String COMPATIBILITY_LOST_MAP_TOOLTIPS = "compatibility_lost_map_tooltips";
    public static final String COMPATIBILITY_UNAFFECTED_MAP_TOOLTIPS = "compatibility_unaffected_map_tooltips";
    public static final String COMPATIBILITY_LOST_STARSCAPE_RESEAT = "compatibility_lost_starscape_reseat";
    public static final String COMPATIBILITY_UNAFFECTED_STARSCAPE_RESEAT =
        "compatibility_unaffected_starscape_reseat";
    public static final String COMPATIBILITY_LOST_STARSCAPE_TERRAIN = "compatibility_lost_starscape_terrain";
    public static final String COMPATIBILITY_UNAFFECTED_STARSCAPE_TERRAIN =
        "compatibility_unaffected_starscape_terrain";

    /**
     * What a map layer that threw and was switched off loses. One slot: the layer's tab name.
     *
     * <p>Names the two ways to bring it back, since the player can do either and both retry it.
     */
    public static final String COMPATIBILITY_LOST_MAP_LAYER = "compatibility_lost_map_layer";
    public static final String COMPATIBILITY_UNAFFECTED_MAP_LAYER = "compatibility_unaffected_map_layer";

    public static final String MAP_LAYER_ARRANGE_TITLE = "map_layer_arrange_title";
    public static final String MAP_LAYER_ARRANGE_HINT = "map_layer_arrange_hint";
    public static final String MAP_LAYER_ARRANGE_HINT_UNCHECK = "map_layer_arrange_hint_uncheck";
    public static final String MAP_LAYER_ARRANGE_MOVE_UP = "map_layer_arrange_move_up";
    public static final String MAP_LAYER_ARRANGE_MOVE_DOWN = "map_layer_arrange_move_down";
    public static final String MAP_LAYER_ARRANGE_APPLY = "map_layer_arrange_apply";
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
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING =
        "map_layer_tooltip_filter_row_toggle_uninstall_warning";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING_ACTION =
        "map_layer_tooltip_filter_row_toggle_uninstall_warning_action";
    public static final String MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING_LABEL =
        "map_layer_tooltip_filter_row_toggle_uninstall_warning_label";
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

    private KmuMapStringKeys() {
    }
}
