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

### Fixed

- **With a faction spotlit, the political map follows a colony changing hands**, rather than showing the old owner until you move the spotlight.
- **The spotlight list follows a colony changing hands too**, so its numbers match the map.
- **A system two factions hold with equal weight repaints when one of them takes it**, rather than at the next full redraw.
- **The alliances view shows a Nexerelin alliance's new name once it is renamed**, rather than keeping the old name until something else redraws the map.
- **The Market Condition Manager no longer opens on a colony you have already left.**

### Changed

- **Minor optimisations**. The political map does less repeated work when it redraws: faction colours, each owner's paint and the border settings are read once per redraw rather than once per system or cell.

### For developers

<details>
<summary>Internals and the map layers framework</summary>

#### Added

- **`ColonyKind.isUngoverned()`**: whether a place is people living under nobody's authority, for every reader telling governed colonies from ungoverned ones.
- **`FactionStanding.isWeighed()`**: whether the pass weighed the faction's colonies, so a reader branching on the kind asks rather than testing the sealed type.
- **`FactionTooltipLine.resolveCrestMark(SectorAPI, String)`**: the mark a faction's line opens on, for a line marked as that faction's.
- **`HolderRankingRules.createByMarketProximity(StarSystemAPI, Colonies, ColonyKnowledge, HolderGrouping)`**: the one build of the live ranking rules, the neutral bar and the proximity tie-break, which the fills and the refresh poll both rank under.
- **`HolderPass.palettes()`** and **`DominancePass.palettes()`**: the pass's one palette read, which remembers each bloc's owner and shades for the pass, so every resolve and band over one rebuild colours a bloc through the same object.
- **`KmuErrorReporter.createLoggingReporter(Class)`**: a reporter writing each caught failure as a warning, with its cause, under the reporting class's logger.
- **`MapLayerRefreshBoard.getGroupingRevision()`**: how many times a system has been marked stale on the board, which no drain moves back. A reader that cannot drain the set, such as the sidebar picker's memo, keys on it.
- **`MapStyling.readFromLunaSettings(OwnerCategories, OwnerReading, ContentInputs)`**: one build's paint scheme, read once and handed to every part of the build that styles by it.
- **`MapStyling.resolveUnownedStyle(boolean)`**: the style an unowned cell draws in, so the map's fills and the border-tracing overlay look it up the same way.
- **`PoliticalHolderView`**, which every political map view implements: the alliance set's revision as its content revision, who stands together in a contest, the holder categories, and the faction reading.
- **`SmoothingStageSink`** and **`BorderSmoothing.smoothBorderLoops(List, BorderSmoothingStyle, SmoothingStageSink)`**: the smoothing reports each pass that ran, so a caller showing the stages shows them in the order and under the gates the map drew by. `ClusterBorderStageCollector` is one.

#### Fixed

- **The *Claims* view's hover box reads the sector it was handed**, through one walk shared by its status line and the claim mechanic, rather than letting the claim read the running game's sector.
- **The Market Condition Manager logs the failures it carries on past**, where they were dropped.

#### Public contracts changed (**breaking**)

None of these reach a player: every LunaLib field ID and every value saved in sector memory keeps its spelling, so settings and saves carry over. They reach a mod building a map layer on KMU's framework.

##### Packages

The owner-map tier's core knows nothing of what an owner is. What the layers painting holders share lives under `kmu.maplayers.ownermap.owners.holders`: `base` for every kind of holder, with its picker in `base.picker`, `factions` for one faction as one owner, and `alliances` for a group of factions as one owner. The core may not import that subtree.

| Before | After |
| --- | --- |
| `ownermap.holding`, `ownermap.owners.holders`, `RibbonPlanInputs`, `BlocPaletteReader`, `SectorBlocPalettes`, `HolderCategories`, `OwnerMapCategory` and `ColonyCellRibbons` | `ownermap.owners.holders.base`, apart from the types below |
| `BlocPickerAssembly`, `BlocStanding`, `BlocStandingReader`, `BlocStandingSortMode` and `SizedBlocMetrics` | `ownermap.owners.holders.base.picker` |
| `SystemStatusRow` and the colony lines of `ownermap.tooltip` | `politicalmap.tooltip`, beside the political map's hover boxes that read them |
| The faction lines of `ownermap.tooltip` | `ownermap.owners.holders.factions` |
| `ownermap.tooltip.TermTooltipLine` | `base.tooltip.content.TermTooltipLine`. `ownermap.tooltip` is gone |
| `ownermap.sidebar.BodyControlTarget` | `base.sidebar.BodyControlTarget` |
| `ownermap.render.clusters.MapStyling` | `ownermap.render.style.MapStyling`, beside the theme reader it is read through |
| `BlocAffiliation`, `ContestSide`, `ContestSides` and `BlocRelations` | `ownermap.owners.holders.alliances` |
| `BlocFriendliness`, with `areBlocsFriendly(Set, Set)` | KMLib's `kmlib.starsector.factions.relation.FactionSetFriendliness`, with `areSetsFriendly(Set, Set)` and `createForSector(SectorAPI)` |
| `base.labels.anchor.DiagnosticPalette` | `base.render.DiagnosticPalette`, which the label and cluster overlays share |
| `base.refresh.MapSubstrateStalenessSource` | `base.visibility.colonies.ColonySightingsPoll`, final, beside the register it writes into. Its sweep is `recordSightingsByInhabitants()`, and it no longer implements `MapLayerStalenessSource` |
| `politicalmap.dominance.standings.StandingFraction` | `politicalmap.dominance.tooltip.StandingFraction`, beside the box that reads it |
| `kmu.mods.nexerelin.alliances.AllianceGroupingFactory` | `ownermap.owners.holders.alliances.AllianceGroupingFactory`: it reads no Nexerelin type |
| `kmu.ui.context` and `kmu.ui.geometry.KmuUiSize` | `kmu.conditions.ui.context` and `kmu.conditions.ui.picker.render.KmuUiSize`, in the one feature they serve. `kmu.ui` is gone |
| `KmuStringKeys`' string IDs | One key class per reader in `kmu.util`: `KmuMapStringKeys`, `KmuOwnerMapStringKeys`, `KmuPoliticalMapStringKeys` and `KmuMarketConditionStringKeys`. `KmuStringKeys` keeps the category, `get(String)`, `format(String, Object...)` and the LunaLib settings pair |

The build closes `kmu.maplayers.base` to `kmu.mods`, `kmu.conditions` and `kmu.maplayers` to each other, and `kmu.settings` and `kmu.util` to both features.

##### Renames

| Before | After |
| --- | --- |
| `SelectableBloc`, `SelectableBlocCache`, `RankedBloc`, `BlocMetrics`, `PaintingBlocMetrics`, `BlocMetricSortMode`, `BlocSortModeComposer`, `BlocPickerRead`, `BlocPresenceIndex`, `BlocPresenceIndexBuilder`, `BlocStatsRead`, `SpotlitBlocs`, `ResolvedBlocPaint`, `BlocNameStyles` and `BlocPresence` | `Owner` in place of `Bloc` in each name and its members |
| `FactionNameFormatChoice`, `FactionPaletteSlot`, `FactionlessStyleResolver` and `kmu.settings.FactionPaletteChoice` | `OwnerNameFormatChoice`, `OwnerPaletteSlot`, `UnownedStyleResolver` and `OwnerPaletteChoice` |
| `HolderOwnerReading` | `FactionOwnerReading`, and `AllianceOwnerReading` over it for a layer painting alliances |
| `OwnerMapCategory`, `OwnerMapInhabitation` and `politicalmap.holders.DefaultHolderProvider` | `HolderCategory`, `HolderInhabitation` and `DominanceHolderProvider` |
| `getFactionLabels()` on `OwnerMapCache` and `OwnerMapDrawables`, `OwnerMapBuildInputs.resolveBlocPaintOf(String, SystemOwner)` and `OwnerStyleResolver.resolveBlocStyleDecision(String, OwnerReading, OwnerCategories, ContentInputs)` | `getOwnerLabels()`, `resolveOwnerPaintOf(SystemOwner)` and `resolveOwnerStyleDecision(String, OwnerReading, OwnerCategories, ContentInputs)`. The owner's ID is the `SystemOwner`'s own |
| `KmuStringKeys.OWNER_MAP_CTL_FACTION_NAMES` and its `strings.json` key | `KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_FACTION_NAMES`, the political map's own caption |
| The nine `KmuStringKeys.OWNER_MAP_TOOLTIP_*` constants and their `strings.json` keys | `KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_*`: only the political map's boxes read them |
| `ownermap.owners.OwnerPalette` | KMLib's `kmlib.colour.ColourPair`, which a faction's pair already is. `SectorBlocPalettes.adoptFactionPalette(FactionPalette)` is gone |
| `HolderPass.over(SectorAPI, ColonyReadRules, HolderGrouping)` | `HolderPass.createOver(SectorAPI, ColonyReadRules, HolderGrouping)` |
| `SystemColonyReading.readColoniesIn(SectorAPI, StarSystemAPI, Colonies, ColonyKnowledge)` | `readColonyReadingOf(SectorAPI, StarSystemAPI, Colonies, ColonyKnowledge)` |
| `SharedBlocSortModes.declareMarketSizeMode(List)` | `SizedBlocMetrics.createMarketSizeMode(List)` |

##### Seams

| Before | After |
| --- | --- |
| `OwnerPaintedView`'s `resolveBlocPickerRead(SectorAPI, ColonyReadRules)`, `buildBlocPickerRead(SectorAPI, HolderGrouping, OwnerReading, BlocStatsRead, ListSortModes)` and `resolveSelectableBlocGate(HolderGrouping)` | On `HolderPaintedView`, where `buildBlocPickerRead` is `readPickerUnderOneGrouping(SectorAPI, Function, ListSortModes)`: it samples the grouping once and names the rows through the view's own reading. `OwnerPaintedView` keeps `resolveOwnerPickerRead(SectorAPI)`, empty by default |
| `HolderPaintedView.resolveContestGrouping()` | `resolveContestAffiliation()`, answering an `OwnerAffiliation` |
| `RibbonPlan.planCellRibbon(Optional, List, BlocAffiliation, RibbonPlanRules)` over a `BlocAffiliation` | Over `kmu.maplayers.ownermap.owners.OwnerAffiliation`, which `BlocAffiliation` implements |
| `SpotlitBlocs.findPresentSystemKeys(HolderPass, String, Set)` and `isBlocPresentIn(HolderPass, String, StarSystemAPI)` | `HolderInhabitation.findSpotlitPresenceSystemKeys(HolderPass, String, Set)` and `HolderInhabitation.isBlocPresentIn(HolderPass, String, StarSystemAPI)` |
| `HolderOwnerSource(HolderGrouping, HolderGrouping, HolderProvider, SystemHolderResolveSource, Function)` | `HolderOwnerSource(HolderGrouping, HolderPaintedView)`: the view answers the rest |
| `HolderOwnerSource.grouping()`, `BlocAffiliation.NONE` and `BlocAffiliation.areBlocsAllied(String, String)` | `grouping()` is gone: the view's `resolveGrouping()` names it. For `NONE`, `OwnerAffiliation.NONE` or `new BlocAffiliation(HolderGrouping.identity())`. `areBlocsAllied` is `areOwnersAllied` |
| `HolderGrouping.collectBlocIds(Collection)` | Gone |
| `HolderPass.readKnownColoniesIn(StarSystemAPI)` and `readInhabitingColoniesIn(StarSystemAPI)`, public | Package-private. A reader outside it takes `readHabitationIn(StarSystemAPI)` or `readKnownColonyFactionIds(StarSystemAPI)` |
| `AllianceBodyControls` | Gone: `AlliancesView.getViewBodyControls(BodyControlTarget)` binds the shared recede control itself |
| `SystemColonyReading`'s `readKindOf(String)`, `isDiscoveredColony(String)`, `isOpenlyKnownColony(String)`, `readConcealmentOf(String, boolean)`, both `readQualifierFacts` overloads and `describeColony(CellTooltipEntryLine, String, ColonyQualifierFacts)` | `describeColony(CellTooltipEntryLine, AccountedColony, String)`, the colony built by `AccountedColony.createEconomyListedColony(String, boolean)` or `createOffEconomyColony(String, boolean)`. A caller already holding the kind passes it to `describeColony(CellTooltipEntryLine, AccountedColony, ColonyKind, String)` |
| `ColonyQualifier`, `ColonyQualifierFacts`, `ColonyConcealment` and the `SystemColonyReading` constructor, public | Package-private: a line is described through `SystemColonyReading.describeColony`. The constructor takes the colony remark as a `BiFunction` and refuses a null part |
| `RenderStyleReader.readFactionStyle()`, `readIndependentStyle()`, `readDecivilisedStyle()` and `readUninhabitedStyle(boolean)` | `HolderCategoryStyleReader` |
| `BlocPresence.collectColouredPresences(Map, BlocPaletteReader)` | Gone: `ColonyCellRibbons` colours its own counts |
| `NameFormatPreference(String)` | `NameFormatPreference(String, String)`: the layer also names the name radio's caption key |
| `StandingRowResolver.resolveRows(SectorAPI, List, HolderGrouping, FactionAccountResolver)` | `StandingRowResolver.createForBox(SectorAPI, HolderGrouping, FactionAccountResolver)` once per box, then `resolveRows(List)` per block |
| `FactionPresentation`, public, with `fullName()` | Package-private, with `labelText()`. A line marked as a faction's takes `FactionTooltipLine.resolveCrestMark(SectorAPI, String)` |
| `GroupStanding(String, int, List)` over an empty member list | Refused with an `IllegalArgumentException`: a group stands in a system only through a member there |
| `SectorClaims.resolveClaimingHolderIn(StarSystemAPI, HolderPass, ClaimReader, SectorBlocPalettes)` | `resolveClaimingHolderIn(StarSystemAPI, HolderPass, ClaimReader)`: a bloc's shades come off the pass's own `palettes()` |
| `FilteredPolitics.resolveSpotlitHolder(SectorAPI, HolderGrouping, String)` and `SpotlitOwners.readSpotlitOwnerKey()` | `SpotlitOwners.createSpotlitOwner(ColourPair)`, handed the bloc's shades off the pass's own `palettes()`, so the claims resolve spotlights without naming the dominance package |
| `ArrangementDialogMapCover`, `CodexMapCover`, `ModalDialogMapCover` and `kmu.mods.console.ConsoleMapCover` | One `ScreenClaimMapCover`, reading `ScreenClaim.isScreenClaimed()` |
| `MapCoverReader.createForLiveScreen()` composing the optional mods' covers itself | `MapCoverReader.registerOptionalModCovers(List)`, called by `MapLayers` with the covers of the mods installed |
| `VanillaMapLayerToggleAttacher()` asking Nexerelin whether the alliances view is offered | `VanillaMapLayerToggleAttacher.registerAllianceViewAvailability(BooleanSupplier)`, called by `MapLayers` |
| `DebugBorderTracingBuilder.buildDebugDrawables(CellGeometryCache, ResolvedOwners, OwnerCategories, ContentInputs)` and `ClusterLabelStylingSnapshot.resolveForTracing(Map, ViewReading, ContentInputs, FontAtlas)` | `buildDebugDrawables(CellGeometryCache, ResolvedOwners, MapStyling)` and `resolveForTracing(Map, MapStyling, OwnerReading, ContentInputs, FontAtlas)`, both handed the one `MapStyling` the tracing rebuild reads. The snapshot's `desaturationPalette` and `categories` components are its `mapStyling` |
| `OwnerMapBuildInputs`, `RibbonPlanRules` and `RibbonPlan`, records | Final classes with the same constructors and accessors. `OwnerMapBuildInputs` remembers each owner's paint and compares by identity; `RibbonPlan` still compares by its runs |
| `MapStyling(RenderStyle, OwnerCategories, ColourPair, ColourPair, ColourPair)` | `MapStyling(RenderStyle, ClusterBorderTrace, OwnerCategories, ColourPair, ColourPair, ColourPair)`: the build's border trace rides with its theme, so a cluster group is never traced under a live read of its own |
| `OwnerMapBuilder.buildClusters(CellGeometryCache, ViewReading, ContentInputs, ResolvedOwners)`, reading the styling itself | `buildClusters(CellGeometryCache, ViewReading, ContentInputs, ResolvedOwners, MapStyling)`: the rebuild reads the styling once for everything it builds |
| `RibbonPlanInputs(HolderPass, BlocPaletteReader, OwnerAffiliation, RibbonPlanRules)` and `RibbonPlanInputs.createForPass(HolderPass, OwnerAffiliation, RibbonPlanRules)` | `RibbonPlanInputs(HolderPass, OwnerAffiliation, RibbonPlanRules)`. `palettes()` answers the pass's own, so a band cannot be coloured through anything the fills were not |
| `HolderRankingRules.createByLowestId(Predicate)` and `createOpenContestByLowestId()` | Gone: every live ranking breaks a tie by proximity through `createByMarketProximity`. A reader with no geometry builds `new HolderRankingRules(Comparator.naturalOrder(), candidacy)` |
| `KmuErrorReporter.noop()` | `KmuErrorReporter.createLoggingReporter(Class)`: a caught failure is always written somewhere |
| `StarsectorMarketUiContextTracker` extending `BaseCampaignEventListener` | Implementing `ColonyInteractionListener`, the interface the listener manager delivers market events to |
| `kmu.mods.nexerelin.alliances.AllianceFingerprint.compute(List)` and `NexerelinAlliances.computeAllianceFingerprint()` | Gone: `PoliticalMapStalenessSource` compares the alliance records themselves between polls. `kmu.mods.nexerelin.alliances` is gone |
| `PoliticalMapLayer(List, OwnerPaintedView)`, `PoliticalMapStanding(MapLayerViewRegistry)`, `PoliticalMapInstaller.installAll(SectorAPI, MapLayerViewRegistry)` and `PoliticalMapStalenessSource(SectorMapMachinery)` | Each also takes the `AllianceSource` the poll reads, which `MapLayers` binds |

##### Moved to KMLib

Code that knows nothing of KMU now lives in KMLib, where any mod built on it can reach it.

| Before | After |
| --- | --- |
| `ObservationStore`, `ObservationCodec` and `RecordedObservations` in `base.visibility.observations` | `kmlib.starsector.memory.observations` |
| `kmu.starsector.listeners.MarketTransferListener` | `kmlib.starsector.listeners.MarketTransferListener` |
| `kmu.mods.nexerelin.NexerelinMarketTransferRelay` and `kmu.mods.nexerelin.NexerelinInvasionListenerInstaller` | Gone: KMLib puts its own relay on each loaded sector where Nexerelin is enabled |
| `base.theme.GlLineHatchStroke` and `base.theme.HatchStroke` | `kmlib.opengl.hatch.GlLineHatchStroke`. `HatchStroke` is gone: `HatchStyle(HatchPattern, GlLineHatchStroke)` holds the record |
| `CellFrontierGeometry.clipCellsToFrontier(List, float[])` | `kmlib.opengl.PolygonTessellator.tessellateToBoundaryLoopsWithinLoop(List, float[])` |
| `KmuValues` | `KmlibStrings`, whose blank rule is `Character.isWhitespace` rather than `String.trim()`'s. `requireNonBlankText(String, String)` is `requireFieldText(String, String)` |
| `KmuTextFormats.joinWithParenthetical(Optional, Optional)` | Forwarding to `KmlibStrings`, under the same blank rule |

</details>

## [0.2.1] - 2026-10-06

### Fixed

- **JSON fields carrying object-holding arrays in `mod_info.json` are put at the bottom of the file**. There are third-party mod installers that naively read the first occurrence of the `id` field whether it's at the root or in records nested under `dependencies`. _Reported by **NH4CI** [at **Fossic**](https://www.fossic.org/forum.php?mod=redirect&goto=findpost&ptid=21517&pid=405627)._

### Dependency changes

- Updated for [KMLib 0.5.1](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/releases/tag/0.5.1).

## [0.2.0] - 2026-10-05

### Fixed

- **A crash under Fast Rendering `0.9.0` and later.** Thanks to **Genir**, [Fast Rendering now implements the missing OpenGL method](https://github.com/Halke1986/starsector-render/issues/11), the one the game itself answers when Fast Rendering is not installed. Under Fast Rendering, the map follows the cursor only from `0.9.1rc1` on.
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
| `getPoliticalMapAllianceMutedOpacityModifier()` and `shouldDecivilisedSystemsDrawTerritory()` | `getOwnerMapMutedOpacityModifier()` and `shouldCountDecivilisedSystemsAsPopulated()` |
| The nineteen `KmuStringKeys.POLITICAL_MAP_*` constants and their `strings.json` keys | `OWNER_MAP_*` |
| `HolderGrouping.allianceNameByBlocId`, `resolveAllianceName(String)`, `isAlliance(String)` and `hasAnyAlliance()` | `groupNameByBlocId`, `resolveGroupName(String)`, `isGroupedBloc(String)` and `hasAnyGroupedBloc()` |
| `ContentInputs.allianceRecedeAdjustment` | `viewRecedeAdjustment` |
| `SystemOccupancy.getHolderBySystemKey()`, `readHolderOf(SystemKey)`, `recordHolderOf(SystemKey, DominantHolder)` and `selectUnheldSystemKeysAmong(Set)` | `getOwnerBySystemKey()`, `readOwnerOf(SystemKey)`, `recordOwnerOf(SystemKey, SystemOwner)` and `selectUnownedSystemKeysAmong(Set)` |
| `SystemOccupancy.selectUnheldSystemKeysIn(Map, Set)` | `SystemOwner.selectUnownedSystemKeysAmong(Map, Set)` |
| `ClusterLabelStylingSnapshot.holderBySystemKey` | `ownerBySystemKey` |
| `DominancePass.over(HolderPass, DominanceRules)`, `over(SectorAPI, DominanceRules, ColonyVisibility, HolderGrouping)` and `MarketProximityTieBreak.forSystem(StarSystemAPI, Colonies, ColonyKnowledge, HolderGrouping)` | `createOver(HolderPass, DominanceRules)`, `createOver(SectorAPI, DominanceRules, ColonyReadRules, HolderGrouping)` and `createForSystem(StarSystemAPI, Colonies, ColonyKnowledge, HolderGrouping)` |

##### Views

A view answers who owns each system through one reading, sampled once, so its parts cannot disagree.

| Before | After |
| --- | --- |
| `OwnerPaintedView`'s `resolveGrouping()`, `resolveHolderProvider()`, `resolveRibbonPlanner(RibbonPlanInputs)`, `shouldUseIndependentStyle(String, HolderGrouping, ElementStyleAdjustment)`, `resolveBlocStyleAdjustment(String, HolderGrouping, ContentInputs)`, `resolveName(String, HolderGrouping, SectorAPI, FactionNameFormatChoice)` and `computeAllianceContentRevision(MapLayerRefreshBoard)` | `resolveViewReading(SectorAPI)`, required: a `ViewReading` of the view, its `OwnerReading` and its `OwnerSource` |
| None | `resolveCategories()`, required: the categories cells divide into |
| None | `resolveViewRecedeAdjustment(ScreenMemoryScope)`, defaulting to receding nothing |
| `buildBlocPickerRead(SectorAPI, HolderGrouping, BlocStatsRead, ListSortModes)` with the grouping | Also takes the view's `OwnerReading` |
| A view painting holders | Implements `kmu.maplayers.ownermap.owners.holders.HolderPaintedView`, whose `resolveGrouping()`, `resolveContestGrouping()`, `resolveHolderProvider()`, `resolveSystemHolderResolveSource()`, `resolveRibbonPlanner(RibbonPlanInputs)` and `resolveOwnerReading(SectorAPI, HolderGrouping)` it assembles into `resolveViewReading(SectorAPI)`. `DominancePaintedView` and `ClaimsView` are holder views. |
| `ViewGrouping` | `ViewReading`, carrying the reading and the owner source |

##### Owner sources

The tier reads no colony: a layer's `OwnerSource` does, over the `SectorWalk` the tier hands it.

| Before | After |
| --- | --- |
| None | `kmu.maplayers.ownermap.owners.OwnerSource`: `resolveOwners(SectorWalk, String)`, `openSystemResolve(SectorWalk, String)` and `resolveRibbonPlanner(SectorWalk, RibbonPlanRules)`. `SectorWalk.readReadingOpenedBy(Object, Class, Supplier)` keeps one reading per source per walk. |
| `ResolvedHolding` | `ResolvedOwners`, in `owners`: the owners, the hatched and unfilled systems, the inhabited systems and the spotlit owner's presence |
| None | `HolderOwnerSource`, the holder layers' source, over one `HolderPass` per walk |
| `SystemHolderResolveSource.openResolveOver(SectorAPI, HolderGrouping)` | Takes the batch's `HolderPass`, and `SystemHolderResolve` answers only `resolveHolderIn(StarSystemAPI)` |
| `ClaimsView` re-deriving a marked system by its holder | `ClaimSystemHolderResolve`, by its claimant, through `SectorClaims.resolveClaimingHolderIn(StarSystemAPI, HolderPass, ClaimReader, SectorBlocPalettes)` |
| `OwnerMapBuilder.resolveHolding(HolderPass, PoliticalMapView, ContentInputs)` | `resolveOwners(OwnerSource, SectorWalk, ContentInputs)`, and `buildClusters(CellGeometryCache, ViewReading, ContentInputs, ResolvedOwners)` takes the `ViewReading` and the `ResolvedOwners` |
| `OwnerMapCache` with a diagnostics provider and a per-system resolve source | A `CellSeedRule`, and `OwnerMapLayerRenderer.createForLiveScreen(SectorMapMachinery, String, MapLayerViewRegistry, OwnerMapBodyPreferences, OwnerMapPreviewHighlight, MapLayerHoverGates, Supplier, CellSeedRule)` follows |
| `IncrementalOwnerRefresh.applyStaleOwnerUpdates(SectorAPI, StandingOwnerMap, Set, SystemHolderResolveSource)` with a resolve source | The batch's `SectorWalk` |
| `CellRibbonsBaker.createForPass(PoliticalMapTerritories, CellGeometryCache, HolderPass, List)` and `CellRibbonSource.createForPass(HolderPass, PoliticalMapView, RibbonBakeSurface)` | Take a `SectorWalk` |
| `DebugBorderTracingBuilder.buildDebugDrawables(CellGeometryCache, SectorAPI, ContentInputs)` | Takes the resolved owners and the categories |
| `ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(StandingClusterAnchors, RevisedCellGeometry, SectorAPI, PoliticalMapView, ContentInputs)` | `rebuildDiagnosticClusterAnchors(StandingClusterAnchors, RevisedCellGeometry, ClusterLabelStylingSnapshot)`, styled by `ClusterLabelStylingSnapshot.resolveForTracing(Map, ViewReading, ContentInputs, FontAtlas)` |
| None | `SpotlitBlocs.isBlocPresentIn(HolderPass, String, StarSystemAPI)` |
| None | `kmu.maplayers.base.geometry.CellSeedRule`, with `SEED_DRAWN_SYSTEMS`. `CellGeometryCache.updateFromSector(MapVisibilityPass, CellSeedRule, Collection, CellSeedInputs)` and `PartitionSites.collectSitesFrom(MapVisibilityPass, CellSeedRule, Collection)` take one, and `DrawnSystemPositions.collectLivePositions(MapVisibilityPass, Predicate)` gains an overload taking a membership rule. |

##### Owner reading and styling

The tier asks a layer about its owners rather than reading factions.

| Before | After |
| --- | --- |
| None | `kmu.maplayers.ownermap.owners.OwnerReading`: each owner's shades, name, crest, recede and category. `HolderOwnerReading` is the political map's. |
| None | `kmu.maplayers.ownermap.render.style.OwnerCategories`: which categories exist and how each is styled. `HolderCategories` declares the four `OwnerMapCategory` values. |
| KMLib's `FactionPalette` in `MapPalettes`, `MapStyling`, `ResolvedBlocPaint`, `BlocPaletteReader`, `BlocPresence` and the label styling | `OwnerPalette` |
| `SystemOwner.factionId`, its two colour components and `mapFactionIdBySystemKey(Map)` | `ownerId`, one `palette` and `mapOwnerIdBySystemKey(Map)`. `resolvePalette()` is gone. |
| `SystemOwner.resolveForBloc(SectorAPI, HolderGrouping, String)` | `SectorBlocPalettes.resolveOwnerOf(String)` |
| `ClusterLabelStylingSnapshot` carrying a `ViewGrouping` | Carries the categories and the reading |
| `OwnerStyleDecision` saying whether an owner takes the independent style | The category the owner draws in. `OwnerStyleResolver.resolveBlocStyleDecision(String, OwnerReading, OwnerCategories, ContentInputs)` and `OwnerStyling.resolveFrom(RenderStyle, OwnerCategories, OwnerStyleDecision)` take the reading and the categories. |
| `RenderStyleReader.readRenderStyle(boolean)` | Takes the layer's categories and the sampled preferences |
| `BlocNameStyles.readFromLunaSettings()` | Gone: `BlocNameStyles` is a name style per category |
| `FactionlessStyleResolver.resolveCategoryOf(Set, SystemKey)` | `isSettledSystem(Set, SystemKey)` |
| `ClusterAnchorsBuilder.rebuildClusterAnchors(StandingClusterAnchors, RevisedCellGeometry, SectorAPI, ClusterLabelStylingSnapshot)` with a sector | No sector |
| None | `OwnerMapBuildInputs.wasBuilt()`: false for the placeholder a failed first build stands behind |

##### Frame sequence

Every painting layer runs one frame sequence, and the framework owns it.

| Before | After |
| --- | --- |
| `PoliticalMapLayerRenderer` | `kmu.maplayers.base.render.SequencedMapLayerRenderer`, over a layer's `MapLayerFrameParts`: a stand-down read, a `MapFrameCache`, a `MapFrameCompositor`, its `MapLayerHoverGates` and its box |
| `OwnerMapLayerRenderer.createForLiveScreen(SectorMapMachinery, String)` | Composes the owner map's parts into one renderer |
| `OwnerMapCache.refresh(PoliticalMapView, ScreenMemoryScope)` | `refreshDrawLists(OwnerPaintedView, ScreenMemoryScope)`, and `resolveHoverTargets()` is new |
| `MapLayerRegistry.resolveDrawnMapRenderer(SectorMapMachinery)` | `DrawnLayerGuard.resolveGuardIn(SectorMapMachinery)`, in `kmu.maplayers.base.render`, which switches off a layer that throws |

##### Layer state and preferences

A layer owns its state, so two layers cannot share it by accident.

| Before | After |
| --- | --- |
| `MapLayerViewRegistry`'s static members and `getActiveView()` | An instance over the layer's save key, views, default view and host tab, answering `resolveActiveViewOn(ScreenLayerPicks)` |
| Per-sector pieces a layer holds | `SectorMapMachinery.resolveLayerMachinery(String, Class, Supplier)` |
| `SelectableBlocCache.resolveBlocCacheIn(SectorMapMachinery)` | Takes the layer ID |
| `FilterSelectionHeal.healStaleSelectionAgainstActiveView()` | Takes the sector and the registry. `healStaleSelectionAgainstLiveSector(MapLayerViewRegistry)` serves a caller with no sector. |
| `OwnerMapBodyControls.buildViewSelector(ScreenMemoryScope)` | Takes the sector its body was built for |
| `PoliticalMapLayer` as a singleton | Constructed with its views |
| `NameFormatPreference`, `UninhabitedOutlinePreference` and `RecedePreferences` as statics, with `RecedePreferences.FILTER` and `ALLIANCE_NON_ALLIED` | Instances over keys a layer names, handed over as `OwnerMapBodyPreferences` to `OwnerMapLayerRenderer.createForLiveScreen(SectorMapMachinery, String, MapLayerViewRegistry, OwnerMapBodyPreferences, OwnerMapPreviewHighlight, MapLayerHoverGates, Supplier, CellSeedRule)`, `OwnerMapCache`, `OwnerMapRebuildDecider` and `ContentInputs.sampleForView(OwnerPaintedView, OwnerMapBodyPreferences, ScreenMemoryScope)` |
| `FactionNameFormatChoice.fromKeyOrDefault(String, FactionNameFormatChoice)` | KMLib's `PersistedChoices.fromKey(PersistedChoice[], String, PersistedChoice)` over a `PersistedChoice` |
| `MapLayerSectorWatcher` shared between layers | Abstract: each layer installs its own subclass, since the engine removes transient scripts by exact class |

##### Hover, transfers and the political map

Political map pieces that moved onto the framework, merged, or answer a richer question.

| Before | After |
| --- | --- |
| `PoliticalMapHoverGates` | `kmu.maplayers.base.hover.MapLayerHoverGates` per layer, and `SharedOwnerMapHoverGates` for the owner map's switches. `MapLayerHoverGates.isCursorReadNeeded()` is a default method. |
| `PoliticalMapMarketTransferListener` implementing Nexerelin's `InvasionListener` | Implements KMU's own `kmu.starsector.listeners.MarketTransferListener`, told of every colony Nexerelin transfers on its sector |
| `HolderPass.openClaimReaderThrough(ClaimReaderSource)` | The political map's `PassClaimReaders.openClaimReaderOver(HolderPass, ClaimReaderSource)` |
| `ColonyQualifierFacts.isHoldingTheClaim` | `leadingFinding`: a worded finding stated ahead of the rest, or null. `SystemColonyReading.readQualifierFacts(String, boolean, boolean, String)` and `readQualifierFacts(String, ColonyKind, boolean, boolean, String)` assemble one. |
| `FactionTooltipLine.buildCountedFactionLine(SectorAPI, String, int)` | Takes whether the faction was weighed |
| `SystemStandingsTooltip` and `SystemClaimContestTooltip` | Merged into `SystemDominationTooltip` and `SystemClaimTooltip` |
| `LiveVisibilityClaimBreakdownReader` | Gone: the claim box opens its reader over the hover's own sector |
| `DominancePass.readFromLunaSettings(SectorAPI, HolderGrouping)` and `readFromLunaSettings(SectorAPI)` | Gone |
| `FilteredPolitics.resolveFilteredHolder(HolderPass, String)` and `resolveFilteredHolder(DominancePass, String)` | Returns a `HolderResolution` |
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
- The project is relicensed under **LGPL-3.0-only**.

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
  - **Fast Rendering** compatibility.
  - **Nexerelin** compatibility.
  - **Random Assortment of Things** compatibility.
  - **LunaLib**-enabled settings.
  - *kmu_profiling* console command.
