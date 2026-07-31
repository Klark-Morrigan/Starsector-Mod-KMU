package kmu.maplayers.politicalmap.base.politics.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.SectorClaims;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the claims-only provider's one job: handing the claim resolve straight through as the whole
 * holding, every claimed system solid. The claim resolve is stubbed - it has its own suite - so
 * these tests isolate that this provider adds no fill exceptions of its own and reads no filter.
 */
final class ClaimsHolderProviderTest {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    @Nested
    class ResolveHolder {

        @Test
        void resolveHolderPaintsEveryClaimSolidWithNoFillExceptions() {
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = HolderGrouping.identity();
            var claimHolder = new DominantHolder("hegemony", PRIMARY, SECONDARY);
            var provider = new ClaimsHolderProvider(claimReaderMock);
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {
                sectorClaimsMock.when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        sectorMock, grouping, claimReaderMock))
                        .thenReturn(Map.of("claimed", claimHolder));

                var resolution = provider.resolveHolder(sectorMock, grouping, null);

                // The claim resolve is the whole holder map, and nothing draws hatched or unfilled -
                // every claim paints solid, so the fill split takes its whole-region-solid fast path.
                assertThat(resolution.ownerBySystemId())
                        .containsExactly(Map.entry("claimed", claimHolder));
                assertThat(resolution.contestedSystemIds()).isEmpty();
                assertThat(resolution.unfilledSystemIds()).isEmpty();
            }
        }

        @Test
        void resolveHolderIgnoresTheSelectedBlocSinceTheViewOffersNoSpotlight() {
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = HolderGrouping.identity();
            var claimHolder = new DominantHolder("tritachyon", PRIMARY, SECONDARY);
            var provider = new ClaimsHolderProvider(claimReaderMock);
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {
                sectorClaimsMock.when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        sectorMock, grouping, claimReaderMock))
                        .thenReturn(Map.of("claimed", claimHolder));

                // A non-null selected bloc must change nothing: the claims view has no spotlight, so
                // the resolution is identical to the off-filter one - the claim keeps its plain holder
                // and no system recedes or hatches.
                var resolution = provider.resolveHolder(sectorMock, grouping, "hegemony");

                assertThat(resolution.ownerBySystemId())
                        .containsExactly(Map.entry("claimed", claimHolder));
                assertThat(resolution.contestedSystemIds()).isEmpty();
                assertThat(resolution.unfilledSystemIds()).isEmpty();
            }
        }
    }
}
