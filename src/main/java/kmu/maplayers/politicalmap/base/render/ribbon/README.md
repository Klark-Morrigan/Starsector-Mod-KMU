# Presence band geometry (`render.ribbon`)

Turns a cell's planned band - the runs [`base.ribbon`](../../ribbon/README.md) laid out in band
widths - into triangles inside that cell's own ring, and draws them. Everything here is a world
size, so the whole band resolves at rebuild and a frame measures nothing about one.

Part of [the political map](../../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../README.md) for project context.

## Index

- [The three classes that build one](#the-three-classes-that-build-one)
- [Laying a band inside a ring](#laying-a-band-inside-a-ring)
- [When the ring has no room](#when-the-ring-has-no-room)
- [Keeping clear of the names](#keeping-clear-of-the-names)
- [One band, one stretch](#one-band-one-stretch)
- [Where a band sits](#where-a-band-sits)
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
2. The name boxes are carved off that path, and the longest stretch left is the one the whole band
   goes on - see [one band, one stretch](#one-band-one-stretch).
3. One width's worth of ring is clamped to that stretch: `min(width, stretchLength / totalUnits)`,
   so a crowded cell - or one much of whose ring is under a name - compresses rather than losing a
   bloc off the end.
4. Below `Limits.MIN_EDGE_LENGTH` the cell says nothing at all. That covers a cell smaller than the
   pad and width together, one narrowed to a neck, and one whose ring the names have eaten.
5. `RingPath.placeSpanNearestStart` settles where along that stretch the band sits - see
   [where a band sits](#where-a-band-sits).

## When the ring has no room

Two of those refusals are the ring turning a band down rather than the system having nothing to
report, and a reader cannot tell the two apart - both are a bare cell. So **Always draw a planned
band** (on by default) turns each into a fallback:

| Refusal | Fallback |
| --- | --- |
| the names cover the whole ring | the band takes the whole ring, clearance given up |
| the cell cannot hold the pad and the half width | traced at `computeUnpaddedCentrelineInset()` |

The pad is what gives way to a narrow cell, never the half width: the pad is a look, while the half
width is what puts the band's near edge on the border instead of over it. A cell narrower than the
band is wide has no band to draw rather than a tighter one, and comes back bare either way.

Off, a cell short of room simply draws nothing - tidier, and no way to tell a system with nothing
to say from one refused the room to say it. That difference is the knob's other use: with it on,
a bare cell means the plan was empty.

Compressing the *length* while leaving the width alone is deliberate: the band says its piece
through the proportions between its runs, so shortening every run by one factor keeps all of them.

## Keeping clear of the names

The names are carved off the **path**, never off the plan. A run's length is the readout, so a run
cut short by a word would say something false about the system, where a name taken off the path
costs the band room rather than proportion. Carving the path first also means the clamp above
resizes against what room is left.

Every name on the map is carved off every cell, since a name sits wherever its own cluster is
roomiest and that can be over a neighbour.

Whether any of this happens is the player's: `CellRibbonsBaker.resolveNameBoxes` hands over no boxes
when the names are switched off, and none when the player would rather keep the whole band. Nothing
below that branches - `CellRibbonBuilder` takes boxes and knows nothing about why the list came back
empty, which is what keeps the carve testable on hand-built rings.

How much room a name is taken to need is the player's as well, and the same call answers it: the
name's fitted box (`ClusterNameBoxes`) or the drawn words (`LabelLineBoxes`, the default), both from
[`base.labels`](../../../../base/labels/README.md). The fitted box is the chord the placement
search accepted, which reaches past the words by however much it beat them, so a band keeping clear
of it gives up ring to a name the reader cannot see there. Either way the builder is handed world
boxes and carves the same way.

## One band, one stretch

The names can cut a ring into several clear stretches, and the band takes the longest of them; the
rest of the ring stays bare. A band scattered over the stretches would not read as the proportional
thing it is - a reader cannot tell one run interrupted by a word from two runs of one colour - so
the split loses the very readout the carve protects and litters the cell for it. The cost is that a
ring cut into two near-equal halves spends one of them.

The stretch running through the path's own start arrives from `RingPath.findClearArcs` as two
`RingStretch`es, one at each end, since that carve deliberately does not wrap.
`RingPath.fuseStretchAcrossStart` reads them as the one stretch they are before the longest is
chosen - without it, every cell whose name sits anywhere but its top centre would be judged on
whichever half of its longest stretch happened to be bigger.

## Where a band sits

A band shorter than its stretch can sit anywhere along it, and where it sits is decided rather
than left to the ring the names happened to leave: **a band sits as near the cell's top centre as
its stretch allows**. That landmark is the path's own origin and where the dominant bloc's run
begins, so every cell is read from the same place - which is what a band opening wherever a name
left off costs the reader.

Near is measured on the band's **start**, since the start is where the reading begins - a band
pulled toward the landmark by its middle would straddle it and put the middle of the readout
where its opening belongs.

The rule itself is `RingPath.placeSpanNearestStart`, because it is arithmetic about a path rather
than about a readout: one clamp, pulling the span's start into what the stretch leaves it by the
shortest way round, ties taking the stretch's start. The range it clamps into is never empty
here, since the clamp above has already sized the band to fit the stretch, so placement is never
a refusal.

## One stroke, many colours

A band is one shape whatever it is coloured in. It is stroked **once** through
`PolylineBands.strokeSpansToTriangles` and cut into its runs afterwards, rather than each run being
stroked on its own: separate strokes butt square ends together, which opens a wedge wherever that
boundary lands on a corner - and a cell's ring is rounded, so most boundaries land on one. Only the
band's own two ends are square. Being one band on one stretch is what makes that true of a cell
with names across it as much as of a cell without.

## Sizes and drawing

`RibbonStyle` is how one bake is laid out, read through `RibbonStyleReader` from the
player's Visuals tab: the width, the clearance from the border, the two run lengths those are
multiples of, and whether a cell short of room draws a band anyway. The sizes are all world sizes,
so nothing about a band is asked of the camera. The mitre spike limit is authored rather than
exposed - it is the angle past which a corner's mitre becomes a spike, a property of stroking a
polyline rather than of how the readout looks.

`CellRibbon` is one cell's baked result, a list of `RibbonBand` (a colour plus flattened triangle
vertices). `CellPresenceRibbonRenderer` draws them in the `ABOVE_STARSCAPE_NEBULAE` band, so a band
reads over the map's nebula sprites rather than being fogged by them - being fogged would cost it
the very thing it is for.

## What is not here

**What a band says** - the runs, the dividers, the presence gate - is
[`base.ribbon`](../../ribbon/README.md). **Where the counts come from** is each mechanic's:
[`dominance.ribbon`](../../../dominance/ribbon/README.md) and
[`claims.ribbon`](../../../claims/ribbon/README.md).
