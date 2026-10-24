package kmu.maplayers.ownermap.ribbon;

/**
 * How far the ribbon's two kinds of run go, in ribbon widths: the segment one stake draws,
 * and the interjection parting two runs - two stakes of one colour, or one colour's whole run
 * from the next.
 *
 * <p>The owner tier's rather than the band's, because only the laying rule reads them: the band is
 * handed runs that already carry their lengths, and a stake is this tier's word for what one run
 * stands for.
 *
 * <p>Paired in a value of their own so two lengths of the same type can never be handed over
 * the wrong way round, and so the proportion between them is set in one place. That
 * proportion is what makes a long run read as several stakes rather than as one long
 * band, so it is a single authored decision rather than two loose numbers.
 *
 * <p>Whole widths rather than fractions: a plan is a count of stakes expressed as a length,
 * and how large a width is in the world is settled when the band is laid, against the cell's own
 * ring, which is where a finer size belongs.
 *
 * @param stakeLengthUnits        how far one stake's segment runs
 * @param interjectionLengthUnits how far a parting runs, whether it stands between two stakes of
 *                                one colour or closes that colour's run at a handover; one length
 *                                for both, since two would read as a statement about the runs a
 *                                divider parts
 */
public record RibbonSegmentLengths(
    int stakeLengthUnits,
    int interjectionLengthUnits) {
}
