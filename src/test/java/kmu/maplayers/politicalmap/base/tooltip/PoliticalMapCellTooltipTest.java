package kmu.maplayers.politicalmap.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one thing naming the claim read in a single place buys: both of the layer's boxes run on
 * that read, so a decree cannot be resolved one way on one tab and another way on the next. Asserted
 * by identity, since two readers agreeing today is not the same as there being one.
 */
final class PoliticalMapCellTooltipTest {

    @Nested
    class VanillaClaimBreakdownReaderBinding {

        @Test
        void vanillaClaimBreakdownReaderBindingIsTheOneEveryViewsBoxRunsOn() {
            // The regression this guards: a view minting a reader of its own reads the same mechanic
            // through a second computation, which nothing on screen would show the player disagreeing.
            assertThat(SystemDominationTooltip.INSTANCE.claimBreakdownReader)
                .isSameAs(PoliticalMapCellTooltip.VANILLA_CLAIM_BREAKDOWN_READER);

            assertThat(SystemClaimTooltip.INSTANCE.claimBreakdownReader)
                .isSameAs(PoliticalMapCellTooltip.VANILLA_CLAIM_BREAKDOWN_READER);
        }
    }
}
