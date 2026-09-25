package kmu.maplayers.ownermap.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the rules put their own authored lengths through their own shortening.
 *
 * <p>What the shortening does to a pair of lengths is stated beside the shortening itself. What is
 * stated here is the pairing: the shortening says nothing without some authored pair to modify, so
 * the one thing that can go wrong at this level is the two halves being crossed - a band laid at
 * this pass's shortening over lengths that came from somewhere else, which is a cell drawn to
 * proportions nobody set and reads as a knob the player moved to no effect.
 */
final class RibbonPlanRulesTest {

    // A market four widths long parted by two. Neither is the design's own proportion, so a result
    // that came from anywhere but these rules' own lengths is visible rather than coincidental.
    private static final RibbonSegmentLengths AUTHORED_LENGTHS = new RibbonSegmentLengths(4, 2);

    @Nested
    class ResolveUncontestedLengths {

        @Test
        void resolveUncontestedLengthsShortensTheMarketRunOfItsOwnAuthoredPair() {

            var lengths = new RibbonPlanRules(AUTHORED_LENGTHS, new UncontestedRibbonRuns(true))
                .resolveUncontestedLengths();

            assertThat(lengths)
                .isEqualTo(new RibbonSegmentLengths(1, 2));
        }

        @Test
        void resolveUncontestedLengthsAnswersItsOwnAuthoredPairWhereNothingIsShortened() {

            var lengths = new RibbonPlanRules(AUTHORED_LENGTHS, new UncontestedRibbonRuns(false))
                .resolveUncontestedLengths();

            assertThat(lengths)
                .isEqualTo(new RibbonSegmentLengths(4, 2));
        }
    }
}
