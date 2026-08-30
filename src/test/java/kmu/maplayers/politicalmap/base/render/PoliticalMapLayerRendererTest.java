package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.hover.MapHoverPublisher;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.hover.cover.MapCover;
import kmu.maplayers.base.hover.cover.MapCoverReader;
import kmu.maplayers.base.render.MapOverlayBand;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.settings.KmuMapLayerSettings;
import kmu.settings.KmuPoliticalMapSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
 * covered cursor parks the hover rather than leaving it standing, and that a game load leaves
 * nothing of the previous sector behind. What the discard actually empties is
 * {@link PoliticalMapCacheTest}'s; the cache refresh and the GL emission run only in-engine and are
 * covered by their own collaborators.
 *
 * <p>Beside those sits the split between a frame and its passes, which is this renderer's alone to
 * get right: what the frame settles once - whether a hover is wanted at all, and the moment the
 * frame before ended on - against what each pass asks of its own transform. The read itself needs a
 * live map, so it arrives as a source this test hands a double to; what is pinned is which call
 * reaches it and with what.
 */
final class PoliticalMapLayerRendererTest {

    private static final float FACTOR = 1f;
    private static final float ALPHA_MULT = 1f;

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

    @Nested
    class PrepareFrame {

        @Test
        void prepareFrameStandsDownWhileNoViewIsSelected() {
            // The tab is open with every view deselected. Standing down on that one read is what
            // keeps a dark overlay near-free per frame: nothing downstream is consulted, not even the
            // hover toggle that gates the cheapest of the work below it.
            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(null);

                PoliticalMapLayerRenderer.INSTANCE.prepareFrame(FACTOR);

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
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubASelectedView(viewRegistryMock);
                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                var renderer = buildRenderer(NOT_COVERING_THE_MAP);

                renderer.decideWhetherTheHoverIsWantedThisFrame();
                renderer.publishHoverForPass(FACTOR);
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
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubASelectedView(viewRegistryMock);
                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                buildRenderer(NOT_COVERING_THE_MAP).prepareFrame(FACTOR);

                verifyNoInteractions(hoverPublisherMock);
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
            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(null);

                PoliticalMapLayerRenderer.INSTANCE.renderOnMap(FACTOR, ALPHA_MULT, band);

                frameworkSettingsMock
                    .verifyNoInteractions();
                layerSettingsMock
                    .verifyNoInteractions();
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
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubTooltipSwitches(frameworkSettingsMock, layerSettingsMock, true);

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(viewMock);

                assertThat(PoliticalMapLayerRenderer.INSTANCE.resolveHoverTooltip())
                    .contains(tooltipMock);
            }
        }

        @Test
        void resolveHoverTooltipIsEmptyWhileThisLayersTooltipSwitchIsOff() {
            // The layer withholds its box by offering none, which is how one layer's box goes dark
            // while every other layer's stays up - the dispatcher above draws whatever it is offered.
            var viewMock = mock(PoliticalMapView.class);

            try (var viewRegistryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubTooltipSwitches(frameworkSettingsMock, layerSettingsMock, false);

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(viewMock);

                assertThat(PoliticalMapLayerRenderer.INSTANCE.resolveHoverTooltip())
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
                    var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubTooltipSwitches(frameworkSettingsMock, layerSettingsMock, true);

                viewRegistryMock
                    .when(PoliticalMapViewRegistry::getActiveView)
                    .thenReturn(null);

                assertThat(PoliticalMapLayerRenderer.INSTANCE.resolveHoverTooltip())
                    .isEmpty();
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
            MapHoverState.resolveLiveSectorHoverState().publishHover(HOVERED_CELL);

            try (var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                buildRenderer(COVERING_THE_MAP)
                    .decideWhetherTheHoverIsWantedThisFrame();

                assertThat(MapHoverState.resolveLiveSectorHoverState().getHover())
                    .isEqualTo(MapHover.NONE);
            }
        }

        @Test
        void decideWhetherTheHoverIsWantedThisFrameParksTheHoverWhileEveryHoverSwitchIsOff() {
            // The other park, pinned beside it so the covered one cannot be read as the only way a
            // stale cell is dropped: with both kinds of feedback switched off there is nothing that
            // wants the answer, and the frame's passes read no cursor at all.
            MapHoverState.resolveLiveSectorHoverState().publishHover(HOVERED_CELL);

            try (var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                frameworkSettingsMock
                    .when(KmuMapLayerSettings::getMapHoveringEnabled)
                    .thenReturn(false);

                buildRenderer(NOT_COVERING_THE_MAP)
                    .decideWhetherTheHoverIsWantedThisFrame();

                assertThat(MapHoverState.resolveLiveSectorHoverState().getHover())
                    .isEqualTo(MapHover.NONE);
            }
        }

        @Test
        void decideWhetherTheHoverIsWantedThisFrameAsksNoCoverWhileEveryHoverSwitchIsOff() {
            // The covers are the dear half - the last of them walks the live widget tree - so a
            // player who wants no feedback at all must not pay for a walk that could only refine an
            // answer nobody asked for.
            var coverReadCount = new int[1];

            try (var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                frameworkSettingsMock
                    .when(KmuMapLayerSettings::getMapHoveringEnabled)
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
            try (var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubHoverSwitchesOn(frameworkSettingsMock, layerSettingsMock);

                var renderer = buildRenderer(NOT_COVERING_THE_MAP);

                renderer.decideWhetherTheHoverIsWantedThisFrame();
                renderer.publishHoverForPass(FACTOR);

                verify(hoverPublisherMock)
                    .publishHoverFrom(null, FACTOR);
            }
        }

        @Test
        void publishHoverForPassAnnouncesNoArrival() {
            // The moment belongs to the frame, not to a pass. A pass answering one would sound the
            // crossing between two transforms' answers every frame a foreign map draws beside the
            // real one.
            try (var frameworkSettingsMock = mockStatic(KmuMapLayerSettings.class);
                    var layerSettingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

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
            buildRenderer(NOT_COVERING_THE_MAP)
                .publishHoverForPass(FACTOR);

            verifyNoInteractions(hoverPublisherMock);
        }
    }

    @Nested
    class DiscardStateFromPreviousSave {

        @Test
        void discardStateFromPreviousSaveRunsBeforeAnySectorHasBeenDrawn() {
            // Called on every load including the session's first, when there is nothing built to drop
            // and no hover to park.
            assertThatCode(PoliticalMapLayerRenderer.INSTANCE::discardStateFromPreviousSave)
                .doesNotThrowAnyException();
        }
    }

    // The renderer under test, over one stated cover and a read it hands out rather than builds.
    // Both are the seams a running game supplies, and stating them here is what lets a frame be
    // driven at all without a map on screen.
    private PoliticalMapLayerRenderer buildRenderer(MapCover cover) {
        return new PoliticalMapLayerRenderer(
            new MapCoverReader(List.of(cover)), () -> hoverPublisherMock);
    }

    // A view the player has picked, so the frame work gated behind one is reached. Which view it is
    // decides only what would be painted, which none of these cases gets as far as.
    private static void stubASelectedView(MockedStatic<PoliticalMapViewRegistry> viewRegistryMock) {

        viewRegistryMock
            .when(PoliticalMapViewRegistry::getActiveView)
            .thenReturn(mock(PoliticalMapView.class));
    }

    // The three tiers the hover box is switched at, set together: the two above the layer left on,
    // and the layer's own set to what the test is about. Each tier's own arithmetic is
    // PoliticalMapHoverGatesTest's; what is pinned here is which of them this renderer obeys.
    private static void stubTooltipSwitches(
            MockedStatic<KmuMapLayerSettings> frameworkSettingsMock,
            MockedStatic<KmuPoliticalMapSettings> layerSettingsMock,
            boolean isPoliticalTooltipEnabled) {

        frameworkSettingsMock
            .when(KmuMapLayerSettings::getMapHoveringEnabled)
            .thenReturn(true);

        frameworkSettingsMock
            .when(KmuMapLayerSettings::getMapHoverTooltipEnabled)
            .thenReturn(true);

        layerSettingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapHoverTooltipEnabled)
            .thenReturn(isPoliticalTooltipEnabled);
    }

    // Every tier the effects are switched at, left on, so the cursor read is wanted and the covers
    // are reached. The effects rather than the box because either kind on is enough to want the
    // read, and the effects tier is the one the union asks first.
    private static void stubHoverSwitchesOn(
            MockedStatic<KmuMapLayerSettings> frameworkSettingsMock,
            MockedStatic<KmuPoliticalMapSettings> layerSettingsMock) {

        frameworkSettingsMock
            .when(KmuMapLayerSettings::getMapHoveringEnabled)
            .thenReturn(true);

        frameworkSettingsMock
            .when(KmuMapLayerSettings::getMapHoverEffectsEnabled)
            .thenReturn(true);

        layerSettingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapHoverEffectsEnabled)
            .thenReturn(true);
    }
}
