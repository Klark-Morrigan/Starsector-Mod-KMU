package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.ui.controls.specs.ControlSpec;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.picker.BlocPresenceIndex;
import kmu.maplayers.ownermap.picker.RankedBloc;
import kmu.maplayers.ownermap.picker.SelectableBloc;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.sidebar.BodyControlTarget;
import kmu.maplayers.politicalmap.dominance.DominanceStats;
import kmu.maplayers.politicalmap.dominance.DominanceStatsAggregator;
import kmu.maplayers.politicalmap.dominance.DominanceStatsRead;
import kmu.maplayers.politicalmap.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.holders.ClaimAugmentedHolderProvider;
import kmu.maplayers.politicalmap.refresh.PoliticalMapRefreshSignal;
import kmu.settings.KmuOwnerMapStyleSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.ownermap.holding.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.picker.BlocPresenceIndexFixtures.buildIndexOf;

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
 *
 * <p>Its two seams that take a refresh board are pinned on the board they are handed rather than on
 * one resolved for them: the revision it folds, and the body controls it contributes.
 */
final class AlliancesViewTest {

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
            var board = new MapLayerRefreshBoard();
            var before = AlliancesView.INSTANCE.getContentRevision(board);

            board.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);

            assertThat(AlliancesView.INSTANCE.getContentRevision(board))
                .isNotEqualTo(before);
        }

        @Test
        void getContentRevisionStandsStillWhenTheRecedeStyleRevisionMoves() {
            // A Mute/Desaturate flip is not folded here. The bake samples this view's non-allied
            // recede with every other preference and folds the values in, so a flip that leaves the
            // recede where it was must cost nothing - and one that moves it rebuilds through the
            // sampled value rather than through a counter that only says somebody clicked.
            var board = new MapLayerRefreshBoard();
            var before = AlliancesView.INSTANCE.getContentRevision(board);

            board.requestRefresh(MapLayerCommonRefreshSignal.RECEDE_STYLE);

            assertThat(AlliancesView.INSTANCE.getContentRevision(board))
                .isEqualTo(before);
        }

        @Test
        void getContentRevisionFoldsTheBoardItIsHandedRatherThanAnother() {
            // One stateless view answers for every sector, so the board handed in is the only thing
            // telling two sectors' asks apart. An alliance formed under one sector must move that
            // sector's number and leave the other's exactly where it was - a view folding an
            // ambient board instead would repaint whichever sector happened to be running.
            //
            // Made once, here, for the seam rather than per view: every view's cases above build a
            // board of their own, so a view resolving one instead of folding the argument already
            // fails them - a raise on a board nothing else reads would move no number at all. What
            // this adds is the second board those cases have no way to disagree with.
            var board = new MapLayerRefreshBoard();
            var otherBoard = new MapLayerRefreshBoard();
            var before = AlliancesView.INSTANCE.getContentRevision(board);
            var otherBefore = AlliancesView.INSTANCE.getContentRevision(otherBoard);

            board.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);

            assertThat(AlliancesView.INSTANCE.getContentRevision(board))
                .isNotEqualTo(before);
            assertThat(AlliancesView.INSTANCE.getContentRevision(otherBoard))
                .isEqualTo(otherBefore);
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
    class GetViewBodyControls {

        @Test
        void getViewBodyControlsHandsThePanelItWasGivenToTheRecedeAdapter() {
            // This view's Mute/Desaturate checkboxes repaint by raising a signal and store under the
            // panel they were placed on, so the panel has to reach them from the tab that placed them.
            // Dropped here - a delegation short enough to look incapable of losing anything - the
            // checkboxes would fall back to the running sector's board and whichever screen was up, and
            // a flip would repaint a map the player is not looking at, or land on the other panel.
            var passedTarget = new BodyControlTarget(
                new MapLayerRefreshBoard(),
                ScreenMemoryScopes.createStandInScreen());

            try (var controlsMock = mockStatic(AllianceBodyControls.class)) {

                var builtControls = List.<ControlSpec>of();

                controlsMock
                    .when(() -> AllianceBodyControls.buildControls(passedTarget))
                    .thenReturn(builtControls);

                assertThat(AlliancesView.INSTANCE.getViewBodyControls(passedTarget))
                    .isSameAs(builtControls);
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

        // The recede the bake sampled for this view's non-allied set, distinct from the identity so
        // a bloc that took it can be told from a bloc the gate spared.
        private static final ElementStyleAdjustment RECEDED = new ElementStyleAdjustment(0.3, true);

        // The picks a pass carrying that recede was baked under. Nothing else in the reading
        // reaches this decision, so the other four sit at the inert values.
        private static final ContentInputs RECEDING_INPUTS =
            ContentInputsFixtures.createInputsRecedingNonAllied(RECEDED);

        @Test
        void resolveBlocStyleAdjustmentIsNoneForAnAllianceBlocEvenWhenTheBackdropRecedes() {
            // An alliance keeps its full colour: the view gates it to NONE ahead of the recede the
            // bake sampled, so recede can never dim or desaturate an alliance.
            assertThat(AlliancesView.INSTANCE.resolveBlocStyleAdjustment(
                    "rebel_pact",
                    ALLIANCE_GROUPING,
                    RECEDING_INPUTS))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void resolveBlocStyleAdjustmentTakesTheSampledNonAlliedRecedeForANonAllianceBloc() {
            // A non-allied faction is backdrop, so the view hands back exactly the non-allied recede
            // the rebuild sampled - the one adjustment every faction outside an alliance takes.
            // Taken off the reading rather than off the stored toggles, so every cell of one rebuild
            // recedes by one answer and a flip mid-rebuild cannot split the backdrop in two.
            assertThat(AlliancesView.INSTANCE.resolveBlocStyleAdjustment(
                    "hegemony",
                    ALLIANCE_GROUPING,
                    RECEDING_INPUTS))
                .isEqualTo(RECEDED);
        }

        @Test
        void resolveBlocStyleAdjustmentIsNoneForEveryBlocWhenNoAllianceExists() {
            // No alliance means no figure, so receding would sink the whole sector rather than
            // isolate anything - the state a fresh Nex campaign opens in, before diplomacy has
            // formed a single alliance. The gate runs ahead of the sampled recede, so the
            // on-by-default Desaturate never reaches a bloc here.
            assertThat(AlliancesView.INSTANCE.resolveBlocStyleAdjustment(
                    "hegemony",
                    HolderGrouping.identity(),
                    RECEDING_INPUTS))
                .isEqualTo(ElementStyleAdjustment.NONE);
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
        void resolveViewRecedeAdjustmentReadsTheNonAlliedBackdropsOwnToggles() {
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
    class ResolveName {

        @Test
        void resolveNameReadsTheAllianceNameForAnAllianceBloc() {
            // An alliance bloc ID is not a faction ID, so its label comes from the grouping, not the
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
        void resolveBlocPickerReadOffersOnlyAllianceBlocsCrestedFromTheLeadMemberWithStats() {
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
                    .thenReturn(new DominanceStatsRead(
                        Map.of("rebel_pact", ANY_STATS, "hegemony", DominanceStats.EMPTY),
                        BlocPresenceIndex.EMPTY));

                assertThat(view.resolveBlocPickerRead(sectorMock, ANY_RULES, UNDER_THE_FOG).picker().items())
                    .containsExactly(new RankedBloc<>(
                        new SelectableBloc(
                            "rebel_pact",
                            "Rebel Pact",
                            "graphics/rebels_crest.png"),
                        ANY_STATS));
            }
        }

        @Test
        void resolveBlocPickerReadRanksItsBlocsByTheDominanceVocabularyThenTheirStanding() {
            // This view's blocs are folded sets of factions, but they carry the same metrics a lone
            // faction's do, so it ranks by the same vocabulary the factions view offers. Asserted
            // per view rather than once on the shared assembly, since which vocabulary a view's rows
            // can be read by is the view's own choice of what paints it. The standing ranking behind
            // them is not a choice - it reads one fact off the sector that holds under every view.
            var view = spy(AlliancesView.INSTANCE);

            doReturn(ALLIANCE_GROUPING)
                .when(view)
                .resolveGrouping();

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock
                    .when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(DominanceStatsRead.EMPTY);

                assertThat(view.resolveBlocPickerRead(mock(SectorAPI.class), ANY_RULES, UNDER_THE_FOG)
                        .picker()
                        .sortModes()
                        .modes())
                    .extracting(ListSortMode::persistenceKey)
                    .containsExactly(
                        "name", "domination", "presence", "score", "market_size", "player_standing");
            }
        }

        @Test
        void resolveBlocPickerReadCarriesThePresenceOfBlocsItsGateDropped() {
            // The index is the whole walk's, not the offered rows'. This is the view where the two
            // differ - a lone faction is present but never listed - and trimming it to match would
            // cost a pass to remove entries no lookup can reach, only a listed bloc being hoverable.
            var view = spy(AlliancesView.INSTANCE);

            doReturn(ALLIANCE_GROUPING)
                .when(view)
                .resolveGrouping();

            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction("rebels"))
                .thenReturn(mock(FactionAPI.class));

            try (var aggregatorMock = mockStatic(DominanceStatsAggregator.class)) {

                aggregatorMock
                    .when(() -> DominanceStatsAggregator.aggregateDominanceStats(any()))
                    .thenReturn(new DominanceStatsRead(
                        Map.of("rebel_pact", ANY_STATS, "hegemony", DominanceStats.EMPTY),
                        buildIndexOf(Map.of(
                            "rebel_pact", List.of("corvus"),
                            "hegemony", List.of("askonia")))));

                var read = view.resolveBlocPickerRead(sectorMock, ANY_RULES, UNDER_THE_FOG);

                assertThat(read.picker().items())
                    .extracting(RankedBloc::itemId)
                    .containsExactly("rebel_pact");

                assertThat(read.presenceIndex().readPresentSystemKeys("hegemony"))
                    .containsExactlyElementsOf(buildCellKeys("askonia"));
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
