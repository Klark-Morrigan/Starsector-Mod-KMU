# Presence band vocabulary (`ownermap.ribbon`)

What a cell's presence band is made of,
stated as pure data ahead of any geometry:
a run of colour per colony,
whose runs,
and in what order -
and,
since one band must mean one thing on every layer,
the count itself.
Nothing here traces a ring or emits a triangle;
that is [`ownermap.render.ribbon`](../render/ribbon/README.md).

Part of [the owner-map tier](../README.md),
in Klark Morrigan's Utilities;
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [The plan](#the-plan)
- [One count for every mechanic](#one-count-for-every-mechanic)
- [The ports it reads through](#the-ports-it-reads-through)
- [Which cells band, and how loudly](#which-cells-band-and-how-loudly)
- [What is not here](#what-is-not-here)

## The plan

`RibbonPlan` is the whole output:
an ordered list of `RibbonSegment`,
each a colour and a length in band widths.
`RibbonPlan.planCellRibbon` is the rule that produces one,
over a ranked list of `BlocPresence` -
a bloc,
its palette,
and how many colonies it holds in the cell.

The grammar it lays down:

- A colony draws a run at the authored run length.
- Two colonies of one bloc are parted by an interjection in that bloc's own dark shade.
- A bloc's whole run is closed off in that same dark shade wherever another bloc's follows.
  The divider belongs to the **outgoing** bloc,
  so it reads as that bloc's holdings ending rather than as a gap belonging to nobody.
- The band's last run is left open,
  so a divider never ends a band.

`RibbonSegmentLengths` pairs the two proportions the grammar is stated in,
and `RibbonPlanRules` pairs those with the shortening below.
`RibbonPlanInputs` carries the rules,
the palette port,
the `BlocAffiliation` the contest is judged against and the `HolderPass` the bake was handed,
so a planner is handed one object rather than a handful of loose knobs -
and so every mechanic of a composed planner counts off a single walk of each system
and judges its contests against one affiliation.

Lengths are counts of widths,
never world sizes:
how large a width is in the world is the render side's question,
which is what keeps this whole package pure data.

## One count for every mechanic

`ColonyCellRibbons` counts every cell on every layer:
the pass's **habitation projection** of the system's colonies,
folded into a count per bloc under the pass's grouping.

That projection is the pass's `ColonyKnowledge` plus the kind tests habitation turns on:
the fog (`MarketVisibility.isCountedAsColony`),
the gates holding back the shapes a bare fog would leak,
then the derelicts nobody has ever been aboard,
and finally the decivilised worlds where the pass's `DecivilisedColonyHabitation` counts them as unpopulated.
It is the same reading the cell beneath is classified on,
sampled once per bake,
so a colony the player has not found is left out of band
and fill together and the dev reveal puts it back on both.

Habitation and not the wider listing,
which is where a band and the hover box over it deliberately differ.
A run stands for somebody holding something in the system;
an abandoned station the player has seen is named in the box at nought
and has never had anybody on it,
so it raises no run -
and a system holding nothing else draws as empty backdrop with no band at all.

The rule is threaded rather than re-read because it is more than one bit.
A counter handed the reveal and not the gates would count a concealed colony the fill declines to draw,
which is the drift the one shared value makes unexpressible.

Past the projection,
nothing about a colony is asked:
unlisted by the economy,
weighed or never weighed,
each counts as the one colony it is.

It is here rather than beside each mechanic because a band reports how a system splits,
which is a fact about the system rather than about the mechanic reading it.
Counted per mechanic,
two layers could answer differently about one system:
a colony left out of one layer's band while its hover box names it,
or the dev reveal reaching one layer's count
while another's goes on hiding what its fill is drawing.

What each mechanic still supplies is the **painter** and the **ranking**,
which are what genuinely differ -
whose fill the band sits inside,
and the order that fill decided.
A bloc the mechanic never ranked draws behind the ranked ones in ID order,
since any place among them would state it took part in a contest it never entered.

## The ports it reads through

Both live reads the rule would otherwise make are inverted,
so nothing above reaches into the live sector itself:

- `SystemRibbonPlanner` -
  the counting itself,
  one system in,
  one plan out.
  The seam each painting mechanic implements,
  and this package's own.
- `BlocPaletteReader` -
  a bloc's bright and dark shades,
  with `SectorBlocPalettes` as the live implementation.
  It is [`ownermap.render.style`](../render/style/README.md)'s rather than this package's:
  what colour a bloc is is not a question about bands alone.

## Which cells band, and how loudly

Every cell anything is present in bands.
Contest decides only the length its runs are laid at:

1. A **contested** cell draws at the player's authored lengths -
   this is the readout the bands exist for,
   and the fill cannot report it.
2. An **uncontested** one draws at whatever `UncontestedRibbonRuns` answers,
   which is a single width a colony unless the player turns the shortening off.
   Its fill has already said whose the system is,
   so the band is a tally,
   and a large lone holding drawn at full length would out-shout the contested cells beside it.

A cell nothing is present in bands under neither reading:
there is no footprint to report.

No knob reaches whether a cell bands,
only how far its runs go.
That is what keeps the one band a map must not be able to lose -
the one saying a system is contested -
out of reach of the settings screen entirely.

**What contested means** depends on whether the cell has a painter,
which the rule takes as a value that can state absence
rather than as an ID no bloc happens to carry:

- **With a painter**,
  any other bloc holding something that does not stand with the painter.
  A cell painted for a bloc that holds nothing in the system still bands,
  against the one rival present,
  because the fill names the painter and the band names somebody else.
- **With none** -
  a cell no bloc's fill covers -
  blocs holding something that fall into two or more sides.
  There is nobody to be a rival of,
  so a lone side says nothing its fill contradicts.

A sentinel painter would collapse the second case into the first:
every bloc differs from an ID nobody carries,
so a lone haven would band at contested length as though it were fought over.

**Who stands with whom** is a `BlocAffiliation` the rule is handed,
never the grouping the counts were folded under.
Two allies each keep their own run in their own colour -
merging them is a grouping's job -
and all the affiliation decides is that the cell they hold between them is not a war.
Where nothing groups factions it stands nobody together,
and every cell is judged bloc by bloc.

## What is not here

**The painter and the ranking.** Which bloc a cell was painted for,
and the order the blocs come out in,
are the painting layer's:
each mechanic ranks a cell by whatever its fill was decided from,
and a cell the mechanic leaves unpainted reaches the rule above with no painter at all.

**Where the colonies come from.** The walk itself is `SectorPassIndex`'s,
reached through the pass on `RibbonPlanInputs` -
so a planner counts what the bake already read rather than being able to walk a system again.

**Every size in the world**,
and every triangle -
[`ownermap.render.ribbon`](../render/ribbon/README.md).
