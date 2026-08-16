package kmu.maplayers.base.labels.anchor;

import kmlib.math.geometry.Segment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a placement turns into once something has to stay out of its way: the oriented box
 * its words fill, every name on the map contributing its own, and a placement that draws nothing
 * contributing none.
 *
 * <p>The "every name" half is the one worth stating outright. A name sits wherever its own
 * cluster is roomiest, which can be over a neighbour's cells entirely, so a reader that took only
 * the boxes belonging to the cluster it was working on would keep clear of the wrong ones.
 */
final class ClusterNameBoxesTest {

    // A name lying along the x axis, four tall: the box is a literal, so a corner falling on the
    // wrong side of the line would show up as a coordinate rather than as an area.
    private static final Segment EASTWARD_NAME = new Segment(0, 10, 20, 10);

    private static final float NAME_THICKNESS = 4f;

    @Nested
    class ListNameBoxes {

        @Test
        void listNameBoxesTurnsAPlacementIntoTheBoxItsWordsFill() {
            // The line plus the girth, read as one shape: the room the block of words occupies,
            // rather than the line the fit searched with.
            assertThat(ClusterNameBoxes.listNameBoxes(List.of(
                    buildAnchor("hegemony", EASTWARD_NAME, NAME_THICKNESS))))
                .singleElement()
                .satisfies(box -> assertThat(box)
                    .containsExactly(
                        new double[] {0, 12},
                        new double[] {20, 12},
                        new double[] {20, 8},
                        new double[] {0, 8}));
        }

        @Test
        void listNameBoxesReportsEveryNameOnTheMap() {
            // Whoever a name belongs to says nothing about whose cells it lies over, so the boxes
            // are the map's rather than any one cluster's.
            assertThat(ClusterNameBoxes.listNameBoxes(List.of(
                    buildAnchor("hegemony", EASTWARD_NAME, NAME_THICKNESS),
                    buildAnchor("tritachyon", new Segment(0, 100, 20, 100), NAME_THICKNESS))))
                .hasSize(2);
        }

        @Test
        void listNameBoxesLeavesOutAPlacementThatAcceptedNoLine() {
            // The collapsed fit: no line was accepted anywhere in the cluster, so the placement is
            // a dot on the debug overlay and nothing at all on the map.
            assertThat(ClusterNameBoxes.listNameBoxes(List.of(
                    buildAnchor("hegemony", null, NAME_THICKNESS))))
                .isEmpty();
        }

        @Test
        void listNameBoxesLeavesOutAPlacementWithNoGirthToItsBlock() {
            // A line but no band: there is no area for words to fill, so nothing is drawn and
            // nothing has to be kept clear of it.
            assertThat(ClusterNameBoxes.listNameBoxes(List.of(
                    buildAnchor("hegemony", EASTWARD_NAME, 0f))))
                .isEmpty();
        }
    }

    @Nested
    class ComputeNameBox {

        @Test
        void computeNameBoxTurnsOnePlacementIntoTheBoxItsWordsFill() {
            // The single-placement reading, so a reader asking whether one name's room moved gets
            // the same answer the whole-map list would have given for it.
            assertThat(ClusterNameBoxes.computeNameBox(
                    buildAnchor("hegemony", EASTWARD_NAME, NAME_THICKNESS)))
                .containsExactly(
                    new double[] {0, 12},
                    new double[] {20, 12},
                    new double[] {20, 8},
                    new double[] {0, 8});
        }

        @Test
        void computeNameBoxReportsNoRoomForAPlacementThatAcceptedNoLine() {
            // Empty rather than absent: a collapsed fit occupies nothing, and a caller asking
            // about one placement has no list for it to be left out of.
            assertThat(ClusterNameBoxes.computeNameBox(
                    buildAnchor("hegemony", null, NAME_THICKNESS)))
                .isEmpty();
        }
    }

    // One placement carrying just what a box is read off - the accepted line and the girth of the
    // block filling it. Every other fitted component is inert here.
    private static ClusterAnchor buildAnchor(
            String ownerKey,
            Segment acceptedAxis,
            float thickness) {

        return new ClusterAnchor(
            new ClusterIdentity(ownerKey, Set.of(ownerKey)),
            0f,
            0f,
            Color.WHITE,
            List.of(),
            0f,
            acceptedAxis,
            null,
            null,
            thickness,
            1);
    }
}
