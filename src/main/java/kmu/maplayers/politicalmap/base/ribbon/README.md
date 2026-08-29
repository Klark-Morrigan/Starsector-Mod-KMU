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
- [Which cells band, and how loudly](#which-cells-band-and-how-loudly)
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
pairs those with the shortening below. `RibbonPlanInputs` carries the rules, the palette port and the
`HolderPass` the bake was handed, so a planner is handed one object rather than four loose knobs -
and so both mechanics of a composed planner count off a single walk of each system.

Lengths are counts of widths, never world sizes: how large a width is in the world is the render
side's question, which is what lets this whole package be exercised on literals.

## One count for both mechanics

`ColonyCellRibbons` counts every cell on every layer: the pass's **habitation projection** of the
system's colonies, folded into a count per bloc under the pass's grouping.

That projection is the pass's `ColonyKnowledge` plus the one kind test habitation turns on: the
fog (`MarketVisibility.isCountedAsColony`), the gates holding back the shapes a bare fog would
leak, and then the derelicts nobody has ever been aboard. It is the same reading the cell beneath
is classified on, sampled once per bake, so a colony the player has not found is left out of band
and fill together and the dev reveal puts it back on both.

Habitation and not the wider listing, which is where a band and the hover box over it deliberately
differ. A run stands for somebody holding something in the system; an abandoned station the player
has seen is named in the box at nought and has never had anybody on it, so it raises no run - and a
system holding nothing else draws as empty backdrop with no band at all.

The rule is threaded rather than re-read because it is more than one bit now. A counter handed the
reveal and not the gates would count a concealed colony the fill declines to draw, which is the drift
the one shared value makes unexpressible.

Past the projection, nothing about a colony is asked: unlisted by the economy, weighed or never
weighed, each counts as the one colony it is.

It is here rather than beside each mechanic because a band reports how a system splits, which is a
fact about the system rather than about the mechanic reading it. Counted per mechanic, the two
answers drifted: a colony the economy never listed reached neither band while both hover boxes named
it, a concealed colony fed a weight on one layer and a sibling term on the other, and the dev reveal
reached the held count while the claim count went on hiding what the fill was drawing.

What each mechanic still supplies is the **painter** and the **ranking**, which are what genuinely
differ - whose fill the band sits inside, and the order that fill decided. A bloc the mechanic never
ranked draws behind the ranked ones in id order, since any place among them would claim it took part
in a contest it never entered.

## The two ports

Both live reads the rule would otherwise make are inverted here, so every case above turns on
hand-built values:

- `BlocPaletteReader` - a bloc's bright and dark shades. `SectorBlocPalettes` is the live
  implementation, resolving them off the sector's factions.
- `SystemRibbonPlanner` - the counting itself, one system in, one plan out. The seam each painting
  mechanic implements.

## Which cells band, and how loudly

Every cell anything is present in bands. Contest decides only the length its runs are laid at:

1. A **contested** cell draws at the player's authored lengths - this is the readout the bands
   exist for, and the fill cannot report it.
2. An **uncontested** one draws at whatever `UncontestedRibbonRuns` answers, which is a single
   width a colony unless the player turns the shortening off. Its fill has already said whose the
   system is, so the band is a tally, and a large lone holding drawn at full length would
   out-shout the contested cells beside it.

A cell nothing is present in bands under neither reading: there is no footprint to report.

No knob reaches whether a cell bands, only how far its runs go. That is what keeps the one band a
map must not be able to lose - the one saying a system is contested - out of reach of the settings
screen entirely.

**What contested means** depends on whether the cell has a painter, which the rule takes as a value
that can state absence rather than as an id no bloc happens to carry:

- **With a painter**, any other bloc holding something that does not stand with the painter. A
  system claimed by decree whose decreed bloc holds nothing there still bands, against the one rival
  present, because the fill names the decreed bloc and the band names somebody else.
- **With none** - an unclaimed cell, which no bloc's fill covers - blocs holding something that fall
  into two or more sides. There is nobody to be a rival of, so a lone side says nothing its fill
  contradicts.

Under a sentinel painter the second case collapsed into the first, every bloc differing from an id
nobody carries, and a lone haven banded at contested length as though it were fought over.

**Who stands with whom** is a `BlocAffiliation` the rule is handed, never the grouping the counts
were folded under. Two allies each keep their own run in their own colour - merging them is the
alliances layer's job - and all the affiliation decides is that the cell they hold between them is
not a war. Where nothing groups factions it stands nobody together, and every cell reads as it
always has.

## What is not here

**The painter and the ranking.** Which bloc a cell was painted for, and the order the blocs come
out in, are the painting mechanic's: [`dominance.ribbon`](../../dominance/ribbon/README.md) ranks a
held cell by the footprints its fill was decided from, and
[`claims.ribbon`](../../claims/ribbon/README.md) ranks a claims-layer cell by the contest over its
system - including the cells no claim covers, which reach the rule above with no painter at all.

**Where the colonies come from.** The walk itself is `SystemColoniesIndex`'s, reached through the
pass on `RibbonPlanInputs` - so a planner counts what the bake already read rather than being able
to walk a system again.

**Every size in the world**, and every triangle - [`base.render.ribbon`](../render/ribbon/README.md).
