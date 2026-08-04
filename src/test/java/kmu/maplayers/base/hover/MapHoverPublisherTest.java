package kmu.maplayers.base.hover;

import kmlib.starsector.ui.map.transform.MapCursor;
import kmlib.starsector.ui.map.transform.ModelviewMatrixReader;

import kmu.maplayers.base.geometry.SystemClusterIndex;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins what the cursor read publishes for a frame: the cell under the cursor with the cluster
 * around it, or a parked hover whenever any input the read depends on is missing. Parking is the
 * whole point of the guards - a wrong hover would wash the wrong cell and answer the tooltip with
 * the wrong system, so every reason the read cannot be trusted has to mean "no hover" and never
 * "a guess".
 *
 * <p>The cursor is stubbed at {@link MapCursor} rather than driven through GL matrices and a
 * mocked mouse, because the pixel-to-world inversion is KMLib's and pinned there. What is left to
 * this publisher - and what these cases exercise - is the step from a world point to a published
 * hover. The targets are a bare {@link MapHoverTargets} for the same reason: a test reaching for a
 * particular layer's build would re-couple exactly what keeps this usable by a second one.
 */
final class MapHoverPublisherTest {

    // A non-trivial zoom, so a publisher that failed to thread the factor through to the cursor
    // read could not pass on the argument assertion below.
    private static final float MAP_ZOOM = 2f;

    private static final String HOVERED_SYSTEM_ID = "corvus";
    private static final String NEIGHBOUR_SYSTEM_ID = "yma";

    // The hovered system's painted cell: a square spanning 100..300 by 50..250 in world
    // coordinates, wide enough that the point below lands well inside it rather than on an edge,
    // where two abutting cells could both claim it.
    private static final List<double[]> CELL_POLYGON = List.of(
        new double[] {100d, 50d},
        new double[] {300d, 50d},
        new double[] {300d, 250d},
        new double[] {100d, 250d});

    private static final Vector2f POINT_ON_CELL = new Vector2f(200f, 150f);

    // Outside the cell: empty space beyond the map, or the channel between two cells, where the
    // map draws nobody's cell.
    private static final Vector2f POINT_OFF_CELL = new Vector2f(0f, 0f);

    // The frame's targets with one drawn cell, clustered with a neighbour so a published hover
    // proves it carries the whole cluster and not just the cell it resolved.
    private static MapHoverTargets buildTargetsWithOneCell() {
        var targetsMock = mock(MapHoverTargets.class);

        when(targetsMock.getFillPolygonByCellId())
            .thenReturn(Map.of(HOVERED_SYSTEM_ID, CELL_POLYGON));
        when(targetsMock.getClusterIndex())
            .thenReturn(SystemClusterIndex.indexClusters(
                List.of(List.of(HOVERED_SYSTEM_ID, NEIGHBOUR_SYSTEM_ID))));

        return targetsMock;
    }

    @Nested
    class PublishHoverFrom {

        private MockedStatic<MapCursor> cursorMock;
        private MapHoverState hoverState;
        private ModelviewMatrixReader readerMock;

        @BeforeEach
        void setUp() {
            readerMock = mock(ModelviewMatrixReader.class);
            cursorMock = mockStatic(MapCursor.class);
            stubCursorAt(POINT_ON_CELL);

            // A standing hover from an earlier frame, so a parking assertion distinguishes "parked"
            // from "left alone": both publish nothing new, only the first clears.
            hoverState = MapHoverState.getInstance();
            hoverState.publishHover(new MapHover("stale", List.of("stale")));
        }

        @AfterEach
        void tearDown() {
            // The holder is shared, so what this test planted must not reach another.
            hoverState.clearHover();
            cursorMock.close();
        }

        @Test
        void publishHoverFromPublishesTheHoveredCellWithItsCluster() {
            new MapHoverPublisher(readerMock)
                .publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            // The cell resolves only if the world point reached the hit test, and the neighbour
            // rides along only if the cluster was looked up off the cell that resolved.
            assertThat(hoverState.getHover().hoveredSystemId())
                .isEqualTo(HOVERED_SYSTEM_ID);
            assertThat(hoverState.getHover().clusterMemberSystemIds())
                .containsExactly(HOVERED_SYSTEM_ID, NEIGHBOUR_SYSTEM_ID);
        }

        @Test
        void publishHoverFromReadsTheCursorThroughTheBindingItWasBuiltWith() {
            // Which reader binds is the caller's decision, so a publisher that resolved its own
            // would silently ignore the choice - and pick the wrong one under Fast Rendering.
            new MapHoverPublisher(readerMock)
                .publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            cursorMock.verify(() ->
                MapCursor.resolveWorldPointDuringMapPass(MAP_ZOOM, readerMock));
        }

        @Test
        void publishHoverFromParksTheHoverWhenTheCursorIsOverNoCell() {
            stubCursorAt(POINT_OFF_CELL);

            new MapHoverPublisher(readerMock)
                .publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            assertThat(hoverState.getHover())
                .isSameAs(MapHover.NONE);
        }

        @Test
        void publishHoverFromParksTheHoverWhenNothingWasPainted() {
            // No targets at all: a diagnostic overlay stood in for the production draw lists, or
            // the first build has yet to succeed. There are no cell shapes to test against.
            new MapHoverPublisher(readerMock).publishHoverFrom(null, MAP_ZOOM);

            assertThat(hoverState.getHover())
                .isSameAs(MapHover.NONE);
        }

        @Test
        void publishHoverFromParksTheHoverWhenTheCursorCannotBeResolved() {
            // The cursor has left the window, the transform is not the map's, or it will not
            // invert - three failures KMLib reports as one, and all of them mean no hover here.
            // Which is which is MapCursorTest's to pin.
            stubCursorAt(null);

            new MapHoverPublisher(readerMock)
                .publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            assertThat(hoverState.getHover())
                .isSameAs(MapHover.NONE);
        }

        private void stubCursorAt(Vector2f worldPoint) {
            cursorMock
                .when(() -> MapCursor.resolveWorldPointDuringMapPass(
                    eq(MAP_ZOOM),
                    any(ModelviewMatrixReader.class)))
                .thenReturn(worldPoint);
        }
    }
}
