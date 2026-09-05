package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.profiling.PhasedSection;
import kmlib.profiling.ProfilePhase;

/**
 * The section a bake of the presence bands is measured under, and the four separable things a bake
 * does to each cell: counting what the cell's system holds, tracing the ring its band runs along,
 * carving the names and the cell's own narrow places off that ring, and stroking what is left into
 * triangles.
 *
 * <p>Four rather than one because they grow on different axes, and one total cannot say which of
 * them moved. The trace grows with the cells whose ring this bake had to walk - a miter inset, a
 * fold splice, a clearance walk, a winding normalisation and an arc-length walk apiece - which is
 * every cell on a fresh build and only the re-shaped ones on a bake that follows, since a traced
 * ring is kept for as long as the shape it was traced inside stands. The carve grows with the cells
 * times the names, since every name on the map is tested against every cell, and the count grows
 * with what the systems hold rather than with either. A sector that doubles its colonies moves them
 * by different factors, so "the bake got slower" is a different question in each case.
 *
 * <p>Phases of one scope rather than sections of their own, so what the readout reports is what one
 * cell costs in each of them: a bake is one call and a cell is one turn of its loop, and it is the
 * turn a rebuild's cost actually scales with. Measuring a cell as a section of its own would cost
 * about what a cell's work costs.
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
