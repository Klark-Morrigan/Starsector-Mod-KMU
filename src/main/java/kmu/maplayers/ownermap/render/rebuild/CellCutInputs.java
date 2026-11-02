package kmu.maplayers.ownermap.render.rebuild;

import kmu.maplayers.base.geometry.CellSeedInputs;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

/**
 * Everything that decides the shape of a cut of the cells: the revision of the reachable set,
 * what each cell is seeded with, and the visibility rules that widen the set that seeds one.
 *
 * <p>Any of the three recuts the cells. A seed input reseeds every cell; the reachable set and the
 * rules each change which systems seed one, and the cells near those that came or went are cut
 * again. They are one fact because a recut is a recut whichever one drove it. Held apart, "have the
 * cells changed" is three comparisons a caller has to remember to make together - and a caller that
 * makes only some of them recuts the cells while still reporting the geometry as standing where it
 * did, which is how work fitted inside the old cell shapes comes to be offered to a rebuild that has
 * since recut them.
 *
 * <p>The reachable-set revision is a framework signal, raised on a visibility or moving-set
 * change. The other two are this layer's own settings and move without it. That asymmetry is the
 * whole reason this value exists rather than the signal being trusted to name a cut on its own, and
 * why a cut is numbered by counting cuts rather than by that signal.
 */
record CellCutInputs(
    int geometryRevision,
    CellSeedInputs seedInputs,
    MapVisibilityRules visibilityRules) {
}
