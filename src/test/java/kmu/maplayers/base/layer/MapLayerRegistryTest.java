package kmu.maplayers.base.layer;

import kmlib.testfixtures.logging.LogAppenderFake;
import kmlib.testfixtures.starsector.memory.SectorMemoryFake;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.settings.KmuMapSidebarSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the framework registry's contract with fake layers: the tab order it hands back, the pick an
 * untouched save resolves to, and the one answer everything that paints hangs off - which layer is in
 * play on the screen showing this frame, and the show-or-hide state folded into it so a hidden screen
 * resolves to nothing to draw.
 *
 * <p>Where those picks live, and the keys they ride in the save under, are {@link MapLayerScreensTest}'s;
 * the generic persisted-pick logic is {@link PersistedActiveLayerSelectionTest}'s and
 * {@link PersistedMapLayerVisibilityTest}'s. What is left here is the composition: the roster, the fold,
 * and that both follow the screen the player is looking at.
 *
 * <p>A hide is acted on only for a screen that has a control able to reverse it, so every case posing
 * hidden layers poses that control too. The rule behind it is
 * {@link ControlBackedMapLayerVisibilityTest}'s.
 */
final class MapLayerRegistryTest {

    // The frozen sector-memory keys the cases pose picks through. Pinned as literals in
    // MapLayerScreensTest, which is what a rename fails against; named here to seed a state, not to
    // assert one.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";
    private static final String MAP_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_map";
    private static final String INTEL_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_intel";

    // A hide pace long enough that a reading taken straight after the flip is unmistakably part-way down
    // the ramp rather than past its end, which is the state the registry must still hand a renderer for.
    private static final float LONG_HIDE_FADE_SECONDS = 1_000f;

    // The machinery of the sector being drawn, which the registry passes through rather than
    // resolves. One for the class, so a case asserting it reached the layer is comparing against
    // the very object it handed in.
    private final SectorMapMachinery machinery = new SectorMapMachinery(null);

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);

    private final IntelScreenViewFake intelScreenFake = new IntelScreenViewFake();

    // The hide ramp is paced by a shipped knob, and that read reaches LunaLib, which no test has. Stood
    // in for the class so every case names a pace: Mockito's own default answers a pace of nothing, which
    // makes each fade a cut and leaves these cases describing the settled answers they are about. The
    // ramp between them is PersistedMapLayerVisibilityTest's subject, and the one case here that wants a
    // reading from part-way down winds the pace up for itself.
    private MockedStatic<KmuMapSidebarSettings> mapLayerSettingsMock;

    private SectorMemoryFake sectorMemoryFake;

    @BeforeEach
    void registerTwoFakeLayers() {

        when(firstLayerMock.getId())
            .thenReturn("first");
        when(secondLayerMock.getId())
            .thenReturn("second");

        // Second layer offers itself as the default, so an untouched save resolves to it - the same
        // shape the real composition root leaves, the leading tab declining the pick.
        when(secondLayerMock.isOfferedAsDefaultPick())
            .thenReturn(true);

        MapLayerRosters.replaceRosterWith(firstLayerMock, secondLayerMock);

        // The screens are static, so one left wired would outlive its test. Handing a fresh fake per
        // test starts each from the intel screen closed rather than wherever a neighbour left it - and
        // keeps the live binding, which reaches into a running game, out of the suite.
        MapLayerScreens.registerIntelScreen(intelScreenFake);

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
    void restoreARegisteredRoster() {
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @AfterEach
    void forgetTheControlsThisCaseStood() {
        // The screens are process-wide and the latch saying one of them has a control never clears
        // itself, so a case that stood one would leave every later case acting on stored hides where a
        // run with no control reads shown - a difference that shows up as whichever case happened to
        // run next.
        MapLayerScreenControls.forgetControlsAttached();
    }

    @AfterEach
    void forgetThePictureThisCaseDrew() {
        // The other half of the same problem. A screen holds what was last on it so a dissolve has a
        // picture to finish, and these cases pose switched-off screens directly - so a case that drew a
        // stand-in layer would leave the next one's dissolve showing a layer from a roster that no
        // longer exists.
        MapLayerScreenControls.forgetDrawnLayers();
    }

    @AfterEach
    void closeTheSectorMemory() {
        sectorMemoryFake.close();
    }

    @Nested
    class GetLayers {

        @Test
        void getLayersReturnsTheRegisteredLayersInOrder() {

            assertThat(MapLayerRegistry.getLayers())
                .containsExactly(firstLayerMock, secondLayerMock);
        }
    }

    @Nested
    class RegisterLayer {

        @Test
        void registerLayerAppendsToTheRightHandEndOfTheRow() {
            // Registration order is row order, and it is all the order there is: nothing on a layer
            // states where it belongs, so a layer arriving later lands to the right of everything
            // already there.
            var thirdLayerMock = createLayerMockWithId("third");

            MapLayerRegistry.registerLayer(thirdLayerMock);

            assertThat(MapLayerRegistry.getLayers())
                .containsExactly(firstLayerMock, secondLayerMock, thirdLayerMock);
        }

        @Test
        void registerLayerReachesARosterThatHasAlreadyBeenRead() {
            // The constraint the whole additive form is shaped by: a mod that depends on this one
            // loads after it, so its layer registers after the bar has been drawn from the roster at
            // least once. A registry that settled its row on first read would leave that mod's tab
            // off until the next load.
            var lateLayerMock = createLayerMockWithId("late");

            assertThat(MapLayerRegistry.getLayers())
                .containsExactly(firstLayerMock, secondLayerMock);

            MapLayerRegistry.registerLayer(lateLayerMock);

            assertThat(MapLayerRegistry.getLayers())
                .containsExactly(firstLayerMock, secondLayerMock, lateLayerMock);
        }

        @Test
        void registerLayerPutsALayerSharingAnIdInThePlaceOfTheOneItReplaces() {
            // Two mods shipping one id will happen, and both tabs would read and write the one stored
            // pick that names it. Arbitrated, the row holds one tab that the save agrees with; left
            // alone, it would hold two the save cannot tell apart.
            var replacementLayerMock = createLayerMockWithId("first");

            MapLayerRegistry.registerLayer(replacementLayerMock);

            assertThat(MapLayerRegistry.getLayers())
                .containsExactly(replacementLayerMock, secondLayerMock);
        }

        @Test
        void registerLayerLeavesTheRowAloneWhenOneLayerRegistersTwice() {
            // Not every second registration is a clash: a mod wiring from two lifecycle hooks, or a
            // composition root run again, re-registers what is already standing. The row it was
            // already in is the row it stays in.
            MapLayerRegistry.registerLayer(firstLayerMock);

            assertThat(MapLayerRegistry.getLayers())
                .containsExactly(firstLayerMock, secondLayerMock);
        }

        @Test
        void registerLayerReportsNothingWhenOneLayerRegistersTwice() {
            // The arbitration line names the two sides of a clash, so writing one for a layer giving
            // way to itself would report a conflict between a mod and itself - and it would be
            // written on every load of any mod that registers more than once.
            assertThat(recordLinesWrittenWhileRegistering(firstLayerMock))
                .isEmpty();
        }

        @Test
        void registerLayerReportsTheTwoLayersThatShareAnId() {
            // The arbitration is silent to the player, so the log is the only place a mod author
            // finds out why their tab is not the one on the bar - which makes the line naming both
            // sides the whole of what the outcome is worth.
            var replacementLayerMock = createLayerMockWithId("first");
            var writtenLines = recordLinesWrittenWhileRegistering(replacementLayerMock);

            assertThat(writtenLines)
                .hasSize(1);
            assertThat(writtenLines.get(0))
                .contains("first")
                .contains(firstLayerMock.getClass().getName())
                .contains(replacementLayerMock.getClass().getName());
        }
    }

    @Nested
    class ResolveLayerById {

        @Test
        void resolveLayerByIdAnswersTheLayerRegisteredUnderIt() {

            assertThat(MapLayerRegistry.resolveLayerById("first"))
                .isSameAs(firstLayerMock);
        }

        @Test
        void resolveLayerByIdIsNullForAnIdNothingRegistered() {
            // What a save holds after the mod that shipped that layer is uninstalled, which is why
            // the answer is an absence to fall back from rather than a fault.
            assertThat(MapLayerRegistry.resolveLayerById("removed_long_ago"))
                .isNull();
        }
    }

    @Nested
    class GetDefaultLayer {

        @Test
        void getDefaultLayerAnswersTheOfferingLayerRatherThanTheLeadingOne() {
            // The two facts the old single call spelled separately, now one: the row leads with a
            // layer that declines, and the pick is the one that offered.
            assertThat(MapLayerRegistry.getDefaultLayer())
                .isSameAs(secondLayerMock);
        }

        @Test
        void getDefaultLayerAnswersTheEarlierOfTwoLayersOffering() {
            // Which is what makes load order settle the pick without any mod stating a rank: a
            // foreign layer offering itself arrives after the host's and loses by arriving later.
            when(firstLayerMock.isOfferedAsDefaultPick())
                .thenReturn(true);

            assertThat(MapLayerRegistry.getDefaultLayer())
                .isSameAs(firstLayerMock);
        }

        @Test
        void getDefaultLayerFallsBackToTheLeadingLayerWhereNobodyOffers() {
            // A roster of layers that all decline is a roster nobody arranged - foreign layers alone,
            // say. Answering nothing there would leave a bar of tabs with none of them lit and a map
            // that paints nothing to explain it.
            MapLayerRosters.replaceRosterWith(firstLayerMock);

            assertThat(MapLayerRegistry.getDefaultLayer())
                .isSameAs(firstLayerMock);
        }

        @Test
        void getDefaultLayerIsNullBeforeAnyLayerIsRegistered() {

            MapLayerRosters.forgetEveryLayer();

            assertThat(MapLayerRegistry.getDefaultLayer())
                .isNull();
        }
    }

    @Nested
    class GetDrawnLayer {

        @Test
        void getDrawnLayerAnswersFromTheShowingScreensPick() {

            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(MapLayerRegistry.getDrawnLayer())
                .isSameAs(firstLayerMock);
        }

        @Test
        void getDrawnLayerIsNullOnceTheShowingScreensLayersHaveFadedOff() {
            // The one read hiding hangs off: every pass driven by the drawn layer already draws nothing
            // for a null, so this takes the overlay, the labels and the hover box off together.
            hideTheMapScreensLayers();

            assertThat(MapLayerRegistry.getDrawnLayer())
                .isNull();
        }

        @Test
        void getDrawnLayerAnswersThePickWhileOnlyTheOtherScreensLayersAreHidden() {
            // The per-screen half of the same gate: the intel screen hidden must leave the sector map
            // painting its own pick, which is the whole reason the two picks are kept apart.
            storeADifferentPickOnEachScreen();
            hideTheIntelScreensLayers();
            intelScreenFake.setIntelTabOpen(false);

            assertThat(MapLayerRegistry.getDrawnLayer())
                .isSameAs(secondLayerMock);
        }

        @Test
        void getDrawnLayerAnswersAShownScreenWithoutReadingTheHidePace() {
            // A screen with its layers on is answered off the stored pick alone. Worth pinning rather
            // than left as an accident of the order two conditions are written in: the fade is derived
            // from a clock and a settings read, and asking for it on every frame the layers are simply
            // on would put both on the map's hottest path to say what the pick has already said.
            assertThat(MapLayerRegistry.getDrawnLayer())
                .isSameAs(secondLayerMock);

            mapLayerSettingsMock
                .verifyNoInteractions();
        }

        @Test
        void getDrawnLayerIsNullBeforeAnyLayerIsRegistered() {
            // The map surface can be asked for a frame before the composition root has run, so the
            // registry has to answer "no pick" rather than leave a caller to find out by throwing.
            MapLayerRosters.forgetEveryLayer();
            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.getDrawnLayer())
                .isNull();
        }
    }

    @Nested
    class ResolveDrawnMapRenderer {

        @Test
        void resolveDrawnMapRendererAsksTheDrawnLayerAboutTheMachineryItWasHanded() {
            // The roster is the process's while a renderer is one sector's, so the registry must
            // pass the machinery through rather than resolve one of its own - a registry that
            // picked the running sector's would hand every surface the same renderer however many
            // sectors were being drawn. Pinned by stubbing that one machinery and no other, so a
            // registry substituting its own would find nothing stubbed for it.
            var layerRendererMock = mock(MapLayerRenderer.class);

            when(secondLayerMock.resolveRenderer(machinery))
                .thenReturn(layerRendererMock);

            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.resolveDrawnMapRenderer(machinery))
                .isSameAs(layerRendererMock);
        }

        @Test
        void resolveDrawnMapRendererIsNullWhenTheDrawnLayerPaintsNothing() {
            // The switch-only tab, whose whole expression is a null renderer - and the pre-
            // registration frame below it, answered the same way so no pass driven by the drawn
            // layer needs a case for either. Stated rather than left to the stub's own default, or
            // the case would pass on a layer that was never asked at all.
            when(secondLayerMock.resolveRenderer(machinery))
                .thenReturn(null);

            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.resolveDrawnMapRenderer(machinery))
                .isNull();
        }

        @Test
        void resolveDrawnMapRendererIsNullBeforeAnyLayerIsRegistered() {

            MapLayerRosters.forgetEveryLayer();
            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.resolveDrawnMapRenderer(machinery))
                .isNull();
        }

        @Test
        void resolveDrawnMapRendererStillDrawsPartWayThroughTheHide() {
            // What the fade is for: the pick already reads hidden, and the overlay must go on being
            // handed a renderer until the dissolve is over, or the picture would blink out from under
            // the sidebar that is still thinning beside it.
            var layerRendererMock = mock(MapLayerRenderer.class);

            when(secondLayerMock.resolveRenderer(machinery))
                .thenReturn(layerRendererMock);

            startHidingTheMapScreensLayers();

            assertThat(MapLayerRegistry.resolveDrawnMapRenderer(machinery))
                .isSameAs(layerRendererMock);
        }
    }

    @Nested
    class IsDrawnLayer {

        @Test
        void isDrawnLayerIsTrueOnlyForTheResolvedDrawnLayer() {

            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.isDrawnLayer(secondLayerMock))
                .isTrue();
            assertThat(MapLayerRegistry.isDrawnLayer(firstLayerMock))
                .isFalse();
        }

        @Test
        void isDrawnLayerAnswersFromTheIntelPickWhileTheIntelScreenIsUp() {
            // The bug this guards: each screen keeps its own tab, so an overlay reading one fixed
            // screen's pick painted the sector map's choice onto the intel screen - No Layer on the
            // intel tab could not turn it off there.
            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(MapLayerRegistry.isDrawnLayer(firstLayerMock))
                .isTrue();
            assertThat(MapLayerRegistry.isDrawnLayer(secondLayerMock))
                .isFalse();
        }

        @Test
        void isDrawnLayerAnswersFromTheMapPickWhileTheIntelScreenIsNotUp() {

            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(false);

            assertThat(MapLayerRegistry.isDrawnLayer(secondLayerMock))
                .isTrue();
            assertThat(MapLayerRegistry.isDrawnLayer(firstLayerMock))
                .isFalse();
        }

        @Test
        void isDrawnLayerIsFalseForEveryLayerOnceTheScreensLayersHaveFadedOff() {
            // A hidden screen has no layer in play at all, which is what stands each layer's own state
            // down without a read of its own - the default pick included, since it is the one a layer
            // would otherwise go on thinking it held.
            hideTheMapScreensLayers();

            assertThat(MapLayerRegistry.isDrawnLayer(secondLayerMock))
                .isFalse();
            assertThat(MapLayerRegistry.isDrawnLayer(firstLayerMock))
                .isFalse();
        }
    }

    @Nested
    class IsDrawnLayerOn {

        @Test
        void isDrawnLayerOnAnswersTheScreenHandedInRatherThanTheShowingOne() {
            // The gate a layer's own state reads, for a caller that already knows which panel it is
            // answering for - posed the same way as the read beneath it, with the visor up.
            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(MapLayerRegistry.isDrawnLayerOn(MapLayerScreens.getMapPicks(), secondLayerMock))
                .isTrue();
            assertThat(MapLayerRegistry.isDrawnLayerOn(MapLayerScreens.getMapPicks(), firstLayerMock))
                .isFalse();
        }
    }

    // Puts the two screens on different tabs, which is what lets a case tell which key an answer came
    // off: with one pick on both keys, a registry reading the wrong one would still answer correctly.
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

        MapLayerScreens
            .resolveLivePicks()
            .layerVisibility()
            .recordControlAttached();
    }

    // Puts the map screen part-way down its hide ramp. The flip is made while the pace still reads as
    // nothing, so the ramp is recorded as setting off from the settled shown end whatever an earlier case
    // left on the process-wide visibility; the pace is only then wound long, which leaves a reading taken
    // straight afterwards a long way short of the end.
    private void startHidingTheMapScreensLayers() {

        recordAControlOnTheShowingScreen(false);

        // A drawn frame before the switch-off, which is what a session always has: every control able to
        // switch a screen off stands on that screen, so the screen is being drawn when one is used. It is
        // that frame the screen catches its picture on, and what it dissolves afterwards.
        MapLayerRegistry.getDrawnLayer();

        MapLayerScreens
            .getMapPicks()
            .layerVisibility()
            .showLayers(false);

        mapLayerSettingsMock
            .when(KmuMapSidebarSettings::getMapLayerHideFadeSeconds)
            .thenReturn(LONG_HIDE_FADE_SECONDS);
    }

    // Registers one layer with the registry's own log captured, and hands back what it wrote.
    //
    // The capture is KMLib's, which owns the attachment, the level and the additivity for the length
    // of the call: the arbitration line is written at a level the running game decides, so a capture
    // reading it off whatever level happened to be in force would report silence for a line the
    // registry did write.
    private static List<String> recordLinesWrittenWhileRegistering(MapLayer layer) {

        return LogAppenderFake
            .captureLogOf(MapLayerRegistry.class, () -> MapLayerRegistry.registerLayer(layer))
            .getMessages();
    }

    // A layer answering nothing but the id it registers under, which is all the registration cases
    // are about: where it lands in the row, and who it displaces.
    private static MapLayer createLayerMockWithId(String layerId) {

        var layerMock = mock(MapLayer.class);

        when(layerMock.getId())
            .thenReturn(layerId);

        return layerMock;
    }

}
