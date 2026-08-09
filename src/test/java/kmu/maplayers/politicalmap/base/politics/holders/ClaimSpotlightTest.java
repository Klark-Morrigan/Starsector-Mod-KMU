package kmu.maplayers.politicalmap.base.politics.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the one rule both claim-reading providers share: which claims a spotlight rekeys and which
 * it leaves alone. The spotlight holder itself is stubbed - it has its own suite - so these tests
 * isolate the routing decision rather than the palette resolve behind it.
 */
final class ClaimSpotlightTest {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;
    private static final DominantHolder SPOTLIT_HOLDER =
        new DominantHolder("$spotlit", PRIMARY, SECONDARY);

    private static Map<String, DominantHolder> buildTwoClaimantSector() {

        var claims = new LinkedHashMap<String, DominantHolder>();

        claims.put("hegemony-claimed", new DominantHolder("hegemony", PRIMARY, SECONDARY));
        claims.put("rival-claimed", new DominantHolder("tritachyon", PRIMARY, SECONDARY));

        return claims;
    }

    @Nested
    class RekeyClaimsOntoSpotlight {

        @Test
        void rekeyClaimsOntoSpotlightPassesTheClaimsThroughOffFilter() {

            var sectorMock = mock(SectorAPI.class);
            var claims = buildTwoClaimantSector();
            var rekeyed = ClaimSpotlight.rekeyClaimsOntoSpotlight(
                sectorMock,
                HolderGrouping.identity(),
                claims,
                null);

            // No selection means no spotlight to fuse anything into, so the resolve is handed back
            // untouched rather than copied.
            assertThat(rekeyed)
                .isSameAs(claims);
        }

        @Test
        void rekeyClaimsOntoSpotlightPassesTheClaimsThroughWithNoSector() {

            var claims = buildTwoClaimantSector();
            var rekeyed = ClaimSpotlight.rekeyClaimsOntoSpotlight(
                null,
                HolderGrouping.identity(),
                claims,
                "hegemony");

            // There is no faction palette to read a spotlight holder from, so nothing is rekeyed.
            assertThat(rekeyed)
                .isSameAs(claims);
        }

        @Test
        void rekeyClaimsOntoSpotlightMovesOnlyTheSelectedBlocsClaimsOntoTheSpotlightHolder() {

            var sectorMock = mock(SectorAPI.class);
            var grouping = HolderGrouping.identity();
            var claims = buildTwoClaimantSector();

            try (var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                filteredPoliticsMock
                    .when(() -> FilteredPolitics
                        .resolveSpotlitHolder(sectorMock, grouping, "hegemony"))
                    .thenReturn(SPOTLIT_HOLDER);

                var rekeyed = ClaimSpotlight.rekeyClaimsOntoSpotlight(
                    sectorMock,
                    grouping,
                    claims,
                    "hegemony");

                // The selected bloc's claim carries the one spotlight key, so it fuses into that
                // bloc's single territory at full strength; the rival's keeps its own bloc key,
                // which is what the style layer reads to recede it.
                assertThat(rekeyed.get("hegemony-claimed"))
                    .isSameAs(SPOTLIT_HOLDER);
                assertThat(rekeyed.get("rival-claimed"))
                    .isSameAs(claims.get("rival-claimed"));
            }
        }

        @Test
        void rekeyClaimsOntoSpotlightDropsTheSelectedBlocsClaimsWhenItsPaletteIsGone() {

            var sectorMock = mock(SectorAPI.class);
            var grouping = HolderGrouping.identity();
            var claims = buildTwoClaimantSector();

            try (var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                filteredPoliticsMock
                    .when(() -> FilteredPolitics
                        .resolveSpotlitHolder(sectorMock, grouping, "hegemony"))
                    .thenReturn(null);

                var rekeyed = ClaimSpotlight.rekeyClaimsOntoSpotlight(
                    sectorMock,
                    grouping,
                    claims,
                    "hegemony");

                // The degenerate case: the selected bloc's colour faction vanished mid-session, so
                // its claims drop rather than paint colourless. Every other claim is unaffected.
                assertThat(rekeyed).containsExactly(
                    Map.entry("rival-claimed", claims.get("rival-claimed")));
            }
        }
    }
}
