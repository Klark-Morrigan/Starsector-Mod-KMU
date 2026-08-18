package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.coreui.CampaignScreenView;
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
import java.util.function.Supplier;

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
 *
 * <p>Everything <em>about</em> that read which does not turn on the pass is settled once, in the
 * preparation: whether any feedback still wants a hover, whether something is drawn over the cursor,
 * and what moment the frame before settled on. None of those changes between two passes of one
 * frame, and the dearest of them walks the live widget tree, so a pass asks only what its own
 * transform can answer.
 */
public final class PoliticalMapLayerRenderer implements MapLayerRenderer {

    // Above INSTANCE, and load-bearing there. Static initialisers run in the order they are written,
    // and constructing INSTANCE runs this class's field initialisers - two of which take this logger
    // and hold it. Declared below, they would each capture null, and the first line either tried to
    // write would take the frame down rather than say anything.
    private static final Logger LOG = Global.getLogger(PoliticalMapLayerRenderer.class);

    /**
     * The one shared instance; the political-map layer hands it to the map surface as its renderer.
     * This is where the live covers and the live cursor read are chosen, the renderer itself naming
     * only the reader and the source.
     */
    public static final PoliticalMapLayerRenderer INSTANCE = new PoliticalMapLayerRenderer(
        MapCoverReader.createForLiveScreen(),
        PoliticalMapLayerRenderer::buildLiveHoverPublisher);

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

    // Where the cursor read comes from when one is first wanted. A source rather than the read
    // itself because the matrix binding it holds is chosen from the renderer in force, which can
    // only be read from a running game - and because a player who leaves the hover off never needs
    // one at all. Handed in for the cover reader's reason: what a frame does with the read is this
    // class's business and answerable without a live map, while building one is not.
    private final Supplier<MapHoverPublisher> hoverPublisherSource;

    // Which terrain icons the map holds and in what order, reported when that order moves. This
    // layer rides on a terrain, so where its icon was seeded is what decides whether the map's own
    // nebula fog paints over the overlay or under it - an insertion-order artefact of the live
    // widget, not a contract, and one no published call reports. A build that starts seeding its
    // icons differently shows up here as a moved position rather than as a picture nobody can
    // account for.
    //
    // Reported under this mod's own verbosity rather than the library's, the line being about this
    // layer's problem: a logger named after a library class would sit outside the switch a player
    // reaches for when the map looks wrong.
    private final ChangedLineTrace iconOrderTrace =
        new ChangedLineTrace(LOG, "Map icon order", MapIconOrderTrace::describeTerrainIconOrder);

    // Which vanilla widgets the cursor is inside, reported when that changes. The hover comes from
    // the map's own geometry and knows nothing of the chrome laid over it, which is what the covers
    // answer for - and this is the line that says which widgets a build actually puts under the
    // cursor, so a cover that stops fitting a game build is diagnosed from the tree rather than
    // guessed at. Logged here for the reason above.
    private final ChangedLineTrace widgetsUnderCursorTrace = new ChangedLineTrace(
        LOG, "Map-tab widget trace", MapTabWidgetTrace::describeWidgetsUnderCursor);

    // The cursor read, taken from the source above on the first pass that wants one.
    private MapHoverPublisher hoverPublisher;

    // Whether this frame's passes are to read the cursor at all: some feedback still wants a hover,
    // and nothing is drawn over where the cursor rests. Settled once per frame because neither half
    // turns on which pass is running, and asking per pass would walk the live widget tree two or
    // three times over for one answer.
    private boolean isHoverWantedThisFrame;

    PoliticalMapLayerRenderer(
            MapCoverReader mapCoverReader,
            Supplier<MapHoverPublisher> hoverPublisherSource) {

        this.mapCoverReader = mapCoverReader;
        this.hoverPublisherSource = hoverPublisherSource;
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
            // The frame's passes stand down with it, or a deselected view would still pay for a
            // matrix read per pass.
            isHoverWantedThisFrame = false;
            return;
        }
        iconOrderTrace.traceWhenChanged();
        widgetsUnderCursorTrace.traceWhenChanged();
        announceArrivalOnTheFrameJustClosed();
        decideWhetherTheHoverIsWantedThisFrame();
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
     * Resolves the cursor for the pass now running, if the frame settled that a hover is wanted at
     * all.
     *
     * <p>Only what turns on the pass is left here. The whole read - the map-matrix read (bridged,
     * and a render-thread hop under Fast Rendering), the unproject, and the cell hit test - hangs
     * off the frame's decision, so a player with both kinds of feedback off pays for none of it, and
     * the publisher (with the renderer binding it holds) is never built.
     *
     * @param factor the per-vertex scale this pass applies, folding in its zoom
     */
    @Override
    public void publishHoverForPass(float factor) {

        if (!isHoverWantedThisFrame) {
            return;
        }
        if (hoverPublisher == null) {
            hoverPublisher = hoverPublisherSource.get();
        }
        // The cursor read sits between the frame's refresh and this pass's draw: after the refresh,
        // so it tests against the shapes the frame actually paints, and before the draw, so the
        // highlight layers have an answer when they emit. It is the one point in a pass with both
        // the live GL matrices it needs and the current draw lists. The draw lists are handed over
        // as the hover targets they satisfy - null when nothing was painted, which the publisher
        // parks on.
        hoverPublisher.publishHoverFrom(cache.getTerritories(), factor);
    }

    // Settles whether this frame's passes are to read the cursor, and parks the hover when they are
    // not so nothing downstream keeps a stale cell lit.
    //
    // Both halves are frame facts rather than pass facts - what the player has switched on, and
    // where the cursor rests relative to what is drawn over the map - and the covers are dear, the
    // last of them walking the live widget tree. Asked once, they cost one walk however many
    // surfaces paint.
    //
    // The read is wanted for either kind of feedback, the halo and wash or the hover box, because
    // the box needs the same hovered cell the halo does. The switches are asked first so a player
    // who wants neither pays for no cover read either.
    //
    // Package-private so the frame's decision is answerable without a live map: the covers answer
    // from the screen, while the refresh beside them needs a running sector.
    void decideWhetherTheHoverIsWantedThisFrame() {

        isHoverWantedThisFrame = PoliticalMapHoverGates.isCursorReadNeeded()
            && !mapCoverReader.isMapCoveredAtCursor();

        if (!isHoverWantedThisFrame) {
            MapHoverState.getInstance().clearHover();
        }
    }

    // The cursor read a running game gets, and what answers a cell reached under it: the binding is
    // chosen from the renderer in force, and what a moment sounds like belongs to whatever owns the
    // look - the publisher under them names only the moment. The cue is composed per arrival rather
    // than fixed now, so a level changed on the settings screen reaches a publisher built long
    // before it.
    //
    // Taken as a source rather than built at construction because both reads behind it need a game
    // that is running, and this renderer is created when the class loads.
    private static MapHoverPublisher buildLiveHoverPublisher() {

        var mapPresence = new MapPresence();
        var soundPlayer = new VanillaUiSoundPlayer();

        return new MapHoverPublisher(
            ModelviewMatrixReaders.selectForActiveRenderer(),
            () -> soundPlayer.playCueIfPresent(MapHoverCues.composeCellArrivalCue()),
            // Composed here rather than inside the publisher because the halves belong to different
            // ends: the settings are the framework's to state, while what counts as a vanilla map on
            // screen and whether the player is looking at the campaign itself are live reads.
            () -> MapHoverGates.isCursorLocatableOn(
                mapPresence::isAnyMapShowing,
                CampaignScreenView::isShowingGameSpace));
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
}
