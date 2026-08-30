package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the one rule both claim-reading providers share: which claims a spotlight rekeys and which
 * it leaves alone. The claim resolve and the spotlight holder are both stubbed - each has its own
 * suite - so these tests isolate the routing decision rather than the reads behind it.
 */
final class FilteredClaimsTest {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;
    private static final DominantHolder SPOTLIT_HOLDER =
        new DominantHolder("$spotlit", PRIMARY, SECONDARY);

    // Two claimed systems under different claimants, so every case can assert both what the
    // spotlight moves and what it leaves where it was.
    private static Map<String, DominantHolder> buildTwoClaimantSector() {

        var claims = new LinkedHashMap<String, DominantHolder>();

        claims.put("hegemony-claimed", new DominantHolder("hegemony", PRIMARY, SECONDARY));
        claims.put("rival-claimed", new DominantHolder("tritachyon", PRIMARY, SECONDARY));

        return claims;
    }

    @Nested
    class ResolveFilteredClaims {

        @Test
        void resolveFilteredClaimsPassesThePlainClaimResolveThroughOffFilter() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.over(sectorMock, BASE_FOG, grouping);
            var claims = buildTwoClaimantSector();

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(
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
        void resolveFilteredClaimsMovesOnlyTheSelectedBlocsClaimsOntoTheSpotlightHolder() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.over(sectorMock, BASE_FOG, grouping);
            var claims = buildTwoClaimantSector();

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        pass,
                        claimReaderMock))
                    .thenReturn(claims);

                filteredPoliticsMock
                    .when(() -> FilteredPolitics
                        .resolveSpotlitHolder(sectorMock, grouping, "hegemony"))
                    .thenReturn(SPOTLIT_HOLDER);

                var resolved = FilteredClaims.resolveFilteredClaims(
                    pass,
                    claimReaderMock,
                    "hegemony");

                // The selected bloc's claim carries the one spotlight key, which is what draws it
                // at full strength and joins it to whatever else that bloc has spotlit; the
                // rival's keeps its own bloc key, which is what the style layer reads to recede it.
                assertThat(resolved.get("hegemony-claimed"))
                    .isSameAs(SPOTLIT_HOLDER);
                assertThat(resolved.get("rival-claimed"))
                    .isSameAs(claims.get("rival-claimed"));
            }
        }

        @Test
        void resolveFilteredClaimsDropsTheSelectedBlocsClaimsWhenItsPaletteIsGone() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.over(sectorMock, BASE_FOG, grouping);
            var claims = buildTwoClaimantSector();

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        pass,
                        claimReaderMock))
                    .thenReturn(claims);

                filteredPoliticsMock
                    .when(() -> FilteredPolitics
                        .resolveSpotlitHolder(sectorMock, grouping, "hegemony"))
                    .thenReturn(null);

                var resolved = FilteredClaims.resolveFilteredClaims(
                    pass,
                    claimReaderMock,
                    "hegemony");

                // The degenerate case: the selected bloc's colour faction vanished mid-session, so
                // its claims drop rather than paint colourless. Every other claim is unaffected.
                assertThat(resolved).containsExactly(
                    Map.entry("rival-claimed", claims.get("rival-claimed")));
            }
        }

        @Test
        void resolveFilteredClaimsPassesThePlainClaimResolveThroughWithNoSector() {

            var claimReaderMock = mock(ClaimReader.class);
            var pass = HolderPass.over(null, BASE_FOG, HolderGrouping.identity());

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        pass,
                        claimReaderMock))
                    .thenReturn(Map.of());

                var resolved = FilteredClaims.resolveFilteredClaims(
                    pass,
                    claimReaderMock,
                    "hegemony");

                // There is no faction palette to read a spotlight holder from, so the selection is
                // not acted on at all - the claim resolve's own empty answer stands.
                assertThat(resolved)
                    .isEmpty();
            }
        }
    }
}
