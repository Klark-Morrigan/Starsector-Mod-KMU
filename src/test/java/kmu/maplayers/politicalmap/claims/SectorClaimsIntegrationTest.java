package kmu.maplayers.politicalmap.claims;

import kmlib.testfixtures.starsector.systems.claims.ClaimReaderFake;

import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.holding.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildDarkTheme;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildHolderPassOver;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildSectorWithSystems;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.listSystemMarkets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wires the claim resolve against a stubbed sector and a {@link ClaimReaderFake}: the claimant
 * comes from the faked port, the palette from a mocked {@code FactionAPI}, so this exercises
 * {@link SectorClaims} together with {@link SystemOwner#resolveForBloc} - the shared bloc
 * colouring a claim reuses - end to end. The systems carry no markets, since a claim is read from
 * the port, not the economy, so a marketless (unpopulated or decivilised) system is exactly the
 * case a claim resolves for.
 */
final class SectorClaimsIntegrationTest {

    @Nested
    class ResolveClaimingHolderBySystemKey {

        @Test
        void resolveClaimingHolderBySystemKeyReturnsEmptyWhenThePassHasNoSector() {
            var claimReaderFake = new ClaimReaderFake();

            assertThat(SectorClaims.resolveClaimingHolderBySystemKey(
                    buildHolderPassOver(null), claimReaderFake)).isEmpty();
        }

        @Test
        void resolveClaimingHolderBySystemKeyOmitsSystemsWithNoClaim() {
            // A system the port reports no claimant for is absent from the map, exactly as an
            // uninhabited system is absent from the held-dominance pass.
            var sectorMock = buildSectorWithSystems(
                    List.of(buildFaction("hegemony", HEGEMONY_BRIGHT)), listSystemMarkets("unclaimed"));
            var claimReaderFake = new ClaimReaderFake();

            assertThat(SectorClaims.resolveClaimingHolderBySystemKey(
                    buildHolderPassOver(sectorMock), claimReaderFake)).isEmpty();
        }

        @Test
        void resolveClaimingHolderBySystemKeyColoursAClaimedSystemInItsClaimantsPalette() {
            // Under identity the claimant's bloc is itself, so the claimed system resolves to the
            // claiming faction's own key and authored shades - the same holder a held system of that
            // faction would carry, so the two fuse into one territory downstream.
            var sectorMock = buildSectorWithSystems(
                    List.of(buildFaction("hegemony", HEGEMONY_BRIGHT)), listSystemMarkets("claimed"));
            var claimReaderFake = new ClaimReaderFake();
            claimReaderFake.setClaim("claimed", "hegemony");

            assertThat(SectorClaims.resolveClaimingHolderBySystemKey(
                    buildHolderPassOver(sectorMock), claimReaderFake))
                    .containsExactly(Map.entry(buildCellKey("claimed"),
                            new SystemOwner("hegemony", HEGEMONY_BRIGHT, buildDarkTheme(HEGEMONY_BRIGHT))));
        }

        @Test
        void resolveClaimingHolderBySystemKeyRollsAnAlliedClaimantIntoItsAllianceBloc() {
            // The alliances grouping folds the claiming faction into its alliance bloc, and the
            // bloc paints in its colour faction's palette - so an allied claimant's claim lands
            // under the alliance key and colour with no claim-specific rollup of its own.
            var grouping = new HolderGrouping(
                    Map.of("hegemony", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of("alliance-1", "Allied Powers"));
            var sectorMock = buildSectorWithSystems(
                    List.of(buildFaction("hegemony", HEGEMONY_BRIGHT)), listSystemMarkets("claimed"));
            var claimReaderFake = new ClaimReaderFake();
            claimReaderFake.setClaim("claimed", "hegemony");

            assertThat(SectorClaims.resolveClaimingHolderBySystemKey(
                    HolderPass.over(sectorMock, UNDER_THE_FOG, grouping), claimReaderFake))
                    .containsExactly(Map.entry(buildCellKey("claimed"),
                            new SystemOwner("alliance-1", HEGEMONY_BRIGHT, buildDarkTheme(HEGEMONY_BRIGHT))));
        }

        @Test
        void resolveClaimingHolderBySystemKeyDropsAClaimWhoseColourFactionDoesNotResolve() {
            // A claimant the sector cannot resolve to a faction (its palette gone) yields a null
            // holder, which is dropped rather than painting a colourless cluster - mirroring how an
            // unresolved held holder drops its system.
            var sectorMock = buildSectorWithSystems(List.of(), listSystemMarkets("claimed"));
            var claimReaderFake = new ClaimReaderFake();
            claimReaderFake.setClaim("claimed", "ghost-faction");

            assertThat(SectorClaims.resolveClaimingHolderBySystemKey(
                    buildHolderPassOver(sectorMock), claimReaderFake)).isEmpty();
        }
    }
}
