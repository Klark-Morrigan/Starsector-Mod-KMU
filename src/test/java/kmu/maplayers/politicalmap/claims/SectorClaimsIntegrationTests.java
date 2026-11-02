package kmu.maplayers.politicalmap.claims;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.testfixtures.starsector.systems.claims.ClaimReaderFake;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.HolderResolution;
import kmu.maplayers.ownermap.owners.holders.base.SectorBlocPalettes;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.HEGEMONY_FACTION_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildDarkTheme;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildHolderPassOver;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorWithSystems;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.listSystemMarkets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wires the claim resolve against a stubbed sector and a {@link ClaimReaderFake}: the claimant
 * comes from the faked port, the palette from a mocked {@code FactionAPI}, so this exercises
 * {@link SectorClaims} together with {@link SectorBlocPalettes#resolveOwnerOf} - the shared bloc
 * colouring a claim reuses - end to end, one system at a time and collected over a sector as a
 * rebuild collects it. The systems carry no markets, since a claim is read from the port, not the
 * economy, so a marketless (unpopulated or decivilised) system is exactly the case a claim resolves
 * for.
 */
final class SectorClaimsIntegrationTests {

    @Nested
    class ResolveClaimingHolderIn {

        @Test
        void returnsNullForNoSystem() {

            // ARRANGE
            var pass = buildHolderPassOver(null);

            // ACT
            var actualHolder = SectorClaims.resolveClaimingHolderIn(null, pass, new ClaimReaderFake());

            // ASSERT
            assertThat(actualHolder)
                .isNull();
        }

        @Test
        void returnsNullForAnUnclaimedSystem() {

            // ARRANGE
            var pass = buildHolderPassOver(buildSectorWithSystems(
                List.of(buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT)),
                listSystemMarkets("unclaimed")));

            var system = pass
                .readSystems()
                .get(0);

            // ACT
            var actualHolder = SectorClaims.resolveClaimingHolderIn(system, pass, new ClaimReaderFake());

            // ASSERT
            assertThat(actualHolder)
                .isNull();
        }

        @Test
        void collectsNothingWhenThePassHasNoSector() {

            // ARRANGE
            var pass = buildHolderPassOver(null);

            // ACT
            var actualHolders = collectClaimingHolders(pass, new ClaimReaderFake());

            // ASSERT
            assertThat(actualHolders)
                .isEmpty();
        }

        // Under identity the claimant's bloc is itself, so the claimed system resolves to the
        // claiming faction's own key and authored shades - the same holder a held system of that
        // faction would carry, so the two fuse into one territory downstream.
        @Test
        void coloursAClaimedSystemInItsClaimantsPalette() {

            // ARRANGE
            var sectorMock = buildSectorWithSystems(
                List.of(buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT)),
                listSystemMarkets("claimed"));
            var claimReaderFake = new ClaimReaderFake();
            var expectedHolder = new SystemOwner(
                "hegemony",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            claimReaderFake.setClaim("claimed", "hegemony");

            // ACT
            var actualHolders = collectClaimingHolders(buildHolderPassOver(sectorMock), claimReaderFake);

            // ASSERT
            assertThat(actualHolders)
                .containsExactly(Map.entry(buildCellKey("claimed"), expectedHolder));
        }

        // The alliances grouping folds the claiming faction into its alliance bloc, and the bloc
        // paints in its colour faction's palette - so an allied claimant's claim lands under the
        // alliance key and colour with no claim-specific rollup of its own.
        @Test
        void rollsAnAlliedClaimantIntoItsAllianceBloc() {

            // ARRANGE
            var grouping = new HolderGrouping(
                Map.of("hegemony", "alliance-1"),
                Map.of("alliance-1", "hegemony"),
                Map.of("alliance-1", "Allied Powers"));
            var sectorMock = buildSectorWithSystems(
                List.of(buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT)),
                listSystemMarkets("claimed"));
            var claimReaderFake = new ClaimReaderFake();
            var expectedHolder = new SystemOwner(
                "alliance-1",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            claimReaderFake.setClaim("claimed", "hegemony");

            // ACT
            var actualHolders = collectClaimingHolders(
                HolderPass.createOver(sectorMock, UNDER_THE_FOG, grouping),
                claimReaderFake);

            // ASSERT
            assertThat(actualHolders)
                .containsExactly(Map.entry(buildCellKey("claimed"), expectedHolder));
        }

        // A claimant the sector cannot resolve to a faction (its palette gone) yields a null holder,
        // which is dropped rather than painting a colourless cluster - mirroring how an unresolved
        // held holder drops its system.
        @Test
        void dropsAClaimWhoseColourFactionDoesNotResolve() {

            // ARRANGE
            var sectorMock = buildSectorWithSystems(List.of(), listSystemMarkets("claimed"));
            var claimReaderFake = new ClaimReaderFake();

            claimReaderFake.setClaim("claimed", "ghost-faction");

            // ACT
            var actualHolders = collectClaimingHolders(buildHolderPassOver(sectorMock), claimReaderFake);

            // ASSERT
            assertThat(actualHolders)
                .isEmpty();
        }
    }

    // Every system's claim as a rebuild collects it: the claim read asked of each system the pass walks.
    private static Map<SystemKey, SystemOwner> collectClaimingHolders(HolderPass pass, ClaimReader claimReader) {

        return HolderResolution
            .collectOver(
                pass,
                system -> ResolvedSystemOwner.createSolidlyFilledBy(
                    SectorClaims.resolveClaimingHolderIn(system, pass, claimReader)))
            .ownerBySystemKey();
    }
}
