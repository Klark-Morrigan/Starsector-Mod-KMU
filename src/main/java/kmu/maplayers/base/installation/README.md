# Installed machinery (`base.installation`)

One sector's map machinery as a thing a caller can hold. Everything the map layers draw is derived
from one sector - which systems exist, who holds each, where they sit, what the cursor is over - so
every holder behind that drawing is a fact about one sector rather than about the process. This
package is where that lifetime lives: made when the layers are installed on a sector, released when
they are removed.

Part of [the map layers](../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../README.md) for project context.

## Index

- [Why a sector owns this at all](#why-a-sector-owns-this-at-all)
- [What an installation holds](#what-an-installation-holds)
- [How a layer's own machinery gets in](#how-a-layers-own-machinery-gets-in)
- [The lifetime](#the-lifetime)
- [Three ways to resolve one](#three-ways-to-resolve-one)
- [The detached installation](#the-detached-installation)
- [Where a sector-less seam still reads the global](#where-a-sector-less-seam-still-reads-the-global)
- [What is not one sector's](#what-is-not-one-sectors)

## Why a sector owns this at all

Two sectors sharing one holder is not merely unsupported, it is wrong and quiet.

Everything under the drawing is keyed by **bare system id**. The stale set names systems by id, the
motion tracker keys observations by id, and `CellGeometryCache` reconciles cells by id. Nothing
forbids two sectors from generating a system under the same one. So a shared holder means one
sector's colony change marks the other's system stale; one sector's drift reads as the other's; and
a system present in both at different positions is not seen to have moved, so each sector keeps the
cells the other cut rather than overwriting them.

The revisions make it worse rather than better: shared counters leave neither sector able to be
stale on its own, since either one's change rebuilds both.

The tell that a holder belongs to a sector is that it used to need emptying when the sector changed.
Every holder here once had a per-load discard; disposal replaced all of them.

## What an installation holds

`MapLayerInstallation` holds four things directly. The first three are the framework's own state:

| Holder | What it remembers |
| --- | --- |
| `MapLayerRefreshBoard` | what went stale in this sector since each consumer last looked - a revision per signal, plus the stale-system set |
| `MovingSystems` | where this sector's systems were last seen, and so which are drifting rather than sitting still |
| `MapHoverState` | what the cursor is over on this sector's map |

The fourth is the **sector itself**, answered by `resolveSector()`. It is there because two seams
below this package are handed no sector to pass down: vanilla's map render hook is given a fade
factor and nothing else, and a render surface is terrain, which reaches a `LocationAPI` and never a
`SectorAPI`. A stage under either would otherwise ask the running game which sector it is looking
at - correct only while the sector it holds cells for and the sector that is loaded are the same
one. Taking it off the installation rather than passing it alongside is what stops a rebuild cutting
cells from one sector while reading holders out of another; the political map's cache and its
staleness poll both do exactly that.

## How a layer's own machinery gets in

A layer's renderer, and the caches behind it, are one sector's too - but they live in packages
*downstream* of this one, and an installation that named them would point back at its own
dependents. So they go in through `InstalledMachinery`, which is a release contract and nothing
else:

```java
installation.resolveMachinery(
    PoliticalMapLayerRenderer.class,
    () -> PoliticalMapLayerRenderer.createForLiveScreen(installation));
```

Keyed by the class asked for, made on the first ask, and handed back by that same type - so one
sector cannot come to hold two of a kind, and the caller needs no cast. The installation never
learns what it is holding.

Release has to be certain rather than incidental: a cached faction name owns a GL buffer, so a
sector removed mid-session would leak every one it had built if disposal were left to a finalizer
sweep.

## The lifetime

`MapLayerInstallations` is the process-wide index, keyed by the sector object itself.

- **`installMachineryOn(sector)`** makes a fresh installation, releasing whatever that sector
  already had. Replacing rather than reusing is what keeps a second install from inheriting the
  first one's cached drawing. Done in one atomic step, so a frame resolving on the render thread
  between a removal and a re-insertion cannot observe the replacement as an absence.
- **`uninstallMachineryFrom(sector)`** releases and forgets. Reached with a sector nothing was ever
  installed on too, since a load with the overlay switched off takes it back rather than declining.
- **`disposeEveryInstallation()`** releases all of them. This is what a load runs *before* installing
  on the sector it loaded, and it is the only point at which a previous save's drawing can be
  stopped from outliving it - nothing else in the engine is told that a sector went away, and the
  index holds each sector by reference.

`KMU_ModPlugin` drives all three: `installMapLayers` stands the installation up first, before the
four installers that stack on it, and `uninstallMapLayers` takes it back last, since those four are
taken back *through* the state it holds.

Concurrent throughout: installing and removing happen on the campaign thread while a frame resolves
what it is about to draw on the render thread. `MapLayerInstallation` also carries a volatile
`isDisposed`, because a resolution taken at the top of a frame outlives a removal that happens
during it.

## Three ways to resolve one

Three, because the seams below differ in what they hold.

| Resolution | For | Answers when nothing is installed |
| --- | --- | --- |
| `resolveInstallationFor(sector)` | anything already holding a sector - an installer, a listener | the detached installation |
| `resolveInstallationIn(location)` | a render surface, which is terrain and reaches only its containing location | **null** |
| `resolveInstallationForLiveSector()` | a seam vanilla hands no sector at all | the detached installation |

The location resolution walks the installed sectors comparing `sector.getHyperspace() == location`
rather than keeping a location index of its own. There is one installed sector, so the walk is the
cheap half of the trade, and what it buys is that a location can never disagree with the sector it
belongs to: a second index would have to be written *after* the sector key, leaving a window in
which a frame resolving by location was handed the installation a reinstall had just released - and
would then build a renderer, and its GL buffers, onto a holder nothing will ever release.

It rests on one identity: `entity.getContainingLocation()` is the same object
`sector.getHyperspace()` returns. It holds because the terrain is added to that hyperspace and the
engine seats an added entity in the location it was added to. Stated rather than assumed, because a
comparison that missed would take the whole overlay off screen instead of degrading.

It is also the only resolution that answers **null**, and deliberately. A surface exists because an
installation put its terrain there, so a location with no installation is a surface belonging to a
sector nothing draws - a save carrying the terrain with the overlay switched off. Handing it the
detached installation would have it paint through the holder every sector-less caller shares, which
is a drawing of no sector at all rather than a fallback.

## The detached installation

One shared installation that nothing indexes and nothing releases, over no sector. It is what an
uninstalled sector resolves to, and it exists because the map layers sit behind a switch a player
can leave off: answering null there would make every seam below branch on a case that means "carry
on as you always did".

Because it is over no sector, `resolveSector()` answers null on it, and a stage reaching it finds
nothing to read rather than falling through to whichever sector happens to be loaded.

## Where a sector-less seam still reads the global

`resolveInstallationForLiveSector()` is the one place in this package that reads
`Global.getSector()`, and it is where that read belongs: vanilla's API offers no other handle, and
the seams it serves are driven by the engine with no sector named.

Two holders are reached that way from outside. `MapLayerRefresh`'s statics resolve the live sector's
board, and `MapHoverState.resolveLiveSectorHoverState()` the live sector's hover; both stand for
readers that are reached from a seam holding no sector. A reader that *does* hold one - or holds an
installation - goes direct instead, which is why the render surfaces, the political map's cache and
its staleness poll name none of these.

## What is not one sector's

Kept out of here on purpose, so a reader does not go looking:

- **The rosters.** `MapLayerRegistry`'s ordered layers, default and intel screen, and
  `PoliticalMapViewRegistry`'s ordered views, default and host tab, are mod-load facts written once
  by `MapLayers.registerAll` before any sector exists. Which layers *exist* is the process's; which
  is *picked* is the sector's, and that half lives in sector memory.
- **The views and layers themselves.** They are stateless strategies. One that started remembering
  would become a shared cache two installations read.
- **Every selection.** The active layer and view, the filter pick, the name format, the recede style
  and the sighting register are already one sector's - they live in that sector's own memory, and
  need nothing from this package.
- **`HoverTooltipDetailLevelState`.** A reading preference, not a fact about a sector; a player who
  set it in one would be surprised to lose it in another.
- **The sidebar hosts.** They host a panel on a screen, and one screen is showing at a time.
