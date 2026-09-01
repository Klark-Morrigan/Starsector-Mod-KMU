package kmu.maplayers.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a layer hands back for one paint: the blocks it composed, and whether it has anything
 * deeper to state.
 *
 * <p>What the blocks come to when there is room for all of them is pinned where the laying-out is
 * ({@link CellTooltipBlocksTest}); what is fixed here is the one state a layer states outright - that
 * it found nothing at all.
 */
final class ComposedCellBodyTest {

    @Nested
    class Nothing {

        @Test
        void nothingIsABodyWithNoBlocksAndNothingToOpenUp() {
            // The one spelling of "the layer found nothing", so a box drawn from it stays undrawn
            // and offers no key either - two spellings of that state agree only until one is edited.
            assertThat(ComposedCellBody.NOTHING.blocks().readSections())
                .isEmpty();
            assertThat(ComposedCellBody.NOTHING.hasDeeperDetail())
                .isFalse();
        }
    }
}
