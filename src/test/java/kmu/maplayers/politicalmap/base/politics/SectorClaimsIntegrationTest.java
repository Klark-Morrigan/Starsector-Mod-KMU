package kmu.maplayers.politicalmap.base.politics;

import kmlib.testfixtures.starsector.systems.claims.ClaimReaderFake;

import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.dark;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.faction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.sectorWithSystems;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.systemMarkets;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wires the claim resolve against a stubbed sector and a {@link ClaimReaderFake}: the claimant
 * comes from the faked port, the palette from a mocked {@code FactionAPI}, so this exercises
 * {@link SectorClaims} together with {@link SectorPolitics#resolveBlocOwner} - the shared bloc
 * colouring a claim reuses - end to end. The systems carry no markets, since a claim is read from
 * the port, not the economy, so a marketless (unpopulated or decivilised) system is exactly the
 * case a claim resolves for.
 */
final class SectorClaimsIntegrationTest {

    @Nested
    class ResolveClaimingOwnerBySystemId {

        @Test
        void resolveClaimingOwnerBySystemIdReturnsEmptyWhenSectorIsNull() {
            var claimReaderFake = new ClaimReaderFake();

            assertThat(SectorClaims.resolveClaimingOwnerBySystemId(
                    null, OwnershipGrouping.identity(), claimReaderFake)).isEmpty();
        }

        @Test
        void resolveClaimingOwnerBySystemIdOmitsSystemsWithNoClaim() {
            // A system the port reports no claimant for is absent from the map, exactly as an
            // uninhabited system is absent from the held-ownership pass.
            var sectorMock = sectorWithSystems(
                    List.of(faction("hegemony", HEGEMONY_BRIGHT)), systemMarkets("unclaimed"));
            var claimReaderFake = new ClaimReaderFake();

            assertThat(SectorClaims.resolveClaimingOwnerBySystemId(
                    sectorMock, OwnershipGrouping.identity(), claimReaderFake)).isEmpty();
        }

        @Test
        void resolveClaimingOwnerBySystemIdColoursAClaimedSystemInItsClaimantsPalette() {
            // Under identity the claimant's bloc is itself, so the claimed system resolves to the
            // claiming faction's own key and authored shades - the same owner a held system of that
            // faction would carry, so the two fuse into one territory downstream.
            var sectorMock = sectorWithSystems(
                    List.of(faction("hegemony", HEGEMONY_BRIGHT)), systemMarkets("claimed"));
            var claimReaderFake = new ClaimReaderFake();
            claimReaderFake.setClaim("claimed", "hegemony");

            assertThat(SectorClaims.resolveClaimingOwnerBySystemId(
                    sectorMock, OwnershipGrouping.identity(), claimReaderFake))
                    .containsExactly(Map.entry("claimed",
                            new DominantOwner("hegemony", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT))));
        }

        @Test
        void resolveClaimingOwnerBySystemIdRollsAnAlliedClaimantIntoItsAllianceBloc() {
            // The alliances grouping folds the claiming faction into its alliance bloc, and the
            // bloc paints in its colour faction's palette - so an allied claimant's claim lands
            // under the alliance key and colour with no claim-specific rollup of its own.
            var grouping = new OwnershipGrouping(
                    Map.of("hegemony", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of("alliance-1", "Allied Powers"));
            var sectorMock = sectorWithSystems(
                    List.of(faction("hegemony", HEGEMONY_BRIGHT)), systemMarkets("claimed"));
            var claimReaderFake = new ClaimReaderFake();
            claimReaderFake.setClaim("claimed", "hegemony");

            assertThat(SectorClaims.resolveClaimingOwnerBySystemId(
                    sectorMock, grouping, claimReaderFake))
                    .containsExactly(Map.entry("claimed",
                            new DominantOwner("alliance-1", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT))));
        }

        @Test
        void resolveClaimingOwnerBySystemIdDropsAClaimWhoseColourFactionDoesNotResolve() {
            // A claimant the sector cannot resolve to a faction (its palette gone) yields a null
            // owner, which is dropped rather than painting a colourless region - mirroring how an
            // unresolved held owner drops its system.
            var sectorMock = sectorWithSystems(List.of(), systemMarkets("claimed"));
            var claimReaderFake = new ClaimReaderFake();
            claimReaderFake.setClaim("claimed", "ghost-faction");

            assertThat(SectorClaims.resolveClaimingOwnerBySystemId(
                    sectorMock, OwnershipGrouping.identity(), claimReaderFake)).isEmpty();
        }
    }
}
