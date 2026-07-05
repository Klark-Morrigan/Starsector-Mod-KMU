package kmu.politicalmap.ui;

import kmlib.starsector.memory.SectorMemoryFlag;

/**
 * The overlay's on/off choice, the single source of truth the sidebar tab writes and both
 * the sidebar and the map terrain read. Backed by a {@link SectorMemoryFlag}, so the pick
 * lives in sector memory and survives save/reload.
 *
 * <p>Unset means the player has never touched the tab; the overlay then starts shown, so
 * the feature is visible the first time the sector map is opened.
 */
public final class PoliticalOverlayToggle {
    // Sector-memory key holding the choice. Frozen once shipped: renaming it silently resets
    // every existing save to the default.
    private static final SectorMemoryFlag FLAG =
            new SectorMemoryFlag("$kmu_political_overlay_enabled", true);

    private PoliticalOverlayToggle() {
    }

    /**
     * @return whether the overlay is currently enabled, defaulting to enabled when the
     *         player has not yet touched the tab
     */
    public static boolean isOverlayEnabled() {
        return FLAG.isSet();
    }

    /** Records the overlay's on/off choice in the save. */
    public static void setOverlayEnabled(boolean isEnabled) {
        FLAG.set(isEnabled);
    }

    /** Flips the overlay between shown and hidden. */
    public static void toggleOverlayEnabled() {
        FLAG.toggle();
    }
}
