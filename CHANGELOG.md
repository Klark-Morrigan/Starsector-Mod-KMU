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

- *Map - Politics - Visuals* setting **Decivilised systems - Should draw territory**, on by default. Switched off, a revealed decivilised world stops counting as somebody living in its system: that system is drawn as uninhabited rather than as territory, its owner is no longer offered in the layer's picker, and it takes no presence band and no colony size in the stats. The world itself is still found, still listed, and still named in the star system tooltip. - Requested by **NoticeMeSenpai** [at **USC**](https://discord.com/channels/187635036525166592/1549091275167240272/1549750490617741395).

### Changed

- A **decivilised world** is now revealed by another faction's colony in the same system at every setting of *Map - Visibility* **Show decivilised worlds surveyed at least to**. That setting now governs your own survey alone: at *Seen* a visit to the system is enough, and at *Preliminary* or *Full* you must survey the world itself. Previously the two upper levels also refused a neighbouring colony's word, so a world plainly visible to everyone living beside it stayed off the map.
- **Show decivilised worlds surveyed at least to** now ships at *Full* rather than *Seen*. Unless you have set the field yourself, in which case your value is kept, a decivilised world is named only once you have surveyed it or somebody is already living in its system. Flying past no longer puts one on the map. With a neighbour's word travelling at every level the common case is still covered, and surveying a world standing alone is now worth doing.
- **Fast Rendering** version mismatches are now reported in-game instead of ending the game. KMU reads where your cursor is on the sector map out of Fast Rendering's internals, and those internals move between its releases. There are three ways that can break: the part KMU looks for is gone, it is called from the game and refuses, or it is called on Fast Rendering's own thread and refuses there. The second is the one that matters from Fast Rendering 0.8.9, which declares every entry point and refuses the ones it does not implement - so the mismatch is invisible until the moment the map is drawn, and then it took the game down from inside KMU's own code. All three are now caught. Where the cursor reading can no longer be taken, a notice names Fast Rendering and both versions once per session, and the sector map keeps drawing without responding directly to the cursor - no star system highlight and tooltip, though the Map Layers sidebar still reacts to it. Nothing else is affected and your save is untouched. Previously the mismatch ended the map's render pass and named KM code in the error, so a Fast Rendering mismatch looked like a KMU bug.

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

- **Hatched fill** lines clipping and overlapping when zoomed out at low resolutions. The hatch line width is now set as a percentage of the hatch spacing rather than in pixels, so the pattern keeps its proportions at every zoom. The *Map - Dev* **Hatch width** setting changes unit with it (0.5-100 pixels becomes 5-90 percent) and keeps whatever number you had set. - Reported [at **USC**](https://discord.com/channels/187635036525166592/1549091275167240272/1549148937540210718) by **Vexlia**.

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
