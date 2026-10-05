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

- **Simplified Chinese (简体中文).** The release carries a second zip, `KMU-<version>-zh-hans.zip`, with the settings screen, the Map Layers sidebar, the hover boxes, the in-game notices and the launcher's mod list entry in Simplified Chinese. It needs the [Chinese localisation](https://github.com/TruthOriginem/Starsector-Localization-CN) laid over `starsector-core` first: the game's own fonts hold no Chinese characters, and without it every one of them draws as `?`. A setting chosen from a fixed list of options keeps its options in English, because LunaLib saves the option's label - so your settings carry over between the English and Chinese zips. The release notes carry each version's changes in Chinese as well, and the Chinese zip's `CHANGELOG.md` is in Chinese.
- *Map - Politics - Visuals* setting **Decivilised systems - Should draw territory**, on by default. Switched off, a revealed decivilised world stops counting as somebody living in its system: that system is drawn as uninhabited rather than as territory, its owner is no longer offered in the layer's picker, and it takes no presence band and no colony size in the stats. The world itself is still found, still listed, and still named in the star system tooltip. - Requested by **NoticeMeSenpai** [at **USC**](https://discord.com/channels/187635036525166592/1549091275167240272/1549750490617741395).
- **Map labels, hover boxes and the Map Layers sidebar draw in a font that holds their text.** Where a font KMU asks for cannot draw a faction, system or colony name, or a word KMU shows - a language pack's fonts leaving some characters out - that text is drawn in a smaller cut of the same font, or in the game's own default font, rather than as question marks. With the Chinese localisation installed, the faction names on the map step down from the largest cut, which it leaves as the game ships it, to the next one. On an English install nothing changes.

### Changed

- **The mod list names the mod Klark Morrigan's Utilities (KMU)**, so the abbreviation used everywhere else in the game and in its settings sits beside the full name. Update checkers show the same name.
- **The release zip is named for its language:** `KMU-<version>-en.zip`. Each language KMU is translated into ships as a zip of its own on the same release page, and the release notes name which zip is which. Update checkers keep working across the change: an install of an earlier version is still told about this release.
- A **decivilised world** is now revealed by another faction's colony in the same system at every setting of *Map - Visibility* **Show decivilised worlds surveyed at least to**. That setting now governs your own survey alone: at *Seen* a visit to the system is enough, and at *Preliminary* or *Full* you must survey the world itself. Previously the two upper levels also refused a neighbouring colony's word, so a world plainly visible to everyone living beside it stayed off the map.
- **Show decivilised worlds surveyed at least to** now ships at *Full* rather than *Seen*. Unless you have set the field yourself, in which case your value is kept, a decivilised world is named only once you have surveyed it or somebody is already living in its system. Flying past no longer puts one on the map. With a neighbour's word travelling at every level the common case is still covered, and surveying a world standing alone is now worth doing.
- **A mod that changes what KMU binds to no longer stops the game loading.** Each piece of KMU's start-up wiring already ran behind its own boundary, so a piece that failed cost itself and nothing else - but only where it failed by throwing. Where another mod had moved or renamed what that piece binds to, the failure arrived as a link error instead, which the boundary did not catch, and one such mod took the whole load down. Both are caught now. The clearest case is Nexerelin: KMU registers a listener implementing one of Nexerelin's own interfaces so the political map repaints when a colony changes hands mid-campaign, and a Nexerelin release that reshapes that interface would previously have ended every load while both mods were enabled. It now costs that listener alone, logged in `starsector.log`, with the rest of the map standing.
- **A LunaLib release that stops KMU hearing about settings changes is reported in-game.** KMU asks LunaLib to tell it when you change a setting, so the change applies without a reload. Where LunaLib refuses, a notice names LunaLib and its version once per session and says what that costs: a changed setting may not take effect until you restart the game. The rest of KMU keeps working. Previously this went only to `starsector.log`, so a setting that did nothing gave no hint why.
- **A Nexerelin release that changes how it keeps alliances no longer stops the game.** Every few seconds the political map checks whether alliances have formed or dissolved, and a failure to reach Nexerelin's alliances during that check was not caught. Now the map treats every faction as standing alone for the rest of the session, a notice says so once, and everything else keeps working.
- **A game update that changes what the map layers read off the game's screens is reported in-game.** The map layers find out whether a map is up, stand their tick box on the filter row, open the arrange dialog, stand aside for the game's own dialogs, the codex and star tooltip, keep their upper band above the nebulae, and draw with the Starscape filter on by reaching into the game's own code, which a game update can change. Each already degraded quietly where that happened; now a notice names Starsector with the game version KMU was made for and the one you are running, says what stops working - one thing per notice, such as the tick box going missing or the layers not showing on a map - and suggests updating or downgrading the game, or waiting for a KMU update. Once per session each, and everything else keeps working. Previously the only trace was a line in `starsector.log`.
- **Fast Rendering** version mismatches are now reported in-game instead of ending the game. KMU reads where your cursor is on the sector map out of Fast Rendering's internals, and those internals move between its releases. There are three ways that can break: the part KMU looks for is gone, it is called from the game and refuses, or it is called on Fast Rendering's own thread and refuses there. The second is the one that matters from Fast Rendering 0.8.9, which declares every entry point and refuses the ones it does not implement - so the mismatch is invisible until the moment the map is drawn, and then it took the game down from inside KMU's own code. All three are now caught. Where the cursor reading can no longer be taken, a notice names Fast Rendering and both versions once per session, and the sector map keeps drawing without responding directly to the cursor - no star system highlight and tooltip, though the Map Layers sidebar still reacts to it. Nothing else is affected and your save is untouched. Previously the mismatch ended the map's render pass and named KM code in the error, so a Fast Rendering mismatch looked like a KMU bug.
- **Each setting's description ends with its default on a line of its own.** The game highlights a run only where the characters beside it are whitespace or ASCII punctuation, so a default closing a sentence in a language whose full stop is neither could not highlight. A line break counts as whitespace.

### Fixed

- **A commented-out row in `data/config/kmu/installations.csv` is ignored.** A row whose entity type starts with `#`, the way the game's own tables comment a row out, was read as an entry for a type named after the comment. It now stays out of the table, as a blank row spacing the file does.
- **A fault in the political map's repaint of a conquered colony no longer ends the game.** Nexerelin tells KMU of a colony changing hands at the end of its own transfer, from inside an invasion, a rebellion or a transfer dialog with no catch of its own, so a failure while the political map repainted that colony would have reached the engine and ended the game with an error naming Nexerelin. It is now caught and logged, and everything Nexerelin does after telling its listeners still runs. The colony is repainted by the map's next check a few seconds later.
- **A fault in a map layer no longer ends the game.** Nothing under the sector map's drawing, or under the star system tooltip over it, caught a failure, so any fault in the layer being drawn took the game down with the map open. The failing layer now stops drawing until you next load a save or switch *Features* **Enable map layers** off and back on, and the fault is logged in `starsector.log` with its trace. The map, the sidebar and your other layers keep drawing.
- **A game update that changes the map screens no longer ends the game through the map-layers tick box or the arrange dialog.** Both find their place by reaching into the game's own screen code, and a game build that changed it could fail there in a way neither caught, ending the game while a map was open. The tick box now stays off that screen, with a line in `starsector.log`, and the arrange dialog closes.
- **On the political map's *Claims* view, a colony founded or lost in a claimed system keeps the system in its claimant's colours.** The map repaints a changed system on its own within a few seconds, and on the *Claims* view that repaint went by who held the system rather than who claimed it, so the system took the holder's colours until the next full redraw.
- **Switching on *Dev* **Reflection probe traces** repeats every warning about the game's screens.** The warnings from the map-layers tick box and the sector map's own state were left out, so turning the traces on after one of those broke added no line explaining it.
- **The Market Condition Manager's counts line draws its available and total counts in grey.** They and the separators before them were meant to read grey and drew in the body colour: the game does not highlight a run that touches the word before it, and each separator started with a space. The grey starts at the dash.

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
