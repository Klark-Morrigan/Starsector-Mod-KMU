package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.markets.colonies.KnownColonyReader;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.ClaimBreakdownReaderSource;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;
import kmlib.starsector.ui.widgets.lists.ListSortMode;

import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.RibbonPlanInputs;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolve;
import kmu.maplayers.ownermap.picker.OwnerPresenceIndex;
import kmu.maplayers.ownermap.picker.OwnerStatsRead;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.DominanceStats;
import kmu.maplayers.politicalmap.dominance.DominanceStatsAggregator;
import kmu.maplayers.politicalmap.dominance.ribbon.HeldOrClaimedSystemRibbonPlanner;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.holders.ClaimAugmentedHolderProvider;
import kmu.maplayers.politicalmap.holders.DominanceSystemHolderResolve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Predicate;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.STANDARD_RULES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what every view the domination contest paints answers alike: the claim-extended holding,
 * the per-system contest, the band counting by the mechanic that painted each cell, and a picker of
 * the contest's own numbers under a weighting rule.
 *
 * <p>Read off the interface's own defaults rather than off a concrete view, so each is pinned once.
 * A view's own suite then pins only what that view decides.
 */
final class DominancePaintedViewTests {

    // A source standing in for the view's, so a case can tell it apart from the vanilla binding.
    private final ClaimBreakdownReaderSource claimReaderSource =
        (knownColonyReader, sectorIndex) -> mock(ClaimBreakdownReader.class);

    // The interface with every default answering for real and nothing else stated, so a case reads
    // the default alone.
    private final DominancePaintedView viewMock = mock(DominancePaintedView.class, CALLS_REAL_METHODS);

    @Nested
    class ResolveClaimReaderSource {

        @Test
        void opensTheVanillaClaimReader() {

            assertThat(viewMock
                    .resolveClaimReaderSource()
                    .openReaderOver(KnownColonyReader.NOTHING_KNOWN, new SectorPassIndex(null)))
                .isInstanceOf(VanillaClaimBreakdownReader.class);
        }
    }

    @Nested
    class ResolveHolderProvider {

        @Test
        void extendsHeldDominanceWithTheClaimsTheViewReads() {
            // Each system's dominant holder, extended with the systems a bloc merely claims - the
            // holding both contest-painted views resolve alike, read through the view's own source.
            var providerMock = mock(ClaimAugmentedHolderProvider.class);

            doReturn(claimReaderSource)
                .when(viewMock).resolveClaimReaderSource();

            try (var providerFactoryMock = mockStatic(ClaimAugmentedHolderProvider.class)) {

                providerFactoryMock
                    .when(() -> ClaimAugmentedHolderProvider.createReadingThrough(same(claimReaderSource)))
                    .thenReturn(providerMock);

                assertThat(viewMock.resolveHolderProvider())
                    .isSameAs(providerMock);
            }
        }
    }

    @Nested
    class ResolveSystemHolderResolveSource {

        @Test
        void reDerivesAMarkedSystemByTheMarketContest() {
            // The per-system half of the contest, so a refreshed cell lands the bloc the whole-sector
            // resolve would - opened over the batch's own pass.
            var holding = new HolderPass(
                new SectorPassIndex(null),
                UNDER_THE_FOG,
                HolderGrouping.identity());

            var resolveMock = mock(SystemHolderResolve.class);

            try (var resolveFactoryMock = mockStatic(DominanceSystemHolderResolve.class)) {

                resolveFactoryMock
                    .when(() -> DominanceSystemHolderResolve.openResolveOver(holding))
                    .thenReturn(resolveMock);

                assertThat(viewMock.resolveSystemHolderResolveSource().openResolveOver(holding))
                    .isSameAs(resolveMock);
            }
        }
    }

    @Nested
    class ResolveRibbonPlanner {

        @Test
        void countsEachSystemByTheMechanicThatPaintedIt() {
            // A contest-painted view paints held territory and extends it with claims, so its bands
            // are counted the same way round rather than one mechanic answering for cells the other
            // painted.
            var inputs = new RibbonPlanInputs(
                new HolderPass(new SectorPassIndex(null), UNDER_THE_FOG, HolderGrouping.identity()),
                OwnerAffiliation.NONE,
                STANDARD_RULES);

            var plannerMock = mock(HeldOrClaimedSystemRibbonPlanner.class);

            doReturn(claimReaderSource)
                .when(viewMock).resolveClaimReaderSource();

            try (var plannerFactoryMock = mockStatic(HeldOrClaimedSystemRibbonPlanner.class)) {

                // Matched on the view's own source, so a claimed cell's band ranks the contest the
                // fills read.
                plannerFactoryMock
                    .when(() -> HeldOrClaimedSystemRibbonPlanner.createForPass(same(inputs), same(claimReaderSource)))
                    .thenReturn(plannerMock);

                assertThat(viewMock.resolveRibbonPlanner(inputs))
                    .isSameAs(plannerMock);
            }
        }
    }

    @Nested
    class ResolveBlocPickerRead {

        // Any stated rule; a case about which rule is handed on compares by identity.
        private static final DominanceRules ANY_RULES_MOCK = mock(DominanceRules.class);

        // Forwarded onto a row verbatim, so any stats stand in.
        private static final DominanceStats ANY_STATS = new DominanceStats(3, 2, 5000, 7);

        @BeforeEach
        void stubTheViewsOwnAnswers() {

            doReturn(HolderGrouping.identity())
                .when(viewMock).resolveGrouping();

            doReturn((Predicate<String>) blocId -> true)
                .when(viewMock)
                .resolveSelectableBlocGate(any());
        }

        @Test
        void carriesTheContestsOwnNumbersOnEachRow() {
            // The rows are the dominance aggregator's: these are the metrics a contest-painted bloc
            // carries, so the picker ranks by the numbers the map paints by.
            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(new OwnerStatsRead<>(Map.of("hegemony", ANY_STATS), OwnerPresenceIndex.EMPTY));

                assertThat(viewMock.resolveBlocPickerRead(mock(SectorAPI.class), ANY_RULES_MOCK, UNDER_THE_FOG)
                        .picker()
                        .items())
                    .extracting(RankedOwner::itemId, RankedOwner::stats)
                    .containsExactly(tuple("hegemony", ANY_STATS));
            }
        }

        @Test
        void ranksItsBlocsByTheDominanceVocabularyThenTheirStanding() {
            // The pairing is fixed rather than a per-view choice: the numbers these blocs carry are
            // the contest's, so the vocabulary is too. The standing ranking behind it is the one fact
            // every view offers.
            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(OwnerStatsRead.createEmptyRead());

                assertThat(viewMock.resolveBlocPickerRead(mock(SectorAPI.class), ANY_RULES_MOCK, UNDER_THE_FOG)
                        .picker()
                        .sortModes()
                        .modes())
                    .extracting(ListSortMode::persistenceKey)
                    .containsExactly(
                        "name", "domination", "presence", "score", "market_size", "player_standing");
            }
        }

        @Test
        void weighsUnderTheStatedRuleAndTheSampledGrouping() {
            // A caller that has already sampled the rule states it, so the pass the numbers come off
            // is opened under that rule rather than a second read of the player's.
            var sectorMock = mock(SectorAPI.class);

            try (var passMock = mockStatic(DominancePass.class);
                    var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(OwnerStatsRead.createEmptyRead());

                viewMock.resolveBlocPickerRead(sectorMock, ANY_RULES_MOCK, UNDER_THE_FOG);

                passMock.verify(() -> DominancePass.createOver(
                    same(sectorMock),
                    same(ANY_RULES_MOCK),
                    same(UNDER_THE_FOG),
                    same(HolderGrouping.identity())));
            }
        }

        @Test
        void weighsUnderThePlayersLiveRuleOnTheLiveEntry() {
            // The entry the shared seam reaches reads the player's rule, the one input that seam does
            // not carry.
            var sectorMock = mock(SectorAPI.class);

            try (var rulesMock = mockStatic(DominanceRules.class);
                    var passMock = mockStatic(DominancePass.class);
                    var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                rulesMock.when(DominanceRules::readFromLunaSettings)
                    .thenReturn(ANY_RULES_MOCK);

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(OwnerStatsRead.createEmptyRead());

                viewMock.resolveBlocPickerRead(sectorMock, UNDER_THE_FOG);

                passMock.verify(() -> DominancePass.createOver(
                    same(sectorMock),
                    same(ANY_RULES_MOCK),
                    same(UNDER_THE_FOG),
                    same(HolderGrouping.identity())));
            }
        }
    }
}
