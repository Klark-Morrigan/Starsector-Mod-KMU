package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.GRAY;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the sentence at the head of a listed line: the mark it leads with and in what colour, and the
 * name that follows - as one run, or as the stretches a name saying one of the box's findings is
 * picked apart into.
 *
 * <p>Asserted as the whole run list rather than run by run, because what a label is made of is
 * exactly what could go wrong here: a stretch dropped, a stretch too many, or a name reassembled into
 * something its subject is not called. The joining is pinned with it, since it is the only thing
 * standing between a split name and a space drawn where its author wrote none.
 *
 * <p>The colour a label speaks in is handed in rather than chosen here, so these cases pose it
 * directly. Which tier hands over which colour is {@link CellTooltipRowsTest}'s, that being a fact
 * about where a line sits rather than about what it says.
 */
final class CellTooltipLabelsTest {

    private static final String CREST = "graphics/ion_storm_icon.png";
    private static final CellTooltipMark CREST_MARK =
        CellTooltipMark.resolveMarkAsAuthored(CREST);

    // A mark of the other kind: a glyph standing in for the name beside it rather than a picture of
    // anything, which is the case that reads in the line's own colour.
    private static final String COLONY_ICON = "graphics/warroom/icon_planet.png";
    private static final CellTooltipMark COLONY_MARK =
        CellTooltipMark.resolveMarkInLineColour(COLONY_ICON);

    // What a line showing nothing at its head carries where a mark would be.
    private static final CellTooltipMark NO_MARK = null;

    @BeforeEach
    void installColours() {
        CellTooltipPaletteFake.installPalette();
    }

    @AfterEach
    void clearColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class ResolveLabelRuns {

        @Test
        void resolveLabelRunsSaysAPlainNameInOneRunOfTheColourItWasHanded() {

            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine.createLine(NO_MARK, "Jangala", "4,000"),
                    PLAYER_BRIGHT))
                .containsExactly(new TextSpan("Jangala", PLAYER_BRIGHT));
        }

        @Test
        void resolveLabelRunsOpensAMarkedLineOnItsMark() {
            // The mark rides inside the label so it lands where the line's own indent put it, and the
            // name follows as the next run of the same sentence - a word space clear of the image.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine.createLine(CREST_MARK, "Ion Storm", "42"),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new ImageSpan(CREST),
                    new TextSpan("Ion Storm", PLAYER_BRIGHT));
        }

        @Test
        void resolveLabelRunsDrawsAMarkStandingInForTheNameInThatNamesColour() {
            // An asset coloured to carry across the sector map arrives here brighter than the words
            // and the numbers around it, so a glyph that is only a shorthand for the name takes the
            // name's own colour and the pair reads as one thing.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine.createLine(COLONY_MARK, "Jangala", "4,000"),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new ImageSpan(COLONY_ICON, PLAYER_BRIGHT),
                    new TextSpan("Jangala", PLAYER_BRIGHT));
        }

        @Test
        void resolveLabelRunsDrawsAMarkOfItsOwnAsItsAssetAuthoredIt() {
            // A crest is a picture of a thing rather than a shorthand for it, and its colours are in
            // its own pixels - multiplied by the line's shade it would come out a tinted smudge.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine.createLine(CREST_MARK, "The Hegemony", "1,200"),
                    PLAYER_BRIGHT))
                .startsWith(new ImageSpan(CREST));
        }

        @Test
        void resolveLabelRunsQuietensTheNameOfAnAside() {
            // An aside stating how a number above it was arrived at is not one of the things the block
            // lists, so its name reads in the shade a value's working does whatever colour it was
            // handed - and its mark follows the name down with it.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(COLONY_MARK, "Same-faction market bonus", "+2")
                        .readsAsAside(),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new ImageSpan(COLONY_ICON, GRAY),
                    new TextSpan("Same-faction market bonus", GRAY));
        }

        @Test
        void resolveLabelRunsGildsTheStretchOfANameThatIsAFinding() {
            // The word is stated where the reader is already looking rather than repeated at the end
            // of the line, and in the same gold a status after the name reads in - it is the same
            // finding, drawn somewhere else.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Abandoned Station", "0")
                        .callsOutInLabel(0, 9),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new TextSpan("Abandoned", HIGHLIGHT),
                    new TextSpan(" Station", PLAYER_BRIGHT).joinsPreviousRun());
        }

        @Test
        void resolveLabelRunsDrawsAGildedNameAsItsAuthorSpelledIt() {
            // The reason every stretch past the first joins the one before it: a label spaces its
            // runs, so a name split anywhere its own spacing does not already part would be drawn
            // with a space the place is not called by.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Abandoned-Station", "0")
                        .callsOutInLabel(0, 9),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new TextSpan("Abandoned", HIGHLIGHT),
                    new TextSpan("-Station", PLAYER_BRIGHT).joinsPreviousRun());
        }

        @Test
        void resolveLabelRunsPicksAFindingOutOfTheMiddleOfAName() {
            // The fullest a name is drawn as: what stands before the word, the word, and what follows
            // - the two plain stretches in the colour the label was handed, so only the finding is
            // picked out.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Old Abandoned Yards", "0")
                        .callsOutInLabel(4, 13),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new TextSpan("Old ", PLAYER_BRIGHT),
                    new TextSpan("Abandoned", HIGHLIGHT).joinsPreviousRun(),
                    new TextSpan(" Yards", PLAYER_BRIGHT).joinsPreviousRun());
        }

        @Test
        void resolveLabelRunsGildsTheWholeOfANameThatSaysNothingElse() {
            // Nothing stands either side of the word, so the label is the one run it always was rather
            // than one opening or closing on a stretch that says nothing.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Abandoned", "0")
                        .callsOutInLabel(0, 9),
                    PLAYER_BRIGHT))
                .containsExactly(new TextSpan("Abandoned", HIGHLIGHT));
        }

        @Test
        void resolveLabelRunsKeepsAGildedNameInTheColourItWasHanded() {
            // Only the finding is gold. What surrounds it takes whatever shade the line speaks in, so
            // a gilded name on a member reads as a member with a word picked out.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Abandoned Station", "0")
                        .callsOutInLabel(0, 9),
                    TEXT))
                .containsExactly(
                    new TextSpan("Abandoned", HIGHLIGHT),
                    new TextSpan(" Station", TEXT).joinsPreviousRun());
        }

        @Test
        void resolveLabelRunsStandsAGildedNameClearOfTheMarkItFollows() {
            // The one case the two joining rules are told apart by: the stretches of a name butt
            // against each other, while the name as a whole is a word of the sentence and keeps its
            // space from the image before it. Joined onto its own mark, the name would draw against
            // the glyph.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(CREST_MARK, "Abandoned Station", "0")
                        .callsOutInLabel(0, 9),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new ImageSpan(CREST),
                    new TextSpan("Abandoned", HIGHLIGHT),
                    new TextSpan(" Station", PLAYER_BRIGHT).joinsPreviousRun());
        }
    }
}
