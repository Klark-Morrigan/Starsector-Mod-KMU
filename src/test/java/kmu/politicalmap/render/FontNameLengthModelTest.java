package kmu.politicalmap.render;

import kmlib.starsector.ui.font.LineWidthMeasurer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link FontNameLengthModel}: the required length is the widest line of a
 * word-boundary wrap balanced by measured width, scaled linearly by the asked line
 * height; the wrapped lines are exactly the split that measurement chose; and a name
 * with fewer words than lines reports the count unfillable. The width measurer is a
 * plain lambda - one unit per character per font size - so the balancing arithmetic is
 * pinned without a real face (the concrete font is a final class the port exists to
 * keep out of the test).
 */
final class FontNameLengthModelTest {

    @Nested
    class RequiredLengthFor {

        @Test
        void requiredLengthForScalesTheMeasuredWidthByTheLineHeight() {
            // One char is one unit wide per unit of line height, so "Hegemony" (8 chars)
            // at line height 200 needs 8 * 200.
            var model = new FontNameLengthModel(characterWideMeasurer(), "Hegemony");

            assertThat(model.requiredLengthFor(200.0, 1)).isCloseTo(1600.0, within(1e-3));
        }

        @Test
        void requiredLengthForUsesTheWidestLineOfTheBalancedTwoLineWrap() {
            // Splitting after "Persean" leaves a 15-char second line; after "League" a
            // 14-char first line - the balanced pick - so the widest line is 14 chars.
            var model = new FontNameLengthModel(characterWideMeasurer(),
                    "Persean League Alliance");

            assertThat(model.requiredLengthFor(100.0, 2)).isCloseTo(1400.0, within(1e-3));
        }

        @Test
        void requiredLengthForReturnsInfinityWhenTheNameHasFewerWordsThanLines() {
            // A one-word name cannot fill two lines, so the count reports unfillable and
            // the fit skips it (the one-line trial already covers the name).
            var model = new FontNameLengthModel(characterWideMeasurer(), "Hegemony");

            assertThat(model.requiredLengthFor(100.0, 2)).isInfinite();
        }
    }

    @Nested
    class WrapIntoLines {

        @Test
        void wrapIntoLinesReturnsTheWholeNameForOneLine() {
            var model = new FontNameLengthModel(characterWideMeasurer(),
                    "Persean League Alliance");

            assertThat(model.wrapIntoLines(1)).containsExactly("Persean League Alliance");
        }

        @Test
        void wrapIntoLinesSplitsOnWordBoundariesMinimisingTheWidestLine() {
            // The same balanced split requiredLengthFor measured: breaking after "League"
            // (widest line 14 chars) beats breaking after "Persean" (15 chars), so the
            // drawn block matches the measured fit.
            var model = new FontNameLengthModel(characterWideMeasurer(),
                    "Persean League Alliance");

            assertThat(model.wrapIntoLines(2))
                    .containsExactly("Persean League", "Alliance");
        }

        @Test
        void wrapIntoLinesReturnsExactlyTheRequestedLineCount() {
            var model = new FontNameLengthModel(characterWideMeasurer(),
                    "Persean League Alliance");

            assertThat(model.wrapIntoLines(3))
                    .containsExactly("Persean", "League", "Alliance");
        }

        @Test
        void wrapIntoLinesReturnsEmptyWhenTheNameHasFewerWordsThanLines() {
            var model = new FontNameLengthModel(characterWideMeasurer(), "Hegemony");

            assertThat(model.wrapIntoLines(2)).isEmpty();
        }

        @Test
        void wrapIntoLinesCollapsesSurroundingAndRepeatedWhitespace() {
            // Display names are not guaranteed tidy; the wrap works on the words, so
            // stray spacing never produces empty lines or padded widths.
            var model = new FontNameLengthModel(characterWideMeasurer(), "  Luddic   Church ");

            assertThat(model.wrapIntoLines(2)).containsExactly("Luddic", "Church");
        }
    }

    // A measurer whose width is one font-size unit per character - width proportional
    // to both inputs, like a real face, but with arithmetic a test can predict exactly.
    private static LineWidthMeasurer characterWideMeasurer() {
        return (line, fontSize) -> line.length() * fontSize;
    }
}
