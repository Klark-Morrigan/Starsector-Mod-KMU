package kmu.maplayers.base.profiling;

import kmlib.profiling.ProfileCounter;

/**
 * What a map rebuild counts as it goes: the quantities its durations have to be read against.
 *
 * <p>A row's "40ms" means nothing until it is "40ms over 2100 cells", so the number a duration is
 * judged by is accumulated beside it rather than printed in prose. Published here so the stages
 * that count one quantity between them - the cells the geometry recut and the cells the bands were
 * baked over are the same unit of work - land in one column rather than in two spelled alike.
 *
 * <p>Its own package rather than beside {@link kmu.maplayers.base.render.MapFrameSections},
 * because the stages that count are on both sides of the render: the cell cut and the label mint
 * run before anything is painted, and geometry importing the render package to name a counter
 * would invert the one direction those two are related in.
 *
 * <p>Deliberately few. Every counter anything in a capture touches adds a group of columns to
 * every reading of it, so what earns one is a volume of work worth dividing a duration by. A
 * reading that is really a detail of one call - which knobs a sweep ran under, how many of the
 * cells it was handed came back with anything to draw - belongs in that call's tag, where it costs
 * the report no width and still reaches the log line the call writes.
 *
 * <p>What the sector holds is counted by the library's own walkers rather than here, so a stage's
 * row already says how many systems and markets it read without this naming any of them.
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
