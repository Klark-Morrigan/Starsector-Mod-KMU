package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;
import kmu.settings.KmuPoliticalMapRibbonSettings;

/**
 * Reads how the presence bands are laid out - the sizes, and the one answer that is not a size -
 * out of the player's LunaLib settings into one {@link RibbonStyle}. The single seam those knobs
 * are fetched through, so a bake resolves them in one read of the player's answers rather than
 * fetching each wherever it happens to be wanted.
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
     * Reads every band knob as one snapshot.
     *
     * @return how the bands are laid out and drawn
     */
    public static RibbonStyle readRibbonStyle() {
        return new RibbonStyle(
            KmuPoliticalMapRibbonSettings.getPoliticalMapRibbonWidth(),
            KmuPoliticalMapRibbonSettings.getPoliticalMapRibbonInsetPad(),
            MITER_SPIKE_LIMIT,
            new RibbonSegmentLengths(
                KmuPoliticalMapRibbonSettings.getPoliticalMapRibbonSegmentLength(),
                KmuPoliticalMapRibbonSettings.getPoliticalMapRibbonInterjectionLength()),
            KmuPoliticalMapRibbonSettings.shouldAlwaysDrawPoliticalMapRibbons());
    }
}
