# Presence band vocabulary (`ownermap.ribbon`)

What a cell's presence band is made of,
stated as pure data ahead of any geometry:
a run of colour per stake,
whose runs,
and in what order.
Nothing here traces a ring or emits a triangle;
that is [`ownermap.render.ribbon`](../render/ribbon/README.md).

Part of [the owner-map tier](../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [The plan](#the-plan)
- [The port it reads through](#the-port-it-reads-through)
- [Which cells band, and how loudly](#which-cells-band-and-how-loudly)
- [What is not here](#what-is-not-here)

## The plan

`RibbonPlan` is the whole output:
an ordered list of `RibbonSegment`,
each a colour and a length in band widths.
`RibbonPlan.planCellRibbon` is the rule that produces one,
over a ranked list of `OwnerPresence` -
an owner,
its palette,
and how many stakes it has in the cell.

The grammar it lays down:

- A stake draws a run at the authored run length.
- Two stakes of one owner are parted by an interjection in that owner's own dark shade.
- An owner's whole run is closed off in that same dark shade wherever another owner's follows.
  The divider belongs to the **outgoing** owner,
  so it reads as that owner's stakes ending rather than as a gap belonging to nobody.
- The band's last run is left open,
  so a divider never ends a band.

`RibbonSegmentLengths` pairs the two proportions the grammar is stated in,
and `RibbonPlanRules` pairs those with the shortening below.

Lengths are counts of widths,
never world sizes:
how large a width is in the world is the render side's question,
which is what keeps this whole package pure data.

## The port it reads through

`SystemRibbonPlanner` is the counting itself:
one system in,
one plan out.
A layer's owner source answers it once per bake,
so the band is counted by the rule that painted the cell,
and this package never reaches into the live sector.

## Which cells band, and how loudly

Every cell anything is present in bands.
Contest decides only the length its runs are laid at:

1. A **contested** cell draws at the player's authored lengths -
   this is the readout the bands exist for,
   and the fill cannot report it.
2. An **uncontested** one draws at whatever `UncontestedRibbonRuns` answers,
   which is a single width a stake unless the player turns the shortening off.
   Its fill has already said whose the system is,
   so the band is a tally,
   and a lone owner with many stakes drawn at full length would out-shout the contested cells beside it.

A cell nothing is present in bands under neither reading:
there is no footprint to report.

No knob reaches whether a cell bands,
only how far its runs go.
That is what keeps the one band a map must not be able to lose -
the one saying a system is contested -
out of reach of the settings screen entirely.

**What contested means** depends on whether the cell has a painter,
which the rule takes as a value that can state absence
rather than as an ID no owner happens to carry:

- **With a painter**,
  any other owner holding something that does not stand with the painter.
  A cell painted for an owner that holds nothing in the system still bands,
  against the one rival present,
  because the fill names the painter and the band names somebody else.
- **With none** -
  a cell no owner's fill covers -
  owners holding something that fall into two or more sides.
  There is nobody to be a rival of,
  so a lone side says nothing its fill contradicts.

A sentinel painter would collapse the second case into the first:
every owner differs from an ID nobody carries,
so a lone haven would band at contested length as though it were fought over.

**Who stands with whom** is an `OwnerAffiliation` the rule is handed,
never anything the counts were folded under.
Two allies each keep their own run in their own colour,
and all the affiliation decides is that the cell they hold between them is not a war.
Where nobody stands together the affiliation is `OwnerAffiliation.NONE`,
and every cell is judged owner by owner.

## What is not here

**The count, the painter and the ranking.** What an owner holds in a cell,
which owner the cell was painted for,
and the order the owners come out in
are the painting layer's.
KMU's own layers count every cell by one rule,
described beside their answers,
which [the owner seams](../owners/README.md) link to.

**Every size in the world**,
and every triangle -
[`ownermap.render.ribbon`](../render/ribbon/README.md).
