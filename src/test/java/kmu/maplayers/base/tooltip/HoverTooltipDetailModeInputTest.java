package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.input.InputEventAPI;

import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins which press the toggle claims and which it lets past. The key is claimed on exactly the two
 * shared gates the box it switches draws behind - the settings tiers above any layer, and a map on
 * screen - so a listener that read one and not the other would swallow F1 on a screen that could
 * show no box at all. What the live map read answers belongs to the seam itself and to the install
 * site; here it arrives as a supplier, so open and closed is the whole of what this level can say.
 *
 * <p>That the mode flips whatever is hovered is pinned by there being no hover anywhere in this
 * fixture: the press is answered on the gate alone, which is what lets the choice reach the next
 * hover that offers a richer box.
 */
final class HoverTooltipDetailModeInputTest {

    // Below the sidebar's own listener, pinned so a sidebar tab hotkey keeps the first claim on any
    // key it is bound to.
    private static final int SIDEBAR_INPUT_PRIORITY = 1000;

    private final HoverTooltipDetailModeInput input =
        new HoverTooltipDetailModeInput(() -> true);

    @AfterEach
    void dropTheDetailModeBackToNormal() {
        // The mode holder is a process-wide singleton, so a flip left standing would reach the next
        // test as a detail level it never asked for.
        HoverTooltipDetailModeState.getInstance().discardModeFromPreviousSave();
    }

    @Nested
    class GetListenerInputPriority {

        @Test
        void getListenerInputPriorityRunsBehindTheSidebarsOwnListener() {
            // Ahead of the core screen, but not ahead of the sidebar: a key bound to a sidebar tab
            // must reach the tab rather than be eaten here.
            assertThat(input.getListenerInputPriority())
                .isLessThan(SIDEBAR_INPUT_PRIORITY)
                .isPositive();
        }
    }

    @Nested
    class ProcessCampaignInputPreCore {

        @Test
        void processCampaignInputPreCoreFlipsTheModeOnTheTogglePress() {

            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverSwitchesOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.EXPANDED);

            // Claimed where it acted, so nothing else answers the same press while the map is open.
            verify(eventMock)
                .consume();
        }

        @Test
        void processCampaignInputPreCoreFlipsTheModeBackOnASecondPress() {
            // The toggle is its own inverse, which is what makes one key both the way in and the way
            // out rather than a mode the player cannot leave.
            runWithHoverSwitchesOn(() -> {
                input.processCampaignInputPreCore(List.of(mockKeyDown(Keyboard.KEY_F1)));
                input.processCampaignInputPreCore(List.of(mockKeyDown(Keyboard.KEY_F1)));
            });

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);
        }

        @Test
        void processCampaignInputPreCoreLeavesTheTogglePressAloneWhileTooltipsAreSwitchedOff() {
            // No box can be drawn behind this switch, so there is nothing for the key to switch and
            // the press must fall through to whatever else claims it.
            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverSwitchesOff(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);

            verify(eventMock, never())
                .consume();
        }

        @Test
        void processCampaignInputPreCoreLeavesTheTogglePressAloneWhileNoMapIsOnScreen() {
            // This listener is called for the whole campaign UI, so without the map gate F1 would be
            // swallowed on every screen the player is on - the refit, the intel list, the market.
            var eventMock = mockKeyDown(Keyboard.KEY_F1);
            var inputWithNoMapShowing = new HoverTooltipDetailModeInput(() -> false);

            runWithHoverSwitchesOn(
                () -> inputWithNoMapShowing.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);

            verify(eventMock, never())
                .consume();
        }

        @Test
        void processCampaignInputPreCoreSkipsAnEventAlreadyClaimedUpstream() {
            // A sidebar tab bound to the same key acts first; taking the press again would flip the
            // mode on a press that was meant for the tab.
            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            when(eventMock.isConsumed())
                .thenReturn(true);

            runWithHoverSwitchesOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);
        }

        @Test
        void processCampaignInputPreCoreLeavesEveryOtherKeyAlone() {
            // The gate is open and a key is down, so only the keycode test stands between this press
            // and the mode - which is every other binding the player has on the map.
            var eventMock = mockKeyDown(Keyboard.KEY_P);

            runWithHoverSwitchesOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);

            verify(eventMock, never())
                .consume();
        }

        @Test
        void processCampaignInputPreCoreClaimsTheTogglePressBehindAnUnrelatedEvent() {
            // Events arrive as a frame's worth at once, so an unrelated one earlier in the list must
            // not end the pass before the toggle is reached.
            var unrelatedEventMock = mockKeyDown(Keyboard.KEY_P);
            var toggleEventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverSwitchesOn(() ->
                input.processCampaignInputPreCore(List.of(unrelatedEventMock, toggleEventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.EXPANDED);

            verify(toggleEventMock)
                .consume();
        }
    }

    @Nested
    class ProcessCampaignInputPreFleetControl {

        @Test
        void processCampaignInputPreFleetControlLeavesTheTogglePressUntouched() {

            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverSwitchesOn(
                () -> input.processCampaignInputPreFleetControl(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);

            verify(eventMock, never())
                .consume();
        }
    }

    @Nested
    class ProcessCampaignInputPostCore {

        @Test
        void processCampaignInputPostCoreLeavesTheTogglePressUntouched() {
            // The key is claimed pre-core, which is where consuming still stops the screen underneath
            // from seeing it.
            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverSwitchesOn(() -> input.processCampaignInputPostCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);

            verify(eventMock, never())
                .consume();
        }
    }

    @Nested
    class IsDetailModeToggleKey {

        @Test
        void isDetailModeToggleKeyIsTrueForTheTogglePressedDown() {
            assertThat(HoverTooltipDetailModeInput.isDetailModeToggleKey(mockKeyDown(Keyboard.KEY_F1)))
                .isTrue();
        }

        @Test
        void isDetailModeToggleKeyIsFalseForTheToggleReleased() {
            // The release arrives as its own event, so acting on both halves of one press would flip
            // the mode twice and leave it exactly where it started.
            var eventMock = mock(InputEventAPI.class);

            when(eventMock.getEventValue())
                .thenReturn(Keyboard.KEY_F1);

            assertThat(HoverTooltipDetailModeInput.isDetailModeToggleKey(eventMock))
                .isFalse();
        }

        @Test
        void isDetailModeToggleKeyIsFalseForAnotherKey() {
            assertThat(HoverTooltipDetailModeInput.isDetailModeToggleKey(mockKeyDown(Keyboard.KEY_P)))
                .isFalse();
        }

        @Test
        void isDetailModeToggleKeyIsFalseForAPointerEvent() {
            // A mouse event carries a button in the same field a key event carries its keycode, so a
            // test that only read the value could match a button number onto the toggle's keycode.
            var eventMock = mock(InputEventAPI.class);

            when(eventMock.isMouseEvent())
                .thenReturn(true);

            assertThat(HoverTooltipDetailModeInput.isDetailModeToggleKey(eventMock))
                .isFalse();
        }
    }

    private static InputEventAPI mockKeyDown(int keycode) {

        var eventMock = mock(InputEventAPI.class);

        when(eventMock.isKeyDownEvent())
            .thenReturn(true);
        when(eventMock.getEventValue())
            .thenReturn(keycode);

        return eventMock;
    }

    // Runs body with both hover switches on - the settings tier above every gate this listener reads.
    // They are static reads, so they can only be answered for the length of a scope, which is what
    // makes this a wrapper rather than a fixture.
    private static void runWithHoverSwitchesOn(Runnable body) {
        runWithHoverSwitches(true, body);
    }

    // The closed side of the same tier: the master is left on so the case is about the tooltip switch
    // alone rather than about hovering being off altogether.
    private static void runWithHoverSwitchesOff(Runnable body) {
        runWithHoverSwitches(false, body);
    }

    private static void runWithHoverSwitches(boolean isTooltipEnabled, Runnable body) {
        try (var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

            settingsMock
                .when(KmuMapLayerSettings::getMapHoveringEnabled)
                .thenReturn(true);
            settingsMock
                .when(KmuMapLayerSettings::getMapHoverTooltipEnabled)
                .thenReturn(isTooltipEnabled);

            body.run();
        }
    }
}
