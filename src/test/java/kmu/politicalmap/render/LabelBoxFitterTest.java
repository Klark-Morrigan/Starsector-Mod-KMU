package kmu.politicalmap.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link LabelBoxFitter#fitLargestBox}: given one candidate placement against a
 * region, it caps the font so the whole band stays inside the border, stacks a name
 * into more lines only when that renders a strictly taller font, honours the line cap
 * and the per-line font clamp, and returns null when even the minimum font cannot hold
 * the name. The fitter is tested apart from the anchor search so its sizing contract is
 * pinned on rings and a name model directly, without the candidate generation the
 * builder wraps it in.
 */
final class LabelBoxFitterTest {

    @Nested
    class FitLargestBox {
        @Test
        void fitLargestBoxCapsTheGirthAtTheRegionSoTheBandStaysInside() {
            // A slab 2000 wide, 700 tall, a fat name (aspect 1) that wants all the girth
            // it can get: the band cannot exceed the 700 the region allows, so the fit caps
            // it just under 700 and the whole band, centred at y=350, stays within y 0..700.
            var box = fitter(1.0, 100.0, 2000.0, 1, 1.0)
                    .fitLargestBox(horizontalPlacement(rectangle(0, 0, 2000, 700), 1000, 350));

            assertThat(box).isNotNull();
            assertThat(box.lineCount()).isEqualTo(1);
            assertThat(box.thickness()).isGreaterThan(600.0);
            assertThat(box.thickness()).isLessThanOrEqualTo(700.0);
            assertThat(box.segment().startY()).isCloseTo(350f, within(1f));
        }

        @Test
        void fitLargestBoxStacksASquareNameIntoTwoLines() {
            // In a 1700-square region a name six times as long as it is tall runs strictly
            // taller on two lines than one - half the length each line needs, spent against
            // the spare girth - and taller than three, which the girth cannot make taller
            // still.
            var box = fitter(6.0, 100.0, 1700.0, 3, 1.15)
                    .fitLargestBox(horizontalPlacement(rectangle(0, 0, 1700, 1700), 850, 850));

            assertThat(box).isNotNull();
            assertThat(box.lineCount()).isEqualTo(2);
        }

        @Test
        void fitLargestBoxKeepsOneLineWhenStackingBuysNoTallerFont() {
            // In the wide slab the single line is not length-limited (aspect 4 at the
            // girth-capped font still fits), so two lines - feasible, but girth-halved to
            // a smaller font - lose to it: a block goes multi-line only when stacking
            // renders a strictly larger font.
            var box = fitter(4.0, 100.0, 2000.0, 3, 1.0)
                    .fitLargestBox(horizontalPlacement(rectangle(0, 0, 2000, 700), 1000, 350));

            assertThat(box).isNotNull();
            assertThat(box.lineCount()).isEqualTo(1);
            assertThat(box.fontHeight()).isGreaterThan(490.0);
        }

        @Test
        void fitLargestBoxStacksWhenTheSingleLineCannotHoldTheNameAtTheMinimumFont() {
            // At the 340 floor a one-line name (aspect 10) needs 3400 - more than the slab
            // holds - but two lines halve that to 1700 and their 680 girth still fits the
            // 700 the region allows: wrapping rescues a name the single line cannot carry
            // at readable size.
            var box = fitter(10.0, 340.0, 2000.0, 2, 1.0)
                    .fitLargestBox(horizontalPlacement(rectangle(0, 0, 2000, 700), 1000, 350));

            assertThat(box).isNotNull();
            assertThat(box.lineCount()).isEqualTo(2);
        }

        @Test
        void fitLargestBoxReturnsNullWhenTheMinimumFontFitsNoLineCount() {
            // A 360 floor: one line needs 3600 of length (more than the slab), two lines
            // need 720 of girth (more than the 700 the region allows) - every count fails
            // at the readability floor, so no box at all.
            var box = fitter(10.0, 360.0, 2000.0, 2, 1.0)
                    .fitLargestBox(horizontalPlacement(rectangle(0, 0, 2000, 700), 1000, 350));

            assertThat(box).isNull();
        }

        @Test
        void fitLargestBoxKeepsOneLineWhenTheLineCapIsOne() {
            // The square that would prefer two lines is held to one when the line cap is
            // one, so the name stays a single line at the smaller font the cap forces.
            var box = fitter(6.0, 100.0, 1700.0, 1, 1.15)
                    .fitLargestBox(horizontalPlacement(rectangle(0, 0, 1700, 1700), 850, 850));

            assertThat(box).isNotNull();
            assertThat(box.lineCount()).isEqualTo(1);
        }

        @Test
        void fitLargestBoxReturnsNullWhenTheMinimumBandCannotFit() {
            // A minimum font taller than the 1700 the square holds cannot sit anywhere,
            // so the fit finds no box at all.
            var box = fitter(6.0, 3000.0, 4000.0, 1, 1.0)
                    .fitLargestBox(horizontalPlacement(rectangle(0, 0, 1700, 1700), 850, 850));

            assertThat(box).isNull();
        }
    }

    // A fitter with no icon clearance and no end inset, so a test isolates the font and
    // line-count sizing from the icon and margin trims; the name model is the aspect
    // stand-in, whose required length the tests can compute by hand.
    private static LabelBoxFitter fitter(double aspect, double minFontHeight,
            double maxFontHeight, int maxLines, double lineSpacing) {
        return new LabelBoxFitter(minFontHeight, maxFontHeight, maxLines, lineSpacing, 0.0,
                0.0, new AspectNameLengthModel(aspect));
    }

    // A horizontal candidate line through the given point against one border ring and no
    // icons - the simplest placement the sizing tests need.
    private static Placement horizontalPlacement(List<double[]> ring, double throughX,
            double throughY) {
        return new Placement(List.of(ring), List.of(), throughX, throughY,
                new double[] {1.0, 0.0});
    }

    // A counter-clockwise rectangle ring anchored at (minX, minY).
    private static List<double[]> rectangle(double minX, double minY, double width,
            double height) {
        return Arrays.asList(
                new double[] {minX, minY},
                new double[] {minX + width, minY},
                new double[] {minX + width, minY + height},
                new double[] {minX, minY + height});
    }
}
