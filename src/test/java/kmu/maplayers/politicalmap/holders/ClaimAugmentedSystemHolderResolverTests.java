package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.BlocSpotlight;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolver;
import kmu.maplayers.politicalmap.claims.FilteredClaimReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.Optional;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the faction and alliance views' holding rule: held dominance first, and the claim only where
 * the contest left the system unheld, folded in unfilled - under one spotlight the rebuild or batch
 * opens for both halves.
 *
 * <p>The spotlight, the contest and the claim read are each stood in for at their static seams, all
 * having suites of their own; what belongs here is how their answers compose.
 */
final class ClaimAugmentedSystemHolderResolverTests {

    private static final String SPOTLIT_BLOC_ID = "hegemony";

    private static final SystemOwner HEGEMONY = new SystemOwner(
        "hegemony",
        new ColourPair(Color.RED, Color.BLUE));

    private static final SystemOwner TRITACHYON = new SystemOwner(
        "tritachyon",
        new ColourPair(Color.GREEN, Color.YELLOW));

    private static final Optional<BlocSpotlight> HEGEMONY_SPOTLIGHT =
        Optional.of(new BlocSpotlight(SPOTLIT_BLOC_ID, HEGEMONY));

    private final HolderPass pass = HolderPass.createOver(
        mock(SectorAPI.class),
        UNDER_THE_FOG,
        HolderGrouping.identity());

    private final ClaimReaderSource claimReaderSource = (knownColonyReader, sectorIndex) -> null;
    private final FilteredClaimReader claimReaderMock = mock(FilteredClaimReader.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    private MockedStatic<BlocSpotlight> spotlightMock;
    private MockedStatic<DominanceSystemHolderResolver> contestMock;
    private MockedStatic<FilteredClaimReader> claimReaderOpenerMock;

    @BeforeEach
    void openTheSeams() {

        spotlightMock = mockStatic(BlocSpotlight.class);
        spotlightMock
            .when(() -> BlocSpotlight.openOver(same(pass), eq(SPOTLIT_BLOC_ID)))
            .thenReturn(HEGEMONY_SPOTLIGHT);

        contestMock = mockStatic(DominanceSystemHolderResolver.class);

        claimReaderOpenerMock = mockStatic(FilteredClaimReader.class);
        claimReaderOpenerMock
            .when(() -> FilteredClaimReader.openOver(
                same(pass),
                same(claimReaderSource),
                same(HEGEMONY_SPOTLIGHT)))
            .thenReturn(claimReaderMock);
    }

    @AfterEach
    void closeTheSeams() {

        claimReaderOpenerMock.close();
        contestMock.close();
        spotlightMock.close();
    }

    @Nested
    class CreateSourceReadingThrough {

        // The held and the claimed cells of the spotlit bloc fuse only through one value, so the
        // batch opens its spotlight once and hands that same one to both halves.
        @Test
        void opensOneSpotlightForBothHalves() {

            // ARRANGE
            stateTheContest(ResolvedSystemOwner.UNOWNED);

            // ACT
            openUnderTheSpotlight();

            // ASSERT
            spotlightMock.verify(() -> BlocSpotlight.openOver(any(), any()));

            contestMock.verify(() -> DominanceSystemHolderResolver.openResolverUnder(
                same(pass),
                same(HEGEMONY_SPOTLIGHT)));
            claimReaderOpenerMock.verify(() -> FilteredClaimReader.openOver(
                same(pass),
                same(claimReaderSource),
                same(HEGEMONY_SPOTLIGHT)));
        }
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

            verifyNoInteractions(claimReaderMock);
        }

        // What a batch could not answer before: a system whose last colony left while its bloc still
        // claims it re-derives to that bloc's unfilled claim, as a rebuild draws it.
        @Test
        void answersAnUnheldClaimedSystemAsItsClaimantUnfilled() {

            // ARRANGE
            var expectedResolved = new ResolvedSystemOwner(
                new SystemOwner("tritachyon", new ColourPair(Color.GREEN, Color.YELLOW)),
                FillState.UNFILLED);

            stateTheContest(ResolvedSystemOwner.UNOWNED);
            stateTheClaimant(TRITACHYON);

            // ACT
            var actualResolved = openUnderTheSpotlight().resolveHolderIn(systemMock);

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }

        // Neither half names anybody, so the system draws as nobody's, as a rebuild leaves it.
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

    // The resolver a batch opens under the spotlight, through this suite's source.
    private SystemHolderResolver openUnderTheSpotlight() {

        return ClaimAugmentedSystemHolderResolver
            .createSourceReadingThrough(claimReaderSource)
            .openResolverOver(pass, SPOTLIT_BLOC_ID);
    }

    // What the contest answers for the system, opened over the batch's pass under its spotlight -
    // any other opening answers nothing.
    private void stateTheContest(ResolvedSystemOwner held) {

        SystemHolderResolver heldResolver = system -> held;

        contestMock
            .when(() -> DominanceSystemHolderResolver.openResolverUnder(same(pass), same(HEGEMONY_SPOTLIGHT)))
            .thenReturn(heldResolver);
    }

    // What the spotlit claim read answers for the system.
    private void stateTheClaimant(SystemOwner claimant) {

        when(claimReaderMock.resolveClaimIn(systemMock))
            .thenReturn(claimant);
    }
}
