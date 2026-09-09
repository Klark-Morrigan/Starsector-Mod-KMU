package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.campaign.CoreUITabId;

import kmlib.starsector.ui.map.controls.MapFilterRow;
import kmlib.starsector.ui.map.controls.MapFilterToggle;
import kmlib.testfixtures.starsector.ui.map.controls.MapFilterButtonFake;
import kmlib.testfixtures.starsector.ui.map.controls.MapFilterRowFake;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.ControlBackedMapLayerVisibility;
import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenMemoryScopes;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
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
 * <p>And that the box standing there says what its screen holds - as it goes up, and on every frame
 * after, since the pick moves under a standing box in more ways than one and a box saying the layers
 * are shown over an empty map is worse than no box at all. The cases posing that read the tick off a
 * real box on a real row, that being the whole of what the claim is about.
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
 * <p>What the word is then worth to the pick standing on the tab it withholds is not here at all:
 * {@link MapLayerPickUpkeep} settles that, per frame and for both screens, a pick being stranded by a
 * row the player arranged as readily as by a box.
 *
 * <p>And what the reach costs on the frames it can find nothing, which is most of them: a screen with
 * no filter row on it is never walked, and a screen that threw is not walked again until the player
 * has been on another. The two are one case apart - a row merely absent on a map screen is retried on
 * every frame, since that is what lets a row the layout had not placed yet take a box a moment
 * later - so both are posed, and posed against how often the row read is actually taken.
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

    // Where an appended box lands on a row carrying the game's own buttons.
    private static final int APPENDED_BUTTON_INDEX = ShownFilterRows.VANILLA_BUTTON_COUNT;

    // The words on the box the stand-in attachment puts up. Arbitrary: which words the live one uses
    // is its own case, and nothing here reads them.
    private static final String BOX_LABEL = "Map layers";

    // The screen the box exists for, which is what every case below but the gate's own poses. Stated
    // once here rather than at each of them, since which of the two map screens it is settles nothing
    // for any case that is not about the gate.
    private static final Supplier<CoreUITabId> ON_THE_MAP_SCREEN = () -> CoreUITabId.MAP;

    @Nested
    class Advance {

        @Test
        void advancePutsAControlOnTheRowOnScreen() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var screenPicks = buildScreenPicks();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            buildUpkeepOnTheMapScreen(
                SWITCH_OPEN, () -> screenPicks, () -> shownRow, toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // Bound to the stored pick of the screen the row belongs to: to that screen's, so a
            // control on one screen's row cannot move the other screen's layers, and to the stored
            // pick, since a box is what lifts the no-control-no-hiding rule rather than a reader of it.
            verify(toggleAttacherMock)
                .attachToggleTo(shownRow, readStoredVisibility(screenPicks));
        }

        @Test
        void advanceLeavesTheControlAloneWhileItStandsOnTheRowOnScreen() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var screenPicks = buildScreenPicks();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = buildUpkeepOnTheMapScreen(
                SWITCH_OPEN, () -> screenPicks, () -> shownRow, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // The common frame: the box held for this screen is still on the row that is up, so there
            // is nothing to append and nothing appended a second time.
            verify(toggleAttacherMock, times(1)).attachToggleTo(any(), any());
        }

        @Test
        void advancePutsAFreshControlUpOnceTheScreenRebuildsItsRow() {

            var firstRow = ShownFilterRows.createRowWithRoomToSpare();
            var rebuiltRow = ShownFilterRows.createRowWithRoomToSpare();
            var shownRow = new AtomicReference<>(firstRow);
            var screenPicks = buildScreenPicks();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = buildUpkeepOnTheMapScreen(
                SWITCH_OPEN, () -> screenPicks, shownRow::get, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            shownRow.set(rebuiltRow);
            upkeep.advance(PAUSED_FRAME);

            // Reopening the screen builds a new row and leaves the old box attached to a widget
            // nobody can see, so the new row gets one of its own.
            var storedVisibility = readStoredVisibility(screenPicks);

            verify(toggleAttacherMock).attachToggleTo(firstRow, storedVisibility);
            verify(toggleAttacherMock).attachToggleTo(rebuiltRow, storedVisibility);
        }

        @Test
        void advanceRemembersEachScreensBoxSeparately() {

            var mapRow = ShownFilterRows.createRowWithRoomToSpare();
            var intelRow = ShownFilterRows.createRowWithRoomToSpare();
            var mapScreenPicks = buildScreenPicks();
            var intelScreenPicks = buildScreenPicks();
            var liveScreenPicks = new AtomicReference<>(mapScreenPicks);
            var shownRow = new AtomicReference<>(mapRow);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = buildUpkeepOnTheMapScreen(
                SWITCH_OPEN, liveScreenPicks::get, shownRow::get, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);

            liveScreenPicks.set(intelScreenPicks);
            shownRow.set(intelRow);
            upkeep.advance(PAUSED_FRAME);

            liveScreenPicks.set(mapScreenPicks);
            shownRow.set(mapRow);
            upkeep.advance(PAUSED_FRAME);

            // Moving to the other screen and back does not append a second box to the first screen's
            // row: each screen's box is held against that screen's own picks.
            verify(toggleAttacherMock, times(2)).attachToggleTo(any(), any());
            verify(toggleAttacherMock, times(1))
                .attachToggleTo(mapRow, readStoredVisibility(mapScreenPicks));
        }

        @Test
        void advanceWritesNothingWhileTheSwitchIsClosed() {

            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

            buildUpkeepOnTheMapScreen(
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

            buildUpkeepOnTheMapScreen(
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
        void advanceReachesForNoRowOnAScreenThatCarriesNone() {

            var rowReadCount = new AtomicInteger();

            Supplier<MapFilterRow> resolveShownRow = () -> {
                rowReadCount.getAndIncrement();
                return ShownFilterRows.createRowWithRoomToSpare();
            };

            new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                MapLayerToggleUpkeepTest::buildScreenPicks,
                resolveShownRow,
                () -> CoreUITabId.FLEET,
                buildAcceptingAttacherMock())
                .advance(PAUSED_FRAME);

            // The row read walks the running game's widget tree by name to find, on a screen that
            // carries no filter row, nothing. Which screen is up is published API and one hop, so it
            // is asked first and the walk is spared - on most frames of a game, and all of the ones
            // spent flying about.
            assertThat(rowReadCount)
                .hasValue(0);
        }

        @Test
        void advanceStopsReachingIntoAScreenThatThrewUntilAnotherIsOpened() {

            var rowReadCount = new AtomicInteger();
            var shownCoreTab = new AtomicReference<>(CoreUITabId.MAP);

            Supplier<MapFilterRow> resolveShownRow = () -> {
                rowReadCount.getAndIncrement();
                throw new IllegalStateException("this build's map tab is not shaped that way");
            };

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                MapLayerToggleUpkeepTest::buildScreenPicks,
                resolveShownRow,
                shownCoreTab::get,
                buildAcceptingAttacherMock());

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // A reach that throws is a game build whose shape this does not recognise, and it will
            // not recognise it on the next frame either - so unlike a row that is merely absent, the
            // refusal is held against the screen it happened on.
            assertThat(rowReadCount)
                .hasValue(1);

            shownCoreTab.set(CoreUITabId.INTEL);
            upkeep.advance(PAUSED_FRAME);

            // Held against that screen and not against the pass: the other map screen is a different
            // widget tree and is worth asking, and so is this one when the player comes back to it.
            assertThat(rowReadCount)
                .hasValue(2);
        }

        @Test
        void advanceTriesAgainOnTheNextFrameAfterARowRefusesTheControl() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var toggleAttacherMock = buildRefusingAttacherMock();

            var upkeep = buildUpkeepOnTheMapScreen(
                SWITCH_OPEN,
                MapLayerToggleUpkeepTest::buildScreenPicks,
                () -> shownRow,
                toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // A refusal is not an attachment, so nothing is held - which is what lets a row the
            // layout had not placed yet take a box on a later frame.
            verify(toggleAttacherMock, times(2)).attachToggleTo(any(), any());
        }

        @Test
        void advanceOpensTheBoxShowingWhatTheScreenHolds() {

            var rowFake = ShownFilterRows.createRowFakeWithRoomToSpare();
            var shownRow = ShownFilterRows.createRowOver(rowFake);

            buildUpkeepOnTheMapScreen(
                SWITCH_OPEN,
                MapLayerToggleUpkeepTest::buildScreenPicksOverAHiddenSave,
                () -> shownRow,
                buildAcceptingAttacherMock())
                .advance(PAUSED_FRAME);

            // On the frame it goes up, not the one after: a box that opened ticked over a hidden save
            // would be wrong on the only frame the player meets it on.
            assertThat(readAppendedButton(rowFake).isChecked())
                .isFalse();
        }

        @Test
        void advanceKeepsTheBoxShowingAPickThatMovedUnderIt() {

            var rowFake = ShownFilterRows.createRowFakeWithRoomToSpare();
            var shownRow = ShownFilterRows.createRowOver(rowFake);
            var areLayersShown = new AtomicBoolean(true);
            var screenPicks = buildScreenPicksReading(areLayersShown);

            var upkeep = buildUpkeepOnTheMapScreen(
                SWITCH_OPEN, () -> screenPicks, () -> shownRow, buildAcceptingAttacherMock());

            upkeep.advance(PAUSED_FRAME);
            areLayersShown.set(false);
            upkeep.advance(PAUSED_FRAME);

            // The pick can move under a standing box - the heal beside this pass does it, and so does
            // the hatch closed and reopened over one - and the row offers no way to take a box off and
            // put a fresh one up. So it is written from the pick each frame rather than seeded once.
            assertThat(readAppendedButton(rowFake).isChecked())
                .isFalse();
        }

        @Test
        void advanceActsOnAStoredHideOnceAControlIsStanding() {

            var screenPicks = buildScreenPicksOverAHiddenSave();

            buildUpkeepOnTheMapScreen(
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

            buildUpkeepOnTheMapScreen(
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

            var upkeep = buildUpkeepOnTheMapScreen(
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

            var upkeep = buildUpkeepOnTheMapScreen(
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
            verify(toggleAttacherMock, times(1)).attachToggleTo(any(), any());
        }

        @Test
        void advanceSwallowsAFailedReadAndPutsTheControlUpOnTheNextScreen() {

            var shownRow = ShownFilterRows.createRowWithRoomToSpare();
            var shownCoreTab = new AtomicReference<>(CoreUITabId.MAP);
            var screenPicks = buildScreenPicks();
            var toggleAttacherMock = buildAcceptingAttacherMock();

            Supplier<MapFilterRow> resolveShownRow = () -> {
                if (shownCoreTab.get() == CoreUITabId.MAP) {
                    throw new IllegalStateException("the map screen no longer has this shape");
                }
                return shownRow;
            };

            var upkeep = new MapLayerToggleUpkeep(
                SWITCH_OPEN,
                () -> screenPicks,
                resolveShownRow,
                shownCoreTab::get,
                toggleAttacherMock);

            assertThatCode(() -> upkeep.advance(PAUSED_FRAME))
                .doesNotThrowAnyException();

            verify(toggleAttacherMock, never()).attachToggleTo(any(), any());

            shownCoreTab.set(CoreUITabId.INTEL);
            upkeep.advance(PAUSED_FRAME);

            // A write into another party's widget must not be able to take the pass down with it,
            // and a session that failed once is not written off: what the refusal is held against is
            // the screen whose shape it was, so the other one still gets its box.
            verify(toggleAttacherMock)
                .attachToggleTo(shownRow, readStoredVisibility(screenPicks));
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

    // The pass as every case but the gate's own wants it: on a screen a row could be found on, so
    // what each of them poses is the row and not the screen.
    private static MapLayerToggleUpkeep buildUpkeepOnTheMapScreen(
            BooleanSupplier isToggleEnabled,
            Supplier<ScreenLayerPicks> resolveLiveScreenPicks,
            Supplier<MapFilterRow> resolveShownFilterRow,
            MapLayerToggleAttacher toggleAttacher) {

        return new MapLayerToggleUpkeep(
            isToggleEnabled,
            resolveLiveScreenPicks,
            resolveShownFilterRow,
            ON_THE_MAP_SCREEN,
            toggleAttacher);
    }

    // One screen's picks, with the show-or-hide state real rather than a stand-in: what the pass says
    // to it is only worth what the screen's layers then read, and a stand-in would pin the saying and
    // not the worth. The stored pick and the tab are the stand-ins instead, since which save they came
    // off is nothing to do with this.
    private static ScreenLayerPicks buildScreenPicks() {
        return buildScreenPicksOver(mock(MapLayerVisibility.class));
    }

    // The same over a save whose layers were switched off in an earlier session, which is the state
    // the whole no-control-no-hiding rule exists for.
    private static ScreenLayerPicks buildScreenPicksOverAHiddenSave() {

        var storedVisibilityMock = mock(MapLayerVisibility.class);

        when(storedVisibilityMock.areLayersShown())
            .thenReturn(false);

        return buildScreenPicksOver(storedVisibilityMock);
    }

    // The same over a save that answers whatever the given flag holds at the moment it is asked, for
    // the cases whose subject is a pick moving while a box stands over it.
    private static ScreenLayerPicks buildScreenPicksReading(AtomicBoolean areLayersShown) {

        var storedVisibilityMock = mock(MapLayerVisibility.class);

        when(storedVisibilityMock.areLayersShown())
            .thenAnswer(read -> areLayersShown.get());

        return buildScreenPicksOver(storedVisibilityMock);
    }

    private static ScreenLayerPicks buildScreenPicksOver(MapLayerVisibility storedVisibility) {
        return new ScreenLayerPicks(
            mock(ActiveLayerSelection.class),
            new ControlBackedMapLayerVisibility(storedVisibility),
            ScreenMemoryScopes.createStandInScreen());
    }

    // The stored pick a box is bound to, dug out of the picks so a case states which of the two
    // readings it means. A mock, so it can be verified as well as read.
    private static MapLayerVisibility readStoredVisibility(ScreenLayerPicks screenPicks) {
        return screenPicks.layerVisibility().getStoredVisibility();
    }

    // An attachment that succeeds, standing a real box on the row it is handed - so the pass's own
    // "is it still there" read answers as it would in play, and a case can read what the box shows
    // off the button itself. A stand-in handing back some other row's box would make the first of
    // those pass by accident and the second unaskable.
    private static MapLayerToggleAttacher buildAcceptingAttacherMock() {

        var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

        when(toggleAttacherMock.attachToggleTo(any(), any()))
            .thenAnswer(attachment -> MapFilterToggle.appendToRow(
                attachment.getArgument(0), BOX_LABEL, () -> { }));

        return toggleAttacherMock;
    }

    // A row that will not take a control - no room left on it, or a shape that no longer builds a
    // drivable button. Stated rather than left to the stub's own default, so a case reading a refusal
    // is reading one the attacher gave.
    private static MapLayerToggleAttacher buildRefusingAttacherMock() {

        var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

        when(toggleAttacherMock.attachToggleTo(any(), any()))
            .thenReturn(null);

        return toggleAttacherMock;
    }

    private static MapFilterButtonFake readAppendedButton(MapFilterRowFake rowFake) {
        return (MapFilterButtonFake) rowFake.getChildrenCopy().get(APPENDED_BUTTON_INDEX);
    }
}
