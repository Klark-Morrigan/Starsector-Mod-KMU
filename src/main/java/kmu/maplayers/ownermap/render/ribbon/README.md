# Presence band bakes (`render.ribbon`)

Bakes the owner map's presence bands:
which cells are offered a band,
whose runs each one plans,
and the player's settings every band is laid out by.
What a band is and how it is laid inside its ring is [`base.ribbon`](../../../base/ribbon/README.md);
the runs it is laid in are [`ownermap.ribbon`](../../ribbon/README.md)'s.

Part of [the owner-map tier](../../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../../README.md) for project context.

## Index

- [The classes that bake one](#the-classes-that-bake-one)
- [Which cells band](#which-cells-band)
- [Which cells a bake covers](#which-cells-a-bake-covers)
- [Where the rings are kept](#where-the-rings-are-kept)
- [Which room the names take](#which-room-the-names-take)
- [Sizes and drawing](#sizes-and-drawing)
- [Seeing the path a cell did not use](#seeing-the-path-a-cell-did-not-use)
- [What a bake spends its time on](#what-a-bake-spends-its-time-on)
- [What is not here](#what-is-not-here)

## The classes that bake one

| Class | Scope | Job |
| --- | --- | --- |
| `CellRibbonsBaker` | the pass | drives the loop over cells and writes each band back |
| `CellRibbonSource` | the pass | holds what a pass is settled from; refuses the cells that take no band and answers the rest one at a time |
| `RibbonBakeSurface` | the pass | the drawn map bands go on: the inhabitation gate, where each band starts, the room it keeps clear of, the rings already traced |
| `RibbonBakeSettings` | the pass | every band knob, read once by the bake's caller and handed to `CellRibbonsBaker` |

`CellRibbonsBaker` runs as its own **stage**,
after the rest of a rebuild,
because a band needs two things no single cell knows:
the shape it runs inside,
and where every cluster name on the map ended up.
It reads the shapes back off the clusters rather than off a shaping pass,
so the incremental refresh re-bakes the cells it disturbed through the very same call the full rebuild bakes all of them through.

A stage of its own,
but not a **walk** of its own.
The walk of the sector a bake counts off arrives from its caller,
because how current it has to be is the caller's question:
a bake running in the same frame as the rebuild before it takes that rebuild's walk,
the sector being unable to move between the two,
while a bake on its own cadence -
whenever a cluster name may have moved -
takes the batch's walk rather than reporting the sector as it stood some flips ago.
Either way the planner is asked of the owner source the build's owners came from,
or a band would plan against owners no cell was drawn for.

`CellRibbonSource` is a source rather than a builder
because the per-cell work is `base.ribbon`'s `CellRibbonBuilder`;
what it adds is the pass that work is done under -
the planner the owner source answered,
the player's sizes,
and the drawn map those bands go on,
each sampled once so no two cells of one pass are settled differently.
The planner is the source's answer (`OwnerSource.resolveRibbonPlanner`),
and so is who stands together in a contest it judges,
so nothing here names what supplies either.

## Which cells band

`RibbonBakeSurface` is the drawn map a bake lays its bands on:
the inhabitation gate,
the sites,
the names' boxes and the rings,
read together off cells that have already been shaped and named,
so a bake cannot be handed a gate from one reading and sites from another.
`CellRibbonSource` settles which cells get a band at all,
off the gate and sites the surface supplies,
in three refusals read in one place:
a cell nobody lives in or with nowhere to start from,
a system the sector no longer lists,
and a cell whose plan came back empty because nothing the layer counts is present in it.
Every cell with something counted in it bands,
contested or not.
Each refusal is a cost gate as much as an answer -
a planner may walk everything a system holds for its count,
and the empty plan is what spares a cell with nothing counted a ring walk.

The second of those refusals is answered against the pass's own systems-by-key index
rather than one this bake traverses the sector for.
The cells were cut from that same index,
so indexing here would be the rebuild's second traversal of the sector -
which is exactly what the frame's bound on a refresh refuses.

The gate is the source's **inhabited set**,
not its owner map,
and that is what puts a band on the systems no layer paints.
A settled system can resolve no owner,
for reasons the layer's own rule sets,
so the layer's fill says nothing about it,
and gating on that fill would leave the band silent on exactly the systems it is the only readout for.
The gate is a contract the source answers to:
an owner in a system nobody inhabits gets no band.
It is also the very set the unowned cell beneath is classified from,
so a cell drawn as settled and a cell offered a band are one set.
Such a cell reaches the view's planner with **no painter**,
which is a case of its own rather than an absent one;
see [`ownermap.ribbon`](../../ribbon/README.md) for the two arms that follow from it.

The gate is **wider** than the owner map,
which is a real cost.
The inhabited set is the larger,
so the systems nobody owns each pay their first walk of what the source reads per system.
For a settled system that walk buys the band it exists for,
and a run it lays for something nobody owns draws in the colours an unowned cell gets,
which is the same thing the cell beneath it says.

## Which cells a bake covers

A full rebuild bakes every cell,
having just built them all from nothing.
An incremental refresh names its cells instead,
from the three separate reasons one can owe a band:

| Reason | Which cells |
| --- | --- |
| the count moved | the systems a change marked, whether or not their owner changed |
| the ring moved | the cells the batch redrew - the ring around a flip, and any system whose own facts moved - each of which lost the band laid in its old shape |
| the room moved | the cells a re-fitted name reaches, which the flip need never have touched |

The third is the one that reaches beyond the flip,
and left to the pass it could be answered only by re-baking the whole map:
a re-fit places a name wherever its new cluster is roomiest,
which can be a cell nothing else about the batch went near,
and the pass has no way to tell which.
The name fit in `base.labels` reports it -
it already indexes the standing placements to decide which clusters it may carry over,
so which names moved is that same index read from the other side.
`ClusterNameDisturbance` turns the boxes that appeared,
vanished and moved into the cells they reach,
against each cell's **bounding box** rather than its outline:
over-inclusion costs a re-bake that changes nothing,
while under-inclusion leaves a band drawn under a word until something unrelated rebuilds the map.

A batch that flipped nothing re-fits nothing,
so the third reason cannot arise on it and only the marked systems' bands are re-baked.

## Where the rings are kept

`PaintedCellStore` holds the `CellRingPathCache` next to its fill polygons,
the write that records a cell's shape drops that cell's path exactly as it drops that cell's band,
and `CellRibbonSource` asks for the standing path before walking one.
Why that is safe with no key and no revision is [`base.ribbon`](../../../base/ribbon/README.md#a-ring-that-outlives-the-bake-that-walked-it)'s.

## Which room the names take

Whether the bands keep clear of the names at all is the player's,
arriving with the rest of the bake's settings:
`RibbonBakeSurface.resolveNameBoxes` hands over no boxes when the names are switched off,
and none when the player would rather keep the whole band.
How much room a name is taken to need is the player's as well,
and the same call answers it:
the name's fitted box (`ClusterNameBoxes`) or the drawn words (`LabelLineBoxes`, the default),
both from [`base.labels`](../../../base/labels/README.md).
The fitted box is the chord the placement search accepted,
which reaches past the words by however much it beat them,
so a band keeping clear of it gives up ring to a name the reader cannot see there.
Either way the builder is handed world boxes and carves the same way.

## Sizes and drawing

`RibbonBakeSettings.readFromLunaSettings` reads every band knob in one go,
and the bake's caller hands the value to `CellRibbonsBaker`,
so no stage of a bake reads a setting of its own.
It holds:

- whether the bands draw at all;
- the `RibbonStyle` from the player's Visuals tab:
  the width,
  the clearance from the border,
  and whether a cell short of room draws a band anyway (**Always draw a planned band**, on by default);
- the `RibbonPlanRules`:
  the two run lengths,
  and whether an uncontested cell's runs are shortened;
- whether the bands keep clear of the names,
  and by which reading;
- whether the path overlay shows.

The mitre spike limit is authored rather than exposed -
it is the angle past which a corner's mitre becomes a spike,
a property of stroking a polyline rather than of how the readout looks.

With the bands switched off,
`CellRibbonsBaker` builds no `CellRibbonSource` and no `RibbonBakeSurface`:
no planner is resolved,
no name room is measured,
no ring is walked,
and every cell is written back bare -
which also clears the bands an earlier pass laid.
That takes the counting off the rebuild as well as the bands off the map.

`base.ribbon`'s `CellRibbonRenderer` draws the bands in the render band the layer hands the tier,
as the `ribbonBand` of its `OwnerMapBandLayout`;
which band that is,
and why,
is the layer's to say.

## Seeing the path a cell did not use

**Show presence band paths** (Map - Dev, off by default) turns on `base.ribbon`'s path overlay.
`CellRibbonsBaker` traces in the same loop as the bake,
under the toggle the bake's settings carry,
and hands over nothing for every cell while it is off -
which is also what clears the paths a pass taken while it was on left behind.
A pass with the bands switched off traces nothing either,
toggle or not:
a ring drawn from sizes nothing is laid at would be a diagnostic of its own arithmetic.
The cost of that second walk is paid only while the toggle is on.

## What a bake spends its time on

The four phases are [`base.ribbon`](../../../base/ribbon/README.md#what-a-bake-spends-its-time-on)'s.
`CellRibbonsBaker` opens the scope once,
begins a turn per cell named by that cell,
and `CellRibbonSource` marks `plan` and `trace` as it finishes each.
Here `plan` is the owner source's planner counting what a system holds,
which may walk all of it.
What the four leave unaccounted -
the loop itself,
and the overlay's second trace while a player has it on -
is the difference between their sum and the turn.

## What is not here

**What a band says** -
the runs,
the dividers,
which cells band and at what length -
is [`ownermap.ribbon`](../../ribbon/README.md).
**How a band is laid inside its ring** is [`base.ribbon`](../../../base/ribbon/README.md).
**Where the counts come from** is each painting layer's own ranking,
which this package never sees.
