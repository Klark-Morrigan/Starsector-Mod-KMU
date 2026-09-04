package kmu.maplayers.base.chrome;

import kmlib.starsector.ui.map.controls.MapFilterRow;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.ControlBackedMapLayerVisibility;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.maplayers.base.layer.NoLayer;
import kmu.maplayers.base.layer.ScreenLayerPicks;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
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
 * a box is standing, never after a row that refused one, and no longer once the switch that permits
 * the reach is closed. That word is the half of failing open no log line covers, so the cases pinning
 * it are here beside the ones pinning the write - and they read it back off a real screen state rather
 * than off a stand-in, since what it is worth is exactly what the screen's layers then do.
 *
 * <p>The closed switch is held on both sides, because taking the word back has a cost of its own if
 * it is done carelessly: the box it describes is still standing on the row, the row offering no way
 * to remove one, so reopening the switch has to restore the word rather than stand a second box
 * beside the first.
 *
 * <p>The first box to stand on a screen also settles a pick that screen's strip stops offering once it
 * has one, so the cases pinning that are here too: the move rides the same word, is owed once, and is
 * owed only where a box actually went up. What the move is and why the strip withholds anything is
 * {@link kmu.maplayers.base.layer.ScreenLayerTabs}'s.
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

    // What a box opens showing on a screen whose pick the stand is about to take over: the map was
    // blank under that pick and stays blank under the hide it becomes.
    private static final boolean LAYERS_HIDDEN = false;

    private final MapLayer paintingLayerMock = mock(MapLayer.class);

    @AfterEach
    void restoreTheRosterTheCasesReplaced() {
        // The registry is static, so a roster left standing would outlive its case; only the ones
        // about the pick move register anything, and this covers them without each saying so.
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @Nested
    class Advance {

        @Test
        void advancePutsAControlOnTheRowOnScreen() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var screenPicks = buildScreenPicks();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> screenPicks, () -> shownRow, toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // Bound to the stored pick of the screen the row belongs to: to that screen's, so a
            // control on one screen's row cannot move the other screen's layers, and to the stored
            // pick, since a box is what lifts the no-control-no-hiding rule rather than a reader of it.
            verify(toggleAttacherMock).attachToggleTo(
                eq(shownRow), eq(readStoredVisibility(screenPicks)), anyBoolean());
        }

        @Test
        void advanceLeavesTheControlAloneWhileItStandsOnTheRowOnScreen() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var screenPicks = buildScreenPicks();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> screenPicks, () -> shownRow, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // The common frame: the row on screen is the row the control was put on, so there is
            // nothing to do and nothing appended a second time.
            verify(toggleAttacherMock, times(1)).attachToggleTo(any(), any(), anyBoolean());
        }

        @Test
        void advancePutsAFreshControlUpOnceTheScreenRebuildsItsRow() {

            var firstRow = ShownFilterRows.createRowWithRoomToSpare();
            var rebuiltRow = ShownFilterRows.createRowWithRoomToSpare();
            var shownRow = new AtomicReference<>(firstRow);
            var screenPicks = buildScreenPicks();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> screenPicks, shownRow::get, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            shownRow.set(rebuiltRow);
            upkeep.advance(PAUSED_FRAME);

            // Reopening the screen builds a new row and leaves the old control attached to a widget
            // nobody can see, so the new row gets one of its own.
            var storedVisibility = readStoredVisibility(screenPicks);

            verify(toggleAttacherMock)
                .attachToggleTo(eq(firstRow), eq(storedVisibility), anyBoolean());
            verify(toggleAttacherMock)
                .attachToggleTo(eq(rebuiltRow), eq(storedVisibility), anyBoolean());
        }

        @Test
        void advanceRemembersEachScreensRowSeparately() {

            var mapRow = ShownFilterRows.createRowWithRoomToSpare();
            var intelRow = ShownFilterRows.createRowWithRoomToSpare();
            var mapScreenPicks = buildScreenPicks();
            var intelScreenPicks = buildScreenPicks();
            var liveScreenPicks = new AtomicReference<>(mapScreenPicks);
            var shownRow = new AtomicReference<>(mapRow);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, liveScreenPicks::get, shownRow::get, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);

            liveScreenPicks.set(intelScreenPicks);
            shownRow.set(intelRow);
            upkeep.advance(PAUSED_FRAME);

            liveScreenPicks.set(mapScreenPicks);
            shownRow.set(mapRow);
            upkeep.advance(PAUSED_FRAME);

            // Moving to the other screen and back does not append a second control to the first
            // screen's row: each screen's row is remembered against that screen's own picks.
            verify(toggleAttacherMock, times(2)).attachToggleTo(any(), any(), anyBoolean());
            verify(toggleAttacherMock, times(1)).attachToggleTo(
                eq(mapRow), eq(readStoredVisibility(mapScreenPicks)), anyBoolean());
        }

        @Test
        void advanceWritesNothingWhileTheSwitchIsClosed() {

            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

            new MapLayerToggleUpkeep(
                SWITCH_CLOSED,
                MapLayerToggleUpkeepTest::buildScreenPicks,
                ShownFilterRows::createRowWithRoomToSpare,
                toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // The hatch is closed, so the row is left exactly as the game built it.
            verifyNoInteractions(toggleAttacherMock);
        }

        @Test
        void advanceWritesNothingWhileNoMapIsOnScreen() {

            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

            new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                MapLayerToggleUpkeepTest::buildScreenPicks,
                () -> null,
                toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // Every screen showing no map, which is most of them - the ordinary answer rather than
            // a failure.
            verifyNoInteractions(toggleAttacherMock);
        }

        @Test
        void advanceTriesAgainOnTheNextFrameAfterARowRefusesTheControl() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var toggleAttacherMock = buildRefusingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                MapLayerToggleUpkeepTest::buildScreenPicks,
                () -> shownRow,
                toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // A refusal is not an attachment, so nothing is remembered - which is what lets a row
            // the layout had not placed yet take a control on a later frame.
            verify(toggleAttacherMock, times(2)).attachToggleTo(any(), any(), anyBoolean());
        }

        @Test
        void advanceActsOnAStoredHideOnceAControlIsStanding() {

            var screenPicks = buildScreenPicksOverAHiddenSave();

            new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                () -> screenPicks,
                ShownFilterRows::createRowWithRoomToSpare,
                buildAcceptingAttacherMock())
                .advance(PAUSED_FRAME);

            // The hide the player made in an earlier session, honoured again now that there is a box
            // on the row to take it back with.
            assertThat(screenPicks.layerVisibility().areLayersShown())
                .isFalse();
        }

        @Test
        void advanceLeavesAStoredHideUnactedOnAfterARowRefusesAControl() {

            var screenPicks = buildScreenPicksOverAHiddenSave();

            new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                () -> screenPicks,
                ShownFilterRows::createRowWithRoomToSpare,
                buildRefusingAttacherMock())
                .advance(PAUSED_FRAME);

            // The whole reason the word is said here rather than anywhere earlier: a screen that
            // never got a control goes on showing its layers whatever the save holds, so a reach that
            // stops working cannot leave a player with them switched off and nothing to switch them
            // back on.
            assertThat(screenPicks.layerVisibility().areLayersShown())
                .isTrue();
        }

        @Test
        void advanceStopsActingOnAStoredHideOnceTheSwitchIsClosed() {

            var screenPicks = buildScreenPicksOverAHiddenSave();
            var isSwitchOpen = new AtomicBoolean(true);

            var upkeep = new MapLayerToggleUpkeep(
                isSwitchOpen::get,
                () -> screenPicks,
                ShownFilterRows::createRowWithRoomToSpare,
                buildAcceptingAttacherMock());

            upkeep.advance(PAUSED_FRAME);
            isSwitchOpen.set(false);
            upkeep.advance(PAUSED_FRAME);

            // Closing the hatch takes the control away, so it has to take the word with it. Left
            // standing, the word would hold the layers hidden with no box on any row to reverse
            // them - the switch would have become a way to hide the layers permanently, which is
            // the opposite of what it is for.
            assertThat(screenPicks.layerVisibility().areLayersShown())
                .isTrue();
        }

        @Test
        void advanceActsOnAStoredHideAgainOnceTheSwitchIsReopenedOverTheStandingRow() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var screenPicks = buildScreenPicksOverAHiddenSave();
            var isSwitchOpen = new AtomicBoolean(true);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = new MapLayerToggleUpkeep(
                isSwitchOpen::get, () -> screenPicks, () -> shownRow, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            isSwitchOpen.set(false);
            upkeep.advance(PAUSED_FRAME);
            isSwitchOpen.set(true);
            upkeep.advance(PAUSED_FRAME);

            // The box never left the row - the row offers no way to take one off - so reopening the
            // hatch restores the word rather than appending a second box beside the first.
            assertThat(screenPicks.layerVisibility().areLayersShown())
                .isFalse();
            verify(toggleAttacherMock, times(1)).attachToggleTo(any(), any(), anyBoolean());
        }

        @Test
        void advanceMovesAPickTheStripStopsOfferingOnceTheBoxStands() {

            var screenPicks = buildScreenPicksOnTheEmptyView();

            new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                () -> screenPicks,
                ShownFilterRows::createRowWithRoomToSpare,
                buildAcceptingAttacherMock())
                .advance(PAUSED_FRAME);

            // The picture is unchanged - blank map, blank map - and what the player chose is now held
            // by the control that can reverse it, rather than by a tab the strip no longer offers.
            verify(screenPicks.layerSelection())
                .selectLayer(paintingLayerMock);
            verify(readStoredVisibility(screenPicks))
                .showLayers(false);
        }

        @Test
        void advanceOpensTheBoxOnTheStateTheScreenSettlesAt() {

            var screenPicks = buildScreenPicksOnTheEmptyView();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                () -> screenPicks,
                ShownFilterRows::createRowWithRoomToSpare,
                toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // A box seeded from what the pick read as it went up would open ticked over the empty map
            // it is taking charge of, and say the layers were shown until it was used twice.
            verify(toggleAttacherMock)
                .attachToggleTo(any(), any(), eq(LAYERS_HIDDEN));
        }

        @Test
        void advanceMovesSuchAPickOnceRatherThanAtEveryStand() {

            var shownRow = new AtomicReference<>(ShownFilterRows.createRowWithRoomToSpare());
            var screenPicks = buildScreenPicksOnTheEmptyView();
            var storedVisibilityMock = readStoredVisibility(screenPicks);

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> screenPicks, shownRow::get, buildAcceptingAttacherMock());

            upkeep.advance(PAUSED_FRAME);
            shownRow.set(ShownFilterRows.createRowWithRoomToSpare());
            upkeep.advance(PAUSED_FRAME);

            // On the first box to stand and no later one. The pick this case leaves standing on the
            // withheld tab is the arrangement a save can never be in after the first move, and it is
            // posed precisely so a second move would show: every row a screen rebuilds would otherwise
            // write the player's tab away again.
            verify(storedVisibilityMock, times(1))
                .showLayers(false);
        }

        @Test
        void advanceLeavesTheScreensPickWhereItIsAfterARowRefusesAControl() {

            var screenPicks = buildScreenPicksOnTheEmptyView();

            new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                () -> screenPicks,
                ShownFilterRows::createRowWithRoomToSpare,
                buildRefusingAttacherMock())
                .advance(PAUSED_FRAME);

            // The move is what a standing box is worth to a screen, so a screen that got none keeps
            // both its tab and its picture: a player on the empty view whose row would not take a box
            // must not find the map painted instead. The pick is read to settle what a box would open
            // at, so what is pinned is that neither half of it was written.
            verify(screenPicks.layerSelection(), never())
                .selectLayer(any());
            verify(readStoredVisibility(screenPicks), never())
                .showLayers(anyBoolean());
        }

        @Test
        void advanceSwallowsAFailedReadAndPutsTheControlUpOnTheNextFrame() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var readCount = new AtomicInteger();
            var screenPicks = buildScreenPicks();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            Supplier<MapFilterRow> resolveShownRow = () -> {
                if (readCount.getAndIncrement() == 0) {
                    throw new IllegalStateException("the map screen no longer has this shape");
                }
                return shownRow;
            };

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN, () -> screenPicks, resolveShownRow, toggleAttacherMock);

            assertThatCode(() -> upkeep.advance(PAUSED_FRAME))
                .doesNotThrowAnyException();

            verify(toggleAttacherMock, never()).attachToggleTo(any(), any(), anyBoolean());

            upkeep.advance(PAUSED_FRAME);

            // A write into another party's widget must not be able to take the pass down with it,
            // and a session that failed once is not written off: the next frame reaches the row.
            verify(toggleAttacherMock).attachToggleTo(
                eq(shownRow), eq(readStoredVisibility(screenPicks)), anyBoolean());
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

    // One screen's picks, with the show-or-hide state real rather than a stand-in: what the pass says
    // to it is only worth what the screen's layers then read, and a stand-in would pin the saying and
    // not the worth. The stored pick and the tab are the stand-ins instead, since which save they came
    // off is nothing to do with this.
    private static ScreenLayerPicks buildScreenPicks() {
        return new ScreenLayerPicks(
            mock(ActiveLayerSelection.class),
            new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class)));
    }

    // The same over a save whose layers were switched off in an earlier session, which is the state
    // the whole no-control-no-hiding rule exists for.
    private static ScreenLayerPicks buildScreenPicksOverAHiddenSave() {

        var storedVisibilityMock = mock(MapLayerVisibility.class);

        when(storedVisibilityMock.areLayersShown())
            .thenReturn(false);

        return new ScreenLayerPicks(
            mock(ActiveLayerSelection.class),
            new ControlBackedMapLayerVisibility(storedVisibilityMock));
    }

    // The stored pick a box is bound to, dug out of the picks so a case states which of the two
    // readings it means. A mock, so it can be verified as well as read.
    private static MapLayerVisibility readStoredVisibility(ScreenLayerPicks screenPicks) {
        return screenPicks.layerVisibility().getStoredVisibility();
    }

    // An attachment that succeeds, which is what the cases about remembering rows are posed over.
    private static MapLayerToggleAttacher buildAcceptingAttacherMock() {

        var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

        when(toggleAttacherMock.attachToggleTo(any(), any(), anyBoolean()))
            .thenReturn(true);

        return toggleAttacherMock;
    }

    // A row that will not take a control - no room left on it, or a shape that no longer builds a
    // drivable button. Stated rather than left to the stub's own default, so a case reading a refusal
    // is reading one the attacher gave.
    private static MapLayerToggleAttacher buildRefusingAttacherMock() {

        var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

        when(toggleAttacherMock.attachToggleTo(any(), any(), anyBoolean()))
            .thenReturn(false);

        return toggleAttacherMock;
    }

    // A screen sitting on the empty view, over a roster that offers a layer that paints beside it -
    // the one arrangement a standing box has to settle, and the only one in which the strip withholds
    // anything at all.
    private ScreenLayerPicks buildScreenPicksOnTheEmptyView() {

        MapLayerRegistry.registerLayers(
            List.of(NoLayer.INSTANCE, paintingLayerMock), paintingLayerMock);

        var layerSelectionMock = mock(ActiveLayerSelection.class);

        when(layerSelectionMock.getActiveLayer())
            .thenReturn(NoLayer.INSTANCE);

        return new ScreenLayerPicks(
            layerSelectionMock,
            new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class)));
    }
}
