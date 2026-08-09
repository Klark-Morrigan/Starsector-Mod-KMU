package kmu.maplayers.politicalmap.base.politics.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
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
 * base provider, the claim resolve, and the spotlight holder are all stubbed - each is covered in
 * its own suite - so these tests isolate the fold: which claims join, which are dropped as
 * redundant, and how a claim is keyed under a spotlight so it shares its bloc's fate.
 */
final class ClaimAugmentedHolderProviderTest {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    @Nested
    class ResolveHolder {

        @Test
        void resolveHolderFoldsClaimedUnheldSystemsInAsUnfilledOffFilter() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(HolderProvider.class);
            var grouping = HolderGrouping.identity();
            var heldHolder = new DominantHolder("hegemony", PRIMARY, SECONDARY);
            var claimedHolder = new DominantHolder("tritachyon", PRIMARY, SECONDARY);

            when(baseProviderMock.resolveHolder(sectorMock, grouping, null))
                .thenReturn(
                    new HolderResolution(Map.of("held", heldHolder), Set.of(), Set.of()));

            var provider = new ClaimAugmentedHolderProvider(baseProviderMock, claimReaderMock);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        sectorMock,
                        grouping,
                        claimReaderMock))
                    .thenReturn(Map.of("claimed", claimedHolder));

                var resolution = provider.resolveHolder(sectorMock, grouping, null);

                // The held system keeps its solid holder; the claimed system joins the holder map
                // under its own claimant colours and is the only unfilled entry.
                assertThat(resolution.ownerBySystemId())
                    .containsOnly(
                        Map.entry("held", heldHolder),
                        Map.entry("claimed", claimedHolder));

                assertThat(resolution.unfilledSystemIds())
                    .containsExactly("claimed");
                assertThat(resolution.contestedSystemIds())
                    .isEmpty();
            }
        }

        @Test
        void resolveHolderKeepsAHeldSystemsSolidHolderWhenItIsAlsoClaimed() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(HolderProvider.class);
            var grouping = HolderGrouping.identity();
            var heldHolder = new DominantHolder("hegemony", PRIMARY, SECONDARY);
            var claimOverHeld = new DominantHolder("tritachyon", PRIMARY, SECONDARY);

            when(baseProviderMock.resolveHolder(sectorMock, grouping, null))
                .thenReturn(
                    new HolderResolution(Map.of("shared", heldHolder), Set.of(), Set.of()));
                    
            var provider = new ClaimAugmentedHolderProvider(baseProviderMock, claimReaderMock);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        sectorMock,
                        grouping,
                        claimReaderMock))
                    .thenReturn(Map.of("shared", claimOverHeld));

                var resolution = provider.resolveHolder(sectorMock, grouping, null);

                // The stronger held signal wins: the system keeps its held holder and draws solid,
                // so the claim over it adds no unfilled entry.
                assertThat(resolution.ownerBySystemId())
                    .containsExactly(Map.entry("shared", heldHolder));
                assertThat(resolution.unfilledSystemIds())
                    .isEmpty();
            }
        }

        @Test
        void resolveHolderRekeysTheSpotlitBlocsOwnClaimOntoItsSpotlightHolderUnderFilter() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(HolderProvider.class);
            var grouping = HolderGrouping.identity();
            var spotlightHolder = new DominantHolder("$spotlit", PRIMARY, SECONDARY);
            var plainClaimHolder = new DominantHolder("hegemony", PRIMARY, SECONDARY);

            // The spotlit bloc holds a contested (hatched) system as well, so this pins that adding
            // its claim leaves that existing fill split untouched.
            when(baseProviderMock.resolveHolder(sectorMock, grouping, "hegemony"))
                .thenReturn(
                    new HolderResolution(
                        Map.of("held", spotlightHolder),
                        Set.of("held"),
                        Set.of()));

            var provider = new ClaimAugmentedHolderProvider(baseProviderMock, claimReaderMock);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        sectorMock,
                        grouping,
                        claimReaderMock))
                    .thenReturn(Map.of("claimed", plainClaimHolder));

                filteredPoliticsMock
                    .when(() -> FilteredPolitics
                        .resolveSpotlitHolder(sectorMock, grouping, "hegemony"))
                    .thenReturn(spotlightHolder);

                var resolution = provider.resolveHolder(sectorMock, grouping, "hegemony");

                // The spotlit faction's own claim carries the spotlight holder, so it fuses into the
                // spotlight territory at full strength - drawn unfilled - rather than its plain
                // bloc colour, which the style layer would recede.
                assertThat(resolution.ownerBySystemId().get("claimed"))
                    .isSameAs(spotlightHolder);
                assertThat(resolution.unfilledSystemIds())
                    .containsExactly("claimed");

                // The base's contested set passes through the fold untouched.
                assertThat(resolution.contestedSystemIds())
                    .containsExactly("held");
            }
        }

        @Test
        void resolveHolderKeepsARivalBlocsClaimUnderItsOwnKeyUnderFilter() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(HolderProvider.class);
            var grouping = HolderGrouping.identity();
            var spotlightHolder = new DominantHolder("$spotlit", PRIMARY, SECONDARY);
            var rivalClaimHolder = new DominantHolder("tritachyon", PRIMARY, SECONDARY);

            when(baseProviderMock.resolveHolder(sectorMock, grouping, "hegemony"))
                .thenReturn(
                    new HolderResolution(Map.of("held", spotlightHolder), Set.of(), Set.of()));

            var provider = new ClaimAugmentedHolderProvider(baseProviderMock, claimReaderMock);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        sectorMock,
                        grouping,
                        claimReaderMock))
                    .thenReturn(Map.of("rival-claimed", rivalClaimHolder));

                var resolution = provider.resolveHolder(sectorMock, grouping, "hegemony");

                // A bloc other than the spotlighted one keeps its plain bloc holder, so the style
                // layer recedes its claim into the muted background just as it recedes that bloc's
                // held cells - the claim shares its bloc's fate.
                assertThat(resolution.ownerBySystemId().get("rival-claimed"))
                    .isSameAs(rivalClaimHolder);
                assertThat(resolution.unfilledSystemIds())
                    .containsExactly("rival-claimed");
            }
        }
    }
}
