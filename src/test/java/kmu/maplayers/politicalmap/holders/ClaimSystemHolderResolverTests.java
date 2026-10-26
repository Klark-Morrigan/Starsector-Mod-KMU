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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the Claims view's per-system holder read: the claim read it asks is opened over the batch's
 * own pass, through the view's source and under the spotlight the batch was opened under, and its
 * answer fills solid. The rekey under a spotlight is {@link FilteredClaimReader}'s and the spotlight
 * {@link BlocSpotlight}'s, each with its own suite, so both are stood in for at their static seams.
 */
final class ClaimSystemHolderResolverTests {

    private static final String SPOTLIT_BLOC_ID = "hegemony";

    private static final SystemOwner HEGEMONY = new SystemOwner(
        SPOTLIT_BLOC_ID,
        new ColourPair(Color.RED, Color.BLUE));

    private static final Optional<BlocSpotlight> HEGEMONY_SPOTLIGHT =
        Optional.of(new BlocSpotlight(SPOTLIT_BLOC_ID, HEGEMONY));

    private final ClaimReaderSource claimReaderSource = (knownColonyReader, sectorIndex) -> null;
    private final FilteredClaimReader claimReaderMock = mock(FilteredClaimReader.class);
    private final HolderPass pass =
        HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());

    private MockedStatic<BlocSpotlight> spotlightMock;
    private MockedStatic<FilteredClaimReader> claimReaderOpenerMock;

    @BeforeEach
    void openTheSeams() {

        spotlightMock = mockStatic(BlocSpotlight.class);
        spotlightMock
            .when(() -> BlocSpotlight.openOver(same(pass), eq(SPOTLIT_BLOC_ID)))
            .thenReturn(HEGEMONY_SPOTLIGHT);

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
        spotlightMock.close();
    }

    @Nested
    class ResolveHolderIn {

        private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

        // The batch re-derives a marked system as the standing build painted it, so the spotlight it
        // was opened under reaches the claim rule, and the claim fills solid as every claim here does.
        @Test
        void answersWithTheClaimantTheSpotlitReadNamesFilledSolid() {

            // ARRANGE
            var expectedResolved = new ResolvedSystemOwner(
                new SystemOwner(SPOTLIT_BLOC_ID, new ColourPair(Color.RED, Color.BLUE)),
                FillState.SOLID);

            when(claimReaderMock.resolveClaimIn(systemMock))
                .thenReturn(HEGEMONY);

            // ACT
            var actualResolved = openUnderTheSpotlight().resolveHolderIn(systemMock);

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }

        // Nobody claims the system, so it draws as nobody's rather than as a claim with no claimant.
        @Test
        void answersUnownedForAnUnclaimedSystem() {

            // ARRANGE
            when(claimReaderMock.resolveClaimIn(systemMock))
                .thenReturn(null);

            // ACT
            var actualResolved = openUnderTheSpotlight().resolveHolderIn(systemMock);

            // ASSERT
            assertThat(actualResolved)
                .isSameAs(ResolvedSystemOwner.UNOWNED);
        }
    }

    // The resolver a batch opens under the spotlight, through this suite's source.
    private SystemHolderResolver openUnderTheSpotlight() {

        return ClaimSystemHolderResolver
            .createSourceReadingThrough(claimReaderSource)
            .openResolverOver(pass, SPOTLIT_BLOC_ID);
    }
}
