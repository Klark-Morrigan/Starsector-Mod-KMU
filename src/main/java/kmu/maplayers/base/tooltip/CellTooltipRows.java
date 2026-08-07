package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.text.KmlibStrings;

import java.awt.Color;
import java.util.List;

/**
 * The line vocabulary a cell tooltip's content is written in: a heading that names a block, a listed
 * line inside it laid at the {@linkplain CellTooltipEntryLevel level} it was found at, a banner row
 * centred across the box to state
 * something about the hovered system as a whole, the qualifier run any of them may end on, and the quiet
 * working a value may open on. Each shape fixes its own indent and colours, and its placement bar the crest gutter - that one column being
 * shared by everything a block lists, so whether it is reserved arrives as the block's answer rather than
 * as this line's. What a layer says is otherwise the only thing that varies between two hover boxes.
 *
 * <p>The split that matters is the table shapes against the banner: a heading and every listed line lay
 * into the box's table, lining up against the crest gutter and the value column, while a banner has left
 * that table to speak for the box. So the choice of builder is a statement about whether a line is one of
 * the findings or a verdict over all of them.
 *
 * <p>Held apart from {@link SystemCellTooltip} because the two answer different questions - that class
 * decides how the box is framed, these decide how one line inside it reads - and because a body is
 * rarely built in one place: a resolver that contributes a single line reaches the same vocabulary as
 * the tooltip composing them, without either having to be the other's subclass.
 *
 * <p>The table shapes take a whole {@link CellTooltipEntryLine} rather than its parts, so the entirety of
 * how a listed thing reads - its tier, its colours, its gutter, its value, and the status it calls out -
 * is settled here and a caller destructures nothing. They are the block's alone
 * ({@link CellTooltipSections}), which is why they are not offered past this package: a body states what
 * its blocks list and the block lays those lines out. Which tier a line takes is read off the level it
 * sits at rather than chosen at the call site, so a heading and a listed line cannot drift into each
 * other and no body can list something at a tier it authored itself. What stays open is what a line is
 * composed from wherever one is authored - the banner and the qualifier run.
 */
public final class CellTooltipRows {

    /**
     * The value of a row that carries no number, such as a status or section line. Rendered as-is it
     * draws nothing and measures zero width, so the value column collapses for that row.
     */
    public static final String NO_SCORE = "";

    /**
     * What a run continuing a line opens with. A run is laid down where the one before it ended, so
     * two runs that do not part read as one word - "Sizehidden", "F1hide score contributions". Owned
     * by the line vocabulary rather than by each phrase, so the gap is the layout's fact and no author
     * has to remember it or write it into a string.
     */
    public static final String CONTINUATION_GAP = " ";

    // The inset one level down adds, so a line reads as belonging to the line above it; a line the block
    // lists in its own right sits flush at zero. Applied per level rather than per tier, so a breakdown
    // three deep steps in evenly instead of collapsing everything below the first level onto one indent
    // no reader could tell apart.
    private static final float MEMBER_INDENT = 14f;

    private CellTooltipRows() {
    }

    /**
     * Builds the run a line ends on to call something out - a status or flag stated on the line it
     * qualifies rather than on a line of its own, in the highlight colour. Hand it to
     * {@code TooltipRow.continuesWith} on whichever line it qualifies. One place decides that such a
     * qualifier reads gold and stands clear of the words it follows, so two layers calling out
     * different facts still call them out alike.
     *
     * @param text the qualifier continuing a line's label, unspaced - the run parts itself from
     *             what it follows
     * @return the run, ready to continue a line
     */
    public static TextSpan buildQualifierSpan(String text) {
        return new TextSpan(
            CONTINUATION_GAP + text,
            StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve());
    }

    /**
     * Builds a banner row: a line centred in the box's content region, led by a small image where one
     * was resolved, in the plain text colour - for a line stating something about the hovered system as
     * a whole rather than entering it in a list. Who holds the system by decree is one, and why it holds
     * nobody is another.
     *
     * <p>Centred rather than laid in the columns because the line speaks for the whole box: a line
     * indented under the title, or aligned to a crest gutter the entries below reserve, reads as the
     * first entry of a list it is not part of. The crest travels as a run of the line rather than in
     * that gutter for the same reason - it belongs to the sentence, and centring is what a line clear of
     * the table is free to do.
     *
     * <p>Its qualifier, where it has one, is the shared {@link #buildQualifierSpan} added by the caller,
     * so a banner calls something out in the same shade every other line does.
     *
     * @param crestSpritePath the leading image's texture path, or null for a banner of words alone
     * @param text            the line's words
     * @return the row, ready to add to a body
     */
    public static TooltipRow.CentredRow buildBannerRow(String crestSpritePath, String text) {
        var textSpan = new TextSpan(text, StarsectorUiColour.VANILLA_TEXT.resolve());

        // A faction the game gives no crest resolves to no path at all, so the line is built from its
        // words alone rather than from an image run with nothing to load.
        if (crestSpritePath == null) {
            return TooltipRow.createCentredRow(textSpan);
        }
        return TooltipRow
            .createCentredRow(new ImageSpan(crestSpritePath))
            .continuesWith(textSpan);
    }

    /**
     * Builds the line naming a block: flush at the box's left content edge, clear of the crest gutter
     * the entries under it lead with, in the highlight colour and carrying neither crest nor value.
     *
     * <p>Both of those are what tells a heading from its own entries. Laid inside the gutter it starts
     * where their labels start and so reads as indented under nothing; drawn in the entries' own bright
     * it is told apart only by lacking a crest - and a block whose heading reads as one of its entries
     * is a flat list of equals with a stray line on top.
     *
     * @param text the block's name
     * @return the row, ready to open a block
     */
    static TooltipRow.TableRow buildSectionHeadingRow(String text) {
        return TooltipRow
            .createRow(new TextSpan(text, StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve()))
            .clearsCrestColumn();
    }

    /**
     * Builds the line for one listed thing, in the shape its level calls for: a thing the block lists in
     * its own right reads flush and bright, and anything found beneath one reads plainer and stepped in
     * once per level, so how far a reader is inside a breakdown is legible from the line alone.
     *
     * <p>Taking the level rather than a choice of tier is what keeps the two apart from the block's side
     * too: the walk that found the line states only where it went, and this decides what that looks
     * like, so a listing three levels deep needs no new shape and no new call.
     *
     * <p>Whether the crest gutter is reserved is the block's answer rather than this line's, since the
     * gutter is one column shared by everything the block lists - which is why it arrives as a parameter.
     *
     * @param line                   the thing being listed
     * @param level                  where beneath the block the line was found, and how far under the
     *                               box's own voice it speaks
     * @param isReservingCrestColumn whether the block reserves the crest gutter for every line in it
     * @return the row, ready to add to a block
     */
    static TooltipRow buildListedRow(
            CellTooltipEntryLine line,
            CellTooltipEntryLevel level,
            boolean isReservingCrestColumn) {

        if (level.isListedInItsOwnRight()) {
            return buildEntryRow(line, level, isReservingCrestColumn);
        }
        return buildMemberRow(line, level, isReservingCrestColumn);
    }

    /**
     * Builds an entry line: at no indent of its own, its label bright and its value in the highlight
     * colour, so one of the things a block lists reads as being listed rather than as part of whatever
     * is listed beneath it.
     *
     * @param line                   what the block lists there
     * @param level                  where the line was found; a block's own line speaks in the box's
     *                               voice
     * @param isReservingCrestColumn whether the block reserves the crest gutter for every line in it
     * @return the row, ready to add to a block
     */
    private static TooltipRow buildEntryRow(
            CellTooltipEntryLine line,
            CellTooltipEntryLevel level,
            boolean isReservingCrestColumn) {

        var row = TooltipRow
            .createRow(new TextSpan(
                line.labelText(),
                StarsectorUiColour.VANILLA_PLAYER_BRIGHT.resolve()))
            .carriesCrest(line.iconSpritePath());

        return appendQualifier(
            placeRow(
                appendValue(row, line, StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve()),
                level,
                isReservingCrestColumn),
            line);
    }

    /**
     * Builds a member line: indented once per level beneath the block and drawn in the plain text
     * colour, so one of the things an entry carries reads as belonging to that entry rather than as
     * something the block lists in its own right, and a level deeper again reads as belonging to that.
     *
     * <p>Every level below the first shares this one shape, differing only in how far it steps in and
     * how far under the box's voice it stands. A tier of its own per level would mean a colour or a
     * weight nobody has designed the moment a breakdown grows a level, whereas the indent already says
     * the one thing the reader needs - what this line is part of.
     *
     * @param line                   one of the things the line above carries
     * @param level                  where beneath the block the line was found, and how far under the
     *                               box's own voice it speaks; at least one step in
     * @param isReservingCrestColumn whether the block reserves the crest gutter for every line in it
     * @return the row, ready to add to a block
     */
    private static TooltipRow buildMemberRow(
            CellTooltipEntryLine line,
            CellTooltipEntryLevel level,
            boolean isReservingCrestColumn) {

        var textColour = StarsectorUiColour.VANILLA_TEXT.resolve();
        var row = TooltipRow
            .createRow(new TextSpan(line.labelText(), textColour))
            .carriesCrest(line.iconSpritePath())
            .indentsBy(level.indentDepth() * MEMBER_INDENT);

        return appendQualifier(
            placeRow(appendValue(row, line, textColour), level, isReservingCrestColumn),
            line);
    }

    // Fills a line's value column: its number in the line's own colour, opened where the line states
    // one on the working it came out of, in the quiet shade.
    //
    // Greying the working is the same move the qualifier run makes in gold - it says which part of the
    // line is the finding and which is the arithmetic behind it. Drawn in one colour the two read as a
    // single number with a stray separator in it, which is exactly what a value like a rate over a total
    // is not.
    //
    // Applied at every tier through one helper, so a value stated on a member is split exactly as one
    // stated on the entry it belongs to - and a line stating no working keeps the single run it had,
    // rather than one opening on a run that draws nothing.
    private static TooltipRow.TableRow appendValue(
            TooltipRow.TableRow row,
            CellTooltipEntryLine line,
            Color valueColour) {

        var valueSpan = new TextSpan(line.valueText(), valueColour);

        if (!KmlibStrings.hasText(line.valueWorkingText())) {
            return row.carriesValue(valueSpan);
        }
        return row.carriesValueRuns(List.of(
            new TextSpan(line.valueWorkingText(), StarsectorUiColour.VANILLA_GRAY.resolve()),
            valueSpan));
    }

    // Where a line sits across the box and how loudly it speaks, the two facts a line's own level and
    // its block settle between them.
    //
    // The label starts behind the crest gutter where the block reserves one, so a line carrying no mark
    // still lines up with the crested lines around it, and at the content edge where the block reserves
    // none - a gutter no line in the block fills has nothing to align to, and reads as the line being
    // indented under the heading above it.
    //
    // The subordination is stated from the level rather than from the indent, so a line set in as a
    // peer - a faction inside the alliance naming it - stays as loud as the line it sits under, while
    // the account beneath either of them quietens by the same step whichever it hangs from.
    //
    // Applied at every tier through one helper, so an entry and the members beneath it cannot start
    // from different columns or read the same level two ways.
    private static TooltipRow.TableRow placeRow(
            TooltipRow.TableRow row,
            CellTooltipEntryLevel level,
            boolean isReservingCrestColumn) {

        var subordinatedRow = row.subordinatedAt(level.subordinationLevel());

        return isReservingCrestColumn
            ? subordinatedRow
            : subordinatedRow.clearsCrestColumn();
    }

    // Runs a line on into whatever it calls out. Applied at every tier through one helper, so a status
    // stated on a member reads exactly as one stated on the entry it belongs to - and a line calling
    // nothing out is left as the single run it was, rather than ending on a run that draws nothing.
    private static TooltipRow appendQualifier(TooltipRow.TableRow row, CellTooltipEntryLine line) {
        if (!KmlibStrings.hasText(line.qualifierText())) {
            return row;
        }
        return row.continuesWith(buildQualifierSpan(line.qualifierText()));
    }
}
