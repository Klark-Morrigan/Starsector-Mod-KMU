package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.input.UiCursor;
import kmlib.starsector.ui.map.MapSurfaceBounds;
import kmlib.starsector.ui.map.MapTabWidgetTrace;
import kmlib.starsector.ui.map.ModelviewMatrixReaders;

import kmu.maplayers.base.hover.MapHoverPublisher;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.maplayers.base.sidebar.runtime.SidebarHosts;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapHoverGates;

import org.apache.log4j.Logger;

import java.util.Optional;

/**
 * Draws the political map on the sector map as merged HOI4-style clusters, where adjacent
 * same-holder systems fuse into one solid national cluster. It is the political-map layer's whole
 * turn at a frame: the map surface hands it one while that tab is the active pick, and it draws
 * whichever view {@link PoliticalMapViewRegistry#getActiveView()} reports, or nothing when none is
 * active - the tab is open but every view is deselected.
 *
 * <p>Resolving the view here rather than at the surface is what keeps the framework out of the
 * feature's business: which of the political views draws is the political map's own question, and
 * the surface learns only that some layer wants the frame. The hover box the layer shows for one
 * system is resolved the same way, off the same view read, so the framework's tooltip dispatcher names
 * no view either.
 *
 * <p>Named for the layer, not for the overlay: this sequences a frame, while
 * {@link PoliticalMapOverlayRenderer} one level down composes the overlay's own sub-layers. It
 * sequences three collaborators per frame - it asks its {@link PoliticalMapCache} to bring the
 * cached draw lists up to date, has its {@link MapHoverPublisher} resolve what the cursor
 * is over, and hands the draw lists to the overlay renderer. All the real work - keeping the draw
 * lists fresh with the least work per frame, reading the cursor, and composing the overlay layers -
 * lives in those three.
 */
public final class PoliticalMapLayerRenderer implements MapLayerRenderer {

    /** The one shared instance; the political-map layer hands it to the map surface as its renderer. */
    public static final PoliticalMapLayerRenderer INSTANCE = new PoliticalMapLayerRenderer();

    private static final Logger LOG = Global.getLogger(PoliticalMapLayerRenderer.class);

    // The freshness cache and the overlay compositor this renderer delegates to. Plain final fields:
    // a layer renderer is reached through a registered layer, so it lives for the session and never
    // enters a save, and neither collaborator needs the transient marking or lazy rebuild a
    // save-serialised holder would. The cache holds one sector's derived state and is emptied per
    // load rather than replaced; see discardStateFromPreviousSave.
    private final PoliticalMapCache cache = new PoliticalMapCache();
    private final PoliticalMapOverlayRenderer overlayRenderer = new PoliticalMapOverlayRenderer();

    // The last widget-trace line logged, so a resting cursor reports once rather than every frame.
    // Held here rather than in the trace because the trace only describes; deciding how often this
    // layer repeats itself is this layer's business.
    private String lastLoggedWidgetTrace;

    // The cursor read, created on the first frame the hover toggle is on. Deferred because the
    // matrix binding it holds is chosen from the renderer in force, which can only be read from a
    // running game - and because a player who leaves the hover off never needs one at all.
    private MapHoverPublisher hoverPublisher;

    private PoliticalMapLayerRenderer() {
    }

    /**
     * Drops everything derived from the sector being left, so the next frame rebuilds against the
     * sector just loaded. Call once per game load.
     *
     * <p>This renderer outlives any one save - it is reached through a registered layer, and a player
     * can load a second save without restarting - while its cache is only meaningful for the sector
     * it was built from. Nothing else would catch the difference: the staleness counters are
     * process-wide and do not move across a load, and the geometry cache reconciles by diffing system
     * <em>ids</em>, so a system present in both saves at a different position is not seen to have
     * changed. Without this the previous save's territories would paint over the new sector and stay
     * until the player happened to trip a rebuild.
     */
    public void discardStateFromPreviousSave() {
        cache.discardCachedState();
        // The hover names a system id from the sector being left, so it is parked rather than left
        // to light a cell - or float a tooltip - that the new sector may not even contain.
        MapHoverState.getInstance().clearHover();
    }

    @Override
    public void renderOnMap(float factor, float alphaMult) {
        // Draws whichever political-map view is active, or nothing when none is - the tab is open
        // with every view deselected. Gating the whole draw (and its refresh) on one view read keeps
        // a dark overlay near-free per frame, and reading the view - not a named faction gate - is
        // what lets any registered view draw here.
        var view = PoliticalMapViewRegistry.getActiveView();
        if (view == null) {
            return;
        }
        cache.refresh(view);
        publishHoverIfAnyFeedbackNeedsIt(factor);
        overlayRenderer.renderOnMap(cache, factor, alphaMult);
    }

    @Override
    public Optional<MapHoverTooltip> resolveHoverTooltip() {
        // This layer's own tooltip switch, off means no box from it - and the framework draws
        // whatever the other layers offer regardless, which is the point of scoping it here rather
        // than at the dispatcher.
        if (!PoliticalMapHoverGates.isHoverTooltipEnabled()) {
            return Optional.empty();
        }
        // The hover box is resolved through the active view for the same reason the paint is: which
        // view is up decides what there is to say about a system - the faction and alliance views show
        // the domination breakdown, the claims view none - and the dispatcher above learns only that
        // this layer has a box, or has not.
        var view = PoliticalMapViewRegistry.getActiveView();
        if (view == null) {
            return Optional.empty();
        }
        return view.resolveHoverTooltip();
    }

    // Runs the cursor read only while some hover feedback still wants the answer - either the halo
    // and wash or the hover box. The whole read - the map-matrix read (bridged, and a per-frame
    // render-thread hop under Fast Rendering), the unproject, and the cell hit test - hangs off this
    // call, so gating it here is what makes the switches real off switches rather than ones that
    // draw nothing while still paying to resolve the hover every frame. It is the union of the two
    // kinds rather than the effects alone because the box needs the same hovered cell the halo does.
    // With both off the hover is parked so nothing downstream keeps a stale cell lit, and the
    // publisher (and the renderer binding it holds) is never created.
    private void publishHoverIfAnyFeedbackNeedsIt(float factor) {
        traceVanillaWidgetsUnderCursor();

        if (!PoliticalMapHoverGates.isCursorReadNeeded()) {
            MapHoverState.getInstance().clearHover();
            return;
        }
        // The sidebar is drawn over the map, so a cursor on it is not hovering the territory
        // beneath. Park the hover so the panel neither lights a cell under it nor floats a tooltip
        // over it.
        if (isCursorOverSidebar()) {
            MapHoverState.getInstance().clearHover();
            return;
        }
        // The map's own chrome is drawn over it for the same reason, and the hover is equally blind
        // to it: it resolves a cell from map geometry, which has no notion of the tab strip and
        // control bar composited on top. Tested second because it costs a read into the live widget
        // tree, while the sidebar test above is arithmetic over a box KMU already holds.
        if (isCursorOverVanillaMapChrome()) {
            MapHoverState.getInstance().clearHover();
            return;
        }
        if (hoverPublisher == null) {
            hoverPublisher = new MapHoverPublisher(ModelviewMatrixReaders.selectForActiveRenderer());
        }
        // The cursor read sits between the refresh and the draw: after, so it tests against the
        // shapes this frame actually paints, and before, so the highlight layers already have the
        // frame's answer when they draw. It is the one point in the frame with both the live GL
        // matrices it needs and the current draw lists. The draw lists are handed over as the
        // hover targets they satisfy - null when nothing was painted, which the publisher parks on.
        hoverPublisher.publishHoverFrom(cache.getTerritories(), factor);
    }

    // Diagnostic only, and silent unless KMU's log verbosity is DEBUG: names the vanilla widgets the
    // cursor is inside. The hover published below comes from the map's own geometry and knows
    // nothing of the chrome laid over it, so a cursor on the map's tab strip still resolves the cell
    // underneath and lights it. Fixing that needs to know which of the tab's widgets is the map and
    // which are chrome, which is a fact about the live tree rather than something derivable.
    //
    // Logged here rather than in the library that reads it: the line is about this layer's problem
    // and belongs under this mod's own verbosity, which a logger named after a library class would
    // sit outside of. Reported only when the answer changes, so a resting cursor costs one line.
    private void traceVanillaWidgetsUnderCursor() {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        var widgetsUnderCursor = MapTabWidgetTrace.describeWidgetsUnderCursor();
        if (widgetsUnderCursor != null && !widgetsUnderCursor.equals(lastLoggedWidgetTrace)) {
            lastLoggedWidgetTrace = widgetsUnderCursor;
            LOG.debug("Map-tab widget trace: " + widgetsUnderCursor);
        }
    }

    // Whether the cursor sits over the map's own chrome - the tab strip above it, the control bar
    // below or across it - rather than over the map. The chrome is several small widgets whose
    // identities are a fact about one game build, while the map surface is one widget, so the rule
    // is stated about the surface: on the map means inside it and clear of everything drawn with it.
    //
    // Screen-blind, like the sidebar test above and for the same reason: the layer draws on the
    // sector map and on the intel screen's map visor through one terrain pass, so the map the cursor
    // is over is whichever the library reports on screen.
    //
    // Fails open. An unreadable widget tree, a screen showing no map at all, or a build this rule no
    // longer fits reads as "the cursor is on the map" - which merely restores the un-suppressed
    // behaviour rather than silencing every hover the layer has. A read taken to refine a feature
    // must not be able to switch it off. The absence is not silent: the surface read warns once when
    // it cannot answer for a map tab it did reach.
    private static boolean isCursorOverVanillaMapChrome() {
        var surfaceArea = MapSurfaceBounds.resolveSurfaceArea();
        return surfaceArea != null
            && !surfaceArea.containsPoint(UiCursor.getUiX(), UiCursor.getUiY());
    }

    // Whether the cursor sits over a map-layer sidebar, on whichever screen this frame is being drawn
    // for. Host-blind because this renderer has no way to be anything else: it is reached through a
    // hook that names no screen, and the layer draws on the sector map and on the intel screen's map
    // visor through the same terrain pass, so the bar the cursor is over is not always the on-map one.
    private static boolean isCursorOverSidebar() {
        return SidebarHosts.isPointOverAnySidebar(UiCursor.getUiX(), UiCursor.getUiY());
    }
}
