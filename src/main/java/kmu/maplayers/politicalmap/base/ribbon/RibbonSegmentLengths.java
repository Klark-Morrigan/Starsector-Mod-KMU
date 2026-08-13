package kmu.maplayers.politicalmap.base.ribbon;

/**
 * How far the ribbon's two kinds of run go, in ribbon widths: the segment one market draws,
 * and the interjection parting two markets of the same bloc.
 *
 * <p>Paired in a value of their own so two lengths of the same type can never be handed over
 * the wrong way round, and so the proportion between them is set in one place. That
 * proportion is what makes a bloc's run read as several markets rather than as one long
 * band, so it is a single authored decision rather than two loose numbers.
 *
 * <p>Whole widths rather than fractions: a plan is a count of holdings expressed as a length,
 * and how large a width is in the world is settled later against the cell's own perimeter,
 * which is where a finer size belongs.
 *
 * @param marketLengthUnits       how far one market's segment runs
 * @param interjectionLengthUnits how far the parting between two of one bloc's markets runs
 */
public record RibbonSegmentLengths(
    int marketLengthUnits,
    int interjectionLengthUnits) {
}
