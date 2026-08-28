package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.LabelRun;
import kmlib.starsector.ui.text.TextSpan;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * What a listed line's label is made of: the mark it leads with, and the name that follows - as one
 * run, or as the stretches a name saying one of the box's findings is picked apart into.
 *
 * <p>Held apart from {@link CellTooltipRows} because the two answer different questions. This decides
 * what the sentence at the head of a line says and in what colours; that decides where the line sits
 * across the box, how far it steps in, how loudly it speaks and what fills its value column. The
 * colour a label is spoken in is therefore taken rather than chosen here - which tier a line belongs
 * to is the row's to know.
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
     * @param line      the thing being listed
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

        return labelRuns;
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

        appendNameRun(labelRuns, nameStart, CellTooltipRows.buildQualifierSpan(
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
}
