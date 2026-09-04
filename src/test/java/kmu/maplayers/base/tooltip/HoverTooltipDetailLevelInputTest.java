package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import kmu.maplayers.base.hover.MapHoverPermissionFixture;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevelState;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins which press the cycle key claims and which it lets past. The key is claimed on exactly the
 * two shared gates the box it switches draws behind - the settings tiers above any layer, and a map
 * on screen - so a listener that read one and not the other would swallow F1 on a screen that could
 * show no box at all. What the live map read answers belongs to the seam itself and to the install
 * site; here the permission is posed over fixed screen reads, so a vanilla host being up or not up
 * is the whole of what these tests can say.
 *
 * <p>Behind those gates the press is claimed only where the box under the cursor would read
 * differently one press on, which is the other half of the same honesty: the level is one shared fact
 * that holds across hovers, so a press swallowed over a system with nothing to expand would decide how
 * the next system that does differ opens. What the cursor is over is a live chain through the hover
 * state, the layer registry and the sector, so it arrives here stood in for - the level a box names,
 * or none, is the whole of what these tests can say.
 *
 * <p>That the press lands where the box said rather than one constant further on is pinned here too:
 * the cycle wraps at the deepest level a box holds anything at, which this pass cannot work out for
 * itself, so a step taken here would walk a shallow box's player through tiers it cannot fill.
 *
 * <p>Which level the question is asked at is pinned here rather than left to the box, since only this
 * pass holds both the read and the move: asked after the move, the gate would answer about a depth the
 * player has not been shown.
 */
final class HoverTooltipDetailLevelInputTest {

    // Below the sidebar's own listener, pinned so a sidebar tab hotkey keeps the first claim on any
    // key it is bound to.
    private static final int SIDEBAR_INPUT_PRIORITY = 1000;

    private final HoverTooltipDetailLevelInput input =
        new HoverTooltipDetailLevelInput(
            MapHoverPermissionFixture.buildPermissionOnAVanillaHost());

    private MockedStatic<HoveredBox> hoveredBoxMock;

    @BeforeEach
    void standUpABoxWithMoreToState() {
        // The ordinary case for a case about the gates: something is hovered and it does read
        // deeper, so the press turns on the gate under test rather than on the offer.
        hoveredBoxMock = Mockito.mockStatic(HoveredBox.class);
        stubHoveredBoxOffering(Optional.of(HoverTooltipDetailLevel.SYSTEM_COMPOSITION));
    }

    @AfterEach
    void dropTheDetailLevelBackToFactions() {

        hoveredBoxMock.close();

        // The level holder is a process-wide singleton, so an advance left standing would reach the
        // next test as a detail level it never asked for.
        HoverTooltipDetailLevelState.getInstance().discardLevelFromPreviousSave();
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
        void processCampaignInputPreCoreMovesToTheLevelTheBoxNamedOnTheCycleKeyPress() {

            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);

            // Claimed where it acted, so nothing else answers the same press while the map is open.
            verify(eventMock)
                .consume();
        }

        @Test
        void processCampaignInputPreCoreAsksTheBoxAboutTheLevelThePressMovesOnFrom() {
            // The offer is a question about this press, so it is asked at the level on screen rather
            // than at the one the move is about to land on. Read the other way round the gate would
            // answer about a depth the player has not been shown - and from the box's deepest level
            // it would ask about the shallowest, claiming the key over a box that offered nothing.
            var tooltipMock = stubHoveredBoxOffering(
                Optional.of(HoverTooltipDetailLevel.SYSTEM_COMPOSITION));

            runWithHoverTooltipSwitchOn(
                () -> input.processCampaignInputPreCore(List.of(mockKeyDown(Keyboard.KEY_F1))));

            verify(tooltipMock)
                .resolveNextLevelFor(any(), any(), eq(HoverTooltipDetailLevel.FACTIONS));
        }

        @Test
        void processCampaignInputPreCoreTakesTheBoxsDestinationRatherThanSteppingTheCycle() {
            // The cycle wraps at the deepest level the box itself holds anything at, which only the
            // box knows - so the press lands exactly where it said and not one constant further on.
            // Stepped here, a box whose account ends early would walk its player through tiers that
            // redraw the same thing.
            stubHoveredBoxOffering(Optional.of(HoverTooltipDetailLevel.FACTIONS));

            HoverTooltipDetailLevelState.getInstance()
                .moveToLevel(HoverTooltipDetailLevel.MARKET_STATS);

            runWithHoverTooltipSwitchOn(
                () -> input.processCampaignInputPreCore(List.of(mockKeyDown(Keyboard.KEY_F1))));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }

        @Test
        void processCampaignInputPreCoreLeavesTheCycleKeyPressAloneWhileTooltipsAreSwitchedOff() {
            // No box can be drawn behind this switch, so there is nothing for the key to switch and
            // the press must fall through to whatever else claims it.
            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverTooltipSwitchOff(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);

            verify(eventMock, never())
                .consume();
        }

        @Test
        void processCampaignInputPreCoreLeavesTheCycleKeyPressAloneWhileNoMapIsOnScreen() {
            // This listener is called for the whole campaign UI, so without the map gate F1 would be
            // swallowed on every screen the player is on - the refit, the intel list, the market.
            var eventMock = mockKeyDown(Keyboard.KEY_F1);
            var inputWithNoMapShowing = new HoverTooltipDetailLevelInput(
                MapHoverPermissionFixture.buildPermissionOffEveryMap());

            runWithHoverTooltipSwitchOn(
                () -> inputWithNoMapShowing.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);

            verify(eventMock, never())
                .consume();
        }

        @Test
        void processCampaignInputPreCoreSkipsAnEventAlreadyClaimedUpstream() {
            // A sidebar tab bound to the same key acts first; taking the press again would advance
            // the level on a press that was meant for the tab.
            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            when(eventMock.isConsumed())
                .thenReturn(true);

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }

        @Test
        void processCampaignInputPreCoreLeavesEveryOtherKeyAlone() {
            // The gate is open and a key is down, so only the keycode test stands between this press
            // and the level - which is every other binding the player has on the map.
            var eventMock = mockKeyDown(Keyboard.KEY_P);

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);

            verify(eventMock, never())
                .consume();
        }

        @Test
        void processCampaignInputPreCoreClaimsTheCycleKeyPressBehindAnUnrelatedEvent() {
            // Events arrive as a frame's worth at once, so an unrelated one earlier in the list must
            // not end the pass before the cycle key is reached.
            var unrelatedEventMock = mockKeyDown(Keyboard.KEY_P);
            var cycleKeyEventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverTooltipSwitchOn(() ->
                input.processCampaignInputPreCore(List.of(unrelatedEventMock, cycleKeyEventMock)));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);

            verify(cycleKeyEventMock)
                .consume();
        }
    }

    @Nested
    class ProcessCampaignInputPreFleetControl {

        @Test
        void processCampaignInputPreFleetControlLeavesTheCycleKeyPressUntouched() {

            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverTooltipSwitchOn(
                () -> input.processCampaignInputPreFleetControl(List.of(eventMock)));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);

            verify(eventMock, never())
                .consume();
        }
    }

    @Nested
    class ProcessCampaignInputPostCore {

        @Test
        void processCampaignInputPostCoreLeavesTheCycleKeyPressUntouched() {
            // The key is claimed pre-core, which is where consuming still stops the screen underneath
            // from seeing it.
            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPostCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);

            verify(eventMock, never())
                .consume();
        }
    }

    @Nested
    class ProcessCampaignInputPreCoreOverABoxWithNothingToExpand {

        @Test
        void processCampaignInputPreCoreLeavesTheCycleKeyPressAloneOverABoxOfferingNothing() {
            // The whole point of asking: the level is shared and holds across hovers, so advancing
            // it here would decide how the next system that does differ opens - a depth the player
            // never chose, from a press that appeared to do nothing.
            stubHoveredBoxOffering(Optional.empty());

            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);

            verify(eventMock, never())
                .consume();
        }

        @Test
        void processCampaignInputPreCoreLeavesTheCycleKeyPressAloneOverNoBoxAtAll() {
            // Nothing is hovered, or no layer is showing a box for what is. There is nothing for the
            // key to switch, and vanilla keeps it.
            hoveredBoxMock
                .when(HoveredBox::resolveHoveredBox)
                .thenReturn(Optional.empty());

            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);

            verify(eventMock, never())
                .consume();
        }

        @Test
        void processCampaignInputPreCoreStillCollapsesTheDeepestBoxItOpened() {
            // The offer is symmetric: a box that could be deepened can be collapsed again, so the key
            // has to keep working once the deepest box is the one on screen. Were it read as "can
            // this grow", the player would open a box they could not close.
            stubHoveredBoxOffering(Optional.of(HoverTooltipDetailLevel.FACTIONS));

            HoverTooltipDetailLevelState.getInstance()
                .moveToLevel(HoverTooltipDetailLevel.PATROL_DETAILS);

            var eventMock = mockKeyDown(Keyboard.KEY_F1);

            runWithHoverTooltipSwitchOn(() -> input.processCampaignInputPreCore(List.of(eventMock)));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }
    }

    @Nested
    class CycleKeyName {

        @Test
        void cycleKeyNameNamesTheKeyThisListenerActuallyClaims() {
            // A box tells the player which key expands it, and it is this listener that decides which
            // key that is - so the printed name is pinned to the press the listener acts on rather
            // than to a second spelling of it that could be left behind by a rebind.
            assertThat(HoverTooltipDetailLevelInput.CYCLE_KEY_NAME)
                .isEqualTo("F1");
            assertThat(HoverTooltipDetailLevelInput.isDetailLevelCycleKey(
                    mockKeyDown(Keyboard.KEY_F1)))
                .isTrue();
        }
    }

    @Nested
    class IsDetailLevelCycleKey {

        @Test
        void isDetailLevelCycleKeyIsTrueForTheCycleKeyPressedDown() {

            assertThat(HoverTooltipDetailLevelInput.isDetailLevelCycleKey(mockKeyDown(Keyboard.KEY_F1)))
                .isTrue();
        }

        @Test
        void isDetailLevelCycleKeyIsFalseForTheKeyReleased() {
            // The release arrives as its own event, so acting on both halves of one press would
            // advance the level twice and skip a depth the player never saw.
            var eventMock = mock(InputEventAPI.class);

            when(eventMock.getEventValue())
                .thenReturn(Keyboard.KEY_F1);

            assertThat(HoverTooltipDetailLevelInput.isDetailLevelCycleKey(eventMock))
                .isFalse();
        }

        @Test
        void isDetailLevelCycleKeyIsFalseForAnotherKey() {
            assertThat(HoverTooltipDetailLevelInput.isDetailLevelCycleKey(mockKeyDown(Keyboard.KEY_P)))
                .isFalse();
        }

        @Test
        void isDetailLevelCycleKeyIsFalseForAPointerEvent() {
            // A mouse event carries a button in the same field a key event carries its keycode, so a
            // test that only read the value could match a button number onto the cycle key's keycode.
            var eventMock = mock(InputEventAPI.class);

            when(eventMock.isMouseEvent())
                .thenReturn(true);

            assertThat(HoverTooltipDetailLevelInput.isDetailLevelCycleKey(eventMock))
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

    // Stands the cursor over a box that answers every press with nextLevel - or, empty, over one that
    // would read no differently one press on. The chain behind the real answer runs through the hover
    // state, the layer registry and the live sector, none of which a unit test can stand up; what the
    // listener acts on is the answer.
    //
    // The box comes back so a case about what it was asked can read the question rather than only
    // the answer.
    private MapHoverTooltip stubHoveredBoxOffering(Optional<HoverTooltipDetailLevel> nextLevel) {

        var tooltipMock = mock(MapHoverTooltip.class);

        when(tooltipMock.resolveNextLevelFor(any(), any(), any()))
            .thenReturn(nextLevel);

        hoveredBoxMock
            .when(HoveredBox::resolveHoveredBox)
            .thenReturn(Optional.of(new HoveredBox(
                tooltipMock,
                mock(SectorAPI.class),
                mock(StarSystemAPI.class))));

        return tooltipMock;
    }
}
