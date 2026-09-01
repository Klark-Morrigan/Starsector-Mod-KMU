package kmu.maplayers.base.chrome;

import kmlib.starsector.ui.map.controls.MapFilterRow;

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
import static org.mockito.Mockito.inOrder;
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
 * <p>And when it says the screen has a control at all, which is what a stored hide is honoured
 * against: after a box is standing, never after a row that refused one. That word is the half of
 * failing open no log line covers, so the cases pinning it are here beside the ones pinning the write.
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

    // What the cases about rows hand in for the word that a screen now has a control: when that word
    // is said is its own cases' subject, and these are about when the row is written to.
    private static final Runnable RECORD_NOTHING = () -> {
    };

    @Nested
    class Advance {

        @Test
        void advancePutsAControlOnTheRowOnScreen() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> visibilityMock, RECORD_NOTHING, () -> shownRow, toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // Bound to the pick of the screen the row belongs to, which is what stops a control on
            // one screen's row moving the other screen's layers.
            verify(toggleAttacherMock).attachToggleTo(shownRow, visibilityMock);
        }

        @Test
        void advanceLeavesTheControlAloneWhileItStandsOnTheRowOnScreen() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> visibilityMock, RECORD_NOTHING, () -> shownRow, toggleAttacherMock);

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
            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> visibilityMock, RECORD_NOTHING, shownRow::get, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            shownRow.set(rebuiltRow);
            upkeep.advance(PAUSED_FRAME);

            // Reopening the screen builds a new row and leaves the old control attached to a widget
            // nobody can see, so the new row gets one of its own.
            verify(toggleAttacherMock).attachToggleTo(firstRow, visibilityMock);
            verify(toggleAttacherMock).attachToggleTo(rebuiltRow, visibilityMock);
        }

        @Test
        void advanceRemembersEachScreensRowSeparately() {

            var mapRow = ShownFilterRows.createRowWithRoomToSpare();
            var intelRow = ShownFilterRows.createRowWithRoomToSpare();
            var mapVisibilityMock = mock(MapLayerVisibility.class);
            var intelVisibilityMock = mock(MapLayerVisibility.class);
            var liveScreenVisibility = new AtomicReference<>(mapVisibilityMock);
            var shownRow = new AtomicReference<>(mapRow);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                liveScreenVisibility::get,
                RECORD_NOTHING,
                shownRow::get,
                toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);

            liveScreenVisibility.set(intelVisibilityMock);
            shownRow.set(intelRow);
            upkeep.advance(PAUSED_FRAME);

            liveScreenVisibility.set(mapVisibilityMock);
            shownRow.set(mapRow);
            upkeep.advance(PAUSED_FRAME);

            // Moving to the other screen and back does not append a second control to the first
            // screen's row: each screen's row is remembered against that screen's own pick.
            verify(toggleAttacherMock, times(2)).attachToggleTo(any(), any());
            verify(toggleAttacherMock, times(1)).attachToggleTo(mapRow, mapVisibilityMock);
        }

        @Test
        void advanceWritesNothingWhileTheSwitchIsClosed() {

            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

            new MapLayerToggleUpkeep(
                SWITCH_CLOSED,
                () -> visibilityMock,
                RECORD_NOTHING,
                ShownFilterRows::createRowWithRoomToSpare,
                toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // The hatch is closed, so the row is left exactly as the game built it.
            verifyNoInteractions(toggleAttacherMock);
        }

        @Test
        void advanceWritesNothingWhileNoMapIsOnScreen() {

            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

            new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> visibilityMock, RECORD_NOTHING, () -> null, toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // Every screen showing no map, which is most of them - the ordinary answer rather than
            // a failure.
            verifyNoInteractions(toggleAttacherMock);
        }

        @Test
        void advanceTriesAgainOnTheNextFrameAfterARowRefusesTheControl() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

            when(toggleAttacherMock.attachToggleTo(any(), any()))
                .thenReturn(false);

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> visibilityMock, RECORD_NOTHING, () -> shownRow, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // A refusal is not an attachment, so nothing is remembered - which is what lets a row
            // the layout had not placed yet take a control on a later frame.
            verify(toggleAttacherMock, times(2)).attachToggleTo(any(), any());
        }

        @Test
        void advanceSaysTheScreenHasAControlOnceOneIsStanding() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = buildAcceptingAttacherMock();
            var recordControlAttachedMock = mock(Runnable.class);

            new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                () -> visibilityMock,
                recordControlAttachedMock,
                () -> shownRow,
                toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // Said after the box is standing rather than before it, since the box is seeded from the
            // stored pick: a screen told it has a control first would honour a stored hide against a
            // box that had just read the layers as shown.
            var attachmentThenTheWord = inOrder(toggleAttacherMock, recordControlAttachedMock);

            attachmentThenTheWord
                .verify(toggleAttacherMock)
                .attachToggleTo(shownRow, visibilityMock);
            attachmentThenTheWord
                .verify(recordControlAttachedMock)
                .run();
        }

        @Test
        void advanceSaysNothingAboutAControlAfterARowRefusesOne() {

            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);
            var recordControlAttachedMock = mock(Runnable.class);

            when(toggleAttacherMock.attachToggleTo(any(), any()))
                .thenReturn(false);

            new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                () -> visibilityMock,
                recordControlAttachedMock,
                ShownFilterRows::createRowWithRoomToSpare,
                toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // The whole point of the word being said here: a screen that never got a control goes on
            // showing its layers whatever the save holds, so a reach that stops working cannot leave
            // a player with them switched off and nothing to switch them back on.
            verifyNoInteractions(recordControlAttachedMock);
        }

        @Test
        void advanceSwallowsAFailedReadAndPutsTheControlUpOnTheNextFrame() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var readCount = new AtomicInteger();
            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            Supplier<MapFilterRow> resolveShownRow = () -> {
                if (readCount.getAndIncrement() == 0) {
                    throw new IllegalStateException("the map screen no longer has this shape");
                }
                return shownRow;
            };

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> visibilityMock, RECORD_NOTHING, resolveShownRow, toggleAttacherMock);

            assertThatCode(() -> upkeep.advance(PAUSED_FRAME))
                .doesNotThrowAnyException();

            verify(toggleAttacherMock, never()).attachToggleTo(any(), any());

            upkeep.advance(PAUSED_FRAME);

            // A write into another party's widget must not be able to take the pass down with it,
            // and a session that failed once is not written off: the next frame reaches the row.
            verify(toggleAttacherMock).attachToggleTo(shownRow, visibilityMock);
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

    // An attachment that succeeds, which is what the cases about remembering rows are posed over.
    private static MapLayerToggleAttacher buildAcceptingAttacherMock() {

        var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

        when(toggleAttacherMock.attachToggleTo(any(), any()))
            .thenReturn(true);

        return toggleAttacherMock;
    }
}
