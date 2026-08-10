package kmu.maplayers.base.tooltip;

import java.util.Objects;

/**
 * The mark a cell-tooltip line opens on: the texture it draws and whether that texture speaks in the
 * line's own colour or in the colours its asset authored.
 *
 * <p>The two travel as one value because neither answers on its own, and because a line that held them
 * apart could hold a colouring for a mark it does not have. Absence is a null mark rather than a mark
 * with nothing to load, so "no mark" is one state instead of a path and a colouring that have to agree.
 *
 * <p>Which of the two colourings a mark takes cannot be read off the texture - the same artwork can be
 * a subject on one line and a shorthand for a name on the next - so whatever composes the line says it
 * here, once, where the path is named.
 *
 * @param spritePath     the mark's texture path, never null
 * @param isInLineColour whether the texture is drawn in the colour of the line it leads, rather than in
 *                       the colours its asset authored. A mark that is a picture of something in its
 *                       own right keeps its pixels; one that is a shorthand for the name beside it
 *                       reads with that name, an asset coloured for another surface being the loudest
 *                       thing on a line whose meaning is in the words
 */
public record CellTooltipMark(
    String spritePath,
    boolean isInLineColour) {

    // The two colourings, named so each factory below reads as the statement it is rather than as a
    // bare true or false a caller has to match against the component order.
    private static final boolean IS_AS_AUTHORED = false;
    private static final boolean IS_IN_LINE_COLOUR = true;

    /**
     * Rejects a null path, since a line showing no mark carries no mark at all rather than one with
     * nothing to load. A null otherwise surfaces at the texture lookup inside a draw call, well past
     * the point that could say which line was meant.
     */
    public CellTooltipMark {
        Objects.requireNonNull(spritePath, "spritePath");
    }

    /**
     * Resolves a mark drawn as its asset authored it - a picture of something in its own right, whose
     * colours are in its own pixels. A faction crest is the usual one.
     *
     * @param spritePath the mark's texture path, or null where the game supplied none
     * @return the mark, or null where there is no image to lead with
     */
    public static CellTooltipMark resolveMarkAsAuthored(String spritePath) {
        return resolveMark(spritePath, IS_AS_AUTHORED);
    }

    /**
     * Resolves a mark drawn in the colour of the line it leads - a shorthand for the name beside it
     * rather than a picture of anything, such as the glyph the sector map marks a colony by.
     *
     * <p>It names no colour: what a line speaks in is the block's to decide and differs by the tier
     * the line lands at, so a mark states only that it follows its name.
     *
     * @param spritePath the mark's texture path, or null where the game supplied none
     * @return the mark, or null where there is no image to lead with
     */
    public static CellTooltipMark resolveMarkInLineColour(String spritePath) {
        return resolveMark(spritePath, IS_IN_LINE_COLOUR);
    }

    // A path the game never supplied answers no mark rather than a mark with nothing to load, which
    // is what lets a caller resolve a mark it may not have without branching first - and is the same
    // absence a line showing no mark carries. Both factories answer it the one way, so a crest that
    // failed to resolve and a colony the map marks with no glyph reach a line identically.
    private static CellTooltipMark resolveMark(String spritePath, boolean isInLineColour) {
        if (spritePath == null) {
            return null;
        }
        return new CellTooltipMark(spritePath, isInLineColour);
    }
}
