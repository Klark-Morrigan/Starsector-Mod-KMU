package kmu.maplayers.politicalmap.base.render;

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
import kmu.maplayers.base.hover.cover.MapCover;
import kmu.maplayers.base.hover.cover.MapCoverReader;
import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.ControlBackedMapLayerVisibility;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.render.MapFrameBeats;
import kmu.maplayers.base.render.MapFrameSections;
import kmu.maplayers.base.render.MapOverlayBand;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapPreviewHighlightRenderer;
import kmu.settings.KmuMapHoverSettings;
import kmu.settings.KmuPoliticalMapDiagnosticsSettings;
import kmu.settings.KmuPoliticalMapGeometrySettings;
import kmu.settings.KmuPoliticalMapHighlightSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins what this renderer decides for itself before any drawing happens: that a deselected view
 * costs a frame nothing whichever of its passes is running, that the hover box it answers the
 * framework with is the active view's and only while this layer's own tooltip switch is on, that a
 * covered cursor parks the hover rather than leaving it standing, and that releasing the machinery
 * behind it leaves nothing of its sector. What the release actually empties is
 * {@link PoliticalMapCacheTest}'s; the cache refresh and the GL emission run only in-engine and are
 * covered by their own collaborators.
 *
 * <p>Beside those sits the split between a frame and its passes, which is this renderer's alone to
 * get right: what the frame settles once - whether a hover is wanted at all, and the moment the
 * frame before ended on - against what each pass asks of its own transform. The read itself needs a
 * live map, so it arrives as a source this test hands a double to; what is pinned is which call
 * reaches it and with what.
 *
 * <p>And the shape each beat leaves in a capture, which is the same arrangement read from the other
 * side: a root per beat under this sector's origin, this layer's row inside it around the work, and
 * the stand-down reads between the two - so a frame with nothing to draw still reports what standing
 * down cost and opens no row for a layer that never ran. A recording profiler is bound for every
 * case here, so what a beat opens is read off the capture rather than off a double.
 */
final class PoliticalMapLayerRendererTest {

    private static final float FACTOR = 1f;
    private static final float ALPHA_MULT = 1f;

    // The sector this renderer's rows are grouped under. Stated rather than taken from the
    // machinery below, which is the detached one and so groups under the reserved origin - the
    // one label a capture uses for rows nobody attributed, which would make an attributed root
    // indistinguishable from an unattributed one.
    private static final ProfileOrigin SECTOR_ORIGIN =
        ProfileOrigin.registerOrigin("test.politicalMapSector");

    // The layer whose work sits inside each beat, named as the framework names one: from the
    // layer's id, through the resolver every layer's row is registered by.
    private static final ProfileSection LAYER_SECTION =
        MapFrameSections.resolveLayerSection("test_political_map");

    // The two cover answers, stated as covers rather than as a stubbed reader, so a test arranges
    // what is over the cursor without naming a console, a sidebar or the map's chrome - which of
    // those is on screen is the reader's business and not this renderer's.
    private static final MapCover COVERING_THE_MAP = () -> true;
    private static final MapCover NOT_COVERING_THE_MAP = () -> false;

    // A hover left standing from an earlier frame, so a parked read is told apart from one that
    // never had anything to drop.
    private static final MapHover HOVERED_CELL = new MapHover("system_id", List.of("system_id"));

    // The cursor read, which needs the running game's GL matrices and so cannot be built here. What
    // it resolves is MapHoverPublisherTest's; what this test asks of it is which call reaches it.
    private final MapHoverPublisher hoverPublisherMock = mock(MapHoverPublisher.class);

    // The hover holder of the sector this renderer draws, made per case rather than shared - a
    // renderer is built with the holder of its own installed machinery.
    private final MapHoverState hoverState = new MapHoverState();

    // The capture each case's beats are recorded into, fresh per case so one case's roots are never
    // read as another's.
    private final RecordingProfiler profiler = new RecordingProfiler();

    // What the read's source was handed when the renderer first wanted one, recorded so the case
    // about the wiring can read it back. Null until a pass has asked for a read.
    private MapHoverState hoverStateHandedToTheReadSource;

    // The holder is process-wide, so a case that left a recording profiler bound would go on
    // recording every suite that ran after it - and the silent one is what the library is meant to
    // be found at.
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
        void prepareFrameStandsDownWhileNoViewIsSelected() {
            // The tab is open with every view deselected. Standing down on that one read is what
            // keeps a dark overlay near-free per frame: nothing downstream is consulted, not even the
            // hover toggle that gates the cheapest of the work below it.
            recordNothingInThisCase();

            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                viewRegistryMock
                    .when(() -> PoliticalMapViewRegistry.resolveActiveViewOn(any()))
                    .thenReturn(null);

                buildRenderer(NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

                frameworkSettingsMock
                    .verifyNoInteractions();
                layerSettingsMock
                    .verifyNoInteractions();
            }
        }

        @Test
        void prepareFrameAnnouncesTheMomentTheFrameBeforeSettledOn() {
            // Where the tick comes from, and the reason it comes from here: the frame's passes each
            // read through their own transform and the last of them wins, so the moment can only be
            // answered once they are all in - which the next frame's single preparation is.
            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class);
                    var geometrySettingsMock = mockStatic(KmuPoliticalMapGeometrySettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                stubASelectedView(viewRegistryMock);
                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                var renderer = buildRenderer(NOT_COVERING_THE_MAP);

                renderer.decideWhetherTheHoverIsWantedThisFrame();
                renderer.publishHoverForPass(FACTOR);
                // Reaches the cache refresh, which asks for the cell seed inputs and the dev
                // overlays: neither turns this case, and both are LunaLib-backed.
                renderer.prepareFrame(FACTOR);

                verify(hoverPublisherMock)
                    .announceSettledArrival();
            }
        }

        @Test
        void prepareFrameAnnouncesNothingBeforeAnyPassHasReadTheCursor() {
            // The first preparation of a session runs before any pass ever has, so there is no read
            // to answer for - and none to build one for either, the read needing a running map.
            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class);
                    var geometrySettingsMock = mockStatic(KmuPoliticalMapGeometrySettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                stubASelectedView(viewRegistryMock);
                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                // Reaches the cache refresh, so the sections it asks on the way through are open
                // for the same reason the case above opens them.
                buildRenderer(NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

                verifyNoInteractions(hoverPublisherMock);
            }
        }

        @Test
        void prepareFrameMeasuresTheCacheRefreshInsideThisLayersRowOfItsOwnBeat() {
            // The whole shape of a prepared frame's capture, read in one case because it is one
            // arrangement: the beat is a root of this sector, the layer's row is what the beat
            // opens around the work, and the refresh sits inside that row.
            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class);
                    var geometrySettingsMock = mockStatic(KmuPoliticalMapGeometrySettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                stubASelectedView(viewRegistryMock);
                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                buildRenderer(NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

                var beat = readOnlyRootOfThisSector();

                assertThat(beat.getSection())
                    .isEqualTo(MapFrameSections.PREPARE);
                assertThat(readSectionsOfChildrenOf(beat))
                    .containsExactly(LAYER_SECTION);
                assertThat(readSectionsOfChildrenOf(beat.getChildren().get(0)))
                    .containsExactly(MapFrameSections.REFRESH);
            }
        }

        @Test
        void prepareFrameTakesTheViewAndTheRefreshsScreenOffOneReadOfTheShowingScreen() {
            // The frame's whole per-screen contract in one case: the view it paints is resolved for the
            // screen showing, and the refresh is handed that same screen's scope. Both are per-screen
            // picks, so a frame resolving the screen twice could paint one panel's view under the other
            // panel's preferences - a map neither panel was ever set to. Pinned on the two calls,
            // because a second resolution is invisible in the picture until the two screens differ.
            var cacheMock = mock(PoliticalMapCache.class);
            var viewMock = mock(PoliticalMapView.class);
            var showingScreen = createStandInScreenPicks();

            try (var screensMock = mockStatic(MapLayerScreens.class);
                    var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                screensMock
                    .when(MapLayerScreens::resolveLivePicks)
                    .thenReturn(showingScreen);
                viewRegistryMock
                    .when(() -> PoliticalMapViewRegistry.resolveActiveViewOn(showingScreen))
                    .thenReturn(viewMock);
                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                buildRendererOver(cacheMock, NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

                verify(cacheMock)
                    .refresh(viewMock, showingScreen.memoryScope());
            }
        }

        @Test
        void prepareFrameStandingDownMeasuresItsBeatAndNothingBeneathIt() {
            // A frame with every view deselected still costs the read that found that out, so the
            // beat is opened around it - but no layer ran, and a row for one that did not would
            // report a cost against work that never happened.
            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                viewRegistryMock
                    .when(() -> PoliticalMapViewRegistry.resolveActiveViewOn(any()))
                    .thenReturn(null);

                buildRenderer(NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

                var beat = readOnlyRootOfThisSector();

                assertThat(beat.getSection())
                    .isEqualTo(MapFrameSections.PREPARE);
                assertThat(beat.getChildren())
                    .isEmpty();
            }
        }
    }

    @Nested
    class RenderOnMap {

        @ParameterizedTest
        @EnumSource(MapOverlayBand.class)
        void renderOnMapStandsDownWhileNoViewIsSelected(MapOverlayBand band) {
            // Every band asks the same question and gets the same answer: a deselected view has
            // nothing to say about either side of the map's nebulae. Driven per band because
            // each is a separate pass from a separate surface, so a band that read the view
            // differently would paint on a frame the others left alone.
            recordNothingInThisCase();

            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(null);

                buildRenderer(NOT_COVERING_THE_MAP).renderOnMap(FACTOR, ALPHA_MULT, band);

                frameworkSettingsMock
                    .verifyNoInteractions();
                layerSettingsMock
                    .verifyNoInteractions();
            }
        }

        @ParameterizedTest
        @EnumSource(MapOverlayBand.class)
        void renderOnMapMeasuresEachBandAsASeparateRootOfThisSector(MapOverlayBand band) {
            // Each band is a pass of its own, so each gets a root of its own. Driven on the
            // stand-down path because that is the one this suite can reach without GL - what is
            // pinned here is the naming and the root, the layer's row inside a beat being pinned
            // where a beat can be driven through.
            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(null);

                buildRenderer(NOT_COVERING_THE_MAP).renderOnMap(FACTOR, ALPHA_MULT, band);

                var beat = readOnlyRootOfThisSector();

                assertThat(beat.getSection())
                    .isEqualTo(MapFrameSections.resolveRenderSection(band));
                assertThat(beat.getChildren())
                    .isEmpty();
            }
        }
    }

    @Nested
    class ResolveHoverTooltip {

        @Test
        void resolveHoverTooltipAnswersTheActiveViewsTooltip() {
            // Which view is up decides what there is to say about a system, so the box handed to the
            // framework is whichever the active view injects - never a fixed one for the layer.
            var tooltipMock = mock(MapHoverTooltip.class);
            var viewMock = mock(PoliticalMapView.class);

            when(viewMock.resolveHoverTooltip())
                .thenReturn(Optional.of(tooltipMock));

            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                stubTooltipSwitches(frameworkSettingsMock, layerSettingsMock, true);

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(viewMock);

                assertThat(buildRenderer(NOT_COVERING_THE_MAP).resolveHoverTooltip())
                    .contains(tooltipMock);
            }
        }

        @Test
        void resolveHoverTooltipIsEmptyWhileThisLayersTooltipSwitchIsOff() {
            // The layer withholds its box by offering none, which is how one layer's box goes dark
            // while every other layer's stays up - the dispatcher above draws whatever it is offered.
            var viewMock = mock(PoliticalMapView.class);

            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                stubTooltipSwitches(frameworkSettingsMock, layerSettingsMock, false);

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(viewMock);

                assertThat(buildRenderer(NOT_COVERING_THE_MAP).resolveHoverTooltip())
                    .isEmpty();

                // The switch answers before the view is consulted, so a view that would have built a
                // box never does the work.
                verifyNoInteractions(viewMock);
            }
        }

        @Test
        void resolveHoverTooltipIsEmptyWhileNoViewIsSelected() {
            // The tab is open with every view deselected: nothing is painted, so there is nothing for a
            // hover to describe either.
            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                stubTooltipSwitches(frameworkSettingsMock, layerSettingsMock, true);

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(null);

                assertThat(buildRenderer(NOT_COVERING_THE_MAP).resolveHoverTooltip())
                    .isEmpty();
            }
        }

        @Test
        void resolveHoverTooltipMeasuresThisLayersRowInsideItsBeatThoughTheLayerOpensNothing() {
            // The row is opened around the callback rather than by it, which is what gives a layer
            // that measures nothing of its own a row all the same - the box here is a double that
            // opens no section, and the layer is still reported under the beat it answered in.
            var viewMock = mock(PoliticalMapView.class);

            when(viewMock.resolveHoverTooltip())
                .thenReturn(Optional.of(mock(MapHoverTooltip.class)));

            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                stubTooltipSwitches(frameworkSettingsMock, layerSettingsMock, true);

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(viewMock);

                buildRenderer(NOT_COVERING_THE_MAP).resolveHoverTooltip();

                var beat = readOnlyRootOfThisSector();

                assertThat(beat.getSection())
                    .isEqualTo(MapFrameSections.TOOLTIP);
                assertThat(readSectionsOfChildrenOf(beat))
                    .containsExactly(LAYER_SECTION);
            }
        }

        @Test
        void resolveHoverTooltipMeasuresItsBeatBesideThePreparationsRatherThanInsideIt() {
            // Two beats of one frame are two roots, in the order they ran. They are separate calls
            // from separate passes with nothing bracketing them, so a beat opened as a child would
            // report the box as part of what the preparation cost - and every beat after the first
            // would disappear into whichever one happened to run first.
            var viewMock = mock(PoliticalMapView.class);

            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class);
                    var geometrySettingsMock = mockStatic(KmuPoliticalMapGeometrySettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                stubTooltipSwitches(frameworkSettingsMock, layerSettingsMock, true);

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(viewMock);
                viewRegistryMock
                    .when(() -> PoliticalMapViewRegistry.resolveActiveViewOn(any()))
                    .thenReturn(viewMock);

                var renderer = buildRenderer(NOT_COVERING_THE_MAP);

                renderer.prepareFrame(FACTOR);
                renderer.resolveHoverTooltip();

                assertThat(readRootsOfThisSector())
                    .extracting(ProfileNode::getSection)
                    .containsExactly(MapFrameSections.PREPARE, MapFrameSections.TOOLTIP);
            }
        }
    }

    @Nested
    class DecideWhetherTheHoverIsWantedThisFrame {

        @Test
        void decideWhetherTheHoverIsWantedThisFrameParksTheHoverWhileTheMapIsCovered() {
            // The renderer's half of the arrangement: a covered cursor is not hovering the cells
            // beneath it, so the hover is parked rather than left standing. Without it the map went
            // on lighting cells and floating boxes behind an open console.
            //
            // Reached with the covers stubbed because the reads a running game answers - the
            // sidebar's laid-out box, the vanilla chrome's widget tree - are the reader's own, and
            // what this pins is that the renderer obeys whichever answer it gets.
            hoverState.publishHover(HOVERED_CELL);

            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                buildRenderer(COVERING_THE_MAP)
                    .decideWhetherTheHoverIsWantedThisFrame();

                assertThat(hoverState.getHover())
                    .isEqualTo(MapHover.NONE);
            }
        }

        @Test
        void decideWhetherTheHoverIsWantedThisFrameParksTheHoverWhileEveryHoverSwitchIsOff() {
            // The other park, pinned beside it so the covered one cannot be read as the only way a
            // stale cell is dropped: with both kinds of feedback switched off there is nothing that
            // wants the answer, and the frame's passes read no cursor at all.
            hoverState.publishHover(HOVERED_CELL);

            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                frameworkSettingsMock
                    .when(KmuMapHoverSettings::isMapHoveringEnabled)
                    .thenReturn(false);

                buildRenderer(NOT_COVERING_THE_MAP)
                    .decideWhetherTheHoverIsWantedThisFrame();

                assertThat(hoverState.getHover())
                    .isEqualTo(MapHover.NONE);
            }
        }

        @Test
        void decideWhetherTheHoverIsWantedThisFrameAsksNoCoverWhileEveryHoverSwitchIsOff() {
            // The covers are the dear half - the last of them walks the live widget tree - so a
            // player who wants no feedback at all must not pay for a walk that could only refine an
            // answer nobody asked for.
            var coverReadCount = new int[1];

            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                frameworkSettingsMock
                    .when(KmuMapHoverSettings::isMapHoveringEnabled)
                    .thenReturn(false);

                buildRenderer(() -> {
                    coverReadCount[0]++;
                    return false;
                }).decideWhetherTheHoverIsWantedThisFrame();

                assertThat(coverReadCount[0])
                    .isZero();
            }
        }
    }

    @Nested
    class PublishHoverForPass {

        @Test
        void publishHoverForPassReadsTheCursorOnceTheFrameWantsAHover() {
            // The pass's whole remaining job: hand the frame's draw lists and this pass's own zoom
            // to the read. The lists are empty here, which is what a pass before the first build
            // sees and what the publisher parks on.
            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                var renderer = buildRenderer(NOT_COVERING_THE_MAP);

                renderer.decideWhetherTheHoverIsWantedThisFrame();
                renderer.publishHoverForPass(FACTOR);

                verify(hoverPublisherMock)
                    .publishHoverFrom(null, FACTOR);
            }
        }

        @Test
        void publishHoverForPassBuildsTheReadAgainstItsOwnHoverHolder() {
            // The read publishes what it resolves, and where it publishes is decided here rather
            // than by the read itself. Handed any other sector's holder, a cursor read taken over
            // this sector's cells would light a cell on another sector's map - and this is the only
            // place that pairing is made, so no suite below can observe it.
            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                var renderer = buildRenderer(NOT_COVERING_THE_MAP);

                renderer.decideWhetherTheHoverIsWantedThisFrame();
                renderer.publishHoverForPass(FACTOR);

                assertThat(hoverStateHandedToTheReadSource)
                    .isSameAs(hoverState);
            }
        }

        @Test
        void publishHoverForPassAnnouncesNoArrival() {
            // The moment belongs to the frame, not to a pass. A pass answering one would sound the
            // crossing between two transforms' answers every frame a foreign map draws beside the
            // real one.
            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                var renderer = buildRenderer(NOT_COVERING_THE_MAP);

                renderer.decideWhetherTheHoverIsWantedThisFrame();
                renderer.publishHoverForPass(FACTOR);

                verify(hoverPublisherMock, never())
                    .announceSettledArrival();
            }
        }

        @Test
        void publishHoverForPassReadsNothingWhileTheFrameWantsNoHover() {
            // The frame's decision is what the passes stand down on, so the read - and with it the
            // publisher and the renderer binding it holds - is never reached at all.
            recordNothingInThisCase();

            buildRenderer(NOT_COVERING_THE_MAP)
                .publishHoverForPass(FACTOR);

            verifyNoInteractions(hoverPublisherMock);
        }

        @Test
        void publishHoverForPassMeasuresThisLayersRowInsideItsOwnBeat() {
            // The read is per pass, so its beat is too - a frame painted by several surfaces then
            // reports the reads it actually made rather than one of them. The publisher is a double
            // that opens nothing, so what is pinned is the beat and the row around it.
            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapHighlightSettings.class)) {

                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                var renderer = buildRenderer(NOT_COVERING_THE_MAP);

                renderer.decideWhetherTheHoverIsWantedThisFrame();
                renderer.publishHoverForPass(FACTOR);

                var beat = readOnlyRootOfThisSector();

                assertThat(beat.getSection())
                    .isEqualTo(MapFrameSections.HOVER_PUBLISH);
                assertThat(readSectionsOfChildrenOf(beat))
                    .containsExactly(LAYER_SECTION);
            }
        }

        @Test
        void publishHoverForPassStandingDownMeasuresItsBeatAndNothingBeneathIt() {
            // A pass of a frame that wants no hover still costs the gate that stood it down, and
            // several passes stand down per frame - so the beat says how much a switched-off hover
            // is costing, and opens no row for a read that never ran.
            //
            // The framework's settings are stood in for because the beat is recorded: closing one
            // asks what it was allowed to take, and that knob is read through LunaLib, which no
            // test classpath carries.
            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class)) {

                buildRenderer(NOT_COVERING_THE_MAP)
                    .publishHoverForPass(FACTOR);

                var beat = readOnlyRootOfThisSector();

                assertThat(beat.getSection())
                    .isEqualTo(MapFrameSections.HOVER_PUBLISH);
                assertThat(beat.getChildren())
                    .isEmpty();
            }
        }
    }

    @Nested
    class DisposeMachinery {

        @Test
        void disposeMachineryRunsBeforeAnySectorHasBeenDrawn() {
            // Reached for a sector installed on with the map never opened, when there is nothing
            // built to release.
            assertThatCode(buildRenderer(NOT_COVERING_THE_MAP)::disposeMachinery)
                .doesNotThrowAnyException();
        }
    }

    // Puts the capture the class binds by default back to the silent one, for a case whose subject
    // is what the layer consults rather than what a frame costs.
    //
    // A recording capture reads a knob of its own as each beat closes - what that beat was allowed
    // to take - so a case holding the framework's settings to no interaction at all would be
    // reading a measurement it never asked for as the layer consulting them.
    private static void recordNothingInThisCase() {
        ActiveProfiler.bindProfiler(SilentProfiler.INSTANCE);
    }

    // The renderer under test, over one stated cover and a read it hands out rather than builds.
    // Both are the seams a running game supplies, and stating them here is what lets a frame be
    // driven at all without a map on screen. The cache is made against machinery of its own,
    // which is how a renderer is reached in play - one sector's, and never shared.
    //
    // The read's source records the holder it was handed instead of ignoring it, since what the
    // renderer passes down is the one thing about the wiring no other suite can see.
    private PoliticalMapLayerRenderer buildRenderer(MapCover cover) {
        return buildRendererOver(new PoliticalMapCache(new SectorMapMachinery(null)), cover);
    }

    // The same renderer over a stated cache, for the case whose subject is what the frame hands the
    // refresh rather than what the refresh then does with it.
    private PoliticalMapLayerRenderer buildRendererOver(PoliticalMapCache cache, MapCover cover) {

        var machinery = new SectorMapMachinery(null);

        return new PoliticalMapLayerRenderer(
            cache,
            new MapCoverReader(List.of(cover)),
            hoverState,
            handedHoverState -> {
                hoverStateHandedToTheReadSource = handedHoverState;
                return hoverPublisherMock;
            },
            new PoliticalMapPreviewHighlightRenderer(machinery),
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

    // This sector's group of the capture, empty when nothing was opened under it - which is itself
    // a failure worth reading as one, since a beat that named another origin would otherwise pass
    // as a beat that opened nothing.
    private List<ProfileNode> readRootsOfThisSector() {
        return profiler.snapshot().stream()
            .filter(originTree -> originTree.getOrigin() == SECTOR_ORIGIN)
            .map(ProfileOriginTree::getRoots)
            .findFirst()
            .orElse(List.of());
    }

    // What a row opened inside another, so a case states the shape it expects as sections rather
    // than as nodes it would then have to unwrap one at a time.
    private static List<ProfileSection> readSectionsOfChildrenOf(ProfileNode node) {
        return node.getChildren().stream()
            .map(ProfileNode::getSection)
            .toList();
    }

    // A view the player has picked, on whichever screen a case's frame is prepared for, so the frame
    // work gated behind one is reached. Which view it is decides only what would be painted, which none
    // of these cases gets as far as.
    private static void stubASelectedView(MockedStatic<PoliticalMapViewRegistry> viewRegistryMock) {

        var viewMock = mock(PoliticalMapView.class);

        viewRegistryMock
            .when(PoliticalMapViewRegistry::getActiveView)
            .thenReturn(viewMock);
        viewRegistryMock
            .when(() -> PoliticalMapViewRegistry.resolveActiveViewOn(any()))
            .thenReturn(viewMock);
    }

    // One screen's picks, of no particular identity: the cases below are about the frame reading the
    // showing screen once and carrying it, not about which of the mod's two screens that is.
    private static ScreenLayerPicks createStandInScreenPicks() {
        return new ScreenLayerPicks(
            mock(ActiveLayerSelection.class),
            new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class)),
            ScreenMemoryScopes.createStandInScreen());
    }

    // The three tiers the hover box is switched at, set together: the two above the layer left on,
    // and the layer's own set to what the test is about. Each tier's own arithmetic is
    // PoliticalMapHoverGatesTest's; what is pinned here is which of them this renderer obeys.
    private static void stubTooltipSwitches(
            MockedStatic<KmuMapHoverSettings> frameworkSettingsMock,
            MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock,
            boolean isPoliticalTooltipEnabled) {

        frameworkSettingsMock
            .when(KmuMapHoverSettings::isMapHoveringEnabled)
            .thenReturn(true);

        frameworkSettingsMock
            .when(KmuMapHoverSettings::isMapHoverTooltipEnabled)
            .thenReturn(true);

        layerSettingsMock
            .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverTooltipEnabled)
            .thenReturn(isPoliticalTooltipEnabled);
    }

    // Every tier the effects are switched at, left on, so the cursor read is wanted and the covers
    // are reached. The effects rather than the box because either kind on is enough to want the
    // read, and the effects tier is the one the union asks first.
    private static void stubHoverSwitchesOn(
            MockedStatic<KmuMapHoverSettings> frameworkSettingsMock,
            MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock) {

        frameworkSettingsMock
            .when(KmuMapHoverSettings::isMapHoveringEnabled)
            .thenReturn(true);

        frameworkSettingsMock
            .when(KmuMapHoverSettings::areMapHoverEffectsEnabled)
            .thenReturn(true);

        layerSettingsMock
            .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverEffectsEnabled)
            .thenReturn(true);
    }
}
