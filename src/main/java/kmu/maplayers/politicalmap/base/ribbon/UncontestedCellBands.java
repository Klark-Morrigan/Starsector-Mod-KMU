package kmu.maplayers.politicalmap.base.ribbon;

import kmu.settings.KmuPoliticalMapSettings;

/**
 * What a cell nobody contests draws: whether a band is laid there at all, and how far the runs in
 * it reach.
 *
 * <p>A cell some rival holds something in draws a band for a reason no setting reaches - it is the
 * readout the whole design exists for, and the fill beneath it cannot say who else is in the
 * system. A cell held by one bloc alone is a different question: its fill has already said whose
 * the system is, so a band there answers only "how much is in it", which is a thing a player may
 * want on the map or may not. This is that second answer, and it is stated apart from the first so
 * that no switch can reach the bands that report a contest.
 *
 * <p>The shortened run is the same answer taken one step further. An uncontested system needs to
 * say only how many colonies are there, and at the authored run length a large lone holding would
 * lay more band than the contested cells the design was built around - out-shouting the very cells
 * a reader is meant to be drawn to. A market run of one width keeps such a cell a tally rather than
 * a statement.
 *
 * @param isBandDrawn    whether a cell no bloc but its own painter holds anything in draws a band
 * @param isRunShortened whether the market runs in such a band are cut to one width each, instead
 *                       of the length the player authored for contested bands; read only where a
 *                       band is drawn at all, so with the bands off it simply keeps its value
 *                       against the bands being switched back on
 */
public record UncontestedCellBands(
    boolean isBandDrawn,
    boolean isRunShortened) {

    // How far one colony's run reaches on an uncontested cell when the runs are shortened: the
    // shortest run a band can draw, since the width is the unit every length in the design is a
    // multiple of. Not a knob of its own - a second length to tune would be one more proportion a
    // player has to hold against the two that already decide how a contested band reads.
    private static final int ONE_WIDTH_PER_MARKET = 1;

    /**
     * Reads the player's live answer for the uncontested cells.
     *
     * <p>Sampled as one value at the start of a pass, like every other setting a rebuild resolves
     * under, so a switch moved mid-pass cannot leave half the sector banded under one rule and half
     * under the other.
     *
     * @return whether the uncontested cells band, and at what run length
     */
    public static UncontestedCellBands readFromLunaSettings() {
        return new UncontestedCellBands(
            KmuPoliticalMapSettings.shouldDrawPoliticalMapUncontestedRibbons(),
            KmuPoliticalMapSettings.shouldShortenPoliticalMapUncontestedRibbonRuns());
    }

    /**
     * The lengths an uncontested cell's runs are laid at, given the ones a contested cell's are.
     *
     * <p>Only the market run is answered for here. The parting keeps the player's own length, since
     * what it separates - one colony from the next - is the same statement on an uncontested cell
     * as on any other, and shortening it too would leave a tally whose ticks and gaps say nothing
     * apart.
     *
     * @param authoredLengths the lengths the player set, which a contested band is laid at
     * @return those same lengths, or the market run cut to a single width where the shortening is on
     */
    public RibbonSegmentLengths resolveRunLengths(RibbonSegmentLengths authoredLengths) {

        return isRunShortened
            ? new RibbonSegmentLengths(
                ONE_WIDTH_PER_MARKET,
                authoredLengths.interjectionLengthUnits())
            : authoredLengths;
    }
}
