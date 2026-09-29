# Game reaches (`base.compatibility`)

What the map layers lose when a game release changes the code they reach into,
as the reporters a failed reach is filed through.

Part of [the map layers](../../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [Why the map layers report at all](#why-the-map-layers-report-at-all)
- [One reporter per loss](#one-reporter-per-loss)
- [What is not here](#what-is-not-here)

## Why the map layers report at all

The map layers stand on the game's own screens:
whether a map is up is read off the game's concrete campaign-UI data and the intel screen's panel,
the tick box is built on the game's filter row,
and the dialogs, the codex and the star tooltip they stand aside for are found by walking the game's widgets.
None of that is published API,
so a game release can move any of it.
Each reach already fails softly,
and KMLib's compatibility channel is what turns a soft failure into a notice naming Starsector,
the game version KMU was made for and the one running,
and what stopped working -
the channel itself is set out in KMLib's `starsector/compatibility/` README.

The library knows which reach broke and nothing of what was built on it,
so the sentence saying what the player lost is KMU's,
out of KMU's own strings.

## One reporter per loss

[`MapLayerGameReach`](MapLayerGameReach.java) has one constant per thing a player loses,
not one per reach,
each holding its feature key, both of its string keys and the reporter built from them -
so a loss cannot be filed with another loss's sentence:

| Constant | What the player loses |
| --- | --- |
| `MAP_VIEW` | the layers and the sidebar on the sector map or the intel screen's map |
| `FILTER_ROW_TOGGLE` | the tick box on the filter row, or the key written on it |
| `ARRANGE_DIALOG` | the dialog the layers are arranged in |
| `SCREEN_COVERS` | standing aside for the game's own dialogs and the codex |
| `MAP_TOOLTIPS` | standing aside for the game's star tooltip, and repainting it over the sidebar |
| `STARSCAPE_RESEAT` | the upper band kept above the map's nebulae |
| `STARSCAPE_TERRAIN` | drawing at all while the Starscape filter is on |

Several reaches can cost one thing -
the sector map's view state and the intel screen's panel both decide whether the layers show -
and a reporter per reach would tell the player one loss twice.
Each reporter files once for the session,
its sentences read out of strings.json on that first failure alone.

Every binding KMU takes names itself through one helper,
[`KmuCompatibilityConsumers`](../../../util/KmuCompatibilityConsumers.java),
so a reach into the game, a renderer's internals and LunaLib's settings all file under KMU alike.

A reporter is handed to the probe at every call site that reads the game,
so the probe files under KMU without knowing KMU exists.
The Starscape terrain is the one reach KMU makes itself:
its entity extends the game's terrain class,
so [`MapLayerTerrainInstaller`](../render/MapLayerTerrainInstaller.java) files a build that no longer links against it
and passes the failure on to the install step's own guard.
A link failure is the one failure of that build that cannot be KMU's own.

## What is not here

- The probes.
  KMLib's `starsector/ui/` packages own every reach into the game's widgets,
  and each takes the reporter it files through.
- The notice.
  KMLib's compatibility channel composes and shows it,
  and the wording of everything but the lost and unaffected sentences is KMLib's.
