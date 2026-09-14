package kmu.maplayers.politicalmap.base.render.hover;

import kmu.maplayers.base.hover.MapHoverGates;
import kmu.settings.KmuPoliticalMapHighlightSettings;

/**
 * Whether the political map answers the cursor, and with which of the two kinds of feedback - the
 * halo and cell wash it paints, and the box naming the hovered system's standings.
 *
 * <p>Each answer is this layer's own switch ANDed with the switches above it that answer for every
 * layer ({@link MapHoverGates}), so a player can silence this layer's box while another layer's
 * still shows, or switch every layer's off from one row above. The two are asked separately because
 * they are switched separately: a player who wants the standings box without the map lighting up
 * under the cursor gets exactly that.
 *
 * <p>Held apart from the renderers that ask so the layer has one answer to each question rather than
 * one per pass: the cursor read, the highlight draw, and the tooltip offer are three passes in two
 * classes, and a tier missed in any of them would read as a switch that half works.
 */
public final class PoliticalMapHoverGates {

    private PoliticalMapHoverGates() {
    }

    /**
     * @return whether the halo and the cell wash may draw - every effects tier, this layer's own
     *         included
     */
    public static boolean isHoverEffectsEnabled() {
        return MapHoverGates.isHoverEffectsEnabled()
            && KmuPoliticalMapHighlightSettings.getPoliticalMapHoverEffectsEnabled();
    }

    /**
     * @return whether this layer may offer its hover box - every tooltip tier, this layer's own
     *         included. The framework asks the tiers above it again before drawing; asking them
     *         here too is what makes the offer answer for itself
     */
    public static boolean isHoverTooltipEnabled() {
        return MapHoverGates.isHoverTooltipEnabled()
            && KmuPoliticalMapHighlightSettings.getPoliticalMapHoverTooltipEnabled();
    }

    /**
     * @return whether anything still needs to know what the cursor is over - either kind of feedback
     *         being on. The cursor read backs both, so it is the union rather than either one: with
     *         only the box on, the read must still run to name a system, and with both off it can be
     *         skipped entirely along with the map-matrix read and hit test behind it
     */
    public static boolean isCursorReadNeeded() {
        return isHoverEffectsEnabled() || isHoverTooltipEnabled();
    }
}
