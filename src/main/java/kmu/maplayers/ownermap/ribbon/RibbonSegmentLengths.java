package kmu.maplayers.ownermap.ribbon;

/**
 * How far the ribbon's two kinds of run go, in ribbon widths: the segment one holding draws,
 * and the interjection parting two runs - two holdings of one owner, or one owner's whole run
 * from the next owner's.
 *
 * <p>Paired in a value of their own so two lengths of the same type can never be handed over
 * the wrong way round, and so the proportion between them is set in one place. That
 * proportion is what makes an owner's run read as several holdings rather than as one long
 * band, so it is a single authored decision rather than two loose numbers.
 *
 * <p>Whole widths rather than fractions: a plan is a count of holdings expressed as a length,
 * and how large a width is in the world is settled at draw time against the cell's own
 * perimeter, which is where a finer size belongs.
 *
 * @param marketLengthUnits       how far one holding's segment runs
 * @param interjectionLengthUnits how far a parting runs, whether it stands between two of one
 *                                owner's holdings or closes that owner's run at a handover; one
 *                                length for both, since two would read as a statement about the
 *                                owners a divider parts
 */
public record RibbonSegmentLengths(
    int marketLengthUnits,
    int interjectionLengthUnits) {
}
