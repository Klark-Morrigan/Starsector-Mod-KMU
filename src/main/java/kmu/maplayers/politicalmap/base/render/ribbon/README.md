# Presence band geometry (`render.ribbon`)

Turns a cell's planned band - the runs [`base.ribbon`](../../ribbon/README.md) laid out in band
widths - into triangles inside that cell's own ring, and draws them. Everything here is a world
size, so the whole band resolves at rebuild and a frame measures nothing about one.

Part of [the political map](../../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../README.md) for project context.

## Index

- [The three classes that build one](#the-three-classes-that-build-one)
- [Laying a band inside a ring](#laying-a-band-inside-a-ring)
- [Keeping clear of the names](#keeping-clear-of-the-names)
- [One stroke, many colours](#one-stroke-many-colours)
- [Sizes and drawing](#sizes-and-drawing)
- [What is not here](#what-is-not-here)

## The three classes that build one

Three collaborators with deliberately different jobs, since the names are close enough to be worth
stating apart:

| Class | Scope | Job |
| --- | --- | --- |
| `CellRibbonsBaker` | the pass | drives the loop over cells and writes each band back |
| `CellRibbonSource` | the pass | holds what a pass is settled from; answers one cell at a time |
| `CellRibbonBuilder` | one cell | pure geometry: ring plus plan in, `CellRibbon` out |

`CellRibbonsBaker` runs as its own pass **after** the rest of a rebuild, because a band needs two
things no single cell knows: the shape it runs inside, and where every cluster name on the map ended
up. It reads the shapes back off the territories rather than off a shaping pass, so the incremental
refresh re-bakes the cells it disturbed through the very same call the full rebuild bakes all of
them through.

`CellRibbonSource` is a source rather than a builder because the per-cell work is
`CellRibbonBuilder`'s; what it adds is the pass that work is done under - the planner the view
resolved, the player's sizes, the holder gate, and the names' boxes, each sampled once so no two
cells of one pass are settled differently. Its holder map is also the cost gate: most of the sector
is cells nobody paints, and the claim mechanic's count walks a system's whole market list.

## Laying a band inside a ring

`CellRibbonBuilder` is pure over a ring, a plan and the name boxes - no sector, no settings, no GL:

1. `RingPath.traceInsetRing` insets the cell's ring by the pad plus half the width, normalises the
   winding, and parameterises it by arc length from the cell's top centre, clockwise.
2. The name boxes are carved off that path, leaving the clear stretches.
3. One width's worth of ring is clamped to what those stretches leave:
   `min(width, clearLength / totalUnits)`, so a crowded cell - or one much of whose ring is under a
   name - compresses rather than losing a bloc off the end.
4. Below `Limits.MIN_EDGE_LENGTH` the cell says nothing at all. That covers a cell smaller than the
   pad and width together, one narrowed to a neck, and one whose ring the names have eaten.

Compressing the *length* while leaving the width alone is deliberate: the band says its piece
through the proportions between its runs, so shortening every run by one factor keeps all of them.

## Keeping clear of the names

The names are carved off the **path**, never off the plan. A run's length is the readout, so a run
cut short by a word would say something false about the system, where a run interrupted by one
simply draws as two pieces of its own colour. Carving the path first also means the clamp above
resizes against what room is left.

Every name on the map is carved off every cell, since a name sits wherever its own cluster is
roomiest and that can be over a neighbour.

Whether any of this happens is the player's: `CellRibbonsBaker.resolveNameBoxes` hands over no boxes
when the names are switched off, and none when the player would rather keep the whole band. Nothing
below that branches - `CellRibbonBuilder` takes boxes and knows nothing about why the list came back
empty, which is what keeps the carve testable on hand-built rings.

## One stroke, many colours

A band is one shape whatever it is coloured in. Each clear stretch is stroked **once** through
`PolylineBands.strokeSpansToTriangles` and cut into its runs afterwards, rather than each run being
stroked on its own: separate strokes butt square ends together, which opens a wedge wherever that
boundary lands on a corner - and a cell's ring is rounded, so most boundaries land on one. Where a
name interrupts the ring the band genuinely stops, so each stretch is its own stroke and its two
ends are square, which is what an interruption should look like.

A run and a piece are not the same thing once the names have cut the ring: `RibbonPiece` carries the
run it came from rather than the two being matched up by position.

## Sizes and drawing

`RibbonStyle` is the sizes one bake is laid out at, read through `RibbonStyleReader` from the
player's Visuals tab: the width, the clearance from the border, and the two run lengths those are
multiples of. All world sizes, so nothing about a band is asked of the camera. The mitre spike limit
is authored rather than exposed - it is the angle past which a corner's mitre becomes a spike, a
property of stroking a polyline rather than of how the readout looks.

`CellRibbon` is one cell's baked result, a list of `RibbonBand` (a colour plus flattened triangle
vertices). `CellPresenceRibbonRenderer` draws them in the `ABOVE_STARSCAPE_NEBULAE` band, so a band
reads over the map's nebula sprites rather than being fogged by them - being fogged would cost it
the very thing it is for.

## What is not here

**What a band says** - the runs, the dividers, the presence gate - is
[`base.ribbon`](../../ribbon/README.md). **Where the counts come from** is each mechanic's:
[`dominance.ribbon`](../../../dominance/ribbon/README.md) and
[`claims.ribbon`](../../../claims/ribbon/README.md).
