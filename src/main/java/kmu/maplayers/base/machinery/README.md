# Installed machinery (`base.machinery`)

One sector's map machinery as a thing a caller can hold.
Everything the map layers draw is derived from one sector -
which systems exist,
who owns each,
where they sit,
what the cursor is over -
so every holder behind that drawing is a fact about one sector rather than about the process.
This package is where that lifetime lives:
made when the layers are installed on a sector,
released when they are removed.

Part of [the map layers](../../README.md),
in Klark Morrigan's Utilities;
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [Why a sector owns this at all](#why-a-sector-owns-this-at-all)
- [What machinery holds](#what-machinery-holds)
- [How a layer's own machinery gets in](#how-a-layers-own-machinery-gets-in)
- [The lifetime](#the-lifetime)
- [Standing a layer up on one sector](#standing-a-layer-up-on-one-sector)
- [Three ways to resolve one](#three-ways-to-resolve-one)
- [The detached machinery](#the-detached-machinery)
- [Where a sector-less seam still reads the global](#where-a-sector-less-seam-still-reads-the-global)
- [What is not one sector's](#what-is-not-one-sectors)

## Why a sector owns this at all

Two sectors sharing one holder is not merely unsupported,
it is wrong and quiet.

Everything under the drawing is keyed by **one sector's naming of its systems**.
The stale set names systems by `SystemKey`,
the motion tracker keys observations by `SystemKey`,
and [the geometry cache](../geometry/README.md) reconciles cells by `SystemKey`.
Nothing forbids two sectors from generating a system under the same ID,
and the key's engine-minted arms are minted by each sector without regard to another's.
So a shared holder means one sector's colony change marks the other's system stale;
one sector's drift reads as the other's;
and a system present in both at different positions is not seen to have moved,
so each sector keeps the cells the other cut rather than overwriting them.

The revisions make it worse rather than better:
shared counters leave neither sector able to be stale on its own,
since either one's change rebuilds both.

The tell that a holder belongs to a sector is that it used to need emptying when the sector changed.
Every holder here once had a per-load discard;
disposal replaced all of them.

## What machinery holds

`SectorMapMachinery` holds five things directly.
The first three are the framework's own state:

| Holder | What it remembers |
| --- | --- |
| `MapLayerRefreshBoard` | what went stale in this sector since each consumer last looked - a revision per signal, plus the stale-system set |
| `MovingSystems` | where this sector's systems were last seen, and so which are drifting rather than sitting still |
| `MapHoverState` | what the cursor is over on this sector's map |

Which signals can be raised on that board,
who raises each,
and which of the four rebuild paths it drives are [the caching notes](../../../../../../../docs/dev/caching.md);
this package settles only whose board it is.

The fourth is the **sector itself**,
answered by `resolveSector()`.
It is there because two seams below this package are handed no sector to pass down:
vanilla's map render hook is given a fade factor and nothing else,
and a render surface is terrain,
which reaches a `LocationAPI` and never a `SectorAPI`.
A stage under either would otherwise ask the running game which sector it is looking at -
correct only while the sector it holds cells for and the sector that is loaded are the same one.
Taking it off the machinery rather than passing it alongside is what stops a rebuild cutting cells from one sector
while reading holders out of another -
which a layer's draw cache and staleness poll both rely on.

The fifth is the **profiling origin**,
answered by `resolveProfilingOrigin()`:
the label every profiling root opened for this sector is grouped under,
so a capture taken across two sectors says which of them each row was measured in
rather than averaging the two into rows that describe neither.
Composed once with the machinery,
out of the seed and the player's name -
`kmlib.starsector.SectorLabels` is what pairs them,
being the pair a save browser shows and so the pair a reader can match a slow row back to a save by.
Once rather than per beat,
because a frame opens several roots and none of them may spend its time building a string.
The detached machinery is nobody's sector and takes the reserved origin unattributed spans land in.

## How a layer's own machinery gets in

A layer's renderer,
and the caches behind it,
are one sector's too -
but they live in packages *downstream* of this one,
and machinery that named them would point back at its own dependents.
So they go in through `InstalledMachinery`,
which is a release contract and nothing else:

```java
machinery.resolveLayerMachinery(
    LAYER_ID,
    OwnerMapLayerRenderer.class,
    () -> OwnerMapLayerRenderer.createForLiveScreen(machinery, LAYER_ID /* , the layer's answers */));
```

Keyed by the class asked for and by whose it is,
made on the first ask,
and handed back by that same type -
so one sector holds one of a kind per holder,
and the caller needs no cast.
The machinery never learns what it is holding.

There are two holders a piece can have.
`resolveMachinery` keys by the class alone,
for the framework's own pieces,
of which a sector has exactly one whichever layers stand on it.
`resolveLayerMachinery` keys by the class and a layer's ID,
for a piece more than one layer holds a copy of:
two owner-painted layers each draw through a renderer of the same class,
and keyed by the class alone the second would be handed the first's cells, picks and answers.
Both kinds go with the sector,
so a layer's pieces need no release of their own.

Release has to be certain rather than incidental:
a cached faction name owns a GL buffer,
so a sector removed mid-session would leak every one it had built if disposal were left to a finalizer sweep.

## The lifetime

`SectorMapMachineryIndex` is the process-wide index,
keyed by the sector object itself.

- **`installMachineryOn(sector)`** makes a fresh machinery,
  releasing whatever that sector already had.
  Replacing rather than reusing is what keeps a second install from inheriting the first one's cached drawing.
  Done in one atomic step,
  so a frame resolving on the render thread between a removal
  and a re-insertion cannot observe the replacement as an absence.
- **`uninstallMachineryFrom(sector)`** releases and forgets.
  Reached with a sector nothing was ever installed on too,
  since a load with the overlay switched off takes it back rather than declining.
- **`disposeAllMachinery()`** releases all of them.
  This is what a load runs *before* installing on the sector it loaded,
  and it is the only point at which a previous save's drawing can be stopped from outliving it -
  nothing else in the engine is told that a sector went away,
  and the index holds each sector by reference.

`KMU_ModPlugin` drives all three:
`installMapLayers` stands the machinery up first,
before everything that stacks on it,
and `uninstallMapLayers` takes it back last,
since all of that is taken back *through* the state it holds.

A fourth reading is over all of them rather than over one.
`getAllMachinery()` lists the installed sectors' machinery,
for a caller acting on a preference that is one preference for every campaign -
which is what the bar arrangement is.
It is a snapshot rather than the index's own view,
so a walk that installs or removes as it goes acts on the row it asked for,
and the detached machinery is not among them.

Concurrent throughout:
installing and removing happen on the campaign thread
while a frame resolves what it is about to draw on the render thread.
`SectorMapMachinery` also carries a volatile `isDisposed`,
because a resolution taken at the top of a frame outlives a removal that happens during it.

## Standing a layer up on one sector

Installing the machinery is not the same as a layer running on the sector.
The machinery is the holders everything drawn is derived from;
a layer's **standing** is what that layer itself registers on the sector -
its listeners,
its polls,
its save heals -
and whether it gets one is the player's,
decided by whether they keep its tab on the bar.
The rule is [the layer framework's](../layer/README.md#what-a-hidden-tab-stands-down);
what this package settles is the scope it acts in.

That scope is one sector,
so a change to the bar is applied by walking the machinery rather than the loaded sector alone:
a sector the walk skipped would keep the wiring of a tab that is no longer on the bar for any campaign.

Which layers are standing on a sector is `StandingLayers`,
and it comes in as `InstalledMachinery` like a renderer does -
made on the first ask,
released with the machinery,
never named by this package.
Disposal forgets rather than stands anything down:
machinery is disposed for a sector the load has already replaced,
and everything a layer registers on a sector is transient,
so there is nothing left to take back
and a stand-down aimed at it would reach the sector that replaced it.

## Three ways to resolve one

Three,
because the seams below differ in what they hold.

| Resolution | For | Answers when nothing is installed |
| --- | --- | --- |
| `resolveMachineryFor(sector)` | anything already holding a sector - an installer, a listener | the detached machinery |
| `resolveMachineryIn(location)` | [a render surface](../render/README.md), which is terrain and reaches only its containing location | **null** |
| `resolveMachineryForLiveSector()` | a seam vanilla hands no sector at all | the detached machinery |

The location resolution walks the installed sectors comparing `sector.getHyperspace() == location`
rather than keeping a location index of its own.
There is one installed sector,
so the walk is the cheap half of the trade,
and what it buys is that a location can never disagree with the sector it belongs to:
a second index would have to be written *after* the sector key,
leaving a window in which a frame resolving by location was handed the machinery a reinstall had just released -
and would then build a renderer,
and its GL buffers,
onto a holder nothing will ever release.

It rests on one identity:
`entity.getContainingLocation()` is the same object `sector.getHyperspace()` returns.
It holds because the terrain is added to that hyperspace
and the engine seats an added entity in the location it was added to.
Stated rather than assumed,
because a comparison that missed would take the whole overlay off screen instead of degrading.

It is also the only resolution that answers **null**,
and deliberately.
A surface exists because an machinery put its terrain there,
so a location with no machinery is a surface belonging to a sector nothing draws -
a save carrying the terrain with the overlay switched off.
Handing it the detached machinery would have it paint through the holder every sector-less caller shares,
which is a drawing of no sector at all rather than a fallback.

## The detached machinery

One shared machinery that nothing indexes and nothing releases,
over no sector.
It is what an uninstalled sector resolves to,
and it exists because the map layers sit behind a switch a player can leave off:
answering null there would make every seam below branch on a case that means "carry on as you always did".

Because it is over no sector,
`resolveSector()` answers null on it,
and a stage reaching it finds nothing to read
rather than falling through to whichever sector happens to be loaded.

## Where a sector-less seam still reads the global

`resolveMachineryForLiveSector()` is the one place in this package that reads `Global.getSector()`,
and it is where that read belongs:
vanilla's API offers no other handle,
and the seams it serves are driven by the engine with no sector named.

Its callers are the screen-side adapters and nothing else:
the tab body build and the hover box the cursor read draws,
each handed a frame and nothing more.
They spell the resolution by that name rather than `resolveMachineryFor(Global.getSector())`,
so every such adapter is findable by one grep
and the global read stays inside this package where `enforceRestrictedCalls` contains it.

Nothing else reaches a holder that way.
Every producer and consumer there is holds the machinery it means:
the render surfaces resolve theirs from the terrain entity they ride,
and a layer's cache,
its staleness poll,
its renderers,
its views and its sidebar controls are each handed one.

The sidebar controls are the ones worth naming,
because a settings change looks like the seam that could not be handed a sector.
It can:
the tab's body build resolves machinery once,
and every control it places carries that board to the preference it writes -
so a flip repaints the map the control was placed over
rather than whichever sector is running when the click lands.

The board and the hover have no such resolution at all.
`MapLayerRefreshBoard` and `MapHoverState` are reached only through the machinery that holds them,
and `kmu.maplayers.base.refresh` and `kmu.maplayers.base.hover` are both gated from importing this package so it stays that way:
a holder that could resolve machinery of its own could only resolve the running game's,
which is the one sector a caller drawing another's map is not looking at.

## What is not one sector's

Kept out of here on purpose,
so a reader does not go looking:

- **The rosters.** `MapLayerRegistry`'s ordered layers,
  `MapLayerScreens`' intel screen,
  and each owner-painted layer's own `MapLayerViewRegistry` -
  its ordered views,
  default and host tab -
  are mod-load facts.
  KMU's own are made in `MapLayers.registerAll` before any sector exists,
  and a layer another mod ships joins the roster when that mod loads -
  later still,
  and no nearer any sector.
  Which layers *exist* is the process's;
  which is *picked* is the sector's,
  and that half lives in sector memory.
- **The views and layers themselves.** They are stateless strategies.
  One that started remembering would become a shared cache two machinery read.
  The claims view is the one that holds a field at all,
  and it holds the *means of opening* a claim reader rather than a reader -
  one is opened over the read being made and discarded with it.
- **The alliance-source registration.** `FactionAllianceRegistry`'s registered source is written
  once by the composition root,
  which is the only place in a position to know what is installed.
  It memoises nothing:
  every pass reads the arrangement standing at that moment through it.
- **The diagnostic throttles.** What has already been logged
  (a repeated font tolerance, a foreign render pass) and whether a vanilla map is on screen are facts about the process and the screen,
  not derivations from a sector.
- **Every selection.** The active layer and view,
  the filter pick,
  the name format,
  the recede style and the sighting register are already one sector's -
  they live in that sector's own memory,
  and need nothing from this package.
- **`HoverTooltipDetailLevelState`.** A reading preference,
  not a fact about a sector;
  a player who set it in one would be surprised to lose it in another.
- **The sidebar hosts.** They host a panel on a screen,
  and one screen is showing at a time.
