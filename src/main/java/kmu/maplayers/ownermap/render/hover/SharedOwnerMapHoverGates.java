package kmu.maplayers.ownermap.render.hover;

import kmu.maplayers.base.hover.MapHoverGates;
import kmu.maplayers.base.hover.MapLayerHoverGates;
import kmu.settings.KmuOwnerMapHighlightSettings;

/**
 * Whether an owner-painted layer answers the cursor, read off the one set of owner-map switches
 * every such layer shares: the halo and cell wash it paints, and the box naming the hovered system.
 *
 * <p>Each answer is the owner-map switch ANDed with the switches above it that answer for every
 * layer ({@link MapHoverGates}), so a player can silence the owner-painted boxes while another kind
 * of layer's still shows, or switch every layer's off from one row above. The two are asked
 * separately because they are switched separately: a player who wants the box without the map
 * lighting up under the cursor gets exactly that.
 *
 * <p>Shared because the owner-map knobs are: a layer wanting switches of its own answers
 * {@link MapLayerHoverGates} with them instead.
 */
public enum SharedOwnerMapHoverGates implements MapLayerHoverGates {

    /** The one set of shared switches; it holds no state. */
    INSTANCE;

    /**
     * @return whether the halo and the cell wash may draw - every effects tier, the owner-map switch
     *         included
     */
    @Override
    public boolean isHoverEffectsEnabled() {
        return MapHoverGates.isHoverEffectsEnabled()
            && KmuOwnerMapHighlightSettings.getOwnerMapHoverEffectsEnabled();
    }

    /**
     * @return whether the layer may offer its hover box - every tooltip tier, the owner-map switch
     *         included. The framework asks the tiers above it again before drawing; asking them
     *         here too is what makes the offer answer for itself
     */
    @Override
    public boolean isHoverTooltipEnabled() {
        return MapHoverGates.isHoverTooltipEnabled()
            && KmuOwnerMapHighlightSettings.getOwnerMapHoverTooltipEnabled();
    }
}
