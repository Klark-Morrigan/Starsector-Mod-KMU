package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolve;
import kmu.maplayers.politicalmap.claims.FilteredClaims;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the faction and alliance views' per-system read: held dominance first, and the claim only
 * where the contest left the system unheld, folded unfilled by the rule the whole-sector provider
 * folds by - under the spotlight the batch was opened under, on both halves.
 *
 * <p>The contest and the claim read are each stood in for at their static seams, both having suites
 * of their own; what belongs here is how their two answers compose.
 */
final class ClaimAugmentedSystemHolderResolveTests {

    private static final String SPOTLIT_BLOC_ID = "hegemony";

    private static final SystemOwner HEGEMONY = new SystemOwner(
        "hegemony",
        new ColourPair(Color.RED, Color.BLUE));

    private static final SystemOwner TRITACHYON = new SystemOwner(
        "tritachyon",
        new ColourPair(Color.GREEN, Color.YELLOW));

    private final HolderPass pass = HolderPass.createOver(
        mock(SectorAPI.class),
        UNDER_THE_FOG,
        HolderGrouping.identity());

    private final ClaimReader claimReaderMock = mock(ClaimReader.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    private MockedStatic<DominanceSystemHolderResolve> contestMock;
    private MockedStatic<FilteredClaims> claimsMock;

    @BeforeEach
    void openTheSeams() {

        contestMock = mockStatic(DominanceSystemHolderResolve.class);
        claimsMock = mockStatic(FilteredClaims.class);
    }

    @AfterEach
    void closeTheSeams() {

        claimsMock.close();
        contestMock.close();
    }

    @Nested
    class ResolveHolderIn {

        // The held signal is the stronger one, so a held system keeps its holder and fill whatever
        // is claimed there, and the claim is not so much as read.
        @Test
        void keepsAHeldSystemsHolderAndFill() {

            // ARRANGE
            var held = new ResolvedSystemOwner(HEGEMONY, FillState.HATCHED);

            stateTheContest(held);

            // ACT
            var actualResolved = openUnderTheSpotlight().resolveHolderIn(systemMock);

            // ASSERT
            assertThat(actualResolved)
                .isSameAs(held);

            claimsMock.verifyNoInteractions();
        }

        // What a batch could not answer before: a system whose last colony left while its bloc still
        // claims it re-derives to that bloc's unfilled claim, as a rebuild draws it.
        @Test
        void answersAnUnheldClaimedSystemAsItsClaimantUnfilled() {

            // ARRANGE
            stateTheContest(ResolvedSystemOwner.UNOWNED);
            stateTheClaimant(TRITACHYON);

            // ACT
            var actualResolved = openUnderTheSpotlight().resolveHolderIn(systemMock);

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(new ResolvedSystemOwner(TRITACHYON, FillState.UNFILLED));
        }

        @Test
        void answersNobodyForAnUnheldUnclaimedSystem() {

            // ARRANGE
            stateTheContest(ResolvedSystemOwner.UNOWNED);
            stateTheClaimant(null);

            // ACT
            var actualResolved = openUnderTheSpotlight().resolveHolderIn(systemMock);

            // ASSERT
            assertThat(actualResolved)
                .isSameAs(ResolvedSystemOwner.UNOWNED);
        }
    }

    // The resolve a batch opens under the spotlight, through a claim reader opened over its pass.
    private SystemHolderResolve openUnderTheSpotlight() {

        return ClaimAugmentedSystemHolderResolve
            .createSourceReadingThrough((knownColonyReader, sectorIndex) -> claimReaderMock)
            .openResolveOver(pass, SPOTLIT_BLOC_ID);
    }

    // What the contest answers for the system, opened over the batch's pass under the spotlight -
    // any other opening answers nothing.
    private void stateTheContest(ResolvedSystemOwner held) {

        SystemHolderResolve heldResolve = system -> held;

        contestMock
            .when(() -> DominanceSystemHolderResolve.openResolveOver(same(pass), eq(SPOTLIT_BLOC_ID)))
            .thenReturn(heldResolve);
    }

    // What the spotlit claim read answers for the system, asked through the batch's reader under the
    // spotlight.
    private void stateTheClaimant(SystemOwner claimant) {

        claimsMock
            .when(() -> FilteredClaims.resolveFilteredClaimIn(
                same(systemMock),
                same(pass),
                same(claimReaderMock),
                eq(SPOTLIT_BLOC_ID)))
            .thenReturn(claimant);
    }
}
