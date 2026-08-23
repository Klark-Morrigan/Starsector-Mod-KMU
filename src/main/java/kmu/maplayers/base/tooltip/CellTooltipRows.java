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
 * working a value may open on. Each shape fixes its own indent, colours, and placement. What a layer says
 * is the only thing that varies between two hover boxes.
 *
 * <p>The split that matters is the table shapes against the banner: a heading and every listed line lay
 * into the box's table, lining up against the value column, while a banner has left that table to speak
 * for the box. So the choice of builder is a statement about whether a line is one of the findings or a
 * verdict over all of them.
 *
 * <p>A mark travels inside the label on every shape here, never in a leading column. A column is one
 * gutter shared down a stack, and it earns its keep only where markless lines have marked ones to align
 * with; a listing four levels deep has no such stack, so a mark several levels in and reserved as a
 * column would draw in the gutter the shallowest marked line widened, well left of the name it belongs
 * to. Set as a run it lands where the indent already put the line - which is what a banner has always
 * done, so the box has one rule for images rather than two.
 *
 * <p>What colour a mark draws in follows from what it is for, which its line states. A mark standing in
 * for the name beside it takes that name's own colour, so the pair reads as one thing and the eye is
 * not pulled to the run carrying the least of the meaning; a mark that is a picture in its own right -
 * a crest - keeps the colours its asset authored.
 *
 * <p>Held apart from {@link SystemCellTooltip} because the two answer different questions - that class
 * decides how the box is framed, these decide how one line inside it reads - and because a body is
 * rarely built in one place: a resolver that contributes a single line reaches the same vocabulary as
 * the tooltip composing them, without either having to be the other's subclass.
 *
 * <p>The table shapes take a whole {@link CellTooltipEntryLine} rather than its parts, so the entirety of
 * how a listed thing reads - its tier, its colours, its mark, its value, and the status it calls out -
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
     * qualifier reads gold, so two layers calling out different facts still call them out alike.
     *
     * <p>The words alone: what parts a run from the one before it is the run vocabulary's
     * ({@code LabelRuns}), spent as the drawing face's own space, so a separator written in here would
     * be the second one on the line.
     *
     * @param text the qualifier continuing a line's label
     * @return the run, ready to continue a line
     */
    public static TextSpan buildQualifierSpan(String text) {
        return new TextSpan(
            text,
            StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve());
    }

    /**
     * Builds a banner row: a line centred in the box's content region, led by a small image where one
     * was resolved, in the plain text colour - for a line stating something about the hovered system as
     * a whole rather than entering it in a list. Who holds the system by decree is one, and why it holds
     * nobody is another.
     *
     * <p>Centred rather than laid in the columns because the line speaks for the whole box: a line
     * indented under the title, or opening at the content edge the listed lines below open at, reads as
     * the first entry of a list it is not part of. Its crest travels as a run of the line for the reason
     * every mark in the box does - it belongs to the sentence rather than to a column - and centring is
     * what a line clear of the table is then free to do.
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
     * Builds the line naming a block: flush at the box's left content edge, in the highlight colour and
     * carrying neither mark nor value.
     *
     * <p>Those two absences are what tells a heading from its own entries, since every line in the box
     * opens at that same edge: drawn in the entries' own bright it would be told apart by lacking a mark
     * alone, and a block whose heading reads as one of its entries is a flat list of equals with a stray
     * line on top.
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
     * <p>Whatever mark the line leads with rides inside its label at either tier, so a line's indent is
     * the only thing that says how deep it sits and a mark never anchors to a column the indent has
     * already left.
     *
     * @param line  the thing being listed
     * @param level where beneath the block the line was found, and how far under the box's own voice it
     *              speaks
     * @return the row, ready to add to a block
     */
    static TooltipRow buildListedRow(CellTooltipEntryLine line, CellTooltipEntryLevel level) {
        if (level.isListedInItsOwnRight()) {
            return buildEntryRow(line, level);
        }
        return buildMemberRow(line, level);
    }

    /**
     * Builds an entry line: at no indent of its own, its label bright and its value in the highlight
     * colour, so one of the things a block lists reads as being listed rather than as part of whatever
     * is listed beneath it.
     *
     * @param line  what the block lists there
     * @param level where the line was found; a block's own line speaks in the box's voice
     * @return the row, ready to add to a block
     */
    private static TooltipRow buildEntryRow(
            CellTooltipEntryLine line,
            CellTooltipEntryLevel level) {

        return finishListedRow(
            openLabel(line, StarsectorUiColour.VANILLA_PLAYER_BRIGHT.resolve()),
            line,
            level,
            StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve());
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
     * @param line  one of the things the line above carries
     * @param level where beneath the block the line was found, and how far under the box's own voice it
     *              speaks; at least one step in
     * @return the row, ready to add to a block
     */
    private static TooltipRow buildMemberRow(
            CellTooltipEntryLine line,
            CellTooltipEntryLevel level) {

        var textColour = StarsectorUiColour.VANILLA_TEXT.resolve();

        return finishListedRow(
            openLabel(line, textColour).indentsBy(level.indentDepth() * MEMBER_INDENT),
            line,
            level,
            textColour);
    }

    // Finishes a listed line once its label is open: its number, where the line sits and how loudly it
    // speaks, then the place, the remark and the status it runs on into.
    //
    // Shared by both tiers rather than spelled at each, because everything here is the same at either -
    // which leaves the two shapes differing in exactly what they are meant to differ in, the colours
    // they speak in and the indent they sit at. Spelled twice, a part added to a line would eventually
    // be added to one tier and not the other.
    private static TooltipRow finishListedRow(
            TooltipRow.TableRow row,
            CellTooltipEntryLine line,
            CellTooltipEntryLevel level,
            Color valueColour) {

        return appendQualifier(
            appendNote(
                appendIndex(
                    placeRow(appendValue(row, line, valueColour), level),
                    line),
                line),
            line);
    }

    // Opens a listed line: on the mark it leads with where it carries one, its name following as the
    // next run of the same sentence, and on the name alone otherwise.
    //
    // The mark is a run rather than a leading column because it belongs to the subject of the line it
    // sits on, at whatever indent that line landed at. Charged to a column it would draw in the gutter
    // the shallowest marked line in the box widened, well left of the name it prefixes, and every
    // markless line of the same breakdown would open past a gutter none of them can fill.
    //
    // A line carrying no mark opens on its words rather than on an image run with nothing to load, the
    // same absence rule the banner shape holds to.
    //
    // A mark that is a shorthand for the name beside it is drawn in that name's own colour, so the two
    // read as one thing. Left in the colours its asset authored it would be the loudest run on a line
    // whose meaning is in the words - an icon coloured to carry across the sector map arrives here far
    // brighter than the plain text of the account it is sitting in. A crest says otherwise and keeps
    // its own pixels, being a picture of a thing rather than a shorthand for it.
    private static TooltipRow.TableRow openLabel(CellTooltipEntryLine line, Color labelColour) {
        var lineColour = resolveLabelColour(line, labelColour);
        var labelSpan = new TextSpan(line.labelText(), lineColour);

        if (!line.hasMark()) {
            return TooltipRow.createRow(labelSpan);
        }
        return TooltipRow
            .createRow(resolveMarkSpan(line.mark(), lineColour))
            .continuesWith(labelSpan);
    }

    // The run a mark is drawn as: tinted to the line's own colour where the mark stands in for the
    // name, and untinted - drawn as its asset authored it - where it does not.
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

        // A number an account recorded for the line rather than one the line achieved reads in that
        // same quiet shade. Drawn as loudly as the numbers around it, such a value invites the reader
        // to compare it with them - which is the one thing it cannot be compared with.
        var valueSpan = new TextSpan(
            line.valueText(),
            line.isValueUncounted() ? StarsectorUiColour.VANILLA_GRAY.resolve() : valueColour);

        if (!KmlibStrings.hasText(line.valueWorkingText())) {
            return row.carriesValue(valueSpan);
        }
        return row.carriesValueRuns(List.of(
            new TextSpan(line.valueWorkingText(), StarsectorUiColour.VANILLA_GRAY.resolve()),
            valueSpan));
    }

    // Where a line sits across the box and how loudly it speaks.
    //
    // Every listed line opens at the content edge, because a mark rides in the label and no line here
    // fills a leading column: a gutter nothing fills has nothing to align to, and reads as the whole
    // block being indented under its own heading. Where a line does sit is then its indent alone, which
    // is the one number a reader can follow down a breakdown.
    //
    // The subordination is stated from the level rather than from the indent, so a line set in as a
    // peer - a faction inside the alliance naming it - stays as loud as the line it sits under, while
    // the account beneath either of them quietens by the same step whichever it hangs from.
    //
    // Applied at every tier through one helper, so an entry and the members beneath it cannot start
    // from different columns or read the same level two ways.
    private static TooltipRow.TableRow placeRow(
            TooltipRow.TableRow row,
            CellTooltipEntryLevel level) {

        return row
            .subordinatedAt(level.subordinationLevel())
            .clearsCrestColumn();
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

    // Runs a line on into where the thing on it falls in its ordering. Laid before any qualifier,
    // because it identifies the line rather than saying something about it: the eye scanning names
    // meets it as part of the name, and the gold run after it stays what the line calls out.
    //
    // Quiet while the place settled nothing, and in vanilla's own positive or negative shade once it
    // did. That is the moment the number stops being a label: two lines equal on everything else are
    // parted by it alone, so the reader looking for why one beat the other should find the answer
    // rather than work out that the smaller number wins.
    //
    // A capability of the vocabulary rather than a decision it makes - a line states a place only
    // where the thing on it belongs to an ordering a reader has some use for, which is the resolver's
    // to know. Applied at every tier through one helper, for the reason the two runs around it are.
    private static TooltipRow.TableRow appendIndex(
            TooltipRow.TableRow row,
            CellTooltipEntryLine line) {

        if (line.indexPlace() == null) {
            return row;
        }
        return row.continuesWith(new TextSpan(
            line.indexPlace().text(),
            resolveIndexColour(line.indexPlace().outcome())));
    }

    // The shade a place reads in, by what it decided. Settled here rather than at whatever resolved
    // the outcome, so two layers marking a decided ordering cannot mark it in two different greens.
    private static Color resolveIndexColour(CellTooltipIndexOutcome outcome) {
        return switch (outcome) {
            case WON -> StarsectorUiColour.VANILLA_HIGHLIGHT_GREEN.resolve();
            case LOST -> StarsectorUiColour.VANILLA_HIGHLIGHT_RED.resolve();
            case UNCONTESTED -> StarsectorUiColour.VANILLA_GRAY.resolve();
        };
    }

    // Runs a line on into whatever it remarks about the thing on it. Laid after the place and before
    // any qualifier, so a reader meets the line's identity, then what the box has to say about its own
    // account of it, then what the box has found - each in the shade that says which it is.
    //
    // In the quiet shade, and that is the whole of what parts it from the gold run after it. A remark
    // about how current the account is would be read as a finding drawn in gold, which invites the
    // reader to weigh it against the numbers on the line rather than against the line's standing.
    //
    // Applied at every tier through one helper, for the reason the runs around it are.
    private static TooltipRow.TableRow appendNote(
            TooltipRow.TableRow row,
            CellTooltipEntryLine line) {

        if (!KmlibStrings.hasText(line.noteText())) {
            return row;
        }
        return row.continuesWith(new TextSpan(
            line.noteText(),
            StarsectorUiColour.VANILLA_GRAY.resolve()));
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
