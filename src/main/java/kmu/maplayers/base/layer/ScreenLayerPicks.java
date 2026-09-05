package kmu.maplayers.base.layer;

/**
 * One screen's two picks for the map layers - which layer is active there, and whether that screen's layers
 * are on it at all - and the scope every other preference set on that screen resolves its key through. All
 * three are that screen's own state, and all three are per screen for the same reason: the controls that
 * move them sit on each screen's own chrome.
 *
 * <p>They travel as one value because no reader wants one of them for a different screen than the others.
 * Handed over separately, they are chosen at each wiring site and can be crossed one way and not the
 * other, which compiles and reads as the feature working until a player switches one screen off and empties
 * the other - or sets a preference on one panel and finds the other's moved. As one value the screen is
 * chosen once, where its keys are named, and the crossing has nowhere to happen.
 *
 * <p>The hide travels as the reading behind a control rather than as the bare pick, because whether this
 * screen has a control able to reverse a hide governs the tab beside it as well: the strip stops offering
 * its own way of emptying the map on a screen that has one. Held as the narrower type, a holder of the value
 * holds that whole story rather than half of it, and no site has to find the other half for itself.
 *
 * @param layerSelection  which layer is active on this screen
 * @param layerVisibility whether this screen's layers show, how far through a change they stand, and
 *                        whether a control able to switch them back on stands on this screen
 * @param memoryScope     the scope a preference set on this screen's panel is saved and resolved under
 */
public record ScreenLayerPicks(
    ActiveLayerSelection layerSelection,
    ControlBackedMapLayerVisibility layerVisibility,
    ScreenMemoryScope memoryScope) {
}
