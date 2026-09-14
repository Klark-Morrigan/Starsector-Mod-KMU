package kmu.maplayers.base.profiling;

import kmlib.profiling.ProfileCounter;

/**
 * What a map rebuild counts as it goes - the quantities its durations are read against - published
 * so the stages counting one quantity between them land in one column.
 *
 * <p>Deliberately few: every counter a capture touches adds a group of columns to every reading of
 * it, so what earns one is a volume of work worth dividing a duration by. A detail of one call -
 * the knobs a sweep ran under, how many of the cells handed to a bake came back banded - rides on
 * that call's tag instead. What the sector holds is counted by the library's own walkers.
 *
 * <p>Beside {@link RebuildStepTerms} rather than the frame sections, because the stages that count
 * run on both sides of the render and geometry must not import it.
 */
public final class MapBuildCounters {

    /** Cells a call recomputed, shaped or baked - the map's own unit of work. */
    public static final ProfileCounter CELLS = ProfileCounter.registerCounter("cells");

    /** Name blocks a call placed or minted, the other per-item cost a rebuild is spent on. */
    public static final ProfileCounter LABELS = ProfileCounter.registerCounter("labels");

    /**
     * Hatch strokes a call cut, as a count of line segments rather than of the floats packing
     * them.
     *
     * <p>Counted where the hatch is cut, so a rebuild's row states how many strokes it produced
     * by the roll-up alone - no stage above it sums them back out of what it built.
     */
    public static final ProfileCounter HATCH_SEGMENTS =
        ProfileCounter.registerCounter("hatchSegments");

    private MapBuildCounters() {
    }
}
