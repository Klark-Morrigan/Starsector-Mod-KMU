# Presence band vocabulary (`base.ribbon`)

What a cell's presence band is made of, stated as pure data ahead of any geometry: a run of colour
per colony, whose runs, and in what order - and, since one band must mean one thing on every layer,
the count itself. Nothing here traces a ring or emits a triangle; that is
[`base.render.ribbon`](../render/ribbon/README.md).

Part of [the political map](../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../README.md) for project context.

## Index

- [The plan](#the-plan)
- [One count for both mechanics](#one-count-for-both-mechanics)
- [The two ports](#the-two-ports)
- [Which cells band at all](#which-cells-band-at-all)
- [What is not here](#what-is-not-here)

## The plan

`RibbonPlan` is the whole output: an ordered list of `RibbonSegment`, each a colour and a length in
band widths. `RibbonPlan.planCellRibbon` is the rule that produces one, over a ranked list of
`BlocPresence` - a bloc, its palette, and how many colonies it holds in the cell.

The grammar it lays down:

- A colony draws a run at the authored run length.
- Two colonies of one bloc are parted by an interjection in that bloc's own dark shade.
- A bloc's whole run is closed off in that same dark shade wherever another bloc's follows. The
  divider belongs to the **outgoing** bloc, so it reads as that bloc's holdings ending rather than
  as a gap belonging to nobody.
- The band's last run is left open, so a divider never ends a band.

`RibbonSegmentLengths` pairs the two proportions the grammar is stated in, and `RibbonPlanRules`
pairs those with the gate below. `RibbonPlanInputs` carries the rules, the palette port and the
bake's own `HolderPass`, so a planner is handed one object rather than four loose knobs - and so
both mechanics of a composed planner count off a single walk of each system.

Lengths are counts of widths, never world sizes: how large a width is in the world is the render
side's question, which is what lets this whole package be exercised on literals.

## One count for both mechanics

`ColonyCellRibbons` counts every cell on every layer: the pass's **known projection** of the
system's colonies, folded into a count per bloc under the pass's grouping.

That projection is the one filter, and it is the fog - `Markets.isCountedAsColony` under the pass's
`shouldIncludeUndiscoveredMarkets`, which is the same arm the cell classification paints on and the
same override, sampled once per bake, that the fills are resolved under. So a colony the player has
not found is left out, and the dev reveal puts it back on band and fill together.

Past it, nothing about a colony is asked: hidden, unlisted by the economy, weighed or never weighed,
each counts as the one colony it is.

It is here rather than beside each mechanic because a band reports how a system splits, which is a
fact about the system rather than about the mechanic reading it. Counted per mechanic, the two
answers drifted: an unlisted station reached neither band while both hover boxes named it, a
concealed colony fed a weight on one layer and a sibling term on the other, and the dev reveal
reached the held count while the claim count went on hiding what the fill was drawing.

What each mechanic still supplies is the **painter** and the **ranking**, which are what genuinely
differ - whose fill the band sits inside, and the order that fill decided. A bloc the mechanic never
ranked draws behind the ranked ones in id order, since any place among them would claim it took part
in a contest it never entered.

## The two ports

Both live reads the rule would otherwise make are inverted here, so every case above tests on
hand-built values:

- `BlocPaletteReader` - a bloc's bright and dark shades. `SectorBlocPalettes` is the live
  implementation, resolving them off the sector's factions.
- `SystemRibbonPlanner` - the counting itself, one system in, one plan out. The seam each painting
  mechanic implements.

## Which cells band at all

Two arms of one gate, stated as a disjunction because they answer different questions and only one
of them is about contest:

1. The cell is **contested**. Unconditional - this is the readout the bands exist for, and the fill
   cannot report it.
2. The cell is populated at all and the player asked for it, which is `UncontestedCellBands`.

Written as two arms rather than one settings-dependent rule so no switch can reach the first kind.
The second arm also carries whether its bands draw their runs at a single width, so a large
uncontested holding cannot out-shout the contested cells beside it. A cell nothing is present in
bands under neither arm.

**What contested means** depends on whether the cell has a painter, which the rule takes as a value
that can state absence rather than as an id no bloc happens to carry:

- **With a painter**, any other bloc holding something. A system claimed by decree whose decreed
  bloc holds nothing there still bands, against the one rival present, because the fill names the
  decreed bloc and the band names somebody else.
- **With none** - an unclaimed cell, which no bloc's fill covers - two or more blocs holding
  something. There is nobody to be a rival of, so a lone holder says nothing its fill contradicts.

Under a sentinel painter the second case collapsed into the first, every bloc differing from an id
nobody carries, and a lone haven banded at contested length as though it were fought over.

## What is not here

**The painter and the ranking.** Which bloc a cell was painted for, and the order the blocs come
out in, are the painting mechanic's: [`dominance.ribbon`](../../dominance/ribbon/README.md) ranks a
held cell by the footprints its fill was decided from, and
[`claims.ribbon`](../../claims/ribbon/README.md) ranks a claimed cell by the contest that settled
the claim.

**Where the colonies come from.** The walk itself is `SystemColoniesIndex`'s, reached through the
pass on `RibbonPlanInputs` - so a planner counts what the bake already read rather than being able
to walk a system again.

**Every size in the world**, and every triangle - [`base.render.ribbon`](../render/ribbon/README.md).
