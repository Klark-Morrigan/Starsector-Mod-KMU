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
}
