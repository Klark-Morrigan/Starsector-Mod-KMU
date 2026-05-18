# Klark Morrigan's Utilities

## Index

- [Purpose](#purpose)
- [Features](#features)
- [Dependencies](#dependencies)
- [Versioning](#versioning)
- [Build And Release](#build-and-release)
- [Documentation Status](#documentation-status)

## Purpose

Klark Morrigan's Utilities is a small Starsector utility mod for making direct,
explicit campaign edits from in-game screens.

## Features

### 1. Planetary Condition Picker (PCP)

Problem doc:
[problem.md](docs/dev/implementation/001-add-planetary-condition/problem.md)

Plan doc:
[plan.md](docs/dev/implementation/001-add-planetary-condition/plan.md)

The planned first feature is a `Planetary Conditions` picker for an active
colony market. The picker opens every planetary market condition as a clickable
entry with a tooltip. Entries already on the planet use the player's normal UI
color; entries not present are darkened. Clicking an absent entry adds that
condition to the market without removing mutually exclusive or same-group
conditions. At this stage, any inspected colony can be edited regardless of
ownership.

When Console Commands is enabled, KMU registers `kmu_pcp_open` as an
optional developer entry point for the same picker. KMU console commands use the
`kmu_` prefix and verb-noun names.

## Dependencies

| Mod | Author | Required | Notes |
|-----|--------|----------|-------|
| [Console Commands](https://fractalsoftworks.com/forum/index.php?topic=4106) | LazyWizard | Optional | Enables `kmu_` developer commands (e.g. `kmu_pcp_open`) |

## Versioning

KMU follows the consumer-mod rules in
[KMLib's versioning policy](https://github.com/<owner>/KMLib/blob/main/docs/dev/versioning.md).
In short: MAJOR for save-breaking changes, MINOR for save-safe new features,
PATCH for fixes and tweaks. The same policy defines how KMU pins the KMLib
dependency in both `mod_info.json` and `.github/workflows/*.yml`.

## Build And Release

KMU is built with Gradle using the local Starsector install as a compile-only
API source. Production code targets Java 17 to match the bundled runtime.

Local build commands:

```powershell
.\gradlew.bat -PstarsectorRoot=C:\a_Games\Starsector test
.\gradlew.bat -PstarsectorRoot=C:\a_Games\Starsector jar
```

The Gradle wrapper is the supported local and CI build path. Pass the install
root via `STARSECTOR_HOME` or `-PstarsectorRoot=<path>`.

## Documentation Status

Development docs live under `docs/dev`. Package-level Mermaid diagrams live
beside the Java packages they describe under `src/main/java/kmu`, with shared
diagram conventions in `src/main/java/kmu/diagram-style.md`.
