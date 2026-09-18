package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.KmuMod;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.sidebar.ListPickerBinder;
import kmu.maplayers.politicalmap.base.politics.BlocPresenceIndex;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;
import kmu.maplayers.politicalmap.base.render.PoliticalMapLayerRenderer;
import kmu.maplayers.politicalmap.base.sidebar.PoliticalMapBodyControls;
import kmu.maplayers.politicalmap.base.sidebar.RecedeControl;
import kmu.settings.KmuMapKeybindSettings;
import kmu.util.KmuStringKeys;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how the political-map tab composes its body: the shared sub-options, then the view-selector
 * radio, then the spotlight picker, then the selected view's own controls appended beneath - so a
 * view shows the filter list plus widgets specific to it (the alliances view's Mute/Desaturate
 * checkboxes) while the tab itself names no concrete view. The shared controls, the selector, and the
 * picker are stubbed to sentinels so this pins the composition order alone, not what those pieces
 * contain.
 *
 * <p>Three cases build over a sector with machinery really installed on it, which is what makes the
 * body's one resolution observable: the composition cases resolve no sector, so a build reaching any
 * other machinery would answer identically. They pin that the picker is read off that sector's
 * memo, that every control the body places is handed that machinery's own refresh board, and that
 * every piece is handed the screen whose panel asked for the body - the two axes a control's write
 * belongs to, which sector's map it repaints and which screen's slot it lands in.
 *
 * <p>Also the two things this tab resolves for itself rather than being read out of: the strings key it
 * letters itself from, which the bar takes as drawn text, and the settings row its shortcut is rebound
 * in, which the bar takes as a keycode. Swapped for another tab's, either is a wrong label or a stolen
 * shortcut on screen and nothing else catches it.
 */
final class PoliticalMapLayerTest {

    // The live LunaLib field ID, pinned as a literal: a rename here silently drops the player's rebind
    // and leaves the tab keyless.
    private static final String SHORTCUT_SETTING_FIELD = "kmu_map_keybinds_layers_factions";

    // Whatever the settings row is answering with - the value is arbitrary, since the point is that the
    // tab hands it back untouched rather than that it is any particular key.
    private static final int BOUND_KEYCODE = 20;

    // The screen whose panel asks for the body in every case below. A stand-in rather than one of the two
    // live screens: what this tab does with the screen it is handed is to pass it on, which is the same
    // whichever one it is.
    private static final ScreenMemoryScope BODY_SCREEN = ScreenMemoryScopes.createStandInScreen();

    // The view slot of the panel that asks for the body, and of the screen showing while it does - the
    // active-view key composed under each. Pinned as literals so a rename of either half, which would
    // reset every save to the default, fails here rather than shipping. Two of them because posing the
    // showing screen holding no view while the asking panel holds one is what tells "reads the panel
    // that asked" apart from "reads whichever screen is up". No intel screen is registered in this
    // suite, so the sector map is the showing one.
    private static final String BODY_SCREEN_ACTIVE_VIEW_KEY = "$kmu_political_active_view_test";
    private static final String LIVE_SCREEN_ACTIVE_VIEW_KEY = "$kmu_political_active_view_map";

    // The stored value meaning no view paints - the tab showing with the map dark.
    private static final String VIEW_OFF_SENTINEL = "";

    // Sentinels standing in for the two view-agnostic pieces, so the assertions read the composition
    // order without depending on the real shared controls or selector contents. Their tone is
    // arbitrary - composition order is what is under test, not what colour a control draws in.
    // The ID the one bloc-offering view registers under, and the scope the picker must therefore be
    // built for - shared between the registration helper and the assertion so the two cannot drift.
    private static final String PICKER_VIEW_ID = "picker-view";

    private static final Color MARKER_COLOUR = Color.WHITE;
    private static final ControlSpec SHARED_MARKER = buildMarker("shared");
    private static final ControlSpec SELECTOR_MARKER = buildMarker("selector");
    private static final ControlSpec VIEW_MARKER = buildMarker("view");
    private static final ControlSpec PICKER_MARKER = buildMarker("picker");

    // Two sectors' installed machinery, since what this tab now answers turns on which of them it
    // is asked about. The body cases below use neither: a tab's controls are the same wherever it
    // is drawn.
    private final SectorMapMachinery machinery = new SectorMapMachinery(null);
    private final SectorMapMachinery otherMachinery = new SectorMapMachinery(null);

    private final PoliticalMapView viewWithControlsMock = mock(PoliticalMapView.class);
    private final PoliticalMapView viewWithoutControlsMock = mock(PoliticalMapView.class);

    // Loads the machinery index before any case stubs Global. The body build resolves the running
    // sector's machinery through it, and the index resolves its logger once at class initialisation -
    // so a first load from inside a Global stub would leave it holding a null logger for the rest of
    // the JVM, and the next suite to install or release machinery would fall over on it.
    @BeforeAll
    static void loadTheMachineryIndex() {
        SectorMapMachineryIndex.resolveMachineryFor(null);
    }

    @Nested
    class ResolveStanding {

        @Test
        void resolveStandingYieldsThePairThatWiresThePoliticalMapToASector() {
            // The counterpart to No Layer's null: this tab is the one with sector wiring - a save
            // heal, four listeners and a poll - so taking its tab off the bar has something to save.
            // One pair for the tab, since the pair holds nothing and every sector arrives as an
            // argument.
            assertThat(PoliticalMapLayer.INSTANCE.resolveStanding())
                .isInstanceOf(PoliticalMapStanding.class)
                .isSameAs(PoliticalMapLayer.INSTANCE.resolveStanding());
        }
    }

    @Nested
    class ResolveRenderer {

        @Test
        void resolveRendererYieldsTheOneRendererThatMachineryKeeps() {
            // The counterpart to No Layer's null: this tab is the one that draws, and it hands the map
            // surface one view-neutral renderer rather than branching on the view roster. Twice for
            // one sector is once, since the surface resolves it every frame and the hover box again
            // in the pass after.
            assertThat(PoliticalMapLayer.INSTANCE.resolveRenderer(machinery))
                .isInstanceOf(PoliticalMapLayerRenderer.class)
                .isSameAs(PoliticalMapLayer.INSTANCE.resolveRenderer(machinery));
        }

        @Test
        void resolveRendererYieldsARendererPerMachinerySoOneSectorsDrawingIsNotAnothers() {
            // This tab is registered once for the process while everything behind its renderer - the
            // cut cells, the territories, the fitted names - is one sector's, so the same tab has to
            // answer for two sectors with two renderers.
            assertThat(PoliticalMapLayer.INSTANCE.resolveRenderer(machinery))
                .isNotSameAs(PoliticalMapLayer.INSTANCE.resolveRenderer(otherMachinery));
        }

        @Test
        void resolveRendererLeavesNothingBehindOnTheMachineryItWasReleasedWith() {
            // The renderer goes with the sector's machinery, so the sector installed on after it
            // draws through one of its own rather than through the previous sector's cached cells.
            var renderer = PoliticalMapLayer.INSTANCE.resolveRenderer(machinery);

            machinery.disposeMachinery();

            assertThat(PoliticalMapLayer.INSTANCE.resolveRenderer(machinery))
                .isNotSameAs(renderer);
        }
    }

    @Nested
    class GetBodyControls {

        @Test
        void getBodyControlsAppendsTheSelectedViewsControlsAfterTheSelector() {

            when(viewWithControlsMock.getViewBodyControls(any()))
                .thenReturn(List.of(VIEW_MARKER));

            registerDefaultView(viewWithControlsMock);

            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class)) {

                // No sector resolves the default view as selected, so the registered view paints.
                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                // The recede paired with the picker's sort selector carries the engine's text tone,
                // which Misc reads off the live settings - so a body built here needs a settings proxy
                // that answers a colour. Stubbed before the static stubbing opens, since its own
                // stubbing would otherwise land inside that one.
                var settingsMock = buildSettingsAnsweringColours();
                globalMock
                    .when(Global::getSettings)
                    .thenReturn(settingsMock);

                stubSharedControlsAndSelector(controlsMock);

                var body = PoliticalMapLayer.INSTANCE.getBodyControls(BODY_SCREEN);

                assertThat(body)
                    .containsExactly(SHARED_MARKER, SELECTOR_MARKER, VIEW_MARKER);
            }
        }

        @Test
        void getBodyControlsPlacesTheSpotlightPickerBetweenTheSelectorAndTheViewControls() {

            when(viewWithControlsMock.getViewBodyControls(any()))
                .thenReturn(List.of(VIEW_MARKER));

            registerDefaultView(viewWithControlsMock);

            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class);
                    var pickerMock = mockStatic(ListPickerBinder.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                // The recede paired with the picker's sort selector carries the engine's text tone,
                // which Misc reads off the live settings - so a body built here needs a settings proxy
                // that answers a colour. Stubbed before the static stubbing opens, since its own
                // stubbing would otherwise land inside that one.
                var settingsMock = buildSettingsAnsweringColours();
                globalMock
                    .when(Global::getSettings)
                    .thenReturn(settingsMock);

                stubSharedControlsAndSelector(controlsMock);
                pickerMock
                    .when(() -> ListPickerBinder.buildPicker(
                        any(),
                        any(),
                        any(),
                        any()))
                    .thenReturn(List.of(PICKER_MARKER));

                var body = PoliticalMapLayer.INSTANCE.getBodyControls(BODY_SCREEN);

                assertThat(body)
                    .containsExactly(SHARED_MARKER, SELECTOR_MARKER, PICKER_MARKER, VIEW_MARKER);
            }
        }

        @Test
        void getBodyControlsOmitsViewControlsWhenTheSelectedViewAddsNone() {

            // The faction view adds no controls of its own, so the body is only the shared rows and
            // the selector - nothing trails the selector.
            when(viewWithoutControlsMock.getViewBodyControls(any()))
                .thenReturn(List.of());

            registerDefaultView(viewWithoutControlsMock);

            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                // The recede paired with the picker's sort selector carries the engine's text tone,
                // which Misc reads off the live settings - so a body built here needs a settings proxy
                // that answers a colour. Stubbed before the static stubbing opens, since its own
                // stubbing would otherwise land inside that one.
                var settingsMock = buildSettingsAnsweringColours();
                globalMock
                    .when(Global::getSettings)
                    .thenReturn(settingsMock);

                stubSharedControlsAndSelector(controlsMock);

                var body = PoliticalMapLayer.INSTANCE.getBodyControls(BODY_SCREEN);

                assertThat(body)
                    .containsExactly(SHARED_MARKER, SELECTOR_MARKER);
            }
        }

        @Test
        void getBodyControlsAppendsNoViewControlsWhenTheMapIsOff() {
            // The off sentinel is stored for the asking panel, so no view is selected there; even a view
            // that has controls contributes none, since the tab is showing but that panel's map is dark.
            registerDefaultView(viewWithControlsMock);

            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class)) {

                var memoryMock = mock(MemoryAPI.class);
                var sectorMock = mock(SectorAPI.class);

                when(sectorMock.getMemoryWithoutUpdate())
                    .thenReturn(memoryMock);

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                when(memoryMock.contains(BODY_SCREEN_ACTIVE_VIEW_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(BODY_SCREEN_ACTIVE_VIEW_KEY))
                    .thenReturn(VIEW_OFF_SENTINEL);

                stubSharedControlsAndSelector(controlsMock);

                var body = PoliticalMapLayer.INSTANCE.getBodyControls(BODY_SCREEN);

                assertThat(body)
                    .containsExactly(SHARED_MARKER, SELECTOR_MARKER);
            }
        }

        @Test
        void getBodyControlsPairsTheFilterRecedeWithThePickersSortSelector() {
            // What sits beside the sort selector is this layer's decision, not the framework
            // picker's: the political map fills that half with the filter recede - a caption and the
            // Mute and Desaturate checkboxes - so the "rest of the sector" knobs read beside the
            // metric. Built for real (no picker stub), since the pairing is the thing under test.
            registerViewWithOneBloc(viewWithoutControlsMock);

            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                // The picker's rows carry the engine's text tone, which Misc reads off the live
                // settings - so building one for real needs a settings proxy that answers a colour.
                // Stubbed before the static stubbing opens, since its own stubbing would otherwise
                // land inside that one.
                var settingsMock = buildSettingsAnsweringColours();
                globalMock
                    .when(Global::getSettings)
                    .thenReturn(settingsMock);

                stubSharedControlsAndSelector(controlsMock);

                var sortRow = findSortRow(PoliticalMapLayer.INSTANCE.getBodyControls(BODY_SCREEN));

                // The left half is the sort selector over this layer's own vocabulary, one row per
                // mode, so the modes reaching the picker are the political map's.
                assertThat(sortRow.leftColumn())
                    .hasSize(1);
                assertThat(sortRow.leftColumn().get(0).labels())
                    .hasSize(DominanceSortModes.MODES.modes().size());

                // The right half is the recede: its caption, then the two toggles.
                assertThat(sortRow.rightColumn().get(0))
                    .isInstanceOf(ControlSpec.Label.class);
                assertThat(sortRow.rightColumn().get(1))
                    .isInstanceOf(ControlSpec.Checkbox.class);
                assertThat(sortRow.rightColumn().get(2))
                    .isInstanceOf(ControlSpec.Checkbox.class);
            }
        }

        @Test
        void getBodyControlsScopesThePickerToTheSelectedViewsId() {
            // The scope this layer hands over is what makes each view remember its own spotlight and
            // its own sort: the stores partition by whatever ID they are given, so a layer passing a
            // constant would still read and write consistently and every store-level test would stay
            // green while all three views shared one slot. Pinned here because this is the only place
            // the ID is chosen.
            registerViewWithOneBloc(viewWithoutControlsMock);
            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class);
                    var pickerMock = mockStatic(ListPickerBinder.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                // The recede paired with the sort selector reads the engine's text tone off the live
                // settings, so even a stubbed picker needs a settings proxy that answers a colour.
                var settingsMock = buildSettingsAnsweringColours();
                globalMock
                    .when(Global::getSettings)
                    .thenReturn(settingsMock);

                stubSharedControlsAndSelector(controlsMock);

                PoliticalMapLayer.INSTANCE.getBodyControls(BODY_SCREEN);

                pickerMock.verify(
                    () -> ListPickerBinder.buildPicker(
                        argThat(slot -> PICKER_VIEW_ID.equals(slot.scopeId())),
                        any(),
                        any(),
                        any()));
            }
        }

        @Test
        void getBodyControlsReadsThePickerOffTheRunningSectorsMachinery() {
            // The body build is handed no sector - a vanilla screen names none - so it resolves the
            // running one's machinery itself, and the list it reads is that sector's. Every case
            // above resolves no sector at all, under which a build reaching any other machinery
            // would answer identically; only a build over a really installed sector can tell them
            // apart.
            buildBodyOverInstalledSector((sector, machinery, controlsMock, pickerMock, recedeMock) ->
                // The walk ran against the installed sector, which it can only have done through
                // that sector's own memo - the machinery of no sector reads no economy at all.
                verify(viewWithoutControlsMock)
                    .resolveBlocPickerRead(sector));
        }

        @Test
        void getBodyControlsHandsEveryWriterTheRunningSectorsOwnRefreshBoard() {
            // The controls this body builds all write a sidebar-only preference, which repaints by
            // raising a signal rather than by moving settingsRevision - so each is handed the board
            // of the machinery this build resolved. Handed any other, a flip would repaint a map the
            // player is not looking at and leave the one they are as it was.
            buildBodyOverInstalledSector((sector, machinery, controlsMock, pickerMock, recedeMock) -> {

                var installedBoard = machinery.resolveRefreshBoard();

                // The three seams the body hands a panel to - the shared sub-options, the filter recede
                // paired with the picker's sort, and the selected view's own controls - and the picker
                // itself, which takes the machinery whole: both of its writers are that sector's, so it
                // derives them rather than being handed them.
                controlsMock.verify(
                    () -> PoliticalMapBodyControls.buildSharedControls(
                        argThat(target -> installedBoard.equals(target.board()))));
                recedeMock.verify(
                    () -> RecedeControl.buildControls(
                        any(),
                        any(),
                        argThat(target -> installedBoard.equals(target.board()))));
                pickerMock.verify(
                    () -> ListPickerBinder.buildPicker(
                        any(),
                        any(),
                        any(),
                        eq(machinery)));
                verify(viewWithoutControlsMock)
                    .getViewBodyControls(argThat(target -> installedBoard.equals(target.board())));
            });
        }

        @Test
        void getBodyControlsHandsEveryPieceTheScreenWhosePanelAskedForTheBody() {
            // The screen travels the whole build for the same reason the board does: a control writes the
            // preference of the panel it was placed on, so a piece left to find a screen for itself would
            // file its click under whichever panel happened to be up when it was pressed. Every seam the
            // body composes is asked for, since one of them dropping the screen is exactly one control set
            // silently landing on the other panel's slots.
            //
            // The view read the body branches on is covered by the arrangement rather than by a verify:
            // the showing screen's map is posed off, so reaching the picker and the view's own controls
            // at all is only possible for a build that resolved the asking panel's view.
            buildBodyOverInstalledSector((sector, machinery, controlsMock, pickerMock, recedeMock) -> {

                controlsMock.verify(
                    () -> PoliticalMapBodyControls.buildSharedControls(
                        argThat(target -> BODY_SCREEN.equals(target.memoryScope()))));
                controlsMock.verify(
                    () -> PoliticalMapBodyControls.buildViewSelector(BODY_SCREEN));
                recedeMock.verify(
                    () -> RecedeControl.buildControls(
                        any(),
                        any(),
                        argThat(target -> BODY_SCREEN.equals(target.memoryScope()))));
                pickerMock.verify(
                    () -> ListPickerBinder.buildPicker(
                        argThat(slot -> BODY_SCREEN.equals(slot.screenSlot().memoryScope())),
                        any(),
                        any(),
                        any()));
                verify(viewWithoutControlsMock)
                    .getViewBodyControls(argThat(target -> BODY_SCREEN.equals(target.memoryScope())));
            });
        }

        @Test
        void getBodyControlsFilesThePickersPicksUnderThisModsOwnStoreNamespace() {
            // The shared stores hold no mod's name, so which mod's spotlight, sort and column count a
            // picker reads and writes is decided here. A slot built under any other namespace reads
            // back nothing every existing save holds, which no store's own suite can catch.
            buildBodyOverInstalledSector((sector, machinery, controlsMock, pickerMock, recedeMock) ->
                pickerMock.verify(
                    () -> ListPickerBinder.buildPicker(
                        argThat(slot ->
                            KmuMod.MAP_STORE_NAMESPACE.equals(slot.screenSlot().namespace())),
                        any(),
                        any(),
                        any())));
        }
    }

    @Nested
    class ResolveTabLabelText {

        @Test
        void resolveTabLabelTextLettersTheTabFromThePoliticalMapsOwnKey() {

            try (var stringsMock = mockStatic(KmuStringKeys.class)) {

                stringsMock
                    .when(() -> KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TAB_POLITICAL_MAP))
                    .thenReturn("Political Map");

                assertThat(PoliticalMapLayer.INSTANCE.resolveTabLabelText())
                    .isEqualTo("Political Map");
            }
        }
    }

    @Nested
    class ResolveShortcutKeycode {

        @Test
        void resolveShortcutKeycodeReadsThePoliticalMapsOwnRebindingField() {
            // Which row the rebind lands in is this tab's own fact now, so a wrong ID here silently
            // ignores the player's rebind while every framework test stays green.
            try (var settingsMock = mockStatic(KmuMapKeybindSettings.class)) {

                settingsMock
                    .when(() -> KmuMapKeybindSettings.getMapLayerShortcut(SHORTCUT_SETTING_FIELD))
                    .thenReturn(BOUND_KEYCODE);

                assertThat(PoliticalMapLayer.INSTANCE.resolveShortcutKeycode())
                    .isEqualTo(BOUND_KEYCODE);
            }
        }

        @Test
        void resolveShortcutKeycodeLeavesTheTabUnboundWhenTheSettingsRowAnswersNoKey() {
            // No key of this tab's own stands behind the row: a settings read answering nothing leaves
            // the tab unbound, which the bar draws no hint for and matches no press against. A fallback
            // keycode here would be a second answer to what the shipped table already decides, and would
            // bind a key the player had cleared.
            try (var settingsMock = mockStatic(KmuMapKeybindSettings.class)) {

                assertThat(PoliticalMapLayer.INSTANCE.resolveShortcutKeycode())
                    .isZero();
            }
        }
    }

    // Builds the body once against machinery really installed on a sector, then hands the caller
    // that sector, its machinery, and the two stubbed seams to make its claim over - before the
    // static stubbing closes, since a MockedStatic cannot be verified after it does.
    //
    // The arrangement is the wide part of the cases that use it and none of what they assert: a real
    // install (made outside the stubbing, so the index resolves a real logger rather than one taken
    // from a stubbed Global), the sector the running game answers with, a settings proxy the recede's
    // text tone is read off, and the two view-agnostic pieces stubbed to their sentinels. Written
    // once so the cases cannot drift into arranging different builds and reading the difference
    // as a finding.
    private void buildBodyOverInstalledSector(BodyBuildAssertion assertion) {

        registerViewWithOneBloc(viewWithoutControlsMock);

        var sectorMock = mock(SectorAPI.class);
        var memoryMock = mock(MemoryAPI.class);

        when(sectorMock.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);

        // The showing screen's own view turned off, while the asking panel's slot stays absent and so
        // resolves the registered default. That makes every claim below a discriminator for which
        // screen the body read: a build resolving the live screen instead of the one that asked finds
        // no view, appends neither the picker nor the view's own controls, and fails.
        when(memoryMock.contains(LIVE_SCREEN_ACTIVE_VIEW_KEY))
            .thenReturn(true);
        when(memoryMock.getString(LIVE_SCREEN_ACTIVE_VIEW_KEY))
            .thenReturn(VIEW_OFF_SENTINEL);

        // Installing machinery registers a profiling origin describing the sector, which a real one
        // always has a seed for. Left unstubbed the install faults before the body is ever built, so
        // every claim below would fail for a reason none of them is about.
        when(sectorMock.getSeedString())
            .thenReturn("body-build-sector");

        // The recede paired with the sort selector reads the engine's text tone off the live
        // settings, and it is built as an argument, so even a stubbed picker needs a settings proxy
        // that answers a colour. Built before the static stubbing opens, since its own stubbing
        // would otherwise land inside that one.
        var settingsMock = buildSettingsAnsweringColours();

        var machinery = SectorMapMachineryIndex.installMachineryOn(sectorMock);

        try (var globalMock = mockStatic(Global.class);
                var controlsMock = mockStatic(PoliticalMapBodyControls.class);
                var pickerMock = mockStatic(ListPickerBinder.class);
                var recedeMock = mockStatic(RecedeControl.class)) {

            globalMock
                .when(Global::getSector)
                .thenReturn(sectorMock);

            globalMock
                .when(Global::getSettings)
                .thenReturn(settingsMock);

            stubSharedControlsAndSelector(controlsMock);
            pickerMock
                .when(() -> ListPickerBinder.buildPicker(
                    any(),
                    any(),
                    any(),
                    any()))
                .thenReturn(List.of(PICKER_MARKER));

            PoliticalMapLayer.INSTANCE.getBodyControls(BODY_SCREEN);

            assertion.assertOverBodyBuild(
                sectorMock,
                machinery,
                controlsMock,
                pickerMock,
                recedeMock);

        } finally {
            SectorMapMachineryIndex.uninstallMachineryFrom(sectorMock);
        }
    }

    // Registers a view offering one spotlightable bloc, under an ID and revision of its own so the
    // picker memo misses on it rather than serving the empty list the composition tests leave
    // cached - these cases resolve no sector, so they all reach the one detached machinery and
    // share the memo it holds. The bloc's contents do not matter - what matters is that the picker
    // builds at all, since an empty list contributes none.
    private static void registerViewWithOneBloc(PoliticalMapView view) {

        when(view.getId())
            .thenReturn(PICKER_VIEW_ID);

        when(view.getContentRevision(any()))
            .thenReturn(1);

        when(view.getViewBodyControls(any()))
            .thenReturn(List.of());

        // Stubbed through doReturn because the seam answers a wildcarded read, whose captured item
        // type a when() stub would have to name. The presence beside the rows stands in: what these
        // assertions are about is where the picker sits in the body, which lights nothing.
        doReturn(new BlocPickerRead<>(
                new ListPicker<>(
                    List.of(new RankedBloc<>(
                        new SelectableBloc("hegemony", "Hegemony", null),
                        DominanceStats.EMPTY)),
                    DominanceSortModes.MODES),
                BlocPresenceIndex.EMPTY))
            .when(view)
            .resolveBlocPickerRead(any());

        var hostTabMock = mock(MapLayer.class);

        when(hostTabMock.getId())
            .thenReturn("host");

        PoliticalMapViewRegistry.registerViews(List.of(view), view, hostTabMock);
        MapLayerRosters.replaceRosterWith(hostTabMock);
    }

    // One sentinel control, named so an assertion can tell the composed pieces apart. A caption is the
    // simplest control there is, which is why it stands in for whatever the layer really contributes.
    private static ControlSpec buildMarker(String markerText) {
        return ControlSpec.Label.createLabel(new TextSpan(markerText, MARKER_COLOUR));
    }

    // A settings proxy that answers every colour lookup with one tone. Which tone a row draws in is
    // not what these tests read, so one stands in for the whole palette.
    private static SettingsAPI buildSettingsAnsweringColours() {

        var settingsMock = mock(SettingsAPI.class);
        when(settingsMock.getColor(any()))
            .thenReturn(Color.LIGHT_GRAY);

        return settingsMock;
    }

    // The picker's paired sort row, found by type rather than by index so the assertion does not
    // re-state the framework picker's own row order, which is pinned where the picker lives.
    private static ControlSpec.SideBySide findSortRow(List<ControlSpec> body) {
        return body
            .stream()
            .filter(ControlSpec.SideBySide.class::isInstance)
            .map(ControlSpec.SideBySide.class::cast)
            .findFirst()
            .orElseThrow();
    }

    // Stubs the two view-agnostic pieces to their sentinels so a test asserts only the composition
    // order the layer imposes, not the pieces' own contents.
    private static void stubSharedControlsAndSelector(
            MockedStatic<PoliticalMapBodyControls> controlsMock) {

        controlsMock
            .when(() -> PoliticalMapBodyControls.buildSharedControls(any()))
            .thenReturn(List.of(SHARED_MARKER));

        controlsMock
            .when(() -> PoliticalMapBodyControls.buildViewSelector(any()))
            .thenReturn(SELECTOR_MARKER);
    }

    // Registers the one view as both the sole registered view and the default, with a host tab the
    // layer registry treats as active, so a sector-less read resolves this view as selected. Also
    // stubs the view's identity and empty picker, since the body build now reads its picker options
    // through the memo (keyed on the view ID and its content revision) rather than off the view
    // directly - an empty picker contributes no controls, keeping these composition assertions about
    // where the picker sits, not what it holds.
    private static void registerDefaultView(PoliticalMapView view) {

        when(view.getId())
            .thenReturn("selected-view");
        when(view.getContentRevision(any()))
            .thenReturn(0);

        doReturn(BlocPickerRead.empty())
            .when(view)
            .resolveBlocPickerRead(any());

        var hostTabMock = mock(MapLayer.class);

        when(hostTabMock.getId())
            .thenReturn("host");

        PoliticalMapViewRegistry.registerViews(List.of(view), view, hostTabMock);
        MapLayerRosters.replaceRosterWith(hostTabMock);
    }

    // What a case makes of a body built over a really installed sector. It takes the sector and its
    // machinery because the claims are about which of the two the build reached, and the two
    // stubbed seams because a MockedStatic can only be verified while it is still open.
    @FunctionalInterface
    private interface BodyBuildAssertion {

        void assertOverBodyBuild(
                SectorAPI sector,
                SectorMapMachinery machinery,
                MockedStatic<PoliticalMapBodyControls> controlsMock,
                MockedStatic<ListPickerBinder> pickerMock,
                MockedStatic<RecedeControl> recedeMock);
    }
}
