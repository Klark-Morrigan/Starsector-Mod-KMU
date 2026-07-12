package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;

import kmu.maplayers.politicalmap.base.render.debug.PoliticalMapStaticDebugRenderer;
import kmu.maplayers.politicalmap.base.render.labels.ClusterAnchorRenderer;
import kmu.maplayers.politicalmap.base.render.labels.LabelRenderer;
import kmu.maplayers.politicalmap.base.render.territories.TerritoryRenderer;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;

/**
 * Composes the political map's map-overlay layers over the {@link PoliticalMapCache}'s current
 * draw lists, bottom to top: the base view (the normal territories, or the debug border-tracing
 * overlay when it has replaced them), then the cluster-anchor debug overlay, then the faction
 * names. The two debug facilities compose rather than one hiding the other - the anchor overlay
 * layers over whichever base view drew - and each layer draws only under its own toggle. The
 * plugin holds one of these behind a transient field and delegates its {@code renderOnMap} here,
 * so the terrain adapter stays a thin surface over the render sequencing.
 */
final class PoliticalMapOverlayRenderer {
    private static final Logger LOG = Global.getLogger(PoliticalMapOverlayRenderer.class);

    // Diagnostic: ensures the first map render logs exactly once, for the no-draw investigation.
    private boolean hasLoggedFirstRender;

    /**
     * Paints the whole overlay for one map frame from the cache's current draw lists, in the map's
     * below-UI pass so the territory and bands stay beneath the vanilla star and constellation
     * names.
     */
    public void renderOnMap(PoliticalMapCache cache, float factor, float alphaMult) {
        logFirstRenderOnce(cache, factor, alphaMult);
        // Swap production and debug base render on which view the cache built: the debug overlay
        // replaces the normal render, and the cache built exactly one of the two.
        if (cache.isDebug()) {
            PoliticalMapStaticDebugRenderer.renderOnMap(cache.getDebugTerritories(), factor, alphaMult);
        } else {
            TerritoryRenderer.renderOnMap(cache.getTerritories(), factor, alphaMult);
        }
        // The anchor overlay layers over whichever base view just drew - independent of the swap
        // above, so the two debug toggles compose. Gated on its own toggle here (not by the list
        // being empty): the placements are also built for the faction names, so the list can be
        // non-empty while the debug overlay is off.
        if (KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            ClusterAnchorRenderer.renderOnMap(cache.getClusterAnchors(), factor, alphaMult);
        }
        // The faction names draw last of the map passes, so a name reads over its territory and the
        // debug band, but still beneath the vanilla star and constellation names (drawn after every
        // terrain renderOnMap). The list is empty unless the names toggle is on, so this is an
        // empty-list check when they are off.
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
                ? "debugBaseLoops=" + cache.getDebugTerritories().baseLoops().size()
                : "styledCells=" + cache.getTerritories().getStyledCellBySystemId().size();
        LOG.debug("Political map render renderOnMap fired: " + builtCounts
                + " factor=" + factor + " alphaMult=" + alphaMult);
    }
}
