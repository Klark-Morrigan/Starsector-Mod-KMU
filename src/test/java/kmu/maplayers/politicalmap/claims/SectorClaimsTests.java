package kmu.maplayers.politicalmap.claims;

import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one rule every reader of a claim lands it on a bloc by: the claimant folded under the
 * grouping, and nobody where nobody claims.
 */
final class SectorClaimsTests {

    private static final String NO_CLAIMANT = null;

    @Nested
    class ReadClaimantBlocId {

        @Test
        void foldsTheClaimantIntoItsBlocUnderTheGrouping() {

            var grouping = HolderGroupingFixture.buildGroupOf("hegemony", "luddic_church");

            assertThat(SectorClaims.readClaimantBlocId("luddic_church", grouping))
                .contains(HolderGroupingFixture.GROUP_BLOC_ID);
        }

        @Test
        void keepsTheClaimantAsItsOwnBlocUnderTheIdentityGrouping() {

            assertThat(SectorClaims.readClaimantBlocId("tritachyon", HolderGrouping.identity()))
                .contains("tritachyon");
        }

        @Test
        void answersNobodyWhereNobodyClaimsTheSystem() {

            assertThat(SectorClaims.readClaimantBlocId(NO_CLAIMANT, HolderGrouping.identity()))
                .isEmpty();
        }
    }
}
