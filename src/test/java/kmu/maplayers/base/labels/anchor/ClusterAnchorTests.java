package kmu.maplayers.base.labels.anchor;

import kmlib.math.geometry.Segment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two readings every other question about a placement is asked through: the box its fit
 * reserved, and the filing that finds it again in a later pass.
 *
 * <p>The box is the one statement of a name's fitted extent, read both for the whole map and for
 * one placement at a time, so a corner falling on the wrong side of the line here is wrong
 * everywhere at once.
 *
 * <p>A fit reports its placements in its own partition's order, so two passes' lists cannot be
 * walked side by side and the cluster each one names is the only thing that survives between them.
 * More than one reader asks that question - whether a placement may be carried over, and whether it
 * moved - so the filing is stated once and both read the same answer.
 */
final class ClusterAnchorTests {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    // A name lying along the x axis, four tall: the box is a literal, so a corner falling on the
    // wrong side of the line shows up as a coordinate rather than as an area.
    private static final Segment EASTWARD_NAME = new Segment(0, 10, 20, 10);

    private static final float NAME_THICKNESS = 4f;

    @Nested
    class ComputeFittedBox {

        // The line plus the girth, read as one shape: the room the block of words occupies, rather
        // than the line the fit searched with.
        @Test
        void turnsThePlacementIntoTheBoxItsWordsFill() {

            // ARRANGE
            var anchor = buildAnchorAlong(EASTWARD_NAME, NAME_THICKNESS);
            var expectedBox = List.of(
                new double[] {0, 12},
                new double[] {20, 12},
                new double[] {20, 8},
                new double[] {0, 8});

            // ACT
            var actualBox = anchor.computeFittedBox();

            // ASSERT
            assertThat(actualBox)
                .containsExactlyElementsOf(expectedBox);
        }

        // Empty rather than absent: a collapsed fit occupies nothing, and a reader asking about one
        // placement has no list for it to be left out of.
        @Test
        void reportsNoRoomForAPlacementThatAcceptedNoLine() {

            // ARRANGE
            var anchor = buildAnchorAlong(null, NAME_THICKNESS);

            // ACT
            var actualBox = anchor.computeFittedBox();

            // ASSERT
            assertThat(actualBox)
                .isEmpty();
        }

        // A line but no band: there is no area for words to fill, so the placement holds no room.
        @Test
        void reportsNoRoomForAPlacementWithNoGirthToItsBlock() {

            // ARRANGE
            var anchor = buildAnchorAlong(EASTWARD_NAME, 0f);

            // ACT
            var actualBox = anchor.computeFittedBox();

            // ASSERT
            assertThat(actualBox)
                .isEmpty();
        }
    }

    @Nested
    class MapAnchorsByIdentity {

        @Test
        void filesEachPlacementUnderTheClusterItNames() {

            var hegemonyAnchor = buildAnchorOf(HEGEMONY);
            var tritachyonAnchor = buildAnchorOf(TRITACHYON);

            assertThat(ClusterAnchor.mapAnchorsByIdentity(
                    List.of(hegemonyAnchor, tritachyonAnchor)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                    hegemonyAnchor.identity(),
                    hegemonyAnchor,
                    tritachyonAnchor.identity(),
                    tritachyonAnchor));
        }

        @Test
        void filesNothingForAPassThatFittedNoPlacement() {
            // The session's first rebuild, and every one taken while the names and the overlay are
            // both off: nothing stands to be looked up, which is a filing rather than a refusal.
            assertThat(ClusterAnchor.mapAnchorsByIdentity(List.of()))
                .isEmpty();
        }
    }

    // One placement carrying just what a box is read off - the accepted line and the girth of the
    // block filling it. Every other fitted component is inert here.
    private static ClusterAnchor buildAnchorAlong(Segment acceptedAxis, float thickness) {

        return new ClusterAnchor(
            new ClusterIdentity(HEGEMONY, Set.of(buildCellKey(HEGEMONY))),
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

    // One placement of one cluster. Only its identity is read here, so every fitted component is
    // inert.
    private static ClusterAnchor buildAnchorOf(String ownerKey) {

        return new ClusterAnchor(
            new ClusterIdentity(ownerKey, Set.of(buildCellKey(ownerKey))),
            0f,
            0f,
            Color.WHITE,
            List.of(),
            0f,
            null,
            null,
            null,
            0f,
            0);
    }
}
