# Territory fills and borders (`render.territories`)

What the production draw of the political map is made of: each faction's coloured cluster, its
national border, and the per-cell province seams and factionless outlines, baked into the
framework's draw packets. This is the base layer the labels overlay sits over - what the player
reads as "who holds what". The emission itself is the framework's, reached through a seam this
package's built state satisfies.

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

- `StyledCellBuilder` bakes one cell, choosing which form of `StyledCell` it takes. An **owned**
  cell becomes a `FusedCell` and contributes only its interior seams, because its fill and national
  border belong to the cluster it fuses into - the form has no slot for either. A **factionless**
  cell (decivilised, or uninhabited) does not fuse, so it becomes a `LoneCell` carrying its own fill
  and outline, and resolves both palette slots to the shared neutral colour. Of the two, only
  decivilised ground takes the pass's recede - see [what recedes](#what-recedes) below.
- `FactionTerritoryBuilder` bakes one bloc into a `StyledClusterGroup`: every body it holds, each
  with its national border traced across the systems in it, and each body's fill - which it hands
  to the framework's `SplitFillBuilder`, see
  [the split fill](#the-split-fill-solid-hatched-unfilled) below. Fill and border come from the
  same loops, so they cannot drift apart.

The shaping the two builders drive is the framework's, in
[`base.render.clusters`](../../../../base/render/clusters/README.md): `BorderSmoothing` sands spikes
and rounds corners of the traced borders, `VertexRuns` flattens shaped cells into GL vertex runs,
and `StyledCell`, `StyledCluster` and `StyledClusterGroup` are the packets they bake into.
Element colours, opacities, and widths come from cascading the `base.theme` records with each
bloc's recede adjustment, asked for through `PoliticalMapTerritories.resolveBlocStyling` so every
part of a bloc resolves from one read.

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
a cluster, so it has no traced cluster to fill from and each cell tessellates its own outline
instead. Which is why a `LoneCell` carries fill triangles at all, where an owned cell has no fill of
its own to carry and takes it from the `StyledCluster` it fused into.

The two player settings under Territory reach on the **Dev**
tab - *Uncontrolled systems give way to faction territory* and *Frontier keep-out* -
describe an asymmetric treatment of empty space that the draw does not yet apply, so
changing them does not move a border.

## The draw packets

`PoliticalMapTerritories` is the built state a rebuild produces and an incremental refresh edits in
place: the two draw lists (`StyledClusterGroup` per bloc, `StyledCell` per cell) plus the retained
ownership, theme, and filter inputs a re-shape needs. Both packets are the framework's, described
in [`base.render.clusters`](../../../../base/render/clusters/README.md) - a bloc's bodies with the
paints they share, each body carrying its own fill triangles, contested-hatch segments, outer loop
and enclaves; and one cell's seam, or its own fill and outline.

A bloc in two places is two bodies under one group, not one record holding both. That is what lets
a fill stop at the frontier of the body it is in: each body's fill is clipped to its own loops,
where a single soup over the whole bloc could only be clipped to all of them at once.

Holding the framework's packets is what lets it satisfy `ClusterDrawLists` outright, so the
framework's emission paints this layer without either side naming the other: the two maps it hands
over are the two it already keeps, and the bloc keys stay opaque across the seam. What is
political is entirely upstream of it - which is why `FactionTerritoryBuilder` keeps its name while
producing a record that says nothing about factions.

It also answers the cursor's candidate-loop read, flattening a bloc's bodies into the one list the
highlight hit-tests against - and retaining it, because the highlight memoises its resolved halo on
that list's identity and a fresh list per frame would re-clip the wash sixty times a second. The
retained answer is keyed on the bloc's cluster group by identity, so a refresh that replaces the
group recomputes once and a refresh that does not costs nothing.

It also records each drawn cell's shaped fill polygon alongside its `StyledCell`, written and
dropped by the same two calls, and that pairing is what lets it answer `base.hover`'s
`PaintedCellShapes` - and through it `MapHoverTargets` - directly: the shapes a cursor is tested
against are the shapes this frame painted, never a re-derivation that could drift from them. The
highlight reads the same shapes through `render/hover`'s adapter, so the cursor and the halo cannot
disagree about what was drawn.

## The split fill: solid, hatched, unfilled

A bloc's footprint is traced as one border whatever its members' fills; the fill is what varies per
system inside it, across three states. **Solid** is the default - a bloc that only dominates fills
its whole cluster from that one border and pays nothing for the split, the common case. The two
exceptions each carve a sub-cluster out of the solid, hatched and unfilled, and both are decided
upstream in `politics.holders`; this section is how the draw honours them.

The machinery is the framework's - `FillSplit` partitions the members and `SplitFillBuilder`
tessellates each state as its own cluster inside the one border; see
[`base.render.clusters`](../../../../base/render/clusters/README.md) for how, and why a state fills
from its own traced rings rather than from its members' cells. What is political is which systems
land in the two non-solid sets, and what that reads as on the map.

**Hatched.** When the filter spotlights one bloc, its whole footprint - the systems it dominates
plus the ones it merely contests - clusters under one bloc, and each body's fill splits inside its
own frontier: solid where the bloc dominates, a pre-clipped diagonal hatch where it is only present
("mine, but contested"). The states are traced once for the bloc and clipped per body, so a bloc
contested in two places pays one trace and reads correctly in both.

The footprint's interior divisions carry no geometry of their own: the whole footprint shares one
owner, so a solid/hatch transition is an interior seam like any other and its two cells
already stroke it in the province style. Giving those divisions the frontier's own border style
instead would put a heavy line under the faction's name label, which the label has to stay legible
over, and would need raw cell edges to draw - untrimmed, so they overshoot the inset frontier and
poke out into the border channel. The province seam has neither problem: it is faint, and the cell
shaper truncates it where it runs into a pulled-in border.

**Unfilled.** A claim extension - a system a bloc claims but does not hold - sits inside its bloc's
one border for outline and label but paints no fill at all, so the split simply skips it. Same
shape as the hatch sub-cluster (one border, a sub-cluster drawn differently) but the sub-cluster is
empty rather than hatched, so a held/claimed boundary reads as the seam where the fill stops inside
a continuous frontier. Which systems are unfilled is resolved in
[`politics.holders`](../../politics/holders/README.md); this package only honours the set.

## Rendering

Nothing here paints. The draw is the framework's `ClusterRenderer`, reached through the
`ClusterDrawLists` seam `PoliticalMapTerritories` satisfies; see
[`base.render.clusters`](../../../../base/render/clusters/README.md) for what it emits and in
what order.

## What is not here

The *styling resolvers* (what colour/width each category and bloc draws in) live in
[`render.style`](../style/README.md), over the framework's
[`base.theme`](../../../../base/theme/README.md) records they read the player's choices
out of; this package consumes both, it does not decide either. The *name overlay*
that sits on top is the framework's [`base.labels`](../../../../base/labels/README.md), fed the
names and shades this layer resolves in `render.labels.anchor`. The *shape work* the two builders
drive - the border-ring trace, the smoothing passes, the vertex packing, the `StyledCell` packet,
the split-fill machinery, and the GL emission itself - is the framework's
[`base.render.clusters`](../../../../base/render/clusters/README.md), which knows nothing of who
holds what; the low-level GL run emission is a generic helper in KMLib (`kmlib.opengl.GlRuns`). The
*incremental refresh* that folds per-system ownership changes into the packets is
`render.IncrementalPoliticsRefresh`, at the render root alongside the plugin and the per-frame
cache that drives it - the composition root that wires these feature packages together. *Which*
change triggers a full rebuild here and which one only re-shapes a handful of cells is
[the caching notes](../../../../../../../../../docs/dev/caching.md).
