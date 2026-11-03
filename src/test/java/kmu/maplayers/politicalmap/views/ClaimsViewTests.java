package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.alliances.AllianceRecord;
import kmlib.starsector.factions.alliances.FactionAlliances;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.markets.colonies.KnownColonyReader;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.ClaimBreakdownReaderSource;
import kmlib.starsector.ui.widgets.lists.ListSortMode;

import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.colonies.RevelationGate;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.RibbonPlanInputs;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolverSource;
import kmu.maplayers.ownermap.picker.OwnerPresenceIndex;
import kmu.maplayers.ownermap.picker.OwnerStatsRead;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.ownermap.picker.SelectableOwner;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.maplayers.politicalmap.claims.ClaimStats;
import kmu.maplayers.politicalmap.claims.ClaimStatsAggregator;
import kmu.maplayers.politicalmap.claims.ribbon.ClaimedSystemRibbonPlanner;
import kmu.maplayers.politicalmap.claims.tooltip.SystemClaimTooltip;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.holders.ClaimSystemHolderResolver;
import kmu.mods.nexerelin.NexerelinAlliances;
import kmu.util.KmuPoliticalMapStringKeys;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.buildRulesUnder;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.STANDARD_RULES;
import static kmu.starsector.SectorFactionFixtures.stubNamedFaction;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Pins what the claims view decides: the factions view's look keyed off the vanilla claim mechanic,
 * so its grouping is identity, its holding is the claim read alone and its bands and hover box are
 * the claim contest's. Its picker reads claim metrics through its own claim reader, recedes a row
 * that claims nothing, and ranks by the claim vocabulary rather than the domination one.
 *
 * <p>What every view of the layer answers alike is {@link PoliticalHolderView}'s, pinned once by its
 * own suite.
 */
final class ClaimsViewTests {

    @Nested
    class GetId {

        // Frozen: renaming it silently resets any save that selected this view to the default.
        @Test
        void isTheSaveStableClaimsId() {

            // ARRANGE
            var expectedId = "claims";

            // ACT
            var actualId = ClaimsView.INSTANCE.getId();

            // ASSERT
            assertThat(actualId)
                .isEqualTo(expectedId);
        }
    }

    @Nested
    class GetSegmentLabelKey {

        @Test
        void isTheClaimsRadioLabel() {

            // ARRANGE
            var expectedKey = KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_CLAIMS;

            // ACT
            var actualKey = ClaimsView.INSTANCE.getSegmentLabelKey();

            // ASSERT
            assertThat(actualKey)
                .isEqualTo(expectedKey);
        }
    }

    @Nested
    class ResolveGrouping {

        // Claims group strictly by claiming faction with no alliance rollup, so the pipeline resolves
        // plain faction holding.
        @Test
        void returnsTheIdentityGrouping() {

            // ARRANGE
            var expectedGrouping = HolderGrouping.identity();

            // ACT
            var actualGrouping = ClaimsView.INSTANCE.resolveGrouping();

            // ASSERT
            assertThat(actualGrouping)
                .isSameAs(expectedGrouping);
        }
    }

    @Nested
    class ResolveSystemHolderResolverSource {

        // Holder is the claim mechanic itself: a rebuild and a batch both resolve what this view
        // paints - the claimant - through the reader every claim of this view is read through,
        // rather than by the market contest. The fills, the bands and the box then cannot name
        // different claimants.
        @Test
        void resolvesByClaimantThroughTheViewsReader() {

            // ARRANGE
            var pass = HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());
            var openedOver = new ArrayList<SectorPassIndex>();

            var view = new ClaimsView((knownColonyReader, sectorIndex) -> {

                openedOver.add(sectorIndex);
                return mock(ClaimBreakdownReader.class);
            });

            // ACT
            var actualResolver = view
                .resolveSystemHolderResolverSource()
                .openResolverOver(pass, null);

            // ASSERT
            assertThat(actualResolver)
                .isInstanceOf(ClaimSystemHolderResolver.class);
            assertThat(openedOver)
                .containsExactly(pass.sectorIndex());
        }

        // The source is stateless past the view's claim reader source, so the view builds it once
        // rather than once per rebuild and batch.
        @Test
        void answersTheOneSourceItBuiltOverTheViewsReader() {

            // ARRANGE
            ClaimBreakdownReaderSource claimReaderSource = (knownColonyReader, sectorIndex) ->
                mock(ClaimBreakdownReader.class);

            var sourceMock = mock(SystemHolderResolverSource.class);

            try (var sourceFactoryMock = mockStatic(ClaimSystemHolderResolver.class)) {

                sourceFactoryMock
                    .when(() -> ClaimSystemHolderResolver.createSourceReadingThrough(same(claimReaderSource)))
                    .thenReturn(sourceMock);

                var view = new ClaimsView(claimReaderSource);

                // ACT
                var actualFirstSource = view.resolveSystemHolderResolverSource();
                var actualSecondSource = view.resolveSystemHolderResolverSource();

                // ASSERT
                assertThat(actualFirstSource)
                    .isSameAs(sourceMock);
                assertThat(actualSecondSource)
                    .isSameAs(sourceMock);

                sourceFactoryMock.verify(
                    () -> ClaimSystemHolderResolver.createSourceReadingThrough(any()),
                    times(1));
            }
        }
    }

    @Nested
    class ResolveRibbonPlanner {

        // Every cell here is painted by the claim mechanic, held systems included, so the band is
        // counted from the contest rather than by the dominance views' composition - which would
        // count a claimed system its claimant does not hold by the markets of whoever does, and open
        // the band on a bloc the cell is not painted for.
        @Test
        void countsEveryCellFromTheClaimContest() {

            // ARRANGE
            var pass = HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());
            var inputs = new RibbonPlanInputs(pass, pass.palettes(), OwnerAffiliation.NONE, STANDARD_RULES);

            var plannerMock = mock(ClaimedSystemRibbonPlanner.class);

            ClaimBreakdownReaderSource claimReaderSource = (knownColonyReader, sectorIndex) ->
                mock(ClaimBreakdownReader.class);

            var view = new ClaimsView(claimReaderSource);

            try (var plannerFactoryMock = mockStatic(ClaimedSystemRibbonPlanner.class)) {

                // Matched on the view's own source, so a planner opened through any other binding
                // answers null and fails the case.
                plannerFactoryMock
                    .when(() -> ClaimedSystemRibbonPlanner.createForPass(same(inputs), same(claimReaderSource)))
                    .thenReturn(plannerMock);

                // ACT
                var actualPlanner = view.resolveRibbonPlanner(inputs);

                // ASSERT
                assertThat(actualPlanner)
                    .isSameAs(plannerMock);
            }
        }
    }

    @Nested
    class ResolveViewReading {

        // Claims fold strictly by claiming faction, so the reading stands on the identity grouping
        // whatever the alliance set says: a member of a live alliance is still named as its own
        // faction.
        @Test
        void namesAnAlliedClaimantAsItsOwnFaction() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var expectedName = "Hegemony";

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");

            try (var alliancesMock = mockStatic(NexerelinAlliances.class)) {

                alliancesMock
                    .when(NexerelinAlliances::resolveGrouping)
                    .thenReturn(HolderGroupingFixture.buildGroupOf("hegemony", "persean"));

                // The same alliance as who stands together in a contest, which the reading's source
                // reads beside the grouping.
                alliancesMock
                    .when(NexerelinAlliances::resolveFactionAlliances)
                    .thenReturn(FactionAlliances.buildFrom(List.of(
                        new AllianceRecord("pact", "Pact", List.of("hegemony", "persean")))));

                // ACT
                var actualName = ClaimsView
                    .INSTANCE
                    .resolveViewReading(sectorMock)
                    .reading()
                    .resolveName("hegemony", OwnerNameFormatChoice.SHORT);

                // ASSERT
                assertThat(actualName)
                    .isEqualTo(expectedName);
            }
        }
    }

    @Nested
    class ResolveHoverTooltip {

        // This layer paints by the claim mechanic, so its hover has to explain that contest: the
        // domination breakdown the other two views inject describes standings this view never
        // painted by, and would read as an account of a border it did not draw.
        @Test
        void isTheClaimBreakdownRatherThanTheDominationOne() {

            // ARRANGE
            var view = ClaimsView.INSTANCE;

            // ACT
            var actualTooltip = view.resolveHoverTooltip();

            // ASSERT
            assertThat(actualTooltip)
                .get()
                .isInstanceOf(SystemClaimTooltip.class);
        }

        // The box states the claim the cell under it was painted by, so it reads through the source
        // the fills do.
        @Test
        void explainsTheClaimsItReadsThroughTheViewsSource() {

            // ARRANGE
            ClaimBreakdownReaderSource claimReaderSource = (knownColonyReader, sectorIndex) ->
                mock(ClaimBreakdownReader.class);

            var tooltipMock = mock(SystemClaimTooltip.class);

            try (var tooltipFactoryMock = mockStatic(SystemClaimTooltip.class)) {

                tooltipFactoryMock
                    .when(() -> SystemClaimTooltip.createReadingThrough(same(claimReaderSource)))
                    .thenReturn(tooltipMock);

                // ACT
                var actualTooltip = new ClaimsView(claimReaderSource).resolveHoverTooltip();

                // ASSERT
                assertThat(actualTooltip)
                    .containsSame(tooltipMock);
            }
        }
    }

    @Nested
    class ResolveBlocPickerRead {

        // A claimant's stats: the view forwards them onto its option verbatim, so these arbitrary
        // numbers are only asserted to survive the pass unchanged. Non-zero claims, so the row they
        // ride on is an ordinary full-strength one.
        private static final ClaimStats ANY_CLAIMANT_STATS = new ClaimStats(2, 7);

        // A rule that is plainly not the fog alone, so a reader opened under a default of its own
        // fails the pass-through case rather than passing it by coincidence.
        private static final ColonyVisibility GATED_VISIBILITY = new ColonyVisibility(
            false,
            DecivilisedMarkets.DEFAULT_SURVEY_LEVEL,
            Set.of(RevelationGate.SPACE_DERELICTS, RevelationGate.HIDDEN_COLONIES));

        // A claiming bloc becomes an option carrying its crest, short name, and the claim stats the
        // fold computed for it, so the option reads exactly as the picker row will draw and sort it.
        @Test
        void carriesEachClaimantsCrestShortNameAndStats() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var expectedRow = new RankedOwner<>(
                new SelectableOwner(
                    "hegemony",
                    "Hegemony",
                    "graphics/hegemony_crest.png"),
                ANY_CLAIMANT_STATS);

            when(stubNamedFaction(sectorMock, "hegemony", "Hegemony").getCrest())
                .thenReturn("graphics/hegemony_crest.png");

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock
                    .when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(buildReadOf(Map.of("hegemony", ANY_CLAIMANT_STATS)));

                // ACT
                var actualRows = ClaimsView
                    .INSTANCE
                    .resolveBlocPickerRead(sectorMock, UNDER_THE_FOG)
                    .picker()
                    .items();

                // ASSERT
                assertThat(actualRows)
                    .containsExactly(expectedRow);
            }
        }

        // The claim half of the picker reads through the same pass the market half does, so the two
        // metrics cost one walk of each system between them and describe the sector at one moment.
        // Opening the reader under a rule of its own would also let a bloc's claim count be answered
        // about colonies its market size was never shown.
        @Test
        void opensItsClaimReaderOverThePassItAggregatesThrough() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimBreakdownReader.class);
            var openedOver = new ArrayList<SectorPassIndex>();
            var openedUnder = new ArrayList<KnownColonyReader>();
            var aggregatedPasses = new ArrayList<HolderPass>();

            var view = new ClaimsView((knownColonyReader, sectorIndex) -> {

                openedUnder.add(knownColonyReader);
                openedOver.add(sectorIndex);

                return claimReaderMock;
            });

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock
                    .when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenAnswer(invocation -> {

                        aggregatedPasses.add(invocation.getArgument(0));
                        return buildReadOf(Map.of());
                    });

                // ACT
                view.resolveBlocPickerRead(sectorMock, buildRulesUnder(GATED_VISIBILITY));

                // ASSERT
                // The port is the pass's own knowledge, so what it was opened under is read back
                // off the rule that knowledge carries - stated against the literal the view was
                // asked with rather than against the pass a second time.
                assertThat(openedUnder)
                    .singleElement(as(type(ColonyKnowledge.class)))
                    .extracting(ColonyKnowledge::rule)
                    .isEqualTo(GATED_VISIBILITY);

                // The view opens its own pass, so the index cannot be named from out here. Pinned
                // as the one the market half was handed instead, which is the sharing that matters.
                assertThat(openedOver)
                    .singleElement()
                    .isSameAs(aggregatedPasses.get(0).sectorIndex());
            }
        }

        // The view answers the list and the modes together, so the numbers its blocs carry and the
        // metrics the sort selector offers can never drift apart - this layer is painted by the claim
        // mechanic, so claims is what the picker ranks by rather than domination. The standing
        // ranking behind them is the one criterion this view shares with the held ones, being read
        // off the sector rather than off either mechanic's fold.
        @Test
        void ranksItsBlocsByTheClaimVocabularyThenTheirStanding() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock
                    .when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(buildReadOf(Map.of()));

                // ACT
                var actualModes = ClaimsView
                    .INSTANCE
                    .resolveBlocPickerRead(sectorMock, UNDER_THE_FOG)
                    .picker()
                    .sortModes()
                    .modes();

                // ASSERT
                assertThat(actualModes)
                    .extracting(ListSortMode::persistenceKey)
                    .containsExactly(
                        "name",
                        "claims",
                        "market_size",
                        "player_standing");
            }
        }
    }

    @Nested
    class ResolveOwnerPickerRead {

        // The claims picker is painted by the claim mechanic, so its live entry reads the settings of
        // no other: the weighting rule is the domination contest's alone.
        @Test
        void readsNoWeightingRule() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class);
                    var visibilityRulesMock = mockStatic(MapVisibilityRules.class);
                    var weightingRulesMock = mockStatic(DominanceRules.class)) {

                aggregatorMock
                    .when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(buildReadOf(Map.of()));

                // The one live read this entry is meant to make, stood in as the plain fog.
                visibilityRulesMock
                    .when(MapVisibilityRules::readFromLunaSettings)
                    .thenReturn(MapVisibilityRules.BASE);

                // ACT
                ClaimsView.INSTANCE.resolveOwnerPickerRead(sectorMock);

                // ASSERT
                weightingRulesMock.verifyNoInteractions();
            }
        }
    }

    // A stubbed aggregation posing blocs and nowhere in particular, for the cases about the rows
    // alone: the claimed-system index beside them is left empty rather than stubbed to systems those
    // cases never ask about.
    private static OwnerStatsRead<ClaimStats> buildReadOf(Map<String, ClaimStats> statsByBlocId) {
        return new OwnerStatsRead<>(statsByBlocId, OwnerPresenceIndex.EMPTY);
    }
}
