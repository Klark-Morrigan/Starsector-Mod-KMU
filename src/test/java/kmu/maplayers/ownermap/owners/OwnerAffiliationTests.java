package kmu.maplayers.ownermap.owners;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the affiliation a layer with no sides answers: under it no two owners stand together.
 */
final class OwnerAffiliationTests {

    @Nested
    class AreOwnersAllied {

        @Test
        void isFalseForTwoDistinctOwnersUnderNone() {

            assertThat(OwnerAffiliation.NONE.areOwnersAllied("first", "second"))
                .isFalse();
        }

        @Test
        void isFalseForOneOwnerNamedTwiceUnderNone() {
            // One owner named twice is one side, not two standing together.
            assertThat(OwnerAffiliation.NONE.areOwnersAllied("first", "first"))
                .isFalse();
        }
    }
}
