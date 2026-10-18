package kmu.maplayers.base.tooltip.layout;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the held box is kept against: it answers exactly what composing would have answered
 * while nothing moves, and composes afresh the moment any of the things it was asked under moves -
 * another box, sector, system or level, the cursor reaching a cell again, or a layer rebuilding what
 * it draws.
 *
 * <p>Each signal gets a case of its own, since a memo that missed one would show the player a box
 * read before the change it ignored, and nothing on screen would say so.
 */
final class CellBoxMemoTests {

    private final MapHoverState hoverState = new MapHoverState();
    private final MapLayerRefreshBoard refreshBoard = new MapLayerRefreshBoard();
    private final CellBoxMemo memo = new CellBoxMemo(hoverState, refreshBoard);

    private final SystemCellTooltip tooltipMock = mock(SystemCellTooltip.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = buildSystem("askonia");

    // How many boxes were composed, which is what each case reads the memo through: a held box and a
    // fresh one can say the same thing, so only the composition tells them apart.
    private int compositionCount;

    @Nested
    class ResolveBox {

        @Test
        void answersWhatComposingAnswered() {

            var composedBox = resolveFactionsBox();

            assertThat(composedBox.body())
                .isSameAs(ComposedCellBody.NOTHING);
            assertThat(compositionCount)
                .isEqualTo(1);
        }

        @Test
        void keepsTheBoxWhileNothingMoves() {
            // The cursor resting on one cell, frame after frame: the read behind the box answers the
            // same thing every time, so it is made once.
            var firstBox = resolveFactionsBox();

            assertThat(resolveFactionsBox())
                .isSameAs(firstBox);
            assertThat(compositionCount)
                .isEqualTo(1);
        }

        @Test
        void composesAgainForAnotherSystem() {

            resolveFactionsBox();

            memo.resolveBox(
                tooltipMock,
                sectorMock,
                buildSystem("corvus"),
                HoverTooltipDetailLevel.FACTIONS,
                CellBoxMemoTests.this::composeBox);

            assertThat(compositionCount)
                .isEqualTo(2);
        }

        @Test
        void composesAgainAtAnotherLevel() {
            // A press of the detail key reads the box to another depth, which is another box.
            resolveFactionsBox();

            memo.resolveBox(
                tooltipMock,
                sectorMock,
                systemMock,
                HoverTooltipDetailLevel.SYSTEM_COMPOSITION,
                CellBoxMemoTests.this::composeBox);

            assertThat(compositionCount)
                .isEqualTo(2);
        }

        @Test
        void composesAgainForAnotherBox() {
            // A view switch injects another box over the same cell, which reads the system its own way.
            resolveFactionsBox();

            memo.resolveBox(
                mock(SystemCellTooltip.class),
                sectorMock,
                systemMock,
                HoverTooltipDetailLevel.FACTIONS,
                CellBoxMemoTests.this::composeBox);

            assertThat(compositionCount)
                .isEqualTo(2);
        }

        @Test
        void composesAgainForAnotherSector() {
            // The machinery standing in where no game is loaded is shared by every sector without one,
            // so a box held for one of them must not be served for the next.
            resolveFactionsBox();

            memo.resolveBox(
                tooltipMock,
                mock(SectorAPI.class),
                systemMock,
                HoverTooltipDetailLevel.FACTIONS,
                CellBoxMemoTests.this::composeBox);

            assertThat(compositionCount)
                .isEqualTo(2);
        }

        @Test
        void composesAgainOnceTheCursorReachesACellAgain() {
            // Stepping off a cell and back onto it is a fresh visit, read afresh - which is also what
            // gives a player a way to see the economy as it stands now.
            resolveFactionsBox();

            hoverState.recordCellArrival();

            resolveFactionsBox();

            assertThat(compositionCount)
                .isEqualTo(2);
        }

        @Test
        void composesAgainOnceALayerRebuildsWhatItDraws() {
            // Every setting, colony event and alliance change that moves the map leads to a rebuild,
            // and a box read before it describes a map that is no longer drawn.
            resolveFactionsBox();

            refreshBoard.requestRefresh(MapLayerCommonRefreshSignal.DRAW_LISTS);

            resolveFactionsBox();

            assertThat(compositionCount)
                .isEqualTo(2);
        }

        @Test
        void keepsTheBoxThroughASignalItDoesNotRead() {
            // A preference that moved nothing on the map rebuilds nothing, so the box holds.
            resolveFactionsBox();

            refreshBoard.requestRefresh(MapLayerCommonRefreshSignal.FILTER);

            resolveFactionsBox();

            assertThat(compositionCount)
                .isEqualTo(1);
        }
    }

    @Nested
    class DisposeMachinery {

        @Test
        void composesAfreshOnceReleased() {

            resolveFactionsBox();

            memo.disposeMachinery();

            resolveFactionsBox();

            assertThat(compositionCount)
                .isEqualTo(2);
        }
    }

    // The box over the posed system at the shallowest level, which is what every case not about the
    // level asks for.
    private ComposedCellBox resolveFactionsBox() {

        return memo.resolveBox(
            tooltipMock,
            sectorMock,
            systemMock,
            HoverTooltipDetailLevel.FACTIONS,
            this::composeBox);
    }

    // A composition saying nothing, counted. What it says is never read: whether it was made is.
    private ComposedCellBox composeBox() {

        compositionCount++;
        return new ComposedCellBox(List.of(), ComposedCellBody.NOTHING);
    }

    // A system keyed by its ID alone, which is all a key is read off for a system with no centre and
    // no anchor.
    private static StarSystemAPI buildSystem(String systemId) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(systemId);

        return systemMock;
    }
}
