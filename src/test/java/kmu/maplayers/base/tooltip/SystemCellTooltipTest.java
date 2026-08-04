package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.render.gl.CursorTooltipRenderer;
import kmlib.starsector.ui.render.gl.CursorTooltipStyle;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.TooltipLineStyle;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the shape this class settles for every layer that extends it - the part a layer has no say in: the
 * hovered system is named above whatever the layer says, that name reads as the box's heading and is drawn
 * in the game's heading face, any title lines follow the name unparted, the body's own opening line is
 * parted from the heading above it wherever that heading ended, and a layer with nothing to say draws no
 * box at all rather than one echoing the cursor. Also that a sector with no live economy is not read,
 * since content assumes one.
 */
final class SystemCellTooltipTest {
    
    private static final Color GOLD = new Color(255, 200, 100);
    private static final Color PLAYER_BASE = new Color(100, 160, 200);
    private static final String SYSTEM_NAME = "Corvus";

    // The sizes the two atlases were rasterised at, restated here rather than read off the enum: taking
    // the native size is the decision under test, and an expectation reading it from the same value the
    // style resolved it from would hold whatever size the box ended up drawing at.
    private static final double HEADER_FONT_SIZE = 20d;
    private static final double BODY_FONT_SIZE = 15d;

    // The rows the box lays out, in draw order: the system name it is titled with, then the body's own
    // lines under it.
    private static final int HEADER_ROW = 0;
    private static final int FIRST_BODY_ROW = 1;
    private static final int SECOND_BODY_ROW = 2;

    // Where the lines sit once a title line heads the box: the name, that title line, then the body
    // beneath the parting.
    private static final int TITLE_ROW = 1;
    private static final int TITLED_FIRST_BODY_ROW = 2;

    // The whole of a box headed by a name and one title line, with no body under it.
    private static final int HEADED_BOX_ROW_COUNT = 2;

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void installColours() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();

        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getHighlightColor)
            .thenReturn(GOLD);
        miscMock
            .when(Misc::getBasePlayerColor)
            .thenReturn(PLAYER_BASE);

        // The box's typography carries a default text colour of its own, resolved when the style is
        // built for the paint - so it is stubbed even though no assertion here reads it.
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(Color.LIGHT_GRAY);
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

            var rows = captureDrawnBox(buildTooltipSayingSomething()).rows();

            // The title says it is a heading and nothing about how one looks - which face that becomes is
            // the box's typography, asserted below.
            assertThat(rows.get(HEADER_ROW))
                .isEqualTo(TooltipRow
                    .createCentredRow(new TextSpan(SYSTEM_NAME, GOLD))
                    .readsAs(TooltipLineStyle.HEADER));
        }

        @Test
        void renderForDrawsHeadingsInTheGamesTitleFaceOverBodyLines() {
            // The box's one typographic decision, and the reason a heading is a kind of line at all: the
            // title takes vanilla's title face while the body stays on its paragraph face, each at the
            // size its own atlas is crisp at rather than at a size the box picked.
            var typography = captureDrawnBox(buildTooltipSayingSomething())
                .style()
                .typography();

            assertThat(typography.headerStyle().face())
                .isEqualTo(new TextFace(StarsectorFont.VANILLA_ORBITRON_20AA, HEADER_FONT_SIZE));
            assertThat(typography.paragraphStyle().face())
                .isEqualTo(new TextFace(StarsectorFont.VANILLA_INSIGNIA_15, BODY_FONT_SIZE));
        }

        @Test
        void renderForPartsTheBodysOpeningLineFromTheTitle() {
            // The parting is the shared shape's decision, not a body's: every cell tooltip is a title
            // over a body, so a layer cannot forget it and cannot double it on later lines.
            var openingRow = buildRow("The Hegemony");
            var followingRow = buildRow("Independent");
            var tooltipFake = new SystemCellTooltipFake(List.of(openingRow, followingRow));
            var rows = captureDrawnBox(tooltipFake).rows();

            assertThat(rows.get(FIRST_BODY_ROW))
                .isEqualTo(openingRow.opensSection());
            assertThat(rows.get(SECOND_BODY_ROW))
                .isEqualTo(followingRow);
        }

        @Test
        void renderForFollowsTheNameWithATitleLineUnparted() {
            // A title line continues the heading, so nothing parts it from the name - which is the whole
            // difference between heading the box with a line and opening the body with one.
            var titleRow = buildRow("The Hegemony");
            var tooltipFake = new SystemCellTooltipFake(
                List.of(titleRow),
                List.of(buildRow("Unpopulated")));

            assertThat(captureDrawnBox(tooltipFake).rows().get(TITLE_ROW))
                .isEqualTo(titleRow);
        }

        @Test
        void renderForPartsTheBodyFromTheTitleLinesAboveIt() {
            // The box's one parting falls under the whole heading block rather than at a fixed row, so a
            // line added to the heading pushes the break down with it instead of being cut off above it.
            var bodyRow = buildRow("Unpopulated");
            var tooltipFake = new SystemCellTooltipFake(
                List.of(buildRow("The Hegemony")),
                List.of(bodyRow));

            assertThat(captureDrawnBox(tooltipFake).rows().get(TITLED_FIRST_BODY_ROW))
                .isEqualTo(bodyRow.opensSection());
        }

        @Test
        void renderForDrawsATitleLineWithNoBodyUnderIt() {
            // A heading line is content in its own right, so a layer with one and nothing else still
            // draws - and with no body there is nothing for the parting to fall on.
            var titleRow = buildRow("The Hegemony");
            var tooltipFake = new SystemCellTooltipFake(List.of(titleRow), List.of());

            var rows = captureDrawnBox(tooltipFake).rows();

            assertThat(rows).hasSize(HEADED_BOX_ROW_COUNT);
            assertThat(rows.get(TITLE_ROW)).isEqualTo(titleRow);
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
            var tooltipFake = buildTooltipSayingSomething();

            try (MockedStatic<CursorTooltipRenderer> rendererMock =
                    Mockito.mockStatic(CursorTooltipRenderer.class)) {

                tooltipFake.renderFor(mock(SectorAPI.class), systemNamed());
                rendererMock.verifyNoInteractions();

                assertThat(tooltipFake.hasBuiltBodyRows)
                    .isFalse();
            }
        }
    }

    // What one paint hands the tooltip widget. The box's placement and its GL pass run only in-engine, so
    // the pinned surface is the render call's own two arguments.
    private static DrawnBox captureDrawnBox(SystemCellTooltip tooltip) {

        ArgumentCaptor<List<TooltipRow>> rowsCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<CursorTooltipStyle> styleCaptor = ArgumentCaptor.captor();

        try (MockedStatic<CursorTooltipRenderer> rendererMock =
                Mockito.mockStatic(CursorTooltipRenderer.class)) {

            tooltip.renderFor(sectorWithEconomy(), systemNamed());

            rendererMock.verify(
                () -> CursorTooltipRenderer.render(
                    rowsCaptor.capture(),
                    styleCaptor.capture()));
        }
        return new DrawnBox(rowsCaptor.getValue(), styleCaptor.getValue());
    }

    // A layer with something - anything - to say, for the tests that turn on the box being drawn at all
    // rather than on what its body holds. Which line the body carries is this class's business only where
    // a test names its rows, so the ones that do not are spared inventing one.
    private static SystemCellTooltipFake buildTooltipSayingSomething() {
        return new SystemCellTooltipFake(List.of(buildRow("The Hegemony")));
    }

    // A line stated with its own colour, so the test's rows carry no dependency on which shade a row
    // builder would resolve - what this class does with a line is the subject, not how one reads.
    private static TooltipRow buildRow(String text) {
        return TooltipRow.createRow(new TextSpan(text, Color.LIGHT_GRAY));
    }

    private static SectorAPI sectorWithEconomy() {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getEconomy())
            .thenReturn(mock(EconomyAPI.class));

        return sectorMock;
    }

    private static StarSystemAPI systemNamed() {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getName())
            .thenReturn(SYSTEM_NAME);

        return systemMock;
    }

    // One paint's two halves - the lines in draw order and the look they are drawn in - kept together
    // because they come off a single render call, so a test reading either is reading the same paint.
    private record DrawnBox(
        List<TooltipRow> rows,
        CursorTooltipStyle style) {
    }

    // A layer's tooltip standing in for any concrete one: it contributes the two blocks it was handed
    // and records whether it was asked for the body, which is what the economy gate is observed through.
    private static final class SystemCellTooltipFake extends SystemCellTooltip {
        
        private final List<TooltipRow> titleRows;
        private final List<TooltipRow> bodyRows;
        private boolean hasBuiltBodyRows;

        // A layer heading its box with nothing, which is the ordinary case and the one most cases here
        // are about - so only a case actually about the heading block names one.
        private SystemCellTooltipFake(List<TooltipRow> bodyRows) {
            this(List.of(), bodyRows);
        }

        private SystemCellTooltipFake(List<TooltipRow> titleRows, List<TooltipRow> bodyRows) {
            this.titleRows = titleRows;
            this.bodyRows = bodyRows;
        }

        @Override
        protected List<TooltipRow> buildTitleRows(SectorAPI sector, StarSystemAPI system) {
            return titleRows;
        }

        @Override
        protected List<TooltipRow> buildBodyRows(SectorAPI sector, StarSystemAPI system) {
            hasBuiltBodyRows = true;
            return bodyRows;
        }
    }
}
