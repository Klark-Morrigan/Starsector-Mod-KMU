# Changelog

All notable changes to KMU are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Index

- [Unreleased](#unreleased)
- [0.2.1](#021---2026-10-06)
- [0.2.0](#020---2026-10-05)
- [0.1.2](#012---2026-09-16)
- [0.1.1](#011---2026-09-15)
- [0.1.0](#010---2026-09-14)

## [Unreleased]

## [0.2.1] - 2026-10-06

### Fixed

- **JSON fields carrying object-holding arrays in `mod_info.json` are put at the bottom of the file**. There are third-party mod installers that naively read the first occurrence of the `id` field whether it's at the root or in records nested under `dependencies`. _Reported by **NH4CI** [at **Fossic**](https://www.fossic.org/forum.php?mod=redirect&goto=findpost&ptid=21517&pid=405627)._

### Dependency changes

- Updated for [KMLib 0.5.1](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/releases/tag/0.5.1).

## [0.2.0] - 2026-10-05

### Fixed

- **A crash under Fast Rendering `0.9.0` and later.** Thanks to **Genir**, [Fast Rendering now implements the missing OpenGL method](https://github.com/Halke1986/starsector-render/issues/11), the one the game itself answers when Fast Rendering is not installed. Under Fast Rendering, the map follows the cursor only from `v0.9.1rc1` on.
  - **On an earlier release the sector map stops reacting to the cursor instead of ending the game**: no star system highlight or tooltip, though the Map Layers sidebar still reacts. A notice names the release to update to.
- **A fault in a map layer no longer ends the game.** The failing layer stops drawing until you next load a save or switch *Features* **Enable map layers** off and on, and a notice says which layer stopped and asks you to report it. The map, the sidebar and your other layers keep drawing.
- **A fault while the political map repaints a conquered colony no longer ends the game.** The map repaints the colony at its next check a few seconds later.
- **A game update that changes the map screens no longer ends the game through the map-layers tick box or the arrange dialog.** The tick box stays off that screen, and the arrange dialog closes.
- **On the political map's *Claims* view, a colony founded or lost in a claimed system keeps the system in its claimant's colours**, rather than its holder's until the next full redraw.
- Switching on *Dev* **Reflection probe traces** repeats every warning about the game's screens, including the map-layers tick box's and the sector map's.

### Added

- **Simplified Chinese (简体中文).** A second zip, `KMU-<version>-zh-hans.zip`, carries the settings screen, the Map Layers sidebar, the hover boxes, the in-game notices and the mod list entry in Simplified Chinese. Install the [Chinese localisation](https://github.com/TruthOriginem/Starsector-Localization-CN) over `starsector-core` first: the game's own fonts hold no Chinese characters, so without it every one draws as `?`. Settings picked from a list keep their options in English, so your settings carry over between the two zips. The release notes and the zip's `CHANGELOG.md` are in Chinese too.
- *Map - Politics - Visuals* setting **Decivilised systems - Should draw territory**, on by default. Switched off, a revealed decivilised world no longer counts as anyone living in its system: the system draws as uninhabited, its owner leaves the layer's picker, and it adds no presence band or colony size to the stats. The world is still found, listed and named in the star system tooltip. - Requested by **NoticeMeSenpai** [at **USC**](https://discord.com/channels/187635036525166592/1549091275167240272/1549750490617741395).

### Changed

- A **decivilised world** is revealed by another faction's colony in its system at every level of *Map - Visibility* **Show decivilised worlds surveyed at least to**. That setting governs only your own survey: at *Seen* a visit to the system is enough, and at *Preliminary* or *Full* you must survey the world itself.
- **Show decivilised worlds surveyed at least to** ships at *Full* rather than *Seen*, unless you have set it yourself. A decivilised world is named once you survey it or someone lives in its system; flying past is not enough.
- **Each language ships as its own zip**, such as `KMU-<version>-en.zip`. The release notes say which is which, and update checkers keep working across the change.
- **When another mod or a game update breaks something KMU relies on, KMU loses only that feature and a notice says so.** It names the mod or the game with both versions, says what stops working, and suggests updating, downgrading or waiting. Each notice shows once per session.
  - **Game updates:** where an update changes the screens the map layers reach into, the notice names the one thing that stops working, such as the tick box going missing.
  - **LunaLib:** where it stops telling KMU about settings changes, a changed setting may not apply until you restart the game.
  - **Nexerelin alliances:** a release that changes how it keeps alliances no longer stops the game. The political map treats every faction as standing alone for the rest of the session.
  - **Nexerelin colony transfers:** a release that moves what KMU listens to no longer stops the game loading. A colony that changes hands takes a few seconds longer to show on the political map.

### For developers

<details>
<summary>Internals and the map layers framework</summary>

#### Fixed

- **Fast Rendering crash:** KMU reads the cursor through KMLib `0.5.0`, which reads the map's modelview with `glGetFloat` and reports an older Fast Rendering release once per session.
- **Map layer fault:** nothing under the sector map's drawing or its star system tooltip caught a failure. `DrawnLayerGuard` switches the failing layer off on that sector, and the fault is logged with its trace.
- **Conquered colony repaint:** Nexerelin tells its listeners of a transfer from inside an invasion, a rebellion or a transfer dialog with no catch of its own. The repaint's failure is caught and logged, and Nexerelin's work after telling its listeners still runs.
- **Map-layers tick box and arrange dialog:** both reach into the game's screen code, and a changed game build could fail there in a way neither caught. The failure is logged in `starsector.log`.
- **Claims view repaint:** the incremental repaint went by the system's holder rather than its claimant.
- **The Market Condition Manager's counts line draws its available and total counts in grey.** The game does not highlight a run that touches the word before it, and each separator started with a space, so the grey starts at the dash.
- **Reflection probe traces:** the tick box's and the sector map's warnings were left out of the re-arm.
- **A commented-out row in `data/config/kmu/installations.csv` is ignored**: a row whose entity type starts with `#` stays out of the table, the way the game's own tables comment a row out.

#### Public contracts changed (**breaking**)

None of these reach a player: every LunaLib field ID and every value saved in sector memory keeps its spelling, so settings and saves carry over. They reach a mod building a map layer on KMU's framework.

##### Tiers and packages

The framework has three tiers: `kmu.maplayers.base`, the substrate; `kmu.maplayers.ownermap`, new, the pipeline for any layer painting systems by an owner key, importing neither the political map nor `kmu.mods`; and `kmu.maplayers.politicalmap`, KMU's political map as one layer on it.

| Before | After |
| --- | --- |
| `kmu.maplayers.politicalmap.base` and its `holding`, `owners`, `owners.holders`, `picker`, `preferences`, `render`, `ribbon`, `sidebar` and `tooltip` | The same packages under `kmu.maplayers.ownermap` |
| The political map's own parts under `kmu.maplayers.politicalmap.base` | `kmu.maplayers.politicalmap`: `PoliticalMapLayer`, `PoliticalMapInstaller` and `PoliticalMapStanding` in the package, and its rules in `dominance`, `claims`, `views`, `holders`, `tooltip`, `refresh` and `render` |

##### Renames

Tier types and members are named for owners and groups, not for the political map or alliances.

| Before | After |
| --- | --- |
| `PoliticalMapView` and `PoliticalMapViewRegistry` | `OwnerPaintedView` and `MapLayerViewRegistry` |
| `PoliticalMapOverlayRenderer`, `PoliticalMapCache`, `PoliticalMapDrawables`, `PoliticalMapRebuildDecider`, `PoliticalMapBandLayout`, `PoliticalMapCategory`, `PoliticalMapBodyControls`, `PoliticalMapInhabitation` and `PoliticalMapHoverHighlightSource` | `OwnerMap` in place of `PoliticalMap` in each name |
| `PoliticalMapTerritories` | `OwnerMapClusters` |
| `TerritoryBuilder`, `TerritoryBuildInputs` and `FactionTerritoryBuilder` | `OwnerMapBuilder`, `OwnerMapBuildInputs` and `ClusterGroupBuilder` |
| `StandingPoliticalMap`, `StalePoliticsDisturbance` and `IncrementalPoliticsRefresh` | `StandingOwnerMap`, `StaleOwnerMapDisturbance` and `IncrementalOwnerRefresh` |
| `BlocStyleDecision`, `BlocStyleResolver` and `BlocStyling` | `Owner` in place of `Bloc` in each name |
| `DominantHolder` and `PoliticalMapPreviewHighlightRenderer` | `SystemOwner` and `SpotlightPreviewHighlightRenderer` |
| `KmuPoliticalMapDiagnosticsSettings`, `KmuPoliticalMapGeometrySettings`, `KmuPoliticalMapHighlightSettings` and `KmuPoliticalMapRibbonSettings` | `OwnerMap` in place of `PoliticalMap` in each name |
| `KmuPoliticalMapTerritorySettings` | `KmuOwnerMapStyleSettings` |
| `getPoliticalMap*` | `getOwnerMap*` |
| `getPoliticalMapAllianceMutedOpacityModifier` and `shouldDecivilisedSystemsDrawTerritory` | `getOwnerMapMutedOpacityModifier` and `shouldCountDecivilisedSystemsAsPopulated` |
| The nineteen `KmuStringKeys.POLITICAL_MAP_*` constants and their `strings.json` keys | `OWNER_MAP_*` |
| `HolderGrouping.allianceNameByBlocId`, `resolveAllianceName`, `isAlliance` and `hasAnyAlliance` | `groupNameByBlocId`, `resolveGroupName`, `isGroupedBloc` and `hasAnyGroupedBloc` |
| `ContentInputs.allianceRecedeAdjustment` | `viewRecedeAdjustment` |
| `SystemOccupancy.getHolderBySystemKey`, `readHolderOf`, `recordHolderOf` and `selectUnheldSystemKeysAmong` | `getOwnerBySystemKey`, `readOwnerOf`, `recordOwnerOf` and `selectUnownedSystemKeysAmong` |
| `SystemOccupancy.selectUnheldSystemKeysIn` | `SystemOwner.selectUnownedSystemKeysAmong` |
| `ClusterLabelStylingSnapshot.holderBySystemKey` | `ownerBySystemKey` |
| `DominancePass.over` and `MarketProximityTieBreak.forSystem` | `createOver` and `createForSystem` |

##### Views

A view answers who owns each system through one reading, sampled once, so its parts cannot disagree.

| Before | After |
| --- | --- |
| `OwnerPaintedView`'s `resolveGrouping()`, `resolveHolderProvider()`, `resolveRibbonPlanner(RibbonPlanInputs)`, `shouldUseIndependentStyle`, `resolveBlocStyleAdjustment`, `resolveName` and `computeAllianceContentRevision` | `resolveViewReading(SectorAPI)`, required: a `ViewReading` of the view, its `OwnerReading` and its `OwnerSource` |
| None | `resolveCategories()`, required: the categories cells divide into |
| None | `resolveViewRecedeAdjustment(ScreenMemoryScope)`, defaulting to receding nothing |
| `buildBlocPickerRead` with the grouping | Also takes the view's `OwnerReading` |
| A view painting holders | Implements `kmu.maplayers.ownermap.owners.holders.HolderPaintedView`, whose `resolveGrouping()`, `resolveContestGrouping()`, `resolveHolderProvider()`, `resolveSystemHolderResolveSource()`, `resolveRibbonPlanner(RibbonPlanInputs)` and `resolveOwnerReading(SectorAPI, HolderGrouping)` it assembles into `resolveViewReading`. `DominancePaintedView` and `ClaimsView` are holder views. |
| `ViewGrouping` | `ViewReading`, carrying the reading and the owner source |

##### Owner sources

The tier reads no colony: a layer's `OwnerSource` does, over the `SectorWalk` the tier hands it.

| Before | After |
| --- | --- |
| None | `kmu.maplayers.ownermap.owners.OwnerSource`: `resolveOwners`, `openSystemResolve` and `resolveRibbonPlanner`. `SectorWalk.readReadingOpenedBy` keeps one reading per source per walk. |
| `ResolvedHolding` | `ResolvedOwners`, in `owners`: the owners, the hatched and unfilled systems, the inhabited systems and the spotlit owner's presence |
| None | `HolderOwnerSource`, the holder layers' source, over one `HolderPass` per walk |
| `SystemHolderResolveSource.openResolveOver` | Takes the batch's `HolderPass`, and `SystemHolderResolve` answers only `resolveHolderIn` |
| `ClaimsView` re-deriving a marked system by its holder | `ClaimSystemHolderResolve`, by its claimant, through `SectorClaims.resolveClaimingHolderIn` |
| `OwnerMapBuilder.resolveHolding` | `resolveOwners(OwnerSource, SectorWalk, ContentInputs)`, and `buildClusters` takes the `ViewReading` and the `ResolvedOwners` |
| `OwnerMapCache` with a diagnostics provider and a per-system resolve source | A `CellSeedRule`, and `OwnerMapLayerRenderer.createForLiveScreen` follows |
| `IncrementalOwnerRefresh.applyStaleOwnerUpdates` with a resolve source | The batch's `SectorWalk` |
| `CellRibbonsBaker.createForPass` and `CellRibbonSource.createForPass` | Take a `SectorWalk` |
| `DebugBorderTracingBuilder.buildDebugDrawables` | Takes the resolved owners and the categories |
| `ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector` | `rebuildDiagnosticClusterAnchors`, styled by `ClusterLabelStylingSnapshot.resolveForTracing` |
| None | `SpotlitBlocs.isBlocPresentIn` |
| None | `kmu.maplayers.base.geometry.CellSeedRule`, with `SEED_DRAWN_SYSTEMS`. `CellGeometryCache.updateFromSector` and `PartitionSites.collectSitesFrom` take one, and `DrawnSystemPositions.collectLivePositions` gains an overload taking a membership rule. |

##### Owner reading and styling

The tier asks a layer about its owners rather than reading factions.

| Before | After |
| --- | --- |
| None | `kmu.maplayers.ownermap.owners.OwnerReading`: each owner's shades, name, crest, recede and category. `HolderOwnerReading` is the political map's. |
| None | `kmu.maplayers.ownermap.render.style.OwnerCategories`: which categories exist and how each is styled. `HolderCategories` declares the four `OwnerMapCategory` values. |
| KMLib's `FactionPalette` in `MapPalettes`, `MapStyling`, `ResolvedBlocPaint`, `BlocPaletteReader`, `BlocPresence` and the label styling | `OwnerPalette` |
| `SystemOwner.factionId`, its two colour components and `mapFactionIdBySystemKey` | `ownerId`, one `palette` and `mapOwnerIdBySystemKey`. `resolvePalette` is gone. |
| `SystemOwner.resolveForBloc` | `SectorBlocPalettes.resolveOwnerOf` |
| `ClusterLabelStylingSnapshot` carrying a `ViewGrouping` | Carries the categories and the reading |
| `OwnerStyleDecision` saying whether an owner takes the independent style | The category the owner draws in. `OwnerStyleResolver.resolveBlocStyleDecision` and `OwnerStyling.resolveFrom` take the reading and the categories. |
| `RenderStyleReader.readRenderStyle` | Takes the layer's categories and the sampled preferences |
| `BlocNameStyles.readFromLunaSettings` | Gone: `BlocNameStyles` is a name style per category |
| `FactionlessStyleResolver.resolveCategoryOf` | `isSettledSystem` |
| `ClusterAnchorsBuilder.rebuildClusterAnchors` with a sector | No sector |
| None | `OwnerMapBuildInputs.wasBuilt()`: false for the placeholder a failed first build stands behind |

##### Frame sequence

Every painting layer runs one frame sequence, and the framework owns it.

| Before | After |
| --- | --- |
| `PoliticalMapLayerRenderer` | `kmu.maplayers.base.render.SequencedMapLayerRenderer`, over a layer's `MapLayerFrameParts`: a stand-down read, a `MapFrameCache`, a `MapFrameCompositor`, its `MapLayerHoverGates` and its box |
| `OwnerMapLayerRenderer.createForLiveScreen` | Composes the owner map's parts into one renderer |
| `OwnerMapCache.refresh` | `refreshDrawLists`, and `resolveHoverTargets` is new |
| `MapLayerRegistry.resolveDrawnMapRenderer` | `DrawnLayerGuard.resolveGuardIn(machinery)`, in `kmu.maplayers.base.render`, which switches off a layer that throws |

##### Layer state and preferences

A layer owns its state, so two layers cannot share it by accident.

| Before | After |
| --- | --- |
| `MapLayerViewRegistry`'s static members and `getActiveView()` | An instance over the layer's save key, views, default view and host tab, answering `resolveActiveViewOn(ScreenLayerPicks)` |
| Per-sector pieces a layer holds | `SectorMapMachinery.resolveLayerMachinery(layerId, type, make)` |
| `SelectableBlocCache.resolveBlocCacheIn` | Takes the layer ID |
| `FilterSelectionHeal.healStaleSelectionAgainstActiveView` | Takes the sector and the registry. `healStaleSelectionAgainstLiveSector(registry)` serves a caller with no sector. |
| `OwnerMapBodyControls.buildViewSelector` | Takes the sector its body was built for |
| `PoliticalMapLayer` as a singleton | Constructed with its views |
| `NameFormatPreference`, `UninhabitedOutlinePreference` and `RecedePreferences` as statics, with `RecedePreferences.FILTER` and `ALLIANCE_NON_ALLIED` | Instances over keys a layer names, handed over as `OwnerMapBodyPreferences` to `OwnerMapLayerRenderer.createForLiveScreen`, `OwnerMapCache`, `OwnerMapRebuildDecider` and `ContentInputs.sampleForView` |
| `FactionNameFormatChoice.fromKeyOrDefault` | KMLib's `PersistedChoices.fromKey` over a `PersistedChoice` |
| `MapLayerSectorWatcher` shared between layers | Abstract: each layer installs its own subclass, since the engine removes transient scripts by exact class |

##### Hover, transfers and the political map

Political map pieces that moved onto the framework, merged, or answer a richer question.

| Before | After |
| --- | --- |
| `PoliticalMapHoverGates` | `kmu.maplayers.base.hover.MapLayerHoverGates` per layer, and `SharedOwnerMapHoverGates` for the owner map's switches. `MapLayerHoverGates.isCursorReadNeeded()` is a default method. |
| `PoliticalMapMarketTransferListener` implementing Nexerelin's `InvasionListener` | Implements KMU's own `kmu.starsector.listeners.MarketTransferListener`, told of every colony Nexerelin transfers on its sector |
| `HolderPass.openClaimReaderThrough` | The political map's `PassClaimReaders.openClaimReaderOver` |
| `ColonyQualifierFacts.isHoldingTheClaim` | `leadingFinding`: a worded finding stated ahead of the rest, or null. `SystemColonyReading.readQualifierFacts` assembles one. |
| `FactionTooltipLine.buildCountedFactionLine` | Takes whether the faction was weighed |
| `SystemStandingsTooltip` and `SystemClaimContestTooltip` | Merged into `SystemDominationTooltip` and `SystemClaimTooltip` |
| `LiveVisibilityClaimBreakdownReader` | Gone: the claim box opens its reader over the hover's own sector |
| `DominancePass.readFromLunaSettings` | Gone |
| `FilteredPolitics.resolveFilteredHolder` | Returns a `HolderResolution` |
| `CellTooltipQualifier`'s canonical constructor | Takes a `findingColour` between `findingText` and `trailingWordText`, set with `drawsFindingIn(Color)`. Left unset, the finding reads in the box's gold. |

</details>

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
