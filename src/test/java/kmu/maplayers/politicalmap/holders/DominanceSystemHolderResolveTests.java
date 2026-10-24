package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.FilteredPolitics;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins which contest a batch re-derives a marked system by: the plain one off filter, filled solid,
 * and the presence-aware one under the spotlight the batch was opened under, resolved once for the
 * batch. Both contests have suites of their own and are stood in for at their static seams.
 */
final class DominanceSystemHolderResolveTests {

    private static final String SPOTLIT_BLOC_ID = "hegemony";
    private static final SystemOwner HEGEMONY = new SystemOwner("hegemony", new ColourPair(Color.RED, Color.BLUE));

    private final HolderPass pass = HolderPass.createOver(
        mock(SectorAPI.class),
        UNDER_THE_FOG,
        HolderGrouping.identity());

    private final DominancePass dominancePassMock = mock(DominancePass.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    private MockedStatic<DominancePass> rulesMock;
    private MockedStatic<SectorPolitics> plainContestMock;
    private MockedStatic<FilteredPolitics> spotlitContestMock;

    @BeforeEach
    void openTheSeams() {

        rulesMock = mockStatic(DominancePass.class);
        rulesMock
            .when(() -> DominancePass.readRulesFromLunaSettings(same(pass)))
            .thenReturn(dominancePassMock);

        plainContestMock = mockStatic(SectorPolitics.class);
        spotlitContestMock = mockStatic(FilteredPolitics.class);
    }

    @AfterEach
    void closeTheSeams() {

        spotlitContestMock.close();
        plainContestMock.close();
        rulesMock.close();
    }

    @Nested
    class ResolveHolderIn {

        @Test
        void answersThePlainContestsHolderFilledSolidOffFilter() {

            // ARRANGE
            plainContestMock
                .when(() -> SectorPolitics.resolveDominantHolder(same(systemMock), same(dominancePassMock)))
                .thenReturn(HEGEMONY);

            // ACT
            var actualResolved = DominanceSystemHolderResolve
                .openResolveOver(pass, null)
                .resolveHolderIn(systemMock);

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(new ResolvedSystemOwner(HEGEMONY, FillState.SOLID));

            spotlitContestMock.verifyNoInteractions();
        }

        // Under a spotlight the plain contest would overwrite the spotlit key, which is the one
        // thing a filtered map could not fold before: the batch asks the presence-aware contest.
        @Test
        void answersThePresenceAwareContestUnderTheSpotlight() {

            // ARRANGE
            var spotlight = new FilteredPolitics.Spotlight(SPOTLIT_BLOC_ID, HEGEMONY);
            var expectedResolved = new ResolvedSystemOwner(HEGEMONY, FillState.HATCHED);

            spotlitContestMock
                .when(() -> FilteredPolitics.createSpotlight(same(dominancePassMock), eq(SPOTLIT_BLOC_ID)))
                .thenReturn(spotlight);

            spotlitContestMock
                .when(() -> FilteredPolitics.resolveFilteredHolderIn(
                    same(systemMock),
                    same(dominancePassMock),
                    same(spotlight)))
                .thenReturn(expectedResolved);

            // ACT
            var actualResolved = DominanceSystemHolderResolve
                .openResolveOver(pass, SPOTLIT_BLOC_ID)
                .resolveHolderIn(systemMock);

            // ASSERT
            assertThat(actualResolved)
                .isSameAs(expectedResolved);

            plainContestMock.verifyNoInteractions();
        }

        // The spotlight is one batch's, as it is one walk's in the whole-sector arm, so every
        // system the batch re-derives is painted the one spotlit holder.
        @Test
        void resolvesTheSpotlightOnceForTheBatch() {

            // ARRANGE
            spotlitContestMock
                .when(() -> FilteredPolitics.createSpotlight(any(), any()))
                .thenReturn(new FilteredPolitics.Spotlight(SPOTLIT_BLOC_ID, HEGEMONY));
            spotlitContestMock
                .when(() -> FilteredPolitics.resolveFilteredHolderIn(any(), any(), any()))
                .thenReturn(ResolvedSystemOwner.UNOWNED);

            var resolve = DominanceSystemHolderResolve.openResolveOver(pass, SPOTLIT_BLOC_ID);

            // ACT
            resolve.resolveHolderIn(systemMock);
            resolve.resolveHolderIn(mock(StarSystemAPI.class));

            // ASSERT
            spotlitContestMock.verify(
                () -> FilteredPolitics.createSpotlight(any(), any()));
        }
    }
}
