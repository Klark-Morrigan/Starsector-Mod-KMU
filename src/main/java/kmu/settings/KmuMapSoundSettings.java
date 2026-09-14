package kmu.settings;

/**
 * How loud the map and its panel answer the pointer, one level per kind of thing there is to
 * reach.
 *
 * <p>Named for what the player is reaching rather than for the widget classes, which is what keeps
 * the set closed as widgets are added. The levels are only meaningful against each other - the gap
 * between them is what stops a column of listed rows reading as chatter - so they are read as one
 * balance rather than as five independent volumes. *
 * <p>What each knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The prose
 * here answers only what that column cannot: why a default is the number it is, and what a caller
 * has to know to use the value.
 */
public final class KmuMapSoundSettings {

    // One level per kind of thing there is to reach, named for what the player is reaching rather
    // than for the widget classes, which is what keeps the set closed as widgets are added.
    private static final String SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_arrival_panelChrome";

    private static final String SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_arrival_singleOptionControl";

    private static final String SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_arrival_listedItem";

    // Its own knob and not one of the arrival levels, because scrolling is not an arrival: the rows
    // travel under a parked cursor.
    private static final String SIDEBAR_LIST_SCROLL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_listScroll";

    private static final String MAP_CELL_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_map_arrival_cell";

    // The shipped balance: vanilla's own mouseover level halved for anything the player aims at,
    // and halved again for the items a sweep crosses several of on its way there. The gap between
    // the two numbers is the whole of what stops a column of listed rows reading as chatter, so
    // they are only meaningful against each other - a player raising one has retuned the balance
    // rather than turned up a part of it. The scroll and the map's cell tick are single moments and
    // sit at the aimed-at level.
    private static final float DEFAULT_SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME = 0.5f;

    private static final float DEFAULT_SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME = 0.5f;

    private static final float DEFAULT_SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME = 0.25f;

    private static final float DEFAULT_SIDEBAR_LIST_SCROLL_VOLUME = 0.5f;

    private static final float DEFAULT_MAP_CELL_ARRIVAL_VOLUME = 0.5f;

    private KmuMapSoundSettings() {
    }

    /**
     * @return how loudly the sidebar's own furniture - a header tab, the collapse handle - sounds
     *         as the cursor reaches it, as a multiple of the level the engine holds for the sample;
     *         0.5 by default, which is vanilla's mouseover halved, and 0 to reach it silently
     */
    public static float getMapSidebarPanelChromeArrivalVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME);
    }

    /**
     * @return how loudly a sidebar control with one answer to give - a tick box, a switch - sounds
     *         as the cursor reaches it, on the same scale; 0.5 by default, level with the panel's
     *         own furniture because both are aimed at rather than crossed
     */
    public static float getMapSidebarSingleOptionControlArrivalVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME);
    }

    /**
     * @return how loudly one of many alike - one option of a row of them, one row of a list -
     *         sounds as the cursor reaches it, on the same scale; 0.25 by default, under the two
     *         levels above because a sweep crosses several of these on the way to the one thing
     *         aimed at
     */
    public static float getMapSidebarListedItemArrivalVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME);
    }

    /**
     * @return how loudly the sidebar's list sounds as the wheel moves it, on the same scale; 0.5 by
     *         default. One answer for the whole movement rather than one per row that passed
     */
    public static float getMapSidebarListScrollVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_LIST_SCROLL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_LIST_SCROLL_VOLUME);
    }

    /**
     * @return how loudly the map ticks as the cursor reaches a new system's cell, on the same
     *         scale; 0.5 by default. Silenced by 0 here and, with every other answer the map makes
     *         to the cursor, by the hovering switches above
     */
    public static float getMapCellArrivalVolume() {
        return KmuLunaSettings.readFloat(
            MAP_CELL_ARRIVAL_VOLUME_FIELD,
            DEFAULT_MAP_CELL_ARRIVAL_VOLUME);
    }
}
