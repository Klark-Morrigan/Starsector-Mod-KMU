package kmu.maplayers.base.tooltip.content;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the one contract a stretch of a name carries: it picks out characters that are actually there.
 * Worth fixing because everything downstream substrings the label by it without checking - a range
 * that starts before the name or ends at its own start reports as a failure inside the draw that
 * splits the label rather than at whoever resolved the range.
 */
final class CellTooltipLabelFindingTest {

    @Nested
    class Constructor {

        @Test
        void constructorKeepsThePositionsItWasGiven() {

            var labelFinding = new CellTooltipLabelFinding(0, 9);

            assertThat(labelFinding.startIndex())
                .isZero();
            assertThat(labelFinding.endIndex())
                .isEqualTo(9);
        }

        @Test
        void constructorRejectsAStretchStartingBeforeTheName() {

            assertThatThrownBy(() -> new CellTooltipLabelFinding(-1, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startIndex");
        }

        @Test
        void constructorRejectsAStretchThatPicksOutNothing() {
            // A caller that found no finding states none at all rather than an empty range, which the
            // draw would split the label around and come out with the name it started with.
            assertThatThrownBy(() -> new CellTooltipLabelFinding(4, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endIndex");
        }

        @Test
        void constructorRejectsAStretchEndingBeforeItStarts() {

            assertThatThrownBy(() -> new CellTooltipLabelFinding(9, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endIndex");
        }
    }
}
