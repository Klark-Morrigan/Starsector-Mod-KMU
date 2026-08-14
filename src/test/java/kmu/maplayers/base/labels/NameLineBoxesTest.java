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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the tight reading of the room a name takes: one box per drawn line, no longer than the words
 * measure at the size they render at, and centred where the renderer hangs them.
 *
 * <p>The length is the point of the whole reading. A placement's fitted box is as long as the chord
 * the search accepted, which is only bounded below by the text - so a box measured off the words is
 * the one thing that says how much ring a name genuinely costs, and a box that quietly took its
 * length from the placement instead would pass every other check here.
 */
final class NameLineBoxesTest {

    // A cluster line running east, well longer than the name it carries: what a fitted box would be
    // measured against, and what a box measured off the words must not inherit.
    private static final Segment EASTWARD_AXIS = new Segment(-500, 10, 500, 10);

    private static final float FONT_HEIGHT = 4f;

    // Two characters wide per unit of font height, so a line's expected length is a multiplication
    // a reader can do in their head, and a box taking the wrong font height comes out wrong rather
    // than coincidentally right.
    private static final double WIDTH_PER_CHARACTER_HEIGHT = 2.0;

    @Nested
    class ListLineBoxes {

        @Test
        void measureLineBoxesSizesABoxToTheWordsRatherThanToTheFittedAxis() {
            // "AB" at font height 4, two characters wide per height unit: 16 long, 4 thick, centred
            // on the axis midpoint - a tenth of the 1000-long axis the placement was fitted along.
            assertThat(measureBoxes(buildAnchor(EASTWARD_AXIS, List.of("AB"))))
                .singleElement()
                .satisfies(box -> assertThat(box)
                    .containsExactly(
                        new double[] {-8, 12},
                        new double[] {8, 12},
                        new double[] {8, 8},
                        new double[] {-8, 8}));
        }

        @Test
        void measureLineBoxesGivesEachStackedLineItsOwnBox() {
            // A two-line name is two blocks of words at two hang points, so it is two boxes: one
            // long box spanning both would claim the ring between them, which nothing draws in.
            assertThat(measureBoxes(buildAnchor(EASTWARD_AXIS, List.of("AB", "CDEF"))))
                .hasSize(2);
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
        return NameLineBoxes.measureLineBoxes(List.of(anchor), buildCharacterWideMeasurer());
    }

    private static LineWidthMeasurer buildCharacterWideMeasurer() {
        return (line, fontSize) -> line.length() * fontSize * WIDTH_PER_CHARACTER_HEIGHT;
    }

    // One placement carrying just what a drawn line is read off - the accepted line the block is
    // centred and slanted on, the wrapped lines, and the height they render at. Every other fitted
    // component is inert here; the thickness is the girth a whole block takes, which is the fitted
    // reading's input rather than this one's.
    private static ClusterAnchor buildAnchor(Segment acceptedAxis, List<String> nameLines) {

        return new ClusterAnchor(
            new ClusterIdentity("hegemony", Set.of("hegemony")),
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
