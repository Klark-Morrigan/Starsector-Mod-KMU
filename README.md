# Klark Morrigan's Utilities

[Install with **TriOS**](https://trilink.wispborne.com/open.html?mod=%7B%22url%22%3A%22https%3A%2F%2Fgithub.com%2FKlark-Morrigan%2FStarsector-Mod-KMU%2Freleases%2Flatest%2Fdownload%2Fkmu.version%22%2C%22id%22%3A%22kmu%22%7D&dep=%7B%22url%22%3A%22https%3A%2F%2Fraw.githubusercontent.com%2FLazyWizard%2Flazylib%2Fmaster%2Fmod%2Flazylib.version%22%2C%22id%22%3A%22lw_lazylib%22%7D&dep=%7B%22url%22%3A%22https%3A%2F%2Fraw.githubusercontent.com%2FLukas22041%2FLunaLib%2Fmain%2FLunaLib.version%22%2C%22id%22%3A%22lunalib%22%7D&dep=%7B%22url%22%3A%22https%3A%2F%2Fgithub.com%2FKlark-Morrigan%2FStarsector-Mod-KMLib%2Freleases%2Flatest%2Fdownload%2Fkmlib.version%22%2C%22id%22%3A%22kmlib%22%7D).

## Index

- [Dependencies](#dependencies)
- [Features](#features)
  - [Map layers](#map-layers)
    - [Political map](#political-map)
    - [Spoiler protection](#spoiler-protection)
  - [Console commands](#console-commands)
  - [Compatibility](#compatibility)
  - [Diagnostic](#diagnostics)
- [For developers](#for-developers)
  - [Versioning](#versioning)
  - [Build And Release](#build-and-release)
  - [Local linting](#local-linting)
  - [Caching](#caching)
  - [Documentation](#documentation)
- [Thanks](#thanks)
- [Licence](#licence)

## Dependencies

| Mod | Required | Notes |
| ----- | ---------- | ------- |
| [Klark Morrigan's Library (KMLib)](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/releases/latest) | Required | Shared library; version pinned in `mod_info.json` |
| [LunaLib](https://fractalsoftworks.com/forum/index.php?topic=25658) | **Required** | Settings framework backing KMU's configuration tabs |
| [LazyLib](https://fractalsoftworks.com/forum/index.php?topic=5444) | **Required** | Exposes game fonts for map labels |
| [Console Commands](https://fractalsoftworks.com/forum/index.php?topic=4106) | *Optional* | Enables `kmu_` commands |
| [Nexerelin](https://fractalsoftworks.com/forum/index.php?topic=9175) | *Optional* | Adds the **Alliances** view to the **Political Map** |
| [Random Assortment of Things](https://fractalsoftworks.com/forum/index.php?topic=26260) | *Optional* | The **compatibility mode** is on the `Map - Compatibility` tab |

## Features

This mod comes with a suite of **LunaLib** settings that allow you to tweak,
visuals,
sound volume,
keybinds,
underlying mechanics.

### Map layers

**Sector Map Layers**
is an overlay for the map screen **(Tab, Q)**,
the intel screen **(E, 1)**,
the minimap in the bottom right corner of the screen (replaces the vanilla radar) if you have **Random Assortment of Things** installed.

Everything is calculated and drawn off of game data,
and it gets updated as the state of the sector shanges.
Map layers don't alter any of the data being used to draw it,
making map layers **read-only** by design.

The map layers feature **turned on by default**.
After loading a save or starting a new game,
when you open the map screen or the intel screen,
you'll see a new `Map layers` button among map filter buttons.
Clicking that button or pressing **(M)** enables the map layer overlay and its sidebar.

The main control surface of map layers is the collapsible **sidebar** that lists all available layers
and related knobs and toggles.

See [more on map layers](src/main/java/kmu/maplayers/README.md).

#### Political map

- Focuses on **populated colonies** and their affiliation.
  *Unclaimed* **unpopulated systems** are drawn very faintly just to show where systems touch.
  Outlines of unpopulated systems can be toggled on and off independently from **populated systems**,
  which are *always drawn even when there's no organised faction in the system*
  (e.g. all colonies in a system are **decivilised**).
- *Any given system can be painted by only a single faction*,
  so each system draws a **presence ribbon** hugging its border that represents each populated colony with the color of the faction holding them.
  Ribbons are shortened to represent **solidified control** when only a single faction or alliance is present is a system,
  while that presence is in no violation of **unconditional claims**.
- **Expandable system tooltips**
  provide detailed information on how each market affects the balance of power.
  Tooltips list **Neutral** (unowned) markets,
  as well as markets producing no domination score or aren't used in claim calculations.
- Factions holding any population centers are listed in a **sortable filter**.
  Selecting any of them spotlights their presence across the sector.
  In domination views (**Factions** and **Alliances**)
  **solid fill** represents domination,
  **hatched fill** represents presence in a system dominated by somebody else,
  **no fill** represents claims on unpopulated systems.
  On the **Claims** view all systems are painted with solid fill.

The **Political Map** comes with 3 views:

- **Factions** -
  paints faction territory based on a custom **domination algorithm** (highly customisable in settings)
  that weighs markets sizes,
  stability,
  whether a market is hidden,
  presence of an orbital station (or if a station is militarised),
  and the number of patrol fleets generated (by their sizes).
  A faction can dominate a system claimed by another faction -
  the claim is listed on the tooltip but otherwise isn't factored in.
- **Alliances**
  (only visible with **Nexerelin** installed) -
  same as **Factions** but allied factions stand as a single political entity with their holdings combined.
- **Claims** -
  paints faction territory based on the vanilla **system claim** mechanic -
  dynamic or unconditional.
  **Unconditional claims** are set via sector memory and are the highest authority.
  **Unless a claim is unconditional,**
  **it is resolved dynamically** -
  the single biggest market (that participates in the economy) wins,
  boosted by the presence of same-faction markets (of any kind) and any military industry constructed.
  *The player faction and non-territorial factions cannot lay claims dynamically.*
  If you filter by a non-territorial faction,
  unclaimed systems with their presence will be spotlit.

See [more on the political map](src/main/java/kmu/maplayers/README.md).

#### Spoiler protection

To avoid spoilers,
map layers operate on a vanilla-derived **visibility** system:

- **Hidden systems**
  (with no hyperspace anchor generated off of a star, nebula, or a black hole)
  are not revealed unless there's a visible population center present or it holds an active gate.
- Entities marked as **discoverable**
  (hidden on the system map until in range of player fleet sensors)
  are not revealed.
  They are also listed on **Claims** tooltips because vanilla uses them in claim calculations,
  but are redacted.
- Markets marked as **hidden** are not revealed if there are no visible colonies held by other non-allied factions,
  or until the player visits their system.
  They are also listed on **Claims** tooltips because vanilla uses them in claim calculations,
  but are redacted.
- **Decivilised markets** are not revealed unless they start so,
  or until the player visits their system.
- **Unowned markets** with the **Abandoned Station** condition follow visibility rules of **hidden markets**.

### Console commands

| Command | Syntax | What it does |
| --- | --- | --- |
| `kmu_profiling` | `[tree\|flat\|walks] [namespace=<prefix>] [top=<count>] [perframe] [reset]` | Writes full or scoped performance measurements to game logs on demand. Calls exceeding the budget are logged automatically without this command. **Requires profiling to be enabled in settings.** |

### Compatibility

Map layers are injected into the map widget as **map-only terrain**.
And to be visible in the **Starscape** mode,
the *starscape terrain variant* is tagged as a **slipstream**.
Custom terrain is the only thing that has to be serialised into saves as Java class references,
and that's why a clean uninstall is required for disabling the mod.

The game reuses the map widget,
and so do many mods.
Those custom placements of the widget get map layers for free,
but they should come with no mouseover detection and no controls
(borrowing selections from the map screen as the main instance)
without a compatibility patch.

Additionally,
the mod has been made specifically compatible with:

- [Fast Rendering](https://fractalsoftworks.com/forum/index.php?topic=33870.0)
  and its **optimisations**.
- [Nexerelin](https://fractalsoftworks.com/forum/index.php?topic=9175.0)
  to support its **alliance** mechanics.
- [Random Assortment of Things](https://fractalsoftworks.com/forum/index.php?topic=26260.0)
  and its **minimap replacement**.
  The minimap draws the map widget via a tooltip in the corner of the screen.
  This mod comes with a RAT **compatibility mode** enabled by default that *scopes mouse detection to the widget*.
  Layer selection is inherited from the map screen.
  As a bonus,
  the *map widget is made invisible when it's docked off-screen*
  (docking is RAT's way of hiding the map widget when the player docks at markets or opens any UI screens) -
  that disables any related rendering and should provide a marginal performance improvement
  (more so when in hyperspace)
  in the menus that don't show the sector map.

### Diagnostics

- The **DEBUG** logging level in both **KMLib** and **KMU** dev settings.
  To write all internal mod logs.
- A **reflection** logs toggle in **KMU** dev settings.
  To trace issues interacting with vanilla UI.
- **Profiling** toggle and level in **KMU** map dev settings.
  To trace where performance drops occur.

## For developers

### Versioning

KMU follows the consumer-mod rules in
[KMLib's versioning policy](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/docs/dev/versioning.md).
In short:
MAJOR for save-breaking changes,
MINOR for save-safe new features,
PATCH for fixes and tweaks.
The same policy defines how KMU pins the KMLib dependency in both `mod_info.json` and `.github/workflows/*.yml`.

### Build And Release

KMU is built with Gradle using the local Starsector install as a compile-only API source.
Production code targets Java 17 to match the bundled runtime.

Local build commands:

```powershell
.\gradlew.bat -PstarsectorRoot=C:\a_Games\Starsector test
.\gradlew.bat -PstarsectorRoot=C:\a_Games\Starsector jar
```

The Gradle wrapper is the supported local and CI build path.
Pass the install root via `STARSECTOR_HOME` or `-PstarsectorRoot=<path>`.

Releases are cut by KMLib's reusable pipeline,
which [release.yml](.github/workflows/release.yml) calls on every push to `master` with no inputs -
the pipeline reads `mod_info.json` for everything it needs.
A push whose version matches the latest git tag stops after one cheap job;
a version bump re-runs the PR gates on that commit,
packages `KMU-<version>.zip`,
pushes the tag,
and publishes a GitHub release
whose body is this repo's [CHANGELOG.md](CHANGELOG.md) section for that version -
so a release with no changelog section fails rather than shipping empty notes.

Two committed files feed the update-check side of that release.
[kmu.version.template](kmu.version.template) is the VersionChecker template:
a complete `.version` file stating the shape KMU publishes,
whose release-varying values are written as tokens for KMLib's `fill-version-file-template` action to substitute from `mod_info.json` -
so no version number is restated by hand outside that file.
The filled result rides inside the zip and is attached to the release in its own right,
that copy being the only form an update checker can poll.
[data/config/version/version_files.csv](data/config/version/version_files.csv)
is what points VersionChecker at the filled `kmu.version` in an install.

`gradlew jar` fills the same template into `kmu.version` at the repo root,
so a checkout symlinked into `mods/` as a dev install reports the version a published ZIP would rather than the tokens.
That generated file is never committed.
The task doing it is `writeVersionFile`,
which KMLib's shared Starsector conventions register for any mod that commits a template -
KMU states no build wiring of its own for it.

### Local linting

Two delegating CI workflows lint the repo's non-Gradle surface on every pull request:
[ci-yaml.yml](.github/workflows/ci-yaml.yml) calls Common-Automation's reusable `ci-yaml.yml`
(actionlint, action-validator, yamllint, ansible-lint) and [ci-bash.yml](.github/workflows/ci-bash.yml) calls its reusable `ci-bash.yml` (shellcheck, check-sh-executable, bats).
Each step auto-skips when its surface is absent.
The Gradle build and tests are NOT part of these workflows -
they run through Gradle
(see [Build And Release](#build-and-release));
these gates cover only YAML / Actions / Bash.

KMU's workflows run on its self-hosted runner,
labelled `kmu-runner`
(provisioned with `STARSECTOR_HOME` + JDK).
[.github/actionlint.yaml](.github/actionlint.yaml)
declares that label so actionlint stops flagging `runs-on` as an unknown runner.

Three sibling shims reproduce that CI surface locally through Git Bash + Docker,
each delegating to Common-Automation's orchestrator:

- [scripts/run-ci-yaml-and-bash.sh](scripts/run-ci-yaml-and-bash.sh)
  (with the [run-ci-yaml-and-bash.bat](scripts/run-ci-yaml-and-bash.bat) launcher for `cmd` / PowerShell) is the MAIN entry -
  it runs BOTH the lint suite AND the bats tests in one go,
  the full local equivalent of ci-yaml.yml + ci-bash.yml.
  This is what most contributors run.
- [scripts/run-lint-yaml-and-bash.sh](scripts/run-lint-yaml-and-bash.sh)
  (with its [.bat](scripts/run-lint-yaml-and-bash.bat) launcher) runs the lint half only
  (shellcheck, actionlint, action-validator, yamllint, ansible-lint);
  no bats.
- [scripts/run-tests-bash.sh](scripts/run-tests-bash.sh)
  (with its [.bat](scripts/run-tests-bash.bat) launcher) runs the bats tests only.

All three are thin shims over Common-Automation's engine,
so they require a Common-Automation checkout as a SIBLING directory (`..\Common-Automation`).
The Gradle build and tests stay separate -
they live in Gradle
(see [Build And Release](#build-and-release));
these shims cover only the YAML / Actions / Bash surface.

[scripts/fix-permissions.sh](scripts/fix-permissions.sh)
(and its [.bat](scripts/fix-permissions.bat)) re-stages the executable bit on tracked `*.sh` files,
which Windows checkouts drop;
run it after adding a shell script so the `check-sh-executable` gate stays green.
[.gitattributes](.gitattributes) pins line endings surgically -
`*.sh` and `gradlew` to LF,
`*.bat` and `gradlew.bat` to CRLF -
and leaves binary / data assets to git's own detection.

### Caching

The political map derives an expensive drawing from live campaign state and repaints it every frame,
so nearly everything it shows is held between frames and rebuilt as narrowly as the change allows.
[docs/dev/caching.md](docs/dev/caching.md)
is the single description of that model:
what notices a change,
the revision counters it announces itself through,
each cache and what it keys on,
and the four rebuild paths a frame can take.
Read it before adding a live input to the overlay -
an input no counter reports is the one way to leave the map silently stale.

### Documentation

Development docs live under [docs/dev](docs/dev).
Package documentation lives beside the Java packages it describes under [src/main/java/kmu](src/main/java/kmu):
a README per package that owns behaviour worth explaining,
each with Mermaid diagrams inline where a picture carries more than prose.
Start from [map layers](src/main/java/kmu/maplayers/README.md).

Diagrams are Mermaid,
written inline in the README that needs them.
There is no separate diagram source format and no standalone diagram files to keep in sync.

## Thanks

- To the **Fractal Softworks** team
  for bringing us Starsector and continuously improving it.
- To the **modding community**
  for producing awesome and inspiring mods that bring me back to Starsector every time.
- To developers and maintainers of
  **LazyLib**,
  **LunaLib**,
  **MagicLib**,
  **Console Commands**
  for providing features this mod relies on.
- To awesome folks over at **r/Starsector** and in **Discord** communities
  for feedback and support.

## Licence

KMU is licensed under the
[GNU Lesser General Public License version 3](LICENSE),
with the GPL it incorporates by reference at [LICENSE.GPL](LICENSE.GPL).

LGPL rather than a permissive licence for what this mod hosts:
a map-layer framework other mods register into.
Building a layer on it costs you nothing -
a mod that compiles against `kmu.maplayers` keeps whatever terms it likes,
conveys none of KMU itself,
and inherits no obligations from this licence.
What the licence does ask is that a *fork of KMU* stays open under the same terms.

The images under [`promo/`](promo/) are documentation rather than shipped content,
and are not covered by the above.
