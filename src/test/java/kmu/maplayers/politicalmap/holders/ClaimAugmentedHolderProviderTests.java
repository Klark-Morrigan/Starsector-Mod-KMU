package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.markets.colonies.KnownColonyReader;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.colonies.RevelationGate;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.BlocSpotlight;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.HolderResolution;
import kmu.maplayers.politicalmap.claims.SectorClaims;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.buildRulesUnder;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the claim-augmenting provider's one job: taking the held resolution its base provider
 * returns and folding each claimed-but-unheld system in as an unfilled part of its bloc. The
 * base provider, the claim read, and the spotlight holder are all stubbed - each is covered in
 * its own suite - so these tests isolate the fold: which claims join, which are dropped as
 * redundant, and how a claim is keyed under a spotlight so it shares its bloc's fate.
 */
final class ClaimAugmentedHolderProviderTests {

    // A rule that is plainly not the fog alone, so a reader opened under a default of its own
    // fails the pass-through case rather than passing it by coincidence.
    private static final ColonyVisibility GATED_VISIBILITY = new ColonyVisibility(
        false,
        DecivilisedMarkets.DEFAULT_SURVEY_LEVEL,
        Set.of(RevelationGate.SPACE_DERELICTS, RevelationGate.HIDDEN_COLONIES));

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    private static final SystemOwner HEGEMONY_HOLDER =
        new SystemOwner("hegemony", new ColourPair(PRIMARY, SECONDARY));
    private static final SystemOwner TRITACHYON_HOLDER =
        new SystemOwner("tritachyon", new ColourPair(PRIMARY, SECONDARY));
    private static final SystemOwner SPOTLIGHT_HOLDER =
        new SystemOwner("$spotlit", new ColourPair(PRIMARY, SECONDARY));

    private static final Optional<BlocSpotlight> HEGEMONY_SPOTLIGHT =
        Optional.of(new BlocSpotlight("hegemony", SPOTLIGHT_HOLDER));

    @Nested
    class CreateReadingThrough {

        // The view hands its one source down, so the fills read claims through the binding its
        // bands read through.
        @Test
        void readsItsClaimsThroughTheSourceItIsHanded() {

            // ARRANGE
            var pass = HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());
            var openedOver = new ArrayList<SectorPassIndex>();

            var provider = ClaimAugmentedHolderProvider.createReadingThrough((knownColonyReader, sectorIndex) -> {
                openedOver.add(sectorIndex);
                return mock(ClaimReader.class);
            });

            try (var sectorPoliticsMock = mockStatic(SectorPolitics.class)) {

                sectorPoliticsMock
                    .when(() -> SectorPolitics.resolveDominantHolderBySystemKey(pass))
                    .thenReturn(Map.of());

                // ACT
                provider.resolveHolder(pass, null);
            }

            // ASSERT
            assertThat(openedOver)
                .containsExactly(pass.sectorIndex());
        }
    }

    @Nested
    class ResolveHolder {

        private final ClaimReader claimReaderMock = mock(ClaimReader.class);
        private final DominanceHolderProvider baseProviderMock = mock(DominanceHolderProvider.class);
        private final HolderPass pass =
            HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());

        private final ClaimAugmentedHolderProvider provider = new ClaimAugmentedHolderProvider(
            baseProviderMock,
            (visibility, colonies) -> claimReaderMock);

        // The held system keeps its solid holder; the claimed system joins the holder map under its
        // own claimant colours and is the only unfilled entry.
        @Test
        void foldsClaimedUnheldSystemsInAsUnfilledOffFilter() {

            // ARRANGE
            stateTheHeld(Optional.empty(), Map.of(buildCellKey("held"), HEGEMONY_HOLDER), SystemFillStates.ALL_SOLID);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateTheClaims(sectorClaimsMock, Map.of(buildCellKey("claimed"), TRITACHYON_HOLDER));

                // ACT
                var actualResolution = provider.resolveHolder(pass, null);

                // ASSERT
                assertThat(actualResolution.ownerBySystemKey())
                    .containsOnly(
                        Map.entry(buildCellKey("held"), HEGEMONY_HOLDER),
                        Map.entry(buildCellKey("claimed"), TRITACHYON_HOLDER));

                assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                    .containsExactly(Map.entry(buildCellKey("claimed"), FillState.UNFILLED));
            }
        }

        // The stronger held signal wins: the system keeps its held holder and draws solid, so the
        // claim over it adds no unfilled entry.
        @Test
        void keepsAHeldSystemsSolidHolderWhenItIsAlsoClaimed() {

            // ARRANGE
            stateTheHeld(Optional.empty(), Map.of(buildCellKey("shared"), HEGEMONY_HOLDER), SystemFillStates.ALL_SOLID);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateTheClaims(sectorClaimsMock, Map.of(buildCellKey("shared"), TRITACHYON_HOLDER));

                // ACT
                var actualResolution = provider.resolveHolder(pass, null);

                // ASSERT
                assertThat(actualResolution.ownerBySystemKey())
                    .containsExactly(Map.entry(buildCellKey("shared"), HEGEMONY_HOLDER));
                assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                    .isEmpty();
            }
        }

        // The same held signal over a contested system: the claim takes neither the holder nor the
        // hatching from it, so the fill split still draws it hatched inside its holder's border.
        @Test
        void keepsAHeldHatchedSystemsHolderAndHatchingWhenItIsAlsoClaimed() {

            // ARRANGE
            stateTheHeld(
                Optional.empty(),
                Map.of(buildCellKey("shared"), HEGEMONY_HOLDER),
                SystemFillStates.createOf(Map.of(buildCellKey("shared"), FillState.HATCHED)));

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateTheClaims(sectorClaimsMock, Map.of(buildCellKey("shared"), TRITACHYON_HOLDER));

                // ACT
                var actualResolution = provider.resolveHolder(pass, null);

                // ASSERT
                assertThat(actualResolution.ownerBySystemKey())
                    .containsExactly(Map.entry(buildCellKey("shared"), HEGEMONY_HOLDER));
                assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                    .containsExactly(Map.entry(buildCellKey("shared"), FillState.HATCHED));
            }
        }

        // The spotlit faction's own claim carries the spotlight holder, so it fuses into the
        // spotlight territory at full strength - drawn unfilled - rather than in its plain bloc
        // colour, which the style layer would recede. The spotlit bloc holds a contested system as
        // well, so this also pins that adding its claim leaves that existing fill split untouched.
        @Test
        void rekeysTheSpotlitBlocsOwnClaimOntoItsSpotlightHolderUnderFilter() {

            // ARRANGE
            stateTheHeld(
                HEGEMONY_SPOTLIGHT,
                Map.of(buildCellKey("held"), SPOTLIGHT_HOLDER),
                SystemFillStates.createOf(Map.of(buildCellKey("held"), FillState.HATCHED)));

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var spotlightMock = mockStatic(BlocSpotlight.class)) {

                stateTheClaims(sectorClaimsMock, Map.of(buildCellKey("claimed"), HEGEMONY_HOLDER));
                stateTheSpotlight(spotlightMock);

                // ACT
                var actualResolution = provider.resolveHolder(pass, "hegemony");

                // ASSERT
                assertThat(actualResolution.ownerBySystemKey())
                    .containsEntry(buildCellKey("claimed"), SPOTLIGHT_HOLDER);
                assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                    .containsOnly(
                        Map.entry(buildCellKey("claimed"), FillState.UNFILLED),
                        Map.entry(buildCellKey("held"), FillState.HATCHED));
            }
        }

        // A bloc other than the spotlighted one keeps its plain bloc holder, so the style layer
        // recedes its claim into the muted background just as it recedes that bloc's held cells -
        // the claim shares its bloc's fate.
        @Test
        void keepsARivalBlocsClaimUnderItsOwnKeyUnderFilter() {

            // ARRANGE
            stateTheHeld(HEGEMONY_SPOTLIGHT, Map.of(buildCellKey("held"), SPOTLIGHT_HOLDER), SystemFillStates.ALL_SOLID);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var spotlightMock = mockStatic(BlocSpotlight.class)) {

                stateTheClaims(sectorClaimsMock, Map.of(buildCellKey("rival-claimed"), TRITACHYON_HOLDER));
                stateTheSpotlight(spotlightMock);

                // ACT
                var actualResolution = provider.resolveHolder(pass, "hegemony");

                // ASSERT
                assertThat(actualResolution.ownerBySystemKey())
                    .containsEntry(buildCellKey("rival-claimed"), TRITACHYON_HOLDER);
                assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                    .containsEntry(buildCellKey("rival-claimed"), FillState.UNFILLED);
            }
        }

        // The two halves each read every system, so a claim reader with a walk of its own would
        // traverse the sector a second time for colonies the held half has just been handed.
        // Opening it over the pass is what makes one rebuild cost one walk.
        @Test
        void opensItsClaimReaderOverTheSameWalkTheHeldHalfRead() {

            // ARRANGE
            var openedOver = new ArrayList<SectorPassIndex>();
            var recordingProvider = new ClaimAugmentedHolderProvider(
                baseProviderMock,
                (visibility, colonies) -> {
                    openedOver.add(colonies);
                    return claimReaderMock;
                });

            stateTheHeld(Optional.empty(), Map.of(), SystemFillStates.ALL_SOLID);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                stateTheClaims(sectorClaimsMock, Map.of());

                // ACT
                recordingProvider.resolveHolder(pass, null);
            }

            // ASSERT
            assertThat(openedOver)
                .containsExactly(pass.sectorIndex());
        }

        // The claim half has to be shown the sector the held half was. A reader opened under a rule
        // of its own would fold in claims resolved against colonies the held fills beside them were
        // never given.
        @Test
        void opensItsClaimReaderUnderThePassesOwnVisibilityRule() {

            // ARRANGE
            var gatedPass = HolderPass.createOver(
                mock(SectorAPI.class),
                buildRulesUnder(GATED_VISIBILITY),
                HolderGrouping.identity());

            var openedUnder = new ArrayList<KnownColonyReader>();
            var recordingProvider = new ClaimAugmentedHolderProvider(
                baseProviderMock,
                (knownColonyReader, colonies) -> {
                    openedUnder.add(knownColonyReader);
                    return claimReaderMock;
                });

            when(baseProviderMock.resolveHolderUnder(gatedPass, Optional.empty()))
                .thenReturn(new HolderResolution(Map.of(), SystemFillStates.ALL_SOLID));

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(gatedPass, claimReaderMock))
                    .thenReturn(Map.of());

                // ACT
                recordingProvider.resolveHolder(gatedPass, null);
            }

            // ASSERT
            // The port itself is the pass's own knowledge, so what it was opened under is read back
            // off the rule that knowledge carries - stated against the literal the pass was built
            // with rather than against the pass a second time.
            assertThat(openedUnder)
                .singleElement(as(type(ColonyKnowledge.class)))
                .extracting(ColonyKnowledge::rule)
                .isEqualTo(GATED_VISIBILITY);
        }

        // What the base provider answers for this case's pass under the given spotlight.
        private void stateTheHeld(
                Optional<BlocSpotlight> spotlight,
                Map<SystemKey, SystemOwner> ownerBySystemKey,
                SystemFillStates fillStates) {

            when(baseProviderMock.resolveHolderUnder(pass, spotlight))
                .thenReturn(new HolderResolution(ownerBySystemKey, fillStates));
        }

        // The spotlight this case's pass opens for the pick, which both halves are handed.
        private void stateTheSpotlight(MockedStatic<BlocSpotlight> spotlightMock) {

            spotlightMock
                .when(() -> BlocSpotlight.openOver(pass, "hegemony"))
                .thenReturn(HEGEMONY_SPOTLIGHT);
        }

        // What the plain claim read answers for this case's pass, read through this case's reader.
        private void stateTheClaims(
                MockedStatic<SectorClaims> sectorClaimsMock,
                Map<SystemKey, SystemOwner> claimingHolderBySystemKey) {

            sectorClaimsMock
                .when(() -> SectorClaims.resolveClaimingHolderBySystemKey(pass, claimReaderMock))
                .thenReturn(claimingHolderBySystemKey);
        }
    }

    @Nested
    class ExtendHeldByClaim {

        // A held system answers as it is held: the held signal is the stronger one, so a claim over
        // a contested system takes neither its holder nor its hatching.
        @Test
        void keepsAHeldHatchedSystemsHolderAndHatchingWhenItIsAlsoClaimed() {

            // ARRANGE
            var held = new ResolvedSystemOwner(HEGEMONY_HOLDER, FillState.HATCHED);
            var expectedResolved = new ResolvedSystemOwner(HEGEMONY_HOLDER, FillState.HATCHED);

            // ACT
            var actualResolved = ClaimAugmentedHolderProvider.extendHeldByClaim(held, TRITACHYON_HOLDER);

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }

        // Nobody holds it and nobody claims it, so there is nothing to extend it into.
        @Test
        void answersUnownedForAnUnheldUnclaimedSystem() {

            // ARRANGE
            var held = ResolvedSystemOwner.UNOWNED;

            // ACT
            var actualResolved = ClaimAugmentedHolderProvider.extendHeldByClaim(held, null);

            // ASSERT
            assertThat(actualResolved)
                .isSameAs(ResolvedSystemOwner.UNOWNED);
        }

        // An unheld claimed system joins its claimant so the claimant's held and claimed cells fuse
        // into one territory, unfilled so the fill split leaves it empty inside that border.
        @Test
        void joinsAnUnheldClaimedSystemToItsClaimantUnfilled() {

            // ARRANGE
            var held = ResolvedSystemOwner.UNOWNED;
            var expectedResolved = new ResolvedSystemOwner(TRITACHYON_HOLDER, FillState.UNFILLED);

            // ACT
            var actualResolved = ClaimAugmentedHolderProvider.extendHeldByClaim(held, TRITACHYON_HOLDER);

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }
    }
}
