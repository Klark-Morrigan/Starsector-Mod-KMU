package kmu.maplayers.base.labels.anchor;

import kmlib.math.geometry.Segment;

import kmu.maplayers.base.render.DiagnosticPalette;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a pass's placements are packed into the overlay's runs: which verdict a line lands in,
 * what a band contributes, and how the dots group.
 *
 * <p>The packing is what the renderer trusts every frame without looking at a placement again, so a
 * line packed into the wrong verdict, or a band packed without its outline, is drawn wrong until the
 * next re-fit.
 */
final class ClusterAnchorRunsTests {

    // A horizontal line ten units long, whose band at the girth below has corners at whole numbers.
    private static final Segment HORIZONTAL_AXIS = new Segment(0, 0, 10, 0);

    private static final float BAND_THICKNESS = 4f;

    // Under the overlay's floor, so the band is left to its centreline.
    private static final float SUB_UNIT_THICKNESS = 0.5f;

    private static final float BAND_OPACITY = 0.25f;
    private static final float LINE_OPACITY = 0.75f;

    // The verdicts in the order the runs hold them.
    private static final int REJECTED = 0;
    private static final int UNBIASED = 1;
    private static final int ACCEPTED = 2;

    @Nested
    class PackAnchorRuns {

        // Answered before the opacity knobs are read, which is what lets a pass with nothing placed
        // pack without a game to read settings from.
        @Test
        void answersNoneForAPassThatPlacedNothing() {

            // ACT
            var actualRuns = ClusterAnchorRuns.packAnchorRuns(List.of());

            // ASSERT
            assertThat(actualRuns)
                .isSameAs(ClusterAnchorRuns.NONE);
            assertThat(actualRuns.isEmpty())
                .isTrue();
        }
    }

    @Nested
    class PackAnchorRunsAt {

        @Test
        void packsTheVerdictsInDrawOrderInTheirPaletteShades() {

            // ARRANGE
            var anchors = List.of(buildAnchor(Color.WHITE, HORIZONTAL_AXIS, BAND_THICKNESS, 1));

            // ACT
            var actualRuns = ClusterAnchorRuns.packAnchorRunsAt(anchors, BAND_OPACITY, LINE_OPACITY);

            // ASSERT
            assertThat(actualRuns.verdictRuns())
                .extracting(ClusterAnchorRuns.VerdictRuns::verdictColour)
                .containsExactly(
                    DiagnosticPalette.DISCARDED_COLOUR,
                    DiagnosticPalette.INTERMEDIATE_COLOUR,
                    DiagnosticPalette.ACCEPTED_COLOUR);
        }

        @Test
        void packsABandAsItsFillItsOutlineAndItsLaneRules() {

            // ARRANGE
            var anchors = List.of(buildAnchor(Color.WHITE, HORIZONTAL_AXIS, BAND_THICKNESS, 2));
            var expectedFills = new float[] {0, 2, 10, 2, 10, -2, 0, -2};

            // The four outline edges, closing edge included, then the one rule between two lanes.
            var expectedStrokes = new float[] {
                0, 2, 10, 2,
                10, 2, 10, -2,
                10, -2, 0, -2,
                0, -2, 0, 2,
                0, 0, 10, 0};

            var expectedCentrelines = new float[] {0, 0, 10, 0};

            // ACT
            var actualAccepted = ClusterAnchorRuns
                .packAnchorRunsAt(anchors, BAND_OPACITY, LINE_OPACITY)
                .verdictRuns()
                .get(ACCEPTED);

            // ASSERT
            assertThat(actualAccepted.bandFills())
                .containsExactly(expectedFills);
            assertThat(actualAccepted.bandStrokes())
                .containsExactly(expectedStrokes);
            assertThat(actualAccepted.centrelines())
                .containsExactly(expectedCentrelines);
        }

        // A collapsed fit or the bisection's residue has no area to fill, so only the line shows.
        @Test
        void packsABandThinnerThanTheFloorAsItsCentrelineAlone() {

            // ARRANGE
            var anchors = List.of(buildAnchor(Color.WHITE, HORIZONTAL_AXIS, SUB_UNIT_THICKNESS, 1));
            var expectedCentrelines = new float[] {0, 0, 10, 0};

            // ACT
            var actualAccepted = ClusterAnchorRuns
                .packAnchorRunsAt(anchors, BAND_OPACITY, LINE_OPACITY)
                .verdictRuns()
                .get(ACCEPTED);

            // ASSERT
            assertThat(actualAccepted.bandFills())
                .isEmpty();
            assertThat(actualAccepted.bandStrokes())
                .isEmpty();
            assertThat(actualAccepted.centrelines())
                .containsExactly(expectedCentrelines);
        }

        // An anchor carries a rejected or unbiased line only while its toggle is on.
        @Test
        void packsNothingIntoAVerdictNoAnchorCarries() {

            // ARRANGE
            var anchors = List.of(buildAnchor(Color.WHITE, HORIZONTAL_AXIS, BAND_THICKNESS, 1));

            // ACT
            var actualRejected = ClusterAnchorRuns
                .packAnchorRunsAt(anchors, BAND_OPACITY, LINE_OPACITY)
                .verdictRuns()
                .get(REJECTED);

            // ASSERT
            assertThat(actualRejected.bandFills())
                .isEmpty();
            assertThat(actualRejected.bandStrokes())
                .isEmpty();
            assertThat(actualRejected.centrelines())
                .isEmpty();
        }

        @Test
        void packsTheUnbiasedLineIntoItsOwnVerdict() {

            // ARRANGE
            var anchors = List.of(new ClusterAnchor(
                new ClusterIdentity("hegemony", Set.of(buildCellKey("hegemony"))),
                0f,
                0f,
                Color.WHITE,
                List.of(),
                0f,
                null,
                null,
                HORIZONTAL_AXIS,
                SUB_UNIT_THICKNESS,
                1));

            var expectedCentrelines = new float[] {0, 0, 10, 0};

            // ACT
            var actualUnbiased = ClusterAnchorRuns
                .packAnchorRunsAt(anchors, BAND_OPACITY, LINE_OPACITY)
                .verdictRuns()
                .get(UNBIASED);

            // ASSERT
            assertThat(actualUnbiased.centrelines())
                .containsExactly(expectedCentrelines);
        }

        // The clusters of one owner share a shade, so the dots draw as one run per shade.
        @Test
        void groupsTheDotsByShadeInTheOrderTheShadesAppear() {

            // ARRANGE
            var anchors = List.of(
                buildAnchorAt("hegemony", Color.RED, 1f, 2f),
                buildAnchorAt("tritachyon", Color.BLUE, 3f, 4f),
                buildAnchorAt("hegemony_second", Color.RED, 5f, 6f));

            var expectedRedDots = new float[] {1f, 2f, 5f, 6f};
            var expectedBlueDots = new float[] {3f, 4f};

            // ACT
            var actualDotRuns = ClusterAnchorRuns
                .packAnchorRunsAt(anchors, BAND_OPACITY, LINE_OPACITY)
                .dotRuns();

            // ASSERT
            assertThat(actualDotRuns)
                .extracting(ClusterAnchorRuns.DotRun::dotColour)
                .containsExactly(Color.RED, Color.BLUE);

            assertThat(actualDotRuns.get(0).dots())
                .containsExactly(expectedRedDots);
            assertThat(actualDotRuns.get(1).dots())
                .containsExactly(expectedBlueDots);
        }

        @Test
        void carriesTheOpacitiesItWasPackedAt() {

            // ARRANGE
            var anchors = List.of(buildAnchor(Color.WHITE, HORIZONTAL_AXIS, BAND_THICKNESS, 1));

            // ACT
            var actualRuns = ClusterAnchorRuns.packAnchorRunsAt(anchors, BAND_OPACITY, LINE_OPACITY);

            // ASSERT
            assertThat(actualRuns.bandOpacity())
                .isEqualTo(0.25f);
            assertThat(actualRuns.lineOpacity())
                .isEqualTo(0.75f);
        }
    }

    // One placement whose accepted line is the given axis at the given girth.
    private static ClusterAnchor buildAnchor(
            Color colour,
            Segment acceptedAxis,
            float thickness,
            int lineCount) {

        return new ClusterAnchor(
            new ClusterIdentity("hegemony", Set.of(buildCellKey("hegemony"))),
            0f,
            0f,
            colour,
            List.of(),
            0f,
            acceptedAxis,
            null,
            null,
            thickness,
            lineCount);
    }

    // One collapsed placement at the given point, carrying nothing but its dot.
    private static ClusterAnchor buildAnchorAt(
            String ownerKey,
            Color colour,
            float anchorX,
            float anchorY) {

        return new ClusterAnchor(
            new ClusterIdentity(ownerKey, Set.of(buildCellKey(ownerKey))),
            anchorX,
            anchorY,
            colour,
            List.of(),
            0f,
            null,
            null,
            null,
            0f,
            0);
    }
}
