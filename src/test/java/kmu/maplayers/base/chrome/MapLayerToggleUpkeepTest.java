package kmu.maplayers.base.chrome;

import kmlib.starsector.ui.map.controls.MapFilterRow;

import kmu.maplayers.base.layer.ControlBackedMapLayerVisibility;
import kmu.maplayers.base.layer.MapLayerVisibility;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins when the pass writes into the game's filter row and when it leaves it alone: once per row per
 * screen, again as soon as a screen rebuilds its row, never while the switch is closed or no map is
 * up, and never in a way that lets a broken read out.
 *
 * <p>And when it says the screen has a control at all, which is what a stored hide is acted on: after
 * a box is standing, never after a row that refused one. That word is the half of failing open no log
 * line covers, so the cases pinning it are here beside the ones pinning the write - and they read it
 * back off a real screen state rather than off a stand-in, since what it is worth is exactly what the
 * screen's layers then do.
 *
 * <p>Also the two answers that are not decisions of its own but which the whole control rests on:
 * that it goes on running for the session, and that it runs while the campaign is paused. Every
 * screen carrying a filter row pauses the campaign, so a pass that stood down under one would never
 * run on a frame where there was a row to write to.
 */
final class MapLayerToggleUpkeepTest {

    // The campaign is paused on every screen carrying a filter row, so this is the delta the pass
    // actually runs at.
    private static final float PAUSED_FRAME = 0f;

    private static final BooleanSupplier SWITCH_OPEN = () -> true;
    private static final BooleanSupplier SWITCH_CLOSED = () -> false;

    @Nested
    class Advance {

        @Test
        void advancePutsAControlOnTheRowOnScreen() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var screenLayerControl = buildScreenLayerControl();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> screenLayerControl, () -> shownRow, toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // Bound to the stored pick of the screen the row belongs to: to that screen's, so a
            // control on one screen's row cannot move the other screen's layers, and to the stored
            // pick, since a box is what lifts the no-control-no-hiding rule rather than a reader of it.
            verify(toggleAttacherMock)
                .attachToggleTo(shownRow, screenLayerControl.getStoredVisibility());
        }

        @Test
        void advanceLeavesTheControlAloneWhileItStandsOnTheRowOnScreen() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var screenLayerControl = buildScreenLayerControl();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> screenLayerControl, () -> shownRow, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // The common frame: the row on screen is the row the control was put on, so there is
            // nothing to do and nothing appended a second time.
            verify(toggleAttacherMock, times(1)).attachToggleTo(any(), any());
        }

        @Test
        void advancePutsAFreshControlUpOnceTheScreenRebuildsItsRow() {

            var firstRow = ShownFilterRows.createRowWithRoomToSpare();
            var rebuiltRow = ShownFilterRows.createRowWithRoomToSpare();
            var shownRow = new AtomicReference<>(firstRow);
            var screenLayerControl = buildScreenLayerControl();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> screenLayerControl, shownRow::get, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            shownRow.set(rebuiltRow);
            upkeep.advance(PAUSED_FRAME);

            // Reopening the screen builds a new row and leaves the old control attached to a widget
            // nobody can see, so the new row gets one of its own.
            var storedVisibility = screenLayerControl.getStoredVisibility();

            verify(toggleAttacherMock).attachToggleTo(firstRow, storedVisibility);
            verify(toggleAttacherMock).attachToggleTo(rebuiltRow, storedVisibility);
        }

        @Test
        void advanceRemembersEachScreensRowSeparately() {

            var mapRow = ShownFilterRows.createRowWithRoomToSpare();
            var intelRow = ShownFilterRows.createRowWithRoomToSpare();
            var mapLayerControl = buildScreenLayerControl();
            var intelLayerControl = buildScreenLayerControl();
            var liveScreenLayerControl = new AtomicReference<>(mapLayerControl);
            var shownRow = new AtomicReference<>(mapRow);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, liveScreenLayerControl::get, shownRow::get, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);

            liveScreenLayerControl.set(intelLayerControl);
            shownRow.set(intelRow);
            upkeep.advance(PAUSED_FRAME);

            liveScreenLayerControl.set(mapLayerControl);
            shownRow.set(mapRow);
            upkeep.advance(PAUSED_FRAME);

            // Moving to the other screen and back does not append a second control to the first
            // screen's row: each screen's row is remembered against that screen's own state.
            verify(toggleAttacherMock, times(2)).attachToggleTo(any(), any());
            verify(toggleAttacherMock, times(1))
                .attachToggleTo(mapRow, mapLayerControl.getStoredVisibility());
        }

        @Test
        void advanceWritesNothingWhileTheSwitchIsClosed() {

            var screenLayerControl = buildScreenLayerControl();
            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

            new MapLayerToggleUpkeep(
                SWITCH_CLOSED,
                () -> screenLayerControl,
                ShownFilterRows::createRowWithRoomToSpare,
                toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // The hatch is closed, so the row is left exactly as the game built it.
            verifyNoInteractions(toggleAttacherMock);
        }

        @Test
        void advanceWritesNothingWhileNoMapIsOnScreen() {

            var screenLayerControl = buildScreenLayerControl();
            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

            new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> screenLayerControl, () -> null, toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // Every screen showing no map, which is most of them - the ordinary answer rather than
            // a failure.
            verifyNoInteractions(toggleAttacherMock);
        }

        @Test
        void advanceTriesAgainOnTheNextFrameAfterARowRefusesTheControl() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var screenLayerControl = buildScreenLayerControl();
            var toggleAttacherMock = buildRefusingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> screenLayerControl, () -> shownRow, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // A refusal is not an attachment, so nothing is remembered - which is what lets a row
            // the layout had not placed yet take a control on a later frame.
            verify(toggleAttacherMock, times(2)).attachToggleTo(any(), any());
        }

        @Test
        void advanceActsOnAStoredHideOnceAControlIsStanding() {

            var screenLayerControl = buildScreenLayerControlOverAHiddenSave();

            new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                () -> screenLayerControl,
                ShownFilterRows::createRowWithRoomToSpare,
                buildAcceptingAttacherMock())
                .advance(PAUSED_FRAME);

            // The hide the player made in an earlier session, honoured again now that there is a box
            // on the row to take it back with.
            assertThat(screenLayerControl.areLayersShown())
                .isFalse();
        }

        @Test
        void advanceLeavesAStoredHideUnactedOnAfterARowRefusesAControl() {

            var screenLayerControl = buildScreenLayerControlOverAHiddenSave();

            new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                () -> screenLayerControl,
                ShownFilterRows::createRowWithRoomToSpare,
                buildRefusingAttacherMock())
                .advance(PAUSED_FRAME);

            // The whole reason the word is said here rather than anywhere earlier: a screen that
            // never got a control goes on showing its layers whatever the save holds, so a reach that
            // stops working cannot leave a player with them switched off and nothing to switch them
            // back on.
            assertThat(screenLayerControl.areLayersShown())
                .isTrue();
        }

        @Test
        void advanceSwallowsAFailedReadAndPutsTheControlUpOnTheNextFrame() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var readCount = new AtomicInteger();
            var screenLayerControl = buildScreenLayerControl();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            Supplier<MapFilterRow> resolveShownRow = () -> {
                if (readCount.getAndIncrement() == 0) {
                    throw new IllegalStateException("the map screen no longer has this shape");
                }
                return shownRow;
            };

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> screenLayerControl, resolveShownRow, toggleAttacherMock);

            assertThatCode(() -> upkeep.advance(PAUSED_FRAME))
                .doesNotThrowAnyException();

            verify(toggleAttacherMock, never()).attachToggleTo(any(), any());

            upkeep.advance(PAUSED_FRAME);

            // A write into another party's widget must not be able to take the pass down with it,
            // and a session that failed once is not written off: the next frame reaches the row.
            verify(toggleAttacherMock)
                .attachToggleTo(shownRow, screenLayerControl.getStoredVisibility());
        }
    }

    @Nested
    class IsDone {

        @Test
        void isDoneIsFalseSoThePassRunsForTheSession() {
            // The row is rebuilt for as long as the player keeps opening map screens, so a pass that
            // ended would leave every screen opened after it bare.
            assertThat(new MapLayerToggleUpkeep().isDone())
                .isFalse();
        }
    }

    @Nested
    class RunWhilePaused {

        @Test
        void runWhilePausedIsTrueSoTheControlReachesTheScreensThatCarryARow() {
            // Every screen carrying a filter row pauses the campaign, so a pass that stood down
            // while paused would run on none of the frames it exists for.
            assertThat(new MapLayerToggleUpkeep().runWhilePaused())
                .isTrue();
        }
    }

    // One screen's show-or-hide state, real rather than a stand-in: what the pass says to it is only
    // worth what the screen's layers then read, and a stand-in would pin the saying and not the worth.
    // Its stored pick is the stand-in instead, since which save it came off is nothing to do with this.
    private static ControlBackedMapLayerVisibility buildScreenLayerControl() {
        return new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class));
    }

    // The same over a save whose layers were switched off in an earlier session, which is the state
    // the whole no-control-no-hiding rule exists for.
    private static ControlBackedMapLayerVisibility buildScreenLayerControlOverAHiddenSave() {

        var storedVisibilityMock = mock(MapLayerVisibility.class);

        when(storedVisibilityMock.areLayersShown())
            .thenReturn(false);

        return new ControlBackedMapLayerVisibility(storedVisibilityMock);
    }

    // An attachment that succeeds, which is what the cases about remembering rows are posed over.
    private static MapLayerToggleAttacher buildAcceptingAttacherMock() {

        var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

        when(toggleAttacherMock.attachToggleTo(any(), any()))
            .thenReturn(true);

        return toggleAttacherMock;
    }

    // A row that will not take a control - no room left on it, or a shape that no longer builds a
    // drivable button. Stated rather than left to the stub's own default, so a case reading a refusal
    // is reading one the attacher gave.
    private static MapLayerToggleAttacher buildRefusingAttacherMock() {

        var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

        when(toggleAttacherMock.attachToggleTo(any(), any()))
            .thenReturn(false);

        return toggleAttacherMock;
    }
}
