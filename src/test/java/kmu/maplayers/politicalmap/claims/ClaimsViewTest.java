package kmu.maplayers.politicalmap.claims;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.markets.colonies.KnownColonyReader;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.ui.widgets.lists.ListSortMode;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.colonies.RevelationGate;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.dominance.BlocAffiliation;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.BlocPresenceIndex;
import kmu.maplayers.politicalmap.base.politics.ClaimStats;
import kmu.maplayers.politicalmap.base.politics.ClaimStatsAggregator;
import kmu.maplayers.politicalmap.base.politics.ClaimStatsRead;
import kmu.maplayers.politicalmap.base.politics.holders.ClaimsHolderProvider;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefreshSignal;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.render.ContentInputsFixtures;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanRules;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;
import kmu.maplayers.politicalmap.base.ribbon.UncontestedRibbonRuns;
import kmu.maplayers.politicalmap.base.tooltip.SystemClaimTooltip;
import kmu.maplayers.politicalmap.claims.ribbon.ClaimedSystemRibbonPlanner;
import kmu.util.KmuStringKeys;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.politicalmap.base.SelectableBlocFixtures.stubNamedFaction;
import static kmu.maplayers.politicalmap.base.dominance.DecivilisedColonyHabitation.COUNTS_AS_POPULATED;
import static kmu.maplayers.politicalmap.base.politics.BlocPresenceIndexFixtures.buildIndexOf;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the claims view's render rules: it is the faction view's look keyed off the vanilla claim
 * mechanic, so its grouping is identity, its holding comes from the claims-only provider, and its
 * bloc styling and label delegate to the faction view. Its picker is its own, though: it lists every
 * bloc that claims or holds something, receding the rows that claim nothing, and ranks them by claim
 * metrics rather than domination ones. Reproducing these here is what lets the
 * shared pipeline read the claims view through
 * {@link kmu.maplayers.politicalmap.base.PoliticalMapView} without naming it.
 */
final class ClaimsViewTest {

    // The claims view ignores its grouping argument (its own grouping is always identity), so any
    // grouping stands in where the interface demands one.
    private static final HolderGrouping ANY_GROUPING = HolderGrouping.identity();

    // A reading that does recede something, so a NONE answer here is this view adjusting nothing
    // rather than the picks it was handed holding nothing to adjust with.
    private static final ContentInputs RECEDING_INPUTS =
        ContentInputsFixtures.createInputsRecedingNonAllied(new ElementStyleAdjustment(0.4, true));

    @Nested
    class GetId {

        @Test
        void getIdIsTheSaveStableClaimsId() {
            assertThat(ClaimsView.INSTANCE.getId())
                .isEqualTo("claims");
        }
    }

    @Nested
    class GetSegmentLabelKey {

        @Test
        void getSegmentLabelKeyIsTheClaimsRadioLabel() {
            assertThat(ClaimsView.INSTANCE.getSegmentLabelKey())
                .isEqualTo(KmuStringKeys.POLITICAL_MAP_CTL_CLAIMS);
        }
    }

    @Nested
    class ResolveGrouping {

        @Test
        void resolveGroupingReturnsTheIdentityGrouping() {
            // Claims group strictly by claiming faction with no alliance rollup, so the pipeline
            // resolves plain faction holding.
            assertThat(ClaimsView.INSTANCE.resolveGrouping())
                .isSameAs(HolderGrouping.identity());
        }
    }

    @Nested
    class ResolveHolderProvider {

        @Test
        void resolveHolderProviderReturnsTheClaimsOnlyProvider() {
            // Holder is the claim mechanic itself, so the view supplies the claims-only provider
            // rather than inheriting the held-plus-claims default.
            assertThat(ClaimsView.INSTANCE.resolveHolderProvider())
                .isSameAs(ClaimsHolderProvider.INSTANCE);
        }
    }

    @Nested
    class ResolveRibbonPlanner {

        @Test
        void resolveRibbonPlannerCountsEveryCellFromTheClaimContest() {
            // Every cell here is painted by the claim mechanic, held systems included, so the band
            // is counted from the contest rather than by the dominance views' composition - which
            // would count a claimed system its claimant does not hold by the markets of whoever
            // does, and open the band on a bloc the cell is not painted for.
            var planner = ClaimsView.INSTANCE.resolveRibbonPlanner(
                new RibbonPlanInputs(
                    HolderPass.over(
                        mock(SectorAPI.class),
                        // Undiscovered colonies do not count, as on the live map.
                        BASE_FOG,
                        COUNTS_AS_POPULATED,
                        HolderGrouping.identity()),
                    blocId -> null,
                    // Nothing stands together, which is what the claims layer's own binding hands
                    // over on an install without alliances - and the planner it resolves does not
                    // read this either way.
                    BlocAffiliation.NONE,
                    new RibbonPlanRules(
                        new RibbonSegmentLengths(3, 1),
                        new UncontestedRibbonRuns(false))));

            assertThat(planner)
                .isInstanceOf(ClaimedSystemRibbonPlanner.class);
        }
    }

    @Nested
    class GetContentRevision {

        @Test
        void getContentRevisionShiftsWhenTheAllianceRevisionMoves() {
            // The alliance set is this view's one live input: the fills stay per-claimant, but the
            // bands over them lay a contested run only against a bloc the claimant is not allied
            // with, so a membership change must shift the revision and repaint them.
            var board = new MapLayerRefreshBoard();
            var before = ClaimsView.INSTANCE.getContentRevision(board);

            board.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);

            assertThat(ClaimsView.INSTANCE.getContentRevision(board))
                .isNotEqualTo(before);
        }

        @Test
        void getContentRevisionIsInvariantAcrossAnUnrelatedSignal() {
            // Only the alliance set is folded in - the market-inferred claims repaint on the shared
            // economy revision, not here - so a signal this view renders nothing from, a geometry
            // rebuild here, leaves its contribution fixed.
            var board = new MapLayerRefreshBoard();
            var before = ClaimsView.INSTANCE.getContentRevision(board);

            board.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);

            assertThat(ClaimsView.INSTANCE.getContentRevision(board))
                .isEqualTo(before);
        }
    }

    @Nested
    class ShouldUseIndependentStyle {

        @Test
        void shouldUseIndependentStyleIsTrueForIndependentSpace() {
            // Delegated to the faction view: an independent claimant recedes to the muted style like
            // independent territory.
            assertThat(ClaimsView.INSTANCE.shouldUseIndependentStyle(
                    Factions.INDEPENDENT,
                    ANY_GROUPING,
                    ElementStyleAdjustment.NONE))
                .isTrue();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForACoreFaction() {
            // A held claimant faction paints in full faction style, exactly as the faction view draws
            // it.
            assertThat(ClaimsView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony",
                    ANY_GROUPING,
                    ElementStyleAdjustment.NONE))
                .isFalse();
        }

        @Test
        void shouldUseIndependentStyleForwardsTheAdjustmentToTheFactionView() {
            // The adjustment argument must reach the delegate: a core faction the recede has
            // desaturated takes the independent style, so passing a desaturating adjustment (rather
            // than NONE) flips the result - pinning that the arg is forwarded, not dropped.
            assertThat(ClaimsView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony",
                    ANY_GROUPING,
                    new ElementStyleAdjustment(0.3, true)))
                .isTrue();
        }
    }

    @Nested
    class ResolveBlocStyleAdjustment {

        @Test
        void resolveBlocStyleAdjustmentIsNoneForAnyBloc() {
            // The claims view adjusts no bloc, delegating the faction view's decision that a claimant
            // and independent space alike draw exactly as classified.
            assertThat(ClaimsView.INSTANCE.resolveBlocStyleAdjustment(
                    "hegemony",
                    ANY_GROUPING,
                    RECEDING_INPUTS))
                .isEqualTo(ElementStyleAdjustment.NONE);
            assertThat(ClaimsView.INSTANCE.resolveBlocStyleAdjustment(
                    Factions.INDEPENDENT,
                    ANY_GROUPING,
                    RECEDING_INPUTS))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }
    }

    @Nested
    class ResolveName {

        @Test
        void resolveNameReadsTheClaimingFactionsOwnName() {
            // A claim bloc ID is a plain faction ID, so the label is that faction's display name in
            // the player's chosen form - resolved through the faction view.
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);

            when(sectorMock.getFaction("hegemony"))
                .thenReturn(factionMock);

            when(factionMock.getDisplayNameLong())
                .thenReturn("The Hegemony");

            assertThat(ClaimsView.INSTANCE.resolveName(
                    "hegemony",
                    ANY_GROUPING,
                    sectorMock,
                    FactionNameFormatChoice.FULL))
                .isEqualTo("The Hegemony");
        }

        @Test
        void resolveNameForwardsTheShortFormatToTheFactionView() {
            // The name-format argument must reach the delegate too: Short reads the faction's short
            // name rather than its long title, so asserting the short name pins that the format is
            // forwarded rather than defaulted.
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);

            when(sectorMock.getFaction("hegemony"))
                .thenReturn(factionMock);

            when(factionMock.getDisplayName())
                .thenReturn("Hegemony");

            assertThat(ClaimsView.INSTANCE.resolveName(
                    "hegemony",
                    ANY_GROUPING,
                    sectorMock,
                    FactionNameFormatChoice.SHORT))
                .isEqualTo("Hegemony");
        }

        @Test
        void resolveNameIsNullWhenTheFactionDoesNotResolve() {

            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction("ghost"))
                .thenReturn(null);

            assertThat(ClaimsView.INSTANCE.resolveName(
                    "ghost",
                    ANY_GROUPING,
                    sectorMock,
                    FactionNameFormatChoice.FULL))
                .isNull();
        }
    }

    @Nested
    class ResolveHoverTooltip {

        @Test
        void resolveHoverTooltipIsTheClaimBreakdownRatherThanTheDominationOne() {
            // This layer paints by the claim mechanic, so its hover has to explain that contest: the
            // domination breakdown the other two views inject describes standings this view never
            // painted by, and would read as an account of a border it did not draw.
            assertThat(ClaimsView.INSTANCE.resolveHoverTooltip())
                .contains(SystemClaimTooltip.INSTANCE);
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

        @Test
        void resolveBlocPickerReadCarriesEachClaimantsCrestShortNameAndStats() {
            // A claiming bloc becomes an option carrying its crest, short name, and the claim stats the
            // fold computed for it, so the option reads exactly as the picker row will draw and sort it.
            var sectorMock = mock(SectorAPI.class);

            when(stubNamedFaction(sectorMock, "hegemony", "Hegemony").getCrest())
                .thenReturn("graphics/hegemony_crest.png");

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(buildReadOf(Map.of("hegemony", ANY_CLAIMANT_STATS)));

                assertThat(ClaimsView.INSTANCE.resolveBlocPickerRead(sectorMock, BASE_FOG)
                        .picker()
                        .items())
                    .containsExactly(new RankedBloc<>(
                        new SelectableBloc(
                            "hegemony",
                            "Hegemony",
                            "graphics/hegemony_crest.png"),
                        ANY_CLAIMANT_STATS));
            }
        }

        @Test
        void resolveBlocPickerReadOffersAClaimantHoldingNoColonyAnywhere() {
            // The gate is claim presence, not market presence: a faction claiming territory while
            // holding nothing paints on this layer, so it must be spotlightable even at a market size
            // of zero.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "luddic_path", "Path");

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(buildReadOf(Map.of("luddic_path", new ClaimStats(1, 0))));

                assertThat(ClaimsView.INSTANCE.resolveBlocPickerRead(sectorMock, BASE_FOG)
                        .picker()
                        .items())
                    .extracting(RankedBloc::itemId)
                    .containsExactly("luddic_path");
            }
        }

        @Test
        void resolveBlocPickerReadOffersABlocThatHoldsColoniesButClaimsNothing() {
            // A faction the player can plainly see going unlisted reads as the map having forgotten
            // it, so a colony holder that claims nowhere is offered rather than dropped. What says
            // it paints nothing here is the row itself: it carries a claim count of zero, which is
            // what its metrics read back from.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");
            stubNamedFaction(sectorMock, "tritachyon", "Tri-Tachyon");

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(buildReadOf(Map.of(
                        "hegemony", new ClaimStats(1, 0),
                        "tritachyon", new ClaimStats(0, 40))));

                assertThat(ClaimsView.INSTANCE.resolveBlocPickerRead(sectorMock, BASE_FOG)
                        .picker()
                        .items())
                    .extracting(RankedBloc::itemId)
                    .containsExactlyInAnyOrder("hegemony", "tritachyon");
            }
        }

        @Test
        void resolveBlocPickerReadRecedesTheRowOfABlocThatClaimsNothing() {
            // Listing the claimless is only legible because the row says which it is, so the option
            // the view builds must carry that state through to the picker rather than reading as an
            // ordinary claimant with a zero on it.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");
            stubNamedFaction(sectorMock, "tritachyon", "Tri-Tachyon");

            var statsByBlocId = new LinkedHashMap<String, ClaimStats>();

            statsByBlocId.put("hegemony", new ClaimStats(1, 0));
            statsByBlocId.put("tritachyon", new ClaimStats(0, 40));

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(buildReadOf(statsByBlocId));

                assertThat(ClaimsView.INSTANCE.resolveBlocPickerRead(sectorMock, BASE_FOG)
                        .picker()
                        .items())
                    .extracting(RankedBloc::itemId, RankedBloc::isDimmed)
                    .containsExactly(
                        tuple("hegemony", false),
                        tuple("tritachyon", true));
            }
        }

        @Test
        void resolveBlocPickerReadKeepsTheBlocsInTheFoldsWalkOrder() {
            // The options come back in the order the sector walk surfaced them, which is the order
            // the sort then arranges from. An ordered stub with a claimless bloc in the middle is
            // what makes a reordering visible - the list must neither drop it nor sink it here, since
            // where a receded row lands is the active sort's decision and not the assembly's.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");
            stubNamedFaction(sectorMock, "tritachyon", "Tri-Tachyon");
            stubNamedFaction(sectorMock, "persean", "Persean League");

            var statsByBlocId = new LinkedHashMap<String, ClaimStats>();

            statsByBlocId.put("hegemony", new ClaimStats(1, 0));
            statsByBlocId.put("tritachyon", new ClaimStats(0, 40));
            statsByBlocId.put("persean", new ClaimStats(3, 12));

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(buildReadOf(statsByBlocId));

                assertThat(ClaimsView.INSTANCE.resolveBlocPickerRead(sectorMock, BASE_FOG)
                        .picker()
                        .items())
                    .extracting(RankedBloc::itemId)
                    .containsExactly("hegemony", "tritachyon", "persean");
            }
        }

        @Test
        void resolveBlocPickerReadOffersNoItemsWhenTheFoldSurfacesNothing() {
            // A bloc that neither claims nor holds anything never reaches the fold, so a sector with
            // none of either offers no options - the picker draws no controls at all - and a stale
            // saved selection heals to none.
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(buildReadOf(Map.of()));

                assertThat(ClaimsView.INSTANCE.resolveBlocPickerRead(sectorMock, BASE_FOG)
                        .picker()
                        .items())
                    .isEmpty();
            }
        }

        @Test
        void resolveBlocPickerReadOpensItsClaimReaderOverThePassItAggregatesThrough() {
            // The claim half of the picker reads through the same pass the market half does, so the
            // two metrics cost one walk of each system between them and describe the sector at one
            // moment. Opening the reader under a rule of its own would also let a bloc's claim count
            // be answered about colonies its market size was never shown.
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var openedOver = new ArrayList<SectorPassIndex>();
            var openedUnder = new ArrayList<KnownColonyReader>();

            var aggregatedPasses = new ArrayList<HolderPass>();

            var view = new ClaimsView((knownColonyReader, sectorIndex) -> {
                openedUnder.add(knownColonyReader);
                openedOver.add(sectorIndex);
                return claimReaderMock;
            });

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenAnswer(invocation -> {
                        aggregatedPasses.add(invocation.getArgument(0));
                        return buildReadOf(Map.of());
                    });

                view.resolveBlocPickerRead(sectorMock, GATED_VISIBILITY);

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

        @Test
        void resolveBlocPickerReadRanksItsBlocsByTheClaimVocabularyThenTheirStanding() {
            // The view answers the list and the modes together, so the numbers its blocs carry and the
            // metrics the sort selector offers can never drift apart - this layer is painted by the
            // claim mechanic, so claims is what the picker ranks by rather than domination. The
            // standing ranking behind them is the one criterion this view shares with the held ones,
            // being read off the sector rather than off either mechanic's fold.
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(buildReadOf(Map.of()));

                assertThat(ClaimsView.INSTANCE.resolveBlocPickerRead(sectorMock, BASE_FOG)
                        .picker()
                        .sortModes()
                        .modes())
                    .extracting(ListSortMode::persistenceKey)
                    .containsExactly("name", "claims", "market_size", "player_standing");
            }
        }

        @Test
        void resolveBlocPickerReadReachesNoWeightingRuleOnItsLiveEntry() {
            // The live entry the sidebar and the stale-selection heal call, driven whole rather than
            // under stated knobs - which is the only way this can be asked at all. A weighting rule
            // is read from LunaLib, a class the test JVM cannot load, so a seam handing this layer
            // one would take the case down here rather than merely doing work nobody spends. That
            // is the assertion: the claims picker is painted by the claim mechanic, and reads the
            // settings of no other.
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class);
                    var visibilityRulesMock = mockStatic(MapVisibilityRules.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(buildReadOf(Map.of()));

                // The one live read this entry is meant to make, stood in as the plain fog.
                visibilityRulesMock
                    .when(MapVisibilityRules::readFromLunaSettings)
                    .thenReturn(MapVisibilityRules.BASE);

                assertThat(ClaimsView.INSTANCE.resolveBlocPickerRead(sectorMock).picker().items())
                    .isEmpty();
            }
        }

        @Test
        void resolveBlocPickerReadCarriesTheClaimWalksPresenceBesideTheRows() {
            // The presence comes off the same walk the rows do, handed on rather than derived here.
            // That the walk's index is the claim arm's alone - a bloc's colonies being summed from
            // wherever they are, so a system it merely lives in is never named - is the aggregator's
            // guarantee and is pinned there; what this pins is that the view does not lose it.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any()))
                    .thenReturn(new ClaimStatsRead(
                        Map.of("hegemony", ANY_CLAIMANT_STATS),
                        buildIndexOf("hegemony", "corvus")));

                assertThat(ClaimsView.INSTANCE.resolveBlocPickerRead(sectorMock, BASE_FOG)
                        .presenceIndex()
                        .readPresentSystemKeys("hegemony"))
                    .containsExactlyElementsOf(buildCellKeys("corvus"));
            }
        }
    }

    // A stubbed aggregation posing blocs and nowhere in particular, for the cases about the rows
    // alone: the claimed-system index beside them is left empty rather than stubbed to systems those
    // cases never ask about.
    private static ClaimStatsRead buildReadOf(Map<String, ClaimStats> statsByBlocId) {
        return new ClaimStatsRead(statsByBlocId, BlocPresenceIndex.EMPTY);
    }
}
