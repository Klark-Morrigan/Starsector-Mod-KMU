package kmu.maplayers.base.tooltip.layout;

import kmlib.starsector.ui.text.LabelRun;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import java.util.List;

/**
 * How a test reads one line of a cell tooltip, the tier values the vocabulary lays lines out at, and
 * where each stretch of a line's label falls. Shared because a row is authored in one place
 * ({@link CellTooltipRows}) and asserted in several - the vocabulary's own tests plus every resolver
 * that contributes a single line - and a copy of the indent per test class is a value that can drift
 * from the one the tooltip actually draws at while every copy goes on agreeing with itself.
 *
 * <p>The values are restated here rather than read from {@link CellTooltipRows}, whose own are private:
 * a constant read from the class under test would be edited alongside it and could never fail, while
 * these have to be changed deliberately when the layout does.
 */
public final class CellTooltipRowReads {

    /** The inset a line one level down draws at - what makes it read as belonging to the line above. */
    public static final float MEMBER_INDENT = 14f;

    /**
     * The inset a line two levels down draws at. Stated as its own value rather than multiplied out of
     * the one above, so a case about how far a breakdown steps in per level cannot pass by restating the
     * arithmetic the layout does.
     */
    public static final float NESTED_MEMBER_INDENT = 28f;

    /** Where a top-tier line sits: at no indent of its own, before the crest gutter is reserved. */
    public static final float NO_INDENT = 0f;

    /** What a line speaking in the box's own voice stands at - a block's own lines, and their peers. */
    public static final int NO_SUBORDINATION = 0;

    /** What a line breaking down the line above it stands at - the box's account of a finding. */
    public static final int ONE_LEVEL_SUBORDINATED = 1;

    /** What a line breaking down an account stands at - a factor's own tiers. */
    public static final int TWO_LEVELS_SUBORDINATED = 2;

    /** Float comparison slack for the indents above, which are laid out in UI units. */
    public static final float TOLERANCE = 0.001f;

    /** Where a line carrying no mark says its name: its opening run. */
    public static final int LABEL_RUN = 0;

    /** Where an unmarked line's closing qualifier sits, that being the run after its name. */
    public static final int QUALIFIER_RUN = 1;

    /**
     * Where a marked line's mark sits: the opening run, ahead of the words. Shared with the run
     * indices below because a mark shifts every run after it, so a test reading the wrong index on a
     * marked line finds an image where it expected words - and every box in the mod marks its lines
     * through the one vocabulary, which is exactly the sort of fact three copies agree on until one
     * is edited.
     */
    public static final int MARK_RUN = 0;

    /** Where a marked line says its name: one run past the mark it opens on. */
    public static final int MARKED_LABEL_RUN = 1;

    /** Where a marked line's closing qualifier sits, that being the run after its name. */
    public static final int MARKED_QUALIFIER_RUN = 2;

    private CellTooltipRowReads() {
    }

    /**
     * Reads one of a line's label runs by position. The runs are a sequence read as one sentence, so a
     * test names which stretch of the line it is about rather than reaching through the row's content.
     *
     * @param row      the line to read
     * @param runIndex the run's position in the label, in reading order
     * @return that run - a stretch of text and the colour it draws in, or an image set among the words
     */
    public static LabelRun readLabelRun(TooltipRow row, int runIndex) {
        return row.labelRuns().get(runIndex);
    }

    /**
     * Reads one of a line's label runs as a stretch of text, for a test asserting on what a run says or
     * the colour it draws in rather than on the run as a whole. A run that turns out to be an image
     * fails the cast, which is the right answer: a test reaching for words found a line that does not
     * say them where it expected.
     *
     * @param row      the line to read
     * @param runIndex the run's position in the label, in reading order
     * @return that run's text and the colour it draws in
     */
    public static TextSpan readLabelTextRun(TooltipRow row, int runIndex) {
        return (TextSpan) readLabelRun(row, runIndex);
    }

    /**
     * Reads what a line opens with in words: its first run that carries any, run on into whatever
     * butts against it. Runs holding an image rather than text are stepped over, so a line led by a
     * crest reads as the name it goes on to say rather than as a sprite path - which is what lets one
     * list of expected lines cover a box mixing plain entries with a crested banner.
     *
     * <p>The joined runs are gathered because a name saying one of the box's findings is split at
     * that stretch and drawn as several runs. Read as the first alone, such a line answers a fragment
     * of its own name - so a fixture named for what it is would fail against the name it is plainly
     * shown by, over a split nothing about the case under test asked for. Nothing past the name is
     * swept in with it: a status or a remark is a word of its own and never joins what it follows.
     *
     * @param row the line to read
     * @return the line's opening words, or empty when it says none
     */
    public static String readOpeningWords(TooltipRow row) {
        var openingWords = new StringBuilder();
        var hasOpened = false;

        for (var labelRun : row.labelRuns()) {

            if (!(labelRun instanceof TextSpan textSpan)) {
                continue;
            }
            // Tracked rather than read off what has been gathered, so a line whose name is blank
            // answers that blank rather than running on into the status behind it.
            if (hasOpened && !textSpan.isJoinedToPreviousRun()) {
                break;
            }
            openingWords.append(textSpan.text());
            hasOpened = true;
        }
        return openingWords.toString();
    }

    /**
     * Reads a whole body as the words a player sees, top to bottom - headings, entries and whatever
     * hangs beneath them alike. What most cases about a box assert on, since one expected list states
     * both what the box says and the order it says it in, which asserting block by block would bury.
     *
     * @param sections the body's blocks, in reading order
     * @return each line's opening words, in draw order
     */
    public static List<String> readSectionOpeningWords(List<TooltipSection> sections) {
        return readRowOpeningWords(TooltipSection.readRowsInOrder(sections));
    }

    /**
     * The same over lines already flattened out of their blocks, for a case holding the rows rather
     * than the body.
     *
     * <p>Named apart from {@link #readSectionOpeningWords} rather than overloading it: both take a
     * list, and two lists erase to the one signature.
     *
     * @param rows the lines of a body, in draw order
     * @return each line's opening words, in that order
     */
    public static List<String> readRowOpeningWords(List<TooltipRow> rows) {
        return rows
            .stream()
            .map(CellTooltipRowReads::readOpeningWords)
            .toList();
    }

    /**
     * Reads one line as the table row it is, for the assertions that reach for an indent, a crest, or a
     * value - which is where those live. A block's lines are typed on the row supertype, since a
     * centred line is a legal shape for one, so a line that turns out not to lay into the box's columns
     * fails the cast rather than the assertion.
     *
     * @param rows     the lines of a body, in draw order
     * @param rowIndex the line's position among them
     * @return that line as a table row
     */
    public static TooltipRow.TableRow readTableRow(List<TooltipRow> rows, int rowIndex) {
        return (TooltipRow.TableRow) rows.get(rowIndex);
    }
}
