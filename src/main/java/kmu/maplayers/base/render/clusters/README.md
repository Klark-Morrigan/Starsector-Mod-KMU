# Clusters: cells into drawable ground (`base.render.clusters`)

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
- [What is not here](#what-is-not-here)

## The vocabulary

*Owner*, *cluster*, and the rest are defined once in
[the framework vocabulary](../../../README.md#the-vocabulary), along with what each political-map
term maps to. Only the term this package owns outright is settled here: `FillSplit.FillState` is
the single definition of the three **fill states** (solid, hatched, unfilled), and everything that
hatches, tessellates, or styles ground refers to it rather than restating what a state means.

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
top of it *is* tunable, off the Dev tab.

The second `traceRings` overload opts a set of neighbours out of that channel. The edge shared with
a *coincident* neighbour insets by nothing, so a cluster traced from either side lands on the same
line and the two abut exactly - which is how one same-owner body is carved into clusters that meet
without a gap opening between them.

## Smoothing and packing

- `BorderSmoothing` - the two passes over traced loops: sanding the needle spikes and inward cusps
  too thin for rounding to fix, then rounding the corners into arcs. Each takes the
  [`BorderSmoothingStyle`](../../theme/README.md) as data rather than reading the live settings
  where it runs, so a lone cell's outline and the cluster border beside it round to one profile and
  cannot drift apart. Each pass also always does what its name says: `smoothBorderLoops` applies
  the profile's own gates in the order the passes must run, while a caller capturing every stage
  gates them itself, and both smooth identically.
- `debug/` - the diagnostic view of those two passes, for the caller that gates them itself.
  `ClusterBorderStageOverlay` holds each stage's loops apart - traced, despiked, rounded - and
  `ClusterBorderStageRenderer` strokes them base under despiked under rounded, in the three grades
  of the [`DiagnosticPalette`](../../labels/README.md) and at tapering widths, so an earlier stage
  haloes out from under the one drawn over it. A stage is filled only when its gate was on, so an
  empty one reads as "that pass did not run" rather than "it ran and changed nothing". What ground
  is traced into it stays the layer's call: nothing here builds an overlay, it only draws one.
- `VertexRuns` - flattens one shaped cell's edges of a single class (cluster border, or interior
  seam) into a GL_LINES run. The generic packing is `kmlib.opengl.GlVertexRuns`; what lives here is
  the one conversion that has to know a `ShapedCell`.
- `StyledCell` - the per-cell draw packet, sealed over the two forms a cell takes. A
  `FusedCell` fused into a cluster and so draws only the seam where it meets a sibling; a
  `LoneCell` fused with nothing, is its own cluster, and so carries its own fill and outline.
  Each form holds what that cell has and nothing else, so a pass that fills never reaches a
  fused cell and one that strokes seams never reaches a lone one. A layer's own builder decides
  which form a cell takes.

## The split fill: one border, several fills

A cluster is traced as one border whatever its members paint inside it; the fill is what varies per
member. `FillSplit` and `SplitFillBuilder` are that mechanism, split along the line between
deciding and drawing.

`FillSplit` is the pure partition - which of three states (solid, hatched, unfilled) each member
system draws in, decidable from plain id sets. Which systems land in the two non-solid sets is the
layer's call, handed in; nothing here decides it.

`SplitFillBuilder` turns the partition into triangles and hatch lines, and owns the choice of
whether a cluster splits at all or fills solid as one cluster - the common case, which pays nothing
for the machinery. Built per cluster around the context the whole fill shares (the cells, their
grouping, the trace, the smoothed loops, the hatch geometry).

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
tessellated at all: it holds ground for the border and the name and paints nothing.

## What is not here

*What* the keys mean - what a cluster stands for, why a system hatches or draws empty, what colour
any of it takes - belongs to whichever layer owns the clusters; for the one layer that paints today
that is [`politicalmap`](../../../politicalmap/README.md). *Who gets the frame at all* is the
[render surface](../README.md) one level up. The *cells and clusters* the shaping runs over, and
the channel it insets by, are [`base.geometry`](../../geometry/README.md); the *theme records* the
widths and opacities cascade from are [`base.theme`](../../theme/README.md); the *cursor's* half of
the render pass - the halo and the wash, and the seam a layer answers them through - is
`base.hover`. The low-level GL run emission is a generic helper in KMLib
(`kmlib.opengl.GlRuns`).
