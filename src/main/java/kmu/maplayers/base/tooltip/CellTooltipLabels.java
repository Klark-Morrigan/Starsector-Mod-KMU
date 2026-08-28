package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.LabelRun;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.text.KmlibStrings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole sentence at the head of a listed line: the mark it leads with, the name that follows -
 * as one run, or as the stretches a name saying one of the box's findings is picked apart into - and
 * everything the line runs on into after its name, being where it falls in an ordering, what it calls
 * out, and what it remarks about itself.
 *
 * <p>Held apart from {@link CellTooltipRows} because the two answer different questions. This decides
 * what a line <em>says</em> and in what shades; that decides where it <em>sits</em> - across the box,
 * how far in, how loudly, and what fills its value column. The colour a label is spoken in is
 * therefore taken rather than chosen here: which tier a line belongs to is the row's to know.
 *
 * <p>The runs after the name are label runs and not a second kind of thing, which is why they are
 * composed here rather than added to a row afterwards. Each is added by the same walk, so a line
 * carrying all of them cannot end up with them in an order one tier reads differently from another,
 * and one carrying none of them is left as the runs its name came to rather than ending on runs that
 * draw nothing.
 *
 * <p>The order is what a reader meets in turn: which one this is, what the box has found about it,
 * and last, how far its account of it can be trusted. Only the findings read gold
 * ({@link #buildFindingSpan}); the place identifying the line and the remark about the box's own
 * account are quiet, so a reader scanning for findings passes over both.
 *
 * <p>Runs rather than a built row, so nothing here holds an opinion about the shape the label ends up
 * on. A banner and a listed line compose their words differently and lay them out differently, and a
 * label that returned a row would have made the second decision on the way to the first.
 *
 * <p>A mark travels inside the label, never in a leading column. A column is one gutter shared down a
 * stack, and it earns its keep only where markless lines have marked ones to align with; a listing
 * four levels deep has no such stack, so a mark several levels in and reserved as a column would draw
 * in the gutter the shallowest marked line widened, well left of the name it belongs to.
 */
final class CellTooltipLabels {

    // The most runs a label comes to: the mark it leads with, then what stands before the finding in
    // its name, the finding, and what stands after it. Fewer for almost every line, so the sizing is
    // a ceiling rather than a promise.
    private static final int MOST_LABEL_RUNS = 4;

    private CellTooltipLabels() {
    }

    /**
     * The runs one listed line's label reads as, in order, and never none: a label always says the
     * line's name, so whatever opens a row is here to open it with.
     *
     * @param line       the thing being listed
     * @param tierColour the colour the line's own tier speaks in, which the name takes unless the
     *                   line reads as an aside
     * @return the label's runs, at least one
     */
    static List<LabelRun> resolveLabelRuns(CellTooltipEntryLine line, Color tierColour) {

        var lineColour = resolveLabelColour(line, tierColour);
        var labelRuns = new ArrayList<LabelRun>(MOST_LABEL_RUNS);

        // A line carrying no mark opens on its words rather than on an image run with nothing to
        // load, the same absence rule the banner shape holds to.
        if (line.hasMark()) {
            labelRuns.add(resolveMarkSpan(line.mark(), lineColour));
        }
        appendNameRuns(labelRuns, line, lineColour);
        appendIndexRun(labelRuns, line);
        appendQualifierRun(labelRuns, line);
        appendNoteRun(labelRuns, line);

        return labelRuns;
    }

    /**
     * Builds the run a finding is stated in: the highlight colour, and the words alone.
     *
     * <p>One place decides that a finding reads gold, so the two places a line states one - picked out
     * of its own name, or run on after it - cannot part on the shade. What separates a run from the
     * one before it is the run vocabulary's own space, so a separator written in here would be the
     * second on the line.
     *
     * @param text the finding's words
     * @return the run, gold
     */
    static TextSpan buildFindingSpan(String text) {

        return new TextSpan(
            text,
            StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve());
    }

    // What a line's own name reads in: the tier's colour, or the quiet shade for a line that is a note
    // about the list rather than one of the things in it. One rule for both tiers, so an aside beneath
    // a listed thing and one beneath a member read alike - and in the same shade a value's working
    // takes, since both are arithmetic rather than a finding.
    private static Color resolveLabelColour(CellTooltipEntryLine line, Color tierColour) {
        return line.isAside()
            ? StarsectorUiColour.VANILLA_GRAY.resolve()
            : tierColour;
    }

    // The run a mark is drawn as: tinted to the line's own colour where the mark stands in for the
    // name, and untinted - drawn as its asset authored it - where it does not.
    //
    // A mark that is a shorthand for the name beside it is drawn in that name's own colour, so the two
    // read as one thing. Left in the colours its asset authored it would be the loudest run on a line
    // whose meaning is in the words - an icon coloured to carry across the sector map arrives here far
    // brighter than the plain text of the account it is sitting in. A crest says otherwise and keeps
    // its own pixels, being a picture of a thing rather than a shorthand for it.
    //
    // Read off the mark rather than the sprite, because nothing about a texture says which of the two
    // it is: the same crest artwork could be either, and only whatever composed the line knows whether
    // the mark is the subject or a label for it.
    private static ImageSpan resolveMarkSpan(CellTooltipMark mark, Color lineColour) {

        if (!mark.isInLineColour()) {
            return new ImageSpan(mark.spritePath());
        }
        return new ImageSpan(mark.spritePath(), lineColour);
    }

    // Adds the name: one run, or the stretch that reads as a finding picked out in gold with what
    // surrounds it either side of it.
    //
    // The stretches are exact substrings and every one past the first is a joined run
    // (LabelRun.isJoinedToPreviousRun, which argues the case), so the name draws as its author spelled
    // it whatever the finding landed beside.
    //
    // In the same gold the status after the name reads in, through the same run, because it is the
    // same finding: the word has qualified in every sense the resolution cares about, and only where
    // it is laid differs.
    private static void appendNameRuns(
            List<LabelRun> labelRuns,
            CellTooltipEntryLine line,
            Color lineColour) {

        var labelText = line.labelText();
        var labelFinding = line.labelFinding();

        if (labelFinding == null) {
            labelRuns.add(new TextSpan(labelText, lineColour));
            return;
        }
        var findingStart = labelFinding.startIndex();
        var findingEnd = labelFinding.endIndex();

        // Where the name begins among the runs, so the first stretch of it stands its own word space
        // clear of any mark while the stretches after it butt against what precedes them. Read as the
        // list stands rather than assumed, a marked line would join its name onto its own mark.
        var nameStart = labelRuns.size();

        appendNameRun(labelRuns, nameStart, new TextSpan(
            labelText.substring(0, findingStart), lineColour));

        appendNameRun(labelRuns, nameStart, buildFindingSpan(
            labelText.substring(findingStart, findingEnd)));

        appendNameRun(labelRuns, nameStart, new TextSpan(
            labelText.substring(findingEnd), lineColour));
    }

    // Adds one stretch of a split name, joined to whatever the name already came to and passed over
    // where the finding sat at either end and left nothing on that side.
    //
    // Passed over on being empty rather than on drawing nothing: a stretch of spaces is part of the
    // name, holding its words apart, and a name that lost it would be drawn as something its subject
    // is not called. What that leaves out is only the nothing either side of a finding that opened or
    // closed the name.
    private static void appendNameRun(List<LabelRun> labelRuns, int nameStart, TextSpan nameSpan) {

        if (nameSpan.text().isEmpty()) {
            return;
        }
        labelRuns.add(labelRuns.size() == nameStart ? nameSpan : nameSpan.joinsPreviousRun());
    }

    // Runs the label on into where the thing on the line falls in its ordering. Laid before any
    // qualifier, because it identifies the line rather than saying something about it: the eye
    // scanning names meets it as part of the name, and the gold run after it stays what the line
    // calls out.
    //
    // A capability of the vocabulary rather than a decision it makes - a line states a place only
    // where the thing on it belongs to an ordering a reader has some use for, which is the resolver's
    // to know.
    private static void appendIndexRun(List<LabelRun> labelRuns, CellTooltipEntryLine line) {

        if (line.indexPlace() == null) {
            return;
        }
        labelRuns.add(new TextSpan(
            line.indexPlace().text(),
            resolveIndexColour(line.indexPlace().outcome())));
    }

    // The shade a place reads in, by what it decided. Settled here rather than at whatever resolved
    // the outcome, so two layers marking a decided ordering cannot mark it in two different greens.
    //
    // Quiet while the place settled nothing, and in vanilla's own positive or negative shade once it
    // did. That is the moment the number stops being a label: two lines equal on everything else are
    // parted by it alone, so the reader looking for why one beat the other should find the answer
    // rather than work out that the smaller number wins.
    private static Color resolveIndexColour(CellTooltipIndexOutcome outcome) {

        return switch (outcome) {
            case WON -> StarsectorUiColour.VANILLA_HIGHLIGHT_GREEN.resolve();
            case LOST -> StarsectorUiColour.VANILLA_HIGHLIGHT_RED.resolve();
            case UNCONTESTED -> StarsectorUiColour.VANILLA_GRAY.resolve();
        };
    }

    // Runs the label on into whatever the line calls out - and leaves a line calling nothing out as
    // the runs it already had, rather than ending on one that draws nothing.
    private static void appendQualifierRun(List<LabelRun> labelRuns, CellTooltipEntryLine line) {

        if (!KmlibStrings.hasText(line.qualifierText())) {
            return;
        }
        labelRuns.add(buildFindingSpan(line.qualifierText()));
    }

    // Runs the label on into whatever it remarks about the thing on the line. Laid last, after the
    // qualifier, so a reader meets the line's identity, then what the box has found about the thing,
    // then what the box has to say about its own account of it - each in the shade that says which
    // it is.
    //
    // Last because the remark is the only run that is not about the subject of the line: a finding
    // set behind it would read as qualifying the remark rather than the colony, so the two findings
    // a line can carry - its place and its status - stay together on the near side of it.
    //
    // In the quiet shade, and that is the whole of what parts it from the gold run before it. A
    // remark about how current the account is would be read as a finding drawn in gold, which invites
    // the reader to weigh it against the numbers on the line rather than against the line's standing.
    private static void appendNoteRun(List<LabelRun> labelRuns, CellTooltipEntryLine line) {

        if (!KmlibStrings.hasText(line.noteText())) {
            return;
        }
        labelRuns.add(new TextSpan(
            line.noteText(),
            StarsectorUiColour.VANILLA_GRAY.resolve()));
    }
}
