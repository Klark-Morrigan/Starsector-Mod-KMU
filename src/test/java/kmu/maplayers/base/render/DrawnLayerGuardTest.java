package kmu.maplayers.base.render;

import kmlib.testfixtures.logging.LogAppenderFake;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.machinery.SectorMapMachinery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.function.Function;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the door every pass reaches the drawn layer through: which renderer it hands over, what it
 * answers where nothing draws, and what becomes of a layer that throws behind it - switched off on
 * that sector alone, its hover parked, and said once in the log.
 *
 * <p>Which layer is drawn is the registry's answer and is stood in for here, so a case poses the
 * layer it is about directly rather than through a screen's stored pick.
 */
final class DrawnLayerGuardTest {

    // What a pass reads back where nothing draws, distinct from anything a renderer answers here.
    private static final String NOTHING_DRAWN = "nothing drawn";

    // What the stand-in work answers where it ran.
    private static final String DRAWN = "drawn";

    private final SectorMapMachinery machinery = new SectorMapMachinery(null);

    private final MapLayer drawingLayerMock = mock(MapLayer.class);
    private final MapLayer siblingLayerMock = mock(MapLayer.class);
    private final MapLayerRenderer layerRendererMock = mock(MapLayerRenderer.class);
    private final MapLayerRenderer siblingRendererMock = mock(MapLayerRenderer.class);

    private final DrawnLayerGuard guard = new DrawnLayerGuard(machinery);

    private MockedStatic<MapLayerRegistry> layerRegistryMock;

    @BeforeEach
    void standInForTheDrawnLayer() {

        when(drawingLayerMock.getId())
            .thenReturn("drawing");
        when(drawingLayerMock.resolveRenderer(machinery))
            .thenReturn(layerRendererMock);
        when(siblingLayerMock.resolveRenderer(machinery))
            .thenReturn(siblingRendererMock);

        layerRegistryMock = mockStatic(MapLayerRegistry.class);
        drawTheLayer(drawingLayerMock);
    }

    @AfterEach
    void releaseTheDrawnLayer() {
        layerRegistryMock.close();
    }

    @Nested
    class CallOnDrawnRenderer {

        @Test
        void handsTheWorkTheDrawnLayersRendererOnTheGuardsSector() {
            // The roster is the process's while a renderer is one sector's, so the guard must ask
            // about its own machinery rather than the running game's - one that did would hand every
            // surface the same renderer however many sectors were being drawn. Pinned by stubbing
            // that one machinery and no other.
            assertThat(guard.callOnDrawnRenderer(Function.identity(), null))
                .isSameAs(layerRendererMock);
        }

        @Test
        void answersNothingDrawnWhereTheDrawnLayerPaintsNothing() {
            // The switch-only tab, whose whole expression is a null renderer. Stated rather than left
            // to the stub's own default, or the case would pass on a layer that was never asked.
            when(drawingLayerMock.resolveRenderer(machinery))
                .thenReturn(null);

            assertThat(guard.callOnDrawnRenderer(layerRenderer -> DRAWN, NOTHING_DRAWN))
                .isEqualTo(NOTHING_DRAWN);
        }

        @Test
        void answersNothingDrawnBeforeAnyLayerIsDrawn() {

            drawTheLayer(null);

            assertThat(guard.callOnDrawnRenderer(layerRenderer -> DRAWN, NOTHING_DRAWN))
                .isEqualTo(NOTHING_DRAWN);
        }

        @Test
        void answersNothingDrawnWhereTheWorkThrows() {
            // The pass is what the game drives with nothing under it that catches, so the throw ends
            // here: reaching the caller would end the game with the map open.
            assertThat(guard.callOnDrawnRenderer(DrawnLayerGuardTest::throwFromTheLayer, NOTHING_DRAWN))
                .isEqualTo(NOTHING_DRAWN);
        }

        @Test
        void neverAsksALayerThatThrewAgain() {
            // Off rather than retried: a layer failing on the sector would throw on every frame, and
            // a trace a frame buries the first one.
            failTheDrawnLayer();

            assertThat(guard.callOnDrawnRenderer(layerRenderer -> DRAWN, NOTHING_DRAWN))
                .isEqualTo(NOTHING_DRAWN);

            verify(drawingLayerMock, times(1))
                .resolveRenderer(machinery);
        }

        @Test
        void switchesOffALayerWhoseRendererFailsToLink() {
            // Resolving the renderer is part of the layer's work, and a class it names that no longer
            // links is thrown as an error rather than an exception.
            when(drawingLayerMock.resolveRenderer(machinery))
                .thenThrow(new NoClassDefFoundError("a class the layer's renderer names"));

            guard.callOnDrawnRenderer(Function.identity(), null);

            assertThat(guard.callOnDrawnRenderer(layerRenderer -> DRAWN, NOTHING_DRAWN))
                .isEqualTo(NOTHING_DRAWN);
        }

        @Test
        void goesOnDrawingAnotherLayerOnceOneWasSwitchedOff() {
            // Off is one layer's state, not the sector's: the player picking another tab gets its
            // picture.
            failTheDrawnLayer();
            drawTheLayer(siblingLayerMock);

            assertThat(guard.callOnDrawnRenderer(Function.identity(), null))
                .isSameAs(siblingRendererMock);
        }

        @Test
        void parksTheHoverTheSwitchedOffLayerPublished() {
            // No pass of the layer's is left to read the cursor again and park it, so a cell it lit
            // would otherwise stay lit for the rest of the sector.
            var hoveredKey = buildCellKey("hovered");

            machinery
                .resolveHoverState()
                .publishHover(new MapHover(hoveredKey, List.of(hoveredKey)));

            failTheDrawnLayer();

            assertThat(machinery.resolveHoverState().getHover().isHovering())
                .isFalse();
        }

        @Test
        void logsTheLayerItSwitchedOffAndHowLong() {

            var capture = LogAppenderFake.captureLogOf(
                DrawnLayerGuard.class,
                DrawnLayerGuardTest.this::failTheDrawnLayer);

            assertThat(capture.getMessages())
                .containsExactly("Map layer 'drawing' failed and is switched off on this sector until the"
                    + " next load, or until the map layers are switched off and back on.");
        }
    }

    @Nested
    class RunOnDrawnRenderer {

        @Test
        void runsTheWorkOverTheDrawnLayersRenderer() {

            guard.runOnDrawnRenderer(layerRenderer -> layerRenderer.prepareFrame(1.5f));

            verify(layerRendererMock)
                .prepareFrame(1.5f);
        }

        @Test
        void containsWorkThatThrows() {

            guard.runOnDrawnRenderer(DrawnLayerGuardTest::throwFromTheLayer);
            guard.runOnDrawnRenderer(layerRenderer -> layerRenderer.prepareFrame(1.5f));

            verify(layerRendererMock, never())
                .prepareFrame(1.5f);
        }
    }

    @Nested
    class ResolveGuardIn {

        @Test
        void answersOneGuardPerSector() {
            // Two guards over one sector would be two switches, and a layer switched off on one would
            // go on drawing through the other.
            assertThat(DrawnLayerGuard.resolveGuardIn(machinery))
                .isSameAs(DrawnLayerGuard.resolveGuardIn(machinery));
        }

        @Test
        void startsASectorWithNothingSwitchedOff() {
            // Clearing with the machinery is what makes a load, or the layers switched off and back
            // on, a retry: the sector after it is guarded afresh.
            var nextMachinery = new SectorMapMachinery(null);

            when(drawingLayerMock.resolveRenderer(nextMachinery))
                .thenReturn(layerRendererMock);

            DrawnLayerGuard
                .resolveGuardIn(machinery)
                .runOnDrawnRenderer(DrawnLayerGuardTest::throwFromTheLayer);

            var nextGuard = DrawnLayerGuard.resolveGuardIn(nextMachinery);

            assertThat(nextGuard.callOnDrawnRenderer(Function.identity(), null))
                .isSameAs(layerRendererMock);
        }
    }

    // Poses which layer the showing screen is drawing, or none.
    private void drawTheLayer(MapLayer drawnLayer) {

        layerRegistryMock
            .when(MapLayerRegistry::getDrawnLayer)
            .thenReturn(drawnLayer);
    }

    // Has the drawn layer throw from inside a pass's work, which switches it off.
    private void failTheDrawnLayer() {
        guard.runOnDrawnRenderer(DrawnLayerGuardTest::throwFromTheLayer);
    }

    // A pass's work failing the way a layer's draw does. Typed to answer anything, so one stand-in
    // serves both the work that answers and the work that only runs.
    private static <T> T throwFromTheLayer(MapLayerRenderer layerRenderer) {
        throw new IllegalStateException("draw list half built");
    }
}
