package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;

import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.ui.map.probes.VanillaMapTooltip;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.hover.MapHoverGates;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.layer.MapLayerRegistry;

import java.util.Optional;
import java.util.function.BooleanSupplier;

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
 * <p>The dispatcher owns only the gates every hover tooltip shares - the settings switches that
 * answer for every layer ({@link MapHoverGates}), a map on screen in either of its looks, a
 * hovered cell, stepping aside while the vanilla map draws its own tooltip - and resolves the hovered
 * system,
 * then hands it to the injected tooltip. A layer's own tooltip switch stays with the layer, which
 * withholds its box by injecting none. The pass is
 * read-only over the hover state and consumes no input, so the vanilla star-system tooltip keeps
 * drawing; the tooltip a layer injects owns its own content, look, and any further precondition.
 *
 * <p>How much detail the drawn box states is settled here too, and by one shared fact rather than
 * per layer: the dispatcher reads {@link HoverTooltipDetailModeState} and draws the counterpart the
 * injected tooltip offers for that mode, or the tooltip itself when it offers none. So the choice
 * holds across hovers and layer switches, and a tooltip that states one amount of detail needs no
 * case of its own.
 */
public final class MapLayerCellTooltip implements CampaignUIRenderingListener {

    // Whether a map is on screen at all this frame - either host, either look. Supplied rather than
    // read here so a test can name the answer: the live read walks the running game's widget tree on
    // the intel side, which no test can stand up.
    private final BooleanSupplier isAnyMapShowing;

    // The live read this dispatcher steps aside for. Supplied rather than built here: it is the one
    // collaborator whose answer changes what this draws, so a caller that can hand over a stub is
    // what makes the step-aside checkable at all.
    private final VanillaMapTooltip vanillaMapTooltip;

    /**
     * @param vanillaMapTooltip the probe answering whether the map is drawing its own tooltip,
     *                          which this box stands aside for
     * @param isAnyMapShowing   whether a map is on screen at all - either host, either look -
     *                          host-blind because this listener is called for the whole campaign UI
     *                          and is never told which screen is up
     */
    public MapLayerCellTooltip(
            VanillaMapTooltip vanillaMapTooltip,
            BooleanSupplier isAnyMapShowing) {
        this.vanillaMapTooltip = vanillaMapTooltip;
        this.isAnyMapShowing = isAnyMapShowing;
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
        // The two hover switches that answer for every layer - the hovering master and the global
        // tooltip switch. With either off no layer's box draws, whatever the map state or hover.
        // The layer's own tooltip switch is the layer's to read: it withholds the box by injecting
        // none, the same way a layer with nothing to say about a cell does.
        if (!MapHoverGates.isHoverTooltipEnabled()) {
            return;
        }
        // A map has to be on screen for the overlay to be up, and only then is a hover meaningful.
        // Either look counts: the layers paint through a terrain pair, one half of which draws over
        // the Starscape starfield, so the box follows the picture into that mode rather than
        // vanishing with the schematic. Asked host-blind because the box belongs on either host -
        // the terrain pass runs on the sector map and on the intel screen's map visor alike, and the
        // box is placed at the cursor rather than against a screen.
        if (!isAnyMapShowing.getAsBoolean()) {
            return;
        }
        var hover = MapHoverState.getInstance().getHover();
        if (!shouldDrawTooltipFor(hover)) {
            return;
        }
        // Step aside when the vanilla map screen is drawing its own tooltip (the player is over a
        // star icon), so only one box shows there. Read live from the map's UI tree; a read that
        // fails on some game build draws ours anyway rather than hiding it.
        if (vanillaMapTooltip.isShowing()) {
            return;
        }
        // The active layer decides which tooltip to draw by injecting one; a layer with none leaves
        // this empty, and nothing draws.
        var tooltip = resolveActiveTooltip();
        if (tooltip.isEmpty()) {
            return;
        }
        var sector = Global.getSector();
        if (sector == null) {
            return;
        }
        // The hover carries a system id; resolve it to the live system, tolerating an id that no longer
        // resolves (a system dropped between the publish and this paint). Matched by getId - vanilla's
        // getStarSystem keys on the optional unique id first and would miss a base-name-keyed system.
        var system = StarSystems.findById(sector, hover.hoveredSystemId());
        if (system == null) {
            return;
        }
        // Which of an injected tooltip's boxes to draw is the shared detail mode's call rather than
        // the layer's: one mode selects for whatever is hovered, so it holds across hovers and layer
        // switches instead of each layer having to remember a choice made over another one's cell.
        selectVariantFor(tooltip.get(), HoverTooltipDetailModeState.getInstance().getMode())
            .renderFor(sector, system);
    }

    // The tooltip the frame draws, asked of whatever draws for the showing screen - the same read the
    // map surface paints through. Nothing drawing at all resolves the same as an injected empty:
    // nothing to show, so a switch-only tab and a pre-registration frame need no case of their own.
    static Optional<MapHoverTooltip> resolveActiveTooltip() {
        var layerRenderer = MapLayerRegistry.resolveActiveMapRenderer();
        if (layerRenderer == null) {
            return Optional.empty();
        }
        return layerRenderer.resolveHoverTooltip();
    }

    // The box the current detail mode calls for: the tooltip's richer counterpart while the mode asks
    // for one and the tooltip defines one, and the tooltip itself in every other case - so a tooltip
    // that defines no counterpart draws the same box under either mode rather than nothing at all.
    // Descends exactly one level: a counterpart is never asked for a counterpart of its own, so the
    // model cannot recurse however deeply a layer nests its variants.
    static MapHoverTooltip selectVariantFor(MapHoverTooltip base, HoverTooltipDetailMode mode) {
        if (mode != HoverTooltipDetailMode.EXPANDED) {
            return base;
        }
        return base.resolveExpandedVariant().orElse(base);
    }

    // Whether a tooltip should draw for this hover - the pure part of the gate: a cell must be
    // hovered. Stepping aside for the vanilla star tooltip is the separate, live VanillaMapTooltip
    // check in the render pass, since it reads the map's UI tree rather than the hover value.
    static boolean shouldDrawTooltipFor(MapHover hover) {
        return hover.isHovering();
    }
}
