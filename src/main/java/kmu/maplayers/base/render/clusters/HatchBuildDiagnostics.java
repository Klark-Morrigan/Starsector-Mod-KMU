package kmu.maplayers.base.render.clusters;

import com.fs.starfarer.api.Global;

import kmlib.opengl.GlVertexRuns;
import kmlib.profiling.Timings;

import kmu.maplayers.base.theme.HatchStyle;

import org.apache.log4j.Logger;

import java.util.function.Function;

/**
 * Reports what a rebuild's hatch was cut to and what cutting it found, as the two kinds of line a
 * capture is read from: the settings in force, once, and then one line per body carrying only what
 * that body's own geometry decided.
 *
 * <p>Split that way because the settings do not vary across the bodies of one rebuild. Restating
 * the joining, the tolerance and the stroke on every body's line makes a reader compare repeated
 * values to notice they never differ, and buries the few numbers that do differ among them. Stated
 * once ahead of the bodies, the specification is the heading its rows are read under - including
 * their units, which then need saying only in the one place.
 *
 * <p>Which readings a body's line carries is settled here too, when the observer is picked, rather
 * than tested per body while one is being written. A joining that merges nothing reads no tolerance
 * and reports a tally of structural zeroes, so a line carrying them would show six numbers that are
 * properties of the joining rather than measurements of the hatch - and would read exactly like a
 * merge that ran and found nothing to close.
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
     * Picks what to extract from each body's hatch, given the settings it will be cut to.
     *
     * @param hatch the sector-wide hatch the rebuild resolved
     * @return an observer reporting a body's segment count, plus its join readings where the
     *         joining in force makes joins at all
     */
    public static HatchRunObserver selectRunObserverFor(HatchStyle hatch) {
        var describeRun = selectRunDescriberFor(hatch);
        return hatchRun -> LOG.debug(describeRun.apply(hatchRun));
    }

    // Which readings a body's row carries, as the function that produces it. Split from the
    // observer above so that what is extracted is separable from where it is put: the choice is
    // the part with a rule behind it, and a logger is the part that cannot be read back.
    static Function<TimedHatchRun, String> selectRunDescriberFor(HatchStyle hatch) {
        if (hatch.joining().isMerging()) {
            return HatchBuildDiagnostics::describeJoinedHatchRun;
        }
        return HatchBuildDiagnostics::describeHatchRun;
    }

    // The specification line. Both halves of the style are named: a capture is attributed to the
    // settings that produced it, and either half alone leaves it half attributed. The stroke prints
    // as itself rather than as fields picked out here, so a stroke of a kind this class has never
    // heard of still says what it is.
    static String describeHatchSpecification(HatchStyle hatch) {
        var specification = "Cluster hatch specification; joining=" + hatch.joining()
            + " spacing=" + hatch.spacing()
            + " angleRadians=" + hatch.angleRadians()
            + " stroke=" + hatch.stroke();

        if (!hatch.joining().isMerging()) {
            return specification;
        }
        return specification
            + " tolerance=" + hatch.joinToleranceFraction()
            + GAP_UNITS_NOTE;
    }

    // One body's line under a joining that merges: the readings that settle whether the tolerance
    // is load-bearing. The widest gap it closed is the reach it had to have; the narrowest one left
    // open is what the next notch up would start joining, and is the only reading a run at zero
    // tolerance can give, since nothing can be tolerated there.
    static String describeJoinedHatchRun(TimedHatchRun timedHatchRun) {
        var joins = timedHatchRun.hatchRun().joins();
        return describeHatchRun(timedHatchRun)
            + " exactJoins=" + joins.exactJoinCount()
            + " toleranceJoins=" + joins.toleranceJoinCount()
            + " overlappingJoins=" + joins.overlappingJoinCount()
            + " widestClosedGap=" + joins.widestToleranceGapFraction()
            + " narrowestOpenGap=" + joins.narrowestOpenGapFraction();
    }

    // One body's line under any joining: how many primitives its ground came back as, and what
    // cutting them cost. Those two are the measurement the joinings are compared on, and the cost
    // is stated in microseconds because a cut is a sub-pass - milliseconds at two decimals round
    // most of them to zero, which compares against nothing.
    static String describeHatchRun(TimedHatchRun timedHatchRun) {
        return "Cluster hatch built; segments="
            + timedHatchRun.hatchRun().segments().length / GlVertexRuns.FLOATS_PER_SEGMENT
            + " took=" + Timings.formatMicros(timedHatchRun.elapsedNanos());
    }
}
