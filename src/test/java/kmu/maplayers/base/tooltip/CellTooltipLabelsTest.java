package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.RedactedSpan;
import kmlib.starsector.ui.text.TextSpan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.GRAY;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT_GREEN;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT_RED;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the sentence at the head of a listed line: the mark it leads with and in what colour, and the
 * name that follows - as one run, as the stretches a name saying one of the box's findings is picked
 * apart into, or as the blocks standing for a name the line withholds.
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

    // The shape of a withheld name: two words of seven and four characters, standing in for a colony
    // the player has not found.
    private static final List<Integer> WITHHELD_NAME = List.of(7, 4);

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
        void resolveLabelRunsBlocksOutAWithheldNameInTheColourItWasHanded() {
            // The redaction stands exactly where the name would, in the shade the name would have read
            // in - a line of the list with one part blocked out rather than a shape of its own.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine.createRedactedLine(NO_MARK, WITHHELD_NAME, "820"),
                    PLAYER_BRIGHT))
                .containsExactly(new RedactedSpan(List.of(7, 4), PLAYER_BRIGHT));
        }

        @Test
        void resolveLabelRunsOpensAWithheldLineOnItsMark() {
            // A redacted line opens on an image run like every other line, so it is not set apart by
            // being shorter as well as blocked out - and the glyph reads in the line's own colour,
            // standing in for the name beside it as any other shorthand would.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine.createRedactedLine(COLONY_MARK, WITHHELD_NAME, "820"),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new ImageSpan(COLONY_ICON, PLAYER_BRIGHT),
                    new RedactedSpan(List.of(7, 4), PLAYER_BRIGHT));
        }

        @Test
        void resolveLabelRunsRunsAWithheldNamesPlaceAndStatusOnAfterIt() {
            // Everything the line runs on into is laid exactly as it is on a line that says its name:
            // the redaction takes the whole of the name's place and none of the runs after it move.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createRedactedLine(NO_MARK, WITHHELD_NAME, "820")
                        .indexedAt("[3]", CellTooltipIndexOutcome.UNCONTESTED)
                        .qualifiedWith("undiscovered"),
                    TEXT))
                .containsExactly(
                    new RedactedSpan(List.of(7, 4), TEXT),
                    new TextSpan("[3]", GRAY),
                    new TextSpan("undiscovered", HIGHLIGHT));
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
        void resolveLabelRunsRunsAPlaceOnAfterTheNameInTheQuietShade() {
            // A place identifies the line rather than saying something about it, so it is drawn in the
            // shade the working behind a value is - not the gold a finding reads in - and sits with the
            // name it belongs to rather than at the end of the line.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Chicomoztoc", "19")
                        .indexedAt("[2]", CellTooltipIndexOutcome.UNCONTESTED),
                    TEXT))
                .containsExactly(
                    new TextSpan("Chicomoztoc", TEXT),
                    new TextSpan("[2]", GRAY));
        }

        @Test
        void resolveLabelRunsPicksAPlaceOutOnceItDecidedSomething() {
            // The moment the number stops being a label: two lines equal on everything else are parted
            // by it alone, so it reads in vanilla's own positive or negative shade rather than leaving
            // the reader to work out that the smaller number wins.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Eventide", "6")
                        .indexedAt("[2]", CellTooltipIndexOutcome.WON),
                    TEXT))
                .contains(new TextSpan("[2]", HIGHLIGHT_GREEN));

            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Culann", "6")
                        .indexedAt("[5]", CellTooltipIndexOutcome.LOST),
                    TEXT))
                .contains(new TextSpan("[5]", HIGHLIGHT_RED));
        }

        @Test
        void resolveLabelRunsRunsWhatTheLineRemarksInTheQuietShade() {
            // A remark is what the box says about its own account of the line rather than something it
            // has found, so it takes the shade a value's working does. In the qualifier's gold a reader
            // would weigh it against the numbers on the line.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Sentinel Gantries", "0")
                        .notedWith("last seen 34 days ago (c206.05.12)"),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new TextSpan("Sentinel Gantries", PLAYER_BRIGHT),
                    new TextSpan("last seen 34 days ago (c206.05.12)", GRAY));
        }

        @Test
        void resolveLabelRunsReadsThePlaceTheStatusAndTheRemarkInThatOrder() {
            // Three runs answering three questions, in the order a reader meets them: which one this
            // is, what the box has found about it, and how current the account of it is. The remark
            // closes the label because it is the only run not about the thing on the line - set ahead
            // of the gold, it would break a status away from the name it qualifies.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Sentinel Gantries", "0")
                        .indexedAt("[2]", CellTooltipIndexOutcome.UNCONTESTED)
                        .notedWith("last seen 34 days ago (c206.05.12)")
                        .qualifiedWith("strongest"),
                    TEXT))
                .containsExactly(
                    new TextSpan("Sentinel Gantries", TEXT),
                    new TextSpan("[2]", GRAY),
                    new TextSpan("strongest", HIGHLIGHT),
                    new TextSpan("last seen 34 days ago (c206.05.12)", GRAY));
        }

        @Test
        void resolveLabelRunsRunsAMarkedLinesPlaceAndStatusOnPastItsMark() {
            // The fullest label the vocabulary can build, and the one place the mark's cost to every
            // run after it is legible: name, place and status each sit a run further along than they
            // would on the same line unmarked, in that order, and the mark still opens the label.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(CREST_MARK, "Chicomoztoc", "19")
                        .indexedAt("[2]", CellTooltipIndexOutcome.UNCONTESTED)
                        .qualifiedWith("strongest"),
                    TEXT))
                .containsExactly(
                    new ImageSpan(CREST),
                    new TextSpan("Chicomoztoc", TEXT),
                    new TextSpan("[2]", GRAY),
                    new TextSpan("strongest", HIGHLIGHT));
        }

        @Test
        void resolveLabelRunsReadsAnIntroducedStatusAsItsWordItsMarkThenItsFinding() {
            // A status naming the thing the line belongs to comes to three runs in the order a reader
            // meets them, and only the last is gold: the box's joining word and the picture beside it
            // are the sentence around the finding rather than findings themselves, so a reader
            // scanning for what the box worked out lands on the name alone.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Astral Armada", "3")
                        .callsOut(CellTooltipQualifier.introduceFinding(
                            "of",
                            CREST_MARK,
                            "Allied Powers")),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new TextSpan("Astral Armada", PLAYER_BRIGHT),
                    new TextSpan("of", GRAY),
                    new ImageSpan(CREST),
                    new TextSpan("Allied Powers", HIGHLIGHT));
        }

        @Test
        void resolveLabelRunsClosesAnEnclosedStatusOnTheWordSayingWhatItNames() {
            // The fullest status the vocabulary builds, for a finding that cannot say for itself
            // what kind of thing it is: the closing word takes the same quiet shade the opening one
            // does, both being the box's own words rather than anything it found.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Church of Galactic Redemption", "1,200")
                        .callsOut(CellTooltipQualifier.encloseFinding(
                            "of the",
                            CREST_MARK,
                            "C.O.G.R.",
                            "alliance")),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new TextSpan("Church of Galactic Redemption", PLAYER_BRIGHT),
                    new TextSpan("of the", GRAY),
                    new ImageSpan(CREST),
                    new TextSpan("C.O.G.R.", HIGHLIGHT),
                    new TextSpan("alliance", GRAY));
        }

        @Test
        void resolveLabelRunsAddsNoRunForWhateverTheLineLeavesUnsaid() {
            // The three absences together, and the reason they matter: a run drawing nothing would
            // still be a run the box measures and parts from its neighbour, so a plain line has to
            // come to the single run its name is.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine.createLine(NO_MARK, "Ion Storm", CellTooltipRows.NO_SCORE),
                    TEXT))
                .containsExactly(new TextSpan("Ion Storm", TEXT));
        }

        @Test
        void resolveLabelRunsStandsAStatusClearOfAGildedName() {
            // Two findings about two different subjects, in the one shade the box reserves for
            // findings: what the place is, said in its name, and that the player has not found it,
            // said after it. The status is a word of its own rather than joining the name it follows.
            assertThat(CellTooltipLabels.resolveLabelRuns(
                    CellTooltipEntryLine
                        .createLine(NO_MARK, "Abandoned Station", "0")
                        .callsOutInLabel(0, 9)
                        .qualifiedWith("undiscovered"),
                    PLAYER_BRIGHT))
                .containsExactly(
                    new TextSpan("Abandoned", HIGHLIGHT),
                    new TextSpan(" Station", PLAYER_BRIGHT).joinsPreviousRun(),
                    new TextSpan("undiscovered", HIGHLIGHT));
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
