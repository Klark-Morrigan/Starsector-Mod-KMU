# Changelog

All notable changes to KMU are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Index

- [Unreleased](#unreleased)
- [0.1.2](#012---2026-09-16)
- [0.1.1](#011---2026-09-15)
- [0.1.0](#010---2026-09-14)

## [Unreleased]

### Added

- *Map - Politics - Visuals* setting **Decivilised systems - Should draw territory**, on by default. Switched off, a revealed decivilised world stops counting as somebody living in its system: that system is drawn as uninhabited rather than as territory, its owner is no longer offered in the layer's picker, and it takes no presence band and no colony size in the stats. The world itself is still found, still listed, and still named in the star system tooltip.

### Changed

- **Fast Rendering** version mismatches are now reported in-game. Where the map's cursor reading can no longer be taken from Fast Rendering, a notice names it and both versions once per session, and the sector map keeps drawing without responding to the cursor - no cell highlight, no star system tooltip. Previously the mismatch ended the map's render pass and named KM code in the error.

## [0.1.2] - 2026-09-16

### Added

- A general note for **settings**.
- **Settings** notes on big tabs listing their contents.

### Changed

- **Star system tooltips** only use term *contested* when **Nexerelin** is installed. Without it factions are *present*.

## [0.1.1] - 2026-09-15

- Updated for [KMLib 0.3.0](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/releases/tag/0.3.0) and [KMLib 0.3.1](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/releases/tag/0.3.1).
- The project is relicenced under under **LGPL-3.0-only**.

### Fixed

- **Hatched fill** lines clipping and overlapping when zoomed out at low resolutions. The hatch line width is now set as a percentage of the hatch spacing rather than in pixels, so the pattern keeps its proportions at every zoom. The *Map - Dev* **Hatch width** setting changes unit with it (0.5-100 pixels becomes 5-90 percent) and keeps whatever number you had set. - Reported at **USC** by **Vexlia**

## [0.1.0] - 2026-09-14

First tagged release, so there is no prior version to diff against.

### Added

- **Sector Map Layers** feature:
  - **Political Map**:
    - **Factions** view.
    - **Alliances** view.
    - **Claims** view.
  - System and market visibility rules.
  - Faction presence ribbons.
  - Map layer toggle injected into the map filter bar.
  - Collapsible sidebar on the map screen and intel screen.
  - Star system tooltips.
  - **Fast Rendering** compatibity.
  - **Nexerelin** compatibility.
  - **Random Assortment of Things** compatibility.
  - **LunaLib**-enabled settings.
  - *kmu_profiling* console command.
