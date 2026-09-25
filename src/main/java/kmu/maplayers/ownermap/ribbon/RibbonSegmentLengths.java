package kmu.maplayers.ownermap.ribbon;

/**
 * How far the ribbon's two kinds of run go, in ribbon widths: the segment one market draws,
 * and the interjection parting two runs - two markets of one bloc, or one bloc's whole run
 * from the next bloc's.
 *
 * <p>Paired in a value of their own so two lengths of the same type can never be handed over
 * the wrong way round, and so the proportion between them is set in one place. That
 * proportion is what makes a bloc's run read as several markets rather than as one long
 * band, so it is a single authored decision rather than two loose numbers.
 *
 * <p>Whole widths rather than fractions: a plan is a count of holdings expressed as a length,
 * and how large a width is in the world is settled at draw time against the cell's own
 * perimeter, which is where a finer size belongs.
 *
 * @param marketLengthUnits       how far one market's segment runs
 * @param interjectionLengthUnits how far a parting runs, whether it stands between two of one
 *                                bloc's markets or closes that bloc's run at a handover; one
 *                                length for both, since two would read as a statement about the
 *                                blocs a divider parts
 */
public record RibbonSegmentLengths(
    int marketLengthUnits,
    int interjectionLengthUnits) {
}
