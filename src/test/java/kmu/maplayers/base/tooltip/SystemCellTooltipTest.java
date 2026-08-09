package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;

import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.render.gl.tooltip.CursorTooltipRenderer;
import kmlib.starsector.ui.render.gl.tooltip.CursorTooltipStyle;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipLabelPlacement;
import kmlib.starsector.ui.widgets.tooltip.TooltipLineStyle;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.starsector.ui.widgets.tooltip.TooltipStyle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.BUTTON_SHORTCUT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.GRAY;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailModeInput.TOGGLE_KEY_NAME;
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

    // What a box taking part in the detail toggle offers the player, in that box's own words. Stated as
    // something no layer says, since what a counterpart holds is the layer's to name and the shape under
    // test only puts it in a sentence.
    private static final String DETAIL_NAME = "the full breakdown";

    // The sizes the atlases were rasterised at, restated here rather than read off the enum: taking the
    // native size is the decision under test, and an expectation reading it from the same value the
    // style resolved it from would hold whatever size the box ended up drawing at.
    private static final double HEADER_FONT_SIZE = 20d;
    private static final double BODY_FONT_SIZE = 15d;

    // The note at the foot is the one line drawn off its atlas's own size: its 12pt rasterisation reads
    // as fine print beside 15pt content, so it takes the body's size and keeps only the narrowness that
    // sets it apart. Stated as its own literal rather than as the body's constant, so a box that stopped
    // matching the body would fail here rather than agree with itself.
    private static final double FOOTNOTE_FONT_SIZE = 15d;

    // How much smaller each step under the box's own voice draws than the step above it, restated rather
    // than read off the class under test: how far the levels are set apart is the decision, and an
    // expectation taking it from the value the box handed over would hold whatever step it asked for.
    private static final float LEVEL_SHRINK = 2f;

    // How far under the box's own voice a line stands - a holder speaking in that voice, what it holds,
    // a term of that, and a tier of that, which is as deep as the boxes go.
    private static final int IN_THE_BOXS_VOICE = 0;
    private static final int ONE_STEP_UNDER = 1;
    private static final int TWO_STEPS_UNDER = 2;
    private static final int THREE_STEPS_UNDER = 3;

    // The sizes those steps land on, as literals rather than as the body size less the step, so the
    // arithmetic is asserted here rather than restated. Every level a box actually reaches clears the
    // widget's own floor, so all four sizes are the step's own reading rather than a floored one.
    private static final double ONE_STEP_UNDER_SIZE = 13d;
    private static final double TWO_STEPS_UNDER_SIZE = 11d;
    private static final double THREE_STEPS_UNDER_SIZE = 9d;

    // A stack deeper than the step can carry, and the size the widget stops it at: further down, no atlas
    // renders legibly and the shrink would arrive at zero and then below it.
    private static final int DEEPER_THAN_THE_FLOOR = 6;
    private static final double SMALLEST_LEVEL_SIZE = 7d;

    // The blocks the box lays out, in draw order: the heading it is titled with, then the layer's own,
    // then the hint at the foot where the box offers one.
    private static final int TITLE_SECTION = 0;
    private static final int FIRST_BODY_SECTION = 1;
    private static final int SECOND_BODY_SECTION = 2;
    private static final int FOOTER_SECTION = 2;

    // The hint stands alone in its block, so it is both that block's only line and its first.
    private static final int FOOTER_ROW = 0;
    private static final int LONE_FOOTER_ROW_COUNT = 1;

    // What a box with one body block and no hint comes to: its heading and that block.
    private static final int BOX_WITH_ONE_BODY_BLOCK_SECTION_COUNT = 2;

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
    class IsOfferingExpansionFor {

        @Test
        void isOfferingExpansionForAnswersFromTheSameReadTheHintIsDrawnFrom() {
            // The key acts exactly where the box says it will. Two reads of "is there more to show"
            // could drift, and the drift would be the cruel kind - a box advertising a key that does
            // nothing - so the offer and the line offering it are asserted off one paint.
            var tooltipFake = new SystemCellTooltipFake(List.of(buildSection("The Hegemony")))
                .offering(DETAIL_NAME, new SystemCellTooltipFake(List.of()));

            assertThat(tooltipFake.isOfferingExpansionFor(
                    buildSectorWithEconomy(),
                    buildNamedSystem()))
                .isTrue();

            assertThat(readRow(
                    captureDrawnBox(tooltipFake).sections(),
                    FOOTER_SECTION,
                    FOOTER_ROW))
                .isNotNull();
        }

        @Test
        void isOfferingExpansionForIsFalseForABoxThatNamesNothingToExpandInto() {
            // The same read answering the other way: no hint is drawn, and the key must not act.
            var tooltipFake = buildTooltipSayingSomething();

            assertThat(tooltipFake.isOfferingExpansionFor(
                    buildSectorWithEconomy(),
                    buildNamedSystem()))
                .isFalse();
                
            assertThat(captureDrawnBox(tooltipFake).sections())
                .hasSize(BOX_WITH_ONE_BODY_BLOCK_SECTION_COUNT);
        }
    }

    @Nested
    class RenderFor {

        @Test
        void renderForTitlesTheBoxWithTheHoveredSystemsName() {

            var sections = captureDrawnBox(buildTooltipSayingSomething()).sections();

            // The name a surface titles a system by rather than vanilla's raw composition, which
            // stutters over a system named after its star - a stutter the box wears at its largest.
            // The hovered system is named "Penelope's Star Star System"; a header taking that
            // straight fails here rather than agreeing with the display read by coincidence.
            //
            // The title says it is a heading and nothing about how one looks - which face that becomes is
            // the box's typography, asserted below.
            assertThat(readRow(sections, TITLE_SECTION, HEADER_ROW))
                .isEqualTo(TooltipRow
                    .createCentredRow(new TextSpan("Penelope's Star System", HIGHLIGHT))
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
        void renderForAsksForAStepPerLevelUnderTheBoxsOwnVoice() {
            // A breakdown several levels deep is hard to read at one size however far it is indented, so
            // the box asks the widget for a second cue agreeing with the indent. Asked for on the shape
            // every layer shares, so no test of one layer's box has to pin it again.
            var typography = captureDrawnBox(buildTooltipSayingSomething())
                .style()
                .typography();

            assertThat(typography.levelShrink())
                .isEqualTo(LEVEL_SHRINK);
        }

        @Test
        void renderForDrawsEachStepUnderTheBoxsOwnVoiceAtTheSizeTheStepResolvesTo() {
            // Where the levels actually land, which is what a reader sees: the step is only worth asking
            // for if neighbouring levels stay comfortably legible while still telling apart at a glance.
            var typography = captureDrawnBox(buildTooltipSayingSomething())
                .style()
                .typography();

            assertThat(resolveBodySizeAt(typography, IN_THE_BOXS_VOICE))
                .isEqualTo(BODY_FONT_SIZE);
            assertThat(resolveBodySizeAt(typography, ONE_STEP_UNDER))
                .isEqualTo(ONE_STEP_UNDER_SIZE);
            assertThat(resolveBodySizeAt(typography, TWO_STEPS_UNDER))
                .isEqualTo(TWO_STEPS_UNDER_SIZE);
            assertThat(resolveBodySizeAt(typography, THREE_STEPS_UNDER))
                .isEqualTo(THREE_STEPS_UNDER_SIZE);
        }

        @Test
        void renderForStopsShrinkingAtTheSmallestLegibleSize() {
            // A listing is as deep as its subject matter, so nothing about the box bounds how far under
            // its voice a line can stand - the deepest levels share the floor rather than shrinking away.
            var typography = captureDrawnBox(buildTooltipSayingSomething())
                .style()
                .typography();

            assertThat(resolveBodySizeAt(typography, DEEPER_THAN_THE_FLOOR))
                .isEqualTo(SMALLEST_LEVEL_SIZE);
        }

        @Test
        void renderForHeadsTheBoxWithTheNameAloneAsItsOwnBlock() {
            // The parting under the heading is what a block buys: the name is not put in with the
            // layer's first block, so the gap beneath it is the box's own rather than a gap inside a
            // block the layer composed.
            var sections = captureDrawnBox(buildTooltipSayingSomething()).sections();

            assertThat(sections.get(TITLE_SECTION).readRowsInOrder())
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

            assertThat(titleSection.readRowsInOrder())
                .hasSize(HEADED_TITLE_ROW_COUNT);
            assertThat(titleSection.readRowsInOrder().get(TITLE_ROW))
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
            assertThat(sections.get(TITLE_SECTION).readRowsInOrder())
                .hasSize(HEADED_TITLE_ROW_COUNT);
            assertThat(sections.get(TITLE_SECTION).readRowsInOrder().get(TITLE_ROW))
                .isEqualTo(titleRow);
        }

        @Test
        void renderForEndsABoxOfferingDetailWithTheKeyThatShowsIt() {
            // What the hint has to say to be worth a line: which key, and what the player would gain -
            // the key picked out and the words about it quiet, which is how the game states its own.
            var tooltipFake = new SystemCellTooltipFake(List.of(buildSection("The Hegemony")))
                .offering(DETAIL_NAME, new SystemCellTooltipFake(List.of()));

            var sections = captureDrawnBox(tooltipFake).sections();

            assertThat(readRow(sections, FOOTER_SECTION, FOOTER_ROW).labelRuns())
                .containsExactly(
                    new TextSpan(TOGGLE_KEY_NAME, BUTTON_SHORTCUT),
                    new TextSpan("show the full breakdown", GRAY));
        }

        @Test
        void renderForEndsTheDetailedBoxWithTheKeyThatHidesItAgain() {
            // The counterpart the framework draws in the plain box's place has no counterpart of its
            // own, which is exactly what tells it it is the detailed one - so the offer reverses without
            // either box being told which mode is in force.
            var tooltipFake = new SystemCellTooltipFake(List.of(buildSection("The Hegemony")))
                .offering(DETAIL_NAME, null);

            var sections = captureDrawnBox(tooltipFake).sections();

            assertThat(readRow(sections, FOOTER_SECTION, FOOTER_ROW).labelRuns())
                .containsExactly(
                    new TextSpan(TOGGLE_KEY_NAME, BUTTON_SHORTCUT),
                    new TextSpan("hide the full breakdown", GRAY));
        }

        @Test
        void renderForGivesTheHintABlockOfItsOwnReadingAsAFootnote() {
            // A line about the box rather than about the system: set off by the box's own parting so it
            // is not read as the last entry of the block above, and marked as the kind of line it is so
            // the typography can set it apart from the content.
            var tooltipFake = new SystemCellTooltipFake(List.of(buildSection("The Hegemony")))
                .offering(DETAIL_NAME, new SystemCellTooltipFake(List.of()));

            var sections = captureDrawnBox(tooltipFake).sections();

            assertThat(sections.get(FOOTER_SECTION).readRowsInOrder())
                .hasSize(LONE_FOOTER_ROW_COUNT);
            assertThat(readRow(sections, FOOTER_SECTION, FOOTER_ROW).lineStyle())
                .isEqualTo(TooltipLineStyle.FOOTNOTE);
        }

        @Test
        void renderForOpensTheHintAtTheBoxsContentEdge() {
            // The crest gutter is one column measured across the whole box, and the boxes that carry
            // this hint are full of crested lines - so a hint left aligned to that column would open
            // behind a gutter it can never fill, reading as indented under the content it is not part
            // of. It is a line about the box, and starts where the box does.
            var tooltipFake = new SystemCellTooltipFake(List.of(buildSection("The Hegemony")))
                .offering(DETAIL_NAME, new SystemCellTooltipFake(List.of()));

            var footerRow = (TooltipRow.TableRow) readRow(
                captureDrawnBox(tooltipFake).sections(),
                FOOTER_SECTION,
                FOOTER_ROW);

            assertThat(footerRow.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
        }

        @Test
        void renderForDrawsFootnotesInTheGamesOwnKeyHintFace() {
            // The face vanilla ends its own boxes in, at the body's size rather than its atlas's own:
            // the narrowness is what sets a line about the box apart from the box's findings, and drawn
            // at 12 beside 15pt content it read as fine print instead.
            var typography = captureDrawnBox(buildTooltipSayingSomething())
                .style()
                .typography();

            assertThat(typography.footnoteStyle().face())
                .isEqualTo(new TextFace(
                    StarsectorFont.VANILLA_ORBITRON_12_CONDENSED,
                    FOOTNOTE_FONT_SIZE));
        }

        @Test
        void renderForEndsABoxOfferingNoDetailWithItsContent() {
            // The ordinary box takes no part in the toggle, so it ends where its content does rather
            // than on a line offering a counterpart that does not exist.
            var sections = captureDrawnBox(buildTooltipSayingSomething()).sections();

            assertThat(sections)
                .hasSize(BOX_WITH_ONE_BODY_BLOCK_SECTION_COUNT);
        }

        @Test
        void renderForDrawsNothingForABoxOfferingDetailAndNothingToSay() {
            // The hint is about the box rather than about the system, so it cannot be the thing that
            // makes a box worth drawing - a lone offer to expand into nothing says less than no box.
            var tooltipFake = new SystemCellTooltipFake(List.of())
                .offering(DETAIL_NAME, new SystemCellTooltipFake(List.of()));

            try (var rendererMock = Mockito.mockStatic(CursorTooltipRenderer.class)) {

                tooltipFake.renderFor(buildSectorWithEconomy(), buildNamedSystem());
                rendererMock.verifyNoInteractions();
            }
        }

        @Test
        void renderForDrawsNothingForABodyWithNothingToSay() {
            // A lone system name only repeats what the cursor already sits on, so an empty body is no
            // box rather than a titled empty one.
            var tooltipFake = new SystemCellTooltipFake(List.of());

            try (var rendererMock = Mockito.mockStatic(CursorTooltipRenderer.class)) {

                tooltipFake.renderFor(buildSectorWithEconomy(), buildNamedSystem());
                rendererMock.verifyNoInteractions();
            }
        }

        @Test
        void renderForDrawsNothingWithoutALiveEconomy() {
            // Bodies read the economy for what a layer holds in the system, so a sector without one is
            // not asked for a body at all.
            var tooltipFake = buildTooltipSayingSomething();

            try (var rendererMock = Mockito.mockStatic(CursorTooltipRenderer.class)) {

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

        try (var rendererMock = Mockito.mockStatic(CursorTooltipRenderer.class)) {

            tooltip.renderFor(buildSectorWithEconomy(), buildNamedSystem());

            rendererMock.verify(
                () -> CursorTooltipRenderer.render(
                    sectionsCaptor.capture(),
                    styleCaptor.capture()));
        }
        return new DrawnBox(sectionsCaptor.getValue(), styleCaptor.getValue());
    }

    // The size a line of the box's body draws at when it stands the given number of steps under the box's
    // own voice - the one lookup a renderer makes per row, read off the look the paint was handed rather
    // than off any row, since the sizing is the box's decision and not any one line's.
    private static double resolveBodySizeAt(TooltipStyle typography, int subordinationLevel) {
        return typography
            .resolveStyleFor(TooltipLineStyle.PARAGRAPH, subordinationLevel)
            .face()
            .size();
    }

    // One line of the drawn box, named by the block it sits in and its place inside that block - the two
    // coordinates a line now has, since a box is a stack of blocks rather than a flat run of lines.
    private static TooltipRow readRow(
            List<TooltipSection> sections,
            int sectionIndex,
            int rowIndex) {

        return sections
            .get(sectionIndex)
            .readRowsInOrder()
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
        return TooltipSection.createSection(List.of(buildRow(text)));
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

        // The two names vanilla answers for one system: the composed one every surface reads, and the
        // base name under it. A system named after its star, so the pair carry the stutter the box's
        // title is meant to drop.
        when(systemMock.getName())
            .thenReturn("Penelope's Star Star System");
        when(systemMock.getNameWithNoType())
            .thenReturn("Penelope's Star");

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

        // What this box offers the player beyond itself, if anything: the words for it, and the
        // counterpart holding it - which a box that IS the counterpart names without holding one, since
        // that is precisely the state the shape under test reads the direction of the offer from.
        private String detailName;
        private MapHoverTooltip expandedVariant;

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

        @Override
        protected Optional<String> resolveExpandedDetailName(
                SectorAPI sector,
                StarSystemAPI system) {

            return Optional.ofNullable(detailName);
        }

        @Override
        public Optional<MapHoverTooltip> resolveExpandedVariant() {
            return Optional.ofNullable(expandedVariant);
        }

        // Puts this box in the detail toggle: what it offers, and the counterpart holding it - null for
        // the box that is itself the counterpart, which has nothing further to offer.
        private SystemCellTooltipFake offering(String detailName, MapHoverTooltip expandedVariant) {
            this.detailName = detailName;
            this.expandedVariant = expandedVariant;
            return this;
        }
    }
}
