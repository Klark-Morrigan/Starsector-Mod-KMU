# The render surface (`base.render`)

Where a map layer's paint reaches the screen. The engine gives the sector map's render pass to a
terrain plugin; this is that plugin, and the seam through which it hands the frame to whichever
layer the player has selected. It names no layer and knows nothing of what any of them paints.

Part of [the map layers](../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../README.md) for project context.

## Index

- [Who gets the frame](#who-gets-the-frame)
- [Two terrains, one draw](#two-terrains-one-draw)
- [What is not here](#what-is-not-here)

## Who gets the frame

`MapLayerRenderer` is the seam a layer draws through: `renderOnMap(factor, alphaMult)` for the
overlay, and `resolveHoverTooltip()` for the box shown over one cell of it, which defaults to none.
It is kept apart from `MapLayer` itself, which is a descriptor (id, tab label, body controls,
hotkey): folding drawing into the descriptor would merge two roles in one type, and a tab that only
switches would be left with methods it has no answer for.

`SectorMapLayerTerrainPlugin` is the terrain the engine actually calls. It reads the active layer,
asks it for a renderer, and draws through it. A layer that supplies none - No Layer, or any future
switch-only tab - reads as nothing to draw, which is the same answer the surface gives when no
layer is active at all. `base.tooltip`'s dispatcher resolves its box the same way, off the same two
reads, so neither pass names a layer.

Because a renderer is reached through a registered layer, it is a session-scoped singleton created
at load: nothing it holds enters a save, so it needs no `transient` marking, no lazy re-creation,
and no save-restore path. The terrain plugin, which *is* serialised with its terrain entity, holds
none of it.

## Two terrains, one draw

`SectorMapLayerStarscapeTerrain` and `SectorMapLayerStarscapeTerrainPlugin` are the second surface,
reaching the same draw while the map's Starscape filter is on - that filter hard-suppresses custom
terrain, so the overlay needs a terrain the filter admits. Both halves dispatch to the same
renderer over one set of draw lists, so nothing is built or held twice.

Both terrain rows are declared in `data/campaign/terrain.json`. Two class names from this package
reach a save: the terrain plugin's, serialised with the entity holding it, and
`SectorMapLayerStarscapeTerrain`'s - the starscape half is a mod-owned entity where the base half
uses a vanilla one. Every former name of either needs a `configureXStream` alias or an existing
save fails to load.

The starscape half carries one further constraint the base half does not. Its entity resolves its
spec from the row id it is constructed with and then reports the whitelisted map type in the
getter's place, so the id cannot be read back off a loaded entity: a sweep by type id can neither
recognise nor retire one left behind by a renamed row. Renaming that row needs a bridge on the
entity itself.

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
