package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.controls.specs.ControlSpec;
import kmlib.testfixtures.starsector.StubbedGlobalLogger;
import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.sidebar.BodyControlTarget;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.picker.OwnerPresenceIndex;
import kmu.maplayers.ownermap.picker.OwnerStatsRead;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.ownermap.picker.SelectableOwner;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.maplayers.ownermap.sidebar.RecedeControl;
import kmu.maplayers.politicalmap.dominance.DominanceStats;
import kmu.maplayers.politicalmap.dominance.DominanceStatsAggregator;
import kmu.maplayers.politicalmap.dominance.tooltip.SystemDominationTooltip;
import kmu.maplayers.politicalmap.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.StationWeighting;
import kmu.mods.nexerelin.NexerelinAlliances;
import kmu.settings.KmuOwnerMapStyleSettings;
import kmu.util.KmuPoliticalMapStringKeys;
import kmu.util.KmuStringKeys;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Pins what the alliances view decides: its frozen ID and label, the alliance reading over the
 * grouping it is handed, its own backdrop toggles, and its body controls. The grouping is sampled
 * from Nexerelin, so its resolution is pinned only for the Nex-absent fallback here; a live alliance
 * set is the in-game test's concern.
 *
 * <p>Its body controls are pinned on the refresh board they are handed rather than on one resolved
 * for them.
 *
 * <p>Beside them, what the view offers the player around the fills: only an alliance as a
 * spotlight target, matching its role of grouping holders by alliance, and a domination box of its
 * own on hover, so the box ranks under the grouping this view painted with.
 *
 * <p>What every view the domination contest paints answers alike is {@link DominancePaintedView}'s,
 * and what every view of the layer answers alike is {@link PoliticalHolderView}'s, each pinned once
 * by its own suite.
 */
final class AlliancesViewTests {

    private static final String NEXERELIN_MOD_ID = "nexerelin";

    // An alliance grouping with one alliance bloc "rebel_pact" fusing two members, coloured off the
    // sorted-first member and named "Rebel Pact"; a faction not in the map stays its own lone bloc.
    private static final HolderGrouping ALLIANCE_GROUPING = new HolderGrouping(
        Map.of("rebels", "rebel_pact", "pirates", "rebel_pact"),
        Map.of("rebel_pact", "rebels"),
        Map.of("rebel_pact", "Rebel Pact"));

    @Nested
    class GetId {

        @Test
        void isTheFrozenAlliancesId() {
            // Frozen: renaming it silently resets any save that selected this view to the default.
            assertThat(AlliancesView.INSTANCE.getId())
                .isEqualTo("alliances");
        }
    }

    @Nested
    class GetSegmentLabelKey {

        @Test
        void isTheAlliancesRadioLabel() {

            assertThat(AlliancesView.INSTANCE.getSegmentLabelKey())
                .isEqualTo(KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_ALLIANCES);
        }
    }

    @Nested
    class ResolveGrouping {

        @Test
        void fallsBackToIdentityWhenNexIsAbsent() {
            // Without Nex the gate returns the identity grouping, so the view degrades to the faction
            // grouping rather than touching any exerelin class.
            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                stubNexEnabled(globalMock, false);

                assertThat(AlliancesView.INSTANCE.resolveGrouping())
                    .isSameAs(HolderGrouping.identity());
            }
        }
    }

    @Nested
    class GetViewBodyControls {

        @Test
        void handsTheNonAlliedSetCaptionAndPanelToTheSharedRecedeControl() {
            // The checkboxes repaint by raising a signal and store under the panel they were placed
            // on, so the panel has to reach them from the tab that placed them. Dropped here, a flip
            // would repaint a map the player is not looking at, or land on the other panel. The
            // caption is pinned by its key, not its live text.
            var passedTarget = new BodyControlTarget(
                new MapLayerRefreshBoard(),
                ScreenMemoryScopes.createStandInScreen());
            var builtControls = List.<ControlSpec>of();

            try (var controlMock = mockStatic(RecedeControl.class);
                    var stringsMock = mockStatic(KmuStringKeys.class)) {

                stringsMock
                    .when(() -> KmuStringKeys.get(KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION))
                    .thenReturn("Non-allied factions are");
                controlMock
                    .when(() -> RecedeControl.buildControls(
                        AlliancesView.NON_ALLIED_RECEDE,
                        "Non-allied factions are",
                        passedTarget))
                    .thenReturn(builtControls);

                assertThat(AlliancesView.INSTANCE.getViewBodyControls(passedTarget))
                    .isSameAs(builtControls);
            }
        }
    }

    @Nested
    class ResolveOwnerReading {

        @Test
        void namesAnAllianceByItsGroupingsName() {
            // One answer read back, enough to show the reading is over the handed grouping and names
            // an alliance as that grouping does. The rest of what it answers is pinned by that
            // reading's own suite.
            assertThat(AlliancesView.INSTANCE
                    .resolveOwnerReading(mock(SectorAPI.class), ALLIANCE_GROUPING)
                    .resolveName("rebel_pact", OwnerNameFormatChoice.FULL))
                .isEqualTo("Rebel Pact");
        }
    }

    @Nested
    class ResolveViewReading {

        @Test
        void namesTheAlliancesOfTheLiveAllianceSet() {
            // The view paints by the live alliance set, so the reading names the alliances that set
            // holds. That the source folds under that same set is the shared assembly's, pinned once.
            try (var alliancesMock = mockStatic(NexerelinAlliances.class)) {
                alliancesMock
                    .when(NexerelinAlliances::resolveGrouping)
                    .thenReturn(ALLIANCE_GROUPING);

                assertThat(AlliancesView.INSTANCE
                        .resolveViewReading(mock(SectorAPI.class))
                        .reading()
                        .resolveName("rebel_pact", OwnerNameFormatChoice.FULL))
                    .isEqualTo("Rebel Pact");
            }
        }
    }

    @Nested
    class ResolveViewRecedeAdjustment {

        // The non-allied backdrop's slots under the stand-in screen, pinned as literals: each is a key
        // a save already holds, so a rename must break this test rather than reset every save's choice.
        private static final String NON_ALLIED_MUTE_KEY = "$kmu_political_alliance_recede_mute_test";
        private static final String NON_ALLIED_DESATURATE_KEY =
            "$kmu_political_alliance_recede_desaturate_test";

        // A modifier the Mute toggle scales by, distinct from the shipped one so reaching it is seen.
        private static final double MUTED_MODIFIER = 0.3;

        @Test
        void readsTheNonAlliedBackdropsOwnToggles() {
            // The view's own backdrop, off its own frozen keys: muted and left in colour here, which
            // neither the shipped defaults nor the layer's filter recede would answer.
            try (var sectorMemoryFake = new SectorMemoryFake();
                    var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getOwnerMapMutedOpacityModifier)
                    .thenReturn(MUTED_MODIFIER);

                sectorMemoryFake.storeValue(NON_ALLIED_MUTE_KEY, true);
                sectorMemoryFake.storeValue(NON_ALLIED_DESATURATE_KEY, false);

                assertThat(AlliancesView.INSTANCE.resolveViewRecedeAdjustment(
                        ScreenMemoryScopes.createStandInScreen()))
                    .isEqualTo(new ElementStyleAdjustment(MUTED_MODIFIER, false));
            }
        }
    }

    @Nested
    class ResolveHoverTooltip {

        @Test
        void offersTheDominationBox() {
            // The box explaining the fills this view paints, which are painted by domination.
            assertThat(AlliancesView.INSTANCE.resolveHoverTooltip())
                .containsInstanceOf(SystemDominationTooltip.class);
        }

        @Test
        void offersABoxOfItsOwnRatherThanTheFactionViews() {
            // Each view's box ranks under that view's own grouping, so the one hovered here nests
            // members under their alliance; sharing the faction view's would list them flat.
            assertThat(AlliancesView.INSTANCE.resolveHoverTooltip().orElseThrow())
                .isNotSameAs(FactionsView.INSTANCE.resolveHoverTooltip().orElseThrow());
        }
    }

    @Nested
    class ResolveSelectableBlocGate {

        @Test
        void admitsAnAllianceBloc() {

            assertThat(AlliancesView.INSTANCE.resolveSelectableBlocGate(ALLIANCE_GROUPING)
                    .test("rebel_pact"))
                .isTrue();
        }

        @Test
        void turnsAwayALoneFaction() {
            // Only an alliance is a spotlight target here; a faction outside every alliance is
            // present on the map but not offered in this view's picker.
            assertThat(AlliancesView.INSTANCE.resolveSelectableBlocGate(ALLIANCE_GROUPING)
                    .test("hegemony"))
                .isFalse();
        }

        @Test
        void turnsAwayEveryBlocWhenNoAllianceExists() {
            // With no alliance formed every bloc is a lone faction, so the picker offers nothing.
            assertThat(AlliancesView.INSTANCE.resolveSelectableBlocGate(HolderGrouping.identity())
                    .test("hegemony"))
                .isFalse();
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

        // The view forwards a surviving alliance's stats onto its option verbatim, so any stats value
        // stands in - these arbitrary numbers are only asserted to survive the pass unchanged.
        private static final DominanceStats ANY_STATS = new DominanceStats(4, 3, 8000, 12);

        @Test
        void offersOnlyAllianceBlocsCrestedFromTheLeadMemberWithStats() {
            // Under the alliances view only an alliance is a filter target: a present lone faction is
            // dropped, and the alliance option reads its name off the grouping, carries its lead
            // (colour) member's crest, and forwards the stats the shared read computed for it.
            // resolveGrouping samples Nex live, so it is stubbed to the alliance grouping the (mocked)
            // stats read is keyed against; the presence gate is the shared read's job, so both blocs
            // arrive already present.
            var viewMock = spy(AlliancesView.INSTANCE);

            doReturn(ALLIANCE_GROUPING)
                .when(viewMock)
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
                    .thenReturn(new OwnerStatsRead<>(
                        Map.of("rebel_pact", ANY_STATS, "hegemony", DominanceStats.EMPTY),
                        OwnerPresenceIndex.EMPTY));

                assertThat(viewMock.resolveBlocPickerRead(sectorMock, ANY_RULES, UNDER_THE_FOG).picker().items())
                    .containsExactly(new RankedOwner<>(
                        new SelectableOwner(
                            "rebel_pact",
                            "Rebel Pact",
                            "graphics/rebels_crest.png"),
                        ANY_STATS));
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
