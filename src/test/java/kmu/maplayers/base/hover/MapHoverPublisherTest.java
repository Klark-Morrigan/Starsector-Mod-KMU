package kmu.maplayers.base.hover;

import kmlib.starsector.ui.map.transform.MapCursor;
import kmlib.starsector.ui.map.transform.ModelviewMatrixReader;
import kmlib.starsector.ui.sound.StarsectorUiSound;
import kmlib.starsector.ui.sound.UiSoundCue;
import kmlib.testfixtures.starsector.ui.sound.UiSoundPlayerFake;

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
 *
 * <p>Beside the hover sits the moment: which frames are the cursor <em>reaching</em> a cell rather
 * than resting on one. That half is asserted through a recording player, a sound being the one thing
 * this pass does that leaves no trace in the state a case could otherwise read back - and the cases
 * are about the rule that a frame is a moment, never about the sample or the level, both of which
 * arrive already composed from the host's own look.
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

    // The neighbour's own painted cell, abutting the first along the x=300 edge. Drawn as a second
    // cell rather than as another point in the first, so the case about crossing from one cell to
    // the next is a change of cell and not of position.
    private static final List<double[]> NEIGHBOUR_CELL_POLYGON = List.of(
        new double[] {300d, 50d},
        new double[] {500d, 50d},
        new double[] {500d, 250d},
        new double[] {300d, 250d});

    private static final Vector2f POINT_ON_CELL = new Vector2f(200f, 150f);
    private static final Vector2f POINT_ON_NEIGHBOUR_CELL = new Vector2f(400f, 150f);

    // Outside the cell: empty space beyond the map, or the channel between two cells, where the
    // map draws nobody's cell.
    private static final Vector2f POINT_OFF_CELL = new Vector2f(0f, 0f);

    // What the host's look says a cell arriving under the cursor sounds like. Handed in whole, this
    // publisher naming neither the sample nor the level - and at a volume none of the shipped
    // defaults hold, so a publisher composing a cue of its own could not record this one.
    private static final UiSoundCue CELL_ARRIVAL_CUE =
        new UiSoundCue(StarsectorUiSound.TEXT_TYPED, 0.35f);

    // The same tick after the player moved its slider, for the case about when the level is read.
    private static final UiSoundCue RETUNED_CELL_ARRIVAL_CUE =
        new UiSoundCue(StarsectorUiSound.TEXT_TYPED, 0.7f);

    // The frame's targets with one drawn cell, clustered with a neighbour so a published hover
    // proves it carries the whole cluster and not just the cell it resolved.
    private static MapHoverTargets buildTargetsWithOneCell() {
        return buildTargetsWithCells(Map.of(HOVERED_SYSTEM_ID, CELL_POLYGON));
    }

    // Both cells of the cluster drawn, for the cases about the cursor crossing between them.
    private static MapHoverTargets buildTargetsWithTwoCells() {
        return buildTargetsWithCells(Map.of(
            HOVERED_SYSTEM_ID, CELL_POLYGON,
            NEIGHBOUR_SYSTEM_ID, NEIGHBOUR_CELL_POLYGON));
    }

    private static MapHoverTargets buildTargetsWithCells(Map<String, List<double[]>> fillPolygons) {

        var targetsMock = mock(MapHoverTargets.class);

        when(targetsMock.getFillPolygonByCellId())
            .thenReturn(fillPolygons);
            
        when(targetsMock.getClusterIndex())
            .thenReturn(SystemClusterIndex.indexClusters(
                List.of(List.of(HOVERED_SYSTEM_ID, NEIGHBOUR_SYSTEM_ID))));

        return targetsMock;
    }

    @Nested
    class PublishHoverFrom {

        // What the host's look answers an arrival with, mutable so the case about a player who has
        // silenced the tick can state that the way a look does - by naming no cue at all.
        private UiSoundCue cellArrivalCue;
        private MockedStatic<MapCursor> cursorMock;
        private MapHoverState hoverState;
        private ModelviewMatrixReader readerMock;
        private UiSoundPlayerFake soundPlayerFake;

        @BeforeEach
        void setUp() {

            readerMock = mock(ModelviewMatrixReader.class);
            soundPlayerFake = new UiSoundPlayerFake();
            cellArrivalCue = CELL_ARRIVAL_CUE;
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

            buildPublisher()
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
            buildPublisher()
                .publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            cursorMock.verify(() ->
                MapCursor.resolveWorldPointDuringMapPass(MAP_ZOOM, readerMock));
        }

        @Test
        void publishHoverFromParksTheHoverWhenTheCursorIsOverNoCell() {

            stubCursorAt(POINT_OFF_CELL);

            buildPublisher()
                .publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            assertThat(hoverState.getHover())
                .isSameAs(MapHover.NONE);
        }

        @Test
        void publishHoverFromParksTheHoverWhenNothingWasPainted() {
            // No targets at all: a diagnostic overlay stood in for the production draw lists, or
            // the first build has yet to succeed. There are no cell shapes to test against.
            buildPublisher().publishHoverFrom(null, MAP_ZOOM);

            assertThat(hoverState.getHover())
                .isSameAs(MapHover.NONE);
        }

        @Test
        void publishHoverFromParksTheHoverWhenTheCursorCannotBeResolved() {
            // The cursor has left the window, the transform is not the map's, or it will not
            // invert - three failures KMLib reports as one, and all of them mean no hover here.
            // Which is which is MapCursorTest's to pin.
            stubCursorAt(null);

            buildPublisher()
                .publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            assertThat(hoverState.getHover())
                .isSameAs(MapHover.NONE);
        }

        @Test
        void publishHoverFromTicksAsTheCursorReachesACell() {
            // The whole moment: the map answers the cursor getting somewhere, and it answers with
            // what the host's look named rather than with a sample or a level of the pass's own.
            buildPublisher()
                .publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE);
        }

        @Test
        void publishHoverFromTicksOnceWhileTheCursorRestsOnACell() {
            // The pass runs every frame and the cursor is usually still, so a tick per frame is what
            // an unlatched read would give - a cell held under the pointer buzzing until it moves.
            var publisher = buildPublisher();

            publisher.publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);
            publisher.publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE);
        }

        @Test
        void publishHoverFromTicksAgainForACellReachedFromItsNeighbour() {
            // Crossing straight from one cell to the next never leaves the map, so an arrival
            // detected only from off a cell would tick once for a whole sweep across the map. Two
            // cells of one cluster, deliberately: the tick answers the cell the hover box names and
            // not the cluster around it, so a move within one cluster is still a move.
            var publisher = buildPublisher();
            publisher.publishHoverFrom(buildTargetsWithTwoCells(), MAP_ZOOM);

            stubCursorAt(POINT_ON_NEIGHBOUR_CELL);
            publisher.publishHoverFrom(buildTargetsWithTwoCells(), MAP_ZOOM);

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE, CELL_ARRIVAL_CUE);
        }

        @Test
        void publishHoverFromTicksAgainWhenTheCursorReturnsToTheCellItLeft() {
            // Parking has to forget where the cursor was, or a cell left for empty space and come
            // back to is silent - the one return trip a player makes constantly, the map being mostly
            // the space between cells.
            var publisher = buildPublisher();
            publisher.publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            stubCursorAt(POINT_OFF_CELL);
            publisher.publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            stubCursorAt(POINT_ON_CELL);
            publisher.publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE, CELL_ARRIVAL_CUE);
        }

        @Test
        void publishHoverFromReachesACellSilentlyWhenTheLookNamesNoCue() {
            // A player who has pulled the tick's slider to the bottom, which the look states by
            // naming no cue at all. Nothing reaches the player rather than a sound played at nothing.
            cellArrivalCue = null;

            buildPublisher()
                .publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            assertThat(soundPlayerFake.getPlayedCues())
                .isEmpty();
        }

        @Test
        void publishHoverFromTicksAtWhateverTheLookNamesWhenTheMomentComes() {
            // The cue is asked for per arrival rather than held from construction, so a level changed
            // on the settings screen reaches a publisher built when the map first drew - which is the
            // only publisher there is, one being kept for the session.
            var publisher = buildPublisher();
            publisher.publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            cellArrivalCue = RETUNED_CELL_ARRIVAL_CUE;
            
            stubCursorAt(POINT_OFF_CELL);
            publisher.publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            stubCursorAt(POINT_ON_CELL);
            publisher.publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE, RETUNED_CELL_ARRIVAL_CUE);
        }

        // The publisher under test, reading the case's own cue field so a case can retune or silence
        // the look between frames the way the settings screen does between visits.
        private MapHoverPublisher buildPublisher() {
            return new MapHoverPublisher(readerMock, soundPlayerFake, () -> cellArrivalCue);
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
