package kmu.maplayers.politicalmap.dominance.alliances;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.DominanceSortMode;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;
import kmu.maplayers.politicalmap.base.politics.DominanceStatsAggregator;
import kmu.maplayers.politicalmap.base.politics.holders.ClaimAugmentedHolderProvider;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefreshSignal;
import kmu.settings.KmuPoliticalMapSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static kmlib.starsector.colonies.ColonyVisibility.BASE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Pins the alliances view's render rules: an alliance bloc paints in full colour and reads its
 * alliance name, while a non-allied faction keeps its own faction style until Desaturate makes it
 * adopt the independent style bundle; Mute only dims the active style via the opacity modifier. A
 * non-allied faction is named exactly as the faction view would name that lone faction. The grouping
 * is sampled from Nexerelin,
 * so its resolution is pinned only for the Nex-absent fallback here; a live alliance set is the
 * in-game test's concern.
 */
final class AlliancesViewTest {

    private static final String NEXERELIN_MOD_ID = "nexerelin";

    // The alliances view's own non-allied recede set keys, driven here through sector memory so the
    // view's real recede reads are exercised. Pinned as literals: this set is what the view consults
    // for a non-allied faction, so a rename that would silently reset the choice breaks here.
    private static final String ALLIANCE_MUTE_KEY = "$kmu_political_alliance_recede_mute";
    private static final String ALLIANCE_DESATURATE_KEY = "$kmu_political_alliance_recede_desaturate";

    // An alliance grouping with one alliance bloc "rebel_pact" fusing two members, coloured off the
    // sorted-first member and named "Rebel Pact"; a faction not in the map stays its own lone bloc.
    private static final HolderGrouping ALLIANCE_GROUPING = new HolderGrouping(
        Map.of("rebels", "rebel_pact", "pirates", "rebel_pact"),
        Map.of("rebel_pact", "rebels"),
        Map.of("rebel_pact", "Rebel Pact"));

    @Nested
    class GetId {

        @Test
        void getIdIsTheFrozenAlliancesId() {
            // Frozen: renaming it silently resets any save that selected this view to the default.
            assertThat(AlliancesView.INSTANCE.getId())
                .isEqualTo("alliances");
        }
    }

    @Nested
    class GetContentRevision {

        @Test
        void getContentRevisionShiftsWhenTheAllianceRevisionMoves() {
            // The alliance set is one of the view's live inputs, so a membership change must shift
            // the revision - that is what folds it into the content token and repaints the view
            // without a reload.
            var before = AlliancesView.INSTANCE.getContentRevision();

            MapLayerRefresh.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);

            assertThat(AlliancesView.INSTANCE.getContentRevision())
                .isNotEqualTo(before);
        }

        @Test
        void getContentRevisionShiftsWhenTheRecedeStyleRevisionMoves() {
            // A Mute/Desaturate flip is the view's other live input: those sidebar-only toggles
            // never move settingsRevision, so the recede-style revision must fold in here for a flip
            // to repaint the overlay live.
            var before = AlliancesView.INSTANCE.getContentRevision();

            MapLayerRefresh.requestRefresh(MapLayerCommonRefreshSignal.RECEDE_STYLE);

            assertThat(AlliancesView.INSTANCE.getContentRevision())
                .isNotEqualTo(before);
        }
    }

    @Nested
    class ResolveGrouping {

        @Test
        void resolveGroupingFallsBackToIdentityWhenNexIsAbsent() {
            // Without Nex the gate returns the identity grouping, so the view degrades to the faction
            // grouping rather than touching any exerelin class.
            try (var globalMock = mockStatic(Global.class)) {

                stubNexEnabled(globalMock, false);

                assertThat(AlliancesView.INSTANCE.resolveGrouping())
                    .isSameAs(HolderGrouping.identity());
            }
        }
    }

    @Nested
    class ResolveHolderProvider {

        @Test
        void resolveHolderProviderReturnsTheDefaultProvider() {
            // The alliances view rolls factions into alliance blocs through its grouping, but the
            // holding source underneath is the same dominant-holder resolution the faction view
            // uses - extended with claimed systems - so it inherits the shared claim-augmented
            // default rather than supplying one of its own.
            assertThat(AlliancesView.INSTANCE.resolveHolderProvider())
                .isSameAs(ClaimAugmentedHolderProvider.INSTANCE);
        }
    }

    @Nested
    class ShouldUseIndependentStyle {

        // The two adjustments the style test discriminates on: one that desaturates the bloc and one
        // that only dims it. The mute multiplier is arbitrary - the test proves only desaturation
        // moves the bundle.
        private static final ElementStyleAdjustment DESATURATED = new ElementStyleAdjustment(0.3, true);
        private static final ElementStyleAdjustment MUTED_ONLY = new ElementStyleAdjustment(0.3, false);

        @Test
        void shouldUseIndependentStyleIsTrueForIndependentSpace() {
            // Genuine independent space always takes the independent style, exactly as the faction
            // view classifies it - short-circuiting before the adjustment is even read.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    Factions.INDEPENDENT,
                    ALLIANCE_GROUPING,
                    ElementStyleAdjustment.NONE))
                .isTrue();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForAnAllianceBlocEvenWhenDesaturated() {
            // An alliance always paints in the full faction style so it stands out, whatever the
            // adjustment says.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    "rebel_pact",
                    ALLIANCE_GROUPING,
                    DESATURATED))
                .isFalse();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForALoneFactionWhenNotDesaturated() {
            // Undesaturated, a non-allied faction keeps its own faction style, so it reads exactly as
            // the faction view draws it.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony",
                    ALLIANCE_GROUPING,
                    ElementStyleAdjustment.NONE))
                .isFalse();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForALoneFactionThatOnlyMutes() {
            // Muting only dims the active style through the opacity modifier; it never swaps the
            // bundle, so a merely dimmed faction keeps its faction borders and seams.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony",
                    ALLIANCE_GROUPING,
                    MUTED_ONLY))
                .isFalse();
        }

        @Test
        void shouldUseIndependentStyleIsTrueForALoneFactionWhenDesaturated() {
            // Desaturate makes a non-allied faction adopt the independent style - its independent
            // borders and seams, not just an independent recolour over the faction ones. The test
            // reads the passed adjustment, so it holds however that desaturation was asked for: this
            // view's own toggle, or the filter recede unioned in upstream. Nothing here touches
            // sector memory, since the view no longer resolves the choice a second time.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony",
                    ALLIANCE_GROUPING,
                    DESATURATED))
                .isTrue();
        }
    }

    @Nested
    class ResolveBlocStyleAdjustment {

        // The muted modifier and the two toggles the non-allied recede set is driven to, so a test
        // proves the view returns exactly what that set resolves rather than composing its own.
        private static final double MUTED_MODIFIER = 0.3;
        private static final ElementStyleAdjustment RECEDED = new ElementStyleAdjustment(MUTED_MODIFIER, true);

        // What an untouched save resolves to: recoloured at full opacity, since Desaturate defaults
        // on and Mute defaults off. A literal, so a flip of either default breaks this test.
        private static final ElementStyleAdjustment DESATURATED_ONLY = new ElementStyleAdjustment(1.0, true);

        @Test
        void resolveBlocStyleAdjustmentIsNoneForAnAllianceBlocEvenWhenTheBackdropRecedes() {
            // An alliance keeps its full colour: the view gates it to NONE before its non-allied
            // recede set is consulted, so recede can never dim or desaturate an alliance - and no
            // memory is touched.
            assertThat(AlliancesView.INSTANCE.resolveBlocStyleAdjustment(
                    "rebel_pact",
                    ALLIANCE_GROUPING))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void resolveBlocStyleAdjustmentTakesTheNonAlliedRecedeForANonAllianceBloc() {
            // A non-allied faction is backdrop, so the view returns exactly what its own
            // non-allied recede set resolves - the one adjustment every faction outside an alliance
            // takes, driven here through that set's mute and desaturate keys.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class);
                    var settingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(ALLIANCE_MUTE_KEY))
                    .thenReturn(true);
                when(memoryMock.getBoolean(ALLIANCE_MUTE_KEY))
                    .thenReturn(true);
                when(memoryMock.contains(ALLIANCE_DESATURATE_KEY))
                    .thenReturn(true);
                when(memoryMock.getBoolean(ALLIANCE_DESATURATE_KEY))
                    .thenReturn(true);

                settingsMock
                    .when(KmuPoliticalMapSettings::getPoliticalMapAllianceMutedOpacityModifier)
                    .thenReturn(MUTED_MODIFIER);

                assertThat(AlliancesView.INSTANCE.resolveBlocStyleAdjustment(
                        "hegemony",
                        ALLIANCE_GROUPING))
                    .isEqualTo(RECEDED);
            }
        }

        @Test
        void resolveBlocStyleAdjustmentDesaturatesANonAlliedFactionOnAnUntouchedSave() {
            // The view opens with the alliances already reading as the figure: with neither key
            // stored, Desaturate's on default recolours a non-allied faction while Mute's off
            // default leaves it at full opacity. No settings mock is needed precisely because the
            // muted modifier goes unread while Mute is off.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(ALLIANCE_MUTE_KEY))
                    .thenReturn(false);
                when(memoryMock.contains(ALLIANCE_DESATURATE_KEY))
                    .thenReturn(false);

                assertThat(AlliancesView.INSTANCE.resolveBlocStyleAdjustment(
                        "hegemony",
                        ALLIANCE_GROUPING))
                    .isEqualTo(DESATURATED_ONLY);
            }
        }

        @Test
        void resolveBlocStyleAdjustmentIsNoneForEveryBlocWhenNoAllianceExists() {
            // No alliance means no figure, so receding would sink the whole sector rather than
            // isolate anything - the state a fresh Nex campaign opens in, before diplomacy has
            // formed a single alliance. The gate runs ahead of the recede set, so the on-by-default
            // Desaturate never reaches a bloc here and sector memory is not even read.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                assertThat(AlliancesView.INSTANCE.resolveBlocStyleAdjustment(
                        "hegemony",
                        HolderGrouping.identity()))
                    .isEqualTo(ElementStyleAdjustment.NONE);

                memoryAccessMock
                    .verifyNoInteractions();
            }
        }
    }

    @Nested
    class ResolveName {

        @Test
        void resolveNameReadsTheAllianceNameForAnAllianceBloc() {
            // An alliance bloc id is not a faction id, so its label comes from the grouping, not the
            // sector - which is therefore never consulted.
            var sectorMock = mock(SectorAPI.class);

            assertThat(AlliancesView.INSTANCE.resolveName(
                    "rebel_pact",
                    ALLIANCE_GROUPING,
                    sectorMock,
                    FactionNameFormatChoice.FULL))
                .isEqualTo("Rebel Pact");
        }

        @Test
        void resolveNameReadsTheFactionNameForALoneFaction() {
            // A non-alliance bloc is a lone faction, named as the faction view names it.
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);

            when(sectorMock.getFaction("hegemony"))
                .thenReturn(factionMock);

            when(factionMock.getDisplayNameLong())
                .thenReturn("The Hegemony");

            assertThat(AlliancesView.INSTANCE.resolveName(
                    "hegemony",
                    ALLIANCE_GROUPING,
                    sectorMock,
                    FactionNameFormatChoice.FULL))
                .isEqualTo("The Hegemony");
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

        // The view forwards a surviving alliance's stats onto its option verbatim, so any stats value
        // stands in - these arbitrary numbers are only asserted to survive the pass unchanged.
        private static final DominanceStats ANY_STATS = new DominanceStats(4, 3, 8000, 12);

        @Test
        void resolveBlocPickerOffersOnlyAllianceBlocsCrestedFromTheLeadMemberWithStats() {
            // Under the alliances view only an alliance is a filter target: a present lone faction is
            // dropped, and the alliance option reads its name off the grouping, carries its lead
            // (colour) member's crest, and forwards the stats the shared read computed for it.
            // resolveGrouping samples Nex live, so it is stubbed to the alliance grouping the (mocked)
            // stats read is keyed against; the presence gate is the shared read's job, so both blocs
            // arrive already present.
            var view = spy(AlliancesView.INSTANCE);

            doReturn(ALLIANCE_GROUPING)
                .when(view)
                .resolveGrouping();

            var sectorMock = mock(SectorAPI.class);

            // "rebels" is rebel_pact's colour faction in ALLIANCE_GROUPING, so its crest is the one
            // the alliance row draws.
            var leadFactionMock = mock(FactionAPI.class);

            when(sectorMock.getFaction("rebels"))
                .thenReturn(leadFactionMock);

            when(leadFactionMock.getCrest())
                .thenReturn("graphics/rebels_crest.png");

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock
                    .when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(Map.of("rebel_pact", ANY_STATS, "hegemony", DominanceStats.EMPTY));

                assertThat(view.resolveBlocPicker(sectorMock, ANY_RULES, BASE_FOG).items())
                    .containsExactly(new RankedBloc<>(
                        new SelectableBloc(
                            "rebel_pact",
                            "Rebel Pact",
                            "graphics/rebels_crest.png"),
                        ANY_STATS));
            }
        }

        @Test
        void resolveBlocPickerRanksItsBlocsByTheDominanceVocabulary() {
            // This view's blocs are folded sets of factions, but they carry the same metrics a lone
            // faction's do, so it ranks by the same vocabulary the factions view offers. Asserted
            // per view rather than once on the shared assembly, since which vocabulary a view's rows
            // can be read by is the view's own choice of what paints it.
            var view = spy(AlliancesView.INSTANCE);

            doReturn(ALLIANCE_GROUPING)
                .when(view)
                .resolveGrouping();

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock
                    .when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(Map.of());

                assertThat(view.resolveBlocPicker(mock(SectorAPI.class), ANY_RULES, BASE_FOG)
                        .sortModes())
                    .isEqualTo(DominanceSortMode.MODES);
            }
        }
    }

    private static void stubNexEnabled(MockedStatic<Global> globalMock, boolean isEnabled) {

        var settingsMock = mock(SettingsAPI.class);
        var modManagerMock = mock(ModManagerAPI.class);

        globalMock.when(Global::getSettings)
            .thenReturn(settingsMock);
            
        when(settingsMock.getModManager())
            .thenReturn(modManagerMock);

        when(modManagerMock.isModEnabled(NEXERELIN_MOD_ID))
            .thenReturn(isEnabled);
    }
}
