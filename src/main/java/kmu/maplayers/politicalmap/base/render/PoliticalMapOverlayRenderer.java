package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;

import kmu.maplayers.base.hover.HoverHighlightRenderer;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.labels.LabelRenderer;
import kmu.maplayers.base.labels.anchor.ClusterAnchorRenderer;
import kmu.maplayers.base.render.MapOverlayBand;
import kmu.maplayers.base.render.clusters.ClusterRenderer;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageRenderer;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapHoverGates;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapHoverHighlightSource;
import kmu.maplayers.politicalmap.base.render.ribbon.CellPresenceRibbonRenderer;
import kmu.maplayers.politicalmap.base.render.ribbon.RibbonStyleReader;
import kmu.settings.KmuPoliticalMapSettings;

import org.apache.log4j.Logger;

/**
 * Composes the political map's map-overlay layers over the {@link PoliticalMapCache}'s current
 * draw lists, bottom to top: the base view (the normal territories, or the debug border-tracing
 * overlay when it has replaced them), then the hover highlight, then the cluster-anchor debug
 * overlay, then the per-cell presence bands, then the faction names. The two debug facilities
 * compose rather than one hiding the other - the anchor overlay layers over whichever base view
 * drew - and each layer draws only under its own toggle.
 *
 * <p>That stack is emitted one band at a time, because the map can put its own drawing between two
 * of the layers: the map holds its own nebula icons, drawn over the sector as a large sprite under
 * Starscape, and the presence bands and the faction names are the sub-layers that have to clear
 * them - both being read rather than merely seen. Which band a sub-layer belongs to is decided here
 * and nowhere else - the surfaces above only say which band they are painting, and the geometry
 * below is emitted the same way whichever band asks for it.
 */
final class PoliticalMapOverlayRenderer {
    private static final Logger LOG = Global.getLogger(PoliticalMapOverlayRenderer.class);

    // Held rather than called statically: the highlight retains the geometry it resolved for the
    // hovered cell, so a cursor resting on one cell resolves it once instead of every frame.
    private final HoverHighlightRenderer hoverHighlightRenderer = new HoverHighlightRenderer();

    // Diagnostic: ensures the first map render logs exactly once, for the no-draw investigation.
    private boolean hasLoggedFirstRender;

    /**
     * Paints one band of the overlay for one map frame from the cache's current draw lists, in the
     * map's below-UI pass so every band stays beneath the vanilla star and constellation names.
     */
    public void renderOnMap(
            PoliticalMapCache cache,
            float factor,
            float alphaMult,
            MapOverlayBand band) {

        logFirstRenderOnce(cache, factor, alphaMult);

        switch (band) {
            case BENEATH_STARSCAPE_NEBULAE -> renderTerritoryBand(cache, factor, alphaMult);
            case ABOVE_STARSCAPE_NEBULAE -> renderClearOfNebulaeBand(cache, factor, alphaMult);

            // A switch statement over an enum is not checked for exhaustiveness, so a band added
            // later would compile clean here and simply paint nothing - an overlay silently missing
            // a layer, which is the one failure this class can produce that nothing else reports.
            // Throwing turns that into a crash on the first frame the new band is asked for.
            default -> throw new IllegalStateException("Unhandled map overlay band: " + band);
        }
    }

    // Everything that reads as an area, and so survives the nebula fog being drawn over it: the
    // territories themselves, the hover feedback that traces them, and the debug overlays that
    // replace or annotate them. They travel together because they are one picture - a highlight
    // lifted clear of the fill it brightens would light nothing, and a border trace read against a
    // fill it no longer sits on top of.
    private void renderTerritoryBand(PoliticalMapCache cache, float factor, float alphaMult) {

        // Swap production and debug base render on which view the cache built: the debug overlay
        // replaces the normal render, and the cache built exactly one of the two.
        if (cache.isDebug()) {
            ClusterBorderStageRenderer.renderOnMap(
                cache.getBorderStageOverlay(),
                factor,
                alphaMult);
        } else {
            ClusterRenderer.renderOnMap(
                cache.getTerritories(),
                factor,
                alphaMult);

            // Over the territories it lights up, so the halo reads off the frontier it traces
            // and the wash brightens the fill beneath it rather than being painted over. Only
            // under the production view: the debug overlay replaced the draw lists the highlight
            // would trace, and the hover has nothing to resolve against. The frame's draw lists
            // are wrapped as the highlight's source, so the framework's pass asks this layer what
            // the cursor is on rather than reading the political model itself.
            //
            // Gated on the effects switches alone, though the hover it reads may have been
            // published for the box's sake: with only the tooltip on, the cursor is still resolved
            // every frame and nothing may be painted over the map for it.
            if (PoliticalMapHoverGates.isHoverEffectsEnabled()) {
                hoverHighlightRenderer.renderOnMap(
                    new PoliticalMapHoverHighlightSource(cache.getTerritories()),
                    cache.getTerritories().getGlobalStyle().hoverHighlight(),
                    MapHoverState.getInstance().getHover(),
                    factor,
                    alphaMult);
            }
        }
        // The anchor overlay layers over whichever base view just drew - independent of the swap
        // above, so the two debug toggles compose. Gated on its own toggle here (not by the list
        // being empty): the placements are also built for the faction names, so the list can be
        // non-empty while the debug overlay is off.
        if (KmuPoliticalMapSettings.getPoliticalMapShowClusterAnchors()) {
            ClusterAnchorRenderer.renderOnMap(cache.getClusterAnchors(), factor, alphaMult);
        }
    }

    // Everything that has to be read rather than merely seen, and so cannot afford to sit under the
    // map's nebula fog: the per-cell presence bands, and the faction names over them. Both draw
    // - where a surface exists to put them there - over the map's nebulae, while still staying
    // beneath the vanilla star and constellation names the map draws after every terrain pass.
    //
    // The names go last of the map passes, so a name wins where it meets a band: a name says which
    // bloc a whole territory belongs to, which is the coarser statement of the two and the one a
    // player is reading the map for. Each is an empty-list check when its own feature is off - the
    // names when the name choice draws none, the bands on the cells where no rival is present.
    private void renderClearOfNebulaeBand(PoliticalMapCache cache, float factor, float alphaMult) {
        // The debug overlay replaced the draw lists the bands were baked into, so there is nothing
        // to paint them from - the same reason the hover feedback stands down under it.
        if (!cache.isDebug()) {

            // The sizes are read here rather than carried on the draw lists because only one of
            // them is asked at draw time - whether the bands are still thick enough to read at
            // this zoom - and that answer moves with the camera, not with the rebuild the rest of
            // the sizes were baked into. Read through the same seam the bake used, so the width
            // measured against the floor is the width the bands were laid at.
            CellPresenceRibbonRenderer.renderOnMap(
                cache.getTerritories().getRibbonByCellId().values(),
                RibbonStyleReader.readRibbonStyle(),
                factor,
                alphaMult);
        }
        LabelRenderer.renderOnMap(cache.getFactionLabels(), factor, alphaMult);
    }

    // One-shot diagnostic for the no-draw investigation. Guarded on isDebugEnabled so the once-flag
    // only trips when the line actually emits. Set KMU log verbosity to DEBUG in LunaLib to see it.
    private void logFirstRenderOnce(PoliticalMapCache cache, float factor, float alphaMult) {
        if (hasLoggedFirstRender || !LOG.isDebugEnabled()) {
            return;
        }
        hasLoggedFirstRender = true;
        
        // Report whichever view is live: the normal draw lists, or the debug overlay when it has
        // replaced them (territories is null in debug mode).
        var builtCounts = cache.isDebug()
            ? "debugBaseLoops=" + cache.getBorderStageOverlay().baseLoops().size()
            : "styledCells=" + cache.getTerritories().getStyledCellByCellId().size();

        LOG.debug("Political map render renderOnMap fired: "
            + builtCounts
            + " factor=" + factor
            + " alphaMult=" + alphaMult);
    }
}
