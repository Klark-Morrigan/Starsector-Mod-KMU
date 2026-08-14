package kmu.maplayers.politicalmap.dominance.factions;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.DominanceSortMode;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;
import kmu.maplayers.politicalmap.base.politics.DominanceStatsAggregator;
import kmu.maplayers.politicalmap.base.politics.holders.ClaimAugmentedHolderProvider;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefreshSignal;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;
import kmu.maplayers.politicalmap.base.ribbon.UncontestedCellBands;
import kmu.maplayers.politicalmap.dominance.ribbon.HeldOrClaimedSystemRibbonPlanner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
            // The held half samples the player's live dominance settings, which reach LunaLib -
            // a class the test JVM cannot load - so the pass is handed over already resolved.
            // What the case reads is which planner the view assembles, not where its knobs came
            // from.
            try (var passMock = mockStatic(DominancePass.class)) {
                passMock
                    .when(() -> DominancePass.readFromLunaSettings(any(HolderGrouping.class)))
                    .thenReturn(new DominancePass(
                        mock(DominanceRules.class),
                        false, // Undiscovered colonies do not count, as on the live map.
                        HolderGrouping.identity()));

                var planner = FactionsView.INSTANCE.resolveRibbonPlanner(
                    mock(SectorAPI.class),
                    HolderGrouping.identity(),
                    new RibbonPlanInputs(
                        blocId -> null,
                        new RibbonSegmentLengths(3, 1),
                        new UncontestedCellBands(false, false)));

                assertThat(planner)
                    .isInstanceOf(HeldOrClaimedSystemRibbonPlanner.class);
            }
        }
    }

    @Nested
    class GetContentRevision {

        @Test
        void getContentRevisionIsInvariantAcrossAllianceChanges() {
            // The identity grouping never changes in a session, so an alliance forming or
            // dissolving (which bumps the shared alliance revision) must leave the faction
            // view's contribution fixed - that is what keeps an alliance change from churning
            // the faction view.
            var before = FactionsView.INSTANCE.getContentRevision();

            MapLayerRefresh.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);

            assertThat(FactionsView.INSTANCE.getContentRevision())
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
                    ANY_GROUPING))
                .isEqualTo(ElementStyleAdjustment.NONE);

            assertThat(FactionsView.INSTANCE.resolveBlocStyleAdjustment(
                    Factions.INDEPENDENT,
                    ANY_GROUPING))
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
            var factionMock = mock(FactionAPI.class);

            when(sectorMock.getFaction("hegemony"))
                .thenReturn(factionMock);
            when(factionMock.getDisplayName())
                .thenReturn("Hegemony");

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
    class ResolveBlocPicker {

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
        void resolveBlocPickerCarriesEachPresentFactionsCrestShortNameAndStats() {
            // Every present faction becomes an option carrying its crest, short name, and the stats the
            // shared read computed for it, so the option reads exactly as the picker row will draw and
            // sort it. The presence gate is the shared stats read's job, stubbed here to one faction.
            var sectorMock = mock(SectorAPI.class);
            var hegemonyMock = mock(FactionAPI.class);

            when(sectorMock.getFaction("hegemony"))
                .thenReturn(hegemonyMock);

            when(hegemonyMock.getCrest())
                .thenReturn("graphics/hegemony_crest.png");
            when(hegemonyMock.getDisplayName())
                .thenReturn("Hegemony");

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any(), any()))
                    .thenReturn(Map.of("hegemony", ANY_STATS));

                assertThat(FactionsView.INSTANCE.resolveBlocPicker(sectorMock, ANY_RULES, false).items())
                    .containsExactly(new RankedBloc<>(
                        new SelectableBloc(
                            "hegemony",
                            "Hegemony",
                            "graphics/hegemony_crest.png"),
                        ANY_STATS));
            }
        }

        @Test
        void resolveBlocPickerKeepsAFactionWithNoCrestAsANullCrestOption() {
            // A faction with no authored crest is still selectable - its option just carries a null
            // crest path and the row draws its name alone, rather than being dropped.
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);

            when(sectorMock.getFaction("luddic_path"))
                .thenReturn(factionMock);

            when(factionMock.getCrest())
                .thenReturn(null);
            when(factionMock.getDisplayName())
                .thenReturn("Path");

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any(), any()))
                    .thenReturn(Map.of("luddic_path", ANY_STATS));

                assertThat(FactionsView.INSTANCE.resolveBlocPicker(sectorMock, ANY_RULES, false).items())
                    .containsExactly(new RankedBloc<>(
                        new SelectableBloc("luddic_path", "Path", null),
                        ANY_STATS));
            }
        }

        @Test
        void resolveBlocPickerOffersNoItemsWhenNoBlocIsPresent() {
            // With no present bloc the picker offers no options and a stale saved selection heals to
            // none.
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any(), any()))
                    .thenReturn(Map.of());

                assertThat(FactionsView.INSTANCE.resolveBlocPicker(sectorMock, ANY_RULES, false).items())
                    .isEmpty();
            }
        }

        @Test
        void resolveBlocPickerRanksItsBlocsByTheDominanceVocabulary() {
            // The view answers the list and the modes together, so the numbers its blocs carry and
            // the metrics the sort selector offers can never drift apart - this layer is painted by
            // domination, so domination is what the picker ranks by.
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock.when(() -> DominanceStatsAggregator.aggregateDominanceStats(any(), any()))
                    .thenReturn(Map.of());

                assertThat(FactionsView.INSTANCE.resolveBlocPicker(sectorMock, ANY_RULES, false)
                        .sortModes())
                    .isEqualTo(DominanceSortMode.MODES);
            }
        }
    }
}
