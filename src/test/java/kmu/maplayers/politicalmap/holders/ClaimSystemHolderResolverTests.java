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
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolver;
import kmu.maplayers.politicalmap.claims.FilteredClaims;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
 * pass, and the answer is the claimant {@link FilteredClaims} names for one system, under the
 * spotlight the batch was opened under, filled solid. The fold into a bloc and the colouring are
 * {@code SectorClaims}', the rekey under a spotlight {@link FilteredClaims}', and each has its own
 * suite.
 */
final class ClaimSystemHolderResolverTests {

    private static final String SPOTLIT_BLOC_ID = "hegemony";

    private final HolderPass pass =
        HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());

    @Nested
    class CreateSourceReadingThrough {

        // The reader shares the batch's walk of each system rather than adding one.
        @Test
        void opensItsClaimReaderOverThePassesOwnWalk() {

            // ARRANGE
            var openedOver = new ArrayList<SectorPassIndex>();
            var source = ClaimSystemHolderResolver.createSourceReadingThrough((visibility, colonies) -> {
                openedOver.add(colonies);
                return mock(ClaimReader.class);
            });

            // ACT
            source.openResolverOver(pass, null);

            // ASSERT
            assertThat(openedOver)
                .containsExactly(pass.sectorIndex());
        }
    }

    @Nested
    class ResolveHolderIn {

        private final ClaimReader claimReaderMock = mock(ClaimReader.class);
        private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

        // The batch re-derives a marked system as the standing build painted it, so the spotlight it
        // was opened under reaches the claim rule, and the claim fills solid as every claim here does.
        @Test
        void answersWithTheClaimantTheSpotlitReadNamesFilledSolid() {

            // ARRANGE
            var claimant = new SystemOwner(SPOTLIT_BLOC_ID, new ColourPair(Color.RED, Color.BLUE));
            var expectedResolved = new ResolvedSystemOwner(
                new SystemOwner(SPOTLIT_BLOC_ID, new ColourPair(Color.RED, Color.BLUE)),
                FillState.SOLID);

            var resolver = openUnderTheSpotlight();

            try (var filteredClaimsMock = mockStatic(FilteredClaims.class)) {

                stateTheClaimant(filteredClaimsMock, claimant);

                // ACT
                var actualResolved = resolver.resolveHolderIn(systemMock);

                // ASSERT
                assertThat(actualResolved)
                    .isEqualTo(expectedResolved);
            }
        }

        // Nobody claims the system, so it draws as nobody's rather than as a claim with no claimant.
        @Test
        void answersUnownedForAnUnclaimedSystem() {

            // ARRANGE
            var resolver = openUnderTheSpotlight();

            try (var filteredClaimsMock = mockStatic(FilteredClaims.class)) {

                stateTheClaimant(filteredClaimsMock, null);

                // ACT
                var actualResolved = resolver.resolveHolderIn(systemMock);

                // ASSERT
                assertThat(actualResolved)
                    .isSameAs(ResolvedSystemOwner.UNOWNED);
            }
        }

        // The resolver a batch opens under the spotlight, through this case's reader.
        private SystemHolderResolver openUnderTheSpotlight() {

            return ClaimSystemHolderResolver
                .createSourceReadingThrough((visibility, colonies) -> claimReaderMock)
                .openResolverOver(pass, SPOTLIT_BLOC_ID);
        }

        // What the spotlit claim read answers for this case's system, asked through the batch's
        // reader under the spotlight.
        private void stateTheClaimant(MockedStatic<FilteredClaims> filteredClaimsMock, SystemOwner claimant) {

            filteredClaimsMock
                .when(() -> FilteredClaims.resolveFilteredClaimIn(
                    same(systemMock),
                    same(pass),
                    same(claimReaderMock),
                    eq(SPOTLIT_BLOC_ID)))
                .thenReturn(claimant);
        }
    }
}
