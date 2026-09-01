package kmu.maplayers.base.chrome;

import kmlib.starsector.ui.map.controls.MapFilterRow;
import kmlib.starsector.ui.map.controls.MapFilterRows;
import kmlib.starsector.ui.map.probes.EmbeddedMap;
import kmlib.testfixtures.starsector.ui.map.controls.FilteredMapWidgetFake;
import kmlib.testfixtures.starsector.ui.map.controls.MapFilterRowFake;

import kmu.maplayers.base.layer.MapLayerVisibility;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
        void putsAControlOnTheRowOnScreen() {

            var shownRow = buildRow();
            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            buildUpkeep(SWITCH_OPEN, () -> visibilityMock, () -> shownRow, toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // Bound to the pick of the screen the row belongs to, which is what stops a control on
            // one screen's row moving the other screen's layers.
            verify(toggleAttacherMock).attachToggleTo(shownRow, visibilityMock);
        }

        @Test
        void leavesTheControlAloneWhileItStandsOnTheRowOnScreen() {

            var shownRow = buildRow();
            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = buildUpkeep(
                SWITCH_OPEN, () -> visibilityMock, () -> shownRow, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // The common frame: the row on screen is the row the control was put on, so there is
            // nothing to do and nothing appended a second time.
            verify(toggleAttacherMock, times(1)).attachToggleTo(any(), any());
        }

        @Test
        void putsAFreshControlUpOnceTheScreenRebuildsItsRow() {

            var firstRow = buildRow();
            var rebuiltRow = buildRow();
            var shownRow = new AtomicReference<>(firstRow);
            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = buildUpkeep(
                SWITCH_OPEN, () -> visibilityMock, shownRow::get, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            shownRow.set(rebuiltRow);
            upkeep.advance(PAUSED_FRAME);

            // Reopening the screen builds a new row and leaves the old control attached to a widget
            // nobody can see, so the new row gets one of its own.
            verify(toggleAttacherMock).attachToggleTo(firstRow, visibilityMock);
            verify(toggleAttacherMock).attachToggleTo(rebuiltRow, visibilityMock);
        }

        @Test
        void remembersEachScreensRowSeparately() {

            var mapRow = buildRow();
            var intelRow = buildRow();
            var mapVisibilityMock = mock(MapLayerVisibility.class);
            var intelVisibilityMock = mock(MapLayerVisibility.class);
            var liveScreenVisibility = new AtomicReference<>(mapVisibilityMock);
            var shownRow = new AtomicReference<>(mapRow);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            var upkeep = buildUpkeep(
                SWITCH_OPEN, liveScreenVisibility::get, shownRow::get, toggleAttacherMock);

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
        void writesNothingWhileTheSwitchIsClosed() {

            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

            buildUpkeep(
                SWITCH_CLOSED,
                () -> visibilityMock,
                MapLayerToggleUpkeepTest::buildRow,
                toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // The hatch is closed, so the row is left exactly as the game built it.
            verifyNoInteractions(toggleAttacherMock);
        }

        @Test
        void writesNothingWhileNoMapIsOnScreen() {

            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

            buildUpkeep(SWITCH_OPEN, () -> visibilityMock, () -> null, toggleAttacherMock)
                .advance(PAUSED_FRAME);

            // Every screen showing no map, which is most of them - the ordinary answer rather than
            // a failure.
            verifyNoInteractions(toggleAttacherMock);
        }

        @Test
        void triesAgainOnTheNextFrameAfterARowRefusesTheControl() {

            var shownRow = buildRow();
            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

            when(toggleAttacherMock.attachToggleTo(any(), any()))
                .thenReturn(false);

            var upkeep = buildUpkeep(
                SWITCH_OPEN, () -> visibilityMock, () -> shownRow, toggleAttacherMock);

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // A refusal is not an attachment, so nothing is remembered - which is what lets a row
            // the layout had not placed yet take a control on a later frame.
            verify(toggleAttacherMock, times(2)).attachToggleTo(any(), any());
        }

        @Test
        void swallowsAFailedReadAndPutsTheControlUpOnTheNextFrame() {

            var shownRow = buildRow();
            var readCount = new AtomicInteger();
            var visibilityMock = mock(MapLayerVisibility.class);
            var toggleAttacherMock = buildAcceptingAttacherMock();

            Supplier<MapFilterRow> resolveShownRow = () -> {
                if (readCount.getAndIncrement() == 0) {
                    throw new IllegalStateException("the map screen no longer has this shape");
                }
                return shownRow;
            };

            var upkeep = buildUpkeep(
                SWITCH_OPEN, () -> visibilityMock, resolveShownRow, toggleAttacherMock);

            assertThatCode(() -> upkeep.advance(PAUSED_FRAME))
                .doesNotThrowAnyException();

            verify(toggleAttacherMock, never()).attachToggleTo(any(), any());

            upkeep.advance(PAUSED_FRAME);

            // A write into another party's widget must not be able to take the pass down with it,
            // and a session that failed once is not written off: the next frame reaches the row.
            verify(toggleAttacherMock).attachToggleTo(shownRow, visibilityMock);
        }
    }

    // One filter row, reached the way the running game's is - off a map widget's own accessor - so
    // each call stands a row of its own, and two of them are the two rows a reopened screen leaves
    // behind.
    private static MapFilterRow buildRow() {

        var mapWidget = new FilteredMapWidgetFake(
            MapFilterRowFake.createMapScreenStrip("Starscape", "Names"));

        return MapFilterRows.resolveEmbeddedMapFilterRow(new EmbeddedMap(mapWidget, List.of()));
    }

    private static MapLayerToggleUpkeep buildUpkeep(
            BooleanSupplier isToggleEnabled,
            Supplier<MapLayerVisibility> resolveLiveScreenVisibility,
            Supplier<MapFilterRow> resolveShownRow,
            MapLayerToggleAttacher toggleAttacher) {

        return new MapLayerToggleUpkeep(
            isToggleEnabled, resolveLiveScreenVisibility, resolveShownRow, toggleAttacher);
    }

    // An attachment that succeeds, which is what the cases about remembering rows are posed over.
    private static MapLayerToggleAttacher buildAcceptingAttacherMock() {

        var toggleAttacherMock = mock(MapLayerToggleAttacher.class);

        when(toggleAttacherMock.attachToggleTo(any(), any()))
            .thenReturn(true);

        return toggleAttacherMock;
    }
}
