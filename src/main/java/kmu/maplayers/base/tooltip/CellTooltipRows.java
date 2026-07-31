package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.color.StarsectorUiColor;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.TooltipRow;

/**
 * The line vocabulary a cell tooltip's body is written in: a top-tier row that opens a block, a nested
 * row that belongs to the one above it, a standalone row stating something about the hovered system as
 * a whole, and the qualifier run any of them may end on. Each shape fixes its own indent and colours, so
 * what a body says is the only thing that varies between two layers' hover boxes.
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
                .createRow(new TextSpan(text, StarsectorUiColor.VANILLA_PLAYER_BRIGHT.resolve()))
                .carriesCrest(crestSpritePath)
                .carriesValue(new TextSpan(value, StarsectorUiColor.VANILLA_HIGHLIGHT_GOLD.resolve()));
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

        var textColour = StarsectorUiColor.VANILLA_TEXT.resolve();
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
        return new TextSpan(text, StarsectorUiColor.VANILLA_HIGHLIGHT_GOLD.resolve());
    }

    /**
     * Builds a standalone row: flush at the box's left content edge, outside the crest column, in the
     * plain text colour and carrying neither crest nor value - for a line stating something about the
     * hovered system as a whole. Flush rather than inset, since an indent would read as the line
     * belonging to an entry above it, and there is no entry for it to belong to.
     *
     * @param text the row's label
     * @return the row, ready to add to a body
     */
    public static TooltipRow.TableRow buildStandaloneRow(String text) {
        return TooltipRow
                .createRow(new TextSpan(text, StarsectorUiColor.VANILLA_TEXT.resolve()))
                .clearsCrestColumn();
    }
}
