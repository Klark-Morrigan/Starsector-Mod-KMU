# The render surface (`base.render`)

Where a map layer's paint reaches the screen.
The engine gives the sector map's render pass to a terrain plugin;
this is that plugin,
and the seam through which it hands the frame to whichever layer the player has selected.
It names no layer and knows nothing of what any of them paints.

Part of [the map layers](../../README.md),
in Klark Morrigan's Utilities;
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [Who gets the frame](#who-gets-the-frame)
- [Bands](#bands)
- [One preparation per frame, one cursor read per pass](#one-preparation-per-frame-one-cursor-read-per-pass)
- [What a frame costs](#what-a-frame-costs)
- [Silencing a minimap parked off screen](#silencing-a-minimap-parked-off-screen)
- [Three terrains, one draw](#three-terrains-one-draw)
- [Where the Starscape surfaces sit in the draw order](#where-the-starscape-surfaces-sit-in-the-draw-order)
- [Renaming a class here loses saves](#renaming-a-class-here-loses-saves)
- [What is not here](#what-is-not-here)

## Who gets the frame

`MapLayerRenderer` is the seam a layer draws through:
`prepareFrame(factor)` to bring what it draws from up to date,
`publishHoverForPass(factor)` to resolve what the cursor is over,
`renderOnMap(factor, alphaMult, band)` for one band of the overlay,
and `resolveHoverTooltip()` for the box shown over one cell of it.
All but the band default to nothing,
so a layer states only what it has.
It is kept apart from `MapLayer` itself,
which is a descriptor
(ID, tab label, body controls, hotkey):
folding drawing into the descriptor would merge two roles in one type,
and a tab that only switches would be left with methods it has no answer for.

`SectorMapLayerTerrainPlugin` is the terrain the engine actually calls.
It reads the layer being drawn -
the screen's pick,
or the picture still dissolving off it -
asks that layer for the renderer belonging to the sector being drawn,
and draws through it.
A layer that supplies none -
No Layer,
or any future switch-only tab -
reads as nothing to draw,
which is the same answer the surface gives when nothing is on the screen at all.
`base.tooltip`'s dispatcher resolves its box the same way,
off the same two reads,
so neither pass names a layer.

Hiding the layers asks nothing of this surface but the alpha.
The show-or-hide pick reaches it through those same two reads,
and all it adds is multiplying the showing screen's fade into the `alphaMult` the map pass already hands it -
so a screen being switched off thins on the same curve as everything else the layers put on it,
rather than snapping out from under the sidebar beside it.

Which sector that is has to be resolved rather than passed:
this hook is handed a fade factor and nothing else,
and the plugin cannot be handed one either -
the engine rebuilds it from the save,
with no seam to inject through.
So it resolves through the one handle it does have,
the terrain entity it rides on:
that entity's containing location is its sector's hyperspace,
which `SectorMapMachineryIndex` answers by.
Per frame,
never held -
machinery is a live object and this plugin is written into the save.
What machinery is,
what it holds and how its lifetime is settled are [the installed machinery](../machinery/README.md);
this file covers only how a surface finds the one it is drawing.

A surface whose location has nothing installed stands down instead of drawing.
It belongs to a sector nothing is drawing -
a save carrying the terrain with the overlay switched off,
or a sector the layers were taken off mid-session -
and the detached machinery an uninstalled *sector* resolves to would be the wrong answer here:
it is the holder every sector-less caller shares,
so painting through it draws no sector at all.

`renderOnMap` is the sector map's hook by contract,
and everything downstream rests on that -
the cursor read inverts whichever transform the running pass bound
and divides by the `factor` it supplied,
both meaningless if the pass is not a map's.
Nothing enforces it:
the hook is a plain method on a terrain plugin,
and any mod walking the sector's terrain can call it with a transform and a zoom of its own.
The failure is quiet rather than loud,
because a cursor pixel inverted against a foreign transform still yields a world point,
and in hyperspace -
where campaign coordinates *are* sector map coordinates -
that point lands inside real cells,
lighting and announcing systems the pointer is nowhere near with no map open.
`ForeignMapPassWarning` says that once per session at WARN,
carrying the caller's stack on a throwable that was never thrown,
since the caller is precisely what cannot be worked out from inside the pass.
It never throws:
it reports on a frame rather than drawing one,
so a presence read that fails costs the report and nothing else.
Naming the caller needs the widget tree rather than the stack,
which is `EmbeddedMapHostTrace`'s job -
a mod builds its map once and the engine renders it every frame after,
so every frame between the hook and the game loop is the engine's.

What to *do* about such a pass is the player's,
on the `Map - Compatibility` settings tab.
The render constraint stands the whole pass down when no vanilla map host is showing -
checked here,
before the preparation,
so a foreign pass cannot take the frame's single preparation from the map entitled to it.
It defaults off,
because the layers drawn into a modded map are a working sight several installs have come to expect.
The hover constraint is separate and defaults on,
and is enforced where the cursor is read rather than here:
a foreign pass may draw the layers and still have no business answering the pointer.

Neither constraint can tell *which* host a pass belongs to.
`MapPresence` is host-blind by design -
it reports that a vanilla map is on screen somewhere,
never that this pass is that map's -
so while one is showing and a foreign map also draws,
both passes satisfy the hover constraint.
The render constraint is what closes that case,
by keeping the foreign pass from running at all.

## Bands

`MapOverlayBand` splits an overlay in two at the one place the map draws something of its own between the parts:
its own synthetic per-system nebula icons.
`resolvePaintedBands()` on the surface says which side it emits,
and the renderer is handed that band per pass.

The bands are named for their positions,
not their contents.
Which sub-layers ride above is a question about how the picture reads,
and moving one is a change of which band a pass is emitted for -
not a rename here.
A layer is free to hand that question to the player
and emit each sub-layer for the band its own setting picked.

## One preparation per frame, one cursor read per pass

Preparation is separate from drawing because a frame can be painted by more than one surface.
It runs on whichever surface paints `BENEATH_STARSCAPE_NEBULAE`,
that band being painted in every mode and reached first,
which is what keeps the draw lists fresh before anything is emitted.

That pinning says *when* preparation lands but cannot say *how often*,
and preparation is not something a frame can absorb twice:
it steps the cursor's arrival latch,
and a latch stepped twice for one frame reports the cursor leaving
and reaching the cell it is resting on -
once per frame,
for as long as it rests there.
Whether a surface draws at all is settled by that surface alone,
so two can paint the lower band of one frame and there is nothing on that side to count them.

`MapFramePreparationClaim` is what counts:
a `CampaignUIRenderingListener` reading the below-UI pass as the frame boundary the surfaces cannot see from where they stand,
and granting the preparation to the first to ask after it.
It fails open -
until that pass has been seen,
every claim is granted -
because a duplicated preparation costs work while a denied one costs the overlay,
nothing else bringing the draw lists up to date.

One claim per machinery,
not one per process.
The counting is only right within a map:
two sectors drawing in one frame each owe their own draw lists a preparation,
and a shared claim would leave the second sector's overlay painting lists nothing brought up to date.
Nothing clears it per load either -
machinery is made fresh when the layers are installed on a sector,
so a claim left mid-frame by the session before goes with the machinery that held it,
and a load that registers nothing falls back to the open state
rather than to an armed claim whose boundary pass is gone.
`MapSurfaceInstaller` registers the machinery's own claim rather than one built beside it:
a listener opening frames on a claim the surfaces never ask would leave every one of them preparing per pass.

The cursor read is the one piece of per-frame work that cannot ride that claim,
because it depends on *which* pass is running:
it inverts the transform that pass bound and divides by the `factor` it supplied.
The claim is first-come,
and a mod compositing a sector map of its own draws it from the campaign HUD -
before the map screen is composited -
so it takes the claim every frame and the read lands on its zoom and pan.
Random Assortment of Things' minimap does exactly this,
and the symptom is a hover offset from the pointer on the real map.

So `publishHoverForPass` runs on every pass the surface admits and the last write wins,
which puts the frame's answer on the surface that drew last.
It costs one matrix read per extra pass -
deferred rather than stalling under Fast Rendering -
and leaves one residual case:
a mod drawing a map surface *after* the map screen would win instead,
which the `Map - Compatibility` hover permissions are the escape from:
withhold both and only the vanilla hosts are read at all.
The same tab's compatibility mode narrows it further where the second surface is a parked minimap,
by stopping that surface rendering at all -
see [below](#silencing-a-minimap-parked-off-screen).

Only the read moves,
though.
Everything *about* the read that does not turn on the pass stays in the preparation:
whether any feedback still wants a hover,
and whether something is drawn over the cursor.
Neither changes between two passes of one frame,
and the last cover in the chain walks the live widget tree,
so a pass asks only what its own transform can answer and reads a flag for the rest.

The moment stays per frame too,
for the reason the claim states.
Two passes of one frame resolving different cells is exactly what a foreign transform produces,
so a latch stepped per pass would report a crossing on every frame the pointer rests still.
`MapHoverPublisher` therefore keeps what its passes settled on and answers the moment once,
from the preparation -
one frame behind the read,
which is 16ms and inaudible.

## What a frame costs

`MapFrameSections` names the beats a frame is profiled under,
and the sequence above is what those names describe:
`mapLayer.prepare` with `mapLayer.refresh` inside it,
`mapLayer.hoverPublish` per pass,
`mapLayer.render.beneathNebulae` / `mapLayer.render.aboveNebulae` per band,
and `mapLayer.tooltip`.
Published rather than spelled at each site,
so a consumer layer names the beat it runs under from one place
and a reader matching a name in a report finds the point in the sequence it belongs to.

`MapFrameBeats` is what opens them,
held by whatever sequences the frame -
the renderer today,
the framework once the roster takes foreign layers -
so the sequence names a beat and nothing else.

Each beat opens a **root** under the sector's profiling origin,
which [the installed machinery](../machinery/README.md) resolves once per sector.
Roots rather than one tree per frame
because the beats are separate calls from separate passes with nothing bracketing them,
and grouping by origin is what lets a capture taken across two games say which one a row came from.

The refresh is the exception:
it is opened *inside* the preparation rather than as a root of its own.
The sequence puts it there,
and a refresh hung off its own root would leave the preparation's inclusive total excluding the dearest thing it does.

Each beat also states what one call of it is allowed,
which is what turns the capture from a table into findings.
Two bounds today.
A beat may take the milliseconds the `Frame beat budget` knob on `Map - Dev` states,
asked for as the beat ends so a knob moved mid-session holds the next frame,
and 0 there states no bound at all.
That number reaches here as a bound value rather than a settings read,
through [`base/profiling`](../profiling/README.md#the-frame-beat-bound):
the knob deciding how much of the framework is measured sits in the same settings section,
and nothing here may be able to reach it.
The refresh may make one traversal of the sector per call -
the rule the framework's indexes exist to keep,
so a second walk is a pass that went looking for the sector
rather than asking for what had already been gathered.
That one traversal is the pass's own index of the sector's systems by `SystemKey`:
the cell sites come off it,
and the band bake's lookups by bare ID are addressed off those same systems
rather than off a walk of their own,
either being enough on its own to put a rebuild over the bound if it indexed the sector for itself.
A broken bound marks the row,
keeps the call that broke it as the row's kept call whatever it took -
which the report then names as the latest breach rather than as the row's worst -
and is written to the log once per section as it happens.

Inside each beat sits one row per layer,
`mapLayer.layer.<layer id>`,
opened around the layer's callback rather than by the layer.
So what a layer costs is read against the beat it cost it in,
a layer that measures nothing of its own still has a row,
and every section the layer's own work opens lands beneath its row instead of beside it -
which is what puts `mapLayer.updateGeometry` and its siblings under the refresh they ran in.
The stand-down reads sit inside the beat and outside that row,
so a frame with nothing to draw reports what standing down cost
and opens no row for a layer that never ran.

A rebuild's own steps say so in the log as they close.
Each registers its section on `RebuildStepTerms.LOGGED_EVERY_CALL` (in `kmu.maplayers.base.profiling`),
so every call of it is a debug line carrying the duration the row was accumulated from,
what the call counted,
and what it named itself.
That replaces the hand-written `took=` lines those steps used to print:
one measurement,
read in the log as a rebuild happens or in the report afterwards,
rather than two that could disagree.
The counts themselves are the three in `MapBuildCounters`,
beside the library's own sector counters -
few on purpose,
since every counter a shown row touches widens the reading it appears in;
anything that is a detail of one call rides on that call's name instead.

Stating a threshold also buys those calls a reading of their own conditions,
written straight after the duration as `first` and `jitMs=`:
a step's first call of a session runs on code the JIT has not compiled yet
and can cost half again what every warm call after it does,
which is a reading to discount rather than a regression to chase.
That is why the mark is worth having exactly here -
a rebuild step runs a handful of times a session,
so its first call is a large share of what a reader sees,
where a beat that runs every frame has warmed up before anyone looks.

Those lines follow the profiler,
which is the trade the single measurement is worth:
with the level knob off,
a rebuild says nothing at all,
where the hand-written lines were written whenever the log was at debug.
Turning the knob on is what a reader after them does,
and it is the same switch that gives them the report.

Nothing is recorded unless a profiler is bound:
the library's default keeps nothing and allocates nothing,
so an unmeasured frame pays a virtual call per beat and no more.
Which one is bound follows the `Profiling level` knob on `Map - Dev`,
which ships off -
so a player who never opens the `kmu_profiling` readout is not measured at all.
The beats above are coarse and so are timed by any capture that is running;
what a level rules out is the finer work inside a per-item loop.

What the readout writes is a reading of the capture rather than the capture itself.
`tree` is the beats as they were measured,
each row under the one it ran inside;
`flat` puts the rows that spent the time first,
each named by its whole path;
and `walks` keeps only the rows that traversed the sector,
worst call first,
which is the same witness the refresh's bound is stated against.
`namespace=` narrows any of them to one layer's rows -
the beats those rows sit inside kept,
so a share is still readable against the whole -
and `top=` keeps only as many as are wanted.

`perframe` divides the totals by the calls of `mapLayer.prepare`,
which is the beat that runs once a frame,
so a figure means the same whether the map was left open for four seconds or four minutes;
a capture holding no call of it says so and leaves the totals alone.

Every reading goes to `starsector-core/starsector.log`,
and the console gets a line saying so.
The table is wider than the overlay can lay out,
so what the overlay would show is a wrapped copy of what the log holds aligned -
and the log is the file a capture asked for in a bug report has to end up in anyway.
The notice is printed rather than nothing,
since a command that visibly did nothing reads as one that failed.

## Silencing a minimap parked off screen

The last-wins rule above settles which pass a frame's hover comes from;
it does not stop the pass.
A mod that docks a minimap parks the panel off screen rather than taking it down,
and a parked minimap goes on rendering a whole sector map every frame -
driving every terrain pass in the sector while it does,
ours among them,
and taking the frame's preparation claim on the way.
So a frame the player opened the `M` map on carries two transforms,
and which of them the cursor resolves through is the engine's child order
rather than anything promised.
A widget that renders nothing contributes no pass,
which leaves one transform and nothing for last-wins to arbitrate.

That is what `RandomAssortmentOfThingsMinimapSuppression` and KMLib's `OffScreenWidgetSuppressor` between them do:
hold the parked widget's own opacity at zero,
and hand back the value it was found at when it comes back.
The split is the compatibility mode's throughout.
What being parked means -
the widget's box meeting the screen at all -
and what switching one off consists of are stated over any widget at all and are KMLib's,
which is why nothing there names a mod or a map;
whether writing into another mod's panel is wanted is the per-mod question the player answers with the mode,
and this package answers only that,
plus the one map it may be aimed at.

Three things about it are worth knowing before touching it:

- **It reads where the widget is,
  never what shows of it.** The two are the same question until this writes,
  and the write is what parts them:
  a read that sifted out widgets drawn to nothing would stop reporting the very map it had just switched off,
  and the minimap would be parked for good.
- **It is not what closes the hover leak**,
  which the game-space permission and the minimap cover already close twice over on these frames -
  a cursor cannot fall inside an off-screen box.
  The offset is the fault it answers,
  along with the work:
  a sector map rendered behind every screen the player opens.
- **The slide is left alone.** A panel partly on screen is one the player can see,
  so it stays drawn and two maps render for as long as the slide lasts.
  Both faults last exactly that long,
  and neither is what this is for.

Exactly one embedded map may be suppressed,
and for a different reason than the cover's confinement needs one:
there a second map makes the hover unattributable,
while here the mode names a mod,
the widgets found carry no mod-owned class to match on,
and with two of them there is no telling which one the player switched the mode on for.
The reasons are per-rule;
the reading is not,
and all three readings that turn on such a surface -
this suppression,
the cover's confinement,
and the tooltip step-aside's search root -
take it from `SingleEmbeddedMapReader` in `kmu.starsector.ui`,
a walk of the live widget tree naming no mod,
which is why it sits there rather than with any one rule.
Copies of a count rule could be changed in one place,
leaving the cursor confined to a widget another had already switched off -
and each holding its own walk would descend the whole core UI
once per holder per frame on exactly the installs these rules exist for,
the finder walking afresh while it has found nothing.

It runs as an `EveryFrameScript` answering `runWhilePaused`,
which is what the mod's own panel script is -
so the hook is known to fire in the states this matters in,
a dialog or the menu among them.
It cannot run in a render pass at all:
the pass it suppresses is the last pass it would ever be asked from,
which on a screen showing no map would strand the minimap invisible for the session.

The residue is a session that ends parked.
This writes into a widget another mod owns,
so if it stops running -
that mod disabled mid-save -
the minimap stays invisible until its owner rebuilds the panel,
which is the next location change or the next load.

## Three terrains, one draw

A renderer belongs to one sector's installed map machinery,
made on the first frame that asks for it and released with that machinery:
nothing it holds enters a save,
so it needs no `transient` marking,
no lazy re-creation,
and no save-restore path.
The terrain plugin,
which *is* serialised with its terrain entity,
holds none of it -
it resolves the machinery from that entity each frame rather than carrying one.

`SectorMapLayerStarscapeTerrainPlugin` and `SectorMapLayerAboveStarscapeNebulaeTerrainPlugin` reach the same draw
while the map's Starscape filter is on -
that filter hard-suppresses custom terrain,
so the overlay needs a terrain the filter admits.
Each rides its own `SectorMapLayerStarscapeTerrain`,
one entity class parameterised by the row ID it resolves its plugin from,
since reporting the whitelisted type is the whole of what that class does.
All three surfaces dispatch to the same renderer over one set of draw lists -
one sector resolves one machinery,
which holds one of each -
so nothing is built or held twice.

Two entities rather than one because the map draws its nebulae between the terrain icons it holds:
a band above them needs an icon of its own,
which needs an entity of its own.
The plugins form a chain -
schematic,
then Starscape,
then above-nebulae -
each adding one thing:
the stand-aside,
then the band.

`MapLayerTerrainInstaller` is what puts any of them into a loaded save
and keeps exactly one of each there,
and it owns the type IDs they are built under.
`MapSurfaceInstaller` calls it and holds none of that knowledge itself,
as the mod's entry point holds none of the installer's.
Which entity in hyperspace belongs to which surface is decided by its plugin class alone,
compared exactly -
the three form a subclass chain,
so an `instanceof` would have one answer for another's,
and two of them report the engine's whitelisted map type
rather than the ID they were installed under,
so there is nothing else to tell them apart by.

All three terrain rows are declared in `data/campaign/terrain.json`.
Four class names from this package reach a save,
which is why [renaming one loses saves](#renaming-a-class-here-loses-saves).

The type IDs are frozen once shipped,
for the reason the class names are:
a terrain entity resolves its spec from the ID it was written under,
and nothing bridges a renamed one back.
The Starscape surfaces make that worse rather than better -
their entity reports the whitelisted map type in the getter's place,
so the ID it was installed under cannot be read back off it at all,
and a renamed row leaves an entity nothing can even recognise as ours.

## Where the Starscape surfaces sit in the draw order

Being drawn at all and being drawn on top are separate questions,
and the whitelisted type only answers the first.
The map's synthetic per-system nebula icons are seeded after everything hyperspace holds,
so both Starscape surfaces are painted beneath them by default -
and the one pass that runs after every terrain icon is the pass the widget skips in this mode,
which leaves no supported hook that paints over the fog.

Moving an icon up that order is nobody's content,
so none of the mechanism is here.
KMLib's `MapIconReseater` owns it,
and `MapIconReseatDecision` states how it works and what it rests on.
It acts on where the icon is,
read back from the widget,
rather than on a map having opened -
so a re-seeding it did not witness corrects itself on the next frame instead of lasting the session.

What KMU supplies is the two ports.
The map read is the Starscape one -
the entity being moved is one of the surfaces that stand aside while a schematic map is up -
and the entity read is `MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain`,
which resolves it afresh per call through the same plugin-class guard the install uses,
so the entity a load brought back is the one moved.
That the entity is a terrain,
and that the fog above it is what makes the move worth making,
is the whole of KMU's side.

`StarscapeTerrainReseat` is where both ports are wired and where the script is held -
by the machinery of the sector it was added to,
so a removal aimed at one sector takes off the script that sector is actually running.
One slot for the whole process could not:
a second sector installed on would take the slot over,
and the first sector's removal would then reach for a script it never had
while the one still running went untouched.
It is taken off by instance rather than by class besides,
`MapIconReseater` being KMLib's and another mod free to run its own over the same sector.
`MapSurfaceInstaller` only says when the reseat goes on and comes off,
and behind which guard.

Only the above-nebulae surface is moved.
The one beneath it is meant to be fogged,
so lifting both would flatten the split back into a single pass beneath the fog.

The move takes that terrain out of hyperspace for one advance,
which is among the reasons `installAboveStarscapeNebulaeTerrain` stays a per-load sweep;
its Javadoc has what that costs a save written inside the window.
Losing the surface entirely costs the upper band alone -
the lower band still paints,
and whatever the layer placed above it stops appearing.

## Renaming a class here loses saves

Four class names from this package are written into every save file:
the three terrain plugins',
serialised with the entities holding them,
and `SectorMapLayerStarscapeTerrain`'s -
the Starscape surfaces use a mod-owned entity where the base one uses a vanilla one.

XStream stores the concrete class name.
A save naming a class that no longer exists does not fail gracefully and does not lose the overlay -
it fails the whole read with `CannotResolveClassException`,
and the player cannot load that game at all.
Renaming,
moving or deleting any of the four is therefore a save-destroying change to every campaign already running with this mod.

Nothing currently bridges an old name to a new one.
Four aliases used to,
covering this package's own rename history,
and they were dropped once the mod was young enough that no save worth keeping predated them.
So the rule for the next rename is:

- **Before shipping a rename**,
  add a `configureXStream` override on the mod plugin that calls `x.alias("<the old fully-qualified name>", TheClass.class)`.
- **Register the live name last**,
  aliased to itself,
  so a re-saved game is written under the real class name
  and sheds the historical one rather than carrying a dead reference forever.
- **Keep each alias** for as long as saves predating that rename might still exist.
  They are read-only bridges and cost nothing to hold.

A rename shipped without one is not caught by the build or by the mod's own logging.
It surfaces as a player reporting that their campaign will not load.

## What is not here

- **[Clusters](clusters/README.md)** -
  the shape work a painting layer draws through this surface:
  the cluster-border trace,
  the smoothing passes,
  the vertex packing,
  and the split fill that puts several fills inside one border.
- **`base.layer`** -
  which layers exist,
  which one each screen has picked,
  and how that pick is persisted.
  The surface only asks the registry for the active one.
- **`base.hover`** -
  the cursor's half of the render pass:
  the read that resolves a cursor pixel to a cell and the cluster around it,
  the halo and the wash drawn on that answer,
  and the two seams a layer supplies them through.
- **`base.tooltip`** -
  what the box floating beside that cursor says,
  drawn in a later UI pass than this one and dispatched through the same active-layer read.
- What any layer actually paints,
  and what its owners mean,
  belongs to the tiers above;
  for a layer painted by owner the assembly it draws through is [the owner-map tier](../../ownermap/README.md).
