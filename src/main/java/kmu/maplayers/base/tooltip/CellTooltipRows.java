package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

/**
 * The line vocabulary a cell tooltip's content is written in: a heading that names a block, a top-tier
 * row that enters something in it, a nested row that belongs to the one above it, a banner row centred
 * across the box to state something about the hovered system as a whole, and the qualifier run any of
 * them may end on. Each shape fixes its own placement, indent, and colours, so what a layer says is the
 * only thing that varies between two hover boxes.
 *
 * <p>The split that matters is the table shapes against the banner: a heading, a top-tier, and a nested
 * row all lay into the box's table, lining up against the crest gutter and the value column, while a
 * banner has left that table to speak for the box. So the choice of builder is a statement about whether
 * a line is one of the findings or a verdict over all of them.
 *
 * <p>Held apart from {@link SystemCellTooltip} because the two answer different questions - that class
 * decides how the box is framed, these decide how one line inside it reads - and because a body is
 * rarely built in one place: a resolver that contributes a single line reaches the same vocabulary as
 * the tooltip composing them, without either having to be the other's subclass.
 *
 * <p>The three table shapes are the block's alone ({@link CellTooltipSections}), which is why they are
 * not offered past this package: a body states what its blocks list and the block lays those lines out,
 * so a heading and an entry cannot drift into each other by two bodies each choosing a tier. What stays
 * open is what a line is composed from wherever one is authored - the banner and the qualifier run.
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
     * Builds a top-tier row: at no indent of its own, its label bright and its value in the highlight
     * colour, so an entry of a block reads as one of the things being listed rather than as part of
     * whatever is listed beneath it.
     *
     * @param crestSpritePath the leading crest's texture path, or null for a crestless row
     * @param text            the row's label
     * @param value           the right-aligned value, or {@link #NO_SCORE} for a row carrying none
     * @return the row, ready to add to a block
     */
    static TooltipRow.TableRow buildTopTierRow(
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
     * colour, so one of the things an entry is made up of reads as belonging to that entry rather than
     * as an entry of its own.
     *
     * @param crestSpritePath the leading crest's texture path, or null for a crestless row
     * @param text            the row's label
     * @param value           the right-aligned value, or {@link #NO_SCORE} for a row carrying none
     * @return the row, ready to add to a block
     */
    static TooltipRow.TableRow buildNestedRow(
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
}
