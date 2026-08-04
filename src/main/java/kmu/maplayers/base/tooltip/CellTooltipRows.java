package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

/**
 * The line vocabulary a cell tooltip's content is written in: a top-tier row that opens a block, a
 * nested row that belongs to the one above it, a banner row centred across the box to state something
 * about the hovered system as a whole, and the qualifier run any of them may end on. Each shape fixes
 * its own indent and colours, so what a layer says is the only thing that varies between two hover
 * boxes.
 *
 * <p>The split that matters is the first two against the third: a top-tier and a nested row are entries
 * in the box's table, lining up against the crest gutter and the value column, while a banner has left
 * that table to speak for the box. So the choice of builder is a statement about whether a line is one
 * of the findings or a verdict over all of them.
 *
 * <p>Held apart from {@link SystemCellTooltip} because the two answer different questions - that class
 * decides how the box is framed, these decide how one line inside it reads - and because a body is
 * rarely built in one place: a resolver that contributes a single line reaches the same vocabulary as
 * the tooltip composing them, without either having to be the other's subclass.
 */
public final class CellTooltipRows {

    /**
     * The value of a row that carries no number, such as a status or section line. Rendered as-is it
     * draws nothing and measures zero width, so the value column collapses for that row.
     */
    public static final String NO_SCORE = "";

    // The inset a nested row draws at, so it reads as belonging to the line above it; a top-tier row
    // sits flush at zero. Only the two row builders apply it, which is what keeps the two tiers a
    // choice of builder at the call site rather than an indent every caller has to remember.
    private static final float MEMBER_INDENT = 14f;

    private CellTooltipRows() {
    }

    /**
     * Builds a top-tier row: flush left, its label bright and its value in the highlight colour, so a
     * section heading, a group header, or the system name reads as opening a block rather than sitting
     * inside one.
     *
     * @param crestSpritePath the leading crest's texture path, or null for a crestless row
     * @param text            the row's label
     * @param value           the right-aligned value, or {@link #NO_SCORE} for a row carrying none
     * @return the row, ready to add to a body
     */
    public static TooltipRow.TableRow buildTopTierRow(
            String crestSpritePath,
            String text,
            String value) {

        return TooltipRow
            .createRow(new TextSpan(text, StarsectorUiColour.VANILLA_PLAYER_BRIGHT.resolve()))
            .carriesCrest(crestSpritePath)
            .carriesValue(new TextSpan(value, StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve()));
    }

    /**
     * Builds a nested row: indented under the top-tier row above it and drawn in the plain text
     * colour, so a section's entry or a group's member reads as belonging to that block.
     *
     * @param crestSpritePath the leading crest's texture path, or null for a crestless row
     * @param text            the row's label
     * @param value           the right-aligned value, or {@link #NO_SCORE} for a row carrying none
     * @return the row, ready to add to a body
     */
    public static TooltipRow.TableRow buildNestedRow(
            String crestSpritePath,
            String text,
            String value) {

        var textColour = StarsectorUiColour.VANILLA_TEXT.resolve();
        return TooltipRow
            .createRow(new TextSpan(text, textColour))
            .carriesCrest(crestSpritePath)
            .carriesValue(new TextSpan(value, textColour))
            .indentsBy(MEMBER_INDENT);
    }

    /**
     * Builds the run a line ends on to call something out - a status or flag stated on the line it
     * qualifies rather than on a line of its own, in the highlight colour. Hand it to
     * {@code TooltipRow.continuesWith} on whichever line it qualifies. One place decides that such a
     * qualifier reads gold, so two layers calling out different facts still call them out alike.
     *
     * @param text the qualifier continuing a line's label
     * @return the run, ready to continue a line
     */
    public static TextSpan buildQualifierSpan(String text) {
        return new TextSpan(text, StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve());
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
}
