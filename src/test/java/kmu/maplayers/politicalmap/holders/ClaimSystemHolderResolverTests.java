package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.politicalmap.claims.FilteredClaims;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the Claims view's per-system holder read: the reader it asks is opened over the batch's own
 * pass, and the answer is the single-system spotlit claim resolve's, under the spotlight the batch
 * was opened under, filled solid. The resolver owns the fold, the colouring and the rekey, and has its
 * own suite.
 */
final class ClaimSystemHolderResolverTests {

    private static final String SPOTLIT_BLOC_ID = "hegemony";

    @Nested
    class CreateSourceReadingThrough {

        @Test
        void opensItsClaimReaderOverThePassesOwnWalk() {
            // The reader shares the batch's walk of each system rather than adding one.
            var pass = HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());
            var openedOver = new ArrayList<SectorPassIndex>();

            ClaimSystemHolderResolver
                .createSourceReadingThrough((visibility, colonies) -> {
                    openedOver.add(colonies);
                    return mock(ClaimReader.class);
                })
                .openResolverOver(pass, null);

            assertThat(openedOver)
                .containsExactly(pass.sectorIndex());
        }
    }

    @Nested
    class ResolveHolderIn {

        // The batch re-derives a marked system as the standing build painted it, so the spotlight it
        // was opened under reaches the claim rule, and the claim fills solid as every claim here does.
        @Test
        void answersWithTheClaimantTheSpotlitResolveFoldsFilledSolid() {

            // ARRANGE
            var pass = HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());
            var claimReaderMock = mock(ClaimReader.class);
            var systemMock = mock(StarSystemAPI.class);
            var claimant = new SystemOwner(SPOTLIT_BLOC_ID, new ColourPair(Color.RED, Color.BLUE));

            var resolver = ClaimSystemHolderResolver
                .createSourceReadingThrough((visibility, colonies) -> claimReaderMock)
                .openResolverOver(pass, SPOTLIT_BLOC_ID);

            try (var filteredClaimsMock = mockStatic(FilteredClaims.class)) {

                filteredClaimsMock
                    .when(() -> FilteredClaims.resolveFilteredClaimIn(
                        same(systemMock),
                        same(pass),
                        same(claimReaderMock),
                        eq(SPOTLIT_BLOC_ID)))
                    .thenReturn(claimant);

                // ACT
                var actualResolved = resolver.resolveHolderIn(systemMock);

                // ASSERT
                assertThat(actualResolved)
                    .isEqualTo(new ResolvedSystemOwner(claimant, FillState.SOLID));
            }
        }
    }
}
