# Settings (`kmu.settings`)

KMU's LunaLib settings as the code reads them:
one accessor class per group of knobs,
the choice enums behind the Radio rows,
and `KmuLunaSettings`,
which binds them at load and holds the reads they all go through.

The rows themselves are declared in the settings table,
one per locale at `localisation/<locale>/LunaSettings.csv`,
and written to the `data/config/LunaSettings.csv` LunaLib reads.

Part of Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../README.md) for project context.

## Index

- [Split by reader](#split-by-reader)
- [Split again by section](#split-again-by-section)
- [What a knob class holds](#what-a-knob-class-holds)
- [Where a knob's prose lives](#where-a-knobs-prose-lives)
- [Field IDs](#field-ids)
- [What holds the table and the code together](#what-holds-the-table-and-the-code-together)
- [What is not here](#what-is-not-here)

## Split by reader

The knob classes are split first by which package reads them,
not by the tab a player finds them on:

| Reader | Classes |
| --- | --- |
| the composition root | `KmuFeatureSettings`, `KmuProfilingSettings` |
| the map-layer framework | the `KmuMap*Settings` classes |
| the owner-painted tier | the `KmuOwnerMap*Settings` classes |
| the political map | the `KmuPoliticalMap*Settings` classes |
| the Market Condition Manager | `KmuMarketConditionSettings` |
| the mod's own logging | `KmuLoggingSettings` |

Splitting on the reader keeps a tier's knobs out of reach of the tiers below it:
a class no framework code imports cannot leak a feature's vocabulary into it,
which a split by tab could not promise.

`Map - Dev` is where the two part company.
It carries the tuning of geometry every layer shares,
but most of that tuning is read by the owner-painted tier that resolves it,
so a class named for that tab would be imported by both halves.
`KmuOwnerMapGeometrySettings` is filed by who reads its knobs,
not by whose geometry they move;
`KmuMapRefreshSettings` is the framework's own row on that tab.

The composition root's two classes are read where the mod's start-up wiring is composed,
because what they decide is whether a feature runs,
or whether it is measured,
and neither is a question the feature itself can be asked.

## Split again by section

Within one tier every knob has the same reader,
so the second split is by section of the settings screen.
What a class named for a section buys is that nothing imports more of the settings than the part it reads.
The owner-painted tier's set is shared by every layer that paints by owner:

- `KmuOwnerMapStyleSettings` - how each kind of cluster paints;
- `KmuOwnerMapRibbonSettings` - the presence bands;
- `KmuOwnerMapHighlightSettings` - what lights up under a hover;
- `KmuOwnerMapGeometrySettings` - the shapes the map is built from;
- `KmuOwnerMapDiagnosticsSettings` - the dev overlays.

The political map's own set is `KmuPoliticalMapDominanceSettings` for the verdicts rather than the paint,
and `KmuPoliticalMapDrawOrderSettings` for which side of the nebulae each sub-layer paints on.

`KmuMapKeybindSettings` is the framework's one class named for a tab,
because the tab is what it holds:
every key the overlay answers to and nothing else.
It buys no isolation,
the framework reading both halves;
what it buys is that a key added to that tab has one obvious home.

## What a knob class holds

Field IDs,
fallbacks and accessors,
and nothing else.
The mod ID,
the settings revision and the typed reads stay in `KmuLunaSettings`,
because they are mod-wide.
A fallback mirroring a CSV default sits beside the accessor that answers with it,
not beside the reader that fetches it.

The keybind section mirrors no default at all:
a key is decided in the table alone,
and a read that cannot reach a row answers unbound.
`KmuMapKeybindSettings` says why.

## Where a knob's prose lives

What each knob does for the player is stated once,
in the settings table's description column,
which is the text the settings screen shows.
The prose in the classes answers only what that column cannot:
why a default is the number it is,
and what a caller has to know to use the value.

## Field IDs

Every field ID spells the tab path a player finds the row under,
segment by segment:
`kmu_<tab>_<section>_<group>_<knob>`.
So a knob's stored key says where it is set,
rather than which feature first wanted it.

- A noun two or more knobs share is a segment of its own,
  and the leaf never repeats it:
  `cellGeometry_radius`,
  not `cellGeometry_cellRadius`.
- A switch names itself as a predicate (`is`, `are`, `should`),
  so its ID reads as the question the row answers.
- A group's own on/off is `<group>_isEnabled`.

A LunaLib field ID is the key its value is stored under,
so IDs are frozen once shipped.
Renaming one resets that setting for every existing player,
exactly as a persisted class or memory key cannot be renamed.
LunaLib only ever adds -
it seeds a default for every row the table declares and prunes nothing -
so a value left behind by a renamed or withdrawn row stays in the player's settings file unread,
and would be handed to any later field that reused the ID.
A Radio row's option labels are stored the same way and are frozen for the same reason.

## What holds the table and the code together

`LunaSettingsCsvIntegrationTests` reads the shipped table and the shipped sources
and holds each against the other:
option labels against their choice enums,
every fallback against its row's default,
every field ID in both directions,
and every row against the tabs the screen is laid out into.
A drift in any of them fails silently in play,
so the suite is what notices it.

## What is not here

- **KMLib's `kmlib.settings`** -
  the LunaLib reader,
  the choice type the enums implement,
  and the log-level binding.
- **The settings table** -
  `localisation/en/LunaSettings.csv` and its locale twins,
  which own every row,
  its default and its description.
