# Political map refresh (`politicalmap.refresh`)

What repaints the political map while the campaign runs:
the poll that finds the changes nobody announced,
and the listeners that catch the ones the engine does fire an event for.
Both mark the same targeted stale set on the framework's refresh board,
so a change a listener caught and the poll's diff re-discovers collapse to one reshape.

The board,
the signals and the poll loop are the framework's -
see [refresh](../../base/refresh/README.md);
what each signal drives is [the caching notes](../../../../../../../docs/dev/caching.md).

Part of [the political map](../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [The poll](#the-poll)
- [Its own script class](#its-own-script-class)
- [The listeners](#the-listeners)
- [The colony-transfer relay](#the-colony-transfer-relay)
- [The alliance signal](#the-alliance-signal)
- [What is not here](#what-is-not-here)

## The poll

`PoliticalMapStalenessSource` is what this layer counts as a change the engine never announced -
a gate activating,
a system being cut off,
a decivilised colony surveyed,
an AI faction founding or capturing a colony -
answered into the framework's poll.
It reads one `PoliticalMapSectorSnapshot` per poll and routes each axis on its own:

| Axis | Read as | Refresh it earns |
| --- | --- | --- |
| which systems are drawn | one scalar fingerprint | a whole-map geometry rebuild, the partition depending on every site |
| who holds each system | a holder map, diffed against the last poll | the changed systems marked politics-stale, as a listener marks them |
| which systems move | `MovingSystems`' moving set | a geometry rebuild when a system starts or stops moving |
| who is allied | the alliance fingerprint | the alliance signal ([below](#the-alliance-signal)) |

The visibility,
holder and alliance baselines live on the source,
since each is the staleness of this layer's own picture.
The moving set's baseline is `MovingSystems`',
because the geometry reads that set too.
The first poll only establishes them.

The snapshot takes both of its halves in the poll's one walk.
Membership is asked of the pass rather than derived from the footprints,
for the reason `PoliticalMapSectorSnapshot` states.

The sector-wide sweep recording what each system's inhabitants can see is not this layer's:
its register is shared by every map family,
so it is [the substrate's](../../base/refresh/README.md).

## Its own script class

`PoliticalMapSectorWatcher` is the script that runs the poll,
this layer's own subclass of the framework's abstract `MapLayerSectorWatcher`.
The engine removes transient scripts by exact class,
so a script class shared between layers would let one layer's install or removal evict another's poll.

## The listeners

The listeners sit in `refresh/listeners`,
and `PoliticalMapInstaller` registers and removes all five together.
Each turns one event into a call to `MarketPoliticsRefresh`,
which owns what every such event does:
the seat guard,
the targeted mark,
the log line,
and recording what the system's inhabitants can see at the moment of the event.
Each listener's own doc says only what its event adds.

| Listener | Event | What it adds |
| --- | --- | --- |
| `PoliticalMapColonySizeListener` | a colony resized | nothing; size feeds the weight |
| `PoliticalMapDiscoveryListener` | an entity discovered | a marketless discovery is filtered first |
| `PoliticalMapColonisationListener` | the player founds or abandons a colony | neither is a resize or a decivilisation, so neither sibling sees it |
| `PoliticalMapDecivListener` | a colony decivilises | records on the `aboutToBe` phase, marks on the completed one |
| `PoliticalMapMarketTransferListener` | a colony changes hands | vanilla fires nothing for it; see [below](#the-colony-transfer-relay) |

A decivilisation is recorded on the `aboutToBe` phase,
since once the colony has died there is nobody left to date what it vouched for.
Where an event fires too late to read the system as it was -
an abandonment,
a Nexerelin transfer -
the comment at that listener says so,
and the colony keeps the sighting it already had.

## The colony-transfer relay

`PoliticalMapMarketTransferListener` answers a colony changing hands,
which vanilla fires nothing for.
It implements KMU's own `kmu.starsector.listeners.MarketTransferListener` and names no Nexerelin type.
`kmu.mods.nexerelin.NexerelinMarketTransferRelay` -
one per sector,
installed by `KMU_ModPlugin.installMapLayers` through `NexerelinInvasionListenerInstaller` -
forwards Nexerelin's transfers to every `MarketTransferListener` on the sector's listener manager.
That keeps `kmu.maplayers` closed to optional-mod imports;
on an install without Nexerelin the listener is registered and never called.

Nexerelin calls the relay at the end of its market transfer,
with no catch of its own above it,
so the relay tells each listener behind a boundary.
A listener that throws is logged with its trace,
the listeners after it are still told,
and it is told again on the next transfer.
The listener is a fast path only:
the poll diffs every system's holder and repaints a changed one within seconds,
so a throw there costs nothing worth a notice to the player.
What Nexerelin reports and when is [the Nexerelin notes](../../../../../../../docs/dev/nexerelin.md).

## The alliance signal

`PoliticalMapRefreshSignal` holds the coarse changes only this layer can raise on the shared board,
alliance membership being the one.
Every view of this layer folds its revision into its content revision
(`computeAllianceContentRevision`).
The alliances view paints its blocs out of the alliance set;
the faction and claims views judge their bands' contests against it.

The poll compares the alliance records whole,
as a set so the order a source reports them in does not count.
Comparing what one view reads instead would miss what another view reads:
the alliances view shows each alliance's name as well as its members and lead.
The source is handed in by `MapLayers`, the composition root.
On an install without Nexerelin it is `AllianceSource.NO_ALLIANCES`,
so the poll never fires and no Nexerelin class loads.

## What is not here

- **[Refresh](../../base/refresh/README.md)** -
  the framework's board,
  signals and poll loop,
  and the substrate's sightings sweep.
- **[The caching notes](../../../../../../../docs/dev/caching.md)** -
  the signals,
  the caches,
  and the rebuild paths a mark drives.
- **[The mod adapters](../../../mods/README.md)** -
  the Nexerelin relay behind its presence gate.
