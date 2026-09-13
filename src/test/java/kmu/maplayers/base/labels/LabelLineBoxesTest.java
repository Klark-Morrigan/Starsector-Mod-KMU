package kmu.maplayers.base.labels;

import kmlib.math.geometry.Segment;
import kmlib.starsector.ui.font.LineWidthMeasurer;

import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.ClusterIdentity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the tight reading of the room a name takes: one box per drawn line, no longer than the words
 * measure at the size they render at, laid along the slant the line reads at and centred where the
 * renderer hangs it.
 *
 * <p>The length is the point of the whole reading. A placement's fitted box is as long as the chord
 * the search accepted, which is only bounded below by the text - so a box measured off the words is
 * the one thing that says how much room a name genuinely costs, and a box that quietly took its
 * length from the placement instead would pass every other check here.
 *
 * <p>Both cases carrying a box state all four corners rather than a width and a height, since a
 * box is only right if it is in the right place: a rectangle of the correct size laid along the
 * wrong axis, or centred on the block instead of on its own line, is what the two would miss.
 */
final class LabelLineBoxesTest {

    // A cluster line running east, well longer than the name it carries: what a fitted box would be
    // measured against, and what a box measured off the words must not inherit.
    private static final Segment EASTWARD_AXIS = new Segment(-500, 10, 500, 10);

    // The same midpoint on a 3-4-5 slope, so a box's slant is exercised with a direction whose sine
    // and cosine are exact tenths - a swapped or sign-flipped pair lands nowhere near the corners
    // below, where a horizontal line would hide both.
    private static final Segment SLOPED_AXIS = new Segment(-400, -290, 400, 310);

    private static final float FONT_HEIGHT = 4f;

    // Two characters wide per unit of font height, so a line's expected length is a multiplication
    // a reader can do in their head, and a box taking the wrong font height comes out wrong rather
    // than coincidentally right.
    private static final double WIDTH_PER_CHARACTER_HEIGHT = 2.0;

    // The slant is carried as degrees and turned back into a direction, so a corner on the sloped
    // axis lands within float rounding of its exact value rather than on it.
    private static final double CORNER_TOLERANCE = 1e-4;

    @Nested
    class ListLineBoxes {

        @Test
        void measureLineBoxesSizesABoxToTheWordsRatherThanToTheFittedAxis() {
            // "AB" at font height 4, two characters wide per height unit: 16 long, 4 thick, centred
            // on the axis midpoint - a tenth of the 1000-long axis the placement was fitted along.
            assertThat(measureBoxes(buildAnchor(EASTWARD_AXIS, List.of("AB"))))
                .singleElement()
                .satisfies(box -> assertBoxCorners(box, new double[][] {
                    {-8, 12},
                    {8, 12},
                    {8, 8},
                    {-8, 8}}));
        }

        @Test
        void measureLineBoxesLaysABoxAlongTheSlantItsLineReadsAt() {
            // The same name on a line rising four across and three up: the box turns with it, so its
            // long edges run 0.8/0.6 and its girth crosses them at 0.6/-0.8.
            assertThat(measureBoxes(buildAnchor(SLOPED_AXIS, List.of("AB"))))
                .singleElement()
                .satisfies(box -> assertBoxCorners(box, new double[][] {
                    {-7.6, 6.8},
                    {5.2, 16.4},
                    {7.6, 13.2},
                    {-5.2, 3.6}}));
        }

        @Test
        void measureLineBoxesGivesEachStackedLineItsOwnBoxAtItsOwnHangPoint() {
            // A two-line name is two blocks of words at two hang points, four apart across the
            // block's own girth of eight, so it is two boxes of different lengths - "CDEF" being
            // twice "AB" - rather than one long box claiming the room between and around them.
            assertThat(measureBoxes(buildAnchor(EASTWARD_AXIS, List.of("AB", "CDEF"))))
                .satisfiesExactly(
                    upperLine -> assertBoxCorners(upperLine, new double[][] {
                        {-8, 14},
                        {8, 14},
                        {8, 10},
                        {-8, 10}}),
                    lowerLine -> assertBoxCorners(lowerLine, new double[][] {
                        {-16, 10},
                        {16, 10},
                        {16, 6},
                        {-16, 6}}));
        }

        @Test
        void measureLineBoxesLeavesOutAPlacementThatAcceptedNoLine() {
            // The collapsed fit: no line was accepted anywhere in the cluster, so nothing is drawn
            // and there is nothing to keep clear of.
            assertThat(measureBoxes(buildAnchor(null, List.of("AB"))))
                .isEmpty();
        }

        @Test
        void measureLineBoxesLeavesOutAPlacementWithNoWords() {
            // A fitted axis whose name never resolved - the fit ran on the aspect stand-in - so the
            // placement reserves room the map draws no words in.
            assertThat(measureBoxes(buildAnchor(EASTWARD_AXIS, List.of())))
                .isEmpty();
        }
    }

    // The boxes of one placement, measured through a width that scales with the font height the way
    // a real face does, so a box built at the wrong height fails rather than passing by chance.
    private static List<List<double[]>> measureBoxes(ClusterAnchor anchor) {
        return LabelLineBoxes.measureLineBoxes(List.of(anchor), buildCharacterWideMeasurer());
    }

    private static LineWidthMeasurer buildCharacterWideMeasurer() {
        return (line, fontSize) -> line.length() * fontSize * WIDTH_PER_CHARACTER_HEIGHT;
    }

    // One box against the corners it should have, in ring order and to the slant's rounding. Named
    // per corner so a failure says which one moved rather than printing two rings to compare by eye.
    private static void assertBoxCorners(List<double[]> box, double[][] expectedCorners) {

        assertThat(box).hasSameSizeAs(expectedCorners);

        for (var corner = 0; corner < expectedCorners.length; corner++) {
            assertThat(box.get(corner)[0])
                .as("corner %d x", corner)
                .isCloseTo(expectedCorners[corner][0], within(CORNER_TOLERANCE));
            assertThat(box.get(corner)[1])
                .as("corner %d y", corner)
                .isCloseTo(expectedCorners[corner][1], within(CORNER_TOLERANCE));
        }
    }

    // One placement carrying just what a drawn line is read off - the accepted line the block is
    // centred and slanted on, the wrapped lines, and the height they render at. The anchor point is
    // that line's own midpoint, as the placement search leaves it. Every other fitted component is
    // inert here; the thickness is the girth the whole block takes, which is what the lines are
    // stacked across.
    private static ClusterAnchor buildAnchor(Segment acceptedAxis, List<String> nameLines) {

        return new ClusterAnchor(
            new ClusterIdentity("hegemony", Set.of(buildCellKey("hegemony"))),
            0f,
            10f,
            Color.WHITE,
            nameLines,
            FONT_HEIGHT,
            acceptedAxis,
            null,
            null,
            FONT_HEIGHT * nameLines.size(),
            Math.max(nameLines.size(), 1));
    }
}
