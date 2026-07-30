package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;

import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.map.VanillaMapTooltip;

import kmu.maplayers.base.hover.PoliticalMapHover;
import kmu.maplayers.base.hover.PoliticalMapHoverState;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.settings.KmuLunaSettings;

import java.util.Optional;

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
 * <p>The dispatcher owns only the gates every hover tooltip shares - the master settings toggle, the
 * sector-map-with-starscape-off gate, a hovered cell, stepping aside while the vanilla map draws its
 * own tooltip - and resolves the hovered system, then hands it to the injected tooltip. The pass is
 * read-only over the hover state and consumes no input, so the vanilla star-system tooltip keeps
 * drawing; the tooltip a layer injects owns its own content, look, and any further precondition.
 */
public final class MapLayerCellTooltip implements CampaignUIRenderingListener {

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
        // Root master switch: with the tooltip turned off in settings the box never draws, whatever
        // the map state or hover. Read live each frame so toggling it takes effect without a rebuild.
        if (!KmuLunaSettings.getPoliticalMapHoverTooltipEnabled()) {
            return;
        }
        // Only the sector map with the starscape filter off shows the overlay, so only then is a hover
        // meaningful; the same gate the sidebar uses.
        if (!CampaignMapView.isSectorMapWithStarscapeOff()) {
            return;
        }
        var hover = PoliticalMapHoverState.getInstance().getHover();
        if (!shouldDrawTooltipFor(hover)) {
            return;
        }
        // Step aside when the vanilla map screen is drawing its own tooltip (the player is over a
        // star icon), so only one box shows there. Read live from the map's UI tree; a read that
        // fails on some game build draws ours anyway rather than hiding it.
        if (VanillaMapTooltip.isShowing()) {
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
        tooltip.get().renderFor(sector, system);
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

    // Whether a tooltip should draw for this hover - the pure part of the gate: a cell must be
    // hovered. Stepping aside for the vanilla star tooltip is the separate, live VanillaMapTooltip
    // check in the render pass, since it reads the map's UI tree rather than the hover value.
    static boolean shouldDrawTooltipFor(PoliticalMapHover hover) {
        return hover.isHovering();
    }
}
