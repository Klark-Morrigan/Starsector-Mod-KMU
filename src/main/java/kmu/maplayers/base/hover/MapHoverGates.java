package kmu.maplayers.base.hover;

import kmu.settings.KmuMapLayerSettings;

/**
 * The hover switches that answer for every map layer at once: whether the map answers the cursor at
 * all, and then whether each kind of answer - the effects (the halo and the cell wash) and the
 * tooltip (the box naming what is under the cursor) - is on across the whole map.
 *
 * <p>Hover feedback is switched at three tiers, and this is the top two of them. The master covers
 * both kinds, the pair under it covers one kind each, and a layer carries its own pair below these
 * for its own paint. Every tier ANDs with the one above, so a layer switch can only withhold
 * feedback the tiers above already allow, and a global switch reaches every layer's.
 *
 * <p>The AND lives here rather than at each reader because the tiers are the whole point of the
 * arrangement: a reader that forgot one would silently answer for a tier a player had switched off.
 * A layer asks these for the tiers above it and ANDs its own switch in, which is the only reading a
 * layer needs to do to take part.
 *
 * <p>Every switch is read live rather than cached, so a player toggling one on the settings screen
 * sees the map change on the next frame without a rebuild.
 */
public final class MapHoverGates {

    private MapHoverGates() {
    }

    /**
     * @return whether hover effects may draw on any layer at all - the master ANDed with the global
     *         effects switch. A layer ANDs its own effects switch into this before painting a halo
     *         or a wash
     */
    public static boolean isHoverEffectsEnabled() {
        return KmuMapLayerSettings.getMapHoveringEnabled()
            && KmuMapLayerSettings.getMapHoverEffectsEnabled();
    }

    /**
     * @return whether a hover tooltip may draw on any layer at all - the master ANDed with the
     *         global tooltip switch. A layer ANDs its own tooltip switch into this before offering
     *         a box
     */
    public static boolean isHoverTooltipEnabled() {
        return KmuMapLayerSettings.getMapHoveringEnabled()
            && KmuMapLayerSettings.getMapHoverTooltipEnabled();
    }

    /**
     * Whether the cursor can be located against the pass now running.
     *
     * <p>A different question from the switches above, and the reason it sits beside them: those
     * ask what the player wants answered, this asks whether an answer is possible. The map hook the
     * layers draw through belongs to the sector map by convention alone, and a map another mod
     * builds drives it too - with its own position and zoom, and no way for this end to tell that
     * transform from the vanilla one. A cursor unprojected against it still yields a world point,
     * and in hyperspace, where campaign and sector map coordinates are one space, that point lands
     * inside real cells. So the wrong answer here is not a missing one but a confident one.
     *
     * <p>The presence read is host-blind by design: it says a vanilla map is on screen somewhere,
     * never that this pass is that map's. With one showing while a foreign map also draws, both
     * passes answer true, and which of them the frame's single cursor read lands on is settled
     * elsewhere. The render constraint is what closes that case, by keeping the foreign pass from
     * running at all.
     *
     * @param isAnyMapShowing whether either vanilla map host is showing, from
     *                        {@code MapPresence#isAnyMapShowing}
     * @return whether the hover may be resolved on this pass
     */
    public static boolean isCursorLocatableOn(boolean isAnyMapShowing) {
        return !KmuMapLayerSettings.getMapLayerMouseoverOnlyOnTheirHosts() || isAnyMapShowing;
    }
}
