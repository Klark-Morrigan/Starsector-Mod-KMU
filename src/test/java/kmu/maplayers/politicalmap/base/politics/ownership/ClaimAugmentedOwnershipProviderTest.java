package kmu.maplayers.politicalmap.base.politics.ownership;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.ClaimReader;

import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.SectorClaims;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the claim-augmenting provider's one job: taking the held resolution its base provider
 * returns and folding each claimed-but-unheld system in as an unfilled part of its bloc. The
 * base provider, the claim resolve, and the spotlight owner are all stubbed - each is covered in
 * its own suite - so these tests isolate the fold: which claims join, which are dropped as
 * redundant, and how a claim is keyed under a spotlight so it shares its bloc's fate.
 */
final class ClaimAugmentedOwnershipProviderTest {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    @Nested
    class ResolveOwnership {

        @Test
        void resolveOwnershipFoldsClaimedUnheldSystemsInAsUnfilledOffFilter() {
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(OwnershipProvider.class);
            var grouping = OwnershipGrouping.identity();
            var heldOwner = new DominantOwner("hegemony", PRIMARY, SECONDARY);
            var claimedOwner = new DominantOwner("tritachyon", PRIMARY, SECONDARY);
            when(baseProviderMock.resolveOwnership(sectorMock, grouping, null)).thenReturn(
                    new OwnershipResolution(Map.of("held", heldOwner), Set.of(), Set.of()));
            var provider = new ClaimAugmentedOwnershipProvider(baseProviderMock, claimReaderMock);
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {
                sectorClaimsMock.when(() -> SectorClaims.resolveClaimingOwnerBySystemId(
                        sectorMock, grouping, claimReaderMock))
                        .thenReturn(Map.of("claimed", claimedOwner));

                var resolution = provider.resolveOwnership(sectorMock, grouping, null);

                // The held system keeps its solid owner; the claimed system joins the owner map
                // under its own claimant colours and is the only unfilled entry.
                assertThat(resolution.ownerBySystemId()).containsOnly(
                        Map.entry("held", heldOwner), Map.entry("claimed", claimedOwner));
                assertThat(resolution.unfilledSystemIds()).containsExactly("claimed");
                assertThat(resolution.contestedSystemIds()).isEmpty();
            }
        }

        @Test
        void resolveOwnershipKeepsAHeldSystemsSolidOwnerWhenItIsAlsoClaimed() {
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(OwnershipProvider.class);
            var grouping = OwnershipGrouping.identity();
            var heldOwner = new DominantOwner("hegemony", PRIMARY, SECONDARY);
            var claimOverHeld = new DominantOwner("tritachyon", PRIMARY, SECONDARY);
            when(baseProviderMock.resolveOwnership(sectorMock, grouping, null)).thenReturn(
                    new OwnershipResolution(Map.of("shared", heldOwner), Set.of(), Set.of()));
            var provider = new ClaimAugmentedOwnershipProvider(baseProviderMock, claimReaderMock);
            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {
                sectorClaimsMock.when(() -> SectorClaims.resolveClaimingOwnerBySystemId(
                        sectorMock, grouping, claimReaderMock))
                        .thenReturn(Map.of("shared", claimOverHeld));

                var resolution = provider.resolveOwnership(sectorMock, grouping, null);

                // The stronger held signal wins: the system keeps its held owner and draws solid,
                // so the claim over it adds no unfilled entry.
                assertThat(resolution.ownerBySystemId())
                        .containsExactly(Map.entry("shared", heldOwner));
                assertThat(resolution.unfilledSystemIds()).isEmpty();
            }
        }

        @Test
        void resolveOwnershipRekeysTheSpotlitBlocsOwnClaimOntoItsSpotlightOwnerUnderFilter() {
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(OwnershipProvider.class);
            var grouping = OwnershipGrouping.identity();
            var spotlightOwner = new DominantOwner("$spotlit", PRIMARY, SECONDARY);
            var plainClaimOwner = new DominantOwner("hegemony", PRIMARY, SECONDARY);
            // The spotlit bloc holds a contested (hatched) system as well, so this pins that adding
            // its claim leaves that existing fill split untouched.
            when(baseProviderMock.resolveOwnership(sectorMock, grouping, "hegemony")).thenReturn(
                    new OwnershipResolution(
                            Map.of("held", spotlightOwner), Set.of("held"), Set.of()));
            var provider = new ClaimAugmentedOwnershipProvider(baseProviderMock, claimReaderMock);
            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {
                sectorClaimsMock.when(() -> SectorClaims.resolveClaimingOwnerBySystemId(
                        sectorMock, grouping, claimReaderMock))
                        .thenReturn(Map.of("claimed", plainClaimOwner));
                filteredPoliticsMock.when(() -> FilteredPolitics
                        .resolveSpotlitOwner(sectorMock, grouping, "hegemony"))
                        .thenReturn(spotlightOwner);

                var resolution = provider.resolveOwnership(sectorMock, grouping, "hegemony");

                // The spotlit faction's own claim carries the spotlight owner, so it fuses into the
                // spotlight territory at full strength - drawn unfilled - rather than its plain
                // bloc colour, which the style layer would recede.
                assertThat(resolution.ownerBySystemId().get("claimed")).isSameAs(spotlightOwner);
                assertThat(resolution.unfilledSystemIds()).containsExactly("claimed");
                // The base's contested set passes through the fold untouched.
                assertThat(resolution.contestedSystemIds()).containsExactly("held");
            }
        }

        @Test
        void resolveOwnershipKeepsARivalBlocsClaimUnderItsOwnKeyUnderFilter() {
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(OwnershipProvider.class);
            var grouping = OwnershipGrouping.identity();
            var spotlightOwner = new DominantOwner("$spotlit", PRIMARY, SECONDARY);
            var rivalClaimOwner = new DominantOwner("tritachyon", PRIMARY, SECONDARY);
            when(baseProviderMock.resolveOwnership(sectorMock, grouping, "hegemony")).thenReturn(
                    new OwnershipResolution(Map.of("held", spotlightOwner), Set.of(), Set.of()));
            var provider = new ClaimAugmentedOwnershipProvider(baseProviderMock, claimReaderMock);
            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {
                sectorClaimsMock.when(() -> SectorClaims.resolveClaimingOwnerBySystemId(
                        sectorMock, grouping, claimReaderMock))
                        .thenReturn(Map.of("rival-claimed", rivalClaimOwner));

                var resolution = provider.resolveOwnership(sectorMock, grouping, "hegemony");

                // A bloc other than the spotlighted one keeps its plain bloc owner, so the style
                // layer recedes its claim into the muted background just as it recedes that bloc's
                // held ground - the claim shares its bloc's fate.
                assertThat(resolution.ownerBySystemId().get("rival-claimed"))
                        .isSameAs(rivalClaimOwner);
                assertThat(resolution.unfilledSystemIds()).containsExactly("rival-claimed");
            }
        }
    }
}
