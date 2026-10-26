package kmu.maplayers.politicalmap.claims;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.ownermap.owners.SpotlitOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the one rule both claim-reading providers share: which claims a spotlight rekeys and which
 * it leaves alone. The plain claim read and the spotlight holder are both stubbed - each has its own
 * suite - so these tests isolate the routing decision rather than the reads behind it.
 */
final class FilteredClaimsTests {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;
    private static final SystemOwner SPOTLIT_HOLDER =
        new SystemOwner("$spotlit", new ColourPair(PRIMARY, SECONDARY));

    private static final SystemOwner HEGEMONY_CLAIMANT =
        new SystemOwner("hegemony", new ColourPair(PRIMARY, SECONDARY));
    private static final SystemOwner RIVAL_CLAIMANT =
        new SystemOwner("tritachyon", new ColourPair(PRIMARY, SECONDARY));

    private static final SystemKey HEGEMONY_CLAIMED = buildCellKey("hegemony-claimed");
    private static final SystemKey RIVAL_CLAIMED = buildCellKey("rival-claimed");

    private final ClaimReader claimReaderMock = mock(ClaimReader.class);
    private final HolderPass pass =
        HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());

    // Two claimed systems under different claimants, so every case can state both what the
    // spotlight moves and what it leaves where it was.
    private final Map<SystemKey, SystemOwner> claims = buildTwoClaimantSector();

    @Nested
    class ResolveFilteredClaimsThrough {

        // Opened over the pass's own walk and colony rule, so the claims read the colonies the pass
        // already walked.
        @Test
        void resolvesThroughAReaderOpenedOverThePass() {

            // ARRANGE
            var openedOver = new ArrayList<Object>();

            ClaimReaderSource claimReaderSource = (knownColonyReader, sectorIndex) -> {
                openedOver.add(knownColonyReader);
                openedOver.add(sectorIndex);
                return claimReaderMock;
            };

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateThePlainClaims(sectorClaimsMock, pass, claims);

                // ACT
                var actualClaims = FilteredClaims.resolveFilteredClaimsThrough(pass, claimReaderSource, null);

                // ASSERT
                assertThat(actualClaims)
                    .isSameAs(claims);
            }

            assertThat(openedOver)
                .containsExactly(pass.colonyKnowledge(), pass.sectorIndex());
        }
    }

    @Nested
    class ResolveFilteredClaims {

        // No selection means no spotlight to fuse anything into, so the plain claim read is handed
        // back untouched rather than copied.
        @Test
        void passesThePlainClaimReadThroughOffFilter() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateThePlainClaims(sectorClaimsMock, pass, claims);

                // ACT
                var actualClaims = FilteredClaims.resolveFilteredClaims(pass, claimReaderMock, null);

                // ASSERT
                assertThat(actualClaims)
                    .isSameAs(claims);
            }
        }

        // The selected bloc's claim carries the one spotlight key, which is what draws it at full
        // strength and joins it to whatever else that bloc has spotlit; the rival's keeps its own
        // bloc key, which is what the style layer reads to recede it.
        @Test
        void movesOnlyTheSelectedBlocsClaimsOntoTheSpotlightHolder() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var spotlitOwnersMock = mockStatic(SpotlitOwners.class)) {

                stateThePlainClaims(sectorClaimsMock, pass, claims);
                stateTheSpotlightHolder(spotlitOwnersMock, SPOTLIT_HOLDER);

                // ACT
                var actualClaims = FilteredClaims.resolveFilteredClaims(pass, claimReaderMock, "hegemony");

                // ASSERT
                assertThat(actualClaims)
                    .containsExactly(
                        Map.entry(HEGEMONY_CLAIMED, SPOTLIT_HOLDER),
                        Map.entry(RIVAL_CLAIMED, RIVAL_CLAIMANT));
            }
        }

        // The degenerate case: the selected bloc's colour faction vanished mid-session, so its
        // claims drop rather than paint colourless. Every other claim is unaffected.
        @Test
        void dropsTheSelectedBlocsClaimsWhenItsPaletteIsGone() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var spotlitOwnersMock = mockStatic(SpotlitOwners.class)) {

                stateThePlainClaims(sectorClaimsMock, pass, claims);
                stateTheSpotlightHolder(spotlitOwnersMock, null);

                // ACT
                var actualClaims = FilteredClaims.resolveFilteredClaims(pass, claimReaderMock, "hegemony");

                // ASSERT
                assertThat(actualClaims)
                    .containsExactly(Map.entry(RIVAL_CLAIMED, RIVAL_CLAIMANT));
            }
        }

        // A pass over no sector walks no systems, so there is no claim for the selection to
        // spotlight - the plain read's own empty answer stands.
        @Test
        void passesThePlainClaimReadThroughWithNoSector() {

            // ARRANGE
            var passOverNoSector = HolderPass.createOver(null, UNDER_THE_FOG, HolderGrouping.identity());

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateThePlainClaims(sectorClaimsMock, passOverNoSector, Map.of());

                // ACT
                var actualClaims = FilteredClaims.resolveFilteredClaims(
                    passOverNoSector,
                    claimReaderMock,
                    "hegemony");

                // ASSERT
                assertThat(actualClaims)
                    .isEmpty();
            }
        }
    }

    @Nested
    class ResolveFilteredClaimIn {

        private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

        // Off filter a batch re-derives the plain claimant, exactly as the whole-sector arm hands
        // the plain read back.
        @Test
        void answersThePlainClaimantOffFilter() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateTheClaimant(sectorClaimsMock, HEGEMONY_CLAIMANT);

                // ACT
                var actualClaimant = FilteredClaims.resolveFilteredClaimIn(systemMock, pass, claimReaderMock, null);

                // ASSERT
                assertThat(actualClaimant)
                    .isSameAs(HEGEMONY_CLAIMANT);
            }
        }

        // The single-system arm rekeys by the same rule as the whole-sector one, so a re-derived
        // claim of the selected bloc joins its spotlit territory as the rebuild drew it.
        @Test
        void movesTheSelectedBlocsClaimOntoTheSpotlightHolder() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var spotlitOwnersMock = mockStatic(SpotlitOwners.class)) {

                stateTheClaimant(sectorClaimsMock, HEGEMONY_CLAIMANT);
                stateTheSpotlightHolder(spotlitOwnersMock, SPOTLIT_HOLDER);

                // ACT
                var actualClaimant = FilteredClaims.resolveFilteredClaimIn(
                    systemMock,
                    pass,
                    claimReaderMock,
                    "hegemony");

                // ASSERT
                assertThat(actualClaimant)
                    .isSameAs(SPOTLIT_HOLDER);
            }
        }

        // A rival's claim keeps its own key under the spotlight, so the style layer recedes it.
        @Test
        void leavesARivalsClaimOnItsOwnKey() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var spotlitOwnersMock = mockStatic(SpotlitOwners.class)) {

                stateTheClaimant(sectorClaimsMock, RIVAL_CLAIMANT);
                stateTheSpotlightHolder(spotlitOwnersMock, SPOTLIT_HOLDER);

                // ACT
                var actualClaimant = FilteredClaims.resolveFilteredClaimIn(
                    systemMock,
                    pass,
                    claimReaderMock,
                    "hegemony");

                // ASSERT
                assertThat(actualClaimant)
                    .isSameAs(RIVAL_CLAIMANT);
            }
        }

        // The single-system arm drops the selected bloc's claim when its colour faction has gone,
        // as the whole-sector arm does: today the claim leaves the map, where the held arm keeps the
        // system on its real holder.
        @Test
        void dropsTheSelectedBlocsClaimWhenItsPaletteIsGone() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var spotlitOwnersMock = mockStatic(SpotlitOwners.class)) {

                stateTheClaimant(sectorClaimsMock, HEGEMONY_CLAIMANT);
                stateTheSpotlightHolder(spotlitOwnersMock, null);

                // ACT
                var actualClaimant = FilteredClaims.resolveFilteredClaimIn(
                    systemMock,
                    pass,
                    claimReaderMock,
                    "hegemony");

                // ASSERT
                assertThat(actualClaimant)
                    .isNull();
            }
        }

        // Nobody claims the system, so there is nothing for the spotlight to rekey.
        @Test
        void answersNobodyForAnUnclaimedSystemUnderASpotlight() {

            // ARRANGE
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateTheClaimant(sectorClaimsMock, null);

                // ACT
                var actualClaimant = FilteredClaims.resolveFilteredClaimIn(
                    systemMock,
                    pass,
                    claimReaderMock,
                    "hegemony");

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

    // The holder every claim of the selected bloc draws as; null for a colour faction that is gone.
    private static void stateTheSpotlightHolder(
            MockedStatic<SpotlitOwners> spotlitOwnersMock,
            SystemOwner spotlightHolder) {

        spotlitOwnersMock
            .when(() -> SpotlitOwners.createSpotlitOwner(any()))
            .thenReturn(spotlightHolder);
    }

    private static Map<SystemKey, SystemOwner> buildTwoClaimantSector() {

        var claimingHolderBySystemKey = new LinkedHashMap<SystemKey, SystemOwner>();

        claimingHolderBySystemKey.put(HEGEMONY_CLAIMED, HEGEMONY_CLAIMANT);
        claimingHolderBySystemKey.put(RIVAL_CLAIMED, RIVAL_CLAIMANT);

        return claimingHolderBySystemKey;
    }

    // What the whole-sector claim read answers over the given pass, through this suite's reader.
    private void stateThePlainClaims(
            MockedStatic<SectorClaims> sectorClaimsMock,
            HolderPass claimedPass,
            Map<SystemKey, SystemOwner> claimingHolderBySystemKey) {

        sectorClaimsMock
            .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(claimedPass, claimReaderMock))
            .thenReturn(claimingHolderBySystemKey);
    }
}
