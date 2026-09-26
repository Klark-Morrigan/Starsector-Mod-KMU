package kmu.maplayers.base.render;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileOrigin;
import kmlib.profiling.ProfileSection;
import kmlib.profiling.SilentProfiler;
import kmlib.profiling.recording.RecordingProfiler;
import kmlib.profiling.snapshot.ProfileNode;
import kmlib.profiling.snapshot.ProfileOriginTree;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.hover.MapHoverPublisher;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.hover.MapHoverTargets;
import kmu.maplayers.base.hover.MapLayerHoverGates;
import kmu.maplayers.base.hover.MapLayerHoverGatesFake;
import kmu.maplayers.base.hover.cover.MapCover;
import kmu.maplayers.base.hover.cover.MapCoverReader;
import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.ControlBackedMapLayerVisibility;
import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.tooltip.MapHoverTooltip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the frame every painting layer runs, over parts that name no layer: a stand-down read
 * answering a plain subject, a cache and a compositor that record what they were asked, and the
 * layer's gates and box stated outright. What a real layer's parts then do with a frame is that
 * layer's suite's.
 *
 * <p>The four rules the sequence exists for are pinned here: the cover read taken once per frame
 * however many passes paint it, the arrival latch stepped once per frame, the cursor read taken on
 * every pass, and a stood-down frame parking the hover. Beside them sits the rest of what the
 * sequence decides for itself - that nothing selected costs a frame nothing, that the subject and
 * the refresh's screen come off one reading, and that the hover box answers the layer's switch
 * before its subject.
 *
 * <p>And the shape each beat leaves in a capture: a root per beat under this sector's origin, this
 * layer's row inside it around the work, and the stand-down reads between the two - so a frame with
 * nothing to draw still reports what standing down cost and opens no row for a layer that never
 * ran. A recording profiler is bound for every case here, so what a beat opens is read off the
 * capture rather than off a double.
 */
final class SequencedMapLayerRendererTest {

    private static final float FACTOR = 1f;
    private static final float OTHER_FACTOR = 2f;
    private static final float ALPHA_MULT = 0.5f;

    // What a frame is painted under, when the layer has something selected. A plain value, since
    // the sequence only ever hands it back to the layer's own parts.
    private static final String SUBJECT = "selected_subject";

    // The sector this renderer's rows are grouped under.
    private static final ProfileOrigin SECTOR_ORIGIN =
        ProfileOrigin.registerOrigin("test.sequencedLayerSector");

    // The layer whose work sits inside each beat, named as the framework names one: from the
    // layer's ID, through the resolver every layer's row is registered by.
    private static final ProfileSection LAYER_SECTION =
        MapFrameSections.resolveLayerSection("test_sequenced_layer");

    // The two cover answers, stated as covers rather than as a stubbed reader, so a case arranges
    // what is over the cursor without naming a console, a sidebar or the map's chrome.
    private static final MapCover COVERING_THE_MAP = () -> true;
    private static final MapCover NOT_COVERING_THE_MAP = () -> false;

    // A hover left standing from an earlier frame, so a parked read is told apart from one that
    // never had anything to drop.
    private static final MapHover HOVERED_CELL = new MapHover(
        buildCellKey("system_id"),
        List.of(buildCellKey("system_id")));

    // The cursor read, which needs the running game's GL matrices and so cannot be built here.
    private final MapHoverPublisher hoverPublisherMock = mock(MapHoverPublisher.class);

    // The layer's draw lists and the pass that emits them, recorded rather than built.
    @SuppressWarnings("unchecked")
    private final MapFrameCache<String> cacheMock = mock(MapFrameCache.class);
    private final MapFrameCompositor compositorMock = mock(MapFrameCompositor.class);

    // The hover holder of the sector this renderer draws, made per case.
    private final MapHoverState hoverState = new MapHoverState();

    // The layer's cursor switches, all open unless a case says otherwise.
    private final MapLayerHoverGatesFake hoverGatesFake = MapLayerHoverGatesFake.createAnswering();

    // What the renderer is built over: the fake above, or a mock for the cases that pin the gates
    // are never consulted at all.
    private MapLayerHoverGates hoverGates = hoverGatesFake;

    // The screen a frame finds showing, and how many times a case's frame asked which that was.
    private final ScreenLayerPicks showingScreen = createStandInScreenPicks();
    private int liveScreenReadCount;

    // What the layer's stand-down read answers, per case: the subject, or null for nothing selected.
    private String subjectOnShowingScreen = SUBJECT;

    // The layer's box for a subject, per case.
    private Function<String, Optional<MapHoverTooltip>> tooltipRead = subject -> Optional.empty();

    // How many times a case's frame asked the covers.
    private int coverReadCount;

    // The capture each case's beats are recorded into, fresh per case.
    private final RecordingProfiler profiler = new RecordingProfiler();

    // What the read's source was handed when the renderer first wanted one. Null until a pass has
    // asked for a read.
    private MapHoverState hoverStateHandedToTheReadSource;

    // The holder is process-wide, so a case that left a recording profiler bound would go on
    // recording every suite that ran after it.
    @BeforeEach
    void bindTheCapture() {
        ActiveProfiler.bindProfiler(profiler);
    }

    @AfterEach
    void unbindTheCapture() {
        ActiveProfiler.bindProfiler(SilentProfiler.INSTANCE);
    }

    @Nested
    class PrepareFrame {

        @Test
        void prepareFrameStandsDownWhileNothingIsSelected() {
            // The tab is open with nothing selected. Standing down on that one read is what keeps a
            // dark overlay near-free per frame: nothing downstream is consulted, not even the hover
            // switch that gates the cheapest of the work below it.
            var hoverGatesMock = mock(MapLayerHoverGates.class);

            hoverGates = hoverGatesMock;
            subjectOnShowingScreen = null;

            buildRenderer(NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

            verifyNoInteractions(hoverGatesMock, cacheMock);
        }

        @Test
        void prepareFrameParksTheHoverWhileNothingIsSelected() {
            // A stood-down frame reads no cursor, so a cell lit by the last painted frame would
            // otherwise stand until something is selected again - and light the moment it is.
            hoverState.publishHover(HOVERED_CELL);
            subjectOnShowingScreen = null;

            buildRenderer(NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

            assertThat(hoverState.getHover())
                .isEqualTo(MapHover.NONE);
        }

        @Test
        void prepareFrameAnnouncesTheMomentTheFrameBeforeSettledOn() {
            // Where the tick comes from, and the reason it comes from here: the frame's passes each
            // read through their own transform and the last of them wins, so the moment can only be
            // answered once they are all in - which the next frame's single preparation is.
            var renderer = buildRenderer(NOT_COVERING_THE_MAP);

            renderer.decideWhetherTheHoverIsWantedThisFrame();
            renderer.publishHoverForPass(FACTOR);
            renderer.prepareFrame(FACTOR);

            verify(hoverPublisherMock)
                .announceSettledArrival();
        }

        @Test
        void prepareFrameAnnouncesNothingBeforeAnyPassHasReadTheCursor() {
            // The first preparation of a session runs before any pass ever has, so there is no read
            // to answer for - and none to build one for either, the read needing a running map.
            buildRenderer(NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

            verifyNoInteractions(hoverPublisherMock);
        }

        @Test
        void prepareFrameStepsTheArrivalOnceAcrossAFramePaintedInTwoPasses() {
            // Two surfaces paint one frame, so two passes read the cursor - and the moment is still
            // the frame's, stepped by the one preparation that follows them.
            var renderer = buildRenderer(NOT_COVERING_THE_MAP);

            renderer.prepareFrame(FACTOR);
            renderer.publishHoverForPass(FACTOR);
            renderer.publishHoverForPass(OTHER_FACTOR);
            renderer.prepareFrame(FACTOR);

            verify(hoverPublisherMock, times(1))
                .announceSettledArrival();
        }

        @Test
        void prepareFrameAsksTheCoversOnceForAFramePaintedInTwoPasses() {
            // The last cover walks the live widget tree, so the question is the frame's: two passes
            // reading the cursor ask a flag the preparation set, not the covers again.
            var renderer = buildRenderer(NOT_COVERING_THE_MAP);

            renderer.prepareFrame(FACTOR);
            renderer.publishHoverForPass(FACTOR);
            renderer.publishHoverForPass(OTHER_FACTOR);

            assertThat(coverReadCount)
                .isEqualTo(1);
        }

        @Test
        void prepareFrameRefreshesTheCacheUnderTheSubjectAndScopeOfOneReadOfTheShowingScreen() {
            // The frame's whole per-screen contract in one case: the subject it paints is found for
            // the screen showing, and the refresh is handed that same screen's scope. Both are
            // per-screen picks, so a frame resolving the screen twice could paint one panel's subject
            // under the other panel's preferences.
            buildRenderer(NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

            verify(cacheMock)
                .refreshDrawLists(SUBJECT, showingScreen.memoryScope());

            assertThat(liveScreenReadCount)
                .isEqualTo(1);
        }

        @Test
        void prepareFrameMeasuresTheCacheRefreshInsideThisLayersRowOfItsOwnBeat() {
            // The whole shape of a prepared frame's capture: the beat is a root of this sector, the
            // layer's row is what the beat opens around the work, and the refresh sits inside it.
            buildRenderer(NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

            var beat = readOnlyRootOfThisSector();

            assertThat(beat.getSection())
                .isEqualTo(MapFrameSections.PREPARE);
            assertThat(readSectionsOfChildrenOf(beat))
                .containsExactly(LAYER_SECTION);
            assertThat(readSectionsOfChildrenOf(beat.getChildren().get(0)))
                .containsExactly(MapFrameSections.REFRESH);
        }

        @Test
        void prepareFrameStandingDownMeasuresItsBeatAndNothingBeneathIt() {
            // A frame with nothing selected still costs the read that found that out, so the beat is
            // opened around it - but no layer ran, and a row for one that did not would report a
            // cost against work that never happened.
            subjectOnShowingScreen = null;

            buildRenderer(NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

            var beat = readOnlyRootOfThisSector();

            assertThat(beat.getSection())
                .isEqualTo(MapFrameSections.PREPARE);
            assertThat(beat.getChildren())
                .isEmpty();
        }
    }

    @Nested
    class RenderOnMap {

        @ParameterizedTest
        @EnumSource(MapOverlayBand.class)
        void renderOnMapStandsDownWhileNothingIsSelected(MapOverlayBand band) {
            // Every band asks the same question and gets the same answer. Driven per band because
            // each is a separate pass from a separate surface, so a band that read the subject
            // differently would paint on a frame the others left alone.
            var hoverGatesMock = mock(MapLayerHoverGates.class);

            hoverGates = hoverGatesMock;
            subjectOnShowingScreen = null;

            buildRenderer(NOT_COVERING_THE_MAP).renderOnMap(FACTOR, ALPHA_MULT, band);

            verifyNoInteractions(hoverGatesMock, compositorMock);
        }

        @ParameterizedTest
        @EnumSource(MapOverlayBand.class)
        void renderOnMapHandsThePassesFrameAndBandToTheCompositor(MapOverlayBand band) {
            // The engine's two loose values reach the layer as one frame, beside the band this pass
            // paints - the compositor is what decides which sub-layers that band holds.
            buildRenderer(NOT_COVERING_THE_MAP).renderOnMap(FACTOR, ALPHA_MULT, band);

            verify(compositorMock)
                .renderBand(new MapFrame(FACTOR, ALPHA_MULT), band);
        }

        @ParameterizedTest
        @EnumSource(MapOverlayBand.class)
        void renderOnMapMeasuresEachBandAsASeparateRootOfThisSector(MapOverlayBand band) {
            // Each band is a pass of its own, so each gets a root of its own, with the layer's row
            // inside it around the compositor.
            buildRenderer(NOT_COVERING_THE_MAP).renderOnMap(FACTOR, ALPHA_MULT, band);

            var beat = readOnlyRootOfThisSector();

            assertThat(beat.getSection())
                .isEqualTo(MapFrameSections.resolveRenderSection(band));
            assertThat(readSectionsOfChildrenOf(beat))
                .containsExactly(LAYER_SECTION);
        }
    }

    @Nested
    class ResolveHoverTooltip {

        @Test
        void resolveHoverTooltipAnswersTheLayersBoxForTheSubjectPainting() {
            // What is painting decides what there is to say about a cell, so the box handed to the
            // framework is the one the layer resolves under the subject its read found.
            var tooltipMock = mock(MapHoverTooltip.class);

            tooltipRead = subject -> SUBJECT.equals(subject) ? Optional.of(tooltipMock) : Optional.empty();

            assertThat(buildRenderer(NOT_COVERING_THE_MAP).resolveHoverTooltip())
                .contains(tooltipMock);
        }

        @Test
        void resolveHoverTooltipIsEmptyWhileThisLayersTooltipSwitchIsOff() {
            // The layer withholds its box by offering none, which is how one layer's box goes dark
            // while every other layer's stays up. The switch answers before the subject is read, so
            // a layer that would have built a box never does the work.
            var subjectReadCount = new int[1];

            hoverGatesFake.setHoverTooltipOn(false);
            tooltipRead = subject -> {
                subjectReadCount[0]++;
                return Optional.of(mock(MapHoverTooltip.class));
            };

            assertThat(buildRenderer(NOT_COVERING_THE_MAP).resolveHoverTooltip())
                .isEmpty();
            assertThat(subjectReadCount[0])
                .isZero();
        }

        @Test
        void resolveHoverTooltipIsEmptyWhileNothingIsSelected() {
            // Nothing is painted, so there is nothing for a hover to describe either.
            subjectOnShowingScreen = null;
            tooltipRead = subject -> Optional.of(mock(MapHoverTooltip.class));

            assertThat(buildRenderer(NOT_COVERING_THE_MAP).resolveHoverTooltip())
                .isEmpty();
        }

        @Test
        void resolveHoverTooltipMeasuresThisLayersRowInsideItsBeatThoughTheLayerOpensNothing() {
            // The row is opened around the layer's box rather than by it, which is what gives a
            // layer that measures nothing of its own a row all the same.
            buildRenderer(NOT_COVERING_THE_MAP).resolveHoverTooltip();

            var beat = readOnlyRootOfThisSector();

            assertThat(beat.getSection())
                .isEqualTo(MapFrameSections.TOOLTIP);
            assertThat(readSectionsOfChildrenOf(beat))
                .containsExactly(LAYER_SECTION);
        }

        @Test
        void resolveHoverTooltipMeasuresItsBeatBesideThePreparationsRatherThanInsideIt() {
            // Two beats of one frame are two roots, in the order they ran. A beat opened as a child
            // would report the box as part of what the preparation cost.
            var renderer = buildRenderer(NOT_COVERING_THE_MAP);

            renderer.prepareFrame(FACTOR);
            renderer.resolveHoverTooltip();

            assertThat(readRootsOfThisSector())
                .extracting(ProfileNode::getSection)
                .containsExactly(MapFrameSections.PREPARE, MapFrameSections.TOOLTIP);
        }
    }

    @Nested
    class DecideWhetherTheHoverIsWantedThisFrame {

        @Test
        void decideWhetherTheHoverIsWantedThisFrameParksTheHoverWhileTheMapIsCovered() {
            // A covered cursor is not hovering the cells beneath it, so the hover is parked rather
            // than left standing. Without it the map went on lighting cells and floating boxes
            // behind an open console.
            hoverState.publishHover(HOVERED_CELL);

            buildRenderer(COVERING_THE_MAP)
                .decideWhetherTheHoverIsWantedThisFrame();

            assertThat(hoverState.getHover())
                .isEqualTo(MapHover.NONE);
        }

        @Test
        void decideWhetherTheHoverIsWantedThisFrameParksTheHoverWhileEveryHoverSwitchIsOff() {
            // With both kinds of feedback switched off there is nothing that wants the answer, and
            // the frame's passes read no cursor at all.
            hoverState.publishHover(HOVERED_CELL);
            hoverGatesFake.setHoverEffectsOn(false);
            hoverGatesFake.setHoverTooltipOn(false);

            buildRenderer(NOT_COVERING_THE_MAP)
                .decideWhetherTheHoverIsWantedThisFrame();

            assertThat(hoverState.getHover())
                .isEqualTo(MapHover.NONE);
        }

        @Test
        void decideWhetherTheHoverIsWantedThisFrameAsksNoCoverWhileEveryHoverSwitchIsOff() {
            // The covers are the dear half, so a player who wants no feedback at all must not pay
            // for a walk that could only refine an answer nobody asked for.
            hoverGatesFake.setHoverEffectsOn(false);
            hoverGatesFake.setHoverTooltipOn(false);

            buildRenderer(NOT_COVERING_THE_MAP)
                .decideWhetherTheHoverIsWantedThisFrame();

            assertThat(coverReadCount)
                .isZero();
        }
    }

    @Nested
    class PublishHoverForPass {

        @Test
        void publishHoverForPassReadsTheCursorAgainstTheCachesTargetsOnceTheFrameWantsAHover() {
            // The pass's whole remaining job: hand the layer's current shapes and this pass's own
            // zoom to the read.
            var targetsMock = mock(MapHoverTargets.class);

            when(cacheMock.resolveHoverTargets())
                .thenReturn(targetsMock);

            var renderer = buildRenderer(NOT_COVERING_THE_MAP);

            renderer.decideWhetherTheHoverIsWantedThisFrame();
            renderer.publishHoverForPass(FACTOR);

            verify(hoverPublisherMock)
                .publishHoverFrom(targetsMock, FACTOR);
        }

        @Test
        void publishHoverForPassReadsTheCursorOnEveryPassThroughThatPassesOwnZoom() {
            // Each pass binds its own transform, so each reads - the last of them owning the answer,
            // which is the publisher's to keep.
            var renderer = buildRenderer(NOT_COVERING_THE_MAP);

            renderer.decideWhetherTheHoverIsWantedThisFrame();
            renderer.publishHoverForPass(FACTOR);
            renderer.publishHoverForPass(OTHER_FACTOR);

            verify(hoverPublisherMock)
                .publishHoverFrom(null, FACTOR);
            verify(hoverPublisherMock)
                .publishHoverFrom(null, OTHER_FACTOR);
        }

        @Test
        void publishHoverForPassBuildsTheReadAgainstItsOwnHoverHolder() {
            // Handed any other sector's holder, a cursor read taken over this sector's cells would
            // light a cell on another sector's map - and this is the only place that pairing is made.
            var renderer = buildRenderer(NOT_COVERING_THE_MAP);

            renderer.decideWhetherTheHoverIsWantedThisFrame();
            renderer.publishHoverForPass(FACTOR);

            assertThat(hoverStateHandedToTheReadSource)
                .isSameAs(hoverState);
        }

        @Test
        void publishHoverForPassAnnouncesNoArrival() {
            // The moment belongs to the frame, not to a pass. A pass answering one would sound the
            // crossing between two transforms' answers every frame a foreign map draws beside the
            // real one.
            var renderer = buildRenderer(NOT_COVERING_THE_MAP);

            renderer.decideWhetherTheHoverIsWantedThisFrame();
            renderer.publishHoverForPass(FACTOR);

            verify(hoverPublisherMock, never())
                .announceSettledArrival();
        }

        @Test
        void publishHoverForPassReadsNothingWhileTheFrameWantsNoHover() {
            // The frame's decision is what the passes stand down on, so the read - and with it the
            // publisher and the renderer binding it holds - is never reached at all.
            buildRenderer(NOT_COVERING_THE_MAP)
                .publishHoverForPass(FACTOR);

            verifyNoInteractions(hoverPublisherMock);
        }

        @Test
        void publishHoverForPassReadsNothingOnAFrameThatStoodDown() {
            // A frame with nothing selected parks its passes along with the hover, or a deselected
            // layer would still pay for a matrix read per pass.
            var renderer = buildRenderer(NOT_COVERING_THE_MAP);

            renderer.decideWhetherTheHoverIsWantedThisFrame();
            subjectOnShowingScreen = null;
            renderer.prepareFrame(FACTOR);
            renderer.publishHoverForPass(FACTOR);

            verify(hoverPublisherMock, never())
                .publishHoverFrom(any(), anyFloat());
        }

        @Test
        void publishHoverForPassMeasuresThisLayersRowInsideItsOwnBeat() {
            // The read is per pass, so its beat is too - a frame painted by several surfaces then
            // reports the reads it actually made rather than one of them.
            var renderer = buildRenderer(NOT_COVERING_THE_MAP);

            renderer.decideWhetherTheHoverIsWantedThisFrame();
            renderer.publishHoverForPass(FACTOR);

            var beat = readOnlyRootOfThisSector();

            assertThat(beat.getSection())
                .isEqualTo(MapFrameSections.HOVER_PUBLISH);
            assertThat(readSectionsOfChildrenOf(beat))
                .containsExactly(LAYER_SECTION);
        }

        @Test
        void publishHoverForPassStandingDownMeasuresItsBeatAndNothingBeneathIt() {
            // A pass of a frame that wants no hover still costs the gate that stood it down, and
            // several passes stand down per frame - so the beat says how much a switched-off hover
            // is costing, and opens no row for a read that never ran.
            buildRenderer(NOT_COVERING_THE_MAP)
                .publishHoverForPass(FACTOR);

            var beat = readOnlyRootOfThisSector();

            assertThat(beat.getSection())
                .isEqualTo(MapFrameSections.HOVER_PUBLISH);
            assertThat(beat.getChildren())
                .isEmpty();
        }
    }

    @Nested
    class DisposeMachinery {

        @Test
        void disposeMachineryReleasesTheLayersCache() {
            // The draw lists behind a painting layer can own GL buffers, and the cache is the one
            // part that knows which - so the release is handed to it rather than decided here.
            buildRenderer(NOT_COVERING_THE_MAP).disposeMachinery();

            verify(cacheMock)
                .disposeCachedState();
        }
    }

    // The renderer under test, over one stated cover and the case's parts. The read's source records
    // the holder it was handed instead of ignoring it, since what the renderer passes down is the one
    // thing about the wiring no other suite can see; the screen source and the cover count the reads
    // a case pins the number of.
    private SequencedMapLayerRenderer<String> buildRenderer(MapCover cover) {

        MapCover countedCover = () -> {
            coverReadCount++;
            return cover.isCoveringCursor();
        };

        return new SequencedMapLayerRenderer<>(
            new MapLayerFrameParts<>(
                screenPicks -> screenPicks == showingScreen ? subjectOnShowingScreen : null,
                cacheMock,
                compositorMock,
                hoverGates,
                subject -> tooltipRead.apply(subject)),
            () -> {
                liveScreenReadCount++;
                return showingScreen;
            },
            new MapCoverReader(List.of(countedCover)),
            hoverState,
            handedHoverState -> {
                hoverStateHandedToTheReadSource = handedHoverState;
                return hoverPublisherMock;
            },
            new MapFrameBeats(SECTOR_ORIGIN, LAYER_SECTION));
    }

    // The one beat the case just drove, asserted to be the only root rather than searched for, so a
    // beat opened where none was meant to be is a failure here rather than a row nobody looked at.
    private ProfileNode readOnlyRootOfThisSector() {

        var roots = readRootsOfThisSector();

        assertThat(roots)
            .hasSize(1);

        return roots.get(0);
    }

    // This sector's group of the capture, empty when nothing was opened under it.
    private List<ProfileNode> readRootsOfThisSector() {

        return profiler
            .snapshot()
            .stream()
            .filter(originTree -> originTree.getOrigin() == SECTOR_ORIGIN)
            .map(ProfileOriginTree::getRoots)
            .findFirst()
            .orElse(List.of());
    }

    // What a row opened inside another, so a case states the shape it expects as sections.
    private static List<ProfileSection> readSectionsOfChildrenOf(ProfileNode node) {

        return node
            .getChildren()
            .stream()
            .map(ProfileNode::getSection)
            .toList();
    }

    // One screen's picks, of no particular identity: the cases are about the frame reading the
    // showing screen once and carrying it, not about which of the mod's screens that is.
    private static ScreenLayerPicks createStandInScreenPicks() {

        return new ScreenLayerPicks(
            mock(ActiveLayerSelection.class),
            new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class)),
            ScreenMemoryScopes.createStandInScreen());
    }
}
