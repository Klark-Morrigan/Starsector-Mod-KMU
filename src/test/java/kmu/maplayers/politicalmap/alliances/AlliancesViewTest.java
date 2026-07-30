package kmu.maplayers.politicalmap.alliances;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.refresh.PoliticalMapRefresh;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.base.politics.BlocStats;
import kmu.maplayers.politicalmap.base.politics.BlocStatsAggregator;
import kmu.maplayers.politicalmap.base.politics.ownership.ClaimAugmentedOwnershipProvider;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

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
    private static final OwnershipGrouping ALLIANCE_GROUPING = new OwnershipGrouping(
            Map.of("rebels", "rebel_pact", "pirates", "rebel_pact"),
            Map.of("rebel_pact", "rebels"),
            Map.of("rebel_pact", "Rebel Pact"));

    @Nested
    class GetId {

        @Test
        void getIdIsTheFrozenAlliancesId() {
            // Frozen: renaming it silently resets any save that selected this view to the default.
            assertThat(AlliancesView.INSTANCE.getId()).isEqualTo("alliances");
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
            PoliticalMapRefresh.requestAllianceRefresh();

            assertThat(AlliancesView.INSTANCE.getContentRevision()).isNotEqualTo(before);
        }

        @Test
        void getContentRevisionShiftsWhenTheRecedeStyleRevisionMoves() {
            // A Mute/Desaturate flip is the view's other live input: those sidebar-only toggles
            // never move settingsRevision, so the recede-style revision must fold in here for a flip
            // to repaint the overlay live.
            var before = AlliancesView.INSTANCE.getContentRevision();
            PoliticalMapRefresh.requestRecedeStyleRefresh();

            assertThat(AlliancesView.INSTANCE.getContentRevision()).isNotEqualTo(before);
        }
    }

    @Nested
    class ResolveGrouping {

        @Test
        void resolveGroupingFallsBackToIdentityWhenNexIsAbsent() {
            // Without Nex the gate returns the identity grouping, so the view degrades to the faction
            // grouping rather than touching any exerelin class.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubNexEnabled(globalMock, false);

                assertThat(AlliancesView.INSTANCE.resolveGrouping())
                        .isSameAs(OwnershipGrouping.identity());
            }
        }
    }

    @Nested
    class ResolveOwnershipProvider {

        @Test
        void resolveOwnershipProviderReturnsTheDefaultProvider() {
            // The alliances view rolls factions into alliance blocs through its grouping, but the
            // ownership source underneath is the same dominant-owner resolution the faction view
            // uses - extended with claimed systems - so it inherits the shared claim-augmented
            // default rather than supplying one of its own.
            assertThat(AlliancesView.INSTANCE.resolveOwnershipProvider())
                    .isSameAs(ClaimAugmentedOwnershipProvider.INSTANCE);
        }
    }

    @Nested
    class ShouldUseIndependentStyle {

        // The two adjustments the style test discriminates on: one that desaturates the bloc and one
        // that only dims it. The mute multiplier is arbitrary - the test proves only desaturation
        // moves the bundle.
        private static final BlocStyleAdjustment DESATURATED = new BlocStyleAdjustment(0.3, true);
        private static final BlocStyleAdjustment MUTED_ONLY = new BlocStyleAdjustment(0.3, false);

        @Test
        void shouldUseIndependentStyleIsTrueForIndependentSpace() {
            // Genuine independent space always takes the independent style, exactly as the faction
            // view classifies it - short-circuiting before the adjustment is even read.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    Factions.INDEPENDENT, ALLIANCE_GROUPING, BlocStyleAdjustment.NONE)).isTrue();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForAnAllianceBlocEvenWhenDesaturated() {
            // An alliance always paints in the full faction style so it stands out, whatever the
            // adjustment says.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    "rebel_pact", ALLIANCE_GROUPING, DESATURATED)).isFalse();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForALoneFactionWhenNotDesaturated() {
            // Undesaturated, a non-allied faction keeps its own faction style, so it reads exactly as
            // the faction view draws it.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony", ALLIANCE_GROUPING, BlocStyleAdjustment.NONE)).isFalse();
        }

        @Test
        void shouldUseIndependentStyleIsFalseForALoneFactionThatOnlyMutes() {
            // Muting only dims the active style through the opacity modifier; it never swaps the
            // bundle, so a merely dimmed faction keeps its faction borders and seams.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony", ALLIANCE_GROUPING, MUTED_ONLY)).isFalse();
        }

        @Test
        void shouldUseIndependentStyleIsTrueForALoneFactionWhenDesaturated() {
            // Desaturate makes a non-allied faction adopt the independent style - its independent
            // borders and seams, not just an independent recolour over the faction ones. The test
            // reads the passed adjustment, so it holds however that desaturation was asked for: this
            // view's own toggle, or the filter recede unioned in upstream. Nothing here touches
            // sector memory, since the view no longer resolves the choice a second time.
            assertThat(AlliancesView.INSTANCE.shouldUseIndependentStyle(
                    "hegemony", ALLIANCE_GROUPING, DESATURATED)).isTrue();
        }
    }

    @Nested
    class ResolveBlocStyleAdjustment {

        // The muted modifier and the two toggles the non-allied recede set is driven to, so a test
        // proves the view returns exactly what that set resolves rather than composing its own.
        private static final double MUTED_MODIFIER = 0.3;
        private static final BlocStyleAdjustment RECEDED = new BlocStyleAdjustment(MUTED_MODIFIER, true);

        @Test
        void resolveBlocStyleAdjustmentIsNoneForAnAllianceBlocEvenWhenGroundRecedes() {
            // An alliance keeps its full colour: the view gates it to NONE before its non-allied
            // recede set is consulted, so recede can never dim or desaturate an alliance - and no
            // memory is touched.
            assertThat(AlliancesView.INSTANCE.resolveBlocStyleAdjustment(
                    "rebel_pact", ALLIANCE_GROUPING)).isEqualTo(BlocStyleAdjustment.NONE);
        }

        @Test
        void resolveBlocStyleAdjustmentTakesTheNonAlliedRecedeForANonAllianceBloc() {
            // A non-allied faction is background ground, so the view returns exactly what its own
            // non-allied recede set resolves - the one adjustment every faction outside an alliance
            // takes, driven here through that set's mute and desaturate keys.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                            mockStatic(SectorMemoryAccess.class);
                    MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(ALLIANCE_MUTE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(ALLIANCE_MUTE_KEY)).thenReturn(true);
                when(memoryMock.contains(ALLIANCE_DESATURATE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(ALLIANCE_DESATURATE_KEY)).thenReturn(true);
                settingsMock.when(KmuLunaSettings::getPoliticalMapAllianceMutedOpacityModifier)
                        .thenReturn(MUTED_MODIFIER);

                assertThat(AlliancesView.INSTANCE.resolveBlocStyleAdjustment(
                        "hegemony", ALLIANCE_GROUPING)).isEqualTo(RECEDED);
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

            assertThat(AlliancesView.INSTANCE.resolveName("rebel_pact", ALLIANCE_GROUPING, sectorMock,
                    FactionNameFormatChoice.FULL)).isEqualTo("Rebel Pact");
        }

        @Test
        void resolveNameReadsTheFactionNameForALoneFaction() {
            // A non-alliance bloc is a lone faction, named as the faction view names it.
            var sectorMock = mock(SectorAPI.class);
            var factionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction("hegemony")).thenReturn(factionMock);
            when(factionMock.getDisplayNameLong()).thenReturn("The Hegemony");

            assertThat(AlliancesView.INSTANCE.resolveName("hegemony", ALLIANCE_GROUPING, sectorMock,
                    FactionNameFormatChoice.FULL)).isEqualTo("The Hegemony");
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

        // The view forwards a surviving alliance's stats onto its option verbatim, so any stats value
        // stands in - these arbitrary numbers are only asserted to survive the pass unchanged.
        private static final BlocStats ANY_STATS = new BlocStats(4, 3, 8000, 12);

        @Test
        void resolveSelectableBlocsOffersOnlyAllianceBlocsCrestedFromTheLeadMemberWithStats() {
            // Under the alliances view only an alliance is a filter target: a present lone faction is
            // dropped, and the alliance option reads its name off the grouping, carries its lead
            // (colour) member's crest, and forwards the stats the shared read computed for it.
            // resolveGrouping samples Nex live, so it is stubbed to the alliance grouping the (mocked)
            // stats read is keyed against; the presence gate is the shared read's job, so both blocs
            // arrive already present.
            var view = spy(AlliancesView.INSTANCE);
            doReturn(ALLIANCE_GROUPING).when(view).resolveGrouping();
            var sectorMock = mock(SectorAPI.class);
            // "rebels" is rebel_pact's colour faction in ALLIANCE_GROUPING, so its crest is the one
            // the alliance row draws.
            var leadFactionMock = mock(FactionAPI.class);
            when(sectorMock.getFaction("rebels")).thenReturn(leadFactionMock);
            when(leadFactionMock.getCrest()).thenReturn("graphics/rebels_crest.png");

            try (MockedStatic<BlocStatsAggregator> aggregatorMock =
                    mockStatic(BlocStatsAggregator.class)) {
                aggregatorMock.when(() -> BlocStatsAggregator.aggregateBlocStats(any(), any()))
                        .thenReturn(Map.of("rebel_pact", ANY_STATS, "hegemony", BlocStats.EMPTY));

                assertThat(view.resolveSelectableBlocs(sectorMock, ANY_RULES, false))
                        .containsExactly(new SelectableBloc(
                                "rebel_pact", "Rebel Pact", "graphics/rebels_crest.png", ANY_STATS));
            }
        }
    }

    private static void stubNexEnabled(MockedStatic<Global> globalMock, boolean isEnabled) {
        var settingsMock = mock(SettingsAPI.class);
        var modManagerMock = mock(ModManagerAPI.class);
        globalMock.when(Global::getSettings).thenReturn(settingsMock);
        when(settingsMock.getModManager()).thenReturn(modManagerMock);
        when(modManagerMock.isModEnabled(NEXERELIN_MOD_ID)).thenReturn(isEnabled);
    }
}
