# Changelog

All notable changes to KMU are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Index

- [Unreleased](#unreleased)
- [0.1.2](#012---2026-09-16)
- [0.1.1](#011---2026-09-15)
- [0.1.0](#010---2026-09-14)

## [Unreleased]

### Fixed

- **A crash under Fast Rendering `0.9.0` and later.** Thanks to **Genir**, [Fast Rendering now implements the missing OpenGL method](https://github.com/Halke1986/starsector-render/issues/11), the one the game itself answers when Fast Rendering is not installed. Under Fast Rendering, the map follows the cursor only from `v0.9.1rc1` on.
  - **On an earlier release the sector map stops reacting to the cursor instead of ending the game**: no star system highlight or tooltip, though the Map Layers sidebar still reacts. A notice names the release to update to.
- **A fault in a map layer no longer ends the game.** The failing layer stops drawing until you next load a save or switch *Features* **Enable map layers** off and on, and a notice says which layer stopped and asks you to report it. The map, the sidebar and your other layers keep drawing.
- **A fault while the political map repaints a conquered colony no longer ends the game.** The map repaints the colony at its next check a few seconds later.
- **A game update that changes the map screens no longer ends the game through the map-layers tick box or the arrange dialog.** The tick box stays off that screen, and the arrange dialog closes.
- **On the political map's *Claims* view, a colony founded or lost in a claimed system keeps the system in its claimant's colours**, rather than its holder's until the next full redraw.
- **The Market Condition Manager's counts line draws its available and total counts in grey**, as intended.
- Switching on *Dev* **Reflection probe traces** repeats every warning about the game's screens, including the map-layers tick box's and the sector map's.

### Added

- **Simplified Chinese (简体中文).** A second zip, `KMU-<version>-zh-hans.zip`, carries the settings screen, the Map Layers sidebar, the hover boxes, the in-game notices and the mod list entry in Simplified Chinese. Install the [Chinese localisation](https://github.com/TruthOriginem/Starsector-Localization-CN) over `starsector-core` first: the game's own fonts hold no Chinese characters, so without it every one draws as `?`. Settings picked from a list keep their options in English, so your settings carry over between the two zips. The release notes and the zip's `CHANGELOG.md` are in Chinese too.
- *Map - Politics - Visuals* setting **Decivilised systems - Should draw territory**, on by default. Switched off, a revealed decivilised world no longer counts as anyone living in its system: the system draws as uninhabited, its owner leaves the layer's picker, and it adds no presence band or colony size to the stats. The world is still found, listed and named in the star system tooltip. - Requested by **NoticeMeSenpai** [at **USC**](https://discord.com/channels/187635036525166592/1549091275167240272/1549750490617741395).
- **Map labels, hover boxes and the Map Layers sidebar draw in a font that holds their text.** Where a font cannot draw a name or a word - a language pack's fonts leaving some characters out - the text draws in a smaller cut of the same font or in the game's default font, rather than as question marks. With the Chinese localisation installed, the faction names on the map step down one cut. On an English install nothing changes.

### Changed

- **The mod list names the mod Klark Morrigan's Utilities (KMU).** Update checkers show the same name.
- **Each language ships as its own zip**, such as `KMU-<version>-en.zip`. The release notes say which is which, and update checkers keep working across the change.
- A **decivilised world** is revealed by another faction's colony in its system at every level of *Map - Visibility* **Show decivilised worlds surveyed at least to**. That setting governs only your own survey: at *Seen* a visit to the system is enough, and at *Preliminary* or *Full* you must survey the world itself.
- **Show decivilised worlds surveyed at least to** ships at *Full* rather than *Seen*, unless you have set it yourself. A decivilised world is named once you survey it or someone lives in its system; flying past is not enough.
- **When another mod or a game update breaks something KMU relies on, KMU loses only that feature and a notice says so.** It names the mod or the game with both versions, says what stops working, and suggests updating, downgrading or waiting. Each notice shows once per session.
  - **Nexerelin alliances:** a release that changes how it keeps alliances no longer stops the game. The political map treats every faction as standing alone for the rest of the session.
  - **LunaLib:** where it stops telling KMU about settings changes, a changed setting may not apply until you restart the game.
  - **Game updates:** where an update changes the screens the map layers reach into, the notice names the one thing that stops working, such as the tick box going missing.
- **A mod that changes what KMU binds to at start-up no longer stops the game loading.** Only the affected piece is lost, with a line in `starsector.log`. For Nexerelin, that is the political map's repaint when a colony changes hands mid-campaign.

### For developers

<details>
<summary>Internals and the map layers framework</summary>

#### Fixed

- **Fast Rendering crash:** KMU reads the cursor through KMLib `0.5.0`, which reads the map's modelview with `glGetFloat` and reports an older Fast Rendering release once per session.
- **Map layer fault:** nothing under the sector map's drawing or its star system tooltip caught a failure. `DrawnLayerGuard` switches the failing layer off on that sector, and the fault is logged with its trace.
- **Conquered colony repaint:** Nexerelin tells its listeners of a transfer from inside an invasion, a rebellion or a transfer dialog with no catch of its own. The repaint's failure is caught and logged, and Nexerelin's work after telling its listeners still runs.
- **Map-layers tick box and arrange dialog:** both reach into the game's screen code, and a changed game build could fail there in a way neither caught. The failure is logged in `starsector.log`.
- **Claims view repaint:** the incremental repaint went by the system's holder rather than its claimant.
- **Counts line:** the game does not highlight a run that touches the word before it, and each separator started with a space. The grey starts at the dash.
- **Reflection probe traces:** the tick box's and the sector map's warnings were left out of the re-arm.
- **A commented-out row in `data/config/kmu/installations.csv` is ignored**: a row whose entity type starts with `#` stays out of the table, the way the game's own tables comment a row out.

#### Changed

- **Start-up binding:** each piece of KMU's start-up wiring already ran behind its own boundary, which now also catches a link error - the form a moved or renamed binding arrives in. KMU's Nexerelin listener implements one of Nexerelin's own interfaces, so a release reshaping it ended every load.

</details>

### Public contracts changed (**breaking**)

None of these reach a player: every LunaLib field ID and every value saved in sector memory keeps its spelling, so settings and saves carry over untouched. They reach a mod building a map layer on KMU's framework.

- **The map layers framework has three tiers:**
  - `kmu.maplayers.base` - the substrate.
  - `kmu.maplayers.ownermap` - new: the pipeline any layer painting systems by an owner key builds on - a faction, a group of factions, or any other value the layer keys a system by. It may import neither the political map nor `kmu.mods`.
  - `kmu.maplayers.politicalmap` - KMU's political map, one layer on the tier.
- **Packages moved:**
  - Everything under `kmu.maplayers.politicalmap.base` is under `kmu.maplayers.ownermap`:
    - `holding`, `owners` and `owners.holders` - reading a sector, and the owners read off it.
    - `picker` and `preferences` - the bloc picker's model, and a layer's per-save body choices.
    - `render`, with `clusters`, `hover`, `labels`, `ribbon` and `style`.
    - `ribbon`, `sidebar` and `tooltip`.
  - Except the political map's own parts, which are in `kmu.maplayers.politicalmap`:
    - `PoliticalMapLayer`, `PoliticalMapInstaller` and `PoliticalMapStanding`, in the package itself.
    - Its rules, in `dominance` (with `standings` and `weighting`), `claims`, `views`, `holders`, `tooltip`, `refresh` and `render`.
- **Tier types renamed with the move:**
  - `PoliticalMapView` is `OwnerPaintedView`, and `PoliticalMapViewRegistry` is `MapLayerViewRegistry`.
  - These take `OwnerMap` for `PoliticalMap`:
    - `PoliticalMapOverlayRenderer`, `PoliticalMapCache`, `PoliticalMapDrawables` and `PoliticalMapRebuildDecider`.
    - `PoliticalMapBandLayout`, `PoliticalMapCategory`, `PoliticalMapBodyControls`, `PoliticalMapInhabitation` and `PoliticalMapHoverHighlightSource`.
  - The cluster build:
    - `PoliticalMapTerritories` is `OwnerMapClusters`.
    - `TerritoryBuilder` is `OwnerMapBuilder`, and `TerritoryBuildInputs` is `OwnerMapBuildInputs`.
    - `FactionTerritoryBuilder` is `ClusterGroupBuilder`.
  - The refresh:
    - `StandingPoliticalMap` is `StandingOwnerMap`, and `StalePoliticsDisturbance` is `StaleOwnerMapDisturbance`.
    - `IncrementalPoliticsRefresh` is `IncrementalOwnerRefresh`.
  - `BlocStyleDecision`, `BlocStyleResolver` and `BlocStyling` take `Owner` for `Bloc`.
  - `DominantHolder` is `SystemOwner`, and `PoliticalMapPreviewHighlightRenderer` is `SpotlightPreviewHighlightRenderer`.
- **`OwnerPaintedView` asks a view two things about its owners:**
  - `resolveViewReading(SectorAPI)` is new and required. It answers a `ViewReading` - the view, its `OwnerReading` and its `OwnerSource` - resolved together under one sampling of whatever the view reads live.
  - `resolveCategories()` is new and required: which categories cells divide into.
  - `resolveViewRecedeAdjustment(ScreenMemoryScope)` is new, defaulting to receding nothing.
  - `resolveGrouping()`, `resolveHolderProvider()`, `resolveRibbonPlanner(RibbonPlanInputs)`, `shouldUseIndependentStyle`, `resolveBlocStyleAdjustment`, `resolveName` and `computeAllianceContentRevision` are gone from the seam.
  - `buildBlocPickerRead` takes the view's `OwnerReading` beside the grouping.
- **A view painting holders implements `kmu.maplayers.ownermap.owners.holders.HolderPaintedView`:** it states `resolveGrouping()`, `resolveContestGrouping()`, `resolveHolderProvider()`, `resolveSystemHolderResolveSource()`, `resolveRibbonPlanner(RibbonPlanInputs)` and `resolveOwnerReading(SectorAPI, HolderGrouping)`, and the interface assembles `resolveViewReading` from them over one sampling of the grouping. `DominancePaintedView` and `ClaimsView` are holder views.
- **The tier reads no colony; a layer's source does:**
  - `kmu.maplayers.ownermap.owners.OwnerSource` resolves who owns each system for a rebuild (`resolveOwners`), opens a per-system `SystemOwnerResolve` for an incremental batch (`openSystemResolve`) and answers the band planner for a bake (`resolveRibbonPlanner`), each over the `SectorWalk` the tier hands it - the rebuild's one `SectorPassIndex` and the visibility rules the cells were cut under. `SectorWalk.readReadingOpenedBy` keeps whatever reading a source opens over the walk, one per source, so a source holds nothing between walks.
  - `ResolvedOwners`, in `owners`, is the source's whole-sector answer: the owners, the hatched and unfilled systems, the inhabited systems and the spotlit owner's presence. `ResolvedHolding` is gone.
  - `HolderOwnerSource` is the source of the layers painting holders. It opens one `HolderPass` per walk, kept by the walk, and answers every question off it through the layer's `HolderProvider` and `SystemHolderResolve`.
  - `SystemHolderResolveSource.openResolveOver` takes the batch's `HolderPass`, and `SystemHolderResolve` answers only `resolveHolderIn`.
  - `ClaimsView` re-derives a marked system through `ClaimSystemHolderResolve`, by its claimant: `SectorClaims.resolveClaimingHolderIn` answers one system by the rule `resolveClaimingHolderBySystemKey` applies to the whole sector.
  - `OwnerMapBuilder.resolveHolding` is `resolveOwners(OwnerSource, SectorWalk, ContentInputs)`, and `buildClusters` takes the `ViewReading` and the `ResolvedOwners` in place of a pass, a view and a holding.
  - `OwnerMapCache` takes a `CellSeedRule` in place of the diagnostics provider and the per-system resolve source, and `OwnerMapLayerRenderer.createForLiveScreen` follows. The diagnostic overlays read owners through the active view's own source.
  - `IncrementalOwnerRefresh.applyStaleOwnerUpdates` takes the batch's `SectorWalk` and no resolve source; the batch asks the source the standing build was resolved by.
  - `CellRibbonsBaker.createForPass` and `CellRibbonSource.createForPass` take a `SectorWalk`, and the bake asks the build's owner source for its planner.
  - `DebugBorderTracingBuilder.buildDebugDrawables` takes the resolved owners and the categories. `ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector` is `rebuildDiagnosticClusterAnchors`, taking the styling `ClusterLabelStylingSnapshot.resolveForTracing` resolves for the traced owners.
  - `SystemOccupancy` reads in owners: `getHolderBySystemKey`, `readHolderOf`, `recordHolderOf` and `selectUnheldSystemKeysAmong` are `getOwnerBySystemKey`, `readOwnerOf`, `recordOwnerOf` and `selectUnownedSystemKeysAmong`. `SystemOccupancy.selectUnheldSystemKeysIn` is `SystemOwner.selectUnownedSystemKeysAmong`, and `SpotlitBlocs.isBlocPresentIn` is new.
  - `ClusterLabelStylingSnapshot.holderBySystemKey` is `ownerBySystemKey`.
- **Which systems seed a cell is the layer's to state:** `kmu.maplayers.base.geometry.CellSeedRule` is new, with `SEED_DRAWN_SYSTEMS` the substrate's own rule. `CellGeometryCache.updateFromSector` and `PartitionSites.collectSitesFrom` take one, and `DrawnSystemPositions.collectLivePositions` gains an overload taking a membership rule.
- **The tier asks a layer about its owners:**
  - `kmu.maplayers.ownermap.owners.OwnerReading` answers each owner's shades, name, crest, recede and category, and the shades an unowned or receded cell is derived from, resolved once per rebuild. `HolderOwnerReading` is the political map's.
  - `kmu.maplayers.ownermap.render.style.OwnerCategories` declares which categories exist and how each is styled, each owned category's name style, the full-strength category and the category an unowned cell falls to. `HolderCategories` declares the four `OwnerMapCategory` values.
  - `OwnerPalette` is the tier's pair of shades in place of KMLib's `FactionPalette`, in `MapPalettes`, `MapStyling`, `ResolvedBlocPaint`, `BlocPaletteReader`, `BlocPresence` and the label styling.
  - `SystemOwner` is an owner ID and an `OwnerPalette`: `factionId` is `ownerId`, the two colour components are one `palette`, `resolvePalette` is gone and `mapFactionIdBySystemKey` is `mapOwnerIdBySystemKey`. `resolveForBloc` is `SectorBlocPalettes.resolveOwnerOf`.
  - `ViewGrouping` is `ViewReading`, carrying the reading and the owner source beside the view. `ClusterLabelStylingSnapshot` carries the categories and the reading in place of it.
  - `OwnerStyleDecision` carries the category an owner draws in rather than whether it takes the independent style, and `OwnerStyleResolver.resolveBlocStyleDecision` and `OwnerStyling.resolveFrom` take the reading and the categories.
  - `RenderStyleReader.readRenderStyle` takes the layer's categories and the sampled preferences, `BlocNameStyles` is a name style per category with `readFromLunaSettings` gone, `FactionlessStyleResolver.resolveCategoryOf` is `isSettledSystem` and `ClusterAnchorsBuilder.rebuildClusterAnchors` no longer takes a sector.
  - `OwnerMapBuildInputs.wasBuilt()` is new: false for the placeholder a failed first build stands behind, which the incremental refresh does not fold into.
- **The frame sequence is the framework's:**
  - `PoliticalMapLayerRenderer` is gone. `kmu.maplayers.base.render.SequencedMapLayerRenderer` runs the frame every painting layer runs - the stand-down, the refresh, the cursor read per pass, the paint per band and the hover box - over the `MapLayerFrameParts` a layer supplies: a stand-down read, a `MapFrameCache`, a `MapFrameCompositor`, its `MapLayerHoverGates` and its box.
  - `OwnerMapLayerRenderer.createForLiveScreen` composes the owner map's parts into one and returns it.
  - `OwnerMapCache` is the owner map's `MapFrameCache`: `refresh` is `refreshDrawLists`, and `resolveHoverTargets` is new.
- **A layer's state is its own:**
  - `MapLayerViewRegistry` is an instance a layer builds over its own save key, views, default view and host tab, in place of static members. `getActiveView()` is gone: `resolveActiveViewOn(ScreenLayerPicks)` answers for the screen a frame read once.
  - Per-sector pieces a layer holds go through `SectorMapMachinery.resolveLayerMachinery(layerId, type, make)`.
  - `SelectableBlocCache.resolveBlocCacheIn` takes the layer ID.
  - `FilterSelectionHeal.healStaleSelectionAgainstActiveView` takes the sector and the registry it heals against; `healStaleSelectionAgainstLiveSector(registry)` is the entry for a caller holding no sector.
  - `OwnerMapBodyControls.buildViewSelector` takes the sector its body was built for, which a view switch heals the spotlights against.
  - `PoliticalMapLayer` is constructed with its views rather than being a singleton.
- **Body preferences are the layer's:**
  - `NameFormatPreference`, `UninhabitedOutlinePreference` and `RecedePreferences` are instances over keys a layer names, handed to the tier together as `OwnerMapBodyPreferences`.
    - Their static members are gone, and so are the `RecedePreferences.FILTER` and `ALLIANCE_NON_ALLIED` sets.
    - `OwnerMapLayerRenderer.createForLiveScreen`, `OwnerMapCache`, `OwnerMapRebuildDecider` and `ContentInputs.sampleForView` take them.
  - `ContentInputs.allianceRecedeAdjustment` is `viewRecedeAdjustment`.
  - `FactionNameFormatChoice.fromKeyOrDefault` is gone: the choice is a KMLib `PersistedChoice`, read back through `PersistedChoices.fromKey`.
- **Groups and mechanics in the tier's own words:**
  - `HolderGrouping`:
    - `allianceNameByBlocId` is `groupNameByBlocId`, and `resolveAllianceName` is `resolveGroupName`.
    - `isAlliance` is `isGroupedBloc`, and `hasAnyAlliance` is `hasAnyGroupedBloc`.
  - `HolderPass.openClaimReaderThrough` is the political map's `PassClaimReaders.openClaimReaderOver`.
  - `ColonyQualifierFacts.isHoldingTheClaim` is `leadingFinding`: a finding the painting layer states ahead of every other, already worded, or null. `SystemColonyReading.readQualifierFacts` assembles one.
  - `FactionTooltipLine.buildCountedFactionLine` takes whether the faction was weighed, drawing an unweighed one quietly.
- **Polls, hover gates and colony transfers:**
  - `MapLayerSectorWatcher` is abstract. A layer installs its own subclass, because the engine removes transient scripts by exact class and a shared class let one layer's poll evict another's.
  - Hover gates:
    - `PoliticalMapHoverGates` is gone: a layer answers its own cursor switches through `kmu.maplayers.base.hover.MapLayerHoverGates`, and `SharedOwnerMapHoverGates` answers it from the one shared set of owner-map hover switches.
    - `MapLayerHoverGates.isCursorReadNeeded()` is a default method.
  - Colony transfers:
    - `PoliticalMapMarketTransferListener` implements KMU's own `kmu.starsector.listeners.MarketTransferListener` rather than Nexerelin's `InvasionListener`.
    - A listener of that type registered on a sector is told of every colony Nexerelin transfers there.
- **The political map's hover boxes and dominance pass:**
  - `SystemStandingsTooltip` and `SystemClaimContestTooltip` are gone, each merged into its one subclass, `SystemDominationTooltip` and `SystemClaimTooltip`. `LiveVisibilityClaimBreakdownReader` is gone too: the claim box opens its reader over the hover's own sector.
  - `DominancePass.over` is `createOver`, and `MarketProximityTieBreak.forSystem` is `createForSystem`. `DominancePass.readFromLunaSettings` is gone.
  - `FilteredPolitics.resolveFilteredHolder` returns a `HolderResolution`.
- **Settings readers and string keys:**
  - Readers:
    - `KmuPoliticalMapDiagnosticsSettings`, `KmuPoliticalMapGeometrySettings`, `KmuPoliticalMapHighlightSettings` and `KmuPoliticalMapRibbonSettings` take `OwnerMap` for `PoliticalMap`.
    - `KmuPoliticalMapTerritorySettings` is `KmuOwnerMapStyleSettings`.
  - Getters:
    - `getPoliticalMap*` are `getOwnerMap*`.
    - Except `getPoliticalMapAllianceMutedOpacityModifier`, which is `getOwnerMapMutedOpacityModifier`.
    - And `shouldDecivilisedSystemsDrawTerritory`, which is `shouldCountDecivilisedSystemsAsPopulated`.
  - The nineteen `KmuStringKeys.POLITICAL_MAP_*` constants the tier labels its controls with are `OWNER_MAP_*`, with their `strings.json` keys.
- **A hover-box status may colour its own finding:** `CellTooltipQualifier` carries a `findingColour`, set with `drawsFindingIn(Color)`, for a finding whose colour is itself the fact - a relation level, say. Left unset, the finding reads in the box's gold. The canonical constructor takes it between `findingText` and `trailingWordText`.
- **A pass reaches the drawn layer through its sector's guard:** `MapLayerRegistry.resolveDrawnMapRenderer` is gone. `DrawnLayerGuard.resolveGuardIn(machinery)`, in `kmu.maplayers.base.render`, hands a pass's work the drawn layer's renderer on that sector, and switches off there a layer that throws from it.

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
