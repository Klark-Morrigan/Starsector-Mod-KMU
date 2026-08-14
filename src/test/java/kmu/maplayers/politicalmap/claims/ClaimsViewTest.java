package kmu.maplayers.politicalmap.claims;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.base.politics.ClaimStats;
import kmu.maplayers.politicalmap.base.politics.ClaimStatsAggregator;
import kmu.maplayers.politicalmap.base.politics.holders.ClaimsHolderProvider;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefreshSignal;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanRules;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;
import kmu.maplayers.politicalmap.base.ribbon.UncontestedCellBands;
import kmu.maplayers.politicalmap.base.tooltip.SystemClaimTooltip;
import kmu.maplayers.politicalmap.claims.ribbon.ClaimedSystemRibbonPlanner;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
                .isEqualTo(KmuStrings.POLITICAL_MAP_CTL_CLAIMS);
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
                mock(SectorAPI.class),
                HolderGrouping.identity(),
                new RibbonPlanInputs(
                    blocId -> null,
                    new RibbonPlanRules(
                        new RibbonSegmentLengths(3, 1),
                        new UncontestedCellBands(false, false))));

            assertThat(planner)
                .isInstanceOf(ClaimedSystemRibbonPlanner.class);
        }
    }

    @Nested
    class GetContentRevision {

        @Test
        void getContentRevisionIsInvariantAcrossAllianceChanges() {
            // The claims view samples no live input of its own - its market-inferred claims repaint on
            // the shared economy revision, not here - so an alliance forming or dissolving (which the
            // alliances view renders) must leave its contribution fixed and never churn this view.
            var before = ClaimsView.INSTANCE.getContentRevision();

            MapLayerRefresh.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);

            assertThat(ClaimsView.INSTANCE.getContentRevision())
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
            assertThat(ClaimsView.INSTANCE.resolveBlocStyleAdjustment("hegemony", ANY_GROUPING))
                .isEqualTo(ElementStyleAdjustment.NONE);
            assertThat(ClaimsView.INSTANCE.resolveBlocStyleAdjustment(
                    Factions.INDEPENDENT,
                    ANY_GROUPING))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }
    }

    @Nested
    class ResolveName {

        @Test
        void resolveNameReadsTheClaimingFactionsOwnName() {
            // A claim bloc id is a plain faction id, so the label is that faction's display name in
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
    class ResolveBlocPicker {

        // The rules are forwarded to the (mocked) stats read, so their value never reaches assertion
        // here - any rules stand in where the seam demands them.
        private static final DominanceRules ANY_RULES =
            new DominanceRules(false,
                new BaseSizeWeighting(1.0, null, 1.0, 1.0),
                new StationWeighting(false, 1.0, 0.5, 0.5),
                new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

        // A claimant's stats: the view forwards them onto its option verbatim, so these arbitrary
        // numbers are only asserted to survive the pass unchanged. Non-zero claims, so the row they
        // ride on is an ordinary full-strength one.
        private static final ClaimStats ANY_CLAIMANT_STATS = new ClaimStats(2, 7);

        @Test
        void resolveBlocPickerCarriesEachClaimantsCrestShortNameAndStats() {
            // A claiming bloc becomes an option carrying its crest, short name, and the claim stats the
            // fold computed for it, so the option reads exactly as the picker row will draw and sort it.
            var sectorMock = mock(SectorAPI.class);

            when(stubNamedFaction(sectorMock, "hegemony", "Hegemony").getCrest())
                .thenReturn("graphics/hegemony_crest.png");

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any(), any()))
                    .thenReturn(Map.of("hegemony", ANY_CLAIMANT_STATS));

                assertThat(ClaimsView.INSTANCE.resolveBlocPicker(sectorMock, ANY_RULES, false).items())
                    .containsExactly(new RankedBloc<>(
                        new SelectableBloc(
                            "hegemony",
                            "Hegemony",
                            "graphics/hegemony_crest.png"),
                        ANY_CLAIMANT_STATS));
            }
        }

        @Test
        void resolveBlocPickerOffersAClaimantHoldingNoColonyAnywhere() {
            // The gate is claim presence, not market presence: a faction claiming territory while
            // holding nothing paints on this layer, so it must be spotlightable even at a market size
            // of zero.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "luddic_path", "Path");

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any(), any()))
                    .thenReturn(Map.of("luddic_path", new ClaimStats(1, 0)));

                assertThat(ClaimsView.INSTANCE.resolveBlocPicker(sectorMock, ANY_RULES, false).items())
                    .extracting(RankedBloc::itemId)
                    .containsExactly("luddic_path");
            }
        }

        @Test
        void resolveBlocPickerOffersABlocThatHoldsColoniesButClaimsNothing() {
            // A faction the player can plainly see going unlisted reads as the map having forgotten
            // it, so a colony holder that claims nowhere is offered rather than dropped. What says
            // it paints nothing here is the row itself: it carries a claim count of zero, which is
            // what its metrics read back from.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");
            stubNamedFaction(sectorMock, "tritachyon", "Tri-Tachyon");

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any(), any()))
                    .thenReturn(Map.of(
                        "hegemony", new ClaimStats(1, 0),
                        "tritachyon", new ClaimStats(0, 40)));

                assertThat(ClaimsView.INSTANCE.resolveBlocPicker(sectorMock, ANY_RULES, false).items())
                    .extracting(RankedBloc::itemId)
                    .containsExactlyInAnyOrder("hegemony", "tritachyon");
            }
        }

        @Test
        void resolveBlocPickerRecedesTheRowOfABlocThatClaimsNothing() {
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

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any(), any()))
                    .thenReturn(statsByBlocId);

                assertThat(ClaimsView.INSTANCE.resolveBlocPicker(sectorMock, ANY_RULES, false).items())
                    .extracting(RankedBloc::itemId, RankedBloc::isDimmed)
                    .containsExactly(
                        tuple("hegemony", false),
                        tuple("tritachyon", true));
            }
        }

        @Test
        void resolveBlocPickerKeepsTheBlocsInTheFoldsWalkOrder() {
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

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any(), any()))
                    .thenReturn(statsByBlocId);

                assertThat(ClaimsView.INSTANCE.resolveBlocPicker(sectorMock, ANY_RULES, false).items())
                    .extracting(RankedBloc::itemId)
                    .containsExactly("hegemony", "tritachyon", "persean");
            }
        }

        @Test
        void resolveBlocPickerOffersNoItemsWhenTheFoldSurfacesNothing() {
            // A bloc that neither claims nor holds anything never reaches the fold, so a sector with
            // none of either offers no options - the picker draws no controls at all - and a stale
            // saved selection heals to none.
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any(), any()))
                    .thenReturn(Map.of());

                assertThat(ClaimsView.INSTANCE.resolveBlocPicker(sectorMock, ANY_RULES, false).items())
                    .isEmpty();
            }
        }

        @Test
        void resolveBlocPickerRanksItsBlocsByTheClaimVocabulary() {
            // The view answers the list and the modes together, so the numbers its blocs carry and the
            // metrics the sort selector offers can never drift apart - this layer is painted by the
            // claim mechanic, so claims is what the picker ranks by rather than domination.
            var sectorMock = mock(SectorAPI.class);

            try (var aggregatorMock = mockStatic(ClaimStatsAggregator.class)) {

                aggregatorMock.when(() -> ClaimStatsAggregator.aggregateClaimStats(any(), any(), any()))
                    .thenReturn(Map.of());

                assertThat(ClaimsView.INSTANCE.resolveBlocPicker(sectorMock, ANY_RULES, false)
                        .sortModes())
                    .isEqualTo(ClaimSortMode.MODES);
            }
        }

        // Stubs a faction the sector resolves by id under a short display name - the two reads the
        // shared option assembly makes of every listed bloc, so a test that only cares which blocs
        // survive the gate can name one in a line. Returned so a test that also cares about the crest
        // stubs it on the same mock.
        private static FactionAPI stubNamedFaction(
                SectorAPI sectorMock,
                String factionId,
                String displayName) {

            var factionMock = mock(FactionAPI.class);

            when(sectorMock.getFaction(factionId))
                .thenReturn(factionMock);
            when(factionMock.getDisplayName())
                .thenReturn(displayName);

            return factionMock;
        }
    }
}
