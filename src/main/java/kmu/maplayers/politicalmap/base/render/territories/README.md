# Territory fills and borders (`render.territories`)

The production draw of the political map: each faction's coloured region, its national border,
and the per-cell province seams and factionless outlines. This is the base layer the labels
overlay sits over - what the player reads as "who holds what".

Part of [the political map](../../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../README.md) for project context.

## Index

- [Building: cells into territories](#building-cells-into-territories)
- [What recedes](#what-recedes)
- [Borders against empty space](#borders-against-empty-space)
- [The draw packets](#the-draw-packets)
- [The split fill: solid, hatched, unfilled](#the-split-fill-solid-hatched-unfilled)
- [Rendering](#rendering)
- [What is not here](#what-is-not-here)

## Building: cells into territories

`TerritoryBuilder` is the orchestration only: it resolves who holds each system, reads the theme,
shapes the cached cells into merged clusters, and drives the two per-item builders. Its job is to
sample every input exactly once so the whole pass keys off one snapshot - which is what lets an
incremental re-shape reuse those same builders on a handful of cells and land on a result
identical to a full rebuild.

- `StyledCellBuilder` bakes one cell. An **owned** cell contributes only its interior seams,
  because its fill and national border belong to the cluster it fuses into. A **factionless**
  cell (decivilised, or uninhabited) does not fuse, so it keeps its own fill and outline and
  resolves both palette slots to the shared neutral colour. Of the two, only decivilised ground
  takes the pass's recede - see [what recedes](#what-recedes) below.
- `FactionTerritoryBuilder` bakes one bloc: its national border, traced across every system it
  holds, and its fill - which it hands to `SplitFillBuilder`, see
  [the split fill](#the-split-fill-solid-hatched-unfilled) below. Fill and border come from the
  same loops, so they cannot drift apart.

`BorderSmoothing` sands spikes and rounds corners of the traced borders; `VertexRuns` flattens
shaped cells into GL vertex runs. Element colours, opacities, and widths come from cascading the
`render.style` theme with each bloc's recede adjustment, asked for through
`PoliticalMapTerritories.resolveBlocStyling` so every part of a bloc resolves from one read.

## What recedes

The Mute and Desaturate toggles sink the background behind a spotlighted bloc. Every non-spotlit
**bloc** recedes through `resolveBlocStyling`, and so does **decivilised** ground: a dead colony
carries a fill of its own, so leaving it at full strength lets it out-read the bloc the spotlight
is meant to isolate. Muting dims it; desaturating recolours it off the same desaturation palette a
receded bloc uses, rather than the neutral colour it paints in normally.

**Uninhabited** ground is the exception. It is the empty backdrop the whole map is drawn over
rather than something the spotlight competes with, and its faint outline is what gives the sector
its shape, so it stays at full strength under every recede.

Only the *filter* recede reaches decivilised ground - it is the one whose receded ground is "the
rest of the sector". The alliances view's non-allied recede describes factions, which decivilised
ground is not, so it leaves it alone. Off filter the pass's recede is the identity, so an
unfiltered map draws its dead worlds untouched.

The rule itself is not here: `StyledCellBuilder` asks
[`FactionlessStyleResolver`](../style/README.md), which also decides which of the two factionless
categories a cell falls in. One classification drives both, so a cell cannot take the decivilised
style yet miss the recede that style draws under.

## Borders against empty space

Every border edge is shaped the same way, whatever sits across it. Against another
organised entity (a rival faction or an alliance) and against *empty* space alike -
an uncontrolled star with no owner, whether never-settled or decivilised - the edge
keeps the mutual midline plus its inward border channel. So a faction cuts off
halfway to a dead star exactly as it does halfway to a rival, and the dead star's
own cell draws its inset outline - and, for a decivilised system, its neutral fill -
in the neutral style on the far side of that channel.

Those factionless fills are per cell rather than per cluster: factionless ground never fuses into
a cluster, so it has no traced region to fill from and each cell tessellates its own outline
instead. Which is why `StyledCell` carries fill triangles at all, where an owned cell leaves them
empty and takes its fill from its `FactionTerritory`.

The two player settings under Territory reach on the **Political map - visuals**
tab - *Uncontrolled systems give way to faction territory* and *Frontier keep-out* -
describe an asymmetric treatment of empty space that the draw does not yet apply, so
changing them does not move a border.

## The draw packets

`PoliticalMapTerritories` is the built state a rebuild produces and an incremental refresh edits in
place: the two draw lists (`FactionTerritory` per owned faction, `StyledCell` per cell) plus the
retained ownership, theme, and filter inputs a re-shape needs. `FactionTerritory` carries one
faction's fill triangles, contested-hatch segments, and border loops; `StyledCell` carries one
cell's fill, outline, and interior seams.

## The split fill: solid, hatched, unfilled

A bloc's footprint is traced as one border whatever its members' fills; the fill is what varies per
system inside it, across three states. **Solid** is the default - a bloc that only dominates fills
its whole region from that one border and pays nothing for the split, the common case. The two
exceptions each carve a sub-region out of the solid, hatched and unfilled, and both are decided
upstream in `politics.ownership`; this section is how the draw honours them.

Two classes own it, split along the line between deciding and drawing. `FillSplit` is the pure
partition - which state each member system draws in, decidable from plain id sets, plus the
coincident-neighbour rule below. `SplitFillBuilder` turns that partition into triangles and hatch
lines; it is built per territory around the trace context the whole fill shares (the cells, their
grouping, the border trace, the smoothed border loops), and it also owns the choice of whether a
territory splits at all or fills solid as one region.

**Hatched.** When the filter spotlights one bloc, its whole footprint - the systems it dominates
plus the ones it merely contests - clusters into a single `FactionTerritory` under one national
frontier, and the fill splits: solid where the bloc dominates, a pre-clipped diagonal hatch where it
is only present ("mine, but contested").

Each of those two states fills from its own traced rings, not from its members' individual cells.
The footprint's one grouping key is suffixed per state, so the border tracer - which fuses same-key
cells - traces the dominant members as one region and the contested members as another, while every
system outside the footprint keeps its real key and the two regions' outer edge therefore lands
exactly where the national border draws it. Each state names the other's members as *coincident*
neighbours, so the boundary they share insets by nothing and the solid and hatched fills abut on the
raw cell edge. Filling per cell instead would truncate each member's kept edges against its own
inset boundary edges, and two members meeting at a corner against a rival would pull their shared
edge back by different amounts - opening an unfilled wedge on the more-receded side.

The footprint's interior divisions carry no geometry of their own: the whole footprint shares one
grouping key, so a solid/hatch transition is an interior seam like any other and its two cells
already stroke it in the province style. Giving those divisions the frontier's own border style
instead would put a heavy line under the faction's name label, which the label has to stay legible
over, and would need raw cell edges to draw - untrimmed, so they overshoot the inset frontier and
poke out into the border channel. The province seam has neither problem: it is faint, and the cell
shaper truncates it where it runs into a pulled-in border.

**Unfilled.** A claim extension - a system a bloc claims but does not hold - sits inside its bloc's
one border for outline and label but paints no fill at all, so the split simply skips it. Same
shape as the hatch sub-region (one border, a sub-region drawn differently) but the sub-region is
empty rather than hatched, so a held/claimed boundary reads as the seam where the fill stops inside
a continuous frontier. Which systems are unfilled is resolved in
[`politics.ownership`](../../politics/ownership/README.md); this package only honours the set.

## Rendering

`TerritoryRenderer` is a pure GL loop over an already-baked `PoliticalMapTerritories`: it scales
world coordinates into map space and strokes/fills the flattened runs, with no knowledge of
settings or how the runs were shaped.

## What is not here

The *theme and the styling resolvers* (what colour/width each category and bloc draws in) live in
[`render.style`](../style/README.md); this package consumes them, it does not decide them. The *name overlay*
that sits on top is the framework's [`base.labels`](../../../../base/labels/README.md), fed the
names and shades this layer resolves in `render.labels.anchor`. The border-ring trace shared with the label anchor search
(`render.PoliticalBorderTrace`) stays at the `render` root because more than one concern uses it;
the low-level GL run emission is a generic helper in KMLib (`kmlib.opengl.GlRuns`). The
*incremental refresh* that folds per-system ownership changes into the packets is
`render.IncrementalPoliticsRefresh`, at the render root alongside the plugin and the per-frame
cache that drives it - the composition root that wires these feature packages together. *Which*
change triggers a full rebuild here and which one only re-shapes a handful of cells is
[the caching notes](../../../../../../../../../docs/dev/caching.md).
