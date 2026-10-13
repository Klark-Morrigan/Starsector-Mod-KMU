package kmu.maplayers.politicalmap.claims;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.ownermap.owners.SpotlitOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
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
 * it leaves alone. The claim resolve and the spotlight holder are both stubbed - each has its own
 * suite - so these tests isolate the routing decision rather than the reads behind it.
 */
final class FilteredClaimsTests {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;
    private static final SystemOwner SPOTLIT_HOLDER =
        new SystemOwner("$spotlit", new ColourPair(PRIMARY, SECONDARY));

    private static final SystemKey HEGEMONY_CLAIMED = buildCellKey("hegemony-claimed");
    private static final SystemKey RIVAL_CLAIMED = buildCellKey("rival-claimed");

    // Two claimed systems under different claimants, so every case can assert both what the
    // spotlight moves and what it leaves where it was.
    private static Map<SystemKey, SystemOwner> buildTwoClaimantSector() {

        var claims = new LinkedHashMap<SystemKey, SystemOwner>();

        claims.put(HEGEMONY_CLAIMED, new SystemOwner("hegemony", new ColourPair(PRIMARY, SECONDARY)));
        claims.put(RIVAL_CLAIMED, new SystemOwner("tritachyon", new ColourPair(PRIMARY, SECONDARY)));

        return claims;
    }

    @Nested
    class ResolveFilteredClaims {

        @Test
        void passesThePlainClaimResolveThroughOffFilter() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.createOver(sectorMock, UNDER_THE_FOG, grouping);
            var claims = buildTwoClaimantSector();

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(
                        pass,
                        claimReaderMock))
                    .thenReturn(claims);

                var resolved = FilteredClaims.resolveFilteredClaims(
                    pass,
                    claimReaderMock,
                    null);

                // No selection means no spotlight to fuse anything into, so the claim resolve is
                // handed back untouched rather than copied.
                assertThat(resolved)
                    .isSameAs(claims);
            }
        }

        @Test
        void movesOnlyTheSelectedBlocsClaimsOntoTheSpotlightHolder() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.createOver(sectorMock, UNDER_THE_FOG, grouping);
            var claims = buildTwoClaimantSector();

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var spotlitOwnersMock = mockStatic(SpotlitOwners.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(
                        pass,
                        claimReaderMock))
                    .thenReturn(claims);

                spotlitOwnersMock
                    .when(() -> SpotlitOwners.createSpotlitOwner(any()))
                    .thenReturn(SPOTLIT_HOLDER);

                var resolved = FilteredClaims.resolveFilteredClaims(
                    pass,
                    claimReaderMock,
                    "hegemony");

                // The selected bloc's claim carries the one spotlight key, which is what draws it
                // at full strength and joins it to whatever else that bloc has spotlit; the
                // rival's keeps its own bloc key, which is what the style layer reads to recede it.
                assertThat(resolved.get(HEGEMONY_CLAIMED))
                    .isSameAs(SPOTLIT_HOLDER);
                assertThat(resolved.get(RIVAL_CLAIMED))
                    .isSameAs(claims.get(RIVAL_CLAIMED));
            }
        }

        @Test
        void dropsTheSelectedBlocsClaimsWhenItsPaletteIsGone() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.createOver(sectorMock, UNDER_THE_FOG, grouping);
            var claims = buildTwoClaimantSector();

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var spotlitOwnersMock = mockStatic(SpotlitOwners.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(
                        pass,
                        claimReaderMock))
                    .thenReturn(claims);

                spotlitOwnersMock
                    .when(() -> SpotlitOwners.createSpotlitOwner(any()))
                    .thenReturn(null);

                var resolved = FilteredClaims.resolveFilteredClaims(
                    pass,
                    claimReaderMock,
                    "hegemony");

                // The degenerate case: the selected bloc's colour faction vanished mid-session, so
                // its claims drop rather than paint colourless. Every other claim is unaffected.
                assertThat(resolved).containsExactly(
                    Map.entry(RIVAL_CLAIMED, claims.get(RIVAL_CLAIMED)));
            }
        }

        @Test
        void passesThePlainClaimResolveThroughWithNoSector() {

            var claimReaderMock = mock(ClaimReader.class);
            var pass = HolderPass.createOver(null, UNDER_THE_FOG, HolderGrouping.identity());

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(
                        pass,
                        claimReaderMock))
                    .thenReturn(Map.of());

                var resolved = FilteredClaims.resolveFilteredClaims(
                    pass,
                    claimReaderMock,
                    "hegemony");

                // A pass over no sector walks no systems, so there is no claim for the selection to
                // spotlight - the claim resolve's own empty answer stands.
                assertThat(resolved)
                    .isEmpty();
            }
        }
    }
}
