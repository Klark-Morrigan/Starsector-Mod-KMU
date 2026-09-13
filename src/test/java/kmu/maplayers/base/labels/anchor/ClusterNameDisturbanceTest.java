package kmu.maplayers.base.labels.anchor;

import kmlib.math.geometry.Segment;
import kmlib.starsector.systems.SystemKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a re-fit is taken to have disturbed, and which cells that reaches.
 *
 * <p>Two claims carry the whole thing. A name that did not move disturbs nothing, which is what
 * makes a re-fit worth comparing at all - most of them move a handful of names and leave the rest
 * of the map alone. And a name that did move disturbs both ends of its journey, since the ring it
 * gave up is as much a change to what can be laid there as the ring it took.
 *
 * <p>The cell test is deliberately loose: a cell is disturbed when its bounding box meets a moved
 * name, not when the cell's own outline does. Over-inclusion costs a re-lay that changes nothing,
 * while under-inclusion leaves a cell carrying work laid around a name that is no longer there.
 */
final class ClusterNameDisturbanceTest {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    // Two names far enough apart that no case can have one standing in for the other.
    private static final Segment NAME_AT_THE_ORIGIN = new Segment(0, 0, 20, 0);
    private static final Segment NAME_ACROSS_THE_SECTOR = new Segment(1000, 1000, 1020, 1000);

    private static final float NAME_THICKNESS = 4f;

    // A cell under each of those two names, keyed so a case reads back which one was disturbed.
    private static final SystemKey CELL_AT_THE_ORIGIN = buildCellKey("origin");
    private static final SystemKey CELL_ACROSS_THE_SECTOR = buildCellKey("far");

    private static final Map<SystemKey, List<double[]>> CELLS_UNDER_BOTH_NAMES = Map.of(
        CELL_AT_THE_ORIGIN,
        buildSquare(-10, -10, 40),
        CELL_ACROSS_THE_SECTOR,
        buildSquare(990, 990, 40));

    @Nested
    class CompareFittedNames {

        @Test
        void compareFittedNamesReportsNothingWhenEveryNameStoodStill() {
            // The carried-over fit: every cluster still names the same members and kept the
            // placement it had, so nothing laid around those names has to be laid again.
            var standing = List.of(buildNameOf(HEGEMONY, NAME_AT_THE_ORIGIN));

            assertThat(ClusterNameDisturbance
                    .compareFittedNames(standing, List.of(buildNameOf(HEGEMONY, NAME_AT_THE_ORIGIN)))
                    .isDisturbingNothing())
                .isTrue();
        }

        @Test
        void compareFittedNamesReportsBothEndsOfANameThatMoved() {
            // The room given up and the room taken, since a cell under either one is now laid out
            // against a name that is not where it was.
            var disturbance = ClusterNameDisturbance.compareFittedNames(
                List.of(buildNameOf(HEGEMONY, NAME_AT_THE_ORIGIN)),
                List.of(buildNameOf(HEGEMONY, NAME_ACROSS_THE_SECTOR)));

            assertThat(disturbance.selectDisturbedCellKeys(CELLS_UNDER_BOTH_NAMES))
                .containsExactlyInAnyOrder(CELL_AT_THE_ORIGIN, CELL_ACROSS_THE_SECTOR);
        }

        @Test
        void compareFittedNamesReportsTheRoomANewNameTook() {
            // A cluster the previous fit did not name at all: a faction's first colony, or a
            // partition the flip bridged into one.
            var disturbance = ClusterNameDisturbance.compareFittedNames(
                List.of(),
                List.of(buildNameOf(HEGEMONY, NAME_ACROSS_THE_SECTOR)));

            assertThat(disturbance.selectDisturbedCellKeys(CELLS_UNDER_BOTH_NAMES))
                .containsExactly(CELL_ACROSS_THE_SECTOR);
        }

        @Test
        void compareFittedNamesReportsTheRoomAVanishedNameGaveUp() {
            // The last colony of a cluster goes: nothing is drawn there any more, so whatever was
            // keeping clear of the word may have the ring back.
            var disturbance = ClusterNameDisturbance.compareFittedNames(
                List.of(buildNameOf(HEGEMONY, NAME_AT_THE_ORIGIN)),
                List.of());

            assertThat(disturbance.selectDisturbedCellKeys(CELLS_UNDER_BOTH_NAMES))
                .containsExactly(CELL_AT_THE_ORIGIN);
        }

        @Test
        void compareFittedNamesReportsARepartitionedClusterAtBothPlacements() {
            // Same owner, different members: the cluster the placement was made for no longer
            // exists, so the fit's own placement is a new one and the old one vanished. Both boxes
            // count, which is what a change severing one cluster into two produces.
            var disturbance = ClusterNameDisturbance.compareFittedNames(
                List.of(buildNameOf(HEGEMONY, NAME_AT_THE_ORIGIN)),
                List.of(new ClusterAnchor(
                    new ClusterIdentity(HEGEMONY, Set.of(buildCellKey(HEGEMONY), buildCellKey(TRITACHYON))),
                    0f,
                    0f,
                    Color.WHITE,
                    List.of(),
                    0f,
                    NAME_ACROSS_THE_SECTOR,
                    null,
                    null,
                    NAME_THICKNESS,
                    1)));

            assertThat(disturbance.selectDisturbedCellKeys(CELLS_UNDER_BOTH_NAMES))
                .containsExactlyInAnyOrder(CELL_AT_THE_ORIGIN, CELL_ACROSS_THE_SECTOR);
        }

        @Test
        void compareFittedNamesReportsARestyledNameAsMoved() {
            // Only the shade changed, and the room is identical - but a placement is compared
            // whole, because what a name occupies has more than one reading and they are read off
            // different components of it. The cost is a re-lay that changes nothing, which is the
            // safe direction for this comparison to be wrong in.
            var restyled = buildNameOf(HEGEMONY, NAME_AT_THE_ORIGIN).copyWithColour(Color.RED);

            assertThat(ClusterNameDisturbance
                    .compareFittedNames(
                        List.of(buildNameOf(HEGEMONY, NAME_AT_THE_ORIGIN)),
                        List.of(restyled))
                    .isDisturbingNothing())
                .isFalse();
        }

        @Test
        void compareFittedNamesReportsNothingForAPlacementThatOccupiesNoRoom() {
            // The collapsed fit: no line was accepted, so the placement is a dot on the debug
            // overlay and nothing at all on the map - it neither took room nor gave any up.
            assertThat(ClusterNameDisturbance
                    .compareFittedNames(List.of(), List.of(buildNameOf(HEGEMONY, null)))
                    .isDisturbingNothing())
                .isTrue();
        }
    }

    @Nested
    class SelectDisturbedCellKeys {

        @Test
        void selectDisturbedCellKeysLeavesOutACellNoMovedNameReaches() {
            var disturbance = ClusterNameDisturbance.compareFittedNames(
                List.of(),
                List.of(buildNameOf(HEGEMONY, NAME_AT_THE_ORIGIN)));

            assertThat(disturbance.selectDisturbedCellKeys(
                    Map.of(CELL_ACROSS_THE_SECTOR, buildSquare(990, 990, 40))))
                .isEmpty();
        }

        @Test
        void selectDisturbedCellKeysReportsACellItsOwnOutlineDoesNotReach() {
            // The name sits in the corner of the cell's bounding box that the cell itself does not
            // fill. Named outright because it is the deliberate error: cheap to make, and it costs
            // only work, where the opposite error costs a wrong map.
            var disturbance = ClusterNameDisturbance.compareFittedNames(
                List.of(),
                List.of(buildNameOf(HEGEMONY, new Segment(80, 90, 90, 90))));

            assertThat(disturbance.selectDisturbedCellKeys(Map.of(
                    CELL_AT_THE_ORIGIN,
                    List.of(
                        new double[] {0, 0},
                        new double[] {100, 0},
                        new double[] {0, 100}))))
                .containsExactly(CELL_AT_THE_ORIGIN);
        }

        @Test
        void selectDisturbedCellKeysLeavesOutACellWithNoOutline() {
            // A cell recorded without a shape encloses nothing, so no name can reach into it -
            // and it has nothing laid on it to be disturbed anyway.
            var disturbance = ClusterNameDisturbance.compareFittedNames(
                List.of(),
                List.of(buildNameOf(HEGEMONY, NAME_AT_THE_ORIGIN)));

            assertThat(disturbance.selectDisturbedCellKeys(Map.of(CELL_AT_THE_ORIGIN, List.of())))
                .isEmpty();
        }

        @Test
        void selectDisturbedCellKeysReportsNoCellWhenNothingWasDisturbed() {
            assertThat(ClusterNameDisturbance.NONE.selectDisturbedCellKeys(CELLS_UNDER_BOTH_NAMES))
                .isEmpty();
        }
    }

    // One placement carrying just what a moved name is read off - the cluster it names and the
    // line its block fills. Every other fitted component is inert here.
    private static ClusterAnchor buildNameOf(String ownerKey, Segment acceptedAxis) {

        return new ClusterAnchor(
            new ClusterIdentity(ownerKey, Set.of(buildCellKey(ownerKey))),
            0f,
            0f,
            Color.WHITE,
            List.of(),
            0f,
            acceptedAxis,
            null,
            null,
            NAME_THICKNESS,
            1);
    }

    // A square cell of the given size with its lower-left corner where it is placed.
    private static List<double[]> buildSquare(double x, double y, double side) {

        return List.of(
            new double[] {x, y},
            new double[] {x + side, y},
            new double[] {x + side, y + side},
            new double[] {x, y + side});
    }
}
