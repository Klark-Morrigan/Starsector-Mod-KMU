package kmu.maplayers.politicalmap.claims;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.BlocSpotlight;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Optional;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the one rule every claim-reading resolver shares: which claims a spotlight rekeys
 * and which it leaves alone. The plain claim read is stood in for at its static seam, having its own
 * suite, so these cases isolate the rekey rather than the read behind it.
 */
final class FilteredClaimReaderTests {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    private static final SystemOwner SPOTLIT_HOLDER =
        new SystemOwner("$spotlit", new ColourPair(PRIMARY, SECONDARY));

    private static final SystemOwner HEGEMONY_CLAIMANT =
        new SystemOwner("hegemony", new ColourPair(PRIMARY, SECONDARY));

    private static final SystemOwner RIVAL_CLAIMANT =
        new SystemOwner("tritachyon", new ColourPair(PRIMARY, SECONDARY));

    private static final Optional<BlocSpotlight> HEGEMONY_SPOTLIGHT =
        Optional.of(new BlocSpotlight("hegemony", SPOTLIT_HOLDER));

    private final ClaimReader claimReaderMock = mock(ClaimReader.class);

    private final HolderPass pass =
        HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());

    @Nested
    class OpenOver {

        // Opened over the pass's own walk and colony rule, so the claims read the colonies the pass
        // already walked.
        @Test
        void opensItsClaimReaderOverThePassesOwnWalkAndColonyRule() {

            // ARRANGE
            var openedOver = new ArrayList<Object>();

            ClaimReaderSource claimReaderSource = (knownColonyReader, sectorIndex) -> {
                openedOver.add(knownColonyReader);
                openedOver.add(sectorIndex);
                return claimReaderMock;
            };

            // ACT
            FilteredClaimReader.openOver(pass, claimReaderSource, Optional.empty());

            // ASSERT
            assertThat(openedOver)
                .containsExactly(pass.colonyKnowledge(), pass.sectorIndex());
        }
    }

    @Nested
    class ResolveClaimIn {

        private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

        // With no spotlight - off filter, or a pick whose colour faction has gone - the pick's own
        // claim stays on its plain key, as its held systems stay on their real holder.
        @Test
        void answersThePlainClaimantWithNoSpotlight() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateTheClaimant(sectorClaimsMock, HEGEMONY_CLAIMANT);

                // ACT
                var actualClaimant = openWithReaderUnder(Optional.empty()).resolveClaimIn(systemMock);

                // ASSERT
                assertThat(actualClaimant)
                    .isSameAs(HEGEMONY_CLAIMANT);
            }
        }

        // The selected bloc's claim carries the one spotlight key, which is what draws it at full
        // strength and joins it to whatever else that bloc has spotlit.
        @Test
        void movesTheSelectedBlocsClaimOntoTheSpotlightHolder() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateTheClaimant(sectorClaimsMock, HEGEMONY_CLAIMANT);

                // ACT
                var actualClaimant = openWithReaderUnder(HEGEMONY_SPOTLIGHT).resolveClaimIn(systemMock);

                // ASSERT
                assertThat(actualClaimant)
                    .isSameAs(SPOTLIT_HOLDER);
            }
        }

        // A rival's claim keeps its own bloc key under the spotlight, which is what the style layer
        // reads to recede it.
        @Test
        void leavesARivalsClaimOnItsOwnKey() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateTheClaimant(sectorClaimsMock, RIVAL_CLAIMANT);

                // ACT
                var actualClaimant = openWithReaderUnder(HEGEMONY_SPOTLIGHT).resolveClaimIn(systemMock);

                // ASSERT
                assertThat(actualClaimant)
                    .isSameAs(RIVAL_CLAIMANT);
            }
        }

        // Nobody claims the system, so there is nothing for the spotlight to rekey.
        @Test
        void answersNobodyForAnUnclaimedSystemUnderASpotlight() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateTheClaimant(sectorClaimsMock, null);

                // ACT
                var actualClaimant = openWithReaderUnder(HEGEMONY_SPOTLIGHT).resolveClaimIn(systemMock);

                // ASSERT
                assertThat(actualClaimant)
                    .isNull();
            }
        }

        // What the single-system claim read answers for this case's system.
        private void stateTheClaimant(MockedStatic<SectorClaims> sectorClaimsMock, SystemOwner claimant) {

            sectorClaimsMock
                .when(() -> SectorClaims.resolveClaimingHolderIn(systemMock, pass, claimReaderMock))
                .thenReturn(claimant);
        }
    }

    // The claim read this suite's pass opens through its reader, under the given spotlight.
    private FilteredClaimReader openWithReaderUnder(Optional<BlocSpotlight> spotlight) {

        return FilteredClaimReader.openOver(
            pass,
            (knownColonyReader, sectorIndex) -> claimReaderMock,
            spotlight);
    }
}
