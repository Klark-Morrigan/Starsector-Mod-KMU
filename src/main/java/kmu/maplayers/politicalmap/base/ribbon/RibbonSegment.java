package kmu.maplayers.politicalmap.base.ribbon;

import java.awt.Color;

/**
 * One run of a cell's presence ribbon: a colour, and how far it runs measured in ribbon
 * widths.
 *
 * <p>Length is stated in widths rather than in world units because a plan is settled before
 * the cell it draws around is measured. A ribbon that would overrun its cell's perimeter
 * compresses by shrinking what one width is worth, which shortens every run at once and
 * leaves the proportions between them exactly as planned - the proportions being the whole
 * of what the band says.
 *
 * @param colour      the shade this run draws in: a bloc's bright colour for a market, its
 *                    dark colour for a parting - between two of its own markets, or closing
 *                    its whole run where another bloc's follows
 * @param lengthUnits how far the run goes, in ribbon widths
 */
public record RibbonSegment(
    Color colour,
    int lengthUnits) {
}
