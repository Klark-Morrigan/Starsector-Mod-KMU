package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;

import kmlib.starsector.ui.map.probes.VanillaMapTooltipProbe;

import kmu.maplayers.base.hover.MapHoverPermission;

/**
 * The map's hover-tooltip dispatcher: a render listener that draws whichever {@link MapHoverTooltip}
 * the active map layer has injected for the star system under the cursor, and nothing when the active
 * layer injects none. Paints in the UI-coords above-tooltips layer - the same layer the map sidebar
 * draws in, the only one composited after the opaque core-UI map and its tooltips.
 *
 * <p>Whether a layer shows a tooltip is decided by composition, not a flag: a layer's renderer returns
 * a tooltip from {@code resolveHoverTooltip} or it does not, and this dispatcher draws whatever the
 * active layer supplies. So a layer adds or replaces its tooltip by what it injects rather than by a
 * branch here, and a switch-only tab - which supplies no renderer at all - shows nothing for the same
 * reason it paints nothing.
 *
 * <p>The dispatcher owns only the gates every hover tooltip shares - the conditions under which any
 * box could draw at all ({@link HoverTooltipGates}), which it shares with the pass claiming the
 * detail-level key, plus its own two: a hovered cell, and stepping aside while the map surface on
 * screen draws its own tooltip. It then resolves the hovered system and hands it to the injected
 * tooltip. A
 * layer's own tooltip switch stays with the layer, which withholds its box by injecting none. The
 * pass is read-only over the hover state and consumes no input, so the vanilla star-system tooltip
 * keeps drawing; the tooltip a layer injects owns its own content, look, and any further
 * precondition.
 *
 * <p>How much detail the drawn box states is settled here too, and by one shared fact rather than
 * per layer: the dispatcher reads {@link HoverTooltipDetailLevelState} and hands the level to the box
 * the active layer injected, which reads its own content only as deep as the level admits. So the
 * choice holds across hovers and layer switches, no layer holds a level of its own to be told about,
 * and the level never decides <em>which</em> box draws - one box per layer, read to four depths.
 */
public final class MapLayerCellTooltip implements CampaignUIRenderingListener {

    // Which frames the cursor can be located against - a vanilla map host up, or a permission the
    // player granted reaching further. Held as the shared type rather than as a boolean so this pass
    // cannot come to compose the screen reads differently from the map pass that resolves the hover
    // it reports; handed in rather than composed here, the live reads walking the running game's
    // widget tree on the intel side.
    private final MapHoverPermission hoverPermission;

    // The live read this dispatcher steps aside for. Supplied rather than built here: it is the one
    // collaborator whose answer changes what this draws, so the caller decides what the step-aside
    // is judged against.
    private final VanillaMapTooltipProbe vanillaMapTooltipProbe;

    /**
     * @param vanillaMapTooltipProbe the probe answering whether the map is drawing its own tooltip,
     *                               which this box stands aside for
     * @param hoverPermission        which frames the cursor can be located against - host-blind
     *                               because this listener is called for the whole campaign UI and is
     *                               never told which screen is up
     */
    public MapLayerCellTooltip(
            VanillaMapTooltipProbe vanillaMapTooltipProbe,
            MapHoverPermission hoverPermission) {

        this.hoverPermission = hoverPermission;
        this.vanillaMapTooltipProbe = vanillaMapTooltipProbe;
    }

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
        // Below the whole campaign UI - under the map screen. Nothing belongs here.
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        // Occluded by the opaque core-UI map, like the sidebar's same pass. The box draws above tooltips.
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        // The conditions shared with the pass that claims the detail-level key, asked of the one seam
        // both read so the key cannot come to be claimed on a frame this draws nothing in: the hover
        // switches answering for every layer, and a frame the cursor can be located against - the
        // same answer the hover under it was resolved from. The layer's own tooltip switch is not
        // among them - it is the layer's to read, withheld by injecting no box at all, the same way
        // a layer with nothing to say about a cell does.
        if (!HoverTooltipGates.canAnyBoxDraw(hoverPermission)) {
            return;
        }
        // Step aside when the map surface on screen is drawing its own tooltip (the player is over a
        // star icon), so only one box shows there. That surface is a vanilla map host or a map
        // another mod docked, the probe being rooted at whichever owns the frame. Read live from its
        // UI tree; a read that fails on some game build draws ours anyway rather than hiding it.
        if (vanillaMapTooltipProbe.isTooltipShowing()) {
            return;
        }
        // What the cursor is over and the box the active layer injected for it, resolved through the
        // one chain the pass claiming the cycle key reads too - so the key cannot come to act on a
        // frame this draws nothing in.
        var hoveredBox = HoveredBox.resolveHoveredBox();
        if (hoveredBox.isEmpty()) {
            return;
        }
        // How much detail the box states is the shared level's call rather than the layer's: one level
        // answers for whatever is hovered, so it holds across hovers and layer switches instead of each
        // layer having to remember a choice made over another one's cell.
        //
        // Handed straight to the box rather than acted on here, because a level is a cut through the
        // one tree a layer composes: which box draws never turns on it, only how deep that box reads
        // itself.
        var detailLevel = HoverTooltipDetailLevelState.getInstance().getLevel();

        hoveredBox.get().tooltip().renderFor(
            hoveredBox.get().sector(),
            hoveredBox.get().system(),
            detailLevel);
    }
}
