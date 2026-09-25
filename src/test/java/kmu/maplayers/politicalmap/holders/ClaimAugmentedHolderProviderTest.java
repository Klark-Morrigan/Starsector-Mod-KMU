package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.markets.colonies.KnownColonyReader;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.colonies.RevelationGate;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.HolderProvider;
import kmu.maplayers.ownermap.owners.holders.HolderResolution;
import kmu.maplayers.politicalmap.claims.SectorClaims;
import kmu.maplayers.politicalmap.dominance.FilteredPolitics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.holding.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.holding.ColonyReadRulesFixtures.buildRulesUnder;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
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

    // A rule that is plainly not the fog alone, so a reader opened under a default of its own
    // fails the pass-through case rather than passing it by coincidence.
    private static final ColonyVisibility GATED_VISIBILITY = new ColonyVisibility(
        false,
        DecivilisedMarkets.DEFAULT_SURVEY_LEVEL,
        Set.of(RevelationGate.SPACE_DERELICTS, RevelationGate.HIDDEN_COLONIES));

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
            var pass = HolderPass.over(sectorMock, UNDER_THE_FOG, grouping);
            var heldHolder = new SystemOwner("hegemony", PRIMARY, SECONDARY);
            var claimedHolder = new SystemOwner("tritachyon", PRIMARY, SECONDARY);

            when(baseProviderMock.resolveHolder(pass, null))
                .thenReturn(
                    new HolderResolution(Map.of(buildCellKey("held"), heldHolder), Set.of(), Set.of()));

            var provider = new ClaimAugmentedHolderProvider(
                baseProviderMock,
                (visibility, colonies) -> claimReaderMock);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(
                        pass,
                        claimReaderMock))
                    .thenReturn(Map.of(buildCellKey("claimed"), claimedHolder));

                var resolution = provider.resolveHolder(pass, null);

                // The held system keeps its solid holder; the claimed system joins the holder map
                // under its own claimant colours and is the only unfilled entry.
                assertThat(resolution.ownerBySystemKey())
                    .containsOnly(
                        Map.entry(buildCellKey("held"), heldHolder),
                        Map.entry(buildCellKey("claimed"), claimedHolder));

                assertThat(resolution.unfilledSystemKeys())
                    .containsExactly(buildCellKey("claimed"));
                assertThat(resolution.contestedSystemKeys())
                    .isEmpty();
            }
        }

        @Test
        void resolveHolderKeepsAHeldSystemsSolidHolderWhenItIsAlsoClaimed() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(HolderProvider.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.over(sectorMock, UNDER_THE_FOG, grouping);
            var heldHolder = new SystemOwner("hegemony", PRIMARY, SECONDARY);
            var claimOverHeld = new SystemOwner("tritachyon", PRIMARY, SECONDARY);

            when(baseProviderMock.resolveHolder(pass, null))
                .thenReturn(
                    new HolderResolution(Map.of(buildCellKey("shared"), heldHolder), Set.of(), Set.of()));

            var provider = new ClaimAugmentedHolderProvider(
                baseProviderMock,
                (visibility, colonies) -> claimReaderMock);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(
                        pass,
                        claimReaderMock))
                    .thenReturn(Map.of(buildCellKey("shared"), claimOverHeld));

                var resolution = provider.resolveHolder(pass, null);

                // The stronger held signal wins: the system keeps its held holder and draws solid,
                // so the claim over it adds no unfilled entry.
                assertThat(resolution.ownerBySystemKey())
                    .containsExactly(Map.entry(buildCellKey("shared"), heldHolder));
                assertThat(resolution.unfilledSystemKeys())
                    .isEmpty();
            }
        }

        @Test
        void resolveHolderRekeysTheSpotlitBlocsOwnClaimOntoItsSpotlightHolderUnderFilter() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(HolderProvider.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.over(sectorMock, UNDER_THE_FOG, grouping);
            var spotlightHolder = new SystemOwner("$spotlit", PRIMARY, SECONDARY);
            var plainClaimHolder = new SystemOwner("hegemony", PRIMARY, SECONDARY);

            // The spotlit bloc holds a contested (hatched) system as well, so this pins that adding
            // its claim leaves that existing fill split untouched.
            when(baseProviderMock.resolveHolder(pass, "hegemony"))
                .thenReturn(
                    new HolderResolution(
                        Map.of(buildCellKey("held"), spotlightHolder),
                        Set.of(buildCellKey("held")),
                        Set.of()));

            var provider = new ClaimAugmentedHolderProvider(
                baseProviderMock,
                (visibility, colonies) -> claimReaderMock);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(
                        pass,
                        claimReaderMock))
                    .thenReturn(Map.of(buildCellKey("claimed"), plainClaimHolder));

                filteredPoliticsMock
                    .when(() -> FilteredPolitics
                        .resolveSpotlitHolder(sectorMock, grouping, "hegemony"))
                    .thenReturn(spotlightHolder);

                var resolution = provider.resolveHolder(pass, "hegemony");

                // The spotlit faction's own claim carries the spotlight holder, so it fuses into the
                // spotlight territory at full strength - drawn unfilled - rather than its plain
                // bloc colour, which the style layer would recede.
                assertThat(resolution.ownerBySystemKey().get(buildCellKey("claimed")))
                    .isSameAs(spotlightHolder);
                assertThat(resolution.unfilledSystemKeys())
                    .containsExactly(buildCellKey("claimed"));

                // The base's contested set passes through the fold untouched.
                assertThat(resolution.contestedSystemKeys())
                    .containsExactly(buildCellKey("held"));
            }
        }

        @Test
        void resolveHolderKeepsARivalBlocsClaimUnderItsOwnKeyUnderFilter() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(HolderProvider.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.over(sectorMock, UNDER_THE_FOG, grouping);
            var spotlightHolder = new SystemOwner("$spotlit", PRIMARY, SECONDARY);
            var rivalClaimHolder = new SystemOwner("tritachyon", PRIMARY, SECONDARY);

            when(baseProviderMock.resolveHolder(pass, "hegemony"))
                .thenReturn(
                    new HolderResolution(Map.of(buildCellKey("held"), spotlightHolder), Set.of(), Set.of()));

            var provider = new ClaimAugmentedHolderProvider(
                baseProviderMock,
                (visibility, colonies) -> claimReaderMock);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(
                        pass,
                        claimReaderMock))
                    .thenReturn(Map.of(buildCellKey("rival-claimed"), rivalClaimHolder));

                var resolution = provider.resolveHolder(pass, "hegemony");

                // A bloc other than the spotlighted one keeps its plain bloc holder, so the style
                // layer recedes its claim into the muted background just as it recedes that bloc's
                // held cells - the claim shares its bloc's fate.
                assertThat(resolution.ownerBySystemKey().get(buildCellKey("rival-claimed")))
                    .isSameAs(rivalClaimHolder);
                assertThat(resolution.unfilledSystemKeys())
                    .containsExactly(buildCellKey("rival-claimed"));
            }
        }

        @Test
        void resolveHolderOpensItsClaimReaderOverTheSameWalkTheHeldHalfRead() {
            // The two halves each read every system, so a claim reader with a walk of its own
            // would traverse the sector a second time for colonies the held half has just been
            // handed. Opening it over the pass is what makes one rebuild cost one walk.
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(HolderProvider.class);
            var pass = HolderPass.over(sectorMock, UNDER_THE_FOG, HolderGrouping.identity());
            var openedOver = new ArrayList<SectorPassIndex>();

            when(baseProviderMock.resolveHolder(pass, null))
                .thenReturn(new HolderResolution(Map.of(), Set.of(), Set.of()));

            var provider = new ClaimAugmentedHolderProvider(
                baseProviderMock,
                (visibility, colonies) -> {
                    openedOver.add(colonies);
                    return claimReaderMock;
                });

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(pass, claimReaderMock))
                    .thenReturn(Map.of());

                provider.resolveHolder(pass, null);

                assertThat(openedOver)
                    .containsExactly(pass.sectorIndex());
            }
        }

        @Test
        void resolveHolderOpensItsClaimReaderUnderThePassesOwnVisibilityRule() {
            // The claim half has to be shown the sector the held half was. A reader opened under
            // a rule of its own would fold in claims resolved against colonies the held fills
            // beside them were never given.
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var baseProviderMock = mock(HolderProvider.class);
            var pass = HolderPass.over(sectorMock, buildRulesUnder(GATED_VISIBILITY), HolderGrouping.identity());
            var openedUnder = new ArrayList<KnownColonyReader>();

            when(baseProviderMock.resolveHolder(pass, null))
                .thenReturn(new HolderResolution(Map.of(), Set.of(), Set.of()));

            var provider = new ClaimAugmentedHolderProvider(
                baseProviderMock,
                (knownColonyReader, colonies) -> {
                    openedUnder.add(knownColonyReader);
                    return claimReaderMock;
                });

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(pass, claimReaderMock))
                    .thenReturn(Map.of());

                provider.resolveHolder(pass, null);

                // The port itself is the pass's own knowledge, so what it was opened under is
                // read back off the rule that knowledge carries - stated against the literal the
                // pass was built with rather than against the pass a second time.
                assertThat(openedUnder)
                    .singleElement(as(type(ColonyKnowledge.class)))
                    .extracting(ColonyKnowledge::rule)
                    .isEqualTo(GATED_VISIBILITY);
            }
        }
    }
}
