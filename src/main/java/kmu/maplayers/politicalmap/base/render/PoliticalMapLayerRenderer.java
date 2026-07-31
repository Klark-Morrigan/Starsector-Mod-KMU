package kmu.maplayers.politicalmap.base.render;

import kmlib.starsector.ui.input.UiCursor;
import kmlib.starsector.ui.map.ModelviewMatrixReaders;

import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.maplayers.base.sidebar.runtime.MapSidebarHost;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.settings.KmuLunaSettings;

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
 * cached draw lists up to date, has its {@link PoliticalMapHoverPublisher} resolve what the cursor
 * is over, and hands the draw lists to the overlay renderer. All the real work - keeping the draw
 * lists fresh with the least work per frame, reading the cursor, and composing the overlay layers -
 * lives in those three.
 */
public final class PoliticalMapLayerRenderer implements MapLayerRenderer {
    /** The one shared instance; the political-map layer hands it to the map surface as its renderer. */
    public static final PoliticalMapLayerRenderer INSTANCE = new PoliticalMapLayerRenderer();

    // The freshness cache and the overlay compositor this renderer delegates to. Plain final fields:
    // a layer renderer is reached through a registered layer, so it lives for the session and never
    // enters a save, and neither collaborator needs the transient marking or lazy rebuild a
    // save-serialised holder would. The cache holds one sector's derived state and is emptied per
    // load rather than replaced; see discardStateFromPreviousSave.
    private final PoliticalMapCache cache = new PoliticalMapCache();
    private final PoliticalMapOverlayRenderer overlayRenderer = new PoliticalMapOverlayRenderer();

    // The cursor read, created on the first frame the hover toggle is on. Deferred because the
    // matrix binding it holds is chosen from the renderer in force, which can only be read from a
    // running game - and because a player who leaves the hover off never needs one at all.
    private PoliticalMapHoverPublisher hoverPublisher;

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
        publishHoverIfEnabled(factor);
        overlayRenderer.renderOnMap(cache, factor, alphaMult);
    }

    @Override
    public Optional<MapHoverTooltip> resolveHoverTooltip() {
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

    // Runs the cursor read only while the hover highlight is switched on. The whole feature - the
    // map-matrix read (bridged, and a per-frame render-thread hop under Fast Rendering), the
    // unproject, and the cell hit test - hangs off this call, so gating it here is what makes the
    // toggle a real off switch rather than one that draws nothing while still paying to resolve the
    // hover every frame. When off, the hover is parked so nothing downstream keeps a stale cell lit,
    // and the publisher (and the renderer binding it holds) is never created.
    private void publishHoverIfEnabled(float factor) {
        if (!KmuLunaSettings.getPoliticalMapHoverEnabled()) {
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
        if (hoverPublisher == null) {
            hoverPublisher = new PoliticalMapHoverPublisher(
                    ModelviewMatrixReaders.selectForActiveRenderer());
        }
        // The cursor read sits between the refresh and the draw: after, so it tests against the
        // shapes this frame actually paints, and before, so the highlight layers already have the
        // frame's answer when they draw. It is the one point in the frame with both the live GL
        // matrices it needs and the current draw lists.
        hoverPublisher.publishHoverFrom(cache, factor);
    }

    // Whether the cursor sits over the map sidebar. Reads the same placement the sidebar draws and
    // hit-tests, so the hover parks over exactly the box the panel occupies; a null placement (the
    // bar is not on screen) is nothing to be over.
    private static boolean isCursorOverSidebar() {
        // The on-map sidebar is the one drawn on this screen, so the cursor test reads the on-map host's
        // own placement - the same host, controller, layer selection, and layout the render pass draws.
        var placement = MapSidebarHost.INSTANCE.resolvePlacement();
        if (placement == null) {
            return false;
        }
        var uiX = UiCursor.getUiX();
        var uiY = UiCursor.getUiY();
        // The collapse notch protrudes past the body's edge - when the panel is docked it is the
        // only part still on screen - so a cursor over it is still over the sidebar.
        return placement.body().box().containsPoint(uiX, uiY)
                || (placement.notch() != null && placement.notch().containsPoint(uiX, uiY));
    }
}
