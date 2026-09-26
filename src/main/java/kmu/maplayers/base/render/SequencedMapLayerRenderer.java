package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;

import kmlib.logging.ChangedLineTrace;
import kmlib.starsector.compatibility.CompatibilityConsumer;
import kmlib.starsector.ui.map.probes.MapIconOrderTrace;
import kmlib.starsector.ui.map.probes.MapTabWidgetTrace;
import kmlib.starsector.ui.map.transform.ModelviewMatrixReaders;
import kmlib.starsector.ui.sound.VanillaUiSoundPlayer;

import kmu.KmuMod;
import kmu.maplayers.base.hover.MapHoverCues;
import kmu.maplayers.base.hover.MapHoverPermission;
import kmu.maplayers.base.hover.MapHoverPublisher;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.hover.cover.MapCoverReader;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.settings.KmuLoggingSettings;
import kmu.util.KmuStringKeys;

import org.apache.log4j.Logger;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The frame every painting layer runs, performed over the {@link MapLayerFrameParts} the layer
 * supplies. It is a layer's whole turn at a frame: the map surface hands it one while that layer's
 * tab is the active pick, and it asks the layer's parts what to draw, draws it, and resolves the
 * cursor against it - or stands the whole frame down when the layer has nothing selected to paint.
 *
 * <p>The sequence is the framework's rather than any layer's because it is the same for every layer
 * and is only ever wrong in play. A layer drawing a graph performs the same beats in the same order
 * as one drawing owner-painted cells, and one written against the raw {@link MapLayerRenderer} seam
 * would get these wrong the same ways:
 *
 * <ul>
 *   <li>the cover read is settled once per frame, because its last cover walks the live widget tree;</li>
 *   <li>the arrival latch is stepped once per frame, because several surfaces paint one frame;</li>
 *   <li>the cursor read is taken per pass, because each pass binds a transform of its own and the
 *       last of them owns the answer;</li>
 *   <li>a frame that stands down parks the hover, rather than leaving the last one standing.</li>
 * </ul>
 *
 * <p>Those run at the three rates a frame has to offer, which is what lets one frame be painted by
 * more than one surface: under Starscape the map draws its own nebulae between two of a layer's bands,
 * so the bands are emitted from separate terrain passes. The refresh runs once per frame in
 * {@code prepareFrame}, the cursor read once per pass in {@code publishHoverForPass}, and the paint
 * per band in {@code renderOnMap}. Everything <em>about</em> the read which does not turn on the pass
 * is settled once, in the preparation: whether any feedback still wants a hover, whether something is
 * drawn over the cursor, and what moment the frame before settled on.
 *
 * <p>Each of those beats is also where a frame's profiling rows begin, opened through the
 * {@link MapFrameBeats} this renderer was made with. The stand-down reads sit between a beat and the
 * layer's row inside it, so a frame with nothing to draw still reports what standing down cost and
 * opens no row for a layer that never ran.
 *
 * @param <S> what a frame is painted under, as the layer's stand-down read finds it
 */
public final class SequencedMapLayerRenderer<S> implements MapLayerRenderer {

    private static final Logger LOG = Global.getLogger(SequencedMapLayerRenderer.class);

    // The identity the framework's cursor binding is recorded under when it stops holding. Stable
    // for the session: it is what keeps a failure of this binding reported once however many times
    // it is taken, and reported apart from another mod's over the same renderer.
    //
    // The feature alone, not the whole key: the library composes that from our mod ID and this, so
    // a key two mods spell alike cannot happen. Such a collision is one nothing can see - the record
    // reads a pair it already holds and ignores it, exactly as it does for the same mod recording
    // twice - so the second mod's players would be told what the first mod lost and nothing would
    // say so. What is spelled here names which of this mod's bindings broke, not which mod broke.
    private static final String MAP_CURSOR_FEATURE_KEY = "map-cursor";

    // What the layer supplies. Held whole rather than taken apart into fields, so the five read as
    // one layer's answers rather than as five collaborators the sequence chose.
    private final MapLayerFrameParts<S> parts;

    // Which screen is showing, read once per beat. A source rather than a static read, so the
    // sequence names the screens it reads through without being the one that decides how.
    private final Supplier<ScreenLayerPicks> liveScreenSource;

    // What the cursor is over on this renderer's own sector. Held rather than resolved at each park
    // because the frame it is parked on is this sector's frame: a park taken against the running
    // game would leave the cell this sector had lit standing while clearing another sector's.
    private final MapHoverState hoverState;

    // Where this frame's profiling rows are opened - this sector's group, and this layer's row
    // inside each beat. Composed once and held, both halves being answers that do not change while
    // a sector is loaded.
    private final MapFrameBeats frameBeats;

    // Whether anything is drawn over the map where the cursor rests. Handed in rather than composed
    // here, so the sequence neither names the things that can cover a map nor holds a set another
    // layer could be given differently.
    private final MapCoverReader mapCoverReader;

    // Where the cursor read comes from when one is first wanted. A source rather than the read
    // itself because the matrix binding it holds is chosen from the renderer in force, which can
    // only be read from a running game - and because a player who leaves the hover off never needs
    // one at all.
    //
    // It takes the hover holder rather than closing over one, so the publisher it builds and the
    // park above cannot name two different sectors' hovers.
    private final Function<MapHoverState, MapHoverPublisher> hoverPublisherSource;

    // Which terrain icons the map holds and in what order, reported when that order moves. A layer
    // rides on a terrain, so where its icon was seeded is what decides whether the map's own nebula
    // fog paints over the overlay or under it - an insertion-order artefact of the live widget, not
    // a contract, and one no published call reports. A build that starts seeding its icons
    // differently shows up here as a moved position rather than as a picture nobody can account for.
    //
    // Reported under this mod's own verbosity rather than the library's, the line being about the
    // framework's problem: a logger named after a library class would sit outside the switch a
    // player reaches for when the map looks wrong.
    private final ChangedLineTrace iconOrderTrace = ChangedLineTrace.createWholeLineTrace(
        LOG, "Map icon order", MapIconOrderTrace::describeTerrainIconOrder);

    // Which vanilla widgets the cursor is inside, reported when that changes. The hover comes from
    // the map's own geometry and knows nothing of the chrome laid over it, which is what the covers
    // answer for - and this is the line that says which widgets a build actually puts under the
    // cursor, so a cover that stops fitting a game build is diagnosed from the tree rather than
    // guessed at. Logged here for the reason above.
    private final ChangedLineTrace widgetsUnderCursorTrace = ChangedLineTrace.createKeyedLineTrace(
        LOG, "Map-tab widget trace", MapTabWidgetTrace::describeWidgetsUnderCursor);

    // The cursor read, taken from the source above on the first pass that wants one.
    private MapHoverPublisher hoverPublisher;

    // Whether this frame's passes are to read the cursor at all: some feedback still wants a hover,
    // and nothing is drawn over where the cursor rests. Settled once per frame because neither half
    // turns on which pass is running, and asking per pass would walk the live widget tree two or
    // three times over for one answer.
    private boolean isHoverWantedThisFrame;

    SequencedMapLayerRenderer(
            MapLayerFrameParts<S> parts,
            Supplier<ScreenLayerPicks> liveScreenSource,
            MapCoverReader mapCoverReader,
            MapHoverState hoverState,
            Function<MapHoverState, MapHoverPublisher> hoverPublisherSource,
            MapFrameBeats frameBeats) {

        this.parts = parts;
        this.liveScreenSource = liveScreenSource;
        this.mapCoverReader = mapCoverReader;
        this.hoverState = hoverState;
        this.hoverPublisherSource = hoverPublisherSource;
        this.frameBeats = frameBeats;
    }

    /**
     * The renderer one sector's map machinery holds for one layer, reading the screen, the covers and
     * the cursor the game is showing. Which sector it draws is the machinery's and what it draws is
     * the layer's; where the reads come from is settled here, the sequence itself naming only the
     * sources.
     *
     * @param machinery the machinery this renderer is being made for, whose hover holder the cursor
     *                  read publishes into and whose origin its profiling rows are grouped under
     * @param layerId   the ID of the layer this renderer draws, which its rows are reported under -
     *                  handed in by the layer rather than named here, so the framework holds no second
     *                  spelling of an ID the layer already owns
     * @param parts     what the layer supplies for its frames
     * @param <S>       what the layer's frames are painted under
     * @return a renderer for that machinery, its publisher unbuilt until a pass first wants a read
     */
    public static <S> SequencedMapLayerRenderer<S> createForLiveScreen(
            SectorMapMachinery machinery,
            String layerId,
            MapLayerFrameParts<S> parts) {

        return new SequencedMapLayerRenderer<>(
            parts,
            MapLayerScreens::resolveLivePicks,
            MapCoverReader.createForLiveScreen(),
            machinery.resolveHoverState(),
            SequencedMapLayerRenderer::buildLiveHoverPublisher,
            new MapFrameBeats(
                machinery.resolveProfilingOrigin(),
                MapFrameSections.resolveLayerSection(layerId)));
    }

    /**
     * Releases everything the layer built for this renderer's sector, when the machinery holding it
     * goes. What that covers is the layer's cache's to say.
     */
    @Override
    public void disposeMachinery() {

        parts
            .cache()
            .disposeCachedState();
    }

    @Override
    public void prepareFrame(float factor) {

        try (var beatScope = frameBeats.openBeat(MapFrameSections.PREPARE)) {
            // Which screen is showing, read once and carried: the subject this frame paints and the
            // preferences the refresh builds under are both that screen's, so resolving the screen
            // twice could paint one panel's subject under the other panel's picks - a map neither
            // panel was ever set to.
            var livePicks = liveScreenSource.get();

            // Stands down when the layer has nothing selected to paint on that screen. Gating the
            // frame on one read keeps a dark overlay near-free per frame.
            var subject = parts.subjectRead().apply(livePicks);
            if (subject == null) {
                standDownTheHoverForThisFrame();
                return;
            }
            try (var layerScope = frameBeats.openLayerRow()) {

                traceReflectiveReadings();
                announceArrivalOnTheFrameJustClosed();
                decideWhetherTheHoverIsWantedThisFrame();

                try (var refreshScope = frameBeats.openStep(MapFrameSections.REFRESH)) {

                    parts
                        .cache()
                        .refreshDrawLists(subject, livePicks.memoryScope());
                }
            }
        }
    }

    @Override
    public void renderOnMap(float factor, float alphaMult, MapOverlayBand band) {

        try (var beatScope = frameBeats.openBeat(MapFrameSections.resolveRenderSection(band))) {
            // The subject read, asked again rather than remembered: a field holding the frame's answer
            // would be render state on a renderer that deliberately holds none. It agrees with the
            // preparation's because a pass and the frame that prepared it are the same screen - the
            // player cannot change screens mid-frame.
            if (resolveLiveSubject() == null) {
                return;
            }
            try (var layerScope = frameBeats.openLayerRow()) {
                // The engine's two loose values become the frame every pass below takes whole. Built
                // here because this is the last point that still speaks the engine's signature: the
                // seam above mirrors vanilla's call so the trail back to it survives, and everything
                // under this line is ours to give a better shape.
                parts
                    .compositor()
                    .renderBand(new MapFrame(factor, alphaMult), band);
            }
        }
    }

    @Override
    public Optional<MapHoverTooltip> resolveHoverTooltip() {

        try (var beatScope = frameBeats.openBeat(MapFrameSections.TOOLTIP)) {
            // The layer's own tooltip switch, off means no box from it - and the framework draws
            // whatever the other layers offer regardless, which is the point of scoping it here
            // rather than at the dispatcher.
            if (!parts.hoverGates().isHoverTooltipEnabled()) {
                return Optional.empty();
            }

            // The box is resolved under the subject painting for the same reason the paint is: what
            // is up decides what there is to say about a cell, and the dispatcher above learns only
            // that this layer has a box, or has not.
            var subject = resolveLiveSubject();
            if (subject == null) {
                return Optional.empty();
            }

            try (var layerScope = frameBeats.openLayerRow()) {

                return parts
                    .tooltipRead()
                    .apply(subject);
            }
        }
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

        try (var beatScope = frameBeats.openBeat(MapFrameSections.HOVER_PUBLISH)) {

            if (!isHoverWantedThisFrame) {
                return;
            }
            if (hoverPublisher == null) {
                hoverPublisher = hoverPublisherSource.apply(hoverState);
            }

            try (var layerScope = frameBeats.openLayerRow()) {
                // The cursor read sits between the frame's refresh and this pass's draw: after the
                // refresh, so it tests against the shapes the frame actually paints, and before the
                // draw, so the highlight layers have an answer when they emit. It is the one point
                // in a pass with both the live GL matrices it needs and the current draw lists.
                hoverPublisher.publishHoverFrom(
                    parts.cache().resolveHoverTargets(),
                    factor);
            }
        }
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

        isHoverWantedThisFrame = parts.hoverGates().isCursorReadNeeded()
            && !mapCoverReader.isMapCoveredAtCursor();

        if (!isHoverWantedThisFrame) {
            hoverState.clearHover();
        }
    }

    // The cursor read a running game gets, and what answers a cell reached under it: the binding is
    // chosen from the renderer in force, and what a moment sounds like belongs to whatever owns the
    // look - the publisher under them names only the moment. The cue is composed per arrival rather
    // than fixed now, so a level changed on the settings screen reaches a publisher built long
    // before it.
    //
    // Taken as a source rather than built at construction because both reads behind it need a game
    // that is running, while the renderer is made whenever its sector's machinery is first asked for
    // it. Built the same way for every layer, since one built differently would report a different
    // cursor for the same frame.
    private static MapHoverPublisher buildLiveHoverPublisher(MapHoverState hoverState) {

        var soundPlayer = new VanillaUiSoundPlayer();

        return new MapHoverPublisher(
            hoverState,
            // The binding is the library's and the consequence is KMU's: it knows which renderer
            // stopped holding and which member moved, and nothing about the overlay drawn over the
            // reading, so what a failed binding costs is said here and once.
            ModelviewMatrixReaders.selectForActiveRenderer(new CompatibilityConsumer(
                KmuMod.MOD_ID,
                MAP_CURSOR_FEATURE_KEY,
                KmuStringKeys.get(KmuStringKeys.COMPATIBILITY_LOST_MAP_CURSOR),
                KmuStringKeys.get(KmuStringKeys.COMPATIBILITY_UNAFFECTED_MAP_CURSOR))),
            () -> soundPlayer.playCueIfPresent(MapHoverCues.composeCellArrivalCue()),
            // Taken from the shared permission rather than composed here, so this pass and the box
            // that reports what it finds answer from one reading: a hover resolved on a frame no box
            // may draw on would light a cell the map then refuses to name.
            MapHoverPermission.createForLiveScreen()::isCursorLocatable);
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

    // What the showing screen's frame is painted under, for a beat that is not carrying the
    // preparation's reading of the screen.
    private S resolveLiveSubject() {
        return parts.subjectRead().apply(liveScreenSource.get());
    }

    // A frame with nothing to paint reads no cursor on any of its passes and leaves no cell lit
    // behind it. Parked rather than left: a hover left standing from the last painted frame would
    // light that cell again the moment something is selected, before any pass had read where the
    // cursor now is.
    private void standDownTheHoverForThisFrame() {

        isHoverWantedThisFrame = false;
        hoverState.clearHover();
    }

    // The two traces that read the game's own widget tree, asked only where the player has asked for
    // them by name. Both walk the live tree and print about a kilobyte a line, which is out of all
    // proportion to the rest of what DEBUG turns on here - so they answer to a switch of their own as
    // well as to the level, and the level alone leaves them off.
    //
    // The switch is read before the traces rather than inside them, and that ordering is the point
    // rather than a saved call: a walk that fails warns once a session, and a warning spent while
    // this is off would be spent on nobody. Not walking at all until someone is listening is what
    // keeps that line available for them - and what a reader who switches this on part way through a
    // session is owed instead is handled where the switch moves.
    private void traceReflectiveReadings() {

        if (!KmuLoggingSettings.areReflectionProbesEnabled()) {
            return;
        }
        iconOrderTrace.traceWhenChanged();
        widgetsUnderCursorTrace.traceWhenChanged();
    }
}
