package kmu.settings;

import kmlib.settings.LabeledChoice;
import kmlib.settings.LabeledChoices;
import kmlib.starsector.ui.layout.ScreenAnchor;

/**
 * Where the political-map overlay sidebar anchors on the sector map - the player's
 * choice behind the "Sidebar anchor" Radio setting.
 *
 * <p>Screen real estate on the sector map is scarce, so the sidebar may sit at any of the
 * four corners or the midpoint of any edge, keeping it clear of whatever the player needs
 * to read. Each choice names one position, carries the {@link ScreenAnchor} the layout
 * places against, and reuses {@link LabeledChoices} to map LunaLib's stored Radio label
 * back to a choice. The labels here must match the {@code secondaryValue} options in
 * data/config/LunaSettings.csv exactly.
 */
public enum SidebarAnchorChoice implements LabeledChoice {
    TOP_LEFT("Top left", ScreenAnchor.TOP_LEFT),
    TOP_CENTER("Top center", ScreenAnchor.TOP_CENTER),
    TOP_RIGHT("Top right", ScreenAnchor.TOP_RIGHT),
    RIGHT_CENTER("Right center", ScreenAnchor.RIGHT_CENTER),
    BOTTOM_RIGHT("Bottom right", ScreenAnchor.BOTTOM_RIGHT),
    BOTTOM_CENTER("Bottom center", ScreenAnchor.BOTTOM_CENTER),
    BOTTOM_LEFT("Bottom left", ScreenAnchor.BOTTOM_LEFT),
    LEFT_CENTER("Left center", ScreenAnchor.LEFT_CENTER);

    private final String label;
    private final ScreenAnchor screenAnchor;

    SidebarAnchorChoice(String label, ScreenAnchor screenAnchor) {
        this.label = label;
        this.screenAnchor = screenAnchor;
    }

    /**
     * @return the LunaLib Radio option label for this choice, used both as a
     *         read fallback and as the CSV default value
     */
    @Override
    public String getLabel() {
        return label;
    }

    /**
     * @return the screen anchor this choice pins the box to, the geometry the box
     *         placement is laid out against
     */
    public ScreenAnchor getScreenAnchor() {
        return screenAnchor;
    }

    /**
     * Maps a stored Radio label back to its choice.
     *
     * @param label    the label LunaLib returned for the field
     * @param fallback the choice to use when {@code label} matches no option
     *                 (unset, unreadable, or a stale label from an old config)
     * @return the matching choice, or {@code fallback} when none matches
     */
    public static SidebarAnchorChoice fromLabel(String label, SidebarAnchorChoice fallback) {
        return LabeledChoices.fromLabel(values(), label, fallback);
    }
}
