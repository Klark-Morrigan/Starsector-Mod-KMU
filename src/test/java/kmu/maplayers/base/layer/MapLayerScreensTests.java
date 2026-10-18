package kmu.maplayers.base.layer;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.settings.KmuMapSidebarSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the two screens' state: the four frozen save keys each screen's picks read and write, the scope
 * each carries for the preferences set on its own panel, that neither screen's pick moves the other's,
 * and that every reading answers from the screen the player is looking at - the drawn layer among them,
 * with the show-or-hide state folded in so a hidden screen resolves to nothing to draw. The generic
 * persisted-pick logic is {@link PersistedActiveLayerSelectionTests}'s and
 * {@link PersistedMapLayerVisibilityTests}'s, the shape of a composed key {@link ScreenMemoryScopeTests}'s,
 * and the dissolve's memory {@link ScreenDrawnLayerTests}'; what is added here is which screen gets which,
 * and the live-screen rule.
 *
 * <p>The screens themselves are stand-in gates: which concrete screens exist is the composition root's
 * business, and this pins only that the showing one wins.
 *
 * <p>A hide is acted on only for a screen that has a control able to reverse it, so every case posing
 * hidden layers poses that control too. The rule behind it, and what a screen without one reads, are
 * {@link ControlBackedMapLayerVisibilityTests}'s.
 */
final class MapLayerScreensTests {

    // The frozen sector-memory keys, pinned as literals so a rename - which would silently reset every
    // existing save - fails this test rather than shipping. One per screen, so the two picks stay
    // independent.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";

    // The same for each screen's show-or-hide pick, pinned for the same reason.
    private static final String MAP_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_map";
    private static final String INTEL_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_intel";

    // A preference of no screen's in particular, for reading which screen a carried scope composes for.
    // Its own key rather than one of the two above, so the case is about the scope rather than about a
    // holder that happens to use it.
    private static final String PREFERENCE_KEY = "$kmu_political_preference";

    // The two ends of the hide ramp, as a consumer reads them.
    private static final float FULLY_HIDDEN = 0f;
    private static final float FULLY_SHOWN = 1f;

    // A hide pace long enough that a reading taken straight after the flip is unmistakably part-way down
    // the ramp rather than past its end, which is the state a screen must still answer its layer in.
    private static final float LONG_HIDE_FADE_SECONDS = 1_000f;

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);

    private final IntelScreenViewFake intelScreenFake = new IntelScreenViewFake();

    // The hide ramp is paced by a shipped knob, and that read reaches LunaLib, which no test has. Stood
    // in for the class so every case names a pace: Mockito's own default answers a pace of nothing, which
    // makes each fade a cut and leaves these cases describing the settled answers they are about. The
    // ramp between them is KMLib's ClockedTraverseFractionTests' subject, and the one case here that
    // wants a reading from part-way down winds the pace up for itself.
    private MockedStatic<KmuMapSidebarSettings> mapLayerSettingsMock;

    private SectorMemoryFake sectorMemoryFake;

    private MapLayerScreens screens;

    @BeforeEach
    void wireScreensOverTwoLayers() {

        when(firstLayerMock.getId())
            .thenReturn("first");
        when(secondLayerMock.getId())
            .thenReturn("second");

        // Second layer offers itself as the default, so an untouched save resolves to it - the same
        // shape the real composition root leaves, the leading tab declining the pick.
        when(secondLayerMock.isOfferedAsDefaultPick())
            .thenReturn(true);

        // A fresh fake per case starts each from the intel screen closed, and keeps the live binding,
        // which reaches into a running game, out of the suite.
        screens = MapLayerRosters.createScreensOver(
            MapLayerRosters.createRosterOf(firstLayerMock, secondLayerMock),
            MapLayerArrangements.createUnarrangedBar(),
            intelScreenFake);

        sectorMemoryFake = new SectorMemoryFake();
    }

    @BeforeEach
    void stubTheHidePace() {
        mapLayerSettingsMock = mockStatic(KmuMapSidebarSettings.class);
    }

    @AfterEach
    void releaseTheHidePace() {
        mapLayerSettingsMock.close();
    }

    @AfterEach
    void closeTheSectorMemory() {
        sectorMemoryFake.close();
    }

    @Nested
    class CreateUnwiredScreens {

        @Test
        void drawsNothing() {
            // What a sector nothing was installed on reads, so a pass reaching it draws nothing rather
            // than branching on an absence.
            assertThat(MapLayerScreens.createUnwiredScreens().resolveDrawnLayer())
                .isNull();
        }
    }

    @Nested
    class ResolveScreensIn {

        @Test
        void answersTheScreensSeatedOnTheMachinery() {
            // The passes the engine builds reach the screens only through the machinery they resolve,
            // so what they read has to be the very screens the installer seated there.
            var machinery = new SectorMapMachinery(mock(SectorAPI.class), screens);

            assertThat(MapLayerScreens.resolveScreensIn(machinery))
                .isSameAs(screens);
        }

        @Test
        void answersUnwiredScreensForMachineryNothingSeated() {
            // The detached machinery is nobody's sector, so it carries no roster to draw from.
            var resolvedScreens = MapLayerScreens.resolveScreensIn(new SectorMapMachinery(null));

            assertThat(resolvedScreens)
                .isNotSameAs(screens);
            assertThat(resolvedScreens.getLayerRegistry().getLayers())
                .isEmpty();
        }
    }

    @Nested
    class ForgetControlsAttached {

        @Test
        void unwindsBothScreensTogether() {
            // A fact about the run rather than about either screen, so a campaign load unwinding one
            // and not the other would leave one screen acting on its stored hide and the other not.
            screens.getMapPicks().layerVisibility().recordControlAttached();
            screens.getIntelPicks().layerVisibility().recordControlAttached();

            screens.forgetControlsAttached();

            assertThat(screens.getAllScreenPicks())
                .extracting(picks -> picks.layerVisibility().hasControlBeenAttached())
                .containsExactly(false, false);
        }
    }

    @Nested
    class GetAllScreenPicks {

        @Test
        void handsBackEveryScreenTheModHas() {
            // The roster a pass owing every panel walks - a self-heal clearing a choice that lapsed for
            // both at once. A screen missing from it is a panel that pass silently skips, so what is
            // pinned is that the two named here are both on it and nothing else is.
            assertThat(screens.getAllScreenPicks())
                .containsExactlyInAnyOrder(
                    screens.getMapPicks(),
                    screens.getIntelPicks());
        }

        @Test
        void carriesEachScreensOwnScope() {
            // Each entry is the screen's whole picks rather than a bare scope, so a caller walking the
            // roster to resolve a preference key cannot pair one screen's scope with another's tab.
            assertThat(screens.getAllScreenPicks())
                .extracting(picks -> picks.memoryScope().resolveKeyFor(PREFERENCE_KEY))
                .containsExactlyInAnyOrder(
                    "$kmu_political_preference_map",
                    "$kmu_political_preference_intel");
        }

        @Test
        void carriesAPictureOfItsOwnPerScreen() {
            // What a screen is drawing is that screen's own memory, holding what was last on it so a
            // dissolve has something to finish. One shared between the panels would have the visor
            // dissolving the sector map's picture, which is the crossing the whole per-screen value
            // exists to make impossible.
            assertThat(screens.getMapPicks().drawnLayer())
                .isNotSameAs(screens.getIntelPicks().drawnLayer());
        }

        @Test
        void carriesTheRosterAndArrangementTheScreensWereBuiltOver() {
            // A screen's tabs are composed from what its picks carry, so both screens must carry the
            // roster their picks resolve against rather than one of their own.
            assertThat(screens.getAllScreenPicks())
                .allSatisfy(picks -> {
                    assertThat(picks.layerRegistry()).isSameAs(screens.getLayerRegistry());
                    assertThat(picks.layerArrangement()).isSameAs(screens.getLayerArrangement());
                });
        }
    }

    @Nested
    class GetMapPicks {

        @Test
        void selectsThroughTheFrozenMapKey() {

            screens
                .getMapPicks()
                .layerSelection()
                .selectLayer(firstLayerMock);

            assertThat(sectorMemoryFake.readStoredValue(MAP_ACTIVE_LAYER_KEY))
                .isEqualTo("first");
        }

        @Test
        void hidesThroughTheFrozenMapKey() {
            // The pair's second key, pinned beside the first: both are this screen's, so a pair built
            // from one screen's tab and the other's hiding fails here rather than in play.
            screens
                .getMapPicks()
                .layerVisibility()
                .showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(MAP_LAYERS_SHOWN_KEY))
                .isEqualTo(false);
        }

        @Test
        void carriesTheMapScreensOwnScope() {
            // The scope travels with the picks so that a preference set on this screen's panel lands in
            // the same screen's save as its tab and its hiding do. Carried wrongly, the panel would set a
            // preference the other screen reads back, with both cases above still green.
            assertThat(screens.getMapPicks().memoryScope().resolveKeyFor(PREFERENCE_KEY))
                .isEqualTo("$kmu_political_preference_map");
        }
    }

    @Nested
    class GetIntelPicks {

        @Test
        void selectsThroughTheFrozenIntelKey() {

            screens
                .getIntelPicks()
                .layerSelection()
                .selectLayer(firstLayerMock);

            assertThat(sectorMemoryFake.readStoredValue(INTEL_ACTIVE_LAYER_KEY))
                .isEqualTo("first");
        }

        @Test
        void hidesThroughTheFrozenIntelKey() {

            screens
                .getIntelPicks()
                .layerVisibility()
                .showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(INTEL_LAYERS_SHOWN_KEY))
                .isEqualTo(false);
        }

        @Test
        void carriesTheIntelScreensOwnScope() {
            // Its own, and not the map screen's: the two panels' preferences part here, so both are
            // pinned rather than one and the composition trusted for the other.
            assertThat(screens.getIntelPicks().memoryScope().resolveKeyFor(PREFERENCE_KEY))
                .isEqualTo("$kmu_political_preference_intel");
        }
    }

    @Nested
    class ResolvePicksFor {

        @Test
        void answersTheMapPicksForTheMapScope() {
            // A sidebar host names its screen by the scope its preferences are stored under, so what it
            // stores and what it draws cannot be two screens'.
            assertThat(screens.resolvePicksFor(MapLayerScreens.MAP_SCOPE))
                .isSameAs(screens.getMapPicks());
        }

        @Test
        void answersTheIntelPicksForTheIntelScope() {

            assertThat(screens.resolvePicksFor(MapLayerScreens.INTEL_SCOPE))
                .isSameAs(screens.getIntelPicks());
        }

        @Test
        void throwsForAScopeNamingNeitherScreen() {
            // A scope no screen is saved under is a wiring fault, and answering either screen for it
            // would cross one panel's preferences with the other's picks without a word.
            assertThatThrownBy(() -> screens.resolvePicksFor(ScreenMemoryScopes.createStandInScreen()))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ResolveLivePicks {

        @Test
        void handsBackTheShowingScreensOwnPicks() {
            // The whole value rather than any one part of it, and one resolution for all of it:
            // everything composed on top of this reads a tab, a show-or-hide state and a scope together,
            // and two resolutions could answer them for different screens.
            intelScreenFake.setIntelTabOpen(true);

            assertThat(screens.resolveLivePicks())
                .isSameAs(screens.getIntelPicks());

            intelScreenFake.setIntelTabOpen(false);

            assertThat(screens.resolveLivePicks())
                .isSameAs(screens.getMapPicks());
        }

        @Test
        void answersTheMapPicksForScreensWiredToNoIntelScreen() {
            // The sector map is the overlay's home, so screens with no intel screen to ask always
            // answer for it.
            var screensWithoutIntel = MapLayerRosters.createScreensOverNoLayer();

            assertThat(screensWithoutIntel.resolveLivePicks())
                .isSameAs(screensWithoutIntel.getMapPicks());
        }

        @Test
        void carriesTheShowingScreensOwnStoredPick() {
            // The value is also what a control on a screen's own chrome is stood through, so it has to
            // carry the stored pick such a control shows and moves - and writing through it must move
            // the screen that was up and leave the other where it was, which is the whole of what a
            // control on one screen's chrome may do.
            intelScreenFake.setIntelTabOpen(true);

            screens
                .resolveLivePicks()
                .layerVisibility()
                .getStoredVisibility()
                .showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(INTEL_LAYERS_SHOWN_KEY))
                .isEqualTo(false);

            assertThat(sectorMemoryFake.readStoredValue(MAP_LAYERS_SHOWN_KEY))
                .isNull();
        }
    }

    @Nested
    class ResolveDrawnLayer {

        @Test
        void answersFromTheShowingScreensPick() {

            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(screens.resolveDrawnLayer())
                .isSameAs(firstLayerMock);
        }

        @Test
        void isNullOnceTheShowingScreensLayersHaveFadedOff() {
            // The one read hiding hangs off: every pass driven by the drawn layer already draws nothing
            // for a null, so this takes the overlay, the labels and the hover box off together.
            hideTheMapScreensLayers();

            assertThat(screens.resolveDrawnLayer())
                .isNull();
        }

        @Test
        void answersThePickWhileOnlyTheOtherScreensLayersAreHidden() {
            // The per-screen half of the same gate: the intel screen hidden must leave the sector map
            // painting its own pick, which is the whole reason the two picks are kept apart.
            storeADifferentPickOnEachScreen();
            hideTheIntelScreensLayers();
            intelScreenFake.setIntelTabOpen(false);

            assertThat(screens.resolveDrawnLayer())
                .isSameAs(secondLayerMock);
        }

        @Test
        void answersAShownScreenWithoutReadingTheHidePace() {
            // A screen with its layers on is answered off the stored pick alone. Worth pinning rather
            // than left as an accident of the order two conditions are written in: the fade is derived
            // from a clock and a settings read, and asking for it on every frame the layers are simply
            // on would put both on the map's hottest path to say what the pick has already said.
            assertThat(screens.resolveDrawnLayer())
                .isSameAs(secondLayerMock);

            mapLayerSettingsMock
                .verifyNoInteractions();
        }

        @Test
        void isNullBeforeAnyLayerIsRegistered() {
            // The map surface can be asked for a frame before the composition root has run, so the
            // screens have to answer "no pick" rather than leave a caller to find out by throwing.
            sectorMemoryFake.removeSector();

            assertThat(MapLayerRosters.createScreensOver().resolveDrawnLayer())
                .isNull();
        }

        @Test
        void stillAnswersTheLayerPartWayThroughTheHide() {
            // What the fade is for: the pick already reads hidden, and the overlay must go on being
            // drawn until the dissolve is over, or the picture would blink out from under the sidebar
            // that is still thinning beside it.
            startHidingTheMapScreensLayers();

            assertThat(screens.resolveDrawnLayer())
                .isSameAs(secondLayerMock);
        }
    }

    @Nested
    class IsDrawingLayer {

        @Test
        void isTrueOnlyForTheLayerOnTheScreen() {

            sectorMemoryFake.removeSector();

            assertThat(screens.getMapPicks().isDrawingLayer(secondLayerMock))
                .isTrue();
            assertThat(screens.getMapPicks().isDrawingLayer(firstLayerMock))
                .isFalse();
        }

        @Test
        void answersItsOwnScreenRatherThanTheShowingOne() {
            // The gate a layer's own state reads, for a caller that already knows which panel it is
            // answering for. Posed with the visor up, so a gate reading the showing screen instead
            // would answer the intel screen's pick.
            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(screens.getMapPicks().isDrawingLayer(secondLayerMock))
                .isTrue();
            assertThat(screens.getMapPicks().isDrawingLayer(firstLayerMock))
                .isFalse();
        }

        @Test
        void answersTheIntelScreensOwnPick() {
            // The bug the per-screen split guards: an overlay reading one fixed screen's pick painted
            // the sector map's choice onto the intel screen - No Layer on the intel tab could not turn
            // it off there.
            storeADifferentPickOnEachScreen();

            assertThat(screens.getIntelPicks().isDrawingLayer(firstLayerMock))
                .isTrue();
            assertThat(screens.getIntelPicks().isDrawingLayer(secondLayerMock))
                .isFalse();
        }

        @Test
        void isFalseForEveryLayerOnceTheScreensLayersHaveFadedOff() {
            // A hidden screen has no layer in play at all, which is what stands each layer's own state
            // down without a read of its own - the default pick included, since it is the one a layer
            // would otherwise go on thinking it held.
            hideTheMapScreensLayers();

            assertThat(screens.getMapPicks().isDrawingLayer(secondLayerMock))
                .isFalse();
            assertThat(screens.getMapPicks().isDrawingLayer(firstLayerMock))
                .isFalse();
        }
    }

    @Nested
    class AreLayersShownOnLiveScreen {

        @Test
        void answersFromTheShowingScreensPick() {
            // Per screen, so hiding on one leaves the other showing: the control sits on each screen's
            // own chrome, and a shared answer would empty a screen the player is not looking at.
            hideTheIntelScreensLayers();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(screens.areLayersShownOnLiveScreen())
                .isFalse();

            intelScreenFake.setIntelTabOpen(false);

            assertThat(screens.areLayersShownOnLiveScreen())
                .isTrue();
        }
    }

    @Nested
    class ResolveShownFadeOnLiveScreen {

        @Test
        void answersFromTheShowingScreensPick() {
            // What a pass multiplies into its alpha, and it follows the same screen the pick does - a
            // fade taken off the other screen would thin an overlay nobody asked to hide.
            hideTheIntelScreensLayers();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(screens.resolveShownFadeOnLiveScreen())
                .isEqualTo(FULLY_HIDDEN);

            intelScreenFake.setIntelTabOpen(false);

            assertThat(screens.resolveShownFadeOnLiveScreen())
                .isEqualTo(FULLY_SHOWN);
        }
    }

    // Puts the two screens on different tabs, which is what lets a case tell which key an answer came
    // off: with one pick on both keys, a reading off the wrong one would still answer correctly.
    private void storeADifferentPickOnEachScreen() {

        sectorMemoryFake.storeValue(MAP_ACTIVE_LAYER_KEY, "second");
        sectorMemoryFake.storeValue(INTEL_ACTIVE_LAYER_KEY, "first");
    }

    // Poses a save in which one screen's layers were switched off and their ramp has long since run out.
    // Seeded rather than flipped, so the reading is the settled one whatever the pace reads.
    //
    // The control that switched them off is posed with it, since a stored hide is acted on only for a
    // screen that has one - a case seeding the hide alone would be posing a screen whose layers are
    // still shown, and would pass for saying so.
    private void hideTheMapScreensLayers() {

        sectorMemoryFake.storeValue(MAP_LAYERS_SHOWN_KEY, false);
        recordAControlOnTheShowingScreen(false);
    }

    private void hideTheIntelScreensLayers() {

        sectorMemoryFake.storeValue(INTEL_LAYERS_SHOWN_KEY, false);
        recordAControlOnTheShowingScreen(true);
    }

    // Says a control stands on one of the two screens. It is recorded against the screen that is up,
    // which is all a control on a screen's own chrome could ever mean, so the tab is put there for the
    // recording; every case that cares which screen is up sets it for itself afterwards.
    private void recordAControlOnTheShowingScreen(boolean isIntelTabOpen) {

        intelScreenFake.setIntelTabOpen(isIntelTabOpen);

        MapLayerScreenControls.standAControlOnTheShownScreen(screens);
    }

    // Puts the map screen part-way down its hide ramp. The flip is made while the pace still reads as
    // nothing, so the ramp is recorded as setting off from the settled shown end; the pace is only then
    // wound long, which leaves a reading taken straight afterwards a long way short of the end.
    private void startHidingTheMapScreensLayers() {

        recordAControlOnTheShowingScreen(false);

        // A drawn frame before the switch-off, which is what a session always has: every control able to
        // switch a screen off stands on that screen, so the screen is being drawn when one is used. It is
        // that frame the screen catches its picture on, and what it dissolves afterwards.
        screens.resolveDrawnLayer();

        screens
            .getMapPicks()
            .layerVisibility()
            .showLayers(false);

        mapLayerSettingsMock
            .when(KmuMapSidebarSettings::getMapLayerHideFadeSeconds)
            .thenReturn(LONG_HIDE_FADE_SECONDS);
    }
}
