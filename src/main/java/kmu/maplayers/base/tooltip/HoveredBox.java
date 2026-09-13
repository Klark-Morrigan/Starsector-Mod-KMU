package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SectorStarSystems;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;

import java.util.Optional;

/**
 * The hover box that would draw this frame, together with what it would draw for: the active layer's
 * injected tooltip, the live sector, and the star system under the cursor.
 *
 * <p>Held as one value because the three are resolved by one chain of conditions - a cell is hovered,
 * the screen's layers are on, a layer injected a box, a sector is live, and the hovered id still
 * resolves to a system - and two passes need the answer: the pass that draws the box, and the pass
 * that claims the key which switches it. A chain spelled out twice is one edit away from the key
 * acting on a frame the box does not draw, or falling through on one it does.
 *
 * <p>It answers what <em>would</em> draw rather than what did: the input pass runs before the render
 * pass and gets no GL context, so it can only ask the same questions and trust that they are the same
 * questions. Which is exactly why they are asked here instead of at each caller.
 *
 * @param tooltip the box the active layer injected for the hovered cell
 * @param sector  the sector whose map is being drawn, which the box reads its content from
 * @param system  the star system under the cursor
 */
record HoveredBox(
    MapHoverTooltip tooltip,
    SectorAPI sector,
    StarSystemAPI system) {

    /**
     * Resolves what the cursor is over into the box that would draw for it, or empty where nothing
     * would: no cell hovered, the screen's layers switched off, no layer showing a box, no live sector,
     * or an id that no longer names a system.
     *
     * <p>Resolves the running sector's machinery rather than taking it, because both callers are
     * passes the engine drives with nothing but a frame - neither is handed the state it would
     * otherwise pass along.
     *
     * @return the box and what it would draw for, or empty when no box would draw at all
     */
    static Optional<HoveredBox> resolveHoveredBox() {

        // One resolution for everything below: the sector the box describes, the hover it published,
        // and the renderer that would say something about what that hover names. Taken off one
        // machinery rather than read apart, so the box cannot describe one sector out of another
        // sector's hover - the trio is what the box is, a cell of one sector's map described by that
        // sector's map.
        var machinery = SectorMapMachineryIndex.resolveMachineryForLiveSector();

        // Absent where no game is loaded, which is the machinery over no sector answering.
        var sector = machinery.resolveSector();
        if (sector == null) {
            return Optional.empty();
        }
        var hover = machinery.resolveHoverState().getHover();

        if (!shouldDrawTooltipFor(hover)) {
            return Optional.empty();
        }
        var tooltip = resolveActiveTooltip(machinery);
        if (tooltip.isEmpty()) {
            return Optional.empty();
        }
        // The hover carries a system key; resolve it to the live system, tolerating a key that no
        // longer resolves (a system dropped between the publish and this frame). By key rather than
        // by id, so a hovered cell whose system shares its id with another names the system whose
        // cell the cursor is actually on.
        var system = SectorStarSystems.findSystemByKey(sector, hover.hoveredSystemKey());
        if (system == null) {
            return Optional.empty();
        }
        return Optional.of(new HoveredBox(tooltip.get(), sector, system));
    }

    /**
     * Where moving on from {@code detailLevel} would take the box under the cursor for this system -
     * the one question the key that cycles the detail level has to answer before acting.
     *
     * <p>The level is taken rather than read here: it is the shared holder's, and this value is
     * resolved by both the pass that reads the key and the pass that draws the box, neither of which
     * this should be reaching past to a state of its own.
     *
     * @param detailLevel how deep the box is being read now, which the press moves on from
     * @return the level one press moves to, or empty where the press would show the player nothing
     *         the drawn box does not
     */
    Optional<HoverTooltipDetailLevel> resolveNextLevel(HoverTooltipDetailLevel detailLevel) {
        return tooltip.resolveNextLevelFor(sector, system, detailLevel);
    }

    /**
     * The tooltip the frame would draw, asked of whatever draws for the showing screen - the same read
     * the map surface paints through. Nothing drawing at all resolves the same as an injected empty:
     * nothing to show, so a switch-only tab and a pre-registration frame need no case of their own.
     *
     * @param machinery the machinery installed on the sector the box would describe, whose
     *                     renderer is the one holding the draw lists the hover was resolved against
     * @return the active layer's injected box, or empty when it injects none
     */
    static Optional<MapHoverTooltip> resolveActiveTooltip(SectorMapMachinery machinery) {

        // The crisp pick rather than the dissolve the overlay rides out. A box reporting what the cursor
        // is over has nothing left to report the moment the player switches the layers off, and it reads
        // the pick rather than painting through the pass that carries the fade.
        if (!MapLayerScreens.areLayersShownOnLiveScreen()) {
            return Optional.empty();
        }
        var layerRenderer = MapLayerRegistry.resolveDrawnMapRenderer(machinery);
        if (layerRenderer == null) {
            return Optional.empty();
        }
        return layerRenderer.resolveHoverTooltip();
    }

    /**
     * Whether a tooltip would draw for this hover - the pure part of the gate: a cell must be hovered.
     * Stepping aside for the vanilla star tooltip is not among it, being a live read of the map's UI
     * tree that only the drawing pass holds the probe for.
     *
     * @param hover what the cursor is over, as the hover state last published it
     * @return true when something is hovered for a box to draw about
     */
    static boolean shouldDrawTooltipFor(MapHover hover) {
        return hover.isHovering();
    }
}
