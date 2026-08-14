package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;
import kmu.settings.KmuPoliticalMapSettings;

/**
 * Reads the sizes the presence bands are drawn at out of the player's LunaLib settings into one
 * {@link RibbonStyle}. The single seam the band sizes are fetched through, so a rebuild that bakes
 * bands and a frame that decides whether to paint them resolve the same knobs in the same place
 * rather than each reaching for the ones it happens to need.
 *
 * <p>The proportions the whole design rests on are the player's, because they are a matter of
 * taste against a real sector: how thick a band has to be before it reads, how far a run should
 * reach relative to the parting beside it, and how deep inside a border a band sits are all
 * answers that only look right or wrong on the map. What stays authored here is the mitre limit,
 * which is not one of those - it is the angle past which a corner's mitre becomes a spike, a
 * property of stroking a polyline rather than of how the readout looks.
 */
public final class RibbonStyleReader {

    // Where a corner stops being a corner and becomes a spike. Two half-widths of overshoot is
    // about a 53-degree turn; anything sharper bevels, which on a cell ring is rare enough to be
    // the exception it is meant to be.
    private static final double MITER_SPIKE_LIMIT = 2.0;

    // Reads only settings; never instantiated.
    private RibbonStyleReader() {
    }

    /**
     * Reads every band size as one snapshot.
     *
     * @return the sizes the bands are laid out and drawn at
     */
    public static RibbonStyle readRibbonStyle() {
        return new RibbonStyle(
            KmuPoliticalMapSettings.getPoliticalMapRibbonWidth(),
            KmuPoliticalMapSettings.getPoliticalMapRibbonInsetPad(),
            MITER_SPIKE_LIMIT,
            new RibbonSegmentLengths(
                KmuPoliticalMapSettings.getPoliticalMapRibbonSegmentLength(),
                KmuPoliticalMapSettings.getPoliticalMapRibbonInterjectionLength()),
            KmuPoliticalMapSettings.getPoliticalMapRibbonMinDrawnWidth());
    }
}
