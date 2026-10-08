package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.ui.widgets.lists.ListSortMode;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderOwnerSource;
import kmu.maplayers.ownermap.picker.OwnerPresenceIndex;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.ownermap.picker.SelectableOwner;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.maplayers.politicalmap.dominance.DominanceStats;
import kmu.maplayers.politicalmap.dominance.DominanceStatsAggregator;
import kmu.maplayers.politicalmap.dominance.DominanceStatsRead;
import kmu.maplayers.politicalmap.dominance.tooltip.SystemDominationTooltip;
import kmu.maplayers.politicalmap.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.refresh.PoliticalMapRefreshSignal;
import kmu.mods.nexerelin.NexerelinAlliances;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.picker.OwnerPresenceIndexFixtures.buildIndexOf;
import static kmu.maplayers.politicalmap.views.FactionNameFixtures.stubNamedFaction;

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
 * faction view through {@link OwnerPaintedView} rather than the inlined tests it used to.
 *
 * <p>Beside them, what the view offers the player around the fills: every bloc as a spotlight
 * target, since no faction here is folded into another, and the domination box on hover, since
 * domination is what painted the cells.
 *
 * <p>What every view the domination contest paints answers alike is {@link DominancePaintedView}'s,
 * pinned once by its own suite.
 */
final class FactionsViewTests {

    // The faction view ignores its grouping argument (its own grouping is always identity),
    // so any grouping stands in where the interface demands one.
    private static final HolderGrouping ANY_GROUPING = HolderGrouping.identity();

    @Nested
    class ResolveGrouping {

        @Test
        void returnsTheIdentityGrouping() {
            // Every faction is its own bloc, so the pipeline resolves plain faction holding.
            assertThat(FactionsView.INSTANCE.resolveGrouping())
                .isSameAs(HolderGrouping.identity());
        }
    }

    @Nested
    class GetContentRevision {

        @Test
        void shiftsWhenTheAllianceRevisionMoves() {
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
        void isInvariantAcrossAnUnrelatedSignal() {
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
    class ResolveOwnerReading {

        @Test
        void namesABlocByItsFactionOffTheHandedSector() {
            // One answer read back, enough to show the reading is over the handed sector and names a
            // bloc as its faction. The rest of what it answers is pinned by that reading's own suite.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");

            assertThat(FactionsView.INSTANCE
                    .resolveOwnerReading(sectorMock, HolderGrouping.identity())
                    .resolveName("hegemony", OwnerNameFormatChoice.SHORT))
                .isEqualTo("Hegemony");
        }
    }

    @Nested
    class ResolveViewReading {

        @Test
        void namesBlocsByTheirFactionsAndFoldsHoldingByFaction() {
            // The two answers the core asks for, both over the identity grouping this view paints
            // by - a reading naming each bloc as its faction, and the holder source folding every
            // faction as its own bloc.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");

            try (var alliancesMock = mockStatic(NexerelinAlliances.class)) {
                alliancesMock
                    .when(NexerelinAlliances::resolveGrouping)
                    .thenReturn(HolderGrouping.identity());

                var viewReading = FactionsView.INSTANCE.resolveViewReading(sectorMock);

                assertThat(viewReading.reading().resolveName("hegemony", OwnerNameFormatChoice.SHORT))
                    .isEqualTo("Hegemony");
                assertThat(viewReading.source())
                    .isInstanceOfSatisfying(
                        HolderOwnerSource.class,
                        source -> assertThat(source.grouping()).isSameAs(HolderGrouping.identity()));
            }
        }
    }

    @Nested
    class ResolveHoverTooltip {

        @Test
        void offersTheDominationBox() {
            // The box explaining the fills this view paints, which are painted by domination.
            assertThat(FactionsView.INSTANCE.resolveHoverTooltip())
                .containsInstanceOf(SystemDominationTooltip.class);
        }

        @Test
        void offersTheSameBoxOnEveryAsk() {
            // Held once by the view rather than built per hover, so every ask reads one box.
            assertThat(FactionsView.INSTANCE.resolveHoverTooltip().orElseThrow())
                .isSameAs(FactionsView.INSTANCE.resolveHoverTooltip().orElseThrow());
        }
    }

    @Nested
    class ResolveSelectableBlocGate {

        @Test
        void admitsEveryBloc() {
            // Under identity every faction is its own bloc, so a core faction, independent space and
            // a pirate bloc alike are spotlight targets; presence is the shared stats read's gate.
            var gate = FactionsView.INSTANCE.resolveSelectableBlocGate(ANY_GROUPING);

            assertThat(List.of("hegemony", Factions.INDEPENDENT, "pirates"))
                .allMatch(gate);
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
        void carriesEachPresentFactionsCrestShortNameAndStats() {
            // Every present faction becomes an option carrying its crest, short name, and the stats the
            // shared read computed for it, so the option reads exactly as the picker row will draw and
            // sort it. The presence gate is the shared stats read's job, stubbed here to one faction.
            var sectorMock = mock(SectorAPI.class);

            when(stubNamedFaction(sectorMock, "hegemony", "Hegemony").getCrest())
                .thenReturn("graphics/hegemony_crest.png");

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(buildReadOf(Map.of("hegemony", ANY_STATS)));

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, UNDER_THE_FOG)
                        .picker()
                        .items())
                    .containsExactly(new RankedOwner<>(
                        new SelectableOwner(
                            "hegemony",
                            "Hegemony",
                            "graphics/hegemony_crest.png"),
                        ANY_STATS));
            }
        }

        @Test
        void keepsAFactionWithNoCrestAsANullCrestOption() {
            // A faction with no authored crest is still selectable - its option just carries a null
            // crest path and the row draws its name alone, rather than being dropped.
            var sectorMock = mock(SectorAPI.class);

            when(stubNamedFaction(sectorMock, "luddic_path", "Path").getCrest())
                .thenReturn(null);

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(buildReadOf(Map.of("luddic_path", ANY_STATS)));

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, UNDER_THE_FOG)
                        .picker()
                        .items())
                    .containsExactly(new RankedOwner<>(
                        new SelectableOwner("luddic_path", "Path", null),
                        ANY_STATS));
            }
        }

        @Test
        void recedesTheRowOfAFactionOfNoWeight() {
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

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, UNDER_THE_FOG)
                        .picker()
                        .items())
                    .extracting(RankedOwner::itemId, RankedOwner::isDimmed)
                    .containsExactly(
                        tuple("hegemony", false),
                        tuple("crusader_plan", true));
            }
        }

        @Test
        void offersNoItemsWhenNoBlocIsPresent() {
            // With no present bloc the picker offers no options and a stale saved selection heals to
            // none.
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(DominanceStatsRead.EMPTY);

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, UNDER_THE_FOG)
                        .picker()
                        .items())
                    .isEmpty();
            }
        }

        @Test
        void ranksItsBlocsByTheDominanceVocabularyThenTheirStanding() {
            // The view answers the list and the modes together, so the numbers its blocs carry and
            // the metrics the sort selector offers can never drift apart - this layer is painted by
            // domination, so domination is what the picker ranks by. Behind them sits the standing
            // ranking, which is not this layer's number but the one fact every view offers.
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(DominanceStatsRead.EMPTY);

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, UNDER_THE_FOG)
                        .picker()
                        .sortModes()
                        .modes())
                    .extracting(ListSortMode::persistenceKey)
                    .containsExactly(
                        "name", "domination", "presence", "score", "market_size", "player_standing");
            }
        }

        @Test
        void carriesTheDominanceWalksPresenceBesideTheRows() {
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

                assertThat(FactionsView.INSTANCE.resolveBlocPickerRead(sectorMock, ANY_RULES, UNDER_THE_FOG)
                        .presenceIndex()
                        .readPresentSystemKeys("hegemony"))
                    .containsExactlyElementsOf(buildCellKeys("corvus", "askonia"));
            }
        }
    }

    // A stubbed aggregation posing blocs and nowhere in particular, for the cases about the rows
    // alone: the presence index beside them is left empty rather than stubbed to systems those
    // cases never ask about.
    private static DominanceStatsRead buildReadOf(Map<String, DominanceStats> statsByBlocId) {
        return new DominanceStatsRead(statsByBlocId, OwnerPresenceIndex.EMPTY);
    }
}
