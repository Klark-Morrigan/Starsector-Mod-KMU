package kmu.settings;

/**
 * The two hatches over the map layers' own controls: whether the tick box that shows and hides them
 * is put on the vanilla filter row, and whether the button that opens the bar-arranging box stands
 * where the roster gives it nothing to arrange - the {@code Map - Dev} Map controls section, one
 * class behind it.
 *
 * <p>Apart from {@link KmuMapSidebarSettings} because neither of these is a look. That class answers
 * where the sidebar sits and how it is drawn, every knob of it something a player sets to taste; a
 * hatch is what somebody opens to reach a control that is otherwise correctly out of the way, or to
 * stop the mod reaching for one at all.
 */
public final class KmuMapControlSettings {

    // The row says "button" because that is what the player is looking at; the mod's own word for
    // the control is the opener.
    private static final String ARRANGEMENT_OPENER_ALWAYS_SHOWN_FIELD =
        "kmu_map_dev_ui_controls_layersArrangementButton_isAlwaysShown";

    private static final String FILTER_ROW_TOGGLE_ENABLED_FIELD =
        "kmu_map_dev_ui_controls_mapLayersToggle_isEnabled";

    // Off: the roster count is the answer the bar should give a player, and an opener onto a box with
    // one row and nothing to do in it teaches them the feature is empty rather than that it is not
    // theirs yet. What this exists for is reaching that box on an install carrying one layer, which
    // is a thing to be asked for rather than the state everyone is put in.
    private static final boolean DEFAULT_ARRANGEMENT_OPENER_ALWAYS_SHOWN = false;

    // On: switched off no screen is given the control, and a screen with no control holds its layers
    // shown - so a player who never opens the settings would get a feature that draws with no way to
    // put it away. An escape hatch is reached for after something misbehaves, so it ships open.
    private static final boolean DEFAULT_FILTER_ROW_TOGGLE_ENABLED = true;

    private KmuMapControlSettings() {
    }

    /**
     * @return whether the tick box that shows and hides the map layers is added to the vanilla map
     *         filter row on either map screen; on by default. Off makes no attempt on either screen,
     *         so both rows are left exactly as the game builds them, and a screen with no control of
     *         its own never hides its layers whatever the save holds
     */
    public static boolean isMapFilterRowToggleEnabled() {
        return KmuLunaSettings.readBoolean(
            FILTER_ROW_TOGGLE_ENABLED_FIELD,
            DEFAULT_FILTER_ROW_TOGGLE_ENABLED);
    }

    /**
     * @return whether the bar's opener stands at the end of the map layer tab row whatever the
     *         roster holds; off by default, which stands it only where there is more than one layer
     *         that paints. On is how the arranging box is reached at all on an install carrying one
     *         layer, where the row it would open has nowhere to move and nothing to uncheck
     */
    public static boolean isMapLayerArrangementOpenerAlwaysShown() {
        return KmuLunaSettings.readBoolean(
            ARRANGEMENT_OPENER_ALWAYS_SHOWN_FIELD,
            DEFAULT_ARRANGEMENT_OPENER_ALWAYS_SHOWN);
    }
}
