package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.HolderResolution;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.TRITACHYON_FACTION_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildDarkTheme;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorWithSystems;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.findSystemIn;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.listSystemMarkets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the faction and alliance views' holding as a rebuild builds it: the claim-augmented rule
 * collected over every system of the sector, with the claim read, the spotlight and the claim fold
 * all real. Only the contest is stood in for at its static seam, since it reads the player's
 * weighting rule, and has its own suite.
 *
 * <p>Posed whole because a rebuild and a batch now ask one rule, so what a rebuild draws and what it
 * reads are this rule's answers over every system, which no single-system case shows.
 */
final class ClaimAugmentedSystemHolderResolverIntegrationTests {

    private static final SystemOwner HEGEMONY = new SystemOwner(
        "hegemony",
        new ColourPair(Color.RED, Color.BLUE));

    private final ClaimReader claimReaderMock = mock(ClaimReader.class);
    private final ClaimReaderSource claimReaderSource = (knownColonyReader, sectorIndex) -> claimReaderMock;

    private final SectorAPI sector = buildSectorWithSystems(
        List.of(buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT)),
        listSystemMarkets("held"),
        listSystemMarkets("claimed"),
        listSystemMarkets("empty"));

    private final HolderPass pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
    private final StarSystemAPI heldSystem = findSystemIn(sector, "held");
    private final StarSystemAPI claimedSystem = findSystemIn(sector, "claimed");

    private MockedStatic<DominanceSystemHolderResolver> contestMock;

    // The contest holds one system for the Hegemony and leaves the other two unheld; Tri-Tachyon
    // claims the held system and one unheld one.
    @BeforeEach
    void stateTheSector() {

        SystemHolderResolver heldResolver = system -> system == heldSystem
            ? ResolvedSystemOwner.createSolidlyFilledBy(HEGEMONY)
            : ResolvedSystemOwner.UNOWNED;

        contestMock = mockStatic(DominanceSystemHolderResolver.class);
        contestMock
            .when(() -> DominanceSystemHolderResolver.openResolverUnder(same(pass), eq(Optional.empty())))
            .thenReturn(heldResolver);

        when(claimReaderMock.readClaimingFactionId(heldSystem))
            .thenReturn("tritachyon");
        when(claimReaderMock.readClaimingFactionId(claimedSystem))
            .thenReturn("tritachyon");
    }

    @AfterEach
    void closeTheSeams() {
        contestMock.close();
    }

    @Nested
    class CollectOver {

        // The held system keeps its holder and fill, and the claimed-but-unheld one joins its
        // claimant unfilled; the system nobody holds or claims stays nobody's.
        @Test
        void foldsAClaimedUnheldSystemInUnfilledBesideTheHeldOne() {

            // ARRANGE
            var expectedOwnerBySystemKey = Map.of(
                buildCellKey("held"),
                HEGEMONY,
                buildCellKey("claimed"),
                new SystemOwner(
                    "tritachyon",
                    new ColourPair(TRITACHYON_FACTION_BRIGHT, buildDarkTheme(TRITACHYON_FACTION_BRIGHT))));
            var expectedFillStateBySystemKey = Map.of(
                buildCellKey("claimed"),
                FillState.UNFILLED);

            // ACT
            var actualResolution = collectTheHolding();

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .isEqualTo(expectedOwnerBySystemKey);
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .isEqualTo(expectedFillStateBySystemKey);
        }

        // The held signal is the stronger one, so a rebuild reads no claim for a held system: of the
        // three systems the sector lists, only the two the contest left unheld are asked.
        @Test
        void readsNoClaimForAHeldSystem() {

            // ARRANGE
            var expectedClaimReads = 2;

            // ACT
            collectTheHolding();

            // ASSERT
            verify(claimReaderMock, times(expectedClaimReads))
                .readClaimingFactionId(any());
            verify(claimReaderMock, never())
                .readClaimingFactionId(heldSystem);
        }
    }

    // The holding a rebuild over this sector draws with nobody spotlit: the views' rule, opened over
    // the pass and asked of every system it walks.
    private HolderResolution collectTheHolding() {

        var resolver = ClaimAugmentedSystemHolderResolver
            .createSourceReadingThrough(claimReaderSource)
            .openResolverOver(pass, null);

        return HolderResolution.collectOver(pass, resolver);
    }
}
