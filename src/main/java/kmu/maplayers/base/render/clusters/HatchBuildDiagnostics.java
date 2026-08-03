package kmu.maplayers.base.render.clusters;

import com.fs.starfarer.api.Global;

import kmlib.opengl.GlVertexRuns;
import kmlib.profiling.Timings;

import kmu.maplayers.base.theme.HatchStyle;

import org.apache.log4j.Logger;

/**
 * Reports what a rebuild's hatch was cut to and what cutting it found, as the two kinds of line a
 * capture is read from: the settings in force, once, and then one line per body carrying only what
 * that body's own geometry decided.
 *
 * <p>Split that way because the settings do not vary across the bodies of one rebuild. Restating
 * the tolerance and the stroke on every body's line makes a reader compare repeated values to
 * notice they never differ, and buries the few numbers that do differ among them. Stated once
 * ahead of the bodies, the specification is the heading its rows are read under - including their
 * units, which then need saying only in the one place.
 */
public final class HatchBuildDiagnostics {

    // The unit every gap and the tolerance are expressed in, said once on the specification line
    // so no reading below has to carry it.
    private static final String GAP_UNITS_NOTE = " (tolerance and gaps as fractions of spacing)";

    private static final Logger LOG = Global.getLogger(HatchBuildDiagnostics.class);

    // Reports only; never instantiated.
    private HatchBuildDiagnostics() {
    }

    /**
     * Records the hatch settings this rebuild will cut to, ahead of cutting anything.
     *
     * @param hatch the sector-wide hatch the rebuild resolved
     */
    public static void logHatchSpecification(HatchStyle hatch) {
        LOG.debug(describeHatchSpecification(hatch));
    }

    /**
     * @return an observer recording each body's segment count, the cost of cutting it, and how its
     *         joins closed
     */
    public static HatchRunObserver createRunObserver() {
        return timedHatchRun -> LOG.debug(describeHatchRun(timedHatchRun));
    }

    // The specification line. Both halves of the style are named: a capture is attributed to the
    // settings that produced it, and either half alone leaves it half attributed. The stroke prints
    // as itself rather than as fields picked out here, so a stroke of a kind this class has never
    // heard of still says what it is.
    static String describeHatchSpecification(HatchStyle hatch) {
        return "Cluster hatch specification; spacing=" + hatch.spacing()
            + " angleRadians=" + hatch.angleRadians()
            + " tolerance=" + hatch.joinToleranceFraction()
            + " stroke=" + hatch.stroke()
            + GAP_UNITS_NOTE;
    }

    // One body's row: how many primitives its ground came back as, what cutting them cost, and the
    // readings that settle whether the join tolerance is load-bearing. The widest gap it closed is
    // the reach it had to have; the narrowest one left open is what the next notch up would start
    // joining, and is the only reading a run at zero tolerance can give, since nothing can be
    // tolerated there.
    //
    // The cost is stated in microseconds because a cut is a sub-pass - milliseconds at two decimals
    // round most of them to zero, which compares against nothing.
    static String describeHatchRun(TimedHatchRun timedHatchRun) {
        var joins = timedHatchRun.hatchRun().joins();
        return "Cluster hatch built; segments="
            + timedHatchRun.hatchRun().segments().length / GlVertexRuns.FLOATS_PER_SEGMENT
            + " took=" + Timings.formatMicros(timedHatchRun.elapsedNanos())
            + " exactJoins=" + joins.exactJoinCount()
            + " toleranceJoins=" + joins.toleranceJoinCount()
            + " overlappingJoins=" + joins.overlappingJoinCount()
            + " widestClosedGap=" + joins.widestToleranceGapFraction()
            + " narrowestOpenGap=" + joins.narrowestOpenGapFraction();
    }
}
