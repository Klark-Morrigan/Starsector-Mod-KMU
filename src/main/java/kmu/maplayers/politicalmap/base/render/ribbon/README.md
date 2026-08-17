# Presence band geometry (`render.ribbon`)

Turns a cell's planned band - the runs [`base.ribbon`](../../ribbon/README.md) laid out in band
widths - into triangles inside that cell's own ring, and draws them. Everything here is a world
size, so the whole band resolves at rebuild and a frame measures nothing about one.

Part of [the political map](../../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../README.md) for project context.

## Index

- [The classes that build one](#the-classes-that-build-one)
- [Laying a band inside a ring](#laying-a-band-inside-a-ring)
- [Which cells a bake covers](#which-cells-a-bake-covers)
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

## The classes that build one

Five collaborators with deliberately different jobs, since the names are close enough to be worth
stating apart, plus the accumulator they charge what they spend to:

| Class | Scope | Job |
| --- | --- | --- |
| `CellRibbonsBaker` | the pass | drives the loop over cells and writes each band back |
| `CellRibbonSource` | the pass | holds what a pass is settled from; answers one cell at a time |
| `CellRibbonBuilder` | one cell | pure geometry: path plus plan in, `CellRibbon` out |
| `RibbonPathTracer` | one cell | pure geometry: the ring a band runs along, at whichever inset fits |
| `CellRingPathCache` | one build | the rings already walked, so a re-bake walks only what was re-shaped |
| `RibbonBakeTimings` | the pass | what the pass spent, split four ways; see [what a bake spends its time on](#what-a-bake-spends-its-time-on) |

`CellRibbonsBaker` runs as its own pass **after** the rest of a rebuild, because a band needs two
things no single cell knows: the shape it runs inside, and where every cluster name on the map ended
up. It reads the shapes back off the territories rather than off a shaping pass, so the incremental
refresh re-bakes the cells it disturbed through the very same call the full rebuild bakes all of
them through.

`CellRibbonSource` is a source rather than a builder because the per-cell work is
`CellRibbonBuilder`'s; what it adds is the pass that work is done under - the planner the view
resolved, the player's sizes, the inhabitation gate, and the names' boxes, each sampled once so no
two cells of one pass are settled differently. It also settles which cells get a band at all, in
three refusals read in one place: a cell nobody lives in or with nowhere to start from, a system the
sector no longer lists, and a cell whose plan came back empty. Each is a cost gate as much as an
answer - the claim mechanic's count walks a system's whole market list, and the empty plan is what
spares the single-holder cell a ring walk.

The gate is the pass's **inhabitation scan**, not its holding, and that is what puts a band on the
systems no layer paints. A settled system can resolve no claimant for
[several reasons](../../politics/holders/README.md) - a pirate haven and a player colony among them
- so the claims layer's fill says nothing about it, and gating on that fill would have left the band
silent on exactly the systems it is the only readout for. Every held system is inhabited (a bloc
holds one only by having a colony in it), so nothing that banded before stops banding. It is also
the very set the factionless cell beneath is classified from, so a cell drawn as settled and a cell
offered a band are one set. Such a cell reaches `ClaimedSystemRibbonPlanner` with **no painter**,
which is a case of its own rather than an absent one; see
[`base.ribbon`](../../ribbon/README.md) for the two arms that follow from it.

The gate is **wider** than the holding it replaced, which is a real cost and not a free swap.
Inhabitation is the larger set, so the systems the holding left out each pay their first walk of the
pass's colony index - and `readMarketsUnlistedByEconomy` walks every entity in the system. For a
pirate haven or a player colony that walk buys the band it exists for. For a **decivilised** system
it does not, yet: those are inhabited (`MapVisibility.isInhabited` reads the revealed-ruin arm), so
they reach the planner, but their markets are condition-only and the shared colony set excludes them
- so they walk for a count that comes back empty and a band that is never laid. That stays true
until the shared set admits a revealed dead world as the colony it is.

## Laying a band inside a ring

`CellRibbonBuilder` is pure over a path, a plan and the name boxes - no sector, no settings, no GL,
and the pass's timings written to but never read:

1. `RibbonPathTracer` insets the cell's ring by the pad plus half the width through
   `RingPath.traceInsetRing`, which normalises the winding and parameterises the ring by arc
   length from the cell's top centre, clockwise. Done by the pass rather than by the builder, since
   the result outlives one bake; see
   [a ring that outlives the bake that walked it](#a-ring-that-outlives-the-bake-that-walked-it).
2. The name boxes are carved off that path - as are the stretches of it the cell's own outline
   leaves no room on - and the longest stretch left is the one the whole band goes on; see
   [one band, one stretch](#one-band-one-stretch).
3. One width's worth of ring is clamped to that stretch: `min(width, stretchLength / totalUnits)`,
   so a crowded cell - or one much of whose ring is under a name - compresses rather than losing a
   bloc off the end.
4. Below `Limits.MIN_EDGE_LENGTH` the cell says nothing at all - too little ring left to state the
   plan at any size, whether because the cell is small or because the names have eaten most of it.
   A cell whose ring holds the band's inset nowhere - smaller than the pad and width together, or
   too narrow for them the whole way round - is refused a step earlier, by the trace. A cell
   merely narrowed in one place is not that cell; see
   [a neck costs its own stretch](#a-neck-costs-its-own-stretch).
5. `RingPath.placeSpanNearestStart` settles where along that stretch the band sits - see
   [where a band sits](#where-a-band-sits).

## Which cells a bake covers

A full rebuild bakes every cell, having just built them all from nothing. An incremental refresh
names its cells instead, from the three separate reasons one can owe a band:

| Reason | Which cells |
| --- | --- |
| the count moved | the systems a colony event marked, whether or not their holder changed |
| the ring moved | the cells the batch redrew - the ring around a flip, and any system whose own facts moved - each of which lost the band laid in its old shape |
| the room moved | the cells a re-fitted name reaches, which the flip need never have touched |

The third is the one that reaches beyond the flip, and it used to be answered by re-baking the
whole map: a re-fit places a name wherever its new cluster is roomiest, which can be a cell nothing
else about the batch went near, and the pass had no way to tell which. `ClusterAnchorsBuilder`
reports it now - it already indexes the standing placements to decide which clusters it may carry
over, so which names moved is that same index read from the other side. `ClusterNameDisturbance`
turns the boxes that appeared, vanished and moved into the cells they reach, against each cell's
**bounding box** rather than its outline: over-inclusion costs a re-bake that changes nothing,
while under-inclusion leaves a band drawn under a word until something unrelated rebuilds the map.

A batch that flipped nothing re-fits nothing, so the third reason cannot arise on it and only the
marked systems' bands are re-baked.

## A ring that outlives the bake that walked it

A bake runs whenever a cluster name may have moved, which is far more often than a cell is
re-shaped. A cell's ring is not moved by a name at all - it is decided by which of the cell's edges
are same-owner seams - so a cell re-baked for a name that landed on it would otherwise pay a full
walk, an inset and a fold splice and a clearance walk and an arc-length walk, to arrive at the path
it discarded a moment earlier.

So the walked path is kept beside the shape it was walked inside. `PoliticalMapTerritories` holds a
`CellRingPathCache` next to its fill polygons, the write that records a cell's shape drops that
cell's path exactly as it drops that cell's band, and `CellRibbonSource` asks for the standing path
before walking one. What is left per bake is the part that genuinely changed: the carve, the
placement and the stroke.

Held under no key and no revision, and that is the whole of why it is safe. A key is a rule
somebody has to keep true; a cache living inside the object whose lifetime it must match is correct
by construction - a rebuild mints fresh territories and the paths go with them, and a slider bumps
the settings revision, which rebuilds them, so a path can never be served at an inset it was not
walked at.

It cannot change what is drawn: the served path is the same function of the same ring, so a cell's
band is identical with the cache and without it. What it does change is the `.trace` row of the
readout below, which is now what walking rings cost this bake rather than what walking them all
would have.

## When the ring has no room

Two of those refusals are the ring turning a band down rather than the system having nothing to
report, and a reader cannot tell the two apart - both are a bare cell. So **Always draw a planned
band** (on by default) turns each into a fallback:

| Refusal | Fallback |
| --- | --- |
| the names cover the whole ring | the band takes the ring the cell's own shape leaves, clearance given up |
| the cell holds the pad and the half width nowhere | traced at `computeUnpaddedCentrelineInset()` |

The pad is what gives way to a narrow cell, never the half width: the pad is a look, while the half
width is what puts the band's near edge on the border instead of over it. A cell narrower than the
band is wide has no band to draw rather than a tighter one, and comes back bare either way.

Off, a cell short of room simply draws nothing - tidier, and no way to tell a system with nothing
to say from one refused the room to say it. That difference is the knob's other use: with it on,
those two refusals are gone, so a bare cell is one the plan was empty for, one narrower than the
band, or one with too little ring left to state its plan - a narrower question than the seven a
bare cell asks with the knob off, and the reason the two refusals it cannot answer are named above
rather than left to be discovered.

Compressing the *length* while leaving the width alone is deliberate: the band says its piece
through the proportions between its runs, so shortening every run by one factor keeps all of them.

## A neck costs its own stretch

A cell's outline can have no room for the band in one place and room several times over
everywhere else - a tab, a spur, the gap where two neighbours nearly meet. `RingPath.traceInsetRing`
states that as ring rather than as a verdict: the stretches where the traced path stands nearer the
cell's border than the inset it was built from are carved off it, exactly as a name's box is, and
the band goes on the longest of what is left. A neck costs its own arc and nothing more.

The refusal above is what that carve leaves rather than a rule beside it. A cell too narrow the
whole way round fails at every corner of its path, so every stretch goes and there is nothing left
to lay a band on - the same answer as before, reached by the rule that spares the cell with one
narrow place.

Forcing does not give a neck back. The names give up the ring they cover because which of two
readouts wins the room is the player's to settle; a neck is the cell's own shape, and a band run
through one hangs over the border the half width exists to keep it inside.

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
vertices). `CellPresenceRibbonRenderer` draws them in whichever band the player's **Nebula draw order**
setting for the presence bands placed them, which ships as `ABOVE_STARSCAPE_NEBULAE`: a band reads
over the map's nebula sprites rather than being fogged by them, and being fogged would cost it the
very thing it is for. The setting exists because how much that costs depends on the palette it is
read against.

## Seeing the path a cell did not use

A cell drawing nothing is the sector's ordinary state and also every one of the band's refusals,
and on the map they are the same picture. **Show presence band paths** (Map - Dev, off by default)
draws what is underneath that silence: the ring each cell's band would have run along, a dot at the
point every band starts from, and the shared diagnostic ramp for what the cell's own room allowed.

The ring is drawn as the two things it is - the stretches a band may lie on in the cell's verdict
shade, the stretches its own shape denied it in the discarded shade - as open strips rather than
one closed loop. A carved ring is not a loop, and on a cell narrow over most of its length the
carved part runs *outside* the cell's own border, since a ring inset past the shape's own width
folds through itself. Drawn whole, that reads as geometry escaping the cell it reports on; drawn in
two shades it reads as what it is - how much of the outline the band never had.

| Shade | `RibbonPathVerdict` | What the cell is |
| --- | --- | --- |
| green | `LAID_AT_PAD` | the ring held the authored inset |
| yellow | `LAID_UNPADDED` | too narrow for the pad everywhere, and drawn anyway |
| red | `REFUSED` | too narrow for the pad everywhere with **Always draw a planned band** off |

The same ramp is read again per stretch: a carved stretch draws red whatever the cell's own verdict
is, so red means one thing on this overlay - outline no band is laid on.

A cell narrower than the band is wide has no path at all and so draws none, which is the one
refusal the overlay cannot show. Nor does a shade promise a band: green says the ring offered room,
while whether anything was laid on it is the plan's business and the names'. A shade is about the
inset the ring was traced at rather than about every stretch of it, so a cell narrowed in one place
is green and simply carries a band shorter than its outline.

`RibbonPathTracer` is what makes the overlay worth looking at. Both the band pass and the overlay
walk the same ladder there - the authored inset, then the pad given up - so the path drawn is the
path a band would use, rather than a second reading that agrees with it by coincidence. They differ
in one thing: the overlay traces the shallower inset even where the player has the fall switched
off, since that cell is exactly the one it is looked at to explain. That is also why it walks the
ring afresh rather than reading the kept path above - the cells it exists for are the ones the band
pass has no path for - and the cost of that walk is paid only while a dev toggle is on.

`CellRibbonsBaker` traces in the same loop as the bake, under a toggle read once per pass, and
hands over nothing for every cell while it is off - which is also what clears the paths a pass
taken while it was on left behind. `CellRibbonPathRenderer` draws them over the bands, so a band
and the path it was laid on can be read against each other.

## What a bake spends its time on

A bake does four separable things, and `RibbonBakeTimings` reports each on its own row of the
profiling readout (`kmu_profiling`) beneath the whole-pass `politicalMap.bakeRibbons`:

| Section | What it covers | What it grows with |
| --- | --- | --- |
| `.plan` | counting what a system holds | the systems' colonies - the claim mechanic walks a system's whole market list |
| `.trace` | `RibbonPathTracer` insetting and walking the ring | the cells this bake had to walk - every cell on a fresh build, only the re-shaped ones after |
| `.carve` | the names and the pinches taken off that ring, and the band placed on what is left | the cells times the names, since every name on the map is tested against every cell |
| `.stroke` | the runs laid end to end and stroked into triangles | the cells that drew something, and how much each planned |

Four rather than one because those axes differ: a sector that doubles its colonies does not move
them by one factor, so a single total can say a bake got slower without saying which of them did.

Summed per pass and recorded once at the end of the loop, since `Profiler.record` takes an elapsed
count - so one pass is one run of each section, and the readout's average is what a bake costs
rather than what a cell does. They are read against the whole-pass row rather than instead of it:
the gap between their sum and the total is the loop itself, plus the overlay's second trace while a
player has it on.

The same four also close the bake's own `LOG.debug` line beside its total
(`plan=1.50ms trace=0.25ms ...`), so a rebuild being watched in the log says which phase stood out
without the console command being opened.

## What is not here

**What a band says** - the runs, the dividers, the presence gate - is
[`base.ribbon`](../../ribbon/README.md). **Where the counts come from** is each mechanic's:
[`dominance.ribbon`](../../../dominance/ribbon/README.md) and
[`claims.ribbon`](../../../claims/ribbon/README.md).
