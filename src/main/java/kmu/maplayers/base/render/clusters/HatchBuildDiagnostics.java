package kmu.maplayers.base.render.clusters;

import com.fs.starfarer.api.Global;

import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.hatch.HatchRun;
import kmlib.profiling.ProfileScope;
import kmlib.profiling.ProfileSection;

import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.profiling.RebuildStepTerms;
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
 *
 * <p>The per-body reading rides on the scope the cut is measured under rather than on a line of
 * its own: what a cut cost and what it produced are then one record, in the report as well as in
 * the log, and the duration a reading is judged against is the profiler's own rather than a second
 * clock read beside it. The specification stays a line, being a statement about the rebuild rather
 * than a measurement of one call.
 */
public final class HatchBuildDiagnostics {

    // The unit every gap and the tolerance are expressed in, said once on the specification line
    // so no reading below has to carry it.
    private static final String GAP_UNITS_NOTE = " (tolerance and gaps as fractions of spacing)";

    private static final Logger LOG = Global.getLogger(HatchBuildDiagnostics.class);

    /**
     * The section one body's hatch cut is measured under.
     *
     * <p>Every cut writes its line: it happens on a rebuild rather than per frame, and a body that
     * cut nothing is as much a part of the trace as one that cut thousands of strokes - the count
     * beside the duration is what tells the two apart.
     */
    public static final ProfileSection CUT_HATCH_SECTION = ProfileSection.registerSection(
        "mapLayer.cutHatch", RebuildStepTerms.LOGGED_EVERY_CALL);

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
     * Records one body's cut on the scope it was cut under: how many strokes it came back as, and
     * the readings that settle whether the join tolerance is load-bearing.
     *
     * @param cutScope the open scope the cut was measured under
     * @param hatchRun the segments cut for this body and how its joins closed
     */
    public static void reportHatchRun(ProfileScope cutScope, HatchRun hatchRun) {

        var segments = hatchRun.segments().length / GlVertexRuns.FLOATS_PER_SEGMENT;

        cutScope.addCount(MapBuildCounters.HATCH_SEGMENTS, segments);

        // A body of a splitting owner that holds no hatched member cuts nothing, and there are no
        // joins to read off a run that has no segments to join.
        if (segments > 0) {
            cutScope.tagCall(describeHatchJoins(hatchRun));
        }
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

    // What one body's cut found: the readings that settle whether the join tolerance is
    // load-bearing. The widest gap it closed is the reach it had to have; the narrowest one left
    // open is what the next notch up would start joining, and is the only reading a run at zero
    // tolerance can give, since nothing can be tolerated there.
    //
    // Named on the call rather than counted, because none of it is a volume of work a duration is
    // divided by - and two of the four are fractions, which a counter cannot hold at all.
    static String describeHatchJoins(HatchRun hatchRun) {

        var joins = hatchRun.joins();

        return "exactJoins=" + joins.exactJoinCount()
            + " toleranceJoins=" + joins.toleranceJoinCount()
            + " overlappingJoins=" + joins.overlappingJoinCount()
            + " widestClosedGap=" + joins.widestToleranceGapFraction()
            + " narrowestOpenGap=" + joins.narrowestOpenGapFraction();
    }
}
