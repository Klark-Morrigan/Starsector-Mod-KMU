package kmu.maplayers.politicalmap.base.politics.ownership;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.ClaimReader;

import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
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
 * ownership, every claimed system solid. The claim resolve is stubbed - it has its own suite - so
 * these tests isolate that this provider adds no fill exceptions of its own and reads no filter.
 */
final class ClaimsOwnershipProviderTest {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    @Nested
    class ResolveOwnership {

        @Test
        void resolveOwnershipPaintsEveryClaimSolidWithNoFillExceptions() {
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = OwnershipGrouping.identity();
            var claimOwner = new DominantOwner("hegemony", PRIMARY, SECONDARY);
            var provider = new ClaimsOwnershipProvider(claimReaderMock);
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {
                sectorClaimsMock.when(() -> SectorClaims.resolveClaimingOwnerBySystemId(
                        sectorMock, grouping, claimReaderMock))
                        .thenReturn(Map.of("claimed", claimOwner));

                var resolution = provider.resolveOwnership(sectorMock, grouping, null);

                // The claim resolve is the whole owner map, and nothing draws hatched or unfilled -
                // every claim paints solid, so the fill split takes its whole-region-solid fast path.
                assertThat(resolution.ownerBySystemId())
                        .containsExactly(Map.entry("claimed", claimOwner));
                assertThat(resolution.contestedSystemIds()).isEmpty();
                assertThat(resolution.unfilledSystemIds()).isEmpty();
            }
        }

        @Test
        void resolveOwnershipIgnoresTheSelectedBlocSinceTheViewOffersNoSpotlight() {
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = OwnershipGrouping.identity();
            var claimOwner = new DominantOwner("tritachyon", PRIMARY, SECONDARY);
            var provider = new ClaimsOwnershipProvider(claimReaderMock);
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {
                sectorClaimsMock.when(() -> SectorClaims.resolveClaimingOwnerBySystemId(
                        sectorMock, grouping, claimReaderMock))
                        .thenReturn(Map.of("claimed", claimOwner));

                // A non-null selected bloc must change nothing: the claims view has no spotlight, so
                // the resolution is identical to the off-filter one - the claim keeps its plain owner
                // and no system recedes or hatches.
                var resolution = provider.resolveOwnership(sectorMock, grouping, "hegemony");

                assertThat(resolution.ownerBySystemId())
                        .containsExactly(Map.entry("claimed", claimOwner));
                assertThat(resolution.contestedSystemIds()).isEmpty();
                assertThat(resolution.unfilledSystemIds()).isEmpty();
            }
        }
    }
}
