package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;

import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.render.gl.CursorTooltipRenderer;
import kmlib.starsector.ui.render.gl.CursorTooltipStyle;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipLineStyle;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.List;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the shape this class settles for every layer that extends it - the part a layer has no say in: the
 * hovered system is named above whatever the layer says, that name reads as the box's heading and is drawn
 * in the game's heading face, any title lines are read together with the name as one block, the layer's
 * own blocks follow beneath it exactly as it composed them, and a layer with nothing to say draws no box
 * at all rather than one echoing the cursor. Also that a sector with no live economy is not read, since
 * content assumes one.
 *
 * <p>How far apart the blocks then stand is the widget's, pinned there: what is fixed here is only that
 * the heading is one block and the layer's content is its own, which is what that spacing follows from.
 */
final class SystemCellTooltipTest {

    private static final String SYSTEM_NAME = "Corvus";

    // The sizes the two atlases were rasterised at, restated here rather than read off the enum: taking
    // the native size is the decision under test, and an expectation reading it from the same value the
    // style resolved it from would hold whatever size the box ended up drawing at.
    private static final double HEADER_FONT_SIZE = 20d;
    private static final double BODY_FONT_SIZE = 15d;

    // The blocks the box lays out, in draw order: the heading it is titled with, then the layer's own.
    private static final int TITLE_SECTION = 0;
    private static final int FIRST_BODY_SECTION = 1;
    private static final int SECOND_BODY_SECTION = 2;

    // Where the lines sit inside the heading block: the system name, then any line the layer heads its
    // box with read on from it.
    private static final int HEADER_ROW = 0;
    private static final int TITLE_ROW = 1;

    // The whole of a heading block naming the system and carrying one title line.
    private static final int HEADED_TITLE_ROW_COUNT = 2;

    // A box whose heading is the system name alone, which is the ordinary case.
    private static final int BARE_TITLE_ROW_COUNT = 1;

    @BeforeEach
    void installColours() {
        CellTooltipPaletteFake.installPalette();
    }

    @AfterEach
    void clearColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class RenderFor {

        @Test
        void renderForTitlesTheBoxWithTheHoveredSystemsName() {

            var sections = captureDrawnBox(buildTooltipSayingSomething()).sections();

            // The title says it is a heading and nothing about how one looks - which face that becomes is
            // the box's typography, asserted below.
            assertThat(readRow(sections, TITLE_SECTION, HEADER_ROW))
                .isEqualTo(TooltipRow
                    .createCentredRow(new TextSpan(SYSTEM_NAME, HIGHLIGHT))
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
        void renderForHeadsTheBoxWithTheNameAloneAsItsOwnBlock() {
            // The parting under the heading is what a block buys: the name is not put in with the
            // layer's first block, so the gap beneath it is the box's own rather than a gap inside a
            // block the layer composed.
            var sections = captureDrawnBox(buildTooltipSayingSomething()).sections();

            assertThat(sections.get(TITLE_SECTION).rows())
                .hasSize(BARE_TITLE_ROW_COUNT);
        }

        @Test
        void renderForKeepsTheLayersOwnBlocksAsItComposedThem() {
            // What a layer groups together is the layer's statement about its own content, so the shared
            // shape adds a block above it and regroups nothing.
            var firstSection = buildSection("The Hegemony");
            var secondSection = buildSection("Independent");
            var tooltipFake = new SystemCellTooltipFake(List.of(firstSection, secondSection));
            var sections = captureDrawnBox(tooltipFake).sections();

            assertThat(sections.get(FIRST_BODY_SECTION))
                .isEqualTo(firstSection);
            assertThat(sections.get(SECOND_BODY_SECTION))
                .isEqualTo(secondSection);
        }

        @Test
        void renderForReadsATitleLineTogetherWithTheName() {
            // A title line continues the heading, so it sits in the heading's own block - which is the
            // whole difference between heading the box with a line and opening the body with one.
            var titleRow = buildRow("The Hegemony");
            var tooltipFake = new SystemCellTooltipFake(
                List.of(titleRow),
                List.of(buildSection("Unpopulated")));

            var titleSection = captureDrawnBox(tooltipFake).sections().get(TITLE_SECTION);

            assertThat(titleSection.rows())
                .hasSize(HEADED_TITLE_ROW_COUNT);
            assertThat(titleSection.rows().get(TITLE_ROW))
                .isEqualTo(titleRow);
        }

        @Test
        void renderForPartsTheBodyFromTheTitleLinesAboveIt() {
            // The box's one parting falls under the whole heading block rather than at a fixed line, so
            // a line added to the heading joins it instead of being cut off above the break.
            var bodySection = buildSection("Unpopulated");
            var tooltipFake = new SystemCellTooltipFake(
                List.of(buildRow("The Hegemony")),
                List.of(bodySection));

            assertThat(captureDrawnBox(tooltipFake).sections().get(FIRST_BODY_SECTION))
                .isEqualTo(bodySection);
        }

        @Test
        void renderForDrawsATitleLineWithNoBodyUnderIt() {
            // A heading line is content in its own right, so a layer with one and nothing else still
            // draws - as the one block it has.
            var titleRow = buildRow("The Hegemony");
            var tooltipFake = new SystemCellTooltipFake(List.of(titleRow), List.of());
            var sections = captureDrawnBox(tooltipFake).sections();

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(TITLE_SECTION).rows())
                .hasSize(HEADED_TITLE_ROW_COUNT);
            assertThat(sections.get(TITLE_SECTION).rows().get(TITLE_ROW))
                .isEqualTo(titleRow);
        }

        @Test
        void renderForDrawsNothingForABodyWithNothingToSay() {
            // A lone system name only repeats what the cursor already sits on, so an empty body is no
            // box rather than a titled empty one.
            var tooltipFake = new SystemCellTooltipFake(List.of());

            try (MockedStatic<CursorTooltipRenderer> rendererMock =
                    Mockito.mockStatic(CursorTooltipRenderer.class)) {

                tooltipFake.renderFor(buildSectorWithEconomy(), buildNamedSystem());
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

                tooltipFake.renderFor(mock(SectorAPI.class), buildNamedSystem());
                rendererMock.verifyNoInteractions();

                assertThat(tooltipFake.hasBuiltBodySections)
                    .isFalse();
            }
        }
    }

    // What one paint hands the tooltip widget. The box's placement and its GL pass run only in-engine, so
    // the pinned surface is the render call's own two arguments.
    private static DrawnBox captureDrawnBox(SystemCellTooltip tooltip) {

        ArgumentCaptor<List<TooltipSection>> sectionsCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<CursorTooltipStyle> styleCaptor = ArgumentCaptor.captor();

        try (MockedStatic<CursorTooltipRenderer> rendererMock =
                Mockito.mockStatic(CursorTooltipRenderer.class)) {

            tooltip.renderFor(buildSectorWithEconomy(), buildNamedSystem());

            rendererMock.verify(
                () -> CursorTooltipRenderer.render(
                    sectionsCaptor.capture(),
                    styleCaptor.capture()));
        }
        return new DrawnBox(sectionsCaptor.getValue(), styleCaptor.getValue());
    }

    // One line of the drawn box, named by the block it sits in and its place inside that block - the two
    // coordinates a line now has, since a box is a stack of blocks rather than a flat run of lines.
    private static TooltipRow readRow(
            List<TooltipSection> sections,
            int sectionIndex,
            int rowIndex) {

        return sections
            .get(sectionIndex)
            .rows()
            .get(rowIndex);
    }

    // A layer with something - anything - to say, for the tests that turn on the box being drawn at all
    // rather than on what its body holds. Which line the body carries is this class's business only where
    // a test names its rows, so the ones that do not are spared inventing one.
    private static SystemCellTooltipFake buildTooltipSayingSomething() {
        return new SystemCellTooltipFake(List.of(buildSection("The Hegemony")));
    }

    // A one-line block, which is all most cases here need: what a layer groups is its own business, and
    // these cases are about what the shared shape does with the blocks rather than what fills them.
    private static TooltipSection buildSection(String text) {
        return new TooltipSection(List.of(buildRow(text)));
    }

    // A line stated with its own colour, so the test's rows carry no dependency on which shade a row
    // builder would resolve - what this class does with a line is the subject, not how one reads.
    private static TooltipRow buildRow(String text) {
        return TooltipRow.createRow(new TextSpan(text, Color.LIGHT_GRAY));
    }

    private static SectorAPI buildSectorWithEconomy() {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getEconomy())
            .thenReturn(mock(EconomyAPI.class));

        return sectorMock;
    }

    private static StarSystemAPI buildNamedSystem() {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getName())
            .thenReturn(SYSTEM_NAME);

        return systemMock;
    }

    // One paint's two halves - the blocks in draw order and the look they are drawn in - kept together
    // because they come off a single render call, so a test reading either is reading the same paint.
    private record DrawnBox(
        List<TooltipSection> sections,
        CursorTooltipStyle style) {
    }

    // A layer's tooltip standing in for any concrete one: it contributes the heading lines and the blocks
    // it was handed, and records whether it was asked for the body, which is what the economy gate is
    // observed through.
    private static final class SystemCellTooltipFake extends SystemCellTooltip {

        private final List<TooltipRow> titleRows;
        private final List<TooltipSection> bodySections;
        private boolean hasBuiltBodySections;

        // A layer heading its box with nothing, which is the ordinary case and the one most cases here
        // are about - so only a case actually about the heading block names one.
        private SystemCellTooltipFake(List<TooltipSection> bodySections) {
            this(List.of(), bodySections);
        }

        private SystemCellTooltipFake(
                List<TooltipRow> titleRows,
                List<TooltipSection> bodySections) {

            this.titleRows = titleRows;
            this.bodySections = bodySections;
        }

        @Override
        protected List<TooltipRow> buildTitleRows(SectorAPI sector, StarSystemAPI system) {
            return titleRows;
        }

        @Override
        protected List<TooltipSection> buildBodySections(SectorAPI sector, StarSystemAPI system) {
            hasBuiltBodySections = true;
            return bodySections;
        }
    }
}
