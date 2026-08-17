package kmu.maplayers.base.hover;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.map.transform.CampaignMapTransform;
import kmlib.starsector.ui.map.transform.MapCursor;
import kmlib.starsector.ui.map.transform.MapCursorRead;
import kmlib.starsector.ui.map.transform.ModelviewMatrixReader;
import kmlib.starsector.ui.sound.StarsectorUiSound;
import kmlib.starsector.ui.sound.UiSoundCue;
import kmlib.testfixtures.starsector.ui.sound.UiSoundPlayerFake;

import kmu.maplayers.base.geometry.SystemClusterIndex;

import org.apache.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
 *
 * <p>The two are driven at the rates the map drives them at, which is what the split between the
 * nested classes is: a read per pass, several of which can paint one frame, and a moment per frame
 * over whatever those passes settled on. The parks divide there where they do not divide over the
 * hover: a hit-test that found nothing is the cursor having left a cell, while a pass whose inputs
 * never arrived is no reading at all and leaves where the cursor was seen alone.
 */
final class MapHoverPublisherTest {

    // A non-trivial zoom, so a publisher that failed to thread the factor through to the cursor
    // read could not pass on the argument assertion below.
    private static final float MAP_ZOOM = 2f;

    // The pixel a stubbed reading says it was taken at. Nothing here asserts on it - the publisher
    // acts on the world point alone - but a reading carries it for the diagnostic line, so a stub
    // that left it out would be a shape the live seam never produces.
    private static final int CURSOR_PIXEL_X = 410;
    private static final int CURSOR_PIXEL_Y = 320;

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

    // What the host's look answers an arrival with, mutable so the case about a player who has
    // silenced the tick can state that the way a look does - by naming no cue at all.
    private UiSoundCue cellArrivalCue;

    // Whether the pass under test is one the cursor can be located against, mutable so a case
    // can put the publisher on a foreign map's pass the way a frame does.
    private boolean isCursorLocatable;
    private MockedStatic<MapCursor> cursorMock;
    private MapHoverState hoverState;
    private ModelviewMatrixReader readerMock;
    private UiSoundPlayerFake soundPlayerFake;

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

    @BeforeEach
    void setUp() {

        // The arrival trace words the reading it announced, and wording one reads the clip live off
        // GL - which a test JVM has no context for. What that line says is a diagnostic rather than
        // anything this publisher promises, so it is held below DEBUG here and pinned nowhere.
        Global.getLogger(MapHoverPublisher.class).setLevel(Level.INFO);

        readerMock = mock(ModelviewMatrixReader.class);
        soundPlayerFake = new UiSoundPlayerFake();
        cellArrivalCue = CELL_ARRIVAL_CUE;
        isCursorLocatable = true;
        cursorMock = mockStatic(MapCursor.class);

        stubCursorAt(POINT_ON_CELL);

        // A standing hover from an earlier frame, so a parking assertion distinguishes "parked"
        // from "left alone": both publish nothing new, only the first clears.
        hoverState = MapHoverState.getInstance();
        hoverState.publishHover(new MapHover("stale", List.of("stale")));
    }

    @AfterEach
    void tearDown() {
        // Both are shared for the run, so what this test planted must not reach another: the level
        // goes back to inheriting whatever the run was configured with.
        Global.getLogger(MapHoverPublisher.class).setLevel(null);
        hoverState.clearHover();
        cursorMock.close();
    }

    @Nested
    class PublishHoverFrom {

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
                MapCursor.readCursorDuringMapPass(MAP_ZOOM, readerMock));
        }

        @Test
        void publishHoverFromLetsTheLastPassOfAFrameOverwriteTheOnesBeforeIt() {
            // The offset's whole fix: several surfaces paint one frame, each binding a transform of
            // its own, and one of them can belong to a map another mod composited - drawn ahead of
            // the map screen, so it is the frame's first pass. Last write wins is what leaves the
            // answer with the surface that drew last, which is the map the pointer is over.
            var publisher = buildPublisher();
            publisher.publishHoverFrom(buildTargetsWithTwoCells(), MAP_ZOOM);

            stubCursorAt(POINT_ON_NEIGHBOUR_CELL);
            publisher.publishHoverFrom(buildTargetsWithTwoCells(), MAP_ZOOM);

            assertThat(hoverState.getHover().hoveredSystemId())
                .isEqualTo(NEIGHBOUR_SYSTEM_ID);
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
        void publishHoverFromParksTheHoverOnAPassTheCursorCannotBeLocatedAgainst() {
            // A foreign map's pass. The cell shapes are there and the unproject would answer - the
            // point simply is not where the pointer is - so nothing further down would catch this.
            isCursorLocatable = false;

            buildPublisher()
                .publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            assertThat(hoverState.getHover())
                .isSameAs(MapHover.NONE);
        }

        @Test
        void publishHoverFromReadsNoCursorOnAPassItCannotBeLocatedAgainst() {
            // Skipped rather than resolved and discarded: the read is a matrix readback and an
            // unproject, and under Fast Rendering a hop to the render thread besides. A foreign map
            // redraws every frame, so paying for that would be a per-frame cost for nothing.
            isCursorLocatable = false;

            buildPublisher()
                .publishHoverFrom(buildTargetsWithOneCell(), MAP_ZOOM);

            cursorMock.verifyNoInteractions();
        }
    }

    @Nested
    class AnnounceSettledArrival {

        @Test
        void announceSettledArrivalTicksAsTheCursorReachesACell() {
            // The whole moment: the map answers the cursor getting somewhere, and it answers with
            // what the host's look named rather than with a sample or a level of the pass's own.
            driveOneFrame(buildPublisher(), buildTargetsWithOneCell());

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE);
        }

        @Test
        void announceSettledArrivalTicksOnceForAFrameWhosePassesDisagree() {
            // The reason the moment is per frame while the read is per pass. Two surfaces paint one
            // frame through different transforms, so they resolve different cells; a latch stepped
            // by each of them would report the cursor crossing back and forth on every frame it
            // rests still - a tick per frame under a motionless pointer.
            var publisher = buildPublisher();

            publishBothPassesOfOneFrame(publisher);
            publisher.announceSettledArrival();

            publishBothPassesOfOneFrame(publisher);
            publisher.announceSettledArrival();

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE);
        }

        @Test
        void announceSettledArrivalTicksForTheCellTheFramesLastPassSettledOn() {
            // And it is the surface that drew last the moment answers, not the one that read first:
            // the tick has to name the cell the highlight and the hover box name.
            var publisher = buildPublisher();

            publishBothPassesOfOneFrame(publisher);
            publisher.announceSettledArrival();

            // Resting on the neighbour is silent, which it can only be if that is the cell the
            // latch took from the frame.
            stubCursorAt(POINT_ON_NEIGHBOUR_CELL);
            driveOneFrame(publisher, buildTargetsWithTwoCells());

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE);
        }

        @Test
        void announceSettledArrivalStaysSilentBeforeAnyPassHasRead() {
            // The frame's preparation runs before its passes do, so the first one ever asks this of
            // a publisher that has resolved nothing. Nothing is owed and nothing is dereferenced.
            var publisher = buildPublisher();

            assertThatCode(publisher::announceSettledArrival)
                .doesNotThrowAnyException();
            assertThat(soundPlayerFake.getPlayedCues())
                .isEmpty();
        }

        @Test
        void announceSettledArrivalStaysSilentOnAPassTheCursorCannotBeLocatedAgainst() {
            // The half the player actually notices: a tick sounded for reaching a cell nobody
            // pointed at, on every frame a foreign map redraws.
            isCursorLocatable = false;

            driveOneFrame(buildPublisher(), buildTargetsWithOneCell());

            assertThat(soundPlayerFake.getPlayedCues())
                .isEmpty();
        }

        @Test
        void announceSettledArrivalTicksForTheCellHeldWhileAForeignPassInterrupted() {
            // A foreign map redrawing between two of the real map's frames must not read as the
            // cursor having left the cell it is resting on, or the tick would sound on every one of
            // those frames - the fault that made this audible in the first place.
            var publisher = buildPublisher();
            driveOneFrame(publisher, buildTargetsWithOneCell());

            isCursorLocatable = false;
            driveOneFrame(publisher, buildTargetsWithOneCell());

            isCursorLocatable = true;
            driveOneFrame(publisher, buildTargetsWithOneCell());

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE);
        }

        @Test
        void announceSettledArrivalTicksOnceWhileTheCursorRestsOnACell() {
            // The pass runs every frame and the cursor is usually still, so a tick per frame is what
            // an unlatched read would give - a cell held under the pointer buzzing until it moves.
            var publisher = buildPublisher();

            driveOneFrame(publisher, buildTargetsWithOneCell());
            driveOneFrame(publisher, buildTargetsWithOneCell());

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE);
        }

        @Test
        void announceSettledArrivalTicksAgainForACellReachedFromItsNeighbour() {
            // Crossing straight from one cell to the next never leaves the map, so an arrival
            // detected only from off a cell would tick once for a whole sweep across the map. Two
            // cells of one cluster, deliberately: the tick answers the cell the hover box names and
            // not the cluster around it, so a move within one cluster is still a move.
            var publisher = buildPublisher();
            driveOneFrame(publisher, buildTargetsWithTwoCells());

            stubCursorAt(POINT_ON_NEIGHBOUR_CELL);
            driveOneFrame(publisher, buildTargetsWithTwoCells());

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE, CELL_ARRIVAL_CUE);
        }

        @Test
        void announceSettledArrivalTicksAgainWhenTheCursorReturnsToTheCellItLeft() {
            // Parking has to forget where the cursor was, or a cell left for empty space and come
            // back to is silent - the one return trip a player makes constantly, the map being mostly
            // the space between cells.
            var publisher = buildPublisher();
            driveOneFrame(publisher, buildTargetsWithOneCell());

            stubCursorAt(POINT_OFF_CELL);
            driveOneFrame(publisher, buildTargetsWithOneCell());

            stubCursorAt(POINT_ON_CELL);
            driveOneFrame(publisher, buildTargetsWithOneCell());

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE, CELL_ARRIVAL_CUE);
        }

        @Test
        void announceSettledArrivalTicksOnceAcrossAFrameThatCannotResolveTheCursor() {
            // A read that failed says nothing about where the cursor is, so the cell it was resting
            // on is still the cell it is resting on. Treated as a departure instead, the frame after
            // reads as a fresh arrival - and a frame can be painted by more than one pass, so a
            // single failing pass beside a resolving one would tick on every frame the cursor is
            // still.
            var publisher = buildPublisher();
            driveOneFrame(publisher, buildTargetsWithOneCell());

            stubCursorAt(null);
            driveOneFrame(publisher, buildTargetsWithOneCell());

            stubCursorAt(POINT_ON_CELL);
            driveOneFrame(publisher, buildTargetsWithOneCell());

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE);
        }

        @Test
        void announceSettledArrivalTicksForANewCellReachedAcrossAnUnresolvableFrame() {
            // The other side of keeping the last cell: holding it must not go deaf to the cursor
            // having genuinely moved while the read was down. A latch kept for the wrong reason
            // would swallow the arrival on the cell the cursor actually crossed to.
            var publisher = buildPublisher();
            driveOneFrame(publisher, buildTargetsWithTwoCells());

            stubCursorAt(null);
            driveOneFrame(publisher, buildTargetsWithTwoCells());

            stubCursorAt(POINT_ON_NEIGHBOUR_CELL);
            driveOneFrame(publisher, buildTargetsWithTwoCells());

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE, CELL_ARRIVAL_CUE);
        }

        @Test
        void announceSettledArrivalTicksOnceAcrossAFrameThatPaintsNothing() {
            // Absent draw lists are a missing input on the same terms as an unreadable cursor: there
            // are no cell shapes to hit-test, so the frame answers nothing about where the cursor is
            // and the cell it was last seen on stands.
            var publisher = buildPublisher();
            driveOneFrame(publisher, buildTargetsWithOneCell());

            driveOneFrame(publisher, null);
            driveOneFrame(publisher, buildTargetsWithOneCell());

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE);
        }

        @Test
        void announceSettledArrivalReachesACellSilentlyWhenTheLookNamesNoCue() {
            // A player who has pulled the tick's slider to the bottom, which the look states by
            // naming no cue at all. Nothing reaches the player rather than a sound played at nothing.
            cellArrivalCue = null;

            driveOneFrame(buildPublisher(), buildTargetsWithOneCell());

            assertThat(soundPlayerFake.getPlayedCues())
                .isEmpty();
        }

        @Test
        void announceSettledArrivalTicksAtWhateverTheLookNamesWhenTheMomentComes() {
            // The cue is composed per arrival rather than held from construction, so a level changed
            // on the settings screen reaches a publisher built when the map first drew - which is the
            // only publisher there is, one being kept for the session.
            var publisher = buildPublisher();
            driveOneFrame(publisher, buildTargetsWithOneCell());

            cellArrivalCue = RETUNED_CELL_ARRIVAL_CUE;

            stubCursorAt(POINT_OFF_CELL);
            driveOneFrame(publisher, buildTargetsWithOneCell());

            stubCursorAt(POINT_ON_CELL);
            driveOneFrame(publisher, buildTargetsWithOneCell());

            assertThat(soundPlayerFake.getPlayedCues())
                .containsExactly(CELL_ARRIVAL_CUE, RETUNED_CELL_ARRIVAL_CUE);
        }

        // Two surfaces painting one frame while the pointer is still, resolving different cells
        // because they bound different transforms - the shape a foreign map's pass gives every
        // frame it draws in.
        private void publishBothPassesOfOneFrame(MapHoverPublisher publisher) {

            stubCursorAt(POINT_ON_CELL);
            publisher.publishHoverFrom(buildTargetsWithTwoCells(), MAP_ZOOM);

            stubCursorAt(POINT_ON_NEIGHBOUR_CELL);
            publisher.publishHoverFrom(buildTargetsWithTwoCells(), MAP_ZOOM);
        }
    }

    // The publisher under test, reading the case's own cue field so a case can retune or silence
    // the look between frames the way the settings screen does between visits.
    private MapHoverPublisher buildPublisher() {
        return new MapHoverPublisher(
            readerMock,
            () -> soundPlayerFake.playCueIfPresent(cellArrivalCue),
            () -> isCursorLocatable);
    }

    // One frame as the map surface drives it: the read each of its passes makes, then the moment
    // the frame settled on, which the frame's single preparation asks for however many passes
    // painted it.
    private void driveOneFrame(MapHoverPublisher publisher, MapHoverTargets targets) {

        publisher.publishHoverFrom(targets, MAP_ZOOM);
        publisher.announceSettledArrival();
    }

    // A null world point stands for no reading at all, which is what the seam reports for every
    // way of not knowing where the cursor is. The reading's other parts are carried for the
    // description and never read by the publisher, so they are left at their empty shapes.
    private void stubCursorAt(Vector2f worldPoint) {

        var cursorRead = worldPoint == null
            ? null
            : new MapCursorRead(
                CURSOR_PIXEL_X,
                CURSOR_PIXEL_Y,
                new CampaignMapTransform(new float[16], new float[16], new int[4], MAP_ZOOM),
                worldPoint);

        cursorMock
            .when(() -> MapCursor.readCursorDuringMapPass(
                eq(MAP_ZOOM),
                any(ModelviewMatrixReader.class)))
            .thenReturn(cursorRead);
    }
}
