package kmu.maplayers.politicalmap.dominance.factions;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.ui.widgets.lists.ListSortMode;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.dominance.BlocAffiliation;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.base.politics.BlocPresenceIndex;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;
import kmu.maplayers.politicalmap.base.politics.DominanceStatsAggregator;
import kmu.maplayers.politicalmap.base.politics.DominanceStatsRead;
import kmu.maplayers.politicalmap.base.politics.holders.ClaimAugmentedHolderProvider;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefreshSignal;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.render.ContentInputsFixtures;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanRules;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;
import kmu.maplayers.politicalmap.base.ribbon.UncontestedRibbonRuns;
import kmu.maplayers.politicalmap.dominance.ribbon.HeldOrClaimedSystemRibbonPlanner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.politicalmap.base.SelectableBlocFixtures.stubNamedFaction;
import static kmu.maplayers.politicalmap.base.politics.BlocPresenceIndexFixtures.buildIndexOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the faction view's render rules - the identity case the shared political-map
 * pipeline was carved out of: the grouping is identity, only independent space is drawn
 * in the muted style, and a bloc is labelled by its own faction's display name in the
 * player's chosen form. Reproducing these here is what lets the shared pipeline read the
 * faction view through {@link PoliticalMapView} rather than the inlined tests it used to.
 */
final class FactionsViewTest {

    // The faction view ignores its grouping argument (its own grouping is always identity),
    // so any grouping stands in where the interface demands one.
    private static final HolderGrouping ANY_GROUPING = HolderGrouping.identity();

    // A reading that does recede something, so a NONE answer here is this view adjusting nothing
    // rather than the picks it was handed holding nothing to adjust with.
    private static final ContentInputs RECEDING_INPUTS =
        ContentInputsFixtures.createInputsRecedingNonAllied(new ElementStyleAdjustment(0.4, true));

    @Nested
    class ResolveGrouping {

        @Test
        void resolveGroupingReturnsTheIdentityGrouping() {
            // Every faction is its own bloc, so the pipeline resolves plain faction holding.
            assertThat(FactionsView.INSTANCE.resolveGrouping())
                .isSameAs(HolderGrouping.identity());
        }
    }

    @Nested
    class ResolveHolderProvider {

        @Test
        void resolveHolderProviderReturnsTheDefaultProvider() {
            // The faction view resolves holding no differently from the pipeline's default -
            // each system's dominant holder, extended with the systems it merely claims - so it
            // inherits the shared claim-augmented default rather than supplying one of its own.
            assertThat(FactionsView.INSTANCE.resolveHolderProvider())
                .isSameAs(ClaimAugmentedHolderProvider.INSTANCE);
        }
    }

    @Nested
    class ResolveRibbonPlanner {

        @Test
        void resolveRibbonPlannerCountsEachSystemByTheMechanicThatPaintedIt() {
            // The faction view paints held territory and extends it with claims, so its bands are
            // counted the same way round: the shared composition every contest-painted view
            // inherits, rather than one mechanic answering for cells the other painted.
            // The held half names the player's live weighting rule, which reaches LunaLib - a
            // class the test JVM cannot load - so the pass it would build is handed over already
            // resolved. What the case reads is which planner the view assembles, not where its
            // knobs came from.
            var holding = new HolderPass(
                HolderGrouping.identity(),
                // Undiscovered colonies do not count, as on the live map.
                BASE_FOG,
                new SectorPassIndex(null));

            try (var passMock = mockStatic(DominancePass.class)) {
                passMock
                    .when(() -> DominancePass.readRulesFromLunaSettings(any(HolderPass.class)))
                    // Built through the constructor rather than the static entry, which is stood
                    // in for here and would answer with the stand-in rather than build a pass.
                    .thenReturn(new DominancePass(mock(DominanceRules.class), holding));

                var planner = FactionsView.INSTANCE.resolveRibbonPlanner(
                    new RibbonPlanInputs(
                        holding,
                        blocId -> null,
                        // Nothing stands together, the case being about which planner the view
                        // assembles rather than about how any cell of it is judged.
                        BlocAffiliation.NONE,
                        new RibbonPlanRules(
                            new RibbonSegmentLengths(3, 1),
                            new UncontestedRibbonRuns(false))));

                assertThat(planner)
                    .isInstanceOf(HeldOrClaimedSystemRibbonPlanner.class);
            }
        }
    }

    @Nested
    class GetContentRevision {

        @Test
        void getContentRevisionShiftsWhenTheAllianceRevisionMoves() {
            // The alliance set is this view's one live input: its bands lay a contested run only
            // against a bloc the painter is not allied with, so a membership change must shift the
            // revision - that is what repaints the bands at their new lengths instead of leaving
            // them stale until an unrelated rebuild.
            var board = new MapLayerRefreshBoard();
            var before = FactionsView.INSTANCE.getContentRevision(board);

            board.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);

            assertThat(FactionsView.INSTANCE.getContentRevision(board))
                .isNotEqualTo(before);
        }

        @Test
        void getContentRevisionIsInvariantAcrossAnUnrelatedSignal() {
            // Only the alliance set is folded in, so a signal this view renders nothing from - a
            // geometry rebuild here - leaves its contribution fixed rather than churning the view.
            var board = new MapLayerRefreshBoard();
            var before = FactionsView.INSTANCE.getContentRevision(board);

            board.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);

            assertThat(FactionsView.INSTANCE.getContentRevision(board))
                .isEqualTo(before);
        }
    }

    @Nested
    class ShouldUseIndependentStyle {

        @Test
        void shouldUseIndependentStyleIsTrueForIndependentSpace() {
            assertThat(FactionsView.INSTANCE.shouldUseIndependentStyle(
                    Factions.INDEPENDENT,
                    ANY_GROUPING,
                    ElementStyleAdjustment.NONE))
                .isTrue();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForACoreFaction() {
            assertThat(FactionsView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony",
                    ANY_GROUPING,
                    ElementStyleAdjustment.NONE))
                .isFalse();
        }

        @Test
        void shouldUseIndependentStyleIsTrueForACoreFactionWhenDesaturated() {
            // A faction the filter recede has desaturated reads as backdrop, so it takes
            // the independent borders and seams paired with the desaturation palette - the same
            // classification the alliances view makes for a desaturated non-allied bloc.
            assertThat(FactionsView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony",
                    ANY_GROUPING,
                    new ElementStyleAdjustment(0.3, true)))
                .isTrue();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForACoreFactionWhenOnlyMuted() {
            // Muting dims a bloc but does not desaturate it, so a merely muted faction keeps its
            // faction bundle: dimming alone never swaps border weight or the palette slot.
            assertThat(FactionsView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony",
                    ANY_GROUPING,
                    new ElementStyleAdjustment(0.3, false)))
                .isFalse();
        }
    }

    @Nested
    class ResolveBlocStyleAdjustment {

        @Test
        void resolveBlocStyleAdjustmentIsNoneForAnyBloc() {
            // The faction view adjusts no bloc - a core faction and independent space alike
            // draw exactly as classified, so the pipeline has nothing to dim or recolour.
            assertThat(FactionsView.INSTANCE.resolveBlocStyleAdjustment(
                    "hegemony",
                    ANY_GROUPING,
                    RECEDING_INPUTS))
                .isEqualTo(ElementStyleAdjustment.NONE);

            assertThat(FactionsView.INSTANCE.resolveBlocStyleAdjustment(
                    Factions.INDEPENDENT,
                    ANY_GROUPING,
                    RECEDING_INPUTS))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }
    }

    @Nested
    class ResolveName {

        @Test
        void resolveNameReadsTheLongNameForTheFullFormat() {

            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);

            when(sectorMock.getFaction("hegemony"))
                .thenReturn(factionMock);
            when(factionMock.getDisplayNameLong())
                .thenReturn("The Hegemony");

            assertThat(FactionsView.INSTANCE.resolveName(
                    "hegemony",
                    ANY_GROUPING,
                    sectorMock,
                    FactionNameFormatChoice.FULL))
                .isEqualTo("The Hegemony");
        }

        @Test
        void resolveNameReadsTheShortNameForTheShortFormat() {

            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");

            assertThat(FactionsView.INSTANCE.resolveName(
                    "hegemony",
                    ANY_GROUPING,
                    sectorMock,
                    FactionNameFormatChoice.SHORT))
                .isEqualTo("Hegemony");
        }

        @Test
        void resolveNameIsNullWhenTheFactionDoesNotResolve() {
            // A bloc id with no faction behind it carries no name; the label fit then sizes
            // its stand-in band instead of drawing a name.
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction("ghost"))
                .thenReturn(null);

            assertThat(FactionsView.INSTANCE.resolveName(
                    "ghost",
                    ANY_GROUPING,
                    sectorMock,
                    FactionNameFormatChoice.FULL))
                .isNull();
        }
    }

    @Nested
    class ResolveBlocPickerRead {

        // The rules are forwarded to the (mocked) stats read, so their value never reaches assertion
        // here - any rules stand in where the seam demands them.
        private static final DominanceRules ANY_RULES =
            new DominanceRules(false,
                new BaseSizeWeighting(1.0, null, 1.0, 1.0),
                new StationWeighting(false, 1.0, 0.5, 0.5),
                new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

        // The view forwards a present bloc's stats onto its option verbatim, so any stats value
        // stands in - these arbitrary numbers are only asserted to survive the pass unchanged.
        private static final DominanceStats ANY_STATS = new DominanceStats(3, 2, 5000, 7);

        @Test
        void resolveBlocPickerReadCarriesEachPresentFactionsCrestShortNameAndStats() {
            // Every present faction becomes an option carrying its crest, short name, and the stats the
            // shared read computed for it, so the option reads exactly as the picker row will draw and
            // sort it. The presence gate is the shared stats read's job, stubbed here to one faction.
            var sectorMock = mock(SectorAPI.class);

            when(stubNamedFaction(sectorMock, "hegemony", "Hegemony").getCrest())
                .thenReturn("graphics/hegemony_crest.png");

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(buildReadOf(Map.of("hegemony", ANY_STATS)));

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, BASE_FOG)
                        .picker()
                        .items())
                    .containsExactly(new RankedBloc<>(
                        new SelectableBloc(
                            "hegemony",
                            "Hegemony",
                            "graphics/hegemony_crest.png"),
                        ANY_STATS));
            }
        }

        @Test
        void resolveBlocPickerReadKeepsAFactionWithNoCrestAsANullCrestOption() {
            // A faction with no authored crest is still selectable - its option just carries a null
            // crest path and the row draws its name alone, rather than being dropped.
            var sectorMock = mock(SectorAPI.class);

            when(stubNamedFaction(sectorMock, "luddic_path", "Path").getCrest())
                .thenReturn(null);

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(buildReadOf(Map.of("luddic_path", ANY_STATS)));

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, BASE_FOG)
                        .picker()
                        .items())
                    .containsExactly(new RankedBloc<>(
                        new SelectableBloc("luddic_path", "Path", null),
                        ANY_STATS));
            }
        }

        @Test
        void resolveBlocPickerReadRecedesTheRowOfAFactionOfNoWeight() {
            // The layers the contest paints grey a bloc by the same rule the claims layer does, on
            // their own metric: a faction present only through colonies the contest never weighed
            // paints no cell anywhere, so its row reads back - and stays listed and pickable, since
            // spotlighting it is the honest answer to "show me what this faction holds".
            var sectorMock = mock(SectorAPI.class);
            var statsByBlocId = new LinkedHashMap<String, DominanceStats>();

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");
            stubNamedFaction(sectorMock, "crusader_plan", "Crusader Plan");

            statsByBlocId.put("hegemony", ANY_STATS);
            statsByBlocId.put("crusader_plan", new DominanceStats(0, 1, 0, 4));

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(buildReadOf(statsByBlocId));

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, BASE_FOG)
                        .picker()
                        .items())
                    .extracting(RankedBloc::itemId, RankedBloc::isDimmed)
                    .containsExactly(
                        tuple("hegemony", false),
                        tuple("crusader_plan", true));
            }
        }

        @Test
        void resolveBlocPickerReadOffersNoItemsWhenNoBlocIsPresent() {
            // With no present bloc the picker offers no options and a stale saved selection heals to
            // none.
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(DominanceStatsRead.EMPTY);

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, BASE_FOG)
                        .picker()
                        .items())
                    .isEmpty();
            }
        }

        @Test
        void resolveBlocPickerReadRanksItsBlocsByTheDominanceVocabularyThenTheirStanding() {
            // The view answers the list and the modes together, so the numbers its blocs carry and
            // the metrics the sort selector offers can never drift apart - this layer is painted by
            // domination, so domination is what the picker ranks by. Behind them sits the standing
            // ranking, which is not this layer's number but the one fact every view offers.
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(DominanceStatsRead.EMPTY);

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, BASE_FOG)
                        .picker()
                        .sortModes()
                        .modes())
                    .extracting(ListSortMode::persistenceKey)
                    .containsExactly(
                        "name", "domination", "presence", "score", "market_size", "player_standing");
            }
        }

        @Test
        void resolveBlocPickerReadCarriesTheDominanceWalksPresenceBesideTheRows() {
            // Where a bloc lives comes off the very walk that totalled its row, handed on rather
            // than derived a second time here. Anything else would be a second answer to "where is
            // this bloc" beside rows already carrying the count of it.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(new DominanceStatsRead(
                        Map.of("hegemony", ANY_STATS),
                        buildIndexOf("hegemony", "corvus", "askonia")));

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, BASE_FOG)
                        .presenceIndex()
                        .readPresentSystemIds("hegemony"))
                    .containsExactly("corvus", "askonia");
            }
        }
    }

    // A stubbed aggregation posing blocs and nowhere in particular, for the cases about the rows
    // alone: the presence index beside them is left empty rather than stubbed to systems those
    // cases never ask about.
    private static DominanceStatsRead buildReadOf(Map<String, DominanceStats> statsByBlocId) {
        return new DominanceStatsRead(statsByBlocId, BlocPresenceIndex.EMPTY);
    }
}
