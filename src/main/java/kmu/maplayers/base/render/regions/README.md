# Regions: cells into drawable ground (`base.render.regions`)

The shape work every painting layer needs: turning shaped cells and opaque grouping keys into the
borders, fills, and GL-ready runs a renderer emits. Same-key cells fuse into one region, and a
cell that fuses with nobody is drawn as its own. Nothing here interprets a key, so a layer's
meaning never reaches it.

Part of [the render surface](../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../README.md) for project context.

## Index

- [The trace: one path to a cluster's rings](#the-trace-one-path-to-a-clusters-rings)
- [Smoothing and packing](#smoothing-and-packing)
- [The split fill: one border, several fills](#the-split-fill-one-border-several-fills)
- [What is not here](#what-is-not-here)

## The trace: one path to a cluster's rings

`PoliticalBorderTrace` is the parameters of one border-ring trace plus the trace itself. Every ring
a layer draws, or fits a name inside, comes from here, which is what makes "a name is clipped
against the rings the player sees" true by construction rather than by two call sites agreeing. It
is agnostic to what a cluster is grouped by: the caller supplies the keys, and same-key cells fuse.

The channel it insets every boundary edge by is not its own - it is
`base.geometry.CellShaper.BORDER_INSET_DISTANCE`, the value the cells were shaped to. It is fixed
rather than player-tunable because it is geometry and not look: the trace must inset by exactly
what the shaper cut, or the border strokes somewhere the fills do not stop. The rounding shape on
top of it *is* tunable, off the Dev tab.

The second `traceRings` overload opts a set of neighbours out of that channel. The edge shared with
a *coincident* neighbour insets by nothing, so a region traced from either side lands on the same
line and the two abut exactly - which is how one same-key body is carved into regions that meet
without a gap opening between them.

## Smoothing and packing

- `BorderSmoothing` - the two passes over traced loops: sanding the needle spikes and inward cusps
  too thin for rounding to fix, then rounding the corners into arcs. Each always does what its name
  says; the on/off decision is the caller's, so a pass that keeps only the final result and one
  that captures every stage smooth identically.
- `VertexRuns` - flattens one shaped cell's edges of a single class (cluster border, or interior
  seam) into a GL_LINES run. The generic packing is `kmlib.opengl.GlVertexRuns`; what lives here is
  the one conversion that has to know a `ShapedCell`.
- `StyledCell` - the flat per-cell draw packet: fill triangles, boundary and interior edge runs,
  three paints, two widths. A layer's own builder decides what goes in it.

## The split fill: one border, several fills

A region is traced as one border whatever its members paint inside it; the fill is what varies per
member. `FillSplit` and `SplitFillBuilder` are that mechanism, split along the line between
deciding and drawing.

`FillSplit` is the pure partition - which of three states (solid, hatched, unfilled) each member
system draws in, decidable from plain id sets. Which systems land in the two non-solid sets is the
layer's call, handed in; nothing here decides it.

`SplitFillBuilder` turns the partition into triangles and hatch lines, and owns the choice of
whether a region splits at all or fills solid as one region - the common case, which pays nothing
for the machinery. Built per region around the context the whole fill shares (the cells, their
grouping, the trace, the smoothed loops, the hatch geometry).

Each drawn state fills from its own traced rings, not from its members' individual cells. The
region's one grouping key is suffixed per state, so the tracer - which fuses same-key cells -
traces each state as its own region, while every system outside keeps its real key and the states'
outer edge therefore lands exactly where the border draws it. Each state names the others' members
as coincident, so the boundary they share insets by nothing and the fills abut on the raw cell
edge. Filling per cell instead would truncate each member's kept edges against its own inset
boundary, and two members meeting at a corner against a rival would pull their shared edge back by
different amounts - opening an unfilled wedge on the more-receded side.

Both drawn states are then clipped to the smoothed loops, so neither keeps the mitered corner the
rounding cut and pokes out past the line the border strokes. The unfilled state is never
tessellated at all: it holds ground for the border and the name and paints nothing.

## What is not here

*What* the keys mean - who holds a system, which of them are contested or drawn empty, what colour
any of it takes - belongs to whichever layer owns the regions; for the one layer that paints today
that is [`politicalmap`](../../../politicalmap/README.md). *Who gets the frame at all* is the
[render surface](../README.md) one level up. The *cells and clusters* the shaping runs over, and
the channel it insets by, are [`base.geometry`](../../geometry/README.md); the *theme records* the
widths and opacities cascade from are [`base.style`](../../style/README.md); the *cursor's* half of
the render pass - the halo and the wash, and the seam a layer answers them through - is
`base.hover`. The low-level GL run emission is a generic helper in KMLib
(`kmlib.opengl.GlRuns`).
