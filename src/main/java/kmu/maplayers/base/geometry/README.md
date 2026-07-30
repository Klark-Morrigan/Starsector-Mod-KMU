# Cell geometry (`base.geometry`)

The shapes a map layer is drawn out of. Every system the map draws gets one
cell - its patch of hyperspace - and every cell knows what lies across each of its
edges. Fills, borders, labels, and hover all read that one structure, so what the
player sees as a territory is a set of cells that agreed on where their shared
edges are.

Nothing here knows who owns anything, or that ownership is what a layer paints at
all. Whatever divides the map arrives as an opaque *grouping key* per system, so
the same geometry serves a map grouped by faction, by alliance, by claim, or by
anything a future layer partitions the sector on.

Part of [the map-layer framework](../../README.md); see the
[mod README](../../../../../../../README.md) for project context.

## Index

- [Cells and edges](#cells-and-edges)
- [From edges to territory](#from-edges-to-territory)
- [Clusters](#clusters)
- [Reading a point back](#reading-a-point-back)
- [Why the cells are cached, not rebuilt](#why-the-cells-are-cached-not-rebuilt)

## Cells and edges

The cells are a Voronoi partition of the drawn systems: each system holds the
space closer to it than to any other.
[`CellGeometryCache`](CellGeometryCache.java) builds and holds
them, keyed by system id.

A cell is kept as a list of [`CellEdge`](CellEdge.java) rather than a bare polygon,
because the edge list is also the adjacency graph. Each edge is tagged with an
[`EdgeTarget`](EdgeTarget.java) naming what is across it, of which there are
exactly three: another system's cell, the cell's own outer reach bound, or more of
the same territory (a cut interior to one owner's ground with no star beyond it).
Naming all three keeps the far side a stated fact instead of something inferred
from a null.

A cell is not a system. Most cells are one star's own ground, but a cell can be
ground held without a star in it, or space belonging to nobody - so
[`CellGrouping`](CellGrouping.java) makes "who holds this cell" two lookups: the
cell resolves to the system it draws as, and that system resolves to a grouping
key.

## From edges to territory

[`EdgeClassifier`](EdgeClassifier.java) turns an edge into an
[`EdgeClass`](EdgeClass.java) by comparing the grouping keys on its two sides:
the same non-null key on both makes an interior seam; a key on exactly one side
makes an open frontier (owned ground facing an uncontested dead or decivilised
star); anything else is a boundary.

[`CellShaper`](CellShaper.java) then shapes each raw cell into what is actually
painted. A seam edge stays on the true cell border, so two same-key cells meet
exactly and fuse with no visible line between them. Every other edge is pulled
inward, which is what gives a cluster its uniform border channel against whatever
is outside it. The result is a [`ShapedCell`](ShapedCell.java): a fill polygon plus
a parallel flag per edge saying which edges are national borders.

## Clusters

[`SystemClusters`](SystemClusters.java) groups systems into contiguous same-key
clusters - the connected components of the adjacency graph. That is stricter than
"everyone who shares a key": a faction's homeland and its far-flung colony come
back as two clusters, each the natural home for its own label.

[`SystemClusterBorders`](SystemClusterBorders.java) works at cluster scale rather
than cell scale: it gathers every boundary edge of a cluster's cells, drops the
internal seams, and chains what remains into the closed rings that outline the
whole territory.

## Reading a point back

[`CellHitTest`](CellHitTest.java) resolves a world point to the system whose cell
covers it - the cursor-to-system read behind hover. It tests against the *shaped*
fill polygon, not the raw cell, so the answer matches the painted territory rather
than the mathematical partition underneath it.

[`SystemClusterIndex`](SystemClusterIndex.java) answers the other direction: given
one system, which whole cluster does it belong to. It is built by indexing the
clustering the map already ran, so a highlighted region is exactly the region that
carries one name.

## Why the cells are cached, not rebuilt

A full partition is O(n^3) in the number of drawn systems, and the map repaints
every frame. Since hyperspace positions are fixed for the life of a save, the cells
are built once and reconciled by diffing the drawn set - adding or removing a site
provably only disturbs cells within twice the cell radius of it.

Systems that move are the exception: a system that rewrites its own position has no
stable cell, and letting it clip its neighbours would drag their borders with it,
so it is dropped from the site set rather than chased.

The cache holds *raw* cells. Everything ownership-dependent - the inset, the seams,
the clusters - is derived downstream, which is why a colony changing hands costs a
re-shape and never a re-partition. The full invalidation model is in
[the caching notes](../../../../../../../docs/dev/caching.md).
