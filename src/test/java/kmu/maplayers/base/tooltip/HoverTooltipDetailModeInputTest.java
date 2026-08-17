package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.hover.HoverSwitchScopes.runWithHoverTooltipSwitchOff;
import static kmu.maplayers.base.hover.HoverSwitchScopes.runWithHoverTooltipSwitchOn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
 * <p>Behind those gates the press is claimed only where the box under the cursor has a second amount
 * of detail to state, which is the other half of the same honesty: the mode is one shared fact that
 * holds across hovers, so a press swallowed over a system with nothing to expand would decide how the
 * next system that does differ opens. What the cursor is over is a live chain through the hover state,
 * the layer registry and the sector, so it arrives here stood in for - offering or not offering is the
 * whole of what this level can say.
 */
final class HoverTooltipDetailModeInputTest {

    // Below the sidebar's own listener, pinned so a sidebar tab hotkey keeps the first claim on any
    // key it is bound to.
    private static final int SIDEBAR_INPUT_PRIORITY = 1000;

    private final HoverTooltipDetailModeInput input =
        new HoverTooltipDetailModeInput(() -> true);

    private MockedStatic<HoveredBox> hoveredBoxMock;

    @BeforeEach
    void standUpABoxWithMoreToState() {
        // The ordinary case for a case about the gates: something is hovered and it does have a
        // richer counterpart, so the press turns on the gate under test rather than on the offer.
        hoveredBoxMock = Mockito.mockStatic(HoveredBox.class);
        stubHoveredBoxOffering(true);
    }

    @AfterEach
    void dropTheDetailModeBackToNormal() {
        
        hoveredBoxMock.close();

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

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

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
            runWithHoverTooltipSwitchOn(() -> {
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

            runWithHoverTooltipSwitchOff(() -> input.processCampaignInputPreCore(List.of(eventMock)));

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

            runWithHoverTooltipSwitchOn(
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

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);
        }

        @Test
        void processCampaignInputPreCoreLeavesEveryOtherKeyAlone() {
            // The gate is open and a key is down, so only the keycode test stands between this press
            // and the mode - which is every other binding the player has on the map.
            var eventMock = mockKeyDown(Keyboard.KEY_P);

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

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

            runWithHoverTooltipSwitchOn(() ->
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

            runWithHoverTooltipSwitchOn(
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

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPostCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);

            verify(eventMock, never())
                .consume();
        }
    }

    @Nested
    class ProcessCampaignInputPreCoreOverABoxWithNothingToExpand {

        @Test
        void processCampaignInputPreCoreLeavesTheTogglePressAloneOverABoxOfferingNothing() {
            // The whole point of asking: the mode is shared and holds across hovers, so flipping it
            // here would decide how the next system that does differ opens - a state the player never
            // chose, from a press that appeared to do nothing.
            stubHoveredBoxOffering(false);

            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);

            verify(eventMock, never())
                .consume();
        }

        @Test
        void processCampaignInputPreCoreLeavesTheTogglePressAloneOverNoBoxAtAll() {
            // Nothing is hovered, or no layer is showing a box for what is. There is nothing for the
            // key to switch, and vanilla keeps it.
            hoveredBoxMock
                .when(HoveredBox::resolveHoveredBox)
                .thenReturn(Optional.empty());

            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);
                
            verify(eventMock, never())
                .consume();
        }

        @Test
        void processCampaignInputPreCoreStillClosesTheDetailedBoxItOpened() {
            // The offer is symmetric: a box that could be expanded can be collapsed again, so the key
            // has to keep working once the richer box is the one on screen. Were it read as "can this
            // grow", the player would open a box they could not close.
            HoverTooltipDetailModeState.getInstance().toggleMode();

            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);
        }
    }

    @Nested
    class ToggleKeyName {

        @Test
        void toggleKeyNameNamesTheKeyThisListenerActuallyClaims() {
            // A box tells the player which key expands it, and it is this listener that decides which
            // key that is - so the printed name is pinned to the press the listener acts on rather
            // than to a second spelling of it that could be left behind by a rebind.
            assertThat(HoverTooltipDetailModeInput.TOGGLE_KEY_NAME)
                .isEqualTo("F1");
            assertThat(HoverTooltipDetailModeInput.isDetailModeToggleKey(
                    mockKeyDown(Keyboard.KEY_F1)))
                .isTrue();
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

    // Stands the cursor over a box that does - or does not - have a second amount of detail to state.
    // The chain behind the real answer runs through the hover state, the layer registry and the live
    // sector, none of which a unit test can stand up; what the listener acts on is the answer.
    private void stubHoveredBoxOffering(boolean isOfferingExpansion) {

        var tooltipMock = mock(MapHoverTooltip.class);

        when(tooltipMock.isOfferingExpansionFor(any(), any()))
            .thenReturn(isOfferingExpansion);

        hoveredBoxMock
            .when(HoveredBox::resolveHoveredBox)
            .thenReturn(Optional.of(new HoveredBox(
                tooltipMock,
                mock(SectorAPI.class),
                mock(StarSystemAPI.class))));
    }
}
