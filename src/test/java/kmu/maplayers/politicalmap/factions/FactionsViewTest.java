package kmu.maplayers.politicalmap.factions;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.base.refresh.PoliticalMapRefresh;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.base.politics.BlocStats;
import kmu.maplayers.politicalmap.base.politics.BlocStatsAggregator;
import kmu.maplayers.politicalmap.base.politics.ownership.ClaimAugmentedOwnershipProvider;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
    private static final OwnershipGrouping ANY_GROUPING = OwnershipGrouping.identity();

    @Nested
    class ResolveGrouping {

        @Test
        void resolveGroupingReturnsTheIdentityGrouping() {
            // Every faction is its own bloc, so the pipeline resolves plain faction ownership.
            assertThat(FactionsView.INSTANCE.resolveGrouping())
                    .isSameAs(OwnershipGrouping.identity());
        }
    }

    @Nested
    class ResolveOwnershipProvider {

        @Test
        void resolveOwnershipProviderReturnsTheDefaultProvider() {
            // The faction view resolves ownership no differently from the pipeline's default -
            // each system's dominant owner, extended with the systems it merely claims - so it
            // inherits the shared claim-augmented default rather than supplying one of its own.
            assertThat(FactionsView.INSTANCE.resolveOwnershipProvider())
                    .isSameAs(ClaimAugmentedOwnershipProvider.INSTANCE);
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
            PoliticalMapRefresh.requestAllianceRefresh();

            assertThat(FactionsView.INSTANCE.getContentRevision()).isEqualTo(before);
        }
    }

    @Nested
    class ShouldUseIndependentStyle {

        @Test
        void shouldUseIndependentStyleIsTrueForIndependentSpace() {
            assertThat(FactionsView.INSTANCE.shouldUseIndependentStyle(
                    Factions.INDEPENDENT, ANY_GROUPING, BlocStyleAdjustment.NONE)).isTrue();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForACoreFaction() {
            assertThat(FactionsView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony", ANY_GROUPING, BlocStyleAdjustment.NONE)).isFalse();
        }

        @Test
        void shouldUseIndependentStyleIsTrueForACoreFactionWhenDesaturated() {
            // A faction the filter recede has desaturated reads as background ground, so it takes
            // the independent borders and seams paired with the desaturation palette - the same
            // classification the alliances view makes for a desaturated non-allied bloc.
            assertThat(FactionsView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony", ANY_GROUPING, new BlocStyleAdjustment(0.3, true))).isTrue();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForACoreFactionWhenOnlyMuted() {
            // Muting dims a bloc but does not desaturate it, so a merely muted faction keeps its
            // faction bundle: dimming alone never swaps border weight or the palette slot.
            assertThat(FactionsView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony", ANY_GROUPING, new BlocStyleAdjustment(0.3, false))).isFalse();
        }
    }

    @Nested
    class ResolveBlocStyleAdjustment {

        @Test
        void resolveBlocStyleAdjustmentIsNoneForAnyBloc() {
            // The faction view adjusts no bloc - a core faction and independent space alike
            // draw exactly as classified, so the pipeline has nothing to dim or recolour.
            assertThat(FactionsView.INSTANCE.resolveBlocStyleAdjustment("hegemony", ANY_GROUPING))
                    .isEqualTo(BlocStyleAdjustment.NONE);
            assertThat(FactionsView.INSTANCE.resolveBlocStyleAdjustment(
                    Factions.INDEPENDENT, ANY_GROUPING)).isEqualTo(BlocStyleAdjustment.NONE);
        }
    }

    @Nested
    class ResolveName {

        @Test
        void resolveNameReadsTheLongNameForTheFullFormat() {
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction("hegemony")).thenReturn(factionMock);
            when(factionMock.getDisplayNameLong()).thenReturn("The Hegemony");

            assertThat(FactionsView.INSTANCE.resolveName("hegemony", ANY_GROUPING, sectorMock,
                    FactionNameFormatChoice.FULL)).isEqualTo("The Hegemony");
        }

        @Test
        void resolveNameReadsTheShortNameForTheShortFormat() {
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction("hegemony")).thenReturn(factionMock);
            when(factionMock.getDisplayName()).thenReturn("Hegemony");

            assertThat(FactionsView.INSTANCE.resolveName("hegemony", ANY_GROUPING, sectorMock,
                    FactionNameFormatChoice.SHORT)).isEqualTo("Hegemony");
        }

        @Test
        void resolveNameIsNullWhenTheFactionDoesNotResolve() {
            // A bloc id with no faction behind it carries no name; the label fit then sizes
            // its stand-in band instead of drawing a name.
            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getFaction("ghost")).thenReturn(null);

            assertThat(FactionsView.INSTANCE.resolveName("ghost", ANY_GROUPING, sectorMock,
                    FactionNameFormatChoice.FULL)).isNull();
        }
    }

    @Nested
    class ResolveSelectableBlocs {

        // The rules are forwarded to the (mocked) stats read, so their value never reaches assertion
        // here - any rules stand in where the seam demands them.
        private static final DominanceRules ANY_RULES =
                new DominanceRules(false,
                        new BaseSizeWeighting(1.0, null, 1.0, 1.0),
                        new StationWeighting(false, 1.0, 0.5, 0.5),
                        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

        // The view forwards a present bloc's stats onto its option verbatim, so any stats value
        // stands in - these arbitrary numbers are only asserted to survive the pass unchanged.
        private static final BlocStats ANY_STATS = new BlocStats(3, 2, 5000, 7);

        @Test
        void resolveSelectableBlocsCarriesEachPresentFactionsCrestShortNameAndStats() {
            // Every present faction becomes an option carrying its crest, short name, and the stats the
            // shared read computed for it, so the option reads exactly as the picker row will draw and
            // sort it. The presence gate is the shared stats read's job, stubbed here to one faction.
            var sectorMock = mock(SectorAPI.class);
            var hegemonyMock = mock(FactionAPI.class);
            when(sectorMock.getFaction("hegemony")).thenReturn(hegemonyMock);
            when(hegemonyMock.getCrest()).thenReturn("graphics/hegemony_crest.png");
            when(hegemonyMock.getDisplayName()).thenReturn("Hegemony");

            try (MockedStatic<BlocStatsAggregator> aggregatorMock =
                    mockStatic(BlocStatsAggregator.class)) {
                aggregatorMock.when(() -> BlocStatsAggregator.aggregateBlocStats(any(), any())).thenReturn(Map.of("hegemony", ANY_STATS));

                assertThat(FactionsView.INSTANCE.resolveSelectableBlocs(sectorMock, ANY_RULES, false))
                        .containsExactly(new SelectableBloc(
                                "hegemony", "Hegemony", "graphics/hegemony_crest.png", ANY_STATS));
            }
        }

        @Test
        void resolveSelectableBlocsKeepsAFactionWithNoCrestAsANullCrestOption() {
            // A faction with no authored crest is still selectable - its option just carries a null
            // crest path and the row draws its name alone, rather than being dropped.
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction("luddic_path")).thenReturn(factionMock);
            when(factionMock.getCrest()).thenReturn(null);
            when(factionMock.getDisplayName()).thenReturn("Path");

            try (MockedStatic<BlocStatsAggregator> aggregatorMock =
                    mockStatic(BlocStatsAggregator.class)) {
                aggregatorMock.when(() -> BlocStatsAggregator.aggregateBlocStats(any(), any()))
                        .thenReturn(Map.of("luddic_path", ANY_STATS));

                assertThat(FactionsView.INSTANCE.resolveSelectableBlocs(sectorMock, ANY_RULES, false))
                        .containsExactly(new SelectableBloc("luddic_path", "Path", null, ANY_STATS));
            }
        }

        @Test
        void resolveSelectableBlocsIsEmptyWhenNoBlocIsPresent() {
            // With no present bloc the picker offers no options and a stale saved selection heals to
            // none.
            var sectorMock = mock(SectorAPI.class);

            try (MockedStatic<BlocStatsAggregator> aggregatorMock =
                    mockStatic(BlocStatsAggregator.class)) {
                aggregatorMock.when(() -> BlocStatsAggregator.aggregateBlocStats(any(), any())).thenReturn(Map.of());

                assertThat(FactionsView.INSTANCE.resolveSelectableBlocs(sectorMock, ANY_RULES, false))
                        .isEmpty();
            }
        }
    }
}
