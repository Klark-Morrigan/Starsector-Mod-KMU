package kmu.maplayers.base.tooltip.layout;

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

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.BUTTON_SHORTCUT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.GRAY;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevelInput.CYCLE_KEY_NAME;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.PATROL_DETAILS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
 *
 * <p>Also that the density knobs land where they are named: the step each level draws smaller by, the
 * box's own line gap, and the two depths a listing runs long at. Each is one number handed to the
 * widget, so a knob wired to the wrong one is invisible on screen until a box happens to list something
 * deep enough to show it.
 */
final class SystemCellTooltipTest {

    // What the hint says at each level, as the phrases the player reads rather than as the keys behind
    // them: the line has to name the step the next press takes, and a case reading the key instead
    // would pass over a level wired to another level's wording.
    private static final String EXPAND_SYSTEM_COMPOSITION = "expand system composition";
    private static final String EXPAND_MARKET_STATS = "expand market stats";
    private static final String EXPAND_PATROL_DETAILS = "expand patrol details";
    private static final String COLLAPSE_TO_FACTIONS = "collapse to factions";

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

    // The density the player is standing in for here: how much smaller each step under the box's own
    // voice draws, and the three gaps the box stacks its lines at. Stated as this suite's own numbers
    // rather than as the shipped defaults, since what is under test is that each knob reaches the part
    // of the box it names - which a case reading the real default could not tell from a wire crossed
    // between two knobs that happen to ship the same value.
    private static final float NESTING_LEVEL_SHRINK = 2f;
    private static final float LINE_GAP = 6f;
    private static final float TIER_2_LINE_GAP = 3f;
    private static final float TIER_3_LINE_GAP = 1f;

    // The line the box runs from a label across to its value, at the weights this suite stands in for the
    // player with. Stated as its own numbers for the same reason the gaps above are, and both unlike the
    // shipped pair, so a box carrying them says the two knobs reached it rather than that the widget's
    // own default happened to match.
    private static final float LEADER_THICKNESS = 2f;
    private static final float LEADER_OPACITY = 0.4f;

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

    // A line deeper than either knob names. No box lists this deep today, which is exactly why it is
    // asserted: the depth a listing reaches follows its subject matter, so the first box to go a step
    // further must not respace itself.
    private static final int DEEPER_THAN_EITHER_TIER = 5;

    // A screen no box these cases compose could overflow, in UI units - well past any real one, so a
    // case that is not about the fit is posed over a box drawn exactly as it was composed.
    private static final float ROOM_FOR_ANY_BOX = 100000f;

    // A screen the listing below cannot be drawn whole on however far it is compressed, which is what
    // puts the box in the one state that gives content up.
    private static final float ROOM_FOR_ALMOST_NOTHING = 80f;

    // What a listed thing in those cases is counted in, and the heading it is listed under. Neither is
    // read by any assertion - what the cases turn on is how many of them the box had room for.
    private static final int LISTED_ENTRY_VALUE = 100;
    private static final String LISTED_BLOCK_HEADING = "Contested by:";

    // A second reading of one knob, for the case that a box is set from the live settings each paint
    // rather than from a look settled once. Tighter than the gap installed above so the two readings
    // cannot be told apart by luck.
    private static final float TIGHTENED_LINE_GAP = 2f;

    // The same second reading for the leader knobs, faded well under the opacity installed above for the
    // same reason.
    private static final float FADED_LEADER_OPACITY = 0.1f;

    // Where the hint sits in a box whose body is one listed block - the heading, that block, then the
    // line at the foot. Its own name because the boxes about the fit list rather than banner, so their
    // body is one block whatever it lists.
    private static final int CUT_BOX_FOOTER_SECTION = 2;

    // Where the figure for withheld content sits among the hint's runs: after the key and the words
    // about it, since it speaks about the box rather than about the press.
    private static final int FOOTER_WITHHELD_RUN = 2;

    // The blocks the box lays out, in draw order: the heading it is titled with, then the layer's own,
    // then the hint at the foot where the box offers one.
    private static final int TITLE_SECTION = 0;
    private static final int FIRST_BODY_SECTION = 1;
    private static final int SECOND_BODY_SECTION = 2;
    private static final int FOOTER_SECTION = 2;

    // The hint stands alone in its block, so it is both that block's only line and its first.
    private static final int FOOTER_ROW = 0;
    private static final int LONE_FOOTER_ROW_COUNT = 1;

    // The hint is drawn in two runs - the key picked out, then the words about it - so a case about
    // what the offer says reads the second and leaves the shortcut to the case about the look.
    private static final int FOOTER_PHRASE_RUN = 1;

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

    // The density knobs the box reads each paint. Held over every case rather than opened per case,
    // because the box resolves them whenever it draws at all - including in the cases that assert it
    // draws nothing, which would otherwise read the live settings from outside the game.
    private MockedStatic<KmuMapLayerSettings> settingsMock;

    @BeforeEach
    void installColours() {
        CellTooltipPaletteFake.installPalette();
    }

    @BeforeEach
    void installDensitySettings() {

        settingsMock = mockStatic(KmuMapLayerSettings.class);

        settingsMock.when(KmuMapLayerSettings::getMapTooltipNestingLevelShrink)
            .thenReturn(NESTING_LEVEL_SHRINK);
        settingsMock.when(KmuMapLayerSettings::getMapTooltipLineGap)
            .thenReturn(LINE_GAP);
        settingsMock.when(KmuMapLayerSettings::getMapTooltipTier2LineGap)
            .thenReturn(TIER_2_LINE_GAP);
        settingsMock.when(KmuMapLayerSettings::getMapTooltipTier3LineGap)
            .thenReturn(TIER_3_LINE_GAP);
        settingsMock.when(KmuMapLayerSettings::getMapTooltipLeaderThickness)
            .thenReturn(LEADER_THICKNESS);
        settingsMock.when(KmuMapLayerSettings::getMapTooltipLeaderOpacity)
            .thenReturn(LEADER_OPACITY);
    }

    @AfterEach
    void clearColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @AfterEach
    void clearDensitySettings() {
        settingsMock.close();
    }

    @Nested
    class ResolveNextLevelFor {

        @Test
        void resolveNextLevelForAgreesWithTheHintTheBoxDraws() {
            // The key acts exactly where the box says it will. The two reach the answer by different
            // routes - the press asks the box, the paint takes what the composition found - so what
            // has to hold is that they agree, and the drift would be the cruel kind: a box
            // advertising a key that does nothing. Asserted over one box, both ways at once.
            var tooltipFake = buildTooltipOfferingDetail();

            assertThat(tooltipFake.resolveNextLevelFor(
                    buildSectorWithEconomy(),
                    buildNamedSystem(),
                    HoverTooltipDetailLevel.FACTIONS))
                .contains(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);

            assertThat(readRow(
                    captureDrawnBox(tooltipFake).sections(),
                    FOOTER_SECTION,
                    FOOTER_ROW))
                .isNotNull();
        }

        @Test
        void resolveNextLevelForIsEmptyForABoxWithNothingDeeperToState() {
            // The same agreement the other way: no hint is drawn, and the key must not act.
            var tooltipFake = buildTooltipSayingSomething();

            assertThat(tooltipFake.resolveNextLevelFor(
                    buildSectorWithEconomy(),
                    buildNamedSystem(),
                    HoverTooltipDetailLevel.FACTIONS))
                .isEmpty();

            assertThat(captureDrawnBox(tooltipFake).sections())
                .hasSize(BOX_WITH_ONE_BODY_BLOCK_SECTION_COUNT);
        }

        @Test
        void resolveNextLevelForCollapsesAtTheDeepestLevelTheBoxItselfHolds() {
            // The wrap is the box's own rather than the cycle's last constant. A box whose account
            // ends at the market stats - a claim, which no patrol enters - would otherwise be offered
            // a patrol tier that redraws exactly what is on screen, and the player would press through
            // it to reach the collapse.
            var tooltipFake = buildTooltipOfferingDetailDownTo(HoverTooltipDetailLevel.MARKET_STATS);

            assertThat(tooltipFake.resolveNextLevelFor(
                    buildSectorWithEconomy(),
                    buildNamedSystem(),
                    HoverTooltipDetailLevel.MARKET_STATS))
                .contains(HoverTooltipDetailLevel.FACTIONS);
        }

        @Test
        void resolveNextLevelForClaimsTheKeyAtTheDeepestLevelWhateverTheBoxHolds() {
            // The cycle wraps, so the press at the deepest level the levels declare collapses the box
            // - which acts over any system at all, including one this box has nothing deeper to say
            // about. Left unclaimed there, a player who reached that level over another system would
            // have no way back out of it while the cursor rests here.
            var tooltipFake = buildTooltipSayingSomething();

            assertThat(tooltipFake.resolveNextLevelFor(
                    buildSectorWithEconomy(),
                    buildNamedSystem(),
                    PATROL_DETAILS))
                .contains(HoverTooltipDetailLevel.FACTIONS);
        }

        @Test
        void resolveNextLevelForCostsNoReadAtTheDeepestLevel() {
            // No box can hold anything past the deepest level the cycle declares, so the press there
            // collapses whatever this one holds and its own bound cannot change the answer - which is
            // the whole point of the press-time seam being deferred: it is the one question that costs
            // a walk of the hovered system, and it is asked once per press.
            var tooltipFake = buildTooltipSayingSomething();

            tooltipFake.resolveNextLevelFor(
                buildSectorWithEconomy(),
                buildNamedSystem(),
                PATROL_DETAILS);

            assertThat(tooltipFake.deeperDetailAskCount)
                .isZero();
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
        void renderForAsksForTheStepPerLevelThePlayerSet() {
            // Whether a breakdown several levels deep gives the eye a second cue agreeing with the
            // indent is the player's call, so the box carries the step across rather than fixing one:
            // a deep listing is easier to read at one size and easier to fit at four. Asked for on the
            // shape every layer shares, so no test of one layer's box has to pin it again.
            var typography = captureDrawnBox(buildTooltipSayingSomething())
                .style()
                .typography();

            assertThat(typography.levelShrink())
                .isEqualTo(NESTING_LEVEL_SHRINK);
        }

        @Test
        void renderForStacksItsLinesAtTheGapThePlayerSet() {
            // The box's own spacing, spent under every line no run of its own claims - so the reader
            // who wants a tighter box gets one without any part of it being singled out.
            var typography = captureDrawnBox(buildTooltipSayingSomething())
                .style()
                .typography();

            assertThat(typography.resolveLineGapAfter(IN_THE_BOXS_VOICE))
                .isEqualTo(LINE_GAP);
            assertThat(typography.resolveLineGapAfter(ONE_STEP_UNDER))
                .isEqualTo(LINE_GAP);
        }

        @Test
        void renderForTightensTheTwoDepthsAListingRunsLongAt() {
            // What actually makes a hover box tall: the terms one listed thing's number was summed
            // from, and the tier one of those terms breaks into. Each depth is bound to its own knob
            // and resolved off the line above the gap, so tightening a run closes it up without moving
            // the line it hangs from - which is why the two can be pushed much harder than the box's
            // own spacing above.
            var typography = captureDrawnBox(buildTooltipSayingSomething())
                .style()
                .typography();

            assertThat(typography.resolveLineGapAfter(TWO_STEPS_UNDER))
                .isEqualTo(TIER_2_LINE_GAP);
            assertThat(typography.resolveLineGapAfter(THREE_STEPS_UNDER))
                .isEqualTo(TIER_3_LINE_GAP);
        }

        @Test
        void renderForKeepsALineDeeperThanEitherTierWithTheRunItBelongsTo() {
            // Nothing bounds how deep a listing goes, and the knobs stop at the third step - so a line
            // below them reads with the run it is part of rather than springing back to the box's own
            // spacing, which would leave the innermost lines of a box the airiest thing in it.
            var typography = captureDrawnBox(buildTooltipSayingSomething())
                .style()
                .typography();

            assertThat(typography.resolveLineGapAfter(DEEPER_THAN_EITHER_TIER))
                .isEqualTo(TIER_3_LINE_GAP);
        }

        @Test
        void renderForRulesItsLeadersAtTheWeightsThePlayerSet() {
            // The line from a label across to its value is the one part of the box whose weight cannot be
            // settled in code - how heavy a solid run looks beside glyphs turns on the face, the size,
            // and the atlas - so the box carries the player's own pair across rather than staying at the
            // widget's shipped one.
            var leaderLineStyle = captureDrawnBox(buildTooltipSayingSomething())
                .style()
                .leaderLineStyle();

            assertThat(leaderLineStyle.thickness())
                .isEqualTo(LEADER_THICKNESS);
            assertThat(leaderLineStyle.alphaMult())
                .isEqualTo(LEADER_OPACITY);
        }

        @Test
        void renderForReadsTheLeaderWeightsAfreshOnEveryPaint() {
            // These two knobs are the ones a player actually tunes by eye, moving a slider with the map
            // open and watching the box - so a look settled once at class load would leave the box
            // ignoring every move until the game was restarted, which is the one thing that would make
            // them untunable.
            var tooltipFake = buildTooltipSayingSomething();

            assertThat(captureDrawnBox(tooltipFake).style().leaderLineStyle().alphaMult())
                .isEqualTo(LEADER_OPACITY);

            settingsMock.when(KmuMapLayerSettings::getMapTooltipLeaderOpacity)
                .thenReturn(FADED_LEADER_OPACITY);

            assertThat(captureDrawnBox(tooltipFake).style().leaderLineStyle().alphaMult())
                .isEqualTo(FADED_LEADER_OPACITY);
        }

        @Test
        void renderForReadsTheDensityAfreshOnEveryPaint() {
            // A slider moved with the box open takes effect on the next frame, which is the whole point
            // of settling the look per paint: a style built once at class load would leave the settings
            // screen and the map disagreeing until the game was restarted.
            var tooltipFake = buildTooltipSayingSomething();

            assertThat(captureDrawnBox(tooltipFake).style().typography().resolveLineGapAfter(
                    IN_THE_BOXS_VOICE))
                .isEqualTo(LINE_GAP);

            settingsMock.when(KmuMapLayerSettings::getMapTooltipLineGap)
                .thenReturn(TIGHTENED_LINE_GAP);

            assertThat(captureDrawnBox(tooltipFake).style().typography().resolveLineGapAfter(
                    IN_THE_BOXS_VOICE))
                .isEqualTo(TIGHTENED_LINE_GAP);
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
            var tooltipFake = new SystemCellTooltipFake(
                List.of(buildRow("The Hegemony"), buildRow("Independent")));

            var sections = captureDrawnBox(tooltipFake).sections();

            assertThat(sections.get(FIRST_BODY_SECTION))
                .isEqualTo(buildSection("The Hegemony"));
            assertThat(sections.get(SECOND_BODY_SECTION))
                .isEqualTo(buildSection("Independent"));
        }

        @Test
        void renderForReadsATitleLineTogetherWithTheName() {
            // A title line continues the heading, so it sits in the heading's own block - which is the
            // whole difference between heading the box with a line and opening the body with one.
            var titleRow = buildRow("The Hegemony");
            var tooltipFake = new SystemCellTooltipFake(
                List.of(titleRow),
                List.of(buildRow("Unpopulated")));

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
            var tooltipFake = new SystemCellTooltipFake(
                List.of(buildRow("The Hegemony")),
                List.of(buildRow("Unpopulated")));

            assertThat(captureDrawnBox(tooltipFake).sections().get(FIRST_BODY_SECTION))
                .isEqualTo(buildSection("Unpopulated"));
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
            // Drawn at the level the box opens on, where the next press opens the account further.
            var sections = captureDrawnBoxAt(
                    buildTooltipOfferingDetail(),
                    HoverTooltipDetailLevel.FACTIONS)
                .sections();

            assertThat(readRow(sections, FOOTER_SECTION, FOOTER_ROW).labelRuns())
                .containsExactly(
                    new TextSpan(CYCLE_KEY_NAME, BUTTON_SHORTCUT),
                    new TextSpan(EXPAND_SYSTEM_COMPOSITION, GRAY));
        }

        @Test
        void renderForDrawsTheHintFromTheCompositionRatherThanAskingTheBoxAgain() {
            // The whole reason the offer travels back beside the blocks. A layer reads its system to
            // build the body and already holds the answer, so asking again would charge that read to a
            // line of fine print - once per frame for as long as the cursor rests on the cell. The
            // hint is drawn all the same, which is what parts this from simply dropping the question.
            var tooltipFake = buildTooltipOfferingDetail();

            var sections = captureDrawnBox(tooltipFake).sections();

            assertThat(readRow(sections, FOOTER_SECTION, FOOTER_ROW))
                .isNotNull();
            assertThat(tooltipFake.deeperDetailAskCount)
                .isZero();
        }

        @Test
        void renderForNamesTheStepEveryPressShortOfTheDeepestTakes() {
            // The hint states what the next press does rather than which level is current, so it is
            // read off the level being moved to and every level names its own step. A number, or a
            // phrase read off the level being drawn, would tell the player nothing about what they
            // would gain by pressing.
            var tooltipFake = buildTooltipOfferingDetail();

            assertThat(readFooterWords(tooltipFake, HoverTooltipDetailLevel.SYSTEM_COMPOSITION))
                .isEqualTo(EXPAND_MARKET_STATS);
            assertThat(readFooterWords(tooltipFake, HoverTooltipDetailLevel.MARKET_STATS))
                .isEqualTo(EXPAND_PATROL_DETAILS);
        }

        @Test
        void renderForEndsTheDeepestBoxWithTheKeyThatCollapsesItAgain() {
            // The cycle wraps, so the deepest level is the one place the next press collapses the box
            // rather than opening it - the only level whose hint names a collapse, and it reads off
            // the level being drawn without the box holding anything that says which way it goes.
            var sections = captureDrawnBoxAt(
                    buildTooltipOfferingDetail(),
                    HoverTooltipDetailLevel.PATROL_DETAILS)
                .sections();

            assertThat(readRow(sections, FOOTER_SECTION, FOOTER_ROW).labelRuns())
                .containsExactly(
                    new TextSpan(CYCLE_KEY_NAME, BUTTON_SHORTCUT),
                    new TextSpan(COLLAPSE_TO_FACTIONS, GRAY));
        }

        @Test
        void renderForEndsABoxAtItsOwnDeepestLevelWithTheCollapseRatherThanADeadTier() {
            // The bug the bound exists for. A box whose account stops at the market stats has nothing
            // at the level below, so offering to expand into it would advertise a press that redraws
            // the box unchanged - the hint names the collapse instead, and the key acts on it.
            var sections = captureDrawnBoxAt(
                    buildTooltipOfferingDetailDownTo(HoverTooltipDetailLevel.MARKET_STATS),
                    HoverTooltipDetailLevel.MARKET_STATS)
                .sections();

            assertThat(readRow(sections, FOOTER_SECTION, FOOTER_ROW).labelRuns())
                .containsExactly(
                    new TextSpan(CYCLE_KEY_NAME, BUTTON_SHORTCUT),
                    new TextSpan(COLLAPSE_TO_FACTIONS, GRAY));
        }

        @Test
        void renderForOffersTheWayOutOfTheDeepestLevelToABoxWithNothingDeeper() {
            // The level is one shared fact carried across hovers, so a box with nothing to expand can
            // be met at the deepest level all the same - reached over some other system. It ends on
            // the collapse rather than on its content, which is the player's way back out; drawn
            // under the same rule the key is claimed by, so the hint and the press agree here too.
            var sections = captureDrawnBoxAt(buildTooltipSayingSomething(), PATROL_DETAILS)
                .sections();

            assertThat(readRow(sections, FOOTER_SECTION, FOOTER_ROW).labelRuns())
                .containsExactly(
                    new TextSpan(CYCLE_KEY_NAME, BUTTON_SHORTCUT),
                    new TextSpan(COLLAPSE_TO_FACTIONS, GRAY));
        }

        @Test
        void renderForGivesTheHintABlockOfItsOwnReadingAsAFootnote() {
            // A line about the box rather than about the system: set off by the box's own parting so it
            // is not read as the last entry of the block above, and marked as the kind of line it is so
            // the typography can set it apart from the content.
            var sections = captureDrawnBox(buildTooltipOfferingDetail()).sections();

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
            var footerRow = (TooltipRow.TableRow) readRow(
                captureDrawnBox(buildTooltipOfferingDetail()).sections(),
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
        void renderForStatesAtTheFootOfTheBoxWhatItHadNoRoomToShow() {
            // The one thing a box short of room must not keep to itself. The rows standing in for
            // withheld entries say it listing by listing; this says it over the box, so a reader can
            // tell a short list from a cut one wherever the cut happened to land.
            var tooltipFake = buildTooltipListing("Chicomoztoc", "Kazeron", "Sindria").offering();

            var footerRow = readRow(
                captureDrawnBoxWithin(tooltipFake, FACTIONS, ROOM_FOR_ALMOST_NOTHING).sections(),
                CUT_BOX_FOOTER_SECTION,
                FOOTER_ROW);

            assertThat(CellTooltipRowReads.readLabelTextRun(footerRow, FOOTER_WITHHELD_RUN))
                .isEqualTo(new TextSpan("2 not shown", GRAY));
        }

        @Test
        void renderForSaysNothingAboutWithheldContentInABoxThatFitted() {
            // A box that was never short of room states no figure about its own account: the hint at
            // its foot is the whole of that line, exactly as it was before a box could be cut.
            var tooltipFake = buildTooltipListing("Chicomoztoc", "Kazeron", "Sindria").offering();

            assertThat(readRow(
                    captureDrawnBox(tooltipFake).sections(),
                    CUT_BOX_FOOTER_SECTION,
                    FOOTER_ROW)
                .labelRuns())
                .containsExactly(
                    new TextSpan(CYCLE_KEY_NAME, BUTTON_SHORTCUT),
                    new TextSpan(EXPAND_SYSTEM_COMPOSITION, GRAY));
        }

        @Test
        void renderForEndsABoxOfferingNoDetailWithItsContent() {
            // The ordinary box takes no part in the detail cycle, so it ends where its content does rather
            // than on a line offering a counterpart that does not exist.
            var sections = captureDrawnBox(buildTooltipSayingSomething()).sections();

            assertThat(sections)
                .hasSize(BOX_WITH_ONE_BODY_BLOCK_SECTION_COUNT);
        }

        @Test
        void renderForDrawsNothingForABoxOfferingDetailAndNothingToSay() {
            // The hint is about the box rather than about the system, so it cannot be the thing that
            // makes a box worth drawing - a lone offer to expand into nothing says less than no box.
            var tooltipFake = new SystemCellTooltipFake(List.of()).offering();

            try (var rendererMock = Mockito.mockStatic(CursorTooltipRenderer.class)) {

                tooltipFake.renderFor(buildSectorWithEconomy(), buildNamedSystem(), PATROL_DETAILS);
                rendererMock.verifyNoInteractions();
            }
        }

        @Test
        void renderForAsksTheBodyForTheDepthItWasDrawnAt() {
            // The shared shape carries the level rather than reading one: it is what the dispatcher was
            // handed for this frame, and a shape that resolved its own would leave every layer drawing
            // a depth the player never chose - invisibly, since one box at the wrong depth still draws.
            var tooltipFake = buildTooltipSayingSomething();

            try (var rendererMock = Mockito.mockStatic(CursorTooltipRenderer.class)) {

                tooltipFake.renderFor(
                    buildSectorWithEconomy(),
                    buildNamedSystem(),
                    HoverTooltipDetailLevel.SYSTEM_COMPOSITION);

                assertThat(tooltipFake.bodyDetailLevel)
                    .isEqualTo(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);
            }
        }

        @Test
        void renderForDrawsNothingForABodyWithNothingToSay() {
            // A lone system name only repeats what the cursor already sits on, so an empty body is no
            // box rather than a titled empty one.
            var tooltipFake = new SystemCellTooltipFake(List.of());

            try (var rendererMock = Mockito.mockStatic(CursorTooltipRenderer.class)) {

                tooltipFake.renderFor(buildSectorWithEconomy(), buildNamedSystem(), PATROL_DETAILS);
                rendererMock.verifyNoInteractions();
            }
        }

        @Test
        void renderForDrawsNothingWithoutALiveEconomy() {
            // Bodies read the economy for what a layer holds in the system, so a sector without one is
            // not asked for a body at all.
            var tooltipFake = buildTooltipSayingSomething();

            try (var rendererMock = Mockito.mockStatic(CursorTooltipRenderer.class)) {

                tooltipFake.renderFor(mock(SectorAPI.class), buildNamedSystem(), PATROL_DETAILS);
                rendererMock.verifyNoInteractions();

                assertThat(tooltipFake.hasBuiltBodySections)
                    .isFalse();
            }
        }
    }

    // What one paint hands the tooltip widget. The box's placement and its GL pass run only in-engine, so
    // the pinned surface is the render call's own two arguments.
    private static DrawnBox captureDrawnBox(SystemCellTooltip tooltip) {
        return captureDrawnBoxAt(tooltip, FACTIONS);
    }

    // What the hint at the foot of the box says when it is drawn at the given level, which is the one
    // thing the cases about the offer's direction read.
    private static String readFooterWords(SystemCellTooltip tooltip, HoverTooltipDetailLevel level) {

        var footerRow = readRow(
            captureDrawnBoxAt(tooltip, level).sections(),
            FOOTER_SECTION,
            FOOTER_ROW);

        // The words about the key rather than the key itself: the shortcut is its own run and is
        // asserted where the hint's two runs are.
        return CellTooltipRowReads.readLabelTextRun(footerRow, FOOTER_PHRASE_RUN).text();
    }

    // The same, at a level a case names for itself - the shallowest being where the box opens, and so
    // the depth every case that is not about the depth is posed at.
    private static DrawnBox captureDrawnBoxAt(
            SystemCellTooltip tooltip,
            HoverTooltipDetailLevel detailLevel) {

        // A screen taller than anything these cases put in a box, so every one of them is about a box
        // drawn as it was composed. What a box short of room does is the subject of its own cases,
        // which state a budget of their own.
        return captureDrawnBoxWithin(tooltip, detailLevel, ROOM_FOR_ANY_BOX);
    }

    // The same over a stated amount of room, for the cases about a box with less of it than its
    // content needs. The screen is read once per paint, so the budget is the one seam standing in for
    // it.
    private static DrawnBox captureDrawnBoxWithin(
            SystemCellTooltip tooltip,
            HoverTooltipDetailLevel detailLevel,
            float heightBudget) {

        ArgumentCaptor<List<TooltipSection>> sectionsCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<CursorTooltipStyle> styleCaptor = ArgumentCaptor.captor();

        try (var rendererMock = Mockito.mockStatic(CursorTooltipRenderer.class)) {

            rendererMock.when(CursorTooltipRenderer::resolveHeightBudget)
                .thenReturn(heightBudget);

            tooltip.renderFor(buildSectorWithEconomy(), buildNamedSystem(), detailLevel);

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
        return new SystemCellTooltipFake(List.of(buildRow("The Hegemony")));
    }

    // The same layer, taking part in the detail cycle: it has something to say and something more to
    // say at every level below, which is what every case about the hint is posed over.
    private static SystemCellTooltipFake buildTooltipOfferingDetail() {
        return buildTooltipSayingSomething().offering();
    }

    // A layer in the detail cycle whose account ends above the deepest level the cycle declares -
    // what a box explaining a mechanic the deeper tiers say nothing about states, and what the wrap
    // has to be judged against.
    private static SystemCellTooltipFake buildTooltipOfferingDetailDownTo(
            HoverTooltipDetailLevel deepestHeldLevel) {

        return buildTooltipSayingSomething().offeringDownTo(deepestHeldLevel);
    }

    // A layer whose body is one block listing several things, which is what a box short of room can
    // actually give up: a line stating something about the system as a whole has no tail to take off.
    private static SystemCellTooltipFake buildTooltipListing(String... labelTexts) {

        var entries = new ArrayList<CellTooltipEntry>(labelTexts.length);

        for (var labelText : labelTexts) {
            entries.add(CellTooltipEntry.createEntry(CellTooltipEntryLine.createCountedLine(
                null,
                labelText,
                LISTED_ENTRY_VALUE)));
        }
        return new SystemCellTooltipFake(List.of(), List.of()).listing(entries);
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
        private final List<TooltipRow> bodyRows;
        private boolean hasBuiltBodySections;

        // The depth the body was asked for, held rather than acted on: a stand-in body hands back the
        // blocks it was built with whatever it is asked, so what it can say about the level is that it
        // arrived. Null until the box has drawn once.
        private HoverTooltipDetailLevel bodyDetailLevel;

        // The deepest level this box holds anything at. The shallowest for a box taking no part in the
        // detail cycle, which is the ordinary case.
        private HoverTooltipDetailLevel deepestHeldLevel = HoverTooltipDetailLevel.FACTIONS;

        // What this box lists under a heading, for the cases about a box with less room than its
        // content needs. Empty for a box whose lines each stand alone, which is every other case here.
        private List<CellTooltipEntry> listedEntries = List.of();

        // How many times the box was asked that through the press-time seam. Counted rather than
        // flagged, since what a paint must not do is ask it even once.
        private int deeperDetailAskCount;

        // A layer heading its box with nothing, which is the ordinary case and the one most cases here
        // are about - so only a case actually about the heading block names one.
        private SystemCellTooltipFake(List<TooltipRow> bodyRows) {
            this(List.of(), bodyRows);
        }

        private SystemCellTooltipFake(
                List<TooltipRow> titleRows,
                List<TooltipRow> bodyRows) {

            this.titleRows = titleRows;
            this.bodyRows = bodyRows;
        }

        @Override
        protected List<TooltipRow> buildTitleRows(SectorAPI sector, StarSystemAPI system) {
            return titleRows;
        }

        @Override
        protected ComposedCellBody composeBody(
                SectorAPI sector,
                StarSystemAPI system,
                HoverTooltipDetailLevel detailLevel) {

            hasBuiltBodySections = true;
            bodyDetailLevel = detailLevel;

            // Each line stated as a block of its own, through the very body a real layer composes
            // into - so what this box hands back is a body the box lays out rather than blocks it
            // could not have built. A banner is the block that lists nothing, which is exactly what a
            // stand-in line is.
            var body = CellTooltipBody.openBody(detailLevel);

            for (var bodyRow : bodyRows) {
                body.appendBannerSection(Optional.of(bodyRow));
            }
            body.appendSection(LISTED_BLOCK_HEADING, listedEntries);
            // The depth is stated beside the blocks the way a real box states it, off the same
            // stand-in answer - so a case reading the hint reads it from this box's composition
            // rather than from a second seam only the stand-in has.
            return new ComposedCellBody(body.readBlocks(), deepestHeldLevel);
        }

        @Override
        protected HoverTooltipDetailLevel resolveDeepestHeldLevelFor(
                SectorAPI sector,
                StarSystemAPI system) {

            deeperDetailAskCount++;

            return deepestHeldLevel;
        }

        // Puts this box in the detail cycle, holding every tier the levels declare - the box that has
        // something for each of them, against which a shallower one is the interesting case.
        private SystemCellTooltipFake offering() {
            return offeringDownTo(HoverTooltipDetailLevel.PATROL_DETAILS);
        }

        // Puts this box in the detail cycle with its account ending at deepestHeldLevel, which is what
        // a box explaining a mechanic the deeper tiers say nothing about states.
        private SystemCellTooltipFake offeringDownTo(HoverTooltipDetailLevel deepestHeldLevel) {
            this.deepestHeldLevel = deepestHeldLevel;
            return this;
        }

        // Gives this box a block listing several things, which is content a box short of room can
        // give some of up.
        private SystemCellTooltipFake listing(List<CellTooltipEntry> listedEntries) {
            this.listedEntries = listedEntries;
            return this;
        }
    }
}
