package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;

import kmu.maplayers.base.hover.HoverHighlightRenderer;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.labels.LabelRenderer;
import kmu.maplayers.base.labels.anchor.ClusterAnchorRenderer;
import kmu.maplayers.base.render.MapFrame;
import kmu.maplayers.base.render.MapOverlayBand;
import kmu.maplayers.base.render.clusters.ClusterRenderer;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageRenderer;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapHoverGates;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapHoverHighlightSource;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapPreviewHighlightRenderer;
import kmu.maplayers.politicalmap.base.render.ribbon.CellPresenceRibbonRenderer;
import kmu.maplayers.politicalmap.base.render.ribbon.CellRibbonPathRenderer;
import kmu.settings.KmuPoliticalMapDiagnosticsSettings;

import org.apache.log4j.Logger;

/**
 * Composes the political map's map-overlay layers over the {@link PoliticalMapCache}'s current
 * draw lists, in one canonical order, bottom to top: the fills (or the debug border-tracing overlay
 * where it has replaced the base view), then the borders, then the picker preview, then the hover
 * highlight, then the cluster-anchor debug overlay, then the per-cell presence bands, then the
 * debug band-path overlay across them, then the faction names. That order is fixed here and is not
 * the player's - it is
 * what makes each sub-layer legible against the ones under it - and each layer still draws only
 * under its own toggle, the debug facilities composing rather than one hiding the other.
 *
 * <p>The stack is emitted one band at a time, because the map can put its own drawing between two
 * of the layers: the map holds its own nebula icons, drawn over the sector as a large blended
 * sprite under Starscape, so a sub-layer is either dimmed by that haze or clear of it. Which side
 * each lands on is the player's, read per pass as a {@link PoliticalMapBandLayout} and asked of it
 * per sub-layer; the surfaces above only say which band they are painting, and the geometry below
 * is emitted the same way whichever band asks for it.
 *
 * <p>Four sub-layers have no choice of their own and ride with one that does, because each is only
 * a picture in the company of what it is drawn against. The contested hatch is half of a cluster's
 * fill and travels inside it. The hover halo and cell wash brighten the fill under the cursor, and
 * the picker preview brightens the fills of a whole bloc, so both follow the fills - left beneath
 * while the fills went above, they would be painted over and light nothing. The cluster anchors and
 * the border-tracing overlay annotate or replace the base view, so they follow it, the tracing
 * overlay having no split of its own to honour: it replaces fills and borders together in one pass.
 */
final class PoliticalMapOverlayRenderer {
    private static final Logger LOG = Global.getLogger(PoliticalMapOverlayRenderer.class);

    // Held rather than called statically: the highlight retains the geometry it resolved for the
    // hovered cell, so a cursor resting on one cell resolves it once instead of every frame.
    private final HoverHighlightRenderer hoverHighlightRenderer = new HoverHighlightRenderer();

    // What the cursor is over on this compositor's own sector, handed over at construction. The
    // highlight lights a cell the cache's draw lists named, so the hover it reads has to be the one
    // the pass over those same draw lists published - a hover taken from whichever sector is running
    // would wash a cell this frame never cut.
    private final MapHoverState hoverState;

    // The sidebar picker's preview over this compositor's own sector, handed over for the reason
    // the hover holder is: it lights systems the cache's draw lists named, off a row hovered on the
    // sidebar this map is drawn under.
    private final PoliticalMapPreviewHighlightRenderer previewHighlightRenderer;

    // Diagnostic: ensures the first map render logs exactly once, for the no-draw investigation.
    private boolean hasLoggedFirstRender;

    /**
     * @param hoverState               the hover holder of the sector whose draw lists this
     *                                 compositor paints, from that sector's installed machinery
     * @param previewHighlightRenderer the picker preview over that same sector's machinery
     */
    PoliticalMapOverlayRenderer(
            MapHoverState hoverState,
            PoliticalMapPreviewHighlightRenderer previewHighlightRenderer) {

        this.hoverState = hoverState;
        this.previewHighlightRenderer = previewHighlightRenderer;
    }

    /**
     * Paints one band of the overlay for one map frame from the cache's current draw lists, in the
     * map's below-UI pass so every band stays beneath the vanilla star and constellation names.
     */
    public void renderOnMap(
            PoliticalMapCache cache,
            MapFrame mapFrame,
            MapOverlayBand band) {

        logFirstRenderOnce(cache, mapFrame);

        var layout = PoliticalMapBandLayout.readChosenLayout();

        // The canonical stack, bottom to top, each entry gated on whether the band it was placed in
        // is the one being painted. Written out in order in one pass rather than one method per
        // band, because the order between the sub-layers is what this class settles and the split
        // between the bands is no longer settled here at all - a pair of band methods would have to
        // state the order twice and could state it two ways.
        var isPaintingFills = layout.fillBand() == band;
        var isPaintingBorders = layout.borderBand() == band;

        // Swap production and debug base render on which view the cache built: the debug overlay
        // replaces the normal render, and the cache built exactly one of the two.
        if (cache.isDebug()) {
            if (isPaintingFills) {
                ClusterBorderStageRenderer.renderOnMap(
                    cache.getBorderStageOverlay(),
                    mapFrame);
            }
        } else {
            if (isPaintingFills) {
                ClusterRenderer.renderFillsOnMap(
                    cache.getTerritories(),
                    mapFrame);
            }

            // After the fills wherever the two meet, which the layout guarantees: the borders can
            // be lifted clear of a fogged fill, but never sunk under their own fill.
            if (isPaintingBorders) {
                ClusterRenderer.renderBordersOnMap(
                    cache.getTerritories(),
                    mapFrame);
            }

            // The sidebar picker's preview, first of the two highlights and gated by neither hover
            // switch: it answers a pointer on the sidebar rather than one on the map, so a player
            // who has switched the map's own cursor feedback off still sees what a row would
            // spotlight. Its own style tier is where it is turned down to nothing.
            if (isPaintingFills) {
                previewHighlightRenderer.renderPreviewOnMap(
                    cache.getTerritories(),
                    mapFrame);
            }

            // Only under the production view: the debug overlay replaced the draw lists the
            // highlight would trace, and the hover has nothing to resolve against.
            //
            // Gated on the effects switches alone, though the hover it reads may have been
            // published for the box's sake: with only the tooltip on, the cursor is still resolved
            // every frame and nothing may be painted over the map for it.
            if (isPaintingFills && PoliticalMapHoverGates.isHoverEffectsEnabled()) {
                renderHoverHighlight(cache, mapFrame);
            }
        }

        // The anchor overlay layers over whichever base view just drew - independent of the swap
        // above, so the two debug toggles compose. Gated on its own toggle here (not by the list
        // being empty): the placements are also built for the faction names, so the list can be
        // non-empty while the debug overlay is off.
        if (isPaintingFills && KmuPoliticalMapDiagnosticsSettings.getPoliticalMapShowClusterAnchors()) {
            ClusterAnchorRenderer.renderOnMap(cache.getClusterAnchors(), mapFrame);
        }

        // The debug overlay replaced the draw lists the bands were baked into, so there is nothing
        // to paint them from - the same reason the hover feedback stands down under it.
        if (layout.ribbonBand() == band && !cache.isDebug()) {
            renderPresenceBands(cache, mapFrame);
        }

        // Last of the stack, so a name wins where it meets a band: a name says which bloc a whole
        // territory belongs to, which is the coarser statement of the two and the one a player is
        // reading the map for. An empty-list check when the name choice draws none.
        if (layout.labelBand() == band) {
            LabelRenderer.renderOnMap(cache.getFactionLabels(), mapFrame);
        }
    }

    // Over the territories it lights up, so the halo reads off the frontier it traces and the wash
    // brightens the fill beneath it rather than being painted over. The frame's draw lists are
    // wrapped as the highlight's source, so the framework's pass asks this layer what the cursor is
    // on rather than reading the political model itself.
    private void renderHoverHighlight(PoliticalMapCache cache, MapFrame mapFrame) {
        hoverHighlightRenderer.renderCursorHighlightOnMap(
            new PoliticalMapHoverHighlightSource(cache.getTerritories()),
            cache.getTerritories().getGlobalStyle().hoverHighlight(),
            hoverState.getHover(),
            mapFrame);
    }

    // The per-cell presence bands, and the debug overlay explaining them straight after: the path
    // draws over the band it explains, so a band and the ring it was laid on read against each
    // other. The overlay is gated by the paths themselves rather than by a settings read: the bake
    // holds a path only while the player has it on, so an off toggle arrives as an empty map. The
    // bands are gated the same way - a cell where no rival is present bakes none.
    private static void renderPresenceBands(
            PoliticalMapCache cache,
            MapFrame mapFrame) {

        CellPresenceRibbonRenderer.renderOnMap(
            cache.getTerritories().getPaintedCells().getRibbonByCellKey().values(),
            mapFrame);

        CellRibbonPathRenderer.renderOnMap(
            cache.getTerritories().getPaintedCells().getRibbonPathByCellKey().values(),
            mapFrame);
    }

    // One-shot diagnostic for the no-draw investigation. Guarded on isDebugEnabled so the once-flag
    // only trips when the line actually emits. Set KMU log verbosity to DEBUG in LunaLib to see it.
    private void logFirstRenderOnce(PoliticalMapCache cache, MapFrame mapFrame) {
        if (hasLoggedFirstRender || !LOG.isDebugEnabled()) {
            return;
        }
        hasLoggedFirstRender = true;

        // Report whichever view is live: the normal draw lists, or the debug overlay when it has
        // replaced them (territories is null in debug mode).
        var builtCounts = cache.isDebug()
            ? "debugBaseLoops=" + cache.getBorderStageOverlay().baseLoops().size()
            : "styledCells=" + cache.getTerritories().getStyledCellByCellKey().size();

        LOG.debug("Political map render renderOnMap fired: "
            + builtCounts
            + " factor=" + mapFrame.factor()
            + " alphaMult=" + mapFrame.alphaMult());
    }
}
