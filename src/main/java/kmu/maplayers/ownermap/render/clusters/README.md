# Cluster fills and borders (`render.clusters`)

What the production draw of an owner map is made of:
each faction's coloured cluster,
its cluster border,
and the per-cell province seams and factionless outlines,
baked into the framework's draw packets.
This is the base layer the labels overlay sits over -
what the player reads as "who holds what".
The emission itself is the framework's,
reached through a seam this package's built state satisfies.

Part of [the owner-map tier](../../README.md),
in Klark Morrigan's Utilities;
see the [mod README](../../../../../../../../README.md) for project context.

## Index

- [Building: cells into clusters](#building-cells-into-clusters)
- [What recedes](#what-recedes)
- [Borders against empty space](#borders-against-empty-space)
- [The draw packets](#the-draw-packets)
  - [`PaintedCellStore`: what one cell carries](#paintedcellstore-what-one-cell-carries)
  - [`OwnerMapBuildInputs`: what the build was baked under](#ownermapbuildinputs-what-the-build-was-baked-under)
- [The split fill: solid, hatched, unfilled](#the-split-fill-solid-hatched-unfilled)
- [Rendering](#rendering)
- [What is not here](#what-is-not-here)

## Building: cells into clusters

`OwnerMapBuilder` is the orchestration only:
it resolves who holds each system,
reads the theme,
shapes the cached cells into merged clusters,
and drives the per-item builders.
Its job is to sample every input exactly once so the whole pass keys off one snapshot -
which is what lets an incremental re-shape reuse those same builders on a handful of cells
and land on a result identical to a full rebuild.

- `PaintedCellBuilder` bakes one cell,
  choosing which form of `StyledCell` it takes.
  An **owned** cell becomes a `FusedCell` and contributes only its interior seams,
  because its fill and cluster border belong to the cluster it fuses into -
  the form has no slot for either.
  A **factionless** cell (settled but unheld, or uninhabited) does not fuse,
  so it becomes a `LoneCell` carrying its own fill and outline,
  and resolves both palette slots to the shared neutral colour.
  Of the two,
  only a settled cell takes the pass's recede -
  see [what recedes](#what-recedes) below.
  Either way it comes back as a `PaintedCell`,
  reporting the ring its ink went on as well as the packet.
  A fused cell reports its raw extent,
  its cluster's border being what bounds the ink;
  a lone cell rounds its own corners here and reports the rounded ring,
  which is not the cell it was shaped from.
- `ClusterGroupBuilder` bakes one bloc into a `StyledClusterGroup`:
  every body it holds,
  each with its cluster border traced across the systems in it,
  and each body's fill -
  which it hands to the framework's `SplitFillBuilder`,
  see [the split fill](#the-split-fill-solid-hatched-unfilled) below.
  Fill and border come from the same loops,
  so they cannot drift apart.

No presence band is laid here.
A band is baked *around* the cluster names,
and the names are fitted after this build -
each inside the border these very cells trace -
so the bands are a stage of their own afterwards,
over the shapes this one recorded and through the same reading of the sector this one was handed.
`CellRibbonsBaker`
(in [`render.ribbon`](../ribbon/CellRibbonsBaker.java)) drives it,
and both the full rebuild and the incremental refresh reach it through the same call,
which is what keeps an incrementally-updated band identical to the one a full rebuild would lay.

The shaping the cell and cluster group builders drive is the framework's,
in [`base.render.clusters`](../../../base/render/clusters/README.md):
`BorderSmoothing` sands spikes and rounds corners of the traced borders,
`VertexRuns` flattens shaped cells into GL vertex runs,
and `StyledCell`,
`StyledCluster` and `StyledClusterGroup` are the packets they bake into.
Element colours,
opacities,
and widths come from cascading the `base.theme` records with each bloc's recede adjustment,
asked for through `OwnerMapBuildInputs.resolveBlocPaintOf` so every part of a bloc resolves from one read.

## What recedes

The Mute and Desaturate toggles sink the background behind a spotlighted bloc.
Every non-spotlit **bloc** recedes through `resolveBlocPaintOf`,
and so does a **settled** factionless cell:
it carries a fill of its own,
so leaving it at full strength lets it out-read the bloc the spotlight is meant to isolate.
Muting dims it;
desaturating recolours it off the same desaturation palette a receded bloc uses,
rather than the neutral colour it paints in normally.

An **uninhabited** cell is the exception.
It is the empty backdrop the whole map is drawn over
rather than something the spotlight competes with,
and its faint outline is what gives the sector its shape,
so it stays at full strength under every recede.

A settled cell **the spotlit bloc lives in** is the second exception.
The recede clears away what the pick is not,
and a system the pick has a colony in is not that -
sinking it would hide the pick's own presence for the sole reason that this layer's holding rule could not attribute it to them.
Under a holding rule that admits only some factions that is routine:
the other factions' colonies always land in a holderless cell.

Such a cell paints the neutral **lifted toward white** by `presenceLightening`,
not the bloc's colours -
nobody holds the system on this layer,
and its shades would state exactly the holding the view reports it does not have.
The lift is what makes sparing the recede visible at all:
the neutral a factionless cell paints in
and the Independent grey the background sinks from are the same grey,
so a merely-unreceded cell sits at the value the background started at and reads as part of it.
`desaturationDarkening` sinks the backdrop,
`presenceLightening` raises the spared cell,
and the pair is what separates them -
by value only,
so a grey stays the same grey.

Only the *filter* recede reaches a settled factionless cell -
it is the one whose backdrop is "the rest of the sector".
A recede the view applies of its own accord
(`OwnerPaintedView.resolveViewRecedeAdjustment`) describes blocs,
which such a cell is not,
so it leaves it alone.
Off filter the pass's recede is the identity,
so an unfiltered map draws its unheld systems untouched.

The rule itself is not here:
`PaintedCellBuilder` asks [`FactionlessStyleResolver`](../style/README.md),
which also decides which of the two factionless categories a cell falls in.
One classification drives both,
so a cell cannot take the settled style yet miss the recede that style draws under.

That classification reads the pass's **inhabited-system set**,
not the holder map.
`OwnerMapBuilder` scans it once per rebuild through `OwnerMapInhabitation.readInhabitedSystemKeys` -
the same rule that decided the system seeds a cell at all -
and `OwnerMapClusters` retains it,
so the incremental re-shape classifies against exactly what the full build used.
Deriving emptiness from the holder map instead would make every view
whose holding rule admits only some factions report its unheld systems as empty space.

The presence exception rides beside it as the **spotlit-presence set**,
read through `SpotlitBlocs.findPresentSystemKeys` -
the same presence rule a filtered resolve keeps a spotlit bloc visible by,
so "the pick lives here" means one thing across the map.
Both read it off the colonies somebody lives on rather than off any mechanic that weighs markets,
since a bloc whose only foothold in a system is a colony the economy does not list weighs nothing there
and lives there all the same.

Both come off one value,
`HolderPass.readHabitationIn` -
the colonies somebody lives on,
with the blocs folded from those very colonies.
The classification asks its emptiness where the presence read asks its bloc set,
so presence is a partition of the set emptiness is asked of
and a cell cannot be called empty space while the spotlight keeps a bloc's fill over it.
Two call sites picking the same projection would be a convention a later edit could break;
one value handed to both is not.

It is asked only of the inhabited systems the holding left out,
since a system somebody holds already draws in that bloc's cluster group -
and which view is painting decides whether anything is left.
A view whose rule leaves settled systems unheld can leave the bloc's own colonies there,
and this read is what spares their cells.
A view whose filtered resolve keeps every system the bloc is present in
has already covered this read,
so what is left over is the systems it is absent from
and the read costs the set arithmetic and returns empty.

Both sets sit with the holder map in **`SystemOccupancy`** -
who is in each system -
rather than beside the `ContentInputs` the build was baked under or the contested set it derived,
both of which are fixed once the build ends,
because all three of these move between rebuilds:
a colony founded or lost changes what stands in a system,
and the pick founding one changes where the pick lives,
in neither case moving a holder the map would notice.
The incremental refresh folds each marked system's answer into that one type,
so the three facts a cell is styled from are read off one state of the sector rather than three.

The type owns its collections -
it copies what a pass hands it and answers every read with an unmodifiable view -
so the only way to move a fact is one of its folds,
each of which reports whether it moved anything.
That report is what the refresh redraws a cell on.
Held as three loose collections instead,
they would have had to be mutable by the caller's good manners,
which a build answering with an immutable empty set keeps perfectly
until the first colony is founded.

## Borders against empty space

Every border edge is shaped the same way,
whatever sits across it.
Against another organised entity (a rival faction or group) and against *empty* space alike -
an uncontrolled star with no owner,
whether never-settled or settled-but-unheld -
the edge keeps the mutual midline plus its inward border channel.
So a faction cuts off halfway to a dead star exactly as it does halfway to a rival,
and the dead star's own cell draws its inset outline -
and,
for a settled system,
its neutral fill -
in the neutral style on the far side of that channel.

Those factionless fills are per cell rather than per cluster:
a factionless cell never fuses into a cluster,
so it has no traced cluster to fill from and each cell tessellates its own outline instead.
Which is why a `LoneCell` carries fill triangles at all,
where an owned cell has no fill of its own to carry
and takes it from the `StyledCluster` it fused into.

## The draw packets

`OwnerMapClusters` is the built state a rebuild produces
and an incremental refresh edits in place:
the two draw lists
(`StyledClusterGroup` per bloc, `StyledCell` per cell),
the cluster index and the occupancy -
all live -
beside the `OwnerMapBuildInputs` the build was baked under,
which a re-shape reads and never moves.
Both packets are the framework's,
described in [`base.render.clusters`](../../../base/render/clusters/README.md) -
a bloc's bodies with the paints they share,
each body carrying its own fill triangles,
contested-hatch segments,
outer loop and enclaves;
and one cell's seam,
or its own fill and outline.

A bloc in two places is two bodies under one group,
not one record holding both.
That is what lets a fill stop at the frontier of the body it is in:
each body's fill is clipped to its own loops,
where a single soup over the whole bloc could only be clipped to all of them at once.

Holding the framework's packets is what lets it satisfy `ClusterDrawLists` outright,
so the framework's emission paints this layer without either side naming the other:
the two maps it hands over are the two it already keeps,
and the bloc keys stay opaque across the seam.
What is the layer's is entirely upstream of it -
which is why `ClusterGroupBuilder` keeps its name
while producing a record that says nothing about factions.

It also answers the cursor's candidate-loop read,
flattening a bloc's bodies into the one list the highlight hit-tests against -
and retaining it,
because the highlight memoises its resolved halo on that list's identity
and a fresh list per frame would re-clip the wash sixty times a second.
The retained answer is keyed on the bloc's cluster group by identity,
so a refresh that replaces the group recomputes once and a refresh that does not costs nothing.

### `PaintedCellStore`: what one cell carries

Everything held per drawn cell lives in its own type,
reached through `getPaintedCells`.
Five stores,
all keyed by cell:
the `StyledCell`,
the ring it was painted on,
the presence band laid inside that ring
(`CellRibbon`, from `render.ribbon`),
that band's path for the diagnostic overlay (`CellRibbonPath`),
and the traced ring the band was laid along (`CellRingPathCache`).

They are five stores rather than one record per cell because most cells are in only two of them:
a band reports what is held in a system and most of the sector is cells nobody lives in,
and a path exists only while the player has the diagnostic on.
What makes them one type is that they are **dropped together**.
Everything after the ring is cut to one particular ring,
so a cell re-shaped while keeping any of it would draw the last shape's work inside this shape's cell.
Owning the five in one place is what makes a re-shape clear them by construction,
rather than by each caller on a larger model remembering the same five fields.

The ring and the `StyledCell` arrive as one `PaintedCell` from the builder,
and that pairing is what lets the clusters answer `base.hover`'s `PaintedCellShapes` -
and through it `MapHoverTargets` -
directly:
the shapes a cursor is tested against are the shapes this frame painted,
never a re-derivation that could drift from them.
The highlight reads the same shapes through `render/hover`'s adapter,
so the cursor and the halo cannot disagree about what was drawn.

The band is written by its own pass rather than beside the ring,
because it is settled from more than the cell it sits in:
it keeps clear of the cluster names (a default the player can switch off),
and those are placed only once every cell has been shaped.
So the ring goes in first and `CellRibbonsBaker` reads it back off the store rather than being handed one -
the store being the only thing that pass is given,
since nothing it does per cell asks the build anything else.

The ring cache is the one member nothing paints.
A bake happens whenever a cluster name may have moved,
while a cell's ring changes only when the cell is cut again,
so without it a cell re-baked because a re-fitted name landed on it would re-walk the outline it just discarded.
Being dropped with its cell is the whole of why it needs no key:
a cache whose lifetime is the object holding it is correct by construction,
where a keyed one is a rule somebody has to keep true.

### `OwnerMapBuildInputs`: what the build was baked under

Everything a rebuild retained to build under lives in its own record,
reached through `getBuildInputs`:
the `MapStyling` (the theme and the three factionless palettes),
the `ViewGrouping` (the view and the grouping snapshot its holding was resolved under),
the `ContentInputs` it sampled,
and the two sets the holding resolve derived about the fill -
the unfilled systems and the contested ones.

They are one record because they are fixed together:
set once when the build ends and read until the next one,
where the occupancy beside them is folded per marked system.
A reader takes the record and names which snapshot it reads -
`styling()`,
`viewGrouping()`,
`contentInputs()` -
rather than reaching one field of it through a flat getter on the built map,
which would let a pass compose a cell out of one snapshot's theme and another's grouping without the type saying so.
`resolveBlocPaintOf` sits on the record for the same reason:
it cascades the view, the grouping, the picks and the theme's shades into the one thing an element is painted from
(`ResolvedBlocPaint`, in [`render.style`](../style/README.md)),
so it belongs to the type that holds all four.
The bundle-and-adjustment step behind it stays private:
a caller wanting one is a caller about to paint an element with it,
and resolving the shades separately is how a fill comes to be muted while the border beside it is not.

## The split fill: solid, hatched, unfilled

A bloc's footprint is traced as one border whatever its members' fills;
the fill is what varies per system inside it,
across three states.
**Solid** is the default -
a bloc with no exception among its systems fills its whole cluster from that one border
and pays nothing for the split,
the common case.
The two exceptions each carve a sub-cluster out of the solid,
hatched and unfilled,
and both are decided upstream by the view's holder source;
this section is how the draw honours them.

The machinery is the framework's -
`FillSplit` partitions the members and `SplitFillBuilder` tessellates each state as its own cluster inside the one border;
see [`base.render.clusters`](../../../base/render/clusters/README.md) for how,
and why a state fills from its own traced rings rather than from its members' cells.
What is the layer's is which systems land in the two non-solid sets,
and what that reads as on the map.

**Hatched.** When the filter spotlights one bloc,
its whole footprint -
the systems it wins plus the ones it is merely present in -
clusters under one bloc,
and each body's fill splits inside its own frontier:
solid where the bloc wins,
a pre-clipped diagonal hatch where it is only present ("mine, but contested").
The states are traced once for the bloc and clipped per body,
so a bloc contested in two places pays one trace and reads correctly in both.

The footprint's interior divisions carry no geometry of their own:
the whole footprint shares one owner,
so a solid/hatch transition is an interior seam like any other
and its two cells already stroke it in the province style.
Giving those divisions the frontier's own border style instead would put a heavy line under the faction's name label,
which the label has to stay legible over,
and would need raw cell edges to draw -
untrimmed,
so they overshoot the inset frontier and poke out into the border channel.
The province seam has neither problem:
it is faint,
and the cell shaper truncates it where it runs into a pulled-in border.

**Unfilled.** A system the view's holder source extends a bloc's holdings with,
though the bloc does not hold it,
sits inside its bloc's one border for outline and label but paints no fill at all,
so the split simply skips it.
Same shape as the hatch sub-cluster
(one border, a sub-cluster drawn differently) but the sub-cluster is empty rather than hatched,
so the boundary between what a bloc holds and what its view extends it with reads as the seam where the fill stops inside a continuous frontier.
Which systems are unfilled is the holder source's to say;
this package only honours the set.

## Rendering

Nothing here paints.
The draw is the framework's `ClusterRenderer`,
reached through the `ClusterDrawLists` seam `OwnerMapClusters` satisfies;
see [`base.render.clusters`](../../../base/render/clusters/README.md) for what it emits and in what order.

## What is not here

The *styling resolvers*
(what colour/width each category and bloc draws in) live in [`render.style`](../style/README.md),
over the framework's [`base.theme`](../../../base/theme/README.md) records they read the player's choices out of;
this package consumes both,
it does not decide either.
The *name overlay* that sits on top is the framework's [`base.labels`](../../../base/labels/README.md),
fed the names and shades this layer resolves in `render.labels`.
The *shape work* the two builders drive -
the border-ring trace,
the smoothing passes,
the vertex packing,
the `StyledCell` packet,
the split-fill machinery,
and the GL emission itself -
is the framework's [`base.render.clusters`](../../../base/render/clusters/README.md),
which knows nothing of who holds what;
the low-level GL run emission is a generic helper in KMLib (`kmlib.opengl.GlRuns`).
The *incremental refresh* that redraws what a colony event moved -
over the marked systems' holder,
inhabitation and spotlit presence,
which `render.MarkedSystemRederive` reads back first -
is `render.IncrementalOwnerRefresh`,
at the render root alongside the plugin and the per-frame cache that drives it -
the composition root that wires these feature packages together.
*Which* change triggers a full rebuild here and
which one only re-shapes a handful of cells is [the caching notes](../../../../../../../../docs/dev/caching.md).
