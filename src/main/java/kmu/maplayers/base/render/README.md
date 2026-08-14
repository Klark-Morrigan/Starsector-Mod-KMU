# The render surface (`base.render`)

Where a map layer's paint reaches the screen. The engine gives the sector map's render pass to a
terrain plugin; this is that plugin, and the seam through which it hands the frame to whichever
layer the player has selected. It names no layer and knows nothing of what any of them paints.

Part of [the map layers](../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../README.md) for project context.

## Index

- [Who gets the frame](#who-gets-the-frame)
- [Bands](#bands)
- [Three terrains, one draw](#three-terrains-one-draw)
- [Where the Starscape surfaces sit in the draw order](#where-the-starscape-surfaces-sit-in-the-draw-order)
- [What is not here](#what-is-not-here)

## Who gets the frame

`MapLayerRenderer` is the seam a layer draws through: `prepareFrame(factor)` to bring what it draws
from up to date, `renderOnMap(factor, alphaMult, band)` for one band of the overlay, and
`resolveHoverTooltip()` for the box shown over one cell of it. The last two default to nothing, so a
layer states only what it has. It is kept apart from `MapLayer` itself, which is a descriptor (id,
tab label, body controls, hotkey): folding drawing into the descriptor would merge two roles in one
type, and a tab that only switches would be left with methods it has no answer for.

`SectorMapLayerTerrainPlugin` is the terrain the engine actually calls. It reads the active layer,
asks it for a renderer, and draws through it. A layer that supplies none - No Layer, or any future
switch-only tab - reads as nothing to draw, which is the same answer the surface gives when no
layer is active at all. `base.tooltip`'s dispatcher resolves its box the same way, off the same two
reads, so neither pass names a layer.

## Bands

`MapOverlayBand` splits an overlay in two at the one place the map draws something of its own
between the parts: its own synthetic per-system nebula icons. `resolvePaintedBands()` on the surface says
which side it emits, and the renderer is handed that band per pass.

Preparation is separate from drawing because a frame can be painted by more than one surface. It
runs once, on whichever surface paints `BENEATH_STARSCAPE_NEBULAE` - that band is painted in every
mode and is always reached first, so pinning the single preparation to it is what keeps a cursor
read from resolving against a half-drawn frame and a staleness check from running twice.

The bands are named for their positions, not their contents. Which sub-layers ride above is a
question about how the picture reads, and moving one is a change of which band a pass is emitted
for - not a rename here. Today what is merely seen is beneath and what is read is above.

## Three terrains, one draw

Because a renderer is reached through a registered layer, it is a session-scoped singleton created
at load: nothing it holds enters a save, so it needs no `transient` marking, no lazy re-creation,
and no save-restore path. The terrain plugin, which *is* serialised with its terrain entity, holds
none of it.

`SectorMapLayerStarscapeTerrainPlugin` and `SectorMapLayerAboveStarscapeNebulaeTerrainPlugin` reach
the same draw while the map's Starscape filter is on - that filter hard-suppresses custom terrain,
so the overlay needs a terrain the filter admits. Each rides its own
`SectorMapLayerStarscapeTerrain`, one entity class parameterised by the row id it resolves its
plugin from, since reporting the whitelisted type is the whole of what that class does. All three
surfaces dispatch to the same renderer over one set of draw lists, so nothing is built or held
twice.

Two entities rather than one because the map draws its nebulae between the terrain icons it holds:
a band above them needs an icon of its own, which needs an entity of its own. The plugins form a
chain - schematic, then Starscape, then above-nebulae - each adding one thing: the stand-aside, then
the band.

`MapLayerTerrainInstaller` is what puts any of them into a loaded save and keeps exactly one of each
there, and it owns the save-facing constants that go with that: the type ids the entities are built
under, and the XStream aliases that let a save written under a former plugin name still load. The
mod's entry point calls it and holds none of that knowledge itself. Its variants are told apart by
plugin class compared exactly - the chain means an `instanceof` would have one answer for another's.

All three terrain rows are declared in `data/campaign/terrain.json`. Four class names from this
package reach a save: the three plugins', serialised with the entities holding them, and
`SectorMapLayerStarscapeTerrain`'s - the Starscape surfaces use a mod-owned entity where the base
one uses a vanilla one. Every former name of any of them needs a `configureXStream` alias or an
existing save fails to load.

The Starscape surfaces carry one further constraint the base one does not. Their entity resolves its
spec from the row id it is constructed with and then reports the whitelisted map type in the
getter's place, so the id cannot be read back off a loaded entity: a sweep by type id can neither
recognise nor retire one left behind by a renamed row. Renaming those rows needs a bridge on the
entity itself.

## Where the Starscape surfaces sit in the draw order

Being drawn at all and being drawn on top are separate questions, and the whitelisted type only
answers the first. The map's synthetic per-system nebula icons are seeded after everything
hyperspace holds, so both Starscape surfaces are painted beneath them by default - and the one pass
that runs after every terrain icon is the pass the widget skips in this mode, which leaves no
supported hook that paints over the fog.

Moving an icon up that order is nobody's content, so none of the mechanism is here. KMLib's
`MapIconReseater` owns it, and `MapIconReseatDecision` states how it works and what it rests on.
It acts on where the icon is, read back from the widget, rather than on a map having opened - so a
re-seeding it did not witness corrects itself on the next frame instead of lasting the session.

What KMU supplies is the two ports. The map read is the Starscape one - the entity being moved is
one of the surfaces that stand aside while a schematic map is up - and the entity read is
`MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain`, which resolves it afresh per call
through the same plugin-class guard the install uses, so the entity a load brought back is the one
moved. Both are wired at the mod's entry point: that the entity is a terrain, and that the fog above
it is what makes the move worth making, is the whole of KMU's side.

Only the above-nebulae surface is moved. The one beneath it is meant to be fogged, so lifting both
would flatten the split back into a single pass beneath the fog.

The move takes that terrain out of hyperspace for one advance, which is among the reasons
`installAboveStarscapeNebulaeTerrain` stays a per-load sweep; its Javadoc has what that costs a save
written inside the window. Losing the surface entirely costs the upper band alone - the geometry
still paints, and the text stops appearing.

## What is not here

- **[Clusters](clusters/README.md)** - the shape work a painting layer draws through this surface:
  the cluster-border trace, the smoothing passes, the vertex packing, and the split fill that puts
  several fills inside one border.
- **`base.layer`** - which layers exist, which one each screen has picked, and how that pick is
  persisted. The surface only asks the registry for the active one.
- **`base.hover`** - the cursor's half of the render pass: the read that resolves a cursor pixel to
  a cell and the cluster around it, the halo and the wash drawn on that answer, and the two seams a
  layer supplies them through.
- **`base.tooltip`** - what the box floating beside that cursor says, drawn in a later UI pass than
  this one and dispatched through the same active-layer read.
- What any layer actually paints, and what its owners mean, belongs to that layer; for the
  one layer that paints today that is [`politicalmap`](../../politicalmap/README.md).
