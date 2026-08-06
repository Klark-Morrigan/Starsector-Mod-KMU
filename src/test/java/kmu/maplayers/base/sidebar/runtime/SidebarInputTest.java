package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins which events the sidebar claims and which it lets past. Two gates decide that: the host's own
 * "is the sidebar live" answer, off which nothing routes at all and a drag left dangling by the overlay
 * closing mid-drag is cancelled; and whether the panel is presenting the tabs a bound key would switch
 * between, since a panel that is not must let that key through to the screen underneath. That second gate
 * is the panel's own answer about the placement drawn, not a reading of its fold - the two part on a tab
 * with no body, which has no fold and no handle to undo one with. A pointer needs a drawn box to hit, so it
 * routes only against a resolved placement; a key press needs none, because jumping to a layer does not
 * depend on where the panel landed, and with nothing drawn the fold is all that is left to ask.
 */
final class SidebarInputTest {
    // Ahead of the core screen and of other mods' listeners, pinned so a tab click or notch press is
    // claimed before anything else can take it.
    private static final int EXPECTED_PRIORITY = 1000;

    private final SidebarHost hostMock = mock(SidebarHost.class);
    private final TabPanelController controllerMock = mock(TabPanelController.class);
    private final TabPanelPlacement placementMock = mock(TabPanelPlacement.class);
    private final SidebarInput input = new SidebarInput(hostMock);

    @BeforeEach
    void showTheSidebarWithAPlacementDrawn() {
        when(hostMock.getController()).thenReturn(controllerMock);
        // The live case, so each test states only the gate it is actually about.
        when(hostMock.isOverlayShowing()).thenReturn(true);
        when(hostMock.resolvePlacement()).thenReturn(placementMock);
    }

    @Nested
    class GetListenerInputPriority {

        @Test
        void getListenerInputPriorityRunsAheadOfTheCoreScreen() {
            assertThat(input.getListenerInputPriority()).isEqualTo(EXPECTED_PRIORITY);
        }
    }

    @Nested
    class ProcessCampaignInputPreCore {

        @Test
        void processCampaignInputPreCoreRoutesAKeyPressWhileThePanelPresentsItsTabs() {
            when(controllerMock.isPresentingTabsOf(placementMock)).thenReturn(true);
            var eventMock = mockKeyPress();

            input.processCampaignInputPreCore(List.of(eventMock));

            verify(hostMock).handleKeyPress(eventMock);
        }

        @Test
        void processCampaignInputPreCoreLeavesAKeyPressAloneWhileThePanelPresentsNoTabs() {
            // Docked, docking, or undocking, the panel is not offering its tabs, so its hotkeys stay inert
            // and the key falls through unconsumed to whatever else claims it.
            when(controllerMock.isPresentingTabsOf(placementMock)).thenReturn(false);
            var eventMock = mockKeyPress();

            input.processCampaignInputPreCore(List.of(eventMock));

            verify(hostMock, never()).handleKeyPress(any());
            verify(eventMock, never()).consume();
        }

        @Test
        void processCampaignInputPreCoreAsksThePanelAboutThePlacementRatherThanItsFold() {
            // The two answers part on a tab with no body: the fold left standing from another tab says the
            // panel is docked, while the placement drawn says its row is there in full. Reading the fold
            // would leave that tab's keys dead for the rest of the session, with no handle to expand a body
            // it does not have.
            when(controllerMock.isFullyExpanded()).thenReturn(false);
            when(controllerMock.isPresentingTabsOf(placementMock)).thenReturn(true);
            var eventMock = mockKeyPress();

            input.processCampaignInputPreCore(List.of(eventMock));

            verify(hostMock).handleKeyPress(eventMock);
        }

        @Test
        void processCampaignInputPreCoreRoutesAKeyPressWithNothingDrawnToHit() {
            // A key press needs no placement: jumping to a layer does not depend on where the box landed,
            // so a frame that drew nothing still answers its hotkeys - and with no placement to ask about,
            // the fold is the only thing left to gate on.
            when(hostMock.resolvePlacement()).thenReturn(null);
            when(controllerMock.isFullyExpanded()).thenReturn(true);
            var eventMock = mockKeyPress();

            input.processCampaignInputPreCore(List.of(eventMock));

            verify(hostMock).handleKeyPress(eventMock);
        }

        @Test
        void processCampaignInputPreCoreRoutesAPointerEventToTheController() {
            var eventMock = mockPointerEvent();

            input.processCampaignInputPreCore(List.of(eventMock));

            verify(controllerMock).handlePointer(eventMock, placementMock);
        }

        @Test
        void processCampaignInputPreCoreIgnoresAPointerEventWithNothingDrawnToHit() {
            // No placement means no box on screen this frame, so there is nothing to hit-test against.
            when(hostMock.resolvePlacement()).thenReturn(null);
            var eventMock = mockPointerEvent();

            input.processCampaignInputPreCore(List.of(eventMock));

            verify(controllerMock, never()).handlePointer(any(), any());
        }

        @Test
        void processCampaignInputPreCoreSkipsAnEventAlreadyClaimedUpstream() {
            var eventMock = mock(InputEventAPI.class);
            when(eventMock.isConsumed()).thenReturn(true);
            when(controllerMock.isFullyExpanded()).thenReturn(true);

            input.processCampaignInputPreCore(List.of(eventMock));

            verify(hostMock, never()).handleKeyPress(any());
            verify(controllerMock, never()).handlePointer(any(), any());
        }

        @Test
        void processCampaignInputPreCoreCancelsADanglingDragWhileTheSidebarIsOff() {
            // Off the gate the sidebar's keys and clicks must be inert, and a grab left over from the
            // overlay closing mid-drag has to end here rather than hijacking the next session.
            when(hostMock.isOverlayShowing()).thenReturn(false);
            when(controllerMock.isFullyExpanded()).thenReturn(true);
            var keyEventMock = mockKeyPress();
            var pointerEventMock = mockPointerEvent();

            input.processCampaignInputPreCore(List.of(keyEventMock, pointerEventMock));

            verify(controllerMock).cancelDrag();
            verify(hostMock, never()).handleKeyPress(any());
            verify(controllerMock, never()).handlePointer(any(), any());
            // Nothing is drawn off the gate, so the placement is never even resolved.
            verify(hostMock, never()).resolvePlacement();
        }

        @Test
        void processCampaignInputPreCoreRoutesEveryUnclaimedEventInTheFrame() {
            // Events arrive as a frame's worth at once, so one claimed event must not end the pass.
            when(controllerMock.isPresentingTabsOf(placementMock)).thenReturn(true);
            var keyEventMock = mockKeyPress();
            var pointerEventMock = mockPointerEvent();

            input.processCampaignInputPreCore(List.of(keyEventMock, pointerEventMock));

            verify(hostMock).handleKeyPress(keyEventMock);
            verify(controllerMock).handlePointer(pointerEventMock, placementMock);
        }
    }

    @Nested
    class ProcessCampaignInputPreFleetControl {

        @Test
        void processCampaignInputPreFleetControlLeavesEveryEventUntouched() {
            input.processCampaignInputPreFleetControl(List.of(mockKeyPress(), mockPointerEvent()));

            verifyNoInteractions(hostMock);
        }
    }

    @Nested
    class ProcessCampaignInputPostCore {

        @Test
        void processCampaignInputPostCoreLeavesEveryEventUntouched() {
            // All of the sidebar's input is claimed pre-core, where consuming still stops the screen
            // underneath from seeing it.
            input.processCampaignInputPostCore(List.of(mockKeyPress(), mockPointerEvent()));

            verifyNoInteractions(hostMock);
        }
    }

    private static InputEventAPI mockKeyPress() {
        var eventMock = mock(InputEventAPI.class);
        when(eventMock.isKeyDownEvent()).thenReturn(true);
        return eventMock;
    }

    private static InputEventAPI mockPointerEvent() {
        var eventMock = mock(InputEventAPI.class);
        when(eventMock.isMouseEvent()).thenReturn(true);
        return eventMock;
    }
}
