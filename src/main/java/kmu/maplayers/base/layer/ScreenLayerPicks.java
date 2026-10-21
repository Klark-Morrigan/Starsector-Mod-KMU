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
 * <p>The roster and the arrangement travel with the picks because they are what this screen's tabs are
 * composed from ({@link ScreenLayerTabs}). Every reader of a screen's tabs already holds its picks, so
 * carried here they reach that reader without a second path - and a screen's tabs cannot be composed from
 * a roster other than the one its pick resolves against.
 *
 * <p>What that screen is actually drawing travels beside the two, and is neither of them: a screen switched
 * off goes on showing what was on it for as long as its dissolve lasts, whatever its pick has done
 * meanwhile. It is composed from the pair rather than supplied, so it cannot be given one screen's picks
 * and another's picture - {@link #ScreenLayerPicks(ActiveLayerSelection, ControlBackedMapLayerVisibility,
 * ScreenMemoryScope, MapLayerRegistry, LiveMapLayerArrangement)} is what every caller builds through.
 *
 * @param layerSelection   which layer is active on this screen
 * @param layerVisibility  whether this screen's layers show, how far through a change they stand, and
 *                         whether a control able to switch them back on stands on this screen
 * @param memoryScope      the scope a preference set on this screen's panel is saved and resolved under
 * @param layerRegistry    the roster this screen's tabs are drawn from
 * @param layerArrangement the player's arrangement of that roster into a bar
 * @param drawnLayer       which layer is on this screen this frame, the dissolve included
 */
public record ScreenLayerPicks(
    ActiveLayerSelection layerSelection,
    ControlBackedMapLayerVisibility layerVisibility,
    ScreenMemoryScope memoryScope,
    MapLayerRegistry layerRegistry,
    LiveMapLayerArrangement layerArrangement,
    ScreenDrawnLayer drawnLayer) {

    /**
     * One screen's picks, the scope they are saved under and the bar they pick from, with what it draws
     * composed over them.
     *
     * <p>The constructor every caller uses. What a screen draws is a reading of the two picks handed in
     * rather than a choice of its own, so composing it here is what keeps a screen's picture bound to
     * that screen's own picks.
     *
     * @param layerSelection   which layer is active on this screen
     * @param layerVisibility  whether this screen's layers show, and how far through a change they stand
     * @param memoryScope      the scope a preference set on this screen's panel is saved and resolved under
     * @param layerRegistry    the roster this screen's tabs are drawn from
     * @param layerArrangement the player's arrangement of that roster into a bar
     */
    public ScreenLayerPicks(
            ActiveLayerSelection layerSelection,
            ControlBackedMapLayerVisibility layerVisibility,
            ScreenMemoryScope memoryScope,
            MapLayerRegistry layerRegistry,
            LiveMapLayerArrangement layerArrangement) {

        this(
            layerSelection,
            layerVisibility,
            memoryScope,
            layerRegistry,
            layerArrangement,
            new ScreenDrawnLayer(layerSelection, layerVisibility));
    }

    /**
     * Whether {@code layer} is the one on this screen this frame - the gate a layer's own state reads to
     * decide whether it is the one in play, which a layer dissolving off a switched-off screen still is
     * until it is gone.
     *
     * <p>For a caller that needs more of one screen than its tab, and so has resolved which screen it
     * is answering for once and carried it. Resolved again, the gate could answer a different screen
     * from the rest of that caller's frame.
     *
     * @param layer the layer asking whether it is the one on this screen
     * @return whether {@code layer} is the layer on this screen
     */
    public boolean isDrawingLayer(MapLayer layer) {
        // Layers are singletons, so identity settles it without an ID compare.
        return drawnLayer.resolveDrawnLayer() == layer;
    }
}
