# Klark Morrigan's Utilities

## Index

- [Purpose](#purpose)
- [Features](#features)
- [Library Policy](#library-policy)
- [Build And Release](#build-and-release)
- [Documentation Status](#documentation-status)

## Purpose

Klark Morrigan's Utilities is a small Starsector utility mod for making direct,
explicit campaign edits from in-game screens.

## Features

### 1. Planetary Condition Editor

Problem doc:
[problem.md](docs/dev/implementation/001-add-planetary-condition/problem.md)

Plan doc:
[plan.md](docs/dev/implementation/001-add-planetary-condition/plan.md)

The proposed first feature adds a `Planetary Conditions` button while a colony
market is open. The button opens every planetary market condition as a clickable
entry with a tooltip. Entries already on the planet use the player's normal UI
color; entries not present are darkened. Clicking an entry adds that condition
to the market without removing mutually exclusive or same-group conditions. At
this stage, any inspected colony can be edited regardless of ownership.

## Library Policy

KMU should use helper libraries when they make UI work safer, smaller, or easier
to maintain. Starsector UI code often depends on reflection and custom panels,
so libraries such as LunaLib, LazyLib, or MagicLib are acceptable dependencies
when they clearly reduce implementation risk.

## Build And Release

KMU is developed in-place under Starsector's `mods/KMU` folder, but source,
tests, and generated release output should stay separate. Production Java source
belongs in `src/main/java`, tests belong in `src/test/java`, and the game should
load compiled code from `jars/KMU.jar`.

Local build commands:

```powershell
.\gradlew.bat -PstarsectorRoot=C:\a_Games\Starsector test
.\gradlew.bat -PstarsectorRoot=C:\a_Games\Starsector jar
```

The Gradle wrapper is the supported local and CI build path. It uses the local
Starsector install as a compile-only API source via `STARSECTOR_HOME` or
`-PstarsectorRoot=<path>`. Production code targets Java 17 to match the bundled
Starsector runtime used by the current game install.

Release shape doc:
[release.md](docs/dev/release.md)
