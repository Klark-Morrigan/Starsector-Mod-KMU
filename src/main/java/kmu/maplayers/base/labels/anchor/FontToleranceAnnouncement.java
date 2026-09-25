package kmu.maplayers.base.labels.anchor;

import com.fs.starfarer.api.Global;

import kmlib.math.solving.Bisection;

import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;

import org.apache.log4j.Logger;

/**
 * The debug line that states the name fit's font precision when the knob behind it moves.
 *
 * <p>The tolerance is a player setting, read once for the process whichever layer fits names with
 * it, so what has already been said about it is a fact about the process too. Held here beside the
 * fit that consumes it rather than by any one layer's fit: held per layer, two layers fitting names
 * would each restate the same knob, and the line would stop meaning "this just moved".
 */
public final class FontToleranceAnnouncement {

    private static final Logger LOG = Global.getLogger(FontToleranceAnnouncement.class);

    // The font tolerance last announced, so a knob that sits still is not restated on every fit.
    // Zero cannot come from the read - the tuning floors it above zero - so it doubles as "nothing
    // announced yet" and the first fit after the log opens labels its baseline.
    private static double lastLoggedFontTolerance;

    // Announces only; never instantiated.
    private FontToleranceAnnouncement() {
    }

    /**
     * Announces the font tolerance when it moves, not on every fit: it is a static setting, so
     * restating it per fit would only pad a line already carrying the counts that do move. A capture
     * still needs each reading attributable to the precision behind it, which one line per change
     * gives at a fraction of the noise. The halving count comes with it because the mapping is a step
     * function - neighbouring tolerances can resolve to the same count, and this is what says an
     * unmoved band-fit total is the knob doing nothing rather than the sweep failing to take.
     *
     * @param spec the tuning a fit is about to run under
     */
    public static void announceIfMoved(LabelAnchorSpecification spec) {

        var tolerance = spec.bandFit().fontHeightTolerance();

        if (tolerance == lastLoggedFontTolerance || !LOG.isDebugEnabled()) {
            return;
        }

        lastLoggedFontTolerance = tolerance;

        LOG.debug("Anchor font search precision;"
            + " tolerance=" + tolerance
            + " bisections=" + Bisection.countStepsForTolerance(
                spec.nameFit().minFontHeight(),
                spec.nameFit().maxFontHeight(),
                tolerance));
    }
}
