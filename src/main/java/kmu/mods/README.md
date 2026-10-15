# Optional-mod adapters (`kmu.mods`)

What KMU does with the mods it can run without:
Console Commands,
Nexerelin,
and Random Assortment of Things.
Each has one subpackage,
and nothing else in KMU names one of those mods' types.

Part of Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../README.md) for project context.

## Index

- [The subpackages](#the-subpackages)
- [The presence gate](#the-presence-gate)
- [Deferring a coupled class](#deferring-a-coupled-class)
- [Nothing coupled reaches a save](#nothing-coupled-reaches-a-save)
- [What the build enforces](#what-the-build-enforces)
- [What is not here](#what-is-not-here)

## The subpackages

| Package | What is in it |
| --- | --- |
| `console` | KMU's console commands, under `console.commands` |
| `nexerelin` | the live alliance set read three ways (`NexerelinAlliances`), and the contest wording an install with colony transfers calls for (`NexerelinContestWording`) |
| `rat` | the compatibility mode for Random Assortment of Things' minimap: the switch (`RandomAssortmentOfThingsCompatibilityMode`), what it stands up (`RandomAssortmentOfThingsCompatibilityInstaller`), the cover confining the hover to the minimap, the widget it may switch off, and the mod's settings as KMU reads them |

Most of these read the mod through KMLib's `kmlib.mods` classes rather than through the mod's own types,
so the mod-specific knowledge is shared with every mod built on KMLib.
No KMU class implements or registers a Nexerelin type.
KMLib puts its own relay for Nexerelin's colony transfers on each loaded sector,
and KMU only registers the listener it tells.

## The presence gate

Every adapter asks whether its mod is enabled before doing anything,
through the library's presence check (`NexerelinPresence.isModEnabled()` and its siblings).
The check answers rather than throws when asked before the game has stood its settings up.
Presence cannot change within a run,
so where an adapter joins a set -
a map cover,
a feature switch -
presence is settled once by the composition root that builds the set,
rather than asked of a part that could only ever answer no.

An optional mod read through its settings rather than its types needs the gate and nothing more:
there is no class to defer,
only a read to skip.

Console Commands is gated by Console Commands itself.
It reads `data/console/commands.csv` at load and is the only thing that ever constructs a command,
so a command class is loaded only where that mod is installed.

## Deferring a coupled class

A class that names a mod's own type must never be loaded on an install without that mod,
or the JVM fails looking for the missing type.
So the sole reference to such a class sits in a nested holder,
which the classloader resolves on first call,
and the gate in front of that call defers it until the mod is known present.
KMU holds no such class today.
KMLib's `NexerelinIntegration.TransferRelay` is the pattern:
`installTransferRelay` returns before touching the holder,
so `exerelin.utilities.InvasionListener` is never sought otherwise.
KMLib's Abyssal Fracture matcher isolates Random Assortment of Things the same way.

Each step that links a coupled class runs as a guarded step (`KmuWiringSteps.runGuardedStep`).
A mod release that moves or renames the type then fails that step with a link error,
reported to the player as what KMU loses,
rather than failing the whole load.

## Nothing coupled reaches a save

An object implementing a mod's interface is registered transient,
so no save ever names its class.
A save naming it would fail to load once the mod was removed.
It is re-added on every load instead,
exactly when the gate still passes,
and removed before it is added,
so a save that does carry a copy is repaired by the load.

## What the build enforces

`gradle/package-layering.gradle` closes every other package tree to the optional mods' packages,
listed tree by tree for the reason stated there;
a new top-level tree has to join the list to be covered.
The map-layer framework is closed to `kmu.mods` itself as well:
what an adapter adds to it is handed in by the composition roots,
so the framework never depends on which mods are installed.
LazyLib and LunaLib are not optional mods:
KMU is compiled against them and cannot run without them,
so they belong with the code that uses them.

The mod plugin sits in the root package and registers the adapters,
so it names `kmu.mods`,
but it may not name a mod's own types:
that would load them on every install and defeat the deferral above.

The build still requires Nexerelin's jar,
so a player can run KMU without Nexerelin while a developer cannot build it without.

## What is not here

- **KMLib's `kmlib.mods`** -
  the presence checks and the reads of each mod that any mod built on KMLib shares.
- **[The map covers](../maplayers/base/hover/cover/README.md#covers-from-optional-mods)** -
  where the minimap cover joins the hover's set.
- **[Political map refresh](../maplayers/politicalmap/refresh/README.md#the-colony-transfer-relay)** -
  the listener KMLib's Nexerelin relay tells about each colony transfer.
- **[The Nexerelin notes](../../../../../docs/dev/nexerelin.md)** -
  what Nexerelin reports and when.
