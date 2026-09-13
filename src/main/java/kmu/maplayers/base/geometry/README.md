# Cell geometry (`base.geometry`)

The shapes a map layer is drawn out of.
Every system the map draws gets one cell -
its patch of hyperspace -
and every cell knows what lies across each of its edges.
Fills,
borders,
labels,
and hover all read that one structure,
so what the player sees as one cluster is a set of cells that agreed on
where their shared edges are.

Nothing here knows what an owner means,
or that ownership is what a layer paints at all.
Whatever divides the map arrives as an opaque *owner* per system,
so the same geometry serves a map grouped by faction,
by alliance,
by claim,
or by anything a future layer partitions the sector on.

Part of [the map-layer framework](../../README.md);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [Cells and edges](#cells-and-edges)
- [From edges to clusters](#from-edges-to-clusters)
- [Clusters](#clusters)
- [Reading a point back](#reading-a-point-back)
- [Why the cells are cached, not rebuilt](#why-the-cells-are-cached-not-rebuilt)

## Cells and edges

The cells are a Voronoi partition of the drawn systems:
each system holds the space closer to it than to any other.
[`CellGeometryCache`](CellGeometryCache.java) builds and holds them,
keyed by `SystemKey`:
a system ID is not unique,
and a partition keyed on one would cut a single cell for two systems sharing it.

A cell is kept as a list of [`CellEdge`](CellEdge.java) rather than a bare polygon,
because the edge list is also the adjacency graph.
Each edge is tagged with an [`EdgeTarget`](EdgeTarget.java) naming what is across it,
of which there are exactly three:
another system's cell,
the cell's own outer reach bound,
or more of the same cell
(a cut interior to one owner's cells with no star beyond it).
Naming all three keeps the far side a stated fact instead of something inferred from a null.

A cell is not a system.
Most cells are one star's own,
but a cell can be one an owner holds without a star in it,
or space no owner holds -
so [`CellGrouping`](CellGrouping.java) makes "who owns this cell" two lookups:
the cell resolves to the system it draws as,
and that system resolves to an owner.
Both lookups are keyed by `SystemKey`,
so two systems sharing a vanilla ID carry two cells and two owners rather than one of each.

## From edges to clusters

[`EdgeClassifier`](EdgeClassifier.java) turns an edge into an [`EdgeClass`](EdgeClass.java) by comparing the owners on its two sides:
the same non-null owner on both makes an interior seam;
an owner on exactly one side makes an open frontier (an owned cell facing unowned space);
anything else is a boundary.

[`CellShaper`](CellShaper.java) then shapes each raw cell into what is actually painted.
A seam edge stays on the true cell border,
so two same-owner cells meet exactly and fuse with no visible line between them.
Every other edge is pulled inward,
which is what gives a cluster its uniform border channel against whatever is outside it.
The result is a [`ShapedCell`](ShapedCell.java):
a fill polygon plus a parallel flag per edge saying which edges are cluster borders.

## Clusters

[`SystemClusters`](SystemClusters.java) groups systems into contiguous same-owner clusters -
the connected components of the adjacency graph.
That is stricter than "everyone who shares an owner":
one owner's main body and its far-flung outlier come back as two clusters,
each the natural home for its own label.

[`SystemClusterBorders`](SystemClusterBorders.java) works at cluster scale rather than cell scale:
it gathers every boundary edge of a cluster's cells,
drops the internal seams,
and chains what remains into the closed rings that outline the whole cluster.

## Reading a point back

[`CellHitTest`](CellHitTest.java) resolves a world point to the system whose cell covers it -
the cursor-to-system read behind hover.
It tests against the *shaped* fill polygon,
not the raw cell,
so the answer matches the painted cluster rather than the mathematical partition underneath it.

[`SystemClusterIndex`](SystemClusterIndex.java) answers the other direction:
given one system,
which whole cluster does it belong to.
It is built by indexing the clustering the map already ran,
so a highlighted cluster is exactly the cluster that carries one name.

## Why the cells are cached, not rebuilt

A full partition is O(n^3) in the number of drawn systems,
and the map repaints every frame.
Since hyperspace positions are fixed for the life of a save,
the cells are built once and reconciled by diffing the drawn set -
adding or removing a site provably only disturbs cells within twice the cell radius of it.

[`CellSeedInputs`](CellSeedInputs.java) is what the diff cannot absorb:
the frontier resolution each cell is cut with and how far it reaches into empty space.
Both are per-cell,
so a change to either invalidates every built cell rather than the ones a drawn-set change touched,
and the cache discards what it holds instead of diffing.
They travel as one value because that consequence is shared,
and because the reach doubles as the distance the diff above scans.

[`RevisedCellGeometry`](RevisedCellGeometry.java) pairs the cells with a revision naming the cut they currently hold.
Work derived from them and kept across rebuilds holds only
while the cells behind it are the ones it was derived from,
and the revision is how a later pass asks that without having kept the cells to compare.
The two travel as one value because a pair handed over disagreeing reads as permission to reuse rather than as a fault.
The obligation that comes with producing one is that the revision move on *every* recut:
a producer echoing some upstream signal meets it only
while that signal is the sole thing able to recut the cells,
which is a fact about that producer's inputs and not about the signal.

Systems that move are the exception:
a system that rewrites its own position has no stable cell,
and letting it clip its neighbours would drag their borders with it,
so it is dropped from the site set rather than chased.

The cache holds *raw* cells.
Everything ownership-dependent -
the inset,
the seams,
the clusters -
is derived downstream,
which is why a system changing its owner costs a re-shape and never a re-partition.
The full invalidation model is in [the caching notes](../../../../../../../docs/dev/caching.md).
