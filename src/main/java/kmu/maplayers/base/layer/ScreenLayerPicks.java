package kmu.maplayers.base.layer;

/**
 * One screen's two picks for the map layers: which layer is active there, and whether that screen's layers
 * are on it at all. Both are that screen's own state under its own frozen key, and both are per screen for
 * the same reason - the controls that move them sit on each screen's own chrome.
 *
 * <p>They travel as one value because no reader wants one of them for a different screen than the other.
 * Handed over separately, the two are chosen at each wiring site and can be crossed one way and not the
 * other, which compiles and reads as the feature working until a player switches one screen off and empties
 * the other. As a pair the screen is chosen once, where the keys are named, and the crossing has nowhere to
 * happen.
 *
 * @param layerSelection  which layer is active on this screen
 * @param layerVisibility whether this screen's layers show, and how far through a change they stand
 */
public record ScreenLayerPicks(
    ActiveLayerSelection layerSelection,
    MapLayerVisibility layerVisibility) {
}
