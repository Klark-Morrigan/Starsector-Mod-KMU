package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.animation.TraverseDurations;
import kmlib.math.geometry.BoxEdge;
import kmlib.starsector.ui.input.UiCursor;
import kmlib.starsector.ui.render.gl.style.WidgetStyle;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.sidebar.SidebarFoldSelection;
import kmu.settings.KmuMapKeybindSettings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the shortcut key that jumps to a layer, which every host shares because the panel offers the same
 * tabs on whichever screen it draws. The jump reads the player's rebound keycode rather than the layer's
 * default, so a clash with a screen's own bindings is settled by rebinding; it writes only the host's own
 * pick, so a press on one screen leaves the other screen's tab where it was; and it consumes only a press
 * it acted on, so every other key reaches the screen underneath.
 *
 * <p>Pins the shared "is the sidebar live" gate with it: a claim on the screen stands every host down,
 * which is what frees the shortcut keys above to type rather than switch tabs. Which things can claim it
 * is {@link ScreenClaim}'s to pin; what is pinned here is that a claim reaches the gate at all, and that
 * it settles the gate without the screen being read.
 *
 * <p>The screen's show-or-hide pick is pinned in the same pair, since it is the second thing to reach both
 * answers: the gate takes it crisply, so a panel switched off stops routing at once, and the paint takes
 * its ramp and multiplies it into whatever a claim has left, so two dissolves compose rather than one
 * winning. The ramp itself is {@link kmu.maplayers.base.layer.PersistedMapLayerVisibility}'s to pin.
 */
final class BaseSidebarHostTest {

    private static final int UNBOUND = 0;
    private static final int FIRST_KEYCODE = 49;
    private static final int SECOND_KEYCODE = 25;
    private static final int UNRELATED_KEYCODE = 30;

    private static final String FIRST_SETTING_KEY = "kmu_testFirstLayerKey";
    private static final String SECOND_SETTING_KEY = "kmu_testSecondLayerKey";

    // Where each registered layer's tab sits in the row, the panel building its tabs from the same registry
    // in the same order - so these are the indices a blink has to land on.
    private static final int FIRST_LAYER_TAB_INDEX = 0;
    private static final int SECOND_LAYER_TAB_INDEX = 1;

    // A whole traverse in one step, so a started blink stands at its peak and an unstarted one at rest -
    // telling the two apart in one number rather than by walking frames. The same pace each way, since
    // only the rise is read here and the paces the panel actually runs at are KMLib's to pin.
    private static final float FULL_STEP_SECONDS = 1f;
    private static final TraverseDurations DURATIONS = TraverseDurations.createSymmetric(1f);
    private static final float TOLERANCE = 0.0001f;

    // The pointer parked well off the panel, so nothing the frame advances can be a hover and a lit tab can
    // only have come from the blink.
    private static final float OFF_PANEL_COORDINATE = 5000f;

    // The two ends of a screen's show-or-hide ramp, as the panel reads them.
    private static final float FULLY_SHOWN = 1f;
    private static final float FULLY_HIDDEN = 0f;

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);

    @BeforeEach
    void registerTwoBoundLayers() {

        when(firstLayerMock.getShortcutSettingKey())
            .thenReturn(FIRST_SETTING_KEY);
        when(firstLayerMock.getDefaultShortcutKeycode())
            .thenReturn(FIRST_KEYCODE);
        when(secondLayerMock.getShortcutSettingKey())
            .thenReturn(SECOND_SETTING_KEY);
        when(secondLayerMock.getDefaultShortcutKeycode())
            .thenReturn(SECOND_KEYCODE);

        // The registry is static, so a neighbour's layers would otherwise outlive their test.
        MapLayerRegistry.registerLayers(List.of(firstLayerMock, secondLayerMock), firstLayerMock);
    }

    @Nested
    class HandleKeyPress {

        @Test
        void handleKeyPressJumpsToTheLayerBoundToThePressedKey() {

            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            var eventMock = mockKeyPress(SECOND_KEYCODE);

            try (MockedStatic<KmuMapKeybindSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            verify(layerSelectionMock)
                .selectLayer(secondLayerMock);

            // Consumed so the key does not also trigger a binding on the screen underneath sharing it.
            verify(eventMock)
                .consume();
        }

        @Test
        void handleKeyPressLeavesAKeyBoundToNoLayerUntouched() {

            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            var eventMock = mockKeyPress(UNRELATED_KEYCODE);

            try (MockedStatic<KmuMapKeybindSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            verifyNoInteractions(layerSelectionMock);

            // Unconsumed, so the screen underneath still receives its own key.
            verify(eventMock, never())
                .consume();
        }

        @Test
        void handleKeyPressSkipsALayerWhoseShortcutThePlayerCleared() {
            // A cleared binding stores 0 (LWJGL's KEY_NONE), so a stray zero-valued press must match no
            // layer rather than falling onto the first cleared one.
            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            var eventMock = mockKeyPress(UNBOUND);

            try (MockedStatic<KmuMapKeybindSettings> settingsMock = mockStatic(KmuMapKeybindSettings.class)) {
                settingsMock
                    .when(() -> KmuMapKeybindSettings.getMapLayerShortcut(anyString(), anyInt()))
                    .thenReturn(UNBOUND);

                host.handleKeyPress(eventMock);
            }

            verifyNoInteractions(layerSelectionMock);
            verify(eventMock, never())
                .consume();
        }

        @Test
        void handleKeyPressFollowsTheReboundKeycodeRatherThanTheLayerDefault() {
            // Rebinding is the way out of a clash with a screen's own bindings, so the jump must follow the
            // player's keycode and stop answering to the default.
            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            var eventMock = mockKeyPress(UNRELATED_KEYCODE);

            try (MockedStatic<KmuMapKeybindSettings> settingsMock = mockStatic(KmuMapKeybindSettings.class)) {

                settingsMock
                    .when(() -> KmuMapKeybindSettings.getMapLayerShortcut(FIRST_SETTING_KEY, FIRST_KEYCODE))
                    .thenReturn(UNBOUND);
                settingsMock
                    .when(() -> KmuMapKeybindSettings.getMapLayerShortcut(SECOND_SETTING_KEY, SECOND_KEYCODE))
                    .thenReturn(UNRELATED_KEYCODE);

                host.handleKeyPress(eventMock);
            }

            verify(layerSelectionMock)
                .selectLayer(secondLayerMock);
            verify(eventMock)
                .consume();
        }

        @Test
        void handleKeyPressBlinksTheTabOfTheLayerItJumpedTo() {
            // The only thing that tells the player a shortcut landed: a keyboard switch puts nothing under
            // the pointer, so an unblinked tab would read as a key the panel ignored. Blinking the wrong tab
            // would be worse than none, marking a switch that did not happen.
            var host = createHost(mock(ActiveLayerSelection.class));
            var eventMock = mockKeyPress(SECOND_KEYCODE);

            try (MockedStatic<KmuMapKeybindSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            advanceAWholeTraverse(host);

            assertThat(hoverFractionAt(host, SECOND_LAYER_TAB_INDEX))
                .isCloseTo(1f, within(TOLERANCE));
            assertThat(hoverFractionAt(host, FIRST_LAYER_TAB_INDEX))
                .isCloseTo(0f, within(TOLERANCE));
        }

        @Test
        void handleKeyPressBlinksNoTabForAKeyBoundToNoLayer() {
            // Nothing switched, so nothing may be marked - a blink here would confirm a press the panel in
            // fact let through to the screen underneath.
            var host = createHost(mock(ActiveLayerSelection.class));
            var eventMock = mockKeyPress(UNRELATED_KEYCODE);

            try (MockedStatic<KmuMapKeybindSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            advanceAWholeTraverse(host);

            assertThat(hoverFractionAt(host, FIRST_LAYER_TAB_INDEX))
                .isCloseTo(0f, within(TOLERANCE));
            assertThat(hoverFractionAt(host, SECOND_LAYER_TAB_INDEX))
                .isCloseTo(0f, within(TOLERANCE));
        }

        @Test
        void handleKeyPressWritesOnlyTheHostsOwnPick() {
            // Each screen keeps its own tab, so a shortcut pressed on one screen must not move the other's.
            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var otherScreenSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);

            createHost(otherScreenSelectionMock);

            var eventMock = mockKeyPress(FIRST_KEYCODE);

            try (MockedStatic<KmuMapKeybindSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            verify(layerSelectionMock)
                .selectLayer(firstLayerMock);

            verifyNoInteractions(otherScreenSelectionMock);
        }
    }

    @Nested
    class IsOverlayShowing {

        @Test
        void isOverlayShowingIsFalseWhileTheHostsOwnScreenIsClaimed() {
            // The whole point of the gate: the panel draws after the entire core UI, so whatever claimed
            // the screen is drawn under it while the panel's own hotkeys and hit-testing go on taking the
            // input that thing was raised to receive. Which claimant it is does not reach the gate.
            var host = createHostOnAShowingScreen(ScreenClaims.createScreenClaimedByAModal());

            assertThat(host.isOverlayShowing())
                .isFalse();
        }

        @Test
        void isOverlayShowingIsTrueOnAShowingScreenNothingHasClaimed() {
            // The claim is the only thing added to the screen read, so an unclaimed screen has to leave the
            // panel exactly where it was - a gate stuck shut would take the sidebar off every screen.
            var host = createHostOnAShowingScreen(ScreenClaims.createUnclaimedScreen());

            assertThat(host.isOverlayShowing())
                .isTrue();
        }

        @Test
        void isOverlayShowingIsFalseOffTheHostsScreenWithNothingClaimingIt() {

            var host = createHost(mock(ActiveLayerSelection.class));

            assertThat(host.isOverlayShowing())
                .isFalse();
        }

        @Test
        void isOverlayShowingIsFalseFromTheFirstFrameOfAClaimStillFadingIn() {
            // Where the gate and the fade below deliberately part: input has to go the instant a
            // claimant appears, so the crisp read cannot wait for the fade to climb. A gate derived
            // from the fade would leave the panel routing for every frame the modal was arriving.
            var host = createHostOnAShowingScreen(ScreenClaims.createScreenClaimedByAModalAt(0f));

            assertThat(host.isOverlayShowing())
                .isFalse();
        }

        @Test
        void isOverlayShowingIsFalseWhileThisScreensLayersAreSwitchedOff() {
            // The panel is part of what the layers put on a screen, so it goes with the rest of that
            // footprint rather than standing on over an emptied map.
            var host = createHostWithVisibility(mockVisibility(false, FULLY_HIDDEN));

            assertThat(host.isOverlayShowing())
                .isFalse();
        }

        @Test
        void isOverlayShowingIsFalseFromTheFirstFrameOfAHideRampStillRunning() {
            // Where the gate and the fade part company again: a panel switched off must stop taking clicks
            // on the frame the player switched it off, whatever of it is still dissolving. A gate derived
            // from the ramp would go on routing for every frame of the hide. What is still painted over
            // those frames is ResolveOverlayFade's to pin.
            var host = createHostWithVisibility(mockVisibility(false, 0.6f));

            assertThat(host.isOverlayShowing())
                .isFalse();
        }

        @Test
        void isOverlayShowingIsTrueFromTheFirstFrameOfARevealRampStillRunning() {
            // The way back, and the same rule: the pick is what the gate reads, so a panel switched on
            // takes the pointer at once rather than waiting out the frames it spends thinning back in.
            var host = createHostWithVisibility(mockVisibility(true, 0.1f));

            assertThat(host.isOverlayShowing())
                .isTrue();
        }

        @Test
        void isOverlayShowingLeavesTheScreenUnreadWhileItIsClaimed() {
            // A screen read walks live widgets, so the claim is asked first and the walk skipped while the
            // panel is standing down anyway.
            var host = createHostOnAShowingScreen(ScreenClaims.createScreenClaimedByAModal());

            host.isOverlayShowing();

            assertThat(host.screenReadCount)
                .isZero();
        }
    }

    @Nested
    class ResolveOverlayFade {

        @Test
        void resolveOverlayFadeIsFullOnAShowingScreenNothingHasClaimed() {

            var host = createHostOnAShowingScreen(ScreenClaims.createUnclaimedScreen());

            assertThat(host.resolveOverlayFade())
                .isCloseTo(1f, within(TOLERANCE));
        }

        @Test
        void resolveOverlayFadeLeavesWhatAClaimHasNotTakenYet() {
            // The point of the pair: a modal four tenths of the way in leaves the panel painting at
            // six, so it thins against a backdrop deepening at the same rate rather than cutting out.
            var host = createHostOnAShowingScreen(ScreenClaims.createScreenClaimedByAModalAt(0.4f));

            assertThat(host.resolveOverlayFade())
                .isCloseTo(0.6f, within(TOLERANCE));
        }

        @Test
        void resolveOverlayFadeStillPaintsFullyOnTheFirstFrameOfAClaim() {
            // The same frame the gate above already refuses. The panel is whole here and the modal
            // has drawn nothing yet, which is what makes the two answers differ rather than one of
            // them being wrong.
            var host = createHostOnAShowingScreen(ScreenClaims.createScreenClaimedByAModalAt(0f));

            assertThat(host.resolveOverlayFade())
                .isCloseTo(1f, within(TOLERANCE));
        }

        @Test
        void resolveOverlayFadeIsNothingUnderAClaimFullyInPlace() {

            var host = createHostOnAShowingScreen(ScreenClaims.createScreenClaimedByAModal());

            assertThat(host.resolveOverlayFade())
                .isCloseTo(0f, within(TOLERANCE));
        }

        @Test
        void resolveOverlayFadeIsNothingOffTheHostsScreen() {
            // No screen, no panel, and no fade to run: a host whose screen is down paints nothing at
            // once rather than dissolving out of a frame it was never in.
            var host = createHost(mock(ActiveLayerSelection.class));

            assertThat(host.resolveOverlayFade())
                .isCloseTo(0f, within(TOLERANCE));
        }

        @Test
        void resolveOverlayFadeLeavesTheScreenUnreadUnderAFullClaim() {
            // The gate's short-circuit, kept: a fully claimed screen paints nothing whichever screen
            // it is, so the widget walk is skipped here exactly as it is there.
            var host = createHostOnAShowingScreen(ScreenClaims.createScreenClaimedByAModal());

            host.resolveOverlayFade();

            assertThat(host.screenReadCount)
                .isZero();
        }

        @Test
        void resolveOverlayFadeRidesTheLayersOwnRampDown() {
            // The panel dissolves with the overlay it drives rather than cutting away from over it, which
            // is the whole reason the pick is read here as a fraction and not as the gate's boolean.
            var host = createHostWithVisibility(mockVisibility(false, 0.4f));

            assertThat(host.resolveOverlayFade())
                .isCloseTo(0.4f, within(TOLERANCE));
        }

        @Test
        void resolveOverlayFadeRidesTheLayersOwnRampBackIn() {
            // The reveal, which is the reading a caller is likeliest to optimise away: the crisp pick
            // already says "shown", so a fade read gated behind it would answer 1 here and snap the panel
            // on while the overlay under it was still arriving - with every hide case above still green.
            var host = createHostWithVisibility(mockVisibility(true, 0.3f));

            assertThat(host.resolveOverlayFade())
                .isCloseTo(0.3f, within(TOLERANCE));
        }

        @Test
        void resolveOverlayFadeIsNothingOnceTheLayersHaveGoneFromTheScreen() {

            var host = createHostWithVisibility(mockVisibility(false, FULLY_HIDDEN));

            assertThat(host.resolveOverlayFade())
                .isCloseTo(FULLY_HIDDEN, within(TOLERANCE));
        }

        @Test
        void resolveOverlayFadeComposesTheClaimsDissolveWithTheLayersOwn() {
            // Two independent dissolves multiply rather than one winning: a modal raised over a panel
            // already thinning darkens over what is left of it, and neither has to know the other is
            // running. Whichever were taken alone, the panel would stand too solid under the other.
            var host = createHost(
                createPicks(mockVisibility(false, 0.4f)),
                ScreenClaims.createScreenClaimedByAModalAt(0.5f),
                true);

            assertThat(host.resolveOverlayFade())
                .isCloseTo(0.2f, within(TOLERANCE));
        }

        @Test
        void resolveOverlayFadeLeavesTheScreenUnreadOnceTheLayersHaveGone() {
            // The same short-circuit the claim gets, for the same reason: a panel with nothing left to
            // paint costs no widget walk whichever screen it is on.
            var host = createHostWithVisibility(mockVisibility(false, FULLY_HIDDEN));

            host.resolveOverlayFade();

            assertThat(host.screenReadCount)
                .isZero();
        }
    }

    @Nested
    class DescribeViewState {

        @Test
        void describeViewStateNamesTheHostsScreenAloneWhileTheLayersAreShown() {
            // The line a player normally reads: the show-or-hide prefix is worth saying only when it is
            // the reason the panel is missing.
            var host = createHostWithVisibility(mockVisibility(true, FULLY_SHOWN));

            assertThat(host.describeViewState())
                .isEqualTo("fake host");
        }

        @Test
        void describeViewStateNamesASettledHiddenScreen() {
            // What "the sidebar is gone" has to be diagnosable as without a second question to the player.
            var host = createHostWithVisibility(mockVisibility(false, FULLY_HIDDEN));

            assertThat(host.describeViewState())
                .isEqualTo("layers hidden; fake host");
        }

        @Test
        void describeViewStateNamesARampStillRunningApartFromTheSettledState() {
            // Different bug reports: a panel that stays away was switched off, one caught part-way was on
            // its way out when the line was written. One wording for both would lose that.
            var host = createHostWithVisibility(mockVisibility(false, 0.4f));

            assertThat(host.describeViewState())
                .isEqualTo("layers hiding; fake host");
        }
    }

    // A host carrying nothing but the plumbing under test: the shared key handling is the base's, so the
    // per-screen answers are stubbed out rather than bound to either live screen.
    private static SidebarHostFake createHost(ActiveLayerSelection layerSelection) {
        return createHost(
            new ScreenLayerPicks(layerSelection, mockVisibility(true, FULLY_SHOWN)),
            ScreenClaims.createUnclaimedScreen(),
            false);
    }

    // A host whose own screen is up and whose layers are on it, so what the gate then answers is down to
    // the claim alone.
    private static SidebarHostFake createHostOnAShowingScreen(ScreenClaim screenClaim) {
        return createHost(
            createPicks(mockVisibility(true, FULLY_SHOWN)),
            screenClaim,
            true);
    }

    // A host on a showing, unclaimed screen carrying the given show-or-hide pick, so what the gate and the
    // fade then answer is down to that pick alone.
    private static SidebarHostFake createHostWithVisibility(MapLayerVisibility layerVisibility) {
        return createHost(
            createPicks(layerVisibility),
            ScreenClaims.createUnclaimedScreen(),
            true);
    }

    private static SidebarHostFake createHost(
            ScreenLayerPicks screenPicks,
            ScreenClaim screenClaim,
            boolean isHostScreenShowing) {

        var foldSelectionMock = mock(SidebarFoldSelection.class);

        when(foldSelectionMock.isRailDocked())
            .thenReturn(false);

        return new SidebarHostFake(
            foldSelectionMock,
            screenPicks,
            screenClaim,
            isHostScreenShowing);
    }

    // One screen's picks around the given show-or-hide pick, for the cases whose subject is that pick and
    // which have no use for the tab beside it.
    private static ScreenLayerPicks createPicks(MapLayerVisibility layerVisibility) {
        return new ScreenLayerPicks(mock(ActiveLayerSelection.class), layerVisibility);
    }

    // A show-or-hide pick posed at both of its readings at once, since the gate takes the crisp one and the
    // paint the fraction - and the pair parting company is the thing several cases here are about.
    private static MapLayerVisibility mockVisibility(boolean areLayersShown, float shownFade) {

        var visibilityMock = mock(MapLayerVisibility.class);

        when(visibilityMock.areLayersShown())
            .thenReturn(areLayersShown);
        when(visibilityMock.resolveShownFade())
            .thenReturn(shownFade);

        return visibilityMock;
    }

    // Each layer bound to its own default, the state before the player rebinds anything.
    private static MockedStatic<KmuMapKeybindSettings> mockDefaultBindings() {

        var settingsMock = mockStatic(KmuMapKeybindSettings.class);

        settingsMock
            .when(() -> KmuMapKeybindSettings.getMapLayerShortcut(FIRST_SETTING_KEY, FIRST_KEYCODE))
            .thenReturn(FIRST_KEYCODE);
        settingsMock
            .when(() -> KmuMapKeybindSettings.getMapLayerShortcut(SECOND_SETTING_KEY, SECOND_KEYCODE))
            .thenReturn(SECOND_KEYCODE);

        return settingsMock;
    }

    // Charges the host's panel one whole traverse of animation with the pointer off it, which is all a
    // blink needs to reach its peak. The cursor read the advance opens with is stubbed rather than left to
    // LWJGL, there being no display under a unit test to point at; a placement with no tabs laid in it and
    // no collapse handle then leaves the frame nothing to hover whatever the stub answers.
    private static void advanceAWholeTraverse(SidebarHostFake host) {
        try (var cursorMock = mockStatic(UiCursor.class)) {

            cursorMock
                .when(UiCursor::getUiX)
                .thenReturn(OFF_PANEL_COORDINATE);
            cursorMock
                .when(UiCursor::getUiY)
                .thenReturn(OFF_PANEL_COORDINATE);

            host.getController().advanceInputMotions(
                SidebarPlacements.placeSidebarWithNoTabsLaid(),
                FULL_STEP_SECONDS,
                DURATIONS);
        }
    }

    // How far onto the hovered shade one of the host's tabs stands, read the way the render pass reads it -
    // through the interaction sources, which is where a blink and a hover are composed into the one value a
    // strip paints from.
    private static float hoverFractionAt(SidebarHostFake host, int tabIndex) {
        return host
            .getController()
            .getInteractionSources()
            .headerTabs()
            .hoverSource()
            .resolveHoverFractionAt(tabIndex);
    }

    private static InputEventAPI mockKeyPress(int keycode) {

        var eventMock = mock(InputEventAPI.class);

        when(eventMock.getEventValue())
            .thenReturn(keycode);

        return eventMock;
    }

    // The base host with its per-screen questions answered as "nothing to draw": only the shared key
    // handling and the shared half of the gate are under test here, and each concrete host pins its own
    // screen read, anchor, edges, and look. A host resolving no placement is never asked to paint, so it
    // has no look to give.
    private static final class SidebarHostFake extends BaseSidebarHost {

        private final boolean isHostScreenShowing;

        // How often the screen half of the gate was asked, so "the console short-circuits it" can be
        // pinned as never reached rather than merely as an answer that came out false anyway.
        private int screenReadCount;

        private SidebarHostFake(
                SidebarFoldSelection foldSelection,
                ScreenLayerPicks screenPicks,
                ScreenClaim screenClaim,
                boolean isHostScreenShowing) {

            super(foldSelection, screenPicks, screenClaim);
            this.isHostScreenShowing = isHostScreenShowing;
        }

        @Override
        public TabPanelPlacement resolvePlacement() {
            return null;
        }

        @Override
        public WidgetStyle resolveWidgetStyle() {
            return null;
        }

        @Override
        public Set<BoxEdge> resolveBorderEdges(TabPanelPlacement placement) {
            return BoxEdge.ALL;
        }

        @Override
        protected String describeHostScreenViewState() {
            return "fake host";
        }

        @Override
        protected boolean isHostScreenShowing() {
            screenReadCount++;
            return isHostScreenShowing;
        }
    }
}
