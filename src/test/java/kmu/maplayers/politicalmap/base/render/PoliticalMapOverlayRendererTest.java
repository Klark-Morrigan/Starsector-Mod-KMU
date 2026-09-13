package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.hover.HoverHighlightRenderer;
import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.labels.LabelRenderer;
import kmu.maplayers.base.labels.anchor.ClusterAnchorRenderer;
import kmu.maplayers.base.render.MapFrame;
import kmu.maplayers.base.render.MapOverlayBand;
import kmu.maplayers.base.render.clusters.ClusterRenderer;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageRenderer;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapHoverGates;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapPreviewHighlightRenderer;
import kmu.maplayers.politicalmap.base.render.ribbon.CellPresenceRibbonRenderer;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.settings.KmuPoliticalMapDiagnosticsSettings;
import kmu.settings.KmuPoliticalMapDrawOrderSettings;
import kmu.settings.NebulaDrawOrderChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins that each sub-layer is emitted in the band its own setting placed it in, which is the one
 * thing this compositor decides that the map can see. Under Starscape the two bands are painted from
 * separate terrain surfaces with the map's own nebulae drawn between them, so a sub-layer emitted for
 * the wrong band is drawn on the wrong side of the fog - and there is nothing in the frame's own
 * output to say so.
 *
 * <p>Every test states the layout it drives with, including the one that states the shipped split:
 * with the placement now the player's, a routing fault and a taste change look alike from inside a
 * single frame, and only a layout asked for up front tells them apart. Where the layout is settled
 * and which pairings it refuses is {@code PoliticalMapBandLayoutTest}'s.
 *
 * <p>The emitting passes are mocked out: each is a static GL call that runs only in-engine, and what
 * they draw is their own to cover. What is asserted here is which of them is reached.
 */
final class PoliticalMapOverlayRendererTest {

    // A frame that paints: neither value is read by anything asserted here, the subject being
    // which renderer each band reaches rather than what it emits.
    private static final MapFrame PAINTING_FRAME = new MapFrame(1f, 1f);

    // What the cursor was resolved to on this sector's map, for the case about which holder the
    // highlight traces.
    private static final MapHover HOVERED_CELL = new MapHover(
        buildCellKey("system_id"),
        List.of(buildCellKey("system_id")));

    // The hover holder of the sector this compositor's draw lists belong to. Made per case, since a
    // compositor is built with the holder of its own installed machinery.
    private final MapHoverState hoverState = new MapHoverState();

    // The picker preview, stood in for. It is the one sub-layer the compositor is handed rather
    // than builds, so it is also the one whose band can be read straight off the seam instead of
    // through the pass it would emit. Declared above the compositor, which is built from it.
    private final PoliticalMapPreviewHighlightRenderer previewHighlightRendererMock =
        mock(PoliticalMapPreviewHighlightRenderer.class);

    private final PoliticalMapOverlayRenderer overlayRenderer = buildOverlayRenderer();

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapEmitsTheTerritoriesForTheBandBeneathTheNebulae() {
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var ribbonRendererMock = mockStatic(CellPresenceRibbonRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubGeometryBelowAndReadoutsAbove(drawOrderSettingsMock);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                overlayRenderer.renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                // Both halves, since the framework hands the territories out as two entries and
                // this band is what puts them back together - a compositor that emitted only one
                // would draw fills with no borders, or borders round nothing.
                clusterRendererMock.verify(() ->
                    ClusterRenderer.renderFillsOnMap(any(), any()));
                clusterRendererMock.verify(() ->
                    ClusterRenderer.renderBordersOnMap(any(), any()));

                anchorRendererMock
                    .verifyNoInteractions();
                labelRendererMock
                    .verifyNoInteractions();
                ribbonRendererMock
                    .verifyNoInteractions();
            }
        }

        @Test
        void renderOnMapEmitsTheFillsBeforeTheBordersInTheBandBeneathTheNebulae() {
            // The order is the whole reason the framework's two entries can be reached separately,
            // and it is not recoverable from the frame: a fill drawn over its own border leaves a
            // blank cell, and every other assertion in this class passes with the two swapped.
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubGeometryBelowAndReadoutsAbove(drawOrderSettingsMock);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                overlayRenderer.renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                // The ordering context is opened on the mocked class rather than on the handle:
                // a static mock registers the class itself as the thing interactions are recorded
                // against, and the handle is only how they are asked for.
                var clusterCallOrder = inOrder(ClusterRenderer.class);

                clusterCallOrder.verify(clusterRendererMock, () ->
                    ClusterRenderer.renderFillsOnMap(any(), any()));
                clusterCallOrder.verify(clusterRendererMock, () ->
                    ClusterRenderer.renderBordersOnMap(any(), any()));
            }
        }

        @Test
        void renderOnMapEmitsThePresenceBandsAndTheFactionNamesForTheBandAboveTheNebulae() {
            // The two sub-layers that are read rather than merely seen, and the reason the band
            // exists: text stops being readable under the fog well before a fill stops reading as
            // territory, and a band that says how a system is split fails the same way.
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var ribbonRendererMock = mockStatic(CellPresenceRibbonRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubGeometryBelowAndReadoutsAbove(drawOrderSettingsMock);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                overlayRenderer.renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                ribbonRendererMock.verify(() ->
                    CellPresenceRibbonRenderer.renderOnMap(
                        any(),
                        any()));

                labelRendererMock.verify(() ->
                    LabelRenderer.renderOnMap(any(), any()));

                clusterRendererMock
                    .verifyNoInteractions();
                anchorRendererMock
                    .verifyNoInteractions();
            }
        }

        @Test
        void renderOnMapEmitsTheHoverHighlightAndTheAnchorsForTheBandBeneathTheNebulae() {
            // Driven with both switches on, which is what makes this a statement about bands rather
            // than about toggles: with them off, a sub-layer moved to the wrong band still emits
            // nothing and every assertion in the two tests above goes on passing.
            try (var hoverRendererConstructionMock = mockConstruction(HoverHighlightRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubGeometryBelowAndReadoutsAbove(drawOrderSettingsMock);
                openTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                // Built inside the construction mock, since the cursor's highlight renderer is a
                // field this compositor creates for itself - unlike the picker preview beside it,
                // there is no seam to inject one through.
                buildOverlayRenderer().renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                anchorRendererMock.verify(() ->
                    ClusterAnchorRenderer.renderOnMap(any(), any()));

                verify(hoverRendererConstructionMock.constructed().get(0))
                    .renderCursorHighlightOnMap(any(), any(), any(), any());
            }
        }

        @Test
        void renderOnMapLightsTheCellItsOwnHoverHolderNames() {
            // The highlight brightens a cell the cache's draw lists cut, so the hover it traces has
            // to be the one the pass over those very lists published. Read off whichever sector was
            // running instead, this would wash a cell the sector it is compositing never drew -
            // nothing forbidding two sectors from holding a system under the same id.
            hoverState.publishHover(HOVERED_CELL);

            try (var hoverRendererConstructionMock = mockConstruction(HoverHighlightRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubGeometryBelowAndReadoutsAbove(drawOrderSettingsMock);
                openTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                buildOverlayRenderer().renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                verify(hoverRendererConstructionMock.constructed().get(0))
                    .renderCursorHighlightOnMap(
                        any(),
                        any(),
                        eq(HOVERED_CELL),
                        any());
            }
        }

        @Test
        void renderOnMapLeavesTheHoverHighlightAndTheAnchorsOutOfTheBandAboveTheNebulae() {
            // The half of the pinning that catches a sub-layer promoted by accident: both switches
            // are on, so anything reached here is reached because of the band it was asked for.
            try (var hoverRendererConstructionMock = mockConstruction(HoverHighlightRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubGeometryBelowAndReadoutsAbove(drawOrderSettingsMock);
                openTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                buildOverlayRenderer().renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                anchorRendererMock
                    .verifyNoInteractions();

                // Held to exactly one stand-in before it is asserted against: an empty list would
                // satisfy "none of them drew" without the compositor ever having had a highlight
                // renderer to leave alone, which is the shape of passing test this group exists to
                // stop.
                assertThat(hoverRendererConstructionMock.constructed())
                    .singleElement()
                    .satisfies(hoverRenderer -> verifyNoInteractions(hoverRenderer));
            }
        }

        @Test
        void renderOnMapEmitsThePickerPreviewForTheBandBeneathTheNebulae() {
            // The preview brightens the fills for a whole bloc, so it rides with them for the
            // reason the cursor's highlight does: left beneath while the fills went above, it
            // would be painted over and light nothing.
            //
            // Both hover switches are silenced, which is the half of this that the cursor's
            // highlight cannot state: the preview answers a pointer on the sidebar rather than one
            // on the map, so a gate borrowed from the cursor would take it dark with the map's own
            // feedback switched off.
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubGeometryBelowAndReadoutsAbove(drawOrderSettingsMock);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                buildOverlayRenderer().renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                verify(previewHighlightRendererMock)
                    .renderPreviewOnMap(any(), any());
            }
        }

        @Test
        void renderOnMapLeavesThePickerPreviewOutOfTheBandAboveTheNebulae() {
            // The other half of that pinning: the preview reaches the upper band only if it were
            // promoted out of the fills it brightens, which no frame's own output would show.
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubGeometryBelowAndReadoutsAbove(drawOrderSettingsMock);
                openTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                buildOverlayRenderer().renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                verifyNoInteractions(previewHighlightRendererMock);
            }
        }

        @Test
        void renderOnMapEmitsTheDebugBorderStageForTheBandBeneathTheNebulae() {
            // The debug overlay replaces the territories rather than layering over them, so it sits
            // in the band they would have occupied - a swap inside one band, not a band of its own.
            try (var borderStageRendererMock = mockStatic(ClusterBorderStageRenderer.class);
                    var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubGeometryBelowAndReadoutsAbove(drawOrderSettingsMock);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                overlayRenderer.renderOnMap(
                    buildDebugCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                borderStageRendererMock.verify(() ->
                    ClusterBorderStageRenderer.renderOnMap(any(), any()));

                clusterRendererMock
                    .verifyNoInteractions();

                // The preview stands down with them: the overlay replaced the very draw lists it
                // would have traced, so there is no painted cell for a lit set to be clipped to.
                verifyNoInteractions(previewHighlightRendererMock);
            }
        }

        @Test
        void renderOnMapEmitsNoPresenceBandsUnderTheDebugOverlayInTheBandAboveTheNebulae() {
            // The bands are baked into the territories, and a debug frame built the border-stage
            // overlay instead of them - so there is nothing to paint them from, and asking would
            // reach through a frame this cache never built. The names are unaffected, being held
            // by the cache in their own right, and are asserted so the guard is seen to stop one
            // sub-layer rather than the whole band.
            try (var borderStageRendererMock = mockStatic(ClusterBorderStageRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var ribbonRendererMock = mockStatic(CellPresenceRibbonRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubGeometryBelowAndReadoutsAbove(drawOrderSettingsMock);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                overlayRenderer.renderOnMap(
                    buildDebugCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                ribbonRendererMock
                    .verifyNoInteractions();

                labelRendererMock.verify(() ->
                    LabelRenderer.renderOnMap(any(), any()));
            }
        }

        @Test
        void renderOnMapEmitsTheTerritoriesAboveTheNebulaeWhereTheFillsWereRaised() {
            // The whole of what the setting buys, and the half that cannot be read off the shipped
            // split: the geometry drawn clear of the fog rather than through it. The borders come
            // with the fills whatever the borders row says, which is the layout's rule showing here
            // as the picture it exists to protect.
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubChosenDrawOrders(
                    drawOrderSettingsMock,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                overlayRenderer.renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                clusterRendererMock.verify(() ->
                    ClusterRenderer.renderFillsOnMap(any(), any()));
                clusterRendererMock.verify(() ->
                    ClusterRenderer.renderBordersOnMap(any(), any()));
            }
        }

        @Test
        void renderOnMapLeavesTheTerritoriesOutOfTheBandBeneathTheNebulaeWhereTheFillsWereRaised() {
            // The other half of a move: a sub-layer that arrived in its new band while still being
            // emitted in the old one is drawn twice, which on a translucent fill reads as one
            // painted at twice the opacity the player set.
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubChosenDrawOrders(
                    drawOrderSettingsMock,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                overlayRenderer.renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                clusterRendererMock
                    .verifyNoInteractions();
            }
        }

        @Test
        void renderOnMapEmitsTheBordersAloneInTheBandTheyWereRaisedTo() {
            // The pairing the layout does offer, and the one the compositor could most easily fail
            // to honour by treating the geometry as a single sub-layer: borders lifted clear of a
            // fill left in the fog.
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubChosenDrawOrders(
                    drawOrderSettingsMock,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                overlayRenderer.renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                clusterRendererMock.verify(() ->
                    ClusterRenderer.renderBordersOnMap(any(), any()));

                clusterRendererMock.verify(
                    () -> ClusterRenderer.renderFillsOnMap(any(), any()),
                    never());
            }
        }

        @Test
        void renderOnMapEmitsThePresenceBandsAndTheFactionNamesBeneathTheNebulaeWhereLowered() {
            // The two readouts moved the other way. They are the sub-layers a player is most likely
            // to move - the fog is the reason they were placed above in the first place - so a
            // routing fault here is one the shipped split would never show.
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var ribbonRendererMock = mockStatic(CellPresenceRibbonRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubChosenDrawOrders(
                    drawOrderSettingsMock,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.BELOW);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                overlayRenderer.renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                ribbonRendererMock.verify(() ->
                    CellPresenceRibbonRenderer.renderOnMap(any(), any()));

                labelRendererMock.verify(() ->
                    LabelRenderer.renderOnMap(any(), any()));
            }
        }

        @Test
        void renderOnMapCarriesTheHoverHighlightAndTheAnchorsWithTheRaisedFills() {
            // Neither has a setting of its own, and neither survives being left behind: a halo under
            // the fill it brightens lights nothing, and anchors mark placements on a base view that
            // is no longer beneath them. Both switches are open, so what is observed is the band
            // they were carried to rather than the toggles.
            try (var hoverRendererConstructionMock = mockConstruction(HoverHighlightRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubChosenDrawOrders(
                    drawOrderSettingsMock,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE);
                openTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                buildOverlayRenderer().renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                anchorRendererMock.verify(() ->
                    ClusterAnchorRenderer.renderOnMap(any(), any()));

                verify(hoverRendererConstructionMock.constructed().get(0))
                    .renderCursorHighlightOnMap(any(), any(), any(), any());
            }
        }

        @Test
        void renderOnMapCarriesTheDebugBorderStageWithTheRaisedFills() {
            // The tracing overlay replaces fills and borders in one pass, so it has no split of its
            // own to honour and follows the view it stands in for. Left on the fills' shipped band
            // while the fills rose, a debug frame would paint on the far side of the fog from every
            // production one it is compared against.
            try (var borderStageRendererMock = mockStatic(ClusterBorderStageRenderer.class);
                    var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubChosenDrawOrders(
                    drawOrderSettingsMock,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                overlayRenderer.renderOnMap(
                    buildDebugCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                borderStageRendererMock.verify(() ->
                    ClusterBorderStageRenderer.renderOnMap(any(), any()));

                clusterRendererMock
                    .verifyNoInteractions();
            }
        }

        @Test
        void renderOnMapLeavesTheHoverHighlightAndTheAnchorsOutOfTheBandTheRaisedFillsLeft() {
            // A rider gated on its own band as well as on the fills' would emit in both, which the
            // test above cannot see: it observes the band the pair arrived in and says nothing about
            // the one they came from. Twice-drawn hover feedback is a wash at twice its opacity.
            try (var hoverRendererConstructionMock = mockConstruction(HoverHighlightRenderer.class);
                    var anchorRendererMock = mockStatic(ClusterAnchorRenderer.class);
                    var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubChosenDrawOrders(
                    drawOrderSettingsMock,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE,
                    NebulaDrawOrderChoice.ABOVE);
                openTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                buildOverlayRenderer().renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                anchorRendererMock
                    .verifyNoInteractions();

                // Held to exactly one stand-in first, so "none of them drew" cannot pass on there
                // having been no highlight renderer at all.
                assertThat(hoverRendererConstructionMock.constructed())
                    .singleElement()
                    .satisfies(hoverRenderer -> verifyNoInteractions(hoverRenderer));
            }
        }

        @Test
        void renderOnMapLeavesThePresenceBandsAndTheFactionNamesOutOfTheBandTheyWereLoweredFrom() {
            // The same absence for the two readouts, which the shipped split pins only the other way
            // round. A band left emitting on its old side as well as its new one is drawn twice over
            // the same cell, and the presence bands are translucent.
            try (var clusterRendererMock = mockStatic(ClusterRenderer.class);
                    var labelRendererMock = mockStatic(LabelRenderer.class);
                    var ribbonRendererMock = mockStatic(CellPresenceRibbonRenderer.class);
                    var hoverGatesMock = mockStatic(PoliticalMapHoverGates.class);
                    var drawOrderSettingsMock = mockStatic(KmuPoliticalMapDrawOrderSettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuPoliticalMapDiagnosticsSettings.class)) {

                NebulaDrawOrderFixtures.stubChosenDrawOrders(
                    drawOrderSettingsMock,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.BELOW,
                    NebulaDrawOrderChoice.BELOW);
                silenceTheTogglesTheBandsDoNotDecide(hoverGatesMock, diagnosticsSettingsMock);

                overlayRenderer.renderOnMap(
                    buildCacheMock(),
                    PAINTING_FRAME,
                    MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

                ribbonRendererMock
                    .verifyNoInteractions();
                labelRendererMock
                    .verifyNoInteractions();
                clusterRendererMock
                    .verifyNoInteractions();
            }
        }
    }

    // The two switches that gate sub-layers within a band rather than deciding which band they are
    // in, both open. What each switch decides is its own gate's to cover; what they allow here is a
    // sub-layer to be observed in the band that asked for it.
    private static void openTheTogglesTheBandsDoNotDecide(
            MockedStatic<PoliticalMapHoverGates> hoverGatesMock,
            MockedStatic<KmuPoliticalMapDiagnosticsSettings> diagnosticsSettingsMock) {

        hoverGatesMock
            .when(PoliticalMapHoverGates::isHoverEffectsEnabled)
            .thenReturn(true);

        diagnosticsSettingsMock
            .when(KmuPoliticalMapDiagnosticsSettings::getPoliticalMapShowClusterAnchors)
            .thenReturn(true);
    }

    // The two switches that gate sub-layers within a band rather than deciding which band they are
    // in. Held off so each test observes the band split alone; what each switch does is its own
    // gate's to cover.
    private static void silenceTheTogglesTheBandsDoNotDecide(
            MockedStatic<PoliticalMapHoverGates> hoverGatesMock,
            MockedStatic<KmuPoliticalMapDiagnosticsSettings> diagnosticsSettingsMock) {

        hoverGatesMock
            .when(PoliticalMapHoverGates::isHoverEffectsEnabled)
            .thenReturn(false);

        diagnosticsSettingsMock
            .when(KmuPoliticalMapDiagnosticsSettings::getPoliticalMapShowClusterAnchors)
            .thenReturn(false);
    }

    // The compositor under test, over this case's own hover holder and its own preview seam.
    private PoliticalMapOverlayRenderer buildOverlayRenderer() {
        return new PoliticalMapOverlayRenderer(hoverState, previewHighlightRendererMock);
    }

    // A cache holding a built, non-debug frame with nothing in it. The bands are decided on the band
    // alone, so an empty frame exercises the split without any geometry having to exist - the styled
    // cells are stubbed only because the compositor's one-shot debug line counts them.
    private static PoliticalMapCache buildCacheMock() {

        var territoriesMock = mock(PoliticalMapTerritories.class);

        when(territoriesMock.getStyledCellByCellKey())
            .thenReturn(Map.of());

        // Stated rather than left to the default a mock would answer with, since the upper band is
        // asserted to have reached the band pass at all - and a stub that resolves by accident is
        // one the pass could stop calling without any test noticing. The diagnostic paths beside
        // them are stated for the same reason: the overlay is gated by its own emptiness rather
        // than by a settings read, so this read is the only thing standing between an off toggle
        // and a pass over the map.
        when(territoriesMock.getPaintedCells().getRibbonByCellKey())
            .thenReturn(Map.of());
        when(territoriesMock.getPaintedCells().getRibbonPathByCellKey())
            .thenReturn(Map.of());

        // Read while assembling the hover highlight's arguments, so it has to resolve even though
        // the highlight renderer itself is stood in for.
        when(territoriesMock.getGlobalStyle())
            .thenReturn(mock(GlobalStyle.class));

        var cacheMock = mock(PoliticalMapCache.class);

        when(cacheMock.isDebug())
            .thenReturn(false);
        when(cacheMock.getTerritories())
            .thenReturn(territoriesMock);
        when(cacheMock.getFactionLabels())
            .thenReturn(List.of());

        return cacheMock;
    }

    // A cache holding a debug frame instead: the cache builds the border-stage overlay or the
    // territories, never both, so this is the other of the two states the lower band has to paint.
    // The loops are stubbed empty for the reason the styled cells are - the one-shot debug line
    // counts them.
    private static PoliticalMapCache buildDebugCacheMock() {

        var borderStageOverlayMock = mock(ClusterBorderStageOverlay.class);

        when(borderStageOverlayMock.baseLoops())
            .thenReturn(List.of());

        var cacheMock = mock(PoliticalMapCache.class);

        when(cacheMock.isDebug())
            .thenReturn(true);
        when(cacheMock.getBorderStageOverlay())
            .thenReturn(borderStageOverlayMock);

        return cacheMock;
    }
}
