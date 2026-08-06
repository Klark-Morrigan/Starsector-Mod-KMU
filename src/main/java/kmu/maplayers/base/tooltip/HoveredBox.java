package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.StarSystems;

import kmu.maplayers.base.hover.MapHoverState;

import java.util.Optional;

/**
 * The hover box that would draw this frame, together with what it would draw for: the active layer's
 * injected tooltip, the live sector, and the star system under the cursor.
 *
 * <p>Held as one value because the three are resolved by one chain of conditions - a cell is hovered, a
 * layer injected a box, a sector is live, and the hovered id still resolves to a system - and two passes
 * need the answer: the pass that draws the box, and the pass that claims the key which switches it. A
 * chain spelled out twice is one edit away from the key acting on a frame the box does not draw, or
 * falling through on one it does.
 *
 * <p>It answers what <em>would</em> draw rather than what did: the input pass runs before the render
 * pass and gets no GL context, so it can only ask the same questions and trust that they are the same
 * questions. Which is exactly why they are asked here instead of at each caller.
 *
 * @param tooltip the box the active layer injected for the hovered cell
 * @param sector  the live sector the box reads its content from
 * @param system  the star system under the cursor
 */
record HoveredBox(
    MapHoverTooltip tooltip,
    SectorAPI sector,
    StarSystemAPI system) {

    /**
     * Resolves what the cursor is over into the box that would draw for it, or empty where nothing
     * would: no cell hovered, no layer showing a box, no live sector, or an id that no longer names a
     * system.
     *
     * <p>Reads the hover, the layer registry, and the sector live rather than taking them, because both
     * callers are passes the engine drives with nothing but a frame - neither is handed the state it
     * would otherwise pass along.
     *
     * @return the box and what it would draw for, or empty when no box would draw at all
     */
    static Optional<HoveredBox> resolveHoveredBox() {

        var hover = MapHoverState.getInstance().getHover();
        if (!MapLayerCellTooltip.shouldDrawTooltipFor(hover)) {
            return Optional.empty();
        }
        var tooltip = MapLayerCellTooltip.resolveActiveTooltip();
        if (tooltip.isEmpty()) {
            return Optional.empty();
        }
        var sector = Global.getSector();
        if (sector == null) {
            return Optional.empty();
        }
        // The hover carries a system id; resolve it to the live system, tolerating an id that no longer
        // resolves (a system dropped between the publish and this frame). Matched by getId - vanilla's
        // getStarSystem keys on the optional unique id first and would miss a base-name-keyed system.
        var system = StarSystems.findById(sector, hover.hoveredSystemId());
        if (system == null) {
            return Optional.empty();
        }
        return Optional.of(new HoveredBox(tooltip.get(), sector, system));
    }

    /**
     * Whether the box under the cursor has a richer counterpart to switch to for this system - the one
     * question the key that switches them has to answer before acting.
     *
     * @return true when switching would show the player something the drawn box does not
     */
    boolean isOfferingExpansion() {
        return tooltip.isOfferingExpansionFor(sector, system);
    }
}
