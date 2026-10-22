# Cell bands (`base.ribbon`)

A band is a stroke of coloured runs laid inside a cell's own ring.
This package states what one is made of,
turns it into triangles clear of the cluster names,
and draws it.
Nothing here knows why a run is the colour it is:
a layer lays its runs by its own rule and hands over the plan.

Everything here is a world size,
so the whole band resolves at rebuild and a frame measures nothing about one.

Part of [the map-layer framework](../../README.md);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [What a band is](#what-a-band-is)
- [The classes that build one](#the-classes-that-build-one)
- [Laying a band inside a ring](#laying-a-band-inside-a-ring)
- [A ring that outlives the bake that walked it](#a-ring-that-outlives-the-bake-that-walked-it)
- [When the ring has no room](#when-the-ring-has-no-room)
- [A neck costs its own stretch](#a-neck-costs-its-own-stretch)
- [Keeping clear of the names](#keeping-clear-of-the-names)
- [One band, one stretch](#one-band-one-stretch)
- [Where a band sits](#where-a-band-sits)
- [One stroke, many colours](#one-stroke-many-colours)
- [Sizes and drawing](#sizes-and-drawing)
- [Seeing the path a cell did not use](#seeing-the-path-a-cell-did-not-use)
- [What a bake spends its time on](#what-a-bake-spends-its-time-on)
- [What is not here](#what-is-not-here)

## What a band is

`RibbonPlan` is a band stated as pure data:
an ordered list of `RibbonSegment`,
each a colour and a length in band widths,
read clockwise from the cell's top centre.
`RibbonPlan.NONE` is the cell that draws no band.

A run reads as a count.
A long run is several *stakes* of one colour,
each parted from the next by a short *interjection*,
and `RibbonSegmentLengths` pairs those two lengths,
so the proportion that makes a run read as several stakes is one authored decision.

Lengths are counts of widths,
never world sizes.
How large a width is in the world is settled against the cell's own ring when the band is laid,
which is what lets a crowded cell compress every run by one factor
and keep the proportions between them - the proportions being the whole of what the band says.

## The classes that build one

| Class | Scope | Job |
| --- | --- | --- |
| `RibbonPlan` | one cell | the runs, in draw order |
| `RibbonPathTracer` | one cell | pure geometry: the ring a band runs along, at whichever inset fits |
| `CellRibbonBuilder` | one cell | pure geometry: path plus plan in, `CellRibbon` out |
| `CellRingPathCache` | one build | the rings already walked, so a re-bake walks only what was re-shaped |
| `RibbonStyle` | the pass | every size a band is laid out at |
| `RibbonBakePhases` | the pass | the section a bake is measured under and its four phases; see [what a bake spends its time on](#what-a-bake-spends-its-time-on) |
| `CellRibbonRenderer` | the frame | draws the baked bands |
| `CellRibbonPathRenderer` | the frame | draws the diagnostic path overlay |

What drives a bake over a whole map -
which cells are offered a band,
whose plan each takes,
and when a bake runs -
is the layer's.

## Laying a band inside a ring

`CellRibbonBuilder` is pure over a path,
a plan and the name boxes -
no sector,
no settings,
no GL,
and the pass's timings written to but never read:

1. `RibbonPathTracer` insets the cell's ring by the pad plus half the width through `RingPath.traceInsetRing`,
   which normalises the winding and parameterises the ring by arc length from the cell's top centre,
   clockwise.
   Done by the caller rather than by the builder,
   since the result outlives one bake;
   see [a ring that outlives the bake that walked it](#a-ring-that-outlives-the-bake-that-walked-it).
2. The name boxes are carved off that path -
   as are the stretches of it the cell's own outline leaves no room on -
   and the longest stretch left is the one the whole band goes on;
   see [one band, one stretch](#one-band-one-stretch).
3. One width's worth of ring is clamped to that stretch:
   `min(width, stretchLength / totalUnits)`,
   so a crowded cell -
   or one much of whose ring is under a name -
   compresses rather than losing a run off the end.
4. Below `Limits.MIN_EDGE_LENGTH` the cell says nothing at all -
   too little ring left to state the plan at any size,
   whether because the cell is small or because the names have eaten most of it.
   A cell whose ring holds the band's inset nowhere -
   smaller than the pad and width together,
   or too narrow for them the whole way round -
   is refused a step earlier,
   by the trace.
   A cell merely narrowed in one place is not that cell;
   see [a neck costs its own stretch](#a-neck-costs-its-own-stretch).
5. `RingPath.placeSpanNearestStart` settles where along that stretch the band sits -
   see [where a band sits](#where-a-band-sits).

Steps 2 to 5 are two methods:
finding the stretch the band goes on,
then placing the band on it.

## A ring that outlives the bake that walked it

A bake runs whenever a cluster name may have moved,
which is far more often than a cell is re-shaped.
A cell's ring is not moved by a name at all -
it is decided by which of the cell's edges are seams inside one cluster -
so a cell re-baked for a name that landed on it would otherwise pay a full walk,
an inset and a fold splice and a clearance walk and an arc-length walk,
to arrive at the path it discarded a moment earlier.

So the walked path is kept beside the shape it was walked inside,
in a `CellRingPathCache` the layer holds next to its cells' fill polygons.
The write that records a cell's shape drops that cell's path,
and a bake asks for the standing path before walking one.
What is left per bake is the part that genuinely changed:
the carve,
the placement and the stroke.

Held under no key and no revision,
and that is the whole of why it is safe.
A key is a rule somebody has to keep true;
a cache living inside the object whose lifetime it must match is correct by construction -
a rebuild mints fresh cells and the paths go with them,
and a slider bumps the settings revision,
which rebuilds them,
so a path can never be served at an inset it was not walked at.

It cannot change what is drawn:
the served path is the same function of the same ring,
so a cell's band is identical with the cache and without it.
What it does change is the `.trace` row of the readout below,
which reports what walking rings cost this bake rather than what walking them all would have.

## When the ring has no room

Two refusals are the ring turning a band down
rather than the cell having nothing to report,
and a reader cannot tell the two apart -
both are a bare cell.
So `RibbonStyle.isBandAlwaysDrawn` turns each into a fallback:

| Refusal | Fallback |
| --- | --- |
| the names cover the whole ring | the band takes the ring the cell's own shape leaves, clearance given up |
| the cell holds the pad and the half width nowhere | traced at `computeUnpaddedCentrelineInset()` |

The pad is what gives way to a narrow cell,
never the half width:
the pad is a look,
while the half width is what puts the band's near edge on the border instead of over it.
A cell narrower than the band is wide has no band to draw rather than a tighter one,
and comes back bare either way.

Off,
a cell short of room simply draws nothing -
tidier,
and no way to tell a cell with nothing to say from one refused the room to say it.

Compressing the *length* while leaving the width alone is deliberate:
the band says its piece through the proportions between its runs,
so shortening every run by one factor keeps all of them.

## A neck costs its own stretch

A cell's outline can have no room for the band in one place
and room several times over everywhere else -
a tab,
a spur,
the gap where two neighbours nearly meet.
`RingPath.traceInsetRing` states that as ring rather than as a verdict:
the stretches where the traced path stands nearer the cell's border than the inset it was built from are carved off it,
exactly as a name's box is,
and the band goes on the longest of what is left.
A neck costs its own arc and nothing more.

The refusal above is what that carve leaves rather than a rule beside it.
A cell too narrow the whole way round fails at every corner of its path,
so every stretch goes and there is nothing left to lay a band on -
the refusal above,
reached by the same rule that spares the cell with one narrow place.

Forcing does not give a neck back.
The names give up the ring they cover because
which of two readouts wins the room is the player's to settle;
a neck is the cell's own shape,
and a band run through one hangs over the border the half width exists to keep it inside.

## Keeping clear of the names

The names are carved off the **path**,
never off the plan.
A run's length is the readout,
so a run cut short by a word would say something false about the cell,
where a name taken off the path costs the band room rather than proportion.
Carving the path first also means the clamp above resizes against what room is left.

Every name on the map is carved off every cell,
since a name sits wherever its own cluster is roomiest and that can be over a neighbour.

The builder takes the boxes as world rings and knows nothing about where they came from,
or why the list may be empty.
Which boxes a layer hands over -
the fitted boxes or the drawn words of [`base.labels`](../labels/README.md),
or none at all -
is the layer's,
which is what keeps the carve a matter of the boxes and nothing else.

## One band, one stretch

The names can cut a ring into several clear stretches,
and the band takes the longest of them;
the rest of the ring stays bare.
A band scattered over the stretches would not read as the proportional thing it is -
a reader cannot tell one run interrupted by a word from two runs of one colour -
so the split loses the very readout the carve protects and litters the cell for it.
The cost is that a ring cut into two near-equal halves spends one of them.

The stretch running through the path's own start arrives from `RingPath.findClearArcs` as two `RingStretch`es,
one at each end,
since that carve deliberately does not wrap.
`RingPath.fuseStretchAcrossStart` reads them as the one stretch they are before the longest is chosen -
without it,
every cell whose name sits anywhere
but its top centre would be judged on whichever half of its longest stretch happened to be bigger.

## Where a band sits

A band shorter than its stretch can sit anywhere along it,
and where it sits is decided rather than left to the ring the names happened to leave:
**a band sits as near the cell's top centre as its stretch allows**.
That landmark is the path's own origin and where the first run begins,
so every cell is read from the same place -
which is what a band opening wherever a name left off costs the reader.

Near is measured on the band's **start**,
since the start is where the reading begins -
a band pulled toward the landmark by its middle would straddle it
and put the middle of the readout where its opening belongs.

The rule itself is `RingPath.placeSpanNearestStart`,
because it is arithmetic about a path rather than about a readout:
one clamp,
pulling the span's start into what the stretch leaves it by the shortest way round,
ties taking the stretch's start.
The range it clamps into is never empty here,
since the clamp above has already sized the band to fit the stretch,
so placement is never a refusal.

## One stroke, many colours

A band is one shape whatever it is coloured in.
It is stroked **once** through `PolylineBands.strokeSpansToTriangles`
and cut into its runs afterwards,
rather than each run being stroked on its own:
separate strokes butt square ends together,
which opens a wedge wherever that boundary lands on a corner -
and a cell's ring is rounded,
so most boundaries land on one.
Only the band's own two ends are square.
Being one band on one stretch is what makes that true of a cell with names across it as much as of a cell without.

## Sizes and drawing

`RibbonStyle` is how one bake is laid out:
the width,
the clearance from the border,
the two run lengths those are multiples of,
the mitre spike limit,
and whether a cell short of room draws a band anyway.
The sizes are all world sizes,
so nothing about a band is asked of the camera.
Where they come from is the layer's -
this package reads no setting.

`CellRibbon` is one cell's baked result,
a list of `RibbonBand`
(a colour plus flattened triangle vertices).
`CellRibbonRenderer` draws them as triangles scaled by the map factor like the fills,
and a fully faded-out map emits nothing.
Which render band it is drawn in -
above the map's nebula sprites or beneath them -
is the layer's.

## Seeing the path a cell did not use

A cell drawing nothing is the sector's ordinary state and also every one of the band's refusals,
and on the map they are the same picture.
The path overlay draws what is underneath that silence:
the ring each cell's band would have run along,
a dot at the point every band starts from,
and the shared diagnostic ramp for what the cell's own room allowed.

The ring is drawn as the two things it is -
the stretches a band may lie on in the cell's verdict shade,
the stretches its own shape denied it in the discarded shade -
as open strips rather than one closed loop.
A carved ring is not a loop,
and on a cell narrow over most of its length the carved part runs *outside* the cell's own border,
since a ring inset past the shape's own width folds through itself.
Drawn whole,
that reads as geometry escaping the cell it reports on;
drawn in two shades it reads as what it is -
how much of the outline the band never had.

| Shade | `RibbonPathVerdict` | What the cell is |
| --- | --- | --- |
| green | `LAID_AT_PAD` | the ring held the authored inset |
| yellow | `LAID_UNPADDED` | too narrow for the pad everywhere, and drawn anyway |
| red | `REFUSED` | too narrow for the pad everywhere with the band not always drawn |

The same ramp is read again per stretch:
a carved stretch draws red whatever the cell's own verdict is,
so red means one thing on this overlay -
outline no band is laid on.

A cell narrower than the band is wide has no path at all and so draws none,
which is the one refusal the overlay cannot show.
Nor does a shade promise a band:
green says the ring offered room,
while whether anything was laid on it is the plan's business and the names'.

`RibbonPathTracer` is what makes the overlay worth looking at.
Both the band pass and the overlay walk the same ladder there -
the authored inset,
then the pad given up -
so the path drawn is the path a band would use,
rather than a second reading that agrees with it by coincidence.
They differ in one thing:
the overlay traces the shallower inset even where the fall is switched off,
since that cell is exactly the one it is looked at to explain.
That is also why it walks the ring afresh rather than reading the kept path above -
the cells it exists for are the ones the band pass has no path for.

`CellRibbonPathRenderer` draws the paths and nothing else.
The layer orders the two renderers,
drawing the paths over the bands so a band and the path it was laid on can be read against each other.

## What a bake spends its time on

A bake does four separable things,
and `RibbonBakePhases` declares each as a phase of the loop the whole-pass section `mapLayer.bakeRibbons` runs.
The profiling readout (`kmu_profiling`) states them under that row,
per cell:

| Phase | What it covers | What it grows with |
| --- | --- | --- |
| `plan` | the layer counting what a cell's runs are | whatever the layer's rule reads per cell |
| `trace` | reaching for the cell's ring, and `RibbonPathTracer` insetting and walking one where none stands | the cells this bake had to walk - every cell on a fresh build, only the re-shaped ones after |
| `carve` | the names and the pinches taken off that ring, and the band placed on what is left | the cells times the names, since every name on the map is tested against every cell |
| `stroke` | the runs laid end to end and stroked into triangles | the cells that drew something, and how much each planned |

Four rather than one because those axes differ,
so a single total can say a bake got slower without saying which of them did.
The builder marks `carve` and `stroke`;
the layer driving the bake opens the scope,
begins a turn per cell,
and marks the other two.

Phases of one open scope rather than sections of their own,
because a bake is one call and a cell is one turn of its loop -
and it is the turn a rebuild's cost scales with.
Measuring a cell as a section of its own would cost about what a cell's work costs.

## What is not here

**Which runs a cell's band has** -
whose,
how many,
in what order and at what length -
is the layer's rule,
and so is driving the bakes;
for a layer painted by owner that is [the owner-map tier](../../ownermap/README.md).
**The settings** the sizes come from are the layer's to read,
since this package reads none.
