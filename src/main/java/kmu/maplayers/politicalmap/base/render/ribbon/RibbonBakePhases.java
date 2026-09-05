package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.profiling.PhasedSection;
import kmlib.profiling.ProfilePhase;

/**
 * The section a bake of the presence bands is measured under, and the four separable things a bake
 * does to each cell: counting what the cell's system holds, tracing the ring its band runs along,
 * carving the names and the cell's own narrow places off that ring, and stroking what is left into
 * triangles.
 *
 * <p>Four rather than one because they grow on different axes - the trace with the cells whose ring
 * had to be walked, the carve with the cells times the names, the count with what the systems hold
 * - so one total could say a bake got slower without saying which of them did. Which axis each grows
 * on, and why, is the package README's.
 *
 * <p>Phases of one scope rather than sections of their own, so what the readout reports is what one
 * cell costs in each of them: a bake is one call and a cell is one turn of its loop, and it is the
 * turn a rebuild's cost scales with.
 */
public final class RibbonBakePhases {

    private static final String BAKE_SECTION_NAME = "politicalMap.bakeRibbons";

    // One spelling per phase, since each is named twice - once declaring the loop's steps and once
    // resolving the constant a caller marks with - and two spellings would be two steps.
    private static final String PLAN_PHASE_NAME = "plan";
    private static final String TRACE_PHASE_NAME = "trace";
    private static final String CARVE_PHASE_NAME = "carve";
    private static final String STROKE_PHASE_NAME = "stroke";

    /** The section a whole bake is measured under, whose loop runs one turn per cell. */
    public static final PhasedSection BAKE_SECTION = PhasedSection.registerPhasedSection(
        BAKE_SECTION_NAME,
        PLAN_PHASE_NAME,
        TRACE_PHASE_NAME,
        CARVE_PHASE_NAME,
        STROKE_PHASE_NAME);

    /** Counting what the cell's system holds, which walks that system's colonies. */
    public static final ProfilePhase PLAN_PHASE = BAKE_SECTION.resolvePhase(PLAN_PHASE_NAME);

    /** Reaching for the ring the band runs along, and walking it where none stands. */
    public static final ProfilePhase TRACE_PHASE = BAKE_SECTION.resolvePhase(TRACE_PHASE_NAME);

    /** Taking the names and the pinches off that ring and placing the band on what is left. */
    public static final ProfilePhase CARVE_PHASE = BAKE_SECTION.resolvePhase(CARVE_PHASE_NAME);

    /** Laying the planned runs end to end and stroking them into triangles. */
    public static final ProfilePhase STROKE_PHASE = BAKE_SECTION.resolvePhase(STROKE_PHASE_NAME);

    private RibbonBakePhases() {
    }
}
