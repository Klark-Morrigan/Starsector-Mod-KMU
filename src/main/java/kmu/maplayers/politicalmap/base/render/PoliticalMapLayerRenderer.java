package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.map.presence.MapPresence;
import kmlib.starsector.ui.map.probes.MapIconOrderTrace;
import kmlib.starsector.ui.map.probes.MapTabWidgetTrace;
import kmlib.starsector.ui.map.transform.ModelviewMatrixReaders;
import kmlib.starsector.ui.sound.VanillaUiSoundPlayer;

import kmu.maplayers.base.hover.MapHoverCues;
import kmu.maplayers.base.hover.MapHoverGates;
import kmu.maplayers.base.hover.MapHoverPublisher;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.hover.cover.MapCoverReader;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.maplayers.base.render.MapOverlayBand;
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
 *
 * <p>Those three run at the three rates a frame has to offer, which is what lets one frame be
 * painted by more than one surface: under Starscape the map draws its own nebulae between two of
 * this layer's bands, so the bands are emitted from separate terrain passes. The refresh runs once
 * per frame in {@code prepareFrame}, the cursor read once per pass in {@code publishHoverForPass} -
 * each pass binding a transform of its own, and the last of them owning the answer - and the paint
 * per band in {@code renderOnMap}.
 */
public final class PoliticalMapLayerRenderer implements MapLayerRenderer {

    /**
     * The one shared instance; the political-map layer hands it to the map surface as its renderer.
     * This is where the live covers are chosen, the renderer itself naming only the reader.
     */
    public static final PoliticalMapLayerRenderer INSTANCE =
        new PoliticalMapLayerRenderer(MapCoverReader.createForLiveScreen(), new MapPresence());

    private static final Logger LOG = Global.getLogger(PoliticalMapLayerRenderer.class);

    // The freshness cache and the overlay compositor this renderer delegates to. Plain final fields:
    // a layer renderer is reached through a registered layer, so it lives for the session and never
    // enters a save, and neither collaborator needs the transient marking or lazy rebuild a
    // save-serialised holder would. The cache holds one sector's derived state and is emptied per
    // load rather than replaced; see discardStateFromPreviousSave.
    private final PoliticalMapCache cache = new PoliticalMapCache();
    private final PoliticalMapOverlayRenderer overlayRenderer = new PoliticalMapOverlayRenderer();

    // Whether anything is drawn over the map where the cursor rests. Handed in rather than composed
    // here, so this layer neither names the things that can cover a map nor holds a set another
    // layer could be given differently.
    private final MapCoverReader mapCoverReader;

    // Whether a vanilla map host is on screen, for the hover's own question of whether the pass now
    // running is one the cursor can be located against. Held rather than resolved per frame, the
    // binding behind it being fixed for the session; handed in for the cover reader's reason, so a
    // test can answer it without a live widget tree.
    private final MapPresence mapPresence;

    // The last widget-trace line logged, so a resting cursor reports once rather than every frame.
    // Held here rather than in the trace because the trace only describes; deciding how often this
    // layer repeats itself is this layer's business.
    private String lastLoggedWidgetTrace;

    // The last icon-order line logged, for the same reason and on the same terms: the order moves
    // only when a map is opened or an entity is reseated, so an unchanging map reports once.
    private String lastLoggedIconOrder;

    // The cursor read, created on the first frame the hover toggle is on. Deferred because the
    // matrix binding it holds is chosen from the renderer in force, which can only be read from a
    // running game - and because a player who leaves the hover off never needs one at all.
    private MapHoverPublisher hoverPublisher;

    PoliticalMapLayerRenderer(MapCoverReader mapCoverReader, MapPresence mapPresence) {
        this.mapCoverReader = mapCoverReader;
        this.mapPresence = mapPresence;
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
    public void prepareFrame(float factor) {
        // Stands down when no political-map view is active - the tab is open with every view
        // deselected. Gating the refresh on one view read keeps a dark overlay near-free per frame,
        // and reading the view - not a named faction gate - is what lets any registered view draw
        // here.
        var view = PoliticalMapViewRegistry.getActiveView();
        if (view == null) {
            return;
        }
        traceMapIconOrder();
        announceArrivalOnTheFrameJustClosed();
        cache.refresh(view);
    }

    @Override
    public void renderOnMap(float factor, float alphaMult, MapOverlayBand band) {
        // The same view read the preparation above stands down on, repeated rather than remembered:
        // it is a registry lookup, and a field holding the frame's answer would be render state on a
        // renderer that deliberately holds none.
        var view = PoliticalMapViewRegistry.getActiveView();
        if (view == null) {
            return;
        }
        overlayRenderer.renderOnMap(cache, factor, alphaMult, band);
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

    /**
     * Resolves the cursor for the pass now running, while some hover feedback still wants the
     * answer - either the halo and wash or the hover box.
     *
     * <p>The whole read - the map-matrix read (bridged, and a render-thread hop under Fast
     * Rendering), the unproject, and the cell hit test - hangs off this call, so gating it here is
     * what makes the switches real off switches rather than ones that draw nothing while still
     * paying to resolve the hover every frame. It is the union of the two kinds rather than the
     * effects alone because the box needs the same hovered cell the halo does. With both off the
     * hover is parked so nothing downstream keeps a stale cell lit, and the publisher (and the
     * renderer binding it holds) is never created.
     *
     * @param factor the per-vertex scale this pass applies, folding in its zoom
     */
    @Override
    public void publishHoverForPass(float factor) {
        // The same view read the preparation stands down on. Repeated here because this runs on
        // every pass rather than under the frame's claim, so a deselected view has to cost each of
        // them nothing rather than only the first.
        if (PoliticalMapViewRegistry.getActiveView() == null) {
            return;
        }
        traceVanillaWidgetsUnderCursor();

        if (!PoliticalMapHoverGates.isCursorReadNeeded()) {
            MapHoverState.getInstance().clearHover();
            return;
        }
        // Something drawn over the map takes the cursor with it, and the hover is blind to all of
        // it: it resolves a cell from map geometry, which has no notion of what is composited on
        // top. Park so nothing under a cover is lit or described.
        if (mapCoverReader.isMapCoveredAtCursor()) {
            MapHoverState.getInstance().clearHover();
            return;
        }
        if (hoverPublisher == null) {
            hoverPublisher = buildHoverPublisher();
        }
        // The cursor read sits between the frame's refresh and this pass's draw: after the refresh,
        // so it tests against the shapes the frame actually paints, and before the draw, so the
        // highlight layers have an answer when they emit. It is the one point in a pass with both
        // the live GL matrices it needs and the current draw lists. The draw lists are handed over
        // as the hover targets they satisfy - null when nothing was painted, which the publisher
        // parks on.
        hoverPublisher.publishHoverFrom(cache.getTerritories(), factor);
    }

    // Answers the moment the frame just closed settled on, that frame's last pass having had the
    // final say on where the cursor was. Nothing is owed before the first pass has ever read, which
    // is also the only state the publisher can be absent in.
    //
    // Driven from the preparation rather than from a pass because the arrival latch behind it must
    // be stepped once a frame: several surfaces paint one frame, and one of them may be a foreign
    // map's, so a latch stepped per pass would report the cursor crossing between two transforms'
    // answers on every frame it rests still.
    private void announceArrivalOnTheFrameJustClosed() {

        if (hoverPublisher == null) {
            return;
        }
        hoverPublisher.announceSettledArrival();
    }

    // The cursor read and what answers a cell reached under it, composed together because both are
    // this host's to name: the binding is chosen from the renderer in force, and what a moment
    // sounds like belongs to whatever owns the look - the publisher under them names only the
    // moment. The cue is composed per arrival rather than fixed now, so a level changed on the
    // settings screen reaches a publisher built long before it.
    private MapHoverPublisher buildHoverPublisher() {

        var soundPlayer = new VanillaUiSoundPlayer();

        return new MapHoverPublisher(
            ModelviewMatrixReaders.selectForActiveRenderer(),
            () -> soundPlayer.playCueIfPresent(MapHoverCues.composeCellArrivalCue()),
            // Composed here rather than inside the publisher because the two halves belong to
            // different ends: the setting is the framework's to state, and what counts as a
            // vanilla map on screen is the live read this renderer already holds a binding for.
            () -> MapHoverGates.isCursorLocatableOn(mapPresence.isAnyMapShowing()));
    }

    // Diagnostic only, and silent unless KMU's log verbosity is DEBUG: names the vanilla widgets the
    // cursor is inside. The hover comes from the map's own geometry and knows nothing of the chrome
    // laid over it, which is what the covers answer for - and this is the line that says which
    // widgets a build actually puts under the cursor, so a cover that stops fitting a game build is
    // diagnosed from the tree rather than guessed at.
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

    // Diagnostic only, and silent unless KMU's log verbosity is DEBUG: names the terrain icons the
    // map widget holds, in the order it will draw them. This layer rides on a terrain, so where its
    // icon was seeded is what decides whether the map's own nebula fog paints over the overlay
    // or under it - an insertion-order artefact of the live widget, not a contract, and one no
    // published call reports. A build that starts seeding its icons differently shows up here as a
    // moved position rather than as a picture nobody can account for.
    //
    // Read on the frames this layer actually paints, since the order only means anything while
    // there is something of ours in it to be buried. Logged here rather than in the library that
    // reads it, on the same terms as the widget trace above: the line answers to this mod's own
    // verbosity, and is repeated only when the order changes.
    private void traceMapIconOrder() {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        var iconOrder = MapIconOrderTrace.describeTerrainIconOrder();
        if (iconOrder != null && !iconOrder.equals(lastLoggedIconOrder)) {
            lastLoggedIconOrder = iconOrder;
            LOG.debug("Map icon order: " + iconOrder);
        }
    }

}
