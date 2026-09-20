package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.controls.specs.ControlSpec;
import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.widgets.tabs.style.TabBox;
import kmlib.starsector.ui.widgets.tabs.style.TabStyle;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.ControlBackedMapLayerVisibility;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerArrangements;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.maplayers.base.layer.NoLayer;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.sidebar.style.SidebarStyles;
import kmu.settings.KmuMapControlSettings;
import kmu.settings.KmuMapSidebarSettings;
import kmu.settings.SidebarSettingsMock;
import kmu.starsector.StarsectorUiColoursMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the intel overlay's anchor math: the box hangs from the visor's top-left corner (UI origin
 * bottom-left, so the visor's top edge is its y plus its height), pushed down by the top padding, flush to
 * the visor's left, and capped to the visor's bottom - all expressed as screen padding for the
 * top-left-anchored layout. The tab band each panel stands its row in is its host's, and pinned there.
 *
 * <p>And where the tab row's letters and key hints come from: each layer's own answer, taken as drawn text
 * and as the keycode in force, so a layer shipped by another mod letters and binds its tab out of its own
 * bundle and its own settings.
 *
 * <p>And which screen the active layer's body is opened under: the asking panel's own, with no live-screen
 * read of its own beside it, since a control in that body writes the preference of the screen it was
 * placed on.
 *
 * <p>And whether the band carries the bar's opener at all: only where the roster holds more than one
 * layer that paints, counted off the roster rather than off the row the screen is offered, so an opener
 * never leaves over an arrangement it is the only way back from.
 *
 * <p>And the one way a placement comes back with nothing in it: the tab face failing to load, which the
 * caller has to read as "draw nothing this frame" rather than as an empty row it may still hit-test.
 */
final class LiveSidebarPlacementTest {

    // A visor whose left edge is x = 100, bottom edge y = 50, and top edge y + height = 650.
    private static final Rectangle MAP_VISOR = new Rectangle(100f, 50f, 800f, 600f);

    // The IDs two registered layers stand under, the arrangement naming its layers by id.
    private static final String FIRST_PAINTING_LAYER_ID = "political";
    private static final String SECOND_PAINTING_LAYER_ID = "trade_routes";

    // The sector map's own tab row, which the opener is asked to stand in.
    private static final float HOST_BAND_HEIGHT = 19f;
    private static final float HOST_TAB_WIDTH = 130f;
    private static final float HOST_TAB_HEIGHT = 18f;
    private static final float HOST_TAB_GAP = 1f;

    // LWJGL's KEY_P and KEY_N, two real keys a layer could be answering with.
    private static final int POLITICAL_MAP_KEYCODE = 25;
    private static final int NO_LAYER_KEYCODE = 49;

    // What the player leaves behind by clearing a binding with Escape.
    private static final int UNBOUND_KEYCODE = 0;

    // Inside LWJGL's name table but not one of its keys, so it names nothing.
    private static final int UNNAMED_KEYCODE = 84;

    // Past the end of that table, which is indexed by keycode with no range check of its own.
    private static final int OFF_THE_KEYBOARD_KEYCODE = 9999;

    // A face size for a style whose font never loads. Arbitrary: nothing measures anything in the one
    // case that stands a style up, so the number is there only because a face carries one.
    private static final double TAB_FACE_SIZE = 12d;

    @Nested
    class ComputeIntelPadding {

        @Test
        void computeIntelPaddingHangsTheBoxFromTheVisorTopPushedDownByTheTopPadding() {
            // Visor top edge = 650; screen 1200 tall; the box top sits 40px below the visor top, so its
            // distance from the screen top is (1200 - 650) + 40 = 590.
            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 40);

            assertThat(padding.top())
                .isEqualTo(590);
        }

        @Test
        void computeIntelPaddingHangsTheBoxAtTheVisorTopWhenTheTopPaddingIsZero() {

            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 0);

            assertThat(padding.top())
                .isEqualTo(550);
        }

        @Test
        void computeIntelPaddingSitsFlushAgainstTheVisorLeftEdgeAndGrowsRightward() {

            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 40);

            assertThat(padding.left())
                .isEqualTo(100);
            assertThat(padding.right())
                .isZero();
        }

        @Test
        void computeIntelPaddingCapsTheBodyToTheVisorBottom() {

            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 40);

            assertThat(padding.bottom())
                .isEqualTo(50);
        }
    }

    @Nested
    class ResolveTabLabels {

        @Test
        void resolveTabLabelsLettersEachTabFromItsOwnLayersAnswer() {
            // The inversion this pins: the bar draws what the layer hands back and resolves no key of its
            // own, which is what lets a layer from another mod letter its tab out of a bundle KMU has no
            // reader for. Two layers, so a bar reading one fixed source would show one of them twice.
            var firstLayerMock = mock(MapLayer.class);
            var secondLayerMock = mock(MapLayer.class);

            when(firstLayerMock.resolveTabLabelText())
                .thenReturn("No Layer");
            when(secondLayerMock.resolveTabLabelText())
                .thenReturn("Trade Routes");

            assertThat(LiveSidebarPlacement.resolveTabLabels(List.of(firstLayerMock, secondLayerMock)))
                .containsExactly("No Layer", "Trade Routes");
        }

        @Test
        void resolveTabLabelsKeepsATabForALayerAnsweringABlankLabel() {
            // A layer whose text resolves to nothing - a missing bundle entry, or a player-set name
            // cleared - keeps its place in the row. Dropping it would take the tab away with it, leaving
            // the player no way back to a layer they can still be holding as their pick.
            var blankLabelLayerMock = mock(MapLayer.class);
            var labelledLayerMock = mock(MapLayer.class);

            when(blankLabelLayerMock.resolveTabLabelText())
                .thenReturn("");
            when(labelledLayerMock.resolveTabLabelText())
                .thenReturn("Political Map");

            assertThat(LiveSidebarPlacement.resolveTabLabels(List.of(blankLabelLayerMock, labelledLayerMock)))
                .containsExactly("", "Political Map");
        }

        @Test
        void resolveTabLabelsLettersNothingForALayerAnsweringNoLabelAtAll() {
            // A layer answering null rather than blank, which an arbitrary mod's layer can. The row refuses
            // a null label outright, so an ungated read would cost the player the whole tab strip over one
            // layer's missing answer instead of costing that layer its letters.
            var namelessLayerMock = mock(MapLayer.class);
            var labelledLayerMock = mock(MapLayer.class);

            when(namelessLayerMock.resolveTabLabelText())
                .thenReturn(null);
            when(labelledLayerMock.resolveTabLabelText())
                .thenReturn("Political Map");

            assertThat(LiveSidebarPlacement.resolveTabLabels(List.of(namelessLayerMock, labelledLayerMock)))
                .containsExactly("", "Political Map");
        }
    }

    @Nested
    class ResolveTabShortcuts {

        @Test
        void resolveTabShortcutsHintsEachTabWithTheKeyItsOwnLayerAnswersTo() {
            // The inversion this pins: the bar prints the key the layer hands back and reads no settings
            // field of its own, which is what lets a layer from another mod bind its tab in a settings file
            // KMU has no reader for. Two layers, so a bar reading one fixed source would hint one of them
            // twice.
            var firstLayerMock = mock(MapLayer.class);
            var secondLayerMock = mock(MapLayer.class);

            when(firstLayerMock.resolveShortcutKeycode())
                .thenReturn(NO_LAYER_KEYCODE);
            when(secondLayerMock.resolveShortcutKeycode())
                .thenReturn(POLITICAL_MAP_KEYCODE);

            assertThat(LiveSidebarPlacement.resolveTabShortcuts(List.of(firstLayerMock, secondLayerMock)))
                .containsExactly("N", "P");
        }

        @Test
        void resolveTabShortcutsHintsNoKeyForALayerThePlayerLeftUnbound() {
            // A cleared binding is a key the tab must stop advertising: LWJGL still names 0 "NONE", so an
            // ungated read would print a key the player cannot press. The entry stays, since the control
            // pairs hints to labels by index.
            var unboundLayerMock = mock(MapLayer.class);
            var boundLayerMock = mock(MapLayer.class);

            when(unboundLayerMock.resolveShortcutKeycode())
                .thenReturn(UNBOUND_KEYCODE);
            when(boundLayerMock.resolveShortcutKeycode())
                .thenReturn(POLITICAL_MAP_KEYCODE);

            assertThat(LiveSidebarPlacement.resolveTabShortcuts(List.of(unboundLayerMock, boundLayerMock)))
                .containsExactly(null, "P");
        }

        @Test
        void resolveTabShortcutsHintsNoKeyForACodeLwjglCannotName() {
            // A real keycode LWJGL holds no name for. The tab keeps its label and simply says nothing about
            // what presses it, rather than printing a blank bracket beside the name.
            var unnamedLayerMock = mock(MapLayer.class);

            when(unnamedLayerMock.resolveShortcutKeycode())
                .thenReturn(UNNAMED_KEYCODE);

            assertThat(LiveSidebarPlacement.resolveTabShortcuts(List.of(unnamedLayerMock)))
                .containsExactly((String) null);
        }

        @Test
        void resolveTabShortcutsHintsNoKeyForACodePastTheKeyboard() {
            // The bound test earns its place here: the keycode is now an arbitrary mod's number and LWJGL
            // indexes its name table by it unchecked, so without the gate one layer answering nonsense
            // would take the whole tab row down instead of costing itself a hint.
            var offTheKeyboardLayerMock = mock(MapLayer.class);

            when(offTheKeyboardLayerMock.resolveShortcutKeycode())
                .thenReturn(OFF_THE_KEYBOARD_KEYCODE);

            assertThat(LiveSidebarPlacement.resolveTabShortcuts(List.of(offTheKeyboardLayerMock)))
                .containsExactly((String) null);
        }
    }

    @Nested
    class BuildTabsSpec {

        @Test
        void buildTabsSpecLightsTheTabOfTheScreensOwnActiveLayer() {

            var layers = List.of(buildLayerMock("No Layer"), buildLayerMock("Political Map"));

            var spec = LiveSidebarPlacement.buildTabsSpec(
                layers,
                layers.get(1),
                mock(ActiveLayerSelection.class));

            // The lit index is the layer's row in the registry, which is what pairs the drawn tab with the
            // body beneath it - both index the same list.
            assertThat(spec.selectedIndex())
                .isEqualTo(1);
        }

        @Test
        void buildTabsSpecLightsNoTabForAnActiveLayerTheRegistryNoLongerHolds() {
            // A pick can outlive its layer: the mod that registered it leaves the load order while the
            // screen is still holding it. The row then lights nothing rather than lighting a tab that
            // stands for something else, and NO_SELECTION is what says so.
            var layers = List.of(buildLayerMock("No Layer"), buildLayerMock("Political Map"));

            var spec = LiveSidebarPlacement.buildTabsSpec(
                layers,
                buildLayerMock("Trade Routes"),
                mock(ActiveLayerSelection.class));

            assertThat(spec.selectedIndex())
                .isEqualTo(ControlSpec.NO_SELECTION);
            assertThat(spec.isLit())
                .isFalse();
        }

        @Test
        void buildTabsSpecSelectsTheLayerAtTheClickedTab() {
            // The switch rides on the control's own action, so the tab that was drawn at an index and the
            // layer a click on it selects are the same registry row - no separate tab callback to fall out
            // of step with the row.
            var layers = List.of(buildLayerMock("No Layer"), buildLayerMock("Political Map"));
            var selectionMock = mock(ActiveLayerSelection.class);

            LiveSidebarPlacement.buildTabsSpec(layers, layers.get(0), selectionMock)
                .action()
                .activateCell(1);

            verify(selectionMock).selectLayer(layers.get(1));
        }

        @Test
        void buildTabsSpecPairsEveryLabelWithAShortcutSlot() {
            // The control reads the two lists by index, so a row whose hints ran shorter than its labels
            // would hand a tab someone else's key - or none where one was bound.
            var layers = List.of(
                buildLayerMock("No Layer"),
                buildLayerMock("Political Map"),
                buildLayerMock("Trade Routes"));

            var spec = LiveSidebarPlacement.buildTabsSpec(
                layers,
                layers.get(0),
                mock(ActiveLayerSelection.class));

            assertThat(spec.labels())
                .hasSize(3);
            assertThat(spec.shortcuts())
                .hasSameSizeAs(spec.labels());
        }

        @Test
        void buildTabsSpecStandsTheRowUpAroundALayerAnsweringNoLabel() {
            // The end of the same path: the strip refuses a null label, so an unsettled answer from one
            // layer would throw here rather than in the read that produced it. The row stands, and the
            // layer that said nothing simply has nothing lettered on its tab.
            var layers = List.of(buildLayerMock(null), buildLayerMock("Political Map"));

            var spec = LiveSidebarPlacement.buildTabsSpec(
                layers,
                layers.get(1),
                mock(ActiveLayerSelection.class));

            assertThat(spec.labels())
                .containsExactly("", "Political Map");
        }

        // A layer that letters its tab and answers no key, which is the shape every case here is about -
        // the label matters because the row refuses a null one, and the keycode is left at its unbound
        // default so no case turns on a hint it did not arrange.
        private MapLayer buildLayerMock(String label) {

            var layerMock = mock(MapLayer.class);
            when(layerMock.resolveTabLabelText())
                .thenReturn(label);

            return layerMock;
        }
    }

    @Nested
    class ResolveOpenerSpec {

        private StarsectorUiColoursMock uiColoursMock;
        private SidebarSettingsMock sidebarSettingsMock;
        private MockedStatic<KmuMapControlSettings> controlSettingsMock;

        @BeforeEach
        void mockLiveColoursAndSettings() {
            // The first two are what the host row's own style is composed from, the opener wearing that
            // style unchanged but for its box.
            uiColoursMock = StarsectorUiColoursMock.install();
            sidebarSettingsMock = SidebarSettingsMock.install();

            // The hatch, stood in at the state its row ships in rather than left to the mock's own
            // default: every case but one turns on the count being what answers, so the case that says
            // otherwise should be the only one that mentions it.
            controlSettingsMock = mockStatic(KmuMapControlSettings.class);
            closeTheArrangementOpenerHatch();
        }

        @AfterEach
        void closeLiveColoursAndSettings() {

            controlSettingsMock.close();
            sidebarSettingsMock.close();
            uiColoursMock.close();
        }

        @AfterEach
        void restoreTheRosterAndBarThisCasePosed() {
            // Both holders are static, so a roster of stand-ins and a bar arranged here would otherwise
            // outlive the case that posed them.
            MapLayerRosters.restoreNonEmptyRoster();
            MapLayerArrangements.forgetTheArrangement();
        }

        @Test
        void resolveOpenerSpecStandsTheOpenerWhereTwoLayersPaint() {
            // The install a foreign mod's layer makes, and the only one in which arranging the bar can
            // change what the map shows.
            registerTwoLayersThatPaintBesideTheEmptyView();

            assertThat(LiveSidebarPlacement.resolveOpenerSpec(buildHostTabStyle()))
                .isNotNull();
        }

        @Test
        void resolveOpenerSpecDropsTheOpenerWhereOneLayerPaintsBesideTheEmptyView() {
            // KMU's own install. A door onto an empty room is worse than no door: one row to move with
            // nowhere to move it that changes which layer paints, and a hide the last-tab guard refuses.
            registerOneLayerThatPaintsBesideTheEmptyView();

            assertThat(LiveSidebarPlacement.resolveOpenerSpec(buildHostTabStyle()))
                .isNull();
        }

        @Test
        void resolveOpenerSpecDropsTheOpenerWhereTheEmptyViewStandsAlone() {
            // The empty view is not counted, so a roster of it alone is a roster of nothing to arrange
            // rather than a row of one.
            MapLayerRosters.replaceRosterWith(NoLayer.INSTANCE);

            assertThat(LiveSidebarPlacement.resolveOpenerSpec(buildHostTabStyle()))
                .isNull();
        }

        @Test
        void resolveOpenerSpecKeepsTheOpenerWhereThePlayerHasHiddenTheSecondLayer() {
            // The count is the roster's and never the offered row's: this button is the only way a
            // hidden tab comes back, so an opener that left once the row got short would strand the
            // arrangement that shortened it.
            registerTwoLayersThatPaintBesideTheEmptyView();

            MapLayerArrangements.arrangeBarWith(List.of(), List.of(SECOND_PAINTING_LAYER_ID));

            assertThat(LiveSidebarPlacement.resolveOpenerSpec(buildHostTabStyle()))
                .isNotNull();
        }

        @Test
        void resolveOpenerSpecStandsTheOpenerOnTheDevHatchOverARosterThatWouldDropIt() {
            // The hatch is how the box is reached at all on an install carrying one layer, which is
            // every install until a second one ships - so it has to beat the count rather than be
            // read beside it.
            registerOneLayerThatPaintsBesideTheEmptyView();

            openTheArrangementOpenerHatch();

            assertThat(LiveSidebarPlacement.resolveOpenerSpec(buildHostTabStyle()))
                .isNotNull();
        }

        @Test
        void resolveOpenerSpecAsksForTheOpenerUnchangedWhereItStands() {
            // Nothing about the control moves with the gate: the band is handed the same mark and the
            // same box it always was, so what changes is only whether it is handed one at all.
            registerTwoLayersThatPaintBesideTheEmptyView();

            var hostStyle = buildHostTabStyle();
            var openerAskedForDirectly = BarOpeners.buildOpenerSpec(hostStyle);

            var opener = LiveSidebarPlacement.resolveOpenerSpec(hostStyle);

            assertThat(opener.icon())
                .isEqualTo(openerAskedForDirectly.icon());
            assertThat(opener.style())
                .isEqualTo(openerAskedForDirectly.style());
        }

        // The hatch as its row ships: the count is what answers.
        private void closeTheArrangementOpenerHatch() {
            controlSettingsMock
                .when(KmuMapControlSettings::isMapLayerArrangementOpenerAlwaysShown)
                .thenReturn(false);
        }

        // The hatch a player opens to reach the box whatever the roster holds.
        private void openTheArrangementOpenerHatch() {
            controlSettingsMock
                .when(KmuMapControlSettings::isMapLayerArrangementOpenerAlwaysShown)
                .thenReturn(true);
        }

        // The host row the opener is asked to stand in: the strip's own style, boxed as the sector map
        // boxes it.
        private TabStyle buildHostTabStyle() {
            return SidebarStyles.buildStripTabStyle(HOST_BAND_HEIGHT)
                .withTabBox(new TabBox(HOST_TAB_WIDTH, HOST_TAB_HEIGHT, HOST_TAB_GAP));
        }

        // KMU's own install: the empty view and the one layer that paints.
        private void registerOneLayerThatPaintsBesideTheEmptyView() {
            MapLayerRosters.replaceRosterWith(
                NoLayer.INSTANCE,
                buildLayerMockUnder(FIRST_PAINTING_LAYER_ID));
        }

        // The same roster with a second painting layer on the end, which is the install a foreign mod's
        // layer makes and the only one there is anything to arrange on.
        private void registerTwoLayersThatPaintBesideTheEmptyView() {
            MapLayerRosters.replaceRosterWith(
                NoLayer.INSTANCE,
                buildLayerMockUnder(FIRST_PAINTING_LAYER_ID),
                buildLayerMockUnder(SECOND_PAINTING_LAYER_ID));
        }

        // A registered layer that is not the empty view, which is the whole of what makes it count. Its
        // ID is stubbed because the roster arbitrates by ID and the arrangement names layers by one.
        private MapLayer buildLayerMockUnder(String layerId) {

            var layerMock = mock(MapLayer.class);
            when(layerMock.getId())
                .thenReturn(layerId);

            return layerMock;
        }
    }

    @Nested
    class BuildBodyControls {

        @Test
        void buildBodyControlsOpensTheBodyUnderThePanelsOwnScreen() {
            // Every control the body opens writes the preference of the screen its panel draws for, so the
            // screen the body is built under is the one the panel carries.
            var panelScope = ScreenMemoryScopes.createStandInScreen();
            var activeLayerMock = mock(MapLayer.class);

            LiveSidebarPlacement.buildBodyControls(activeLayerMock, buildPicksUnder(panelScope));

            verify(activeLayerMock)
                .getBodyControls(panelScope);
        }

        @Test
        void buildBodyControlsAsksNoScreenOfItsOwn() {
            // The live screen goes unread, which is the half a passing scope cannot show: a layout that
            // resolved one here would answer the panel's own question a second time, and the two part
            // company whenever one host lays its body out while the other screen is up - filing that
            // panel's clicks under the screen the player is not looking at.
            var activeLayerMock = mock(MapLayer.class);

            try (var screensMock = mockStatic(MapLayerScreens.class)) {

                LiveSidebarPlacement.buildBodyControls(
                    activeLayerMock,
                    buildPicksUnder(ScreenMemoryScopes.createStandInScreen()));

                screensMock.verifyNoInteractions();
            }
        }

        // One screen's picks under the given scope. The tab and the hide are left as bare stand-ins,
        // since which of the three the body is built from is the whole of what these cases read.
        private ScreenLayerPicks buildPicksUnder(ScreenMemoryScope memoryScope) {
            return new ScreenLayerPicks(
                mock(ActiveLayerSelection.class),
                new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class)),
                memoryScope);
        }
    }

    @Nested
    class ResolveMapPlacement {

        @Test
        void resolveMapPlacementDrawsNothingWhenTheTabFontCannotLoad() {
            // The layout snaps every tab to its own measured label, so a face that will not load leaves
            // the panel unmeasurable rather than merely unstyled. Answering null is what lets the caller
            // draw nothing and consume nothing that frame, instead of laying a row out at no width and
            // then hit-testing it - which would take clicks over the map underneath.
            var selectionMock = mock(ActiveLayerSelection.class);
            var panel = buildPanelWithAnUnloadableFace(selectionMock);

            try (var fontsMock = mockStatic(LazyFontCache.class);
                    var settingsMock = mockStatic(KmuMapSidebarSettings.class)) {

                fontsMock
                    .when(() -> LazyFontCache.loadByFace(any()))
                    .thenReturn(null);

                assertThat(LiveSidebarPlacement.resolveMapPlacement(panel))
                    .isNull();
            }

            // The measurer is loaded before anything else is read, so a frame that cannot draw costs no
            // screen pick and no roster walk either. Read through the pick, the one collaborator the
            // panel hands over rather than resolves statically.
            verifyNoInteractions(selectionMock);
        }

        // A host panel whose look names a face nothing can load, which is the one arrangement every case
        // here is about. The font is left unnamed because the cache read is stood in for wholesale - what
        // the face would have resolved to never comes up.
        private SidebarHostPanel buildPanelWithAnUnloadableFace(ActiveLayerSelection selection) {

            var tabStyleMock = mock(TabStyle.class);

            when(tabStyleMock.face())
                .thenReturn(new TextFace(null, TAB_FACE_SIZE));

            return new SidebarHostPanel(
                tabStyleMock,
                mock(TabPanelController.class),
                new ScreenLayerPicks(
                    selection,
                    new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class)),
                    ScreenMemoryScopes.createStandInScreen()),
                Set.of());
        }
    }
}
