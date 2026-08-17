package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.geometry.CellSeedInputs;
import kmu.maplayers.base.visibility.MapVisibilityOverrides;

/**
 * Everything that decides the shape of a cut of the cells: the revision of the reachable set,
 * what each cell is seeded with, and which reveal toggles widen the set that seeds one.
 *
 * <p>The three are one fact because each of them recuts every cell, and a recut is a recut
 * whichever one drove it. Held apart, "have the cells changed" is three comparisons a caller has
 * to remember to make together - and a caller that makes only some of them recuts the cells
 * while still reporting the geometry as standing where it did, which is how work fitted inside
 * the old cell shapes comes to be offered to a rebuild that has since recut them.
 *
 * <p>The reachable-set revision is a framework signal, raised on a visibility or moving-set
 * change. The other two are this layer's own settings and move without it. That asymmetry is the
 * whole reason this value exists rather than the signal being trusted to name a cut on its own.
 */
record CellCutInputs(
    int geometryRevision,
    CellSeedInputs seedInputs,
    MapVisibilityOverrides visibilityOverrides) {
}
