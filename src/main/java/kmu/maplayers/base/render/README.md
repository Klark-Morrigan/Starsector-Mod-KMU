# The render surface (`base.render`)

Where a map layer's paint reaches the screen. The engine gives the sector map's render pass to a
terrain plugin; this is that plugin, and the seam through which it hands the frame to whichever
layer the player has selected. It names no layer and knows nothing of what any of them paints.

Part of [the map layers](../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../README.md) for project context.

## Index

- [Who gets the frame](#who-gets-the-frame)
- [Bands](#bands)
- [One preparation per frame, one cursor read per pass](#one-preparation-per-frame-one-cursor-read-per-pass)
- [Silencing a minimap parked off screen](#silencing-a-minimap-parked-off-screen)
- [Three terrains, one draw](#three-terrains-one-draw)
- [Where the Starscape surfaces sit in the draw order](#where-the-starscape-surfaces-sit-in-the-draw-order)
- [What is not here](#what-is-not-here)

## Who gets the frame

`MapLayerRenderer` is the seam a layer draws through: `prepareFrame(factor)` to bring what it draws
from up to date, `publishHoverForPass(factor)` to resolve what the cursor is over,
`renderOnMap(factor, alphaMult, band)` for one band of the overlay, and `resolveHoverTooltip()` for
the box shown over one cell of it. All but the band default to nothing, so a layer states only what
it has. It is kept apart from `MapLayer` itself, which is a descriptor (id, tab label, body controls,
hotkey): folding drawing into the descriptor would merge two roles in one type, and a tab that only
switches would be left with methods it has no answer for.

`SectorMapLayerTerrainPlugin` is the terrain the engine actually calls. It reads the active layer,
asks it for a renderer, and draws through it. A layer that supplies none - No Layer, or any future
switch-only tab - reads as nothing to draw, which is the same answer the surface gives when no
layer is active at all. `base.tooltip`'s dispatcher resolves its box the same way, off the same two
reads, so neither pass names a layer.

`renderOnMap` is the sector map's hook by contract, and everything downstream rests on that - the
cursor read inverts whichever transform the running pass bound and divides by the `factor` it
supplied, both meaningless if the pass is not a map's. Nothing enforces it: the hook is a plain
method on a terrain plugin, and any mod walking the sector's terrain can call it with a transform
and a zoom of its own. The failure is quiet rather than loud, because a cursor pixel inverted
against a foreign transform still yields a world point, and in hyperspace - where campaign
coordinates *are* sector map coordinates - that point lands inside real cells, lighting and
announcing systems the pointer is nowhere near with no map open. `ForeignMapPassWarning` says that
once per session at WARN, carrying the caller's stack on a throwable that was never thrown, since
the caller is precisely what cannot be worked out from inside the pass. It never throws: it reports
on a frame rather than drawing one, so a presence read that fails costs the report and nothing
else. Naming the caller needs the widget tree rather than the stack, which is
`EmbeddedMapHostTrace`'s job - a mod builds its map once and the engine renders it every frame
after, so every frame between the hook and the game loop is the engine's.

What to *do* about such a pass is the player's, on the `Map - Compatibility` settings tab. The
render constraint stands the whole pass down when no vanilla map host is showing - checked here,
before the preparation, so a foreign pass cannot take the frame's single preparation from the map
entitled to it. It defaults off, because the layers drawn into a modded map are a working sight
several installs have come to expect. The hover constraint is separate and defaults on, and is
enforced where the cursor is read rather than here: a foreign pass may draw the layers and still
have no business answering the pointer.

Neither constraint can tell *which* host a pass belongs to. `MapPresence` is host-blind by design -
it reports that a vanilla map is on screen somewhere, never that this pass is that map's - so while
one is showing and a foreign map also draws, both passes satisfy the hover constraint. The render
constraint is what closes that case, by keeping the foreign pass from running at all.

## Bands

`MapOverlayBand` splits an overlay in two at the one place the map draws something of its own
between the parts: its own synthetic per-system nebula icons. `resolvePaintedBands()` on the surface says
which side it emits, and the renderer is handed that band per pass.

The bands are named for their positions, not their contents. Which sub-layers ride above is a
question about how the picture reads, and moving one is a change of which band a pass is emitted
for - not a rename here. A layer is free to hand that question to the player and emit each
sub-layer for the band its own setting picked, which is what the political map does.

## One preparation per frame, one cursor read per pass

Preparation is separate from drawing because a frame can be painted by more than one surface. It
runs on whichever surface paints `BENEATH_STARSCAPE_NEBULAE`, that band being painted in every mode
and reached first, which is what keeps the draw lists fresh before anything is emitted.

That pinning says *when* preparation lands but cannot say *how often*, and preparation is not
something a frame can absorb twice: it steps the cursor's arrival latch, and a latch stepped twice
for one frame reports the cursor leaving and reaching the cell it is resting on - once per frame,
for as long as it rests there. Whether a surface draws at all is settled by that surface alone, so
two can paint the lower band of one frame and there is nothing on that side to count them.

`MapFramePreparationClaim` is what counts: a `CampaignUIRenderingListener` reading the below-UI pass
as the frame boundary the surfaces cannot see from where they stand, and granting the preparation to
the first to ask after it. It fails open - until that pass has been seen, every claim is granted -
because a duplicated preparation costs work while a denied one costs the overlay, nothing else
bringing the draw lists up to date. `MapSurfaceInstaller` clears it per load before re-registering
it, so a load that never re-registers falls back to that open state rather than to a claim armed by
a session whose boundary pass is gone.

The cursor read is the one piece of per-frame work that cannot ride that claim, because it depends
on *which* pass is running: it inverts the transform that pass bound and divides by the `factor` it
supplied. The claim is first-come, and a mod compositing a sector map of its own draws it from the
campaign HUD - before the map screen is composited - so it takes the claim every frame and the read
lands on its zoom and pan. Random Assortment of Things' minimap does exactly this, and the symptom
is a hover offset from the pointer on the real map.

So `publishHoverForPass` runs on every pass the surface admits and the last write wins, which puts
the frame's answer on the surface that drew last. It costs one matrix read per extra pass - deferred
rather than stalling under Fast Rendering - and leaves one residual case: a mod drawing a map
surface *after* the map screen would win instead, which the `Map - Compatibility` hover permissions
are the escape from: withhold both and only the vanilla hosts are read at all. The same tab's
compatibility mode narrows it further where the second surface is a parked minimap, by stopping that
surface rendering at all - see [below](#silencing-a-minimap-parked-off-screen).

Only the read moves, though. Everything *about* the read that does not turn on the pass stays in the
preparation: whether any feedback still wants a hover, and whether something is drawn over the
cursor. Neither changes between two passes of one frame, and the last cover in the chain walks the
live widget tree, so a pass asks only what its own transform can answer and reads a flag for the
rest.

The moment stays per frame too, for the reason the claim states. Two passes of one frame resolving
different cells is exactly what a foreign transform produces, so a latch stepped per pass would
report a crossing on every frame the pointer rests still. `MapHoverPublisher` therefore keeps what
its passes settled on and answers the moment once, from the preparation - one frame behind the read,
which is 16ms and inaudible.

## Silencing a minimap parked off screen

The last-wins rule above settles which pass a frame's hover comes from; it does not stop the pass.
A mod that docks a minimap parks the panel off screen rather than taking it down, and a parked
minimap goes on rendering a whole sector map every frame - driving every terrain pass in the sector
while it does, ours among them, and taking the frame's preparation claim on the way. So a frame the
player opened the `M` map on carries two transforms, and which of them the cursor resolves through
is the engine's child order rather than anything promised. A widget that renders nothing contributes
no pass, which leaves one transform and nothing for last-wins to arbitrate.

That is what `RandomAssortmentOfThingsMinimapSuppression` and KMLib's `OffScreenWidgetSuppressor`
between them do: hold the parked widget's own opacity at zero, and hand back the value it was found
at when it comes back. The split is the compatibility mode's throughout. What being parked means -
the widget's box meeting the screen at all - and what switching one off consists of are stated over
any widget at all and are KMLib's, which is why nothing there names a mod or a map; whether writing
into another mod's panel is wanted is the per-mod question the player answers with the mode, and
this package answers only that, plus the one map it may be aimed at.

Three things about it are worth knowing before touching it:

- **It reads where the widget is, never what shows of it.** The two are the same question until this
  writes, and the write is what parts them: a read that sifted out widgets drawn to nothing would
  stop reporting the very map it had just switched off, and the minimap would be parked for good.
- **It is not what closes the hover leak**, which the game-space permission and the minimap cover
  already close twice over on these frames - a cursor cannot fall inside an off-screen box. The
  offset is the fault it answers, along with the work: a sector map rendered behind every screen the
  player opens.
- **The slide is left alone.** A panel partly on screen is one the player can see, so it stays drawn
  and two maps render for as long as the slide lasts. Both faults last exactly that long, and
  neither is what this is for.

Exactly one embedded map may be suppressed, and for a different reason than the cover's confinement
needs one: there a second map makes the hover unattributable, while here the mode names a mod, the
widgets found carry no mod-owned class to match on, and with two of them there is no telling which
one the player switched the mode on for. The reasons are per-rule; the reading is not, and both take
it from `SingleEmbeddedMapReader` in `base.hover`. Two copies of a count rule could be changed in
one, leaving the cursor confined to a widget the other had already switched off - and each holding
its own walk would descend the whole core UI twice a frame on exactly the installs these rules exist
for, the finder walking afresh while it has found nothing.

It runs as an `EveryFrameScript` answering `runWhilePaused`, which is what the mod's own panel
script is - so the hook is known to fire in the states this matters in, a dialog or the menu among
them. It cannot run in a render pass at all: the pass it suppresses is the last pass it would ever
be asked from, which on a screen showing no map would strand the minimap invisible for the session.

The residue is a session that ends parked. This writes into a widget another mod owns, so if it
stops running - that mod disabled mid-save - the minimap stays invisible until its owner rebuilds
the panel, which is the next location change or the next load.

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
under, and the XStream aliases that let a save written under a former plugin name still load.
`MapSurfaceInstaller` calls it and holds none of that knowledge itself, as the mod's entry point
holds none of the installer's. Its variants are told apart by
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
moved. Both are wired in `MapSurfaceInstaller`: that the entity is a terrain, and that the fog above
it is what makes the move worth making, is the whole of KMU's side.

Only the above-nebulae surface is moved. The one beneath it is meant to be fogged, so lifting both
would flatten the split back into a single pass beneath the fog.

The move takes that terrain out of hyperspace for one advance, which is among the reasons
`installAboveStarscapeNebulaeTerrain` stays a per-load sweep; its Javadoc has what that costs a save
written inside the window. Losing the surface entirely costs the upper band alone - the lower
band still paints, and whatever the layer placed above it stops appearing.

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
