package kmu.maplayers.ownermap.ribbon;

import kmu.settings.KmuOwnerMapRibbonSettings;

/**
 * How far the runs reach in the band a cell nobody contests draws.
 *
 * <p>Every populated cell bands, so what is left to answer about an uncontested one is only how
 * loudly. A cell some rival holds something in is the readout the whole design exists for, and its
 * runs are the player's authored lengths; a cell held by one bloc alone reports a footprint its
 * fill has already named the owner of, and needs to say only how many colonies are there. At the
 * authored run length a large lone holding would lay more band than the contested cells the design
 * was built around - out-shouting the very cells a reader is meant to be drawn to. A market run of
 * one width keeps such a cell a tally rather than a statement.
 *
 * <p>Stated as its own value rather than as a second length beside the authored pair, because it
 * is a change to those lengths for one kind of cell and not a proportion of its own: a knob to
 * tune would be one more ratio a player has to hold against the two that already decide how a
 * contested band reads.
 *
 * @param isRunShortened whether the market runs on such a cell are cut to one width each, instead
 *                       of the length the player authored for contested bands
 */
public record UncontestedRibbonRuns(
    boolean isRunShortened) {

    // How far one colony's run reaches on an uncontested cell when the runs are shortened: the
    // shortest run a band can draw, since the width is the unit every length in the design is a
    // multiple of.
    private static final int ONE_WIDTH_PER_MARKET = 1;

    /**
     * Reads the player's live answer for the uncontested cells.
     *
     * <p>Sampled as one value at the start of a pass, like every other setting a rebuild resolves
     * under, so a switch moved mid-pass cannot leave half the sector banded under one rule and half
     * under the other.
     *
     * @return the run length the uncontested cells band at
     */
    public static UncontestedRibbonRuns readFromLunaSettings() {
        return new UncontestedRibbonRuns(
            KmuOwnerMapRibbonSettings.shouldShortenOwnerMapUncontestedRibbonRuns());
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
