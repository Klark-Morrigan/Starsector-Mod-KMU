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
shapes the cached cells into merged clusters, and drives the per-item builders. Its job is to
sample every input exactly once so the whole pass keys off one snapshot - which is what lets an
incremental re-shape reuse those same builders on a handful of cells and land on a result
identical to a full rebuild.

- `StyledCellBuilder` bakes one cell, choosing which form of `StyledCell` it takes. An **owned**
  cell becomes a `FusedCell` and contributes only its interior seams, because its fill and national
  border belong to the cluster it fuses into - the form has no slot for either. A **factionless**
  cell (settled but unheld, or uninhabited) does not fuse, so it becomes a `LoneCell` carrying its
  own fill and outline, and resolves both palette slots to the shared neutral colour. Of the two,
  only a settled cell takes the pass's recede - see [what recedes](#what-recedes) below.
- `FactionTerritoryBuilder` bakes one bloc into a `StyledClusterGroup`: every body it holds, each
  with its national border traced across the systems in it, and each body's fill - which it hands
  to the framework's `SplitFillBuilder`, see
  [the split fill](#the-split-fill-solid-hatched-unfilled) below. Fill and border come from the
  same loops, so they cannot drift apart.

No presence band is laid here. A band is baked *around* the cluster names, and the names are
fitted after this build - each inside the border these very cells trace - so the bands are a stage
of their own afterwards, over the shapes this one recorded and through the same reading of the
sector this one was handed. `CellRibbonsBaker` (in
[`render.ribbon`](../ribbon/CellRibbonsBaker.java)) drives it, and both the full rebuild and the
incremental refresh reach it through the same call, which is what keeps an incrementally-updated
band identical to the one a full rebuild would lay.

The shaping the cell and territory builders drive is the framework's, in
[`base.render.clusters`](../../../../base/render/clusters/README.md): `BorderSmoothing` sands spikes
and rounds corners of the traced borders, `VertexRuns` flattens shaped cells into GL vertex runs,
and `StyledCell`, `StyledCluster` and `StyledClusterGroup` are the packets they bake into.
Element colours, opacities, and widths come from cascading the `base.theme` records with each
bloc's recede adjustment, asked for through `PoliticalMapTerritories.resolveBlocStyling` so every
part of a bloc resolves from one read.

## What recedes

The Mute and Desaturate toggles sink the background behind a spotlighted bloc. Every non-spotlit
**bloc** recedes through `resolveBlocStyling`, and so does a **settled** factionless cell: it
carries a fill of its own, so leaving it at full strength lets it out-read the bloc the spotlight
is meant to isolate. Muting dims it; desaturating recolours it off the same desaturation palette a
receded bloc uses, rather than the neutral colour it paints in normally.

An **uninhabited** cell is the exception. It is the empty backdrop the whole map is drawn over
rather than something the spotlight competes with, and its faint outline is what gives the sector
its shape, so it stays at full strength under every recede.

A settled cell **the spotlit bloc lives in** is the second exception. The recede clears away what
the pick is not, and a system the pick has a colony in is not that - sinking it would hide the
pick's own presence for the sole reason that this layer's holding rule could not attribute it to
them. On the claims view that is routine: pirates, the Path, and independents may not claim, so
their own colonies always land in a holderless cell.

Such a cell paints the neutral **lifted toward white** by `presenceLightening`, not the bloc's
colours - nobody holds the system on this layer, and its shades would state exactly the claim the
view reports it does not have. The lift is what makes sparing the recede visible at all: the
neutral a factionless cell paints in and the Independent grey the background sinks from are the
same grey, so a merely-unreceded cell sits at the value the background started at and reads as part
of it. `desaturationDarkening` sinks the backdrop, `presenceLightening` raises the spared cell,
and the pair is what separates them - by value only, so a grey stays the same grey.

Only the *filter* recede reaches a settled factionless cell - it is the one whose backdrop is "the
rest of the sector". The alliances view's non-allied recede describes factions, which such a
cell is not, so it leaves it alone. Off filter the pass's recede is the identity, so an
unfiltered map draws its unheld systems untouched.

The rule itself is not here: `StyledCellBuilder` asks
[`FactionlessStyleResolver`](../style/README.md), which also decides which of the two factionless
categories a cell falls in. One classification drives both, so a cell cannot take the settled
style yet miss the recede that style draws under.

That classification reads the pass's **inhabited-system set**, not the holder map. `TerritoryBuilder`
scans it once per rebuild through `PoliticalMapInhabitation.readInhabitedSystemIds` - the same rule
that decided the system seeds a cell at all - and `PoliticalMapTerritories` retains it, so the
incremental re-shape classifies against exactly what the full build used. Deriving emptiness from
the holder map instead would make every view whose holding rule admits only some factions report its
unheld systems as empty space.

The presence exception rides beside it as the **spotlit-presence set**, read through
`FilteredPolitics.findPresentSystemIds` - the same presence rule `resolveFilteredHolder`
keeps a spotlit bloc visible by, so "the pick lives here" means one thing across the map. Both read
it off the colonies somebody lives on rather than off the dominance weights, since a bloc whose only
foothold in a system is a colony the economy does not list wins nothing there and lives there all
the same.

Both come off one value, `HolderPass.readHabitationIn` - the colonies somebody lives on, with the
blocs folded from those very colonies. The classification asks its emptiness where the presence read
asks its bloc set, so presence is a partition of the set emptiness is asked of and a cell cannot be
called empty space while the spotlight keeps a bloc's fill over it. Two call sites picking the same
projection would be a convention a later edit could break; one value handed to both is not.

It is asked only of the inhabited systems the holding left out, since a system somebody holds
already draws in that bloc's territory - and which view is painting decides whether anything is
left. The claims views leave a system unheld whenever nobody claims it, so a bloc's own unclaimed
colonies land here and this read is what spares their cells. The faction and alliance views resolve
holding through `FilteredPolitics` itself, which has already kept every system the bloc is present
in, so what is left over is the systems it is absent from and the read costs the set arithmetic and
returns empty.

Both sets sit with the holder map in **`SystemOccupancy`** - who is in each system - rather than
beside the `FilterSnapshot` the spotlight's fixed answers live in, because all three move between
rebuilds: a colony founded or lost changes what stands in a system, and the pick founding one
changes where the pick lives, in neither case moving a holder the map would notice. The
incremental refresh folds each marked system's answer into that one type, so the three facts a
cell is styled from are read off one state of the sector rather than three.

The type owns its collections - it copies what a pass hands it and answers every read with an
unmodifiable view - so the only way to move a fact is one of its folds, each of which reports
whether it moved anything. That report is what the refresh redraws a cell on. Held as three loose
collections instead, they would have had to be mutable by the caller's good manners, which a
build answering with an immutable empty set keeps perfectly until the first colony is founded.

## Borders against empty space

Every border edge is shaped the same way, whatever sits across it. Against another
organised entity (a rival faction or an alliance) and against *empty* space alike -
an uncontrolled star with no owner, whether never-settled or settled-but-unheld - the edge
keeps the mutual midline plus its inward border channel. So a faction cuts off
halfway to a dead star exactly as it does halfway to a rival, and the dead star's
own cell draws its inset outline - and, for a settled system, its neutral fill -
in the neutral style on the far side of that channel.

Those factionless fills are per cell rather than per cluster: a factionless cell never fuses into
a cluster, so it has no traced cluster to fill from and each cell tessellates its own outline
instead. Which is why a `LoneCell` carries fill triangles at all, where an owned cell has no fill of
its own to carry and takes it from the `StyledCluster` it fused into.

The two player settings under Territory reach on the **Map - Dev**
tab - *Uncontrolled systems give way to faction territory* and *Frontier keep-out* -
describe an asymmetric treatment of empty space that the draw does not yet apply, so
changing them does not move a border.

## The draw packets

`PoliticalMapTerritories` is the built state a rebuild produces and an incremental refresh edits in
place: the two draw lists (`StyledClusterGroup` per bloc, `StyledCell` per cell) plus the
occupancy, theme, and filter inputs a re-shape needs. Both packets are the framework's, described
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

The cell's presence band (`CellRibbon`, from `render.ribbon`) is written by its own call, because
it is settled from more than the cell it sits in: a band keeps clear of the cluster names (a
default the player can switch off), and those are placed only once every cell has been shaped. So the shape goes in first and the band
follows, and `CellRibbonsBaker` reads the shape back off this record rather than being handed one.
What ties the two is the write that records a shape *dropping* whatever band the cell was carrying:
a band is triangles laid inside one particular ring, so a re-shaped cell keeping its band would
draw the last shape's band inside this shape's cell. Unlike the two maps beside it this one is
sparse - a bandless cell is left out rather than held as an empty value - since a band reports what
is held in a system, and most of the sector is cells nobody lives in.

Beside it sits the cell's band *path* (`CellRibbonPath`), written by the same pass and dropped by
the same writes, for the same reason: a ring traced inside the last shape would report the
overlay's staleness as this cell's geometry. It is held only while the player has the band-path
diagnostic on - the bake hands over nothing per cell while it is off, which is how switching it off
clears what an earlier pass left - so on an ordinary frame this map is empty.

Third beside those two is the ring each band is laid along (`CellRingPathCache`), and it is a cache
rather than a draw list - nothing paints it. A bake happens whenever a cluster name may have moved,
while a cell's ring changes only when the cell is cut again, so without it a cell re-baked because
a re-fitted name landed on it would re-walk the outline it just discarded. The re-shaped cells walk
again regardless, their paths having gone with their shapes; every other cell in a bake is what the
cache spares. It is dropped by the same two writes as the band, and that is the whole of why it needs
no key: a cache living inside the object whose lifetime it must match is correct by construction,
where a keyed one is a rule somebody has to keep true.

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
*incremental refresh* that redraws what a colony event moved - over the marked systems'
holder, inhabitation and spotlit presence, which `render.MarkedSystemRederive` reads back first -
is
`render.IncrementalPoliticsRefresh`, at the render root alongside the plugin and the per-frame
cache that drives it - the composition root that wires these feature packages together. *Which*
change triggers a full rebuild here and which one only re-shapes a handful of cells is
[the caching notes](../../../../../../../../../docs/dev/caching.md).
