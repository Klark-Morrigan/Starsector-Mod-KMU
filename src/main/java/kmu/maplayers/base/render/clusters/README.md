# Clusters: cells into drawable fills (`base.render.clusters`)

The shape work every painting layer needs: turning shaped cells and opaque owner ids into the
borders, fills, and GL-ready runs a renderer emits. Same-owner cells fuse into one cluster, and a
cell that fuses with nobody is drawn as its own. Nothing here interprets an owner, so a layer's
meaning never reaches it.

Part of [the render surface](../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../README.md) for project context.

## Index

- [The vocabulary](#the-vocabulary)
- [The trace: one path to a cluster's rings](#the-trace-one-path-to-a-clusters-rings)
- [Smoothing and packing](#smoothing-and-packing)
- [The split fill: one border, several fills](#the-split-fill-one-border-several-fills)
- [Painting: the draw-list seam](#painting-the-draw-list-seam)
- [What is not here](#what-is-not-here)

## The vocabulary

*Owner*, *cluster*, and the rest are defined once in
[the framework vocabulary](../../../README.md#the-vocabulary), along with what each political-map
term maps to. Only the term this package owns outright is settled here: `FillSplit.FillState` is
the single definition of the three **fill states** (solid, hatched, unfilled), and everything that
hatches, tessellates, or styles a fill refers to it rather than restating what a state means.

One term is worth repeating because the geometry below turns on it: a **coincident** neighbour is
one whose shared edge insets by nothing, so two clusters traced against each other abut flush.

## The trace: one path to a cluster's rings

`ClusterBorderTrace` is the parameters of one border-ring trace plus the trace itself. Every ring
a layer draws, or fits a name inside, comes from here, which is what makes "a name is clipped
against the rings the player sees" true by construction rather than by two call sites agreeing. It
is agnostic to what a cluster is keyed by: the caller supplies the owners, and same-owner cells fuse.

The channel it insets every boundary edge by is not its own - it is
`base.geometry.CellShaper.BORDER_INSET_DISTANCE`, the value the cells were shaped to. It is fixed
rather than player-tunable because it is geometry and not look: the trace must inset by exactly
what the shaper cut, or the border strokes somewhere the fills do not stop. The rounding shape on
top of it *is* tunable, off the **Map - Dev** tab.

The second `traceRings` overload opts a set of neighbours out of that channel. The edge shared with
a *coincident* neighbour insets by nothing, so a cluster traced from either side lands on the same
line and the two abut exactly - which is how one same-owner body is carved into clusters that meet
without a gap opening between them.

## Smoothing and packing

- `BorderSmoothing` - the two passes over traced loops: sanding the needle spikes and inward cusps
  too thin for rounding to fix, then rounding the corners into arcs. Each takes its own half of
  the [`BorderSmoothingStyle`](../../theme/README.md) - the `SpikeSandingStyle` or the
  `CornerRoundingStyle` - as data rather than reading the live settings where it runs, so a lone
  cell's outline and the cluster border beside it round to one profile and cannot drift apart, and
  neither pass has the other's numbers in scope to be handed by mistake. Each pass also always
  does what its name says: `smoothBorderLoops` applies each half's own gate in the order the
  passes must run, while a caller capturing every stage gates them itself, and both smooth
  identically.
- `debug/` - the diagnostic view of those two passes, for the caller that gates them itself.
  `ClusterBorderStageOverlay` holds each stage's loops apart - traced, despiked, rounded -
  `ClusterBorderStageCollector` gathers them as the producer runs the passes, and
  `ClusterBorderStageRenderer` strokes them base under despiked under rounded, in the three grades
  of the [`DiagnosticPalette`](../../labels/README.md) and at tapering widths, so an earlier stage
  haloes out from under the one drawn over it. A stage is filled only when its gate was on, so an
  empty one reads as "that pass did not run" rather than "it ran and changed nothing" - which is
  why the collector takes a named call per stage rather than three lists to append into. Which
  cells are traced at all stays the layer's own call; only the shape of the capture is here.
- `VertexRuns` - flattens one shaped cell's edges of a single class (cluster border, or interior
  seam) into a GL_LINES run. The generic packing is `kmlib.opengl.GlVertexRuns`; what lives here is
  the one conversion that has to know a `ShapedCell`.
- `StyledCell` - the per-cell draw packet, sealed over the two forms a cell takes. A
  `FusedCell` fused into a cluster and so draws only the seam where it meets a sibling; a
  `LoneCell` fused with nothing, is its own cluster, and so carries its own fill and outline.
  Each form holds what that cell has and nothing else, so a pass that fills never reaches a
  fused cell and one that strokes seams never reaches a lone one. A layer's own builder decides
  which form a cell takes.
- `StyledCluster` - its cluster-level sibling: everything drawn once for one connected body
  rather than per cell - its solid fill triangles, its hatch run, its outer loop, and the enclave
  loops cut out of it. What a fused cell keeps is only its seam; the rest is here.
- `StyledClusterGroup` - everything one owner paints: the bodies it holds, plus the fill paint,
  border paint, and border width all of them share. Owners are not connected, so geometry is per
  body and paint is per owner, and the two types say so. Spelled as one record with the paint
  repeated per body, two bodies of one owner could carry different colours - a state no build can
  produce and every reader would have to distrust anyway. It also gives the emission one colour
  bind per owner however scattered that owner is.

  Recovering the bodies from a traced ring soup is `PolygonRegions.groupRingsIntoRegions` in
  KMLib, which sorts outer rings from enclaves by winding and attaches each enclave to the ring
  containing it. Nothing here re-traces to find them: the trace already emits one ring per body
  and per enclave, and the resolve that follows smoothing leaves them wound to that convention.

## The split fill: one border, several fills

A cluster is traced as one border whatever its members paint inside it; the fill is what varies per
member. `FillSplit` and `SplitFillBuilder` are that mechanism, split along the line between
deciding and drawing.

`FillSplit` is the pure partition - which of three states (solid, hatched, unfilled) each member
system draws in, decidable from plain id sets. Which systems land in the two non-solid sets is the
layer's call, handed in; nothing here decides it.

`SplitFillBuilder` owns the choice of whether a fill splits at all or fills solid - the common
case, which pays nothing for the machinery. Built per owner around the context the whole fill
shares (the cells, their grouping, the trace, the hatch geometry), and answered per owner rather
than per body, since the split's rings are the owner's and are traced once.

Each body's cut is measured under `HatchBuildDiagnostics.CUT_HATCH_SECTION`, and what the cut found
is recorded on that scope: its strokes as a count, and how its joins closed as the call's name. A
run is reduced to its segments the moment it lands in a draw record, so anything else it knows is
gone unless it is taken as the body is cut. `HatchBuildDiagnostics` owns what a cut says, and the
shape it says it in is the point - the settings once as a line, ahead of any geometry, then per
body only what that body decided. A value that cannot vary across a rebuild belongs on the heading;
restated per body it buries the few numbers that do vary among repetitions of the ones that never
do. The duration comes from the scope rather than from a clock read beside it, so what the log says
a cut cost and what the profiling report says are one measurement.

`TracedFill` is what it hands back, and where the cutting lives: sealed over the three ways an
owner fills - nothing, whole bodies, or per fill state - so only the third carries rings, and
`buildFillFor(RingRegion)` cuts one body's share of them on demand. Splitting it this way is what
keeps a body's fill tied to the body it was cut for; handing back a list of fills to be walked
alongside a list of bodies would leave them paired by position, and a body painted with its
neighbour's fill draws perfectly happily.

Each drawn state fills from its own traced rings, not from its members' individual cells. The
cluster's one owner is suffixed per state, so the tracer - which fuses same-owner cells -
traces each state as its own cluster, while every system outside keeps its real owner and the states'
outer edge therefore lands exactly where the border draws it. Each state names the others' members
as coincident, so the boundary they share insets by nothing and the fills abut on the raw cell
edge. Filling per cell instead would truncate each member's kept edges against its own inset
boundary, and two members meeting at a corner against a neighbouring cluster would pull their shared
edge back by different amounts - opening an unfilled wedge on the more-receded side.

Both drawn states are then clipped to the smoothed loops, so neither keeps the mitered corner the
rounding cut and pokes out past the line the border strokes. The unfilled state is never
tessellated at all: it holds its place for the border and the name and paints nothing.

## Painting: the draw-list seam

`ClusterRenderer` is the emission: it scales world coordinates into map space and strokes and
fills the flattened runs, with the fills below the seams below the borders. It knows nothing of
settings, caches, or how any run was shaped, and the ids it walks are opaque to it.

It hands that out as two entry points - `renderFillsOnMap` and `renderBordersOnMap` - because a
caller may need to put its own drawing between the two halves, as the sector map does when it lays
its nebulae over the overlay mid-frame. Called back to back they are the single pass they used to
be; nothing here orders them, so keeping fills under borders is the caller's. Each carries the same
guard - an empty or fully faded-out overlay is skipped rather than emitted at zero alpha - so one
reached without the other still pays nothing for a frame with nothing on it.

Each pass opens its own blended pass rather than sharing one line state: the fills aliased, which
is also the baseline nothing inherits by accident; the hatch inside them at whatever quality the
theme's stroke names; the borders smoothed, because they are long continuous runs a player follows.

The fills go down in two sweeps, all the solid triangle soups and then every hatch run, rather than
both runs per owner. Hoisting the hatch above every solid fill covers nothing that was visible
before - cells are disjoint, so no owner's triangles can land on another's hatched fill - and it
buys the hatch a pass of its own, so the line state it strokes under is set once for the whole map
instead of being pushed and popped around each owner. That pass is where `HatchStroke` is
dispatched on: the theme decides which substrate the hatch reaches the screen through, and the
renderer reads only the numbers that substrate carries.

What it paints arrives through `ClusterDrawLists`, which is four reads and no more - is there
anything to paint, the sector-wide style tier, the cell records, and the cluster groups keyed by
owner. A layer's own built state satisfies it directly wherever its draw lists already are those
two maps, so nothing is copied or adapted per frame.

The seam exists because the emission is the framework's and the built state is not. Typed on one
layer's model, the only cluster painter there is would have to live in that layer's package, and a
second layer would reach it by importing the first or by copying the emission wholesale. Four reads
is what it actually uses, so four reads is what a layer has to answer.

`debug/`'s `ClusterBorderStageRenderer` is the diagnostic analogue, over its own overlay record
rather than these lists.

## What is not here

*What* the keys mean - what a cluster stands for, why a system hatches or draws empty, what colour
any of it takes - belongs to whichever layer owns the clusters; for the one layer that paints today
that is [`politicalmap`](../../../politicalmap/README.md). *Who gets the frame at all* is the
[render surface](../README.md) one level up. The *cells and clusters* the shaping runs over, and
the channel it insets by, are [`base.geometry`](../../geometry/README.md); the *theme records* the
widths and opacities cascade from are [`base.theme`](../../theme/README.md); the *cursor's* half of
the render pass - resolving a cursor pixel to a cell, and the halo and wash drawn on that answer -
is `base.hover`. The low-level GL run emission is a generic helper in KMLib
(`kmlib.opengl.GlRuns`).
