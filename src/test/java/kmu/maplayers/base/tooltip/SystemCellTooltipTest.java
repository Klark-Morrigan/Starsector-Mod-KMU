package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.render.gl.CursorTooltipRenderer;
import kmlib.starsector.ui.render.gl.CursorTooltipStyle;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.TooltipRow;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the shape this class settles for every layer that extends it - the part a body has no say in: the
 * hovered system is named above whatever the body says, the body's own opening line is parted from that
 * name, and a body with nothing to say draws no box at all rather than one echoing the cursor. Also that
 * a sector with no live economy is not read, since bodies assume one.
 */
final class SystemCellTooltipTest {
    private static final Color GOLD = new Color(255, 200, 100);
    private static final Color PLAYER_BASE = new Color(100, 160, 200);
    private static final String SYSTEM_NAME = "Corvus";

    // The rows the box lays out, in draw order: the system name it is titled with, then the body's own
    // lines under it.
    private static final int HEADER_ROW = 0;
    private static final int FIRST_BODY_ROW = 1;
    private static final int SECOND_BODY_ROW = 2;

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void installColours() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();
        miscMock = Mockito.mockStatic(Misc.class);
        miscMock.when(Misc::getHighlightColor).thenReturn(GOLD);
        miscMock.when(Misc::getBasePlayerColor).thenReturn(PLAYER_BASE);
        // The box's typography carries a default text colour of its own, resolved when the style is
        // built for the paint - so it is stubbed even though no assertion here reads it.
        miscMock.when(Misc::getTextColor).thenReturn(Color.LIGHT_GRAY);
    }

    @AfterEach
    void clearColours() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class RenderFor {

        @Test
        void renderForTitlesTheBoxWithTheHoveredSystemsName() {
            var bodyRow = buildBodyRow("The Hegemony");
            var tooltipFake = new SystemCellTooltipFake(List.of(bodyRow));

            var rows = captureDrawnRows(tooltipFake);

            assertThat(rows.get(HEADER_ROW))
                    .isEqualTo(TooltipRow.createCentredRow(new TextSpan(SYSTEM_NAME, GOLD)));
        }

        @Test
        void renderForPartsTheBodysOpeningLineFromTheTitle() {
            // The parting is the shared shape's decision, not a body's: every cell tooltip is a title
            // over a body, so a layer cannot forget it and cannot double it on later lines.
            var openingRow = buildBodyRow("The Hegemony");
            var followingRow = buildBodyRow("Independent");
            var tooltipFake = new SystemCellTooltipFake(List.of(openingRow, followingRow));

            var rows = captureDrawnRows(tooltipFake);

            assertThat(rows.get(FIRST_BODY_ROW)).isEqualTo(openingRow.opensSection());
            assertThat(rows.get(SECOND_BODY_ROW)).isEqualTo(followingRow);
        }

        @Test
        void renderForDrawsNothingForABodyWithNothingToSay() {
            // A lone system name only repeats what the cursor already sits on, so an empty body is no
            // box rather than a titled empty one.
            var tooltipFake = new SystemCellTooltipFake(List.of());

            try (MockedStatic<CursorTooltipRenderer> rendererMock =
                    Mockito.mockStatic(CursorTooltipRenderer.class)) {
                tooltipFake.renderFor(sectorWithEconomy(), systemNamed());

                rendererMock.verifyNoInteractions();
            }
        }

        @Test
        void renderForDrawsNothingWithoutALiveEconomy() {
            // Bodies read the economy for what a layer holds in the system, so a sector without one is
            // not asked for a body at all.
            var tooltipFake = new SystemCellTooltipFake(List.of(buildBodyRow("The Hegemony")));

            try (MockedStatic<CursorTooltipRenderer> rendererMock =
                    Mockito.mockStatic(CursorTooltipRenderer.class)) {
                tooltipFake.renderFor(mock(SectorAPI.class), systemNamed());

                rendererMock.verifyNoInteractions();
                assertThat(tooltipFake.hasBuiltBodyRows).isFalse();
            }
        }
    }

    // The rows one paint hands the tooltip widget, in draw order. The box's placement and its GL pass
    // run only in-engine, so the pinned surface is the row list the paint composes.
    private static List<TooltipRow> captureDrawnRows(SystemCellTooltip tooltip) {
        ArgumentCaptor<List<TooltipRow>> rowsCaptor = ArgumentCaptor.captor();
        try (MockedStatic<CursorTooltipRenderer> rendererMock =
                Mockito.mockStatic(CursorTooltipRenderer.class)) {
            tooltip.renderFor(sectorWithEconomy(), systemNamed());

            rendererMock.verify(() -> CursorTooltipRenderer.render(
                    rowsCaptor.capture(),
                    any(CursorTooltipStyle.class)));
        }
        return rowsCaptor.getValue();
    }

    // A body line stated with its own colour, so the test's rows carry no dependency on which shade a
    // row builder would resolve - what this class does with a body row is the subject, not how one reads.
    private static TooltipRow buildBodyRow(String text) {
        return TooltipRow.createRow(new TextSpan(text, Color.LIGHT_GRAY));
    }

    private static SectorAPI sectorWithEconomy() {
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getEconomy()).thenReturn(mock(EconomyAPI.class));
        return sectorMock;
    }

    private static StarSystemAPI systemNamed() {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getName()).thenReturn(SYSTEM_NAME);
        return systemMock;
    }

    // A layer's tooltip standing in for any concrete one: it contributes the body it was handed and
    // records whether it was asked for it, which is what the economy gate is observed through.
    private static final class SystemCellTooltipFake extends SystemCellTooltip {
        private final List<TooltipRow> bodyRows;
        private boolean hasBuiltBodyRows;

        private SystemCellTooltipFake(List<TooltipRow> bodyRows) {
            this.bodyRows = bodyRows;
        }

        @Override
        protected List<TooltipRow> buildBodyRows(SectorAPI sector, StarSystemAPI system) {
            hasBuiltBodyRows = true;
            return bodyRows;
        }
    }
}
