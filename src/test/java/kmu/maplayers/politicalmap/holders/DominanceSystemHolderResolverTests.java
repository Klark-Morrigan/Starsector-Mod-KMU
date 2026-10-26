package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.BlocSpotlight;
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
import java.util.Optional;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins which contest a batch re-derives a marked system by: the plain one with no spotlight, filled
 * solid, and the presence-aware one under the spotlight the batch was opened under. Both contests
 * have suites of their own and are stood in for at their static seams.
 */
final class DominanceSystemHolderResolverTests {

    private static final SystemOwner HEGEMONY = new SystemOwner("hegemony", new ColourPair(Color.RED, Color.BLUE));
    private static final BlocSpotlight HEGEMONY_SPOTLIGHT = new BlocSpotlight("hegemony", HEGEMONY);

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
    class OpenResolverUnder {

        // With no spotlight - off filter, or a pick whose colour faction has gone - the batch
        // re-derives by the plain contest, which fills every system it gives anybody solid, as the
        // whole-sector pass does.
        @Test
        void answersThePlainContestsHolderFilledSolidWithNoSpotlight() {

            // ARRANGE
            var expectedResolved = new ResolvedSystemOwner(HEGEMONY, FillState.SOLID);

            plainContestMock
                .when(() -> SectorPolitics.resolveDominantHolder(same(systemMock), same(dominancePassMock)))
                .thenReturn(HEGEMONY);

            // ACT
            var actualResolved = DominanceSystemHolderResolver
                .openResolverUnder(pass, Optional.empty())
                .resolveHolderIn(systemMock);

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);

            spotlitContestMock.verifyNoInteractions();
        }

        // A system the contest gives nobody - nothing weighed there, or a system the sector no
        // longer lists - draws as nobody's, the one unowned answer.
        @Test
        void answersUnownedWhereThePlainContestNamesNobodyWithNoSpotlight() {

            // ARRANGE
            plainContestMock
                .when(() -> SectorPolitics.resolveDominantHolder(same(systemMock), same(dominancePassMock)))
                .thenReturn(null);

            // ACT
            var actualResolved = DominanceSystemHolderResolver
                .openResolverUnder(pass, Optional.empty())
                .resolveHolderIn(systemMock);

            // ASSERT
            assertThat(actualResolved)
                .isSameAs(ResolvedSystemOwner.UNOWNED);
        }

        // Under a spotlight the plain contest would overwrite the spotlit key, so the batch asks the
        // presence-aware contest, handed the very spotlight the batch was opened under.
        @Test
        void answersThePresenceAwareContestUnderTheSpotlight() {

            // ARRANGE
            var expectedResolved = new ResolvedSystemOwner(HEGEMONY, FillState.HATCHED);

            spotlitContestMock
                .when(() -> FilteredPolitics.resolveFilteredHolderIn(
                    same(systemMock),
                    same(dominancePassMock),
                    same(HEGEMONY_SPOTLIGHT)))
                .thenReturn(expectedResolved);

            // ACT
            var actualResolved = DominanceSystemHolderResolver
                .openResolverUnder(pass, Optional.of(HEGEMONY_SPOTLIGHT))
                .resolveHolderIn(systemMock);

            // ASSERT
            assertThat(actualResolved)
                .isSameAs(expectedResolved);

            plainContestMock.verifyNoInteractions();
        }
    }
}
