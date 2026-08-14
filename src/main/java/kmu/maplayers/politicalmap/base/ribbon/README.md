# Presence band vocabulary (`base.ribbon`)

What a cell's presence band is made of, stated as pure data ahead of any geometry: a run of colour
per colony, whose runs, and in what order. Nothing here traces a ring or emits a triangle - that is
[`base.render.ribbon`](../render/ribbon/README.md) - and nothing here counts colonies either, which
is each painting mechanic's own business.

Part of [the political map](../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../README.md) for project context.

## Index

- [The plan](#the-plan)
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
pairs those with the gate below. `RibbonPlanInputs` carries the rules plus the palette port, so a
planner is handed one object rather than four loose knobs.

Lengths are counts of widths, never world sizes: how large a width is in the world is the render
side's question, which is what lets this whole package be exercised on literals.

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

1. Some bloc **other than the cell's own painter** is present. Unconditional - this is the readout
   the bands exist for, and the fill cannot report it.
2. The cell is populated at all and the player asked for it, which is `UncontestedCellBands`.

Written as two arms rather than one settings-dependent rule so no switch can reach the first kind.
The second arm also carries whether its bands draw their runs at a single width, so a large
uncontested holding cannot out-shout the contested cells beside it. A cell nothing is present in
bands under neither arm.

## What is not here

**The counts.** Where a bloc's colony count comes from is the mechanic's, so it lives with the
mechanic: [`dominance.ribbon`](../../dominance/ribbon/README.md) counts a held cell off the very
footprints the fill was ranked from, and [`claims.ribbon`](../../claims/ribbon/README.md) counts a
claimed cell off the contest that settled the claim. Keeping the counting out of here is what makes
one band mean one thing on every view.

**Every size in the world**, and every triangle - [`base.render.ribbon`](../render/ribbon/README.md).
