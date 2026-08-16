package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.profiling.Profiler;
import kmlib.profiling.Timings;

/**
 * What one bake of the presence bands spent, split by the four separable things a bake does:
 * counting what each system holds, tracing the ring its band runs along, carving the names and the
 * cell's own narrow places off that ring, and stroking what is left into triangles.
 *
 * <p>Four numbers rather than one because they grow on different axes, and one total cannot say
 * which of them moved. The trace grows with the cells - a miter inset, a fold splice, a clearance
 * walk, a winding normalisation and an arc-length walk, each cell paying its own - while the carve
 * grows with the cells times the names, since every name on the map is tested against every cell,
 * and the count grows with what the systems hold rather than with either. A sector that doubles its
 * colonies moves them by different factors, so "the bake got slower" is a different question in
 * each case.
 *
 * <p>Summed here and recorded once rather than measured per cell: {@link Profiler#record} takes an
 * elapsed count, so a pass adds into four longs and hands the profiler four numbers at the end,
 * where a profiler call per cell over the whole sector would time itself as much as the work it
 * timed. One pass is therefore one run of each section, so the readout's average is what a bake
 * costs rather than what a cell does.
 */
public final class RibbonBakeTimings {

    /** The section a whole bake is measured under, which the four phases below break down. */
    public static final String BAKE_SECTION = "politicalMap.bakeRibbons";

    // One spelling per phase, since each is named twice - once as a profiler section and once in
    // the log line a bake writes - and two spellings of one phase would read as two phases.
    private static final String PLAN_PHASE = "plan";
    private static final String TRACE_PHASE = "trace";
    private static final String CARVE_PHASE = "carve";
    private static final String STROKE_PHASE = "stroke";

    // Named off the whole-pass section so the five rows sort together in the readout.
    private static final String PLAN_SECTION = BAKE_SECTION + "." + PLAN_PHASE;
    private static final String TRACE_SECTION = BAKE_SECTION + "." + TRACE_PHASE;
    private static final String CARVE_SECTION = BAKE_SECTION + "." + CARVE_PHASE;
    private static final String STROKE_SECTION = BAKE_SECTION + "." + STROKE_PHASE;

    private long planNanos;
    private long traceNanos;
    private long carveNanos;
    private long strokeNanos;

    /**
     * Adds what counting one system's holdings cost.
     *
     * @param elapsedNanos the duration to add
     */
    public void addPlanNanos(long elapsedNanos) {
        planNanos += elapsedNanos;
    }

    /**
     * Adds what tracing one cell's ring cost.
     *
     * @param elapsedNanos the duration to add
     */
    public void addTraceNanos(long elapsedNanos) {
        traceNanos += elapsedNanos;
    }

    /**
     * Adds what carving one cell's ring and placing the band on what remained cost.
     *
     * @param elapsedNanos the duration to add
     */
    public void addCarveNanos(long elapsedNanos) {
        carveNanos += elapsedNanos;
    }

    /**
     * Adds what stroking one cell's band into triangles cost.
     *
     * @param elapsedNanos the duration to add
     */
    public void addStrokeNanos(long elapsedNanos) {
        strokeNanos += elapsedNanos;
    }

    /**
     * @return the four totals as one log fragment, in the order a cell pays them
     *
     *     <p>Beside the whole-pass duration in the bake's own log line so the split is there for
     *     a reader already watching a rebuild in the log, without the console command being
     *     opened - and so a bake that stood out says in the same breath which phase stood out.
     *     Milliseconds, matching the duration it sits beside and the profiler's own table; a bake
     *     small enough to round every phase to zero is one whose total rounds to zero too, and
     *     the accumulated rows are where such a bake shows.
     */
    public String describePhaseTotals() {

        return PLAN_PHASE + "=" + Timings.formatMillis(planNanos)
            + " " + TRACE_PHASE + "=" + Timings.formatMillis(traceNanos)
            + " " + CARVE_PHASE + "=" + Timings.formatMillis(carveNanos)
            + " " + STROKE_PHASE + "=" + Timings.formatMillis(strokeNanos);
    }

    /**
     * Records the pass's four totals, in the order a cell pays them.
     *
     * <p>A pass that spent nothing on a phase still records its zero, since a phase absent from
     * the readout and a phase that cost nothing are the same row to a reader otherwise - and a
     * bake with the bands switched off is exactly the pass whose zeroes are worth seeing.
     *
     * @param profiler the profiler the map's other timings are read from
     */
    public void recordPhaseTotals(Profiler profiler) {

        profiler.record(PLAN_SECTION, planNanos);
        profiler.record(TRACE_SECTION, traceNanos);
        profiler.record(CARVE_SECTION, carveNanos);
        profiler.record(STROKE_SECTION, strokeNanos);
    }
}
