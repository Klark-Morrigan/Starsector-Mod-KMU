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
- **The Chinese zip shows the Market Condition Manager in Chinese.**
- **The Chinese zip's console text carries a Chinese translation beside the English**: the help shows the Chinese above the English, and the Market Condition Manager command prints its English, ready for when the Chinese version of Console Commands draws Chinese. Until then the Chinese shows as `?`, a [known issue on its Fossic thread](https://www.fossic.org/forum.php?mod=redirect&goto=findpost&ptid=14602&pid=299524) that players reported on [2025-03-11](https://www.fossic.org/forum.php?mod=redirect&goto=findpost&ptid=14602&pid=299560), [2025-11-22](https://www.fossic.org/forum.php?mod=redirect&goto=findpost&ptid=14602&pid=348650) and [2026-01-12](https://www.fossic.org/forum.php?mod=redirect&goto=findpost&ptid=14602&pid=357213).
- **[KMLib]** **An update checker opens the Chinese zip's changelog in Chinese.**

### Changed

- **Minor optimisations**. The political map does less repeated work when it redraws: faction colours, each owner's paint and the border settings are read once per redraw rather than once per system or cell. The hover box reads the system under the cursor once while the cursor rests there, rather than on every frame, and reads it again when the map is redrawn or you move back onto the system.

### For developers

<details>
<summary>Internals and the map layers framework</summary>

#### Added

- **The Starscape reseat's two reads are profiled**, as `mapLayer.reseat.findTerrain` and `mapLayer.reseat.readPlacement`. The script asks both on every advance while a map is up, several per frame under the campaign speed-up.
- **The hover box is profiled**, as `mapLayer.hoverBox.paint` for each frame it draws and `mapLayer.hoverBox.compose` for each time it reads the sector.
- **`CellBoxMemo`**: the box a sector last composed, kept while nothing it was read under moves.
- **`MapLayerCommonRefreshSignal.DRAW_LISTS`**: raised by the overlay cache once it has rebuilt what it draws, in full or for marked systems.
- **`MapHoverState.getCellArrivalCount()`**: how many times the cursor has reached a cell it was not on, for work held for one visit to a cell.
- **`KnownMarketFootprints.foldFootprintsByFaction(Map)`**: the footprints folded out of breakdowns already read.
- **`ColonyKind.isUngoverned()`**: whether a place is people living under nobody's authority, for every reader telling governed colonies from ungoverned ones.
- **`FactionStanding.isWeighed()`**: whether the pass weighed the faction's colonies, so a reader branching on the kind asks rather than testing the sealed type.
- **`FactionTooltipLine.resolveCrestMark(SectorAPI, String)`**: the mark a faction's line opens on, for a line marked as that faction's.
- **`HolderRankingRules.createByMarketProximity(StarSystemAPI, Colonies, ColonyKnowledge, HolderGrouping)`**: the one build of the live ranking rules, the neutral bar and the proximity tie-break, which the fills and the refresh poll both rank under.
- **`HolderPass.palettes()`** and **`DominancePass.palettes()`**: the pass's one palette read, which remembers each bloc's owner and shades for the pass, so every resolve and band over one rebuild colours a bloc through the same object.
- **`CellTooltipEntryLine.createCountedEntityLine(EntityNameplate, int)`**: a counted line naming something the sector map draws, led by its map glyph.
- **`ContestBlockHeading.createFixedKeySource(String)`**: the heading source of a block whose heading no install changes.
- **`FilteredClaims.resolveFilteredClaimsThrough(HolderPass, ClaimReaderSource, String)`**: the spotlit claims with the reader opened over the pass.
- **`KmuErrorReporter.createLoggingReporter(Class)`**: a reporter writing each caught failure as a warning, with its cause, under the reporting class's logger.
- **`MapLayerRefreshBoard.getGroupingRevision()`**: how many times a system has been marked stale on the board, which no drain moves back. A reader that cannot drain the set, such as the sidebar picker's memo, keys on it.
- **`MapStyling.readFromLunaSettings(OwnerCategories, OwnerReading, ContentInputs)`**: one build's paint scheme, read once and handed to every part of the build that styles by it.
- **`NexerelinAlliances.resolveFactionAlliancesIn(SectorAPI)`**: the live alliance memberships as a `FactionAllianceSource` answers them. Nexerelin keeps one set for the running game, so the sector is not consulted.
- **`MapStyling.resolveUnownedStyle(boolean)`**: the style an unowned cell draws in, so the map's fills and the border-tracing overlay look it up the same way.
- **`MapPaints`**: one build's paints, over its scheme, owner reading and sampled picks. It answers an owner's paint and an unowned cell's, and the style decision `resolveOwnerStyleDecisionOf(String)` a cluster's name is coloured by, each remembered once resolved. A stage that paints takes it rather than the build, and `OwnerMapBuildInputs.getPaints()` hands out a build's own.
- **`SystemFillStates`**: the fill state each system draws in, naming each one that hatches or paints no fill and answering solid for every other.
- **`SystemOccupancy.readOwnerOfMembers(CellGrouping, List)`**: who owns the cells one cluster group is made of.
- **`PoliticalHolderView`**, which every political map view implements: the alliance set's revision as its content revision, who stands together in a contest, the holder categories, and the faction reading.
- **`SectorClaims.readClaimantBlocId(String, HolderGrouping)`**: the bloc a claim lands on, which the fill, the picker's stats and the band all read.
- **`StandingRankOrder.createRankOrder(ToIntFunction, Function)`**: the one order a box's standings and the band beneath a cell rank by.
- **`SmoothingStageSink`** and **`BorderSmoothing.smoothBorderLoops(List, BorderSmoothingStyle, SmoothingStageSink)`**: the smoothing reports each pass that ran, so a caller showing the stages shows them in the order and under the gates the map drew by. `ClusterBorderStageCollector` is one.
- **`base.ribbon.RibbonPlanBuilder`**: a plan laid run by run, whose list the plan takes over rather than copies.
- **`RibbonPlan.hasLength()`**: whether a plan's runs add up to any length, which decides whether a cell's ring is walked for it.
- **`base.ribbon.CellRibbonStore`**: a build's bands, their overlay paths and the rings they were traced along, dropped together when a cell is re-shaped.
- **`base.ribbon.RibbonPathInset`**: the two insets a band's path is traced at, padded and unpadded.
- **`base.ribbon.RibbonBakeTally`**: what one bake did with the cells a layer offered a band, written on the `mapLayer.bakeRibbons` call as `banded`, `traced`, `reused`, `unplanned` and `refusedForRoom`.
- **Debug lines** for a band bake's settings, a name fit nothing asked for, an owner whose clusters draw no name because it resolves none, and which mechanic counted each system on the domination views.

#### Fixed

- **The *Claims* view's hover box reads the sector it was handed**, through one walk shared by its status line and the claim mechanic, rather than letting the claim read the running game's sector.
- **The Market Condition Manager logs the failures it carries on past**, where they were dropped.
- **The Market Condition Manager and the console help in Chinese:** `strings.json` holds the manager's feedback, metadata lines and open messages, read through `KmuMarketConditionStringKeys`. `localisation/<locale>/commands.csv` is written to `data/console/commands.csv`, which is no longer committed. A translated help ends in the English one after a `-- English --` line, and so does each `console_` string, which the command reads through KMLib's `ConsoleStrings`. KMLib's `LocaleParity` holds each copy to the English bundle. The log keeps the manager's failures in English, whatever the result says.

#### Changed

- **Building KMU needs no Nexerelin jar.** KMU names no Nexerelin type, KMLib's adapters doing all of the reading, so a KMU class that named one fails to compile. The CI runner still provides the jar for the KMLib build alongside.
- **The band bake and the name fit are profiled under the map layers' prefix**, as `mapLayer.bakeRibbons` and `mapLayer.fitClusterAnchors` in place of `ownerMap.bakeRibbons` and `ownerMap.fitClusterAnchors`, since both now run in the map layers framework.
- **The `mapLayer.fitClusterAnchors` call ends with what it carried over**: `carried`, and `reuse` as `offered`, `unrecorded`, or `dropped` with the fingerprint parts that `moved`.
- **`mapLayer.render.ribbons` times the draw inside the GL state push**, as the other map passes do.

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
| `RibbonPlan`, `RibbonSegment` and `SystemRibbonPlanner` in `ownermap.ribbon`, and `CellRibbon`, `CellRibbonBuilder`, `RibbonPathTracer`, `CellRingPathCache`, `RibbonBand`, `RibbonStyle` and `RibbonBakePhases` in `ownermap.render.ribbon` | `base.ribbon`: a band is coloured runs laid round a cell's ring, whoever the runs belong to. `CellPresenceRibbonRenderer` is `base.ribbon.CellRibbonRenderer` |
| `CellRibbonPath`, `RibbonPathVerdict` and `CellRibbonPathRenderer` in `ownermap.render.ribbon` | `base.ribbon.overlay`, the band's diagnostic path overlay apart from the band itself. The build closes it to the rest of `base.ribbon` |
| `ownermap.render.labels.ClusterAnchorsBuilder` | `base.labels.anchor.ClusterAnchorsBuilder`: any layer with clusters fits its names the same way |
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
| `getFactionLabels()` on `OwnerMapCache` and `OwnerMapDrawables`, `OwnerMapBuildInputs.resolveBlocPaintOf(String, SystemOwner)` and `OwnerStyleResolver.resolveBlocStyleDecision(String, OwnerReading, OwnerCategories, ContentInputs)` | `getOwnerLabels()`, `MapPaints.resolveOwnerPaintOf(SystemOwner)` and `resolveOwnerStyleDecision(String, OwnerReading, OwnerCategories, ContentInputs)`. The owner's ID is the `SystemOwner`'s own |
| `KmuStringKeys.OWNER_MAP_CTL_FACTION_NAMES` and its `strings.json` key | `KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_FACTION_NAMES`, the political map's own caption |
| The nine `KmuStringKeys.OWNER_MAP_TOOLTIP_*` constants and their `strings.json` keys | `KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_*`: only the political map's boxes read them |
| `ownermap.owners.OwnerPalette` | KMLib's `kmlib.colour.ColourPair`, which a faction's pair already is. `SectorBlocPalettes.adoptFactionPalette(FactionPalette)` is gone |
| `HolderPass.over(SectorAPI, ColonyReadRules, HolderGrouping)` | `HolderPass.createOver(SectorAPI, ColonyReadRules, HolderGrouping)` |
| `SystemColonyReading.readColoniesIn(SectorAPI, StarSystemAPI, Colonies, ColonyKnowledge)` | `readColonyReadingOf(SectorAPI, StarSystemAPI, Colonies, ColonyKnowledge)` |
| `SharedBlocSortModes.declareMarketSizeMode(List)` | `SizedBlocMetrics.createMarketSizeMode(List)` |
| `SidebarStyles.buildSidebarSoundScheme()` | `SidebarSounds.buildSoundScheme()` |
| `MapPalettes.pickHolderPaletteColour(ElementPaintSelection, SystemOwner, Color)` | `pickOwnerPaletteColour(ElementPaintSelection, SystemOwner, Color)` |
| `OwnerPresence`'s `marketCount`, with `marketCount()` and `hasMarkets()`, and `RibbonSegmentLengths.marketLengthUnits()` | `stakeCount`, `stakeCount()`, `hasStakes()` and `stakeLengthUnits()`. The owner-map core no longer says *holder* or *market*, and the build holds it to that |
| `SystemDominance.resolveDominantFactionId(Map, HolderRankingRules)` | `resolveDominantBlocId(Map, HolderRankingRules)`: it ranks blocs |

##### Seams

| Before | After |
| --- | --- |
| `OwnerPaintedView`'s `resolveBlocPickerRead(SectorAPI, ColonyReadRules)`, `buildBlocPickerRead(SectorAPI, HolderGrouping, OwnerReading, BlocStatsRead, ListSortModes)` and `resolveSelectableBlocGate(HolderGrouping)` | On `HolderPaintedView`, where `buildBlocPickerRead` is `readPickerUnderOneGrouping(SectorAPI, Function, ListSortModes)`: it samples the grouping once and names the rows through the view's own reading. `OwnerPaintedView` keeps `resolveOwnerPickerRead(SectorAPI)`, empty by default |
| `HolderPaintedView.resolveContestGrouping()` | `resolveContestAffiliation()`, answering an `OwnerAffiliation` |
| `RibbonPlan.planCellRibbon(Optional, List, BlocAffiliation, RibbonPlanRules)` over a `BlocAffiliation` | `ownermap.ribbon.OwnerRibbonRuns.planCellRibbon(Optional, List, OwnerAffiliation, RibbonPlanRules)`, over `kmu.maplayers.ownermap.owners.OwnerAffiliation`, which `BlocAffiliation` implements. The plan it answers is `base.ribbon.RibbonPlan` |
| `ClusterAnchorsBuilder.rebuildClusterAnchors(StandingClusterAnchors, RevisedCellGeometry, ClusterLabelStylingSnapshot)` and `rebuildDiagnosticClusterAnchors` over the same three | `rebuildClusterAnchors` over a `base.labels.anchor.ClusterAnchorFitInputs` in place of the snapshot, which `ClusterLabelStylingSnapshot.createFitInputs()` answers: the keys, the face, whether a fit is wanted, and the resolver pair as a function of the face the fit measures in. `rebuildDiagnosticClusterAnchors` is gone: the tracing view calls `rebuildClusterAnchors`, its snapshot wanting a fit only while the anchor overlay is shown |
| `SpotlitBlocs.findPresentSystemKeys(HolderPass, String, Set)` and `isBlocPresentIn(HolderPass, String, StarSystemAPI)` | `HolderInhabitation.findSpotlitPresenceSystemKeys(HolderPass, String, Set)` and `HolderInhabitation.isBlocPresentIn(HolderPass, String, StarSystemAPI)` |
| `HolderOwnerSource(HolderGrouping, HolderGrouping, HolderProvider, SystemHolderResolveSource, Function)` | `HolderOwnerSource(HolderGrouping, HolderPaintedView)`: the view answers the rest |
| `HolderOwnerSource.grouping()`, `BlocAffiliation.NONE` and `BlocAffiliation.areBlocsAllied(String, String)` | `grouping()` is gone: the view's `resolveGrouping()` names it. For `NONE`, `OwnerAffiliation.NONE` or `new BlocAffiliation(FactionAlliances.NONE)`. `areBlocsAllied` is `areOwnersAllied` |
| `HolderGrouping.collectBlocIds(Collection)` | Gone |
| `HolderPass.readKnownColoniesIn(StarSystemAPI)` and `readInhabitingColoniesIn(StarSystemAPI)`, public | Package-private. A reader outside it takes `readHabitationIn(StarSystemAPI)` or `readKnownColonyFactionIds(StarSystemAPI)` |
| `AllianceBodyControls` | Gone: `AlliancesView.getViewBodyControls(BodyControlTarget)` binds the shared recede control itself |
| `SystemColonyReading`'s `readKindOf(String)`, `isDiscoveredColony(String)`, `isOpenlyKnownColony(String)`, `readConcealmentOf(String, boolean)`, both `readQualifierFacts` overloads and `describeColony(CellTooltipEntryLine, String, ColonyQualifierFacts)` | `describeColony(CellTooltipEntryLine, AccountedColony, String)`, the colony built by `AccountedColony.createEconomyListedColony(String, boolean)` or `createOffEconomyColony(String, boolean)`. A line led by no finding takes `describeColony(CellTooltipEntryLine, AccountedColony)`, and a caller already holding the kind passes it to `describeColony(CellTooltipEntryLine, AccountedColony, ColonyKind)` |
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
| `DebugBorderTracingBuilder.buildDebugDrawables(CellGeometryCache, ResolvedOwners, OwnerCategories, ContentInputs)` and `ClusterLabelStylingSnapshot.resolveForTracing(Map, ViewReading, ContentInputs, FontAtlas)` | `buildDebugDrawables(CellGeometryCache, ResolvedOwners, MapStyling)` and `resolveForTracing(Map, MapStyling, OwnerReading, ContentInputs, boolean, FontAtlas)`, both handed the one `MapStyling` the tracing rebuild reads. The snapshot also takes whether the anchor overlay is shown, as the rebuild sampled it. The snapshot's `desaturationPalette` and `categories` components are its `paints` |
| `RibbonPlanRules(RibbonSegmentLengths, UncontestedRibbonRuns)`, a record with `lengths()`, `uncontestedRuns()` and `resolveUncontestedLengths()`, and `UncontestedRibbonRuns` | A final class made only by `RibbonPlanRules.createFromAuthoredLengths(RibbonSegmentLengths, boolean)`, the switch saying whether an uncontested cell's stake runs are cut to one width. Its reads are `getContestedLengths()` and `getUncontestedLengths()`. `UncontestedRibbonRuns` is gone |
| `RibbonStyle(double, double, double, RibbonSegmentLengths, boolean)` and `RibbonStyleReader.readRibbonStyle()` | `RibbonStyle(double, double, double, boolean)`: the run lengths are the plan rules'. `ownermap.render.ribbon.RibbonBakeSettings.readFromLunaSettings()` reads every band knob in one go, the style among them |
| `CellRibbonsBaker.createForPass(OwnerMapClusters, CellGeometryCache, SectorWalk, StandingClusterAnchors)`, `CellRibbonSource.createForPass(SectorWalk, OwnerSource, RibbonBakeSurface)` and `RibbonBakeSurface.createForPass(OwnerMapClusters, CellGeometryCache, StandingClusterAnchors)`, each reading the settings it needs | `CellRibbonsBaker.createForPass(OwnerMapClusters, CellGeometryCache, SectorWalk, StandingClusterAnchors, RibbonBakeSettings)`, handed the knobs its caller read. `CellRibbonSource.createForPass` also takes the `RibbonStyle` and the `RibbonPlanRules`, and `RibbonBakeSurface.createForPass` whether the bands keep clear of the names and the `RibbonNameClearanceChoice`. A pass with the bands switched off builds neither |
| `CellRibbonBuilder.buildCellRibbon(RingPath, RibbonPlan, RibbonStyle, List, IterationScope)` and `RibbonPathTracer.traceLaidRibbonPath(List, double[], RibbonStyle)`, each a step a layer called in turn | `CellRibbonBuilder.layCellRibbon(SystemKey, List, double[], RibbonPlan, RibbonStyle, List, CellRingPathCache, IterationScope, RibbonBakeTally)`: one call lays a cell's band, answering an empty plan before any ring is walked and reaching for the ring through the kept rings. It marks the trace, carve and stroke phases, so `RibbonBakePhases` leaves a layer only `BAKE_SECTION` and `PLAN_PHASE`, and counts the cell on its bake call's tally |
| `CellRibbonSource.buildCellRibbon(SystemKey, SystemKey, List, IterationScope)` | Also takes the `RibbonBakeTally` its bake call counts on. A cell refused before planning is not counted |
| `ClusterAnchorPlacement.ClusterAnchorFit(List, int, int)` | `ClusterAnchorFit(List, int, int, int)`: the placements carried over unsearched come before the two cost counts |
| `RibbonPathTracer.traceInspectedRibbonPath(List, double[], RibbonStyle)` and `CellRibbonSource.traceCellRibbonPath(SystemKey, List)` | `traceInspectedRibbonPath(SystemKey, List, double[], RibbonStyle, CellRingPathCache)` and `traceCellRibbonPath(SystemKey, SystemKey, List)`, the cell's key first: the overlay reads the rings the band pass walked |
| `CellRingPathCache`'s `findRingPathOf(SystemKey)`, `putRingPath(SystemKey, RingPath)` and `dropRingPathOf(SystemKey)` | `findRingPathOf(SystemKey, RibbonPathInset)`: a cell keeps one ring per inset it was traced at. The writes are the band's own |
| `PaintedCellStore`'s `getRibbonByCellKey()`, `getRibbonPathByCellKey()`, `getRingPathCache()`, `putCellRibbon(SystemKey, CellRibbon)` and `putCellRibbonPath(SystemKey, CellRibbonPath)` | On the `base.ribbon.CellRibbonStore` that `PaintedCellStore.getRibbonStore()` answers, whose `dropRingFittedWorkOf(SystemKey)` clears a cell's band, path and rings together |
| `OwnerMapBuildInputs`, a record over the scheme, the view reading, the picks and the two fill sets | A final class over the scheme, the view reading, the picks, whether the anchor overlay is shown and a `SystemFillStates`, holding the build's `MapPaints` and comparing by identity. Its reads are `getStyling()`, `getViewReading()`, `getContentInputs()`, `isAnchorOverlayShown()`, `getFillStates()` and `getPaints()` |
| `contestedSystemKeys` and `unfilledSystemKeys` on `HolderResolution` and `ResolvedOwners`, `FillSplit.splitMembersByFillState(CellGrouping, List, Set, Set)` and `FillSplit.classifyFillState(SystemKey, Set, Set)` | One `SystemFillStates fillStates`, so no system is both hatched and unfilled. The split takes it as `splitMembersByFillState(CellGrouping, List, SystemFillStates)`, and one system's state is `resolveFillStateOf(SystemKey)` |
| `ClusterLabelStylingSnapshot(Map, MapStyling, OwnerReading, ContentInputs, FontAtlas)` | `ClusterLabelStylingSnapshot(Map, MapPaints, FontAtlas, boolean)`: a production snapshot carries the build's own paints, so a name is coloured off the style decision its fill was painted from. The last component is whether a fit is wanted, which `resolveFrom` answers off the build's names and anchor overlay |
| `OwnerMapClusters.reindexClusters(Map, Map)` | `reindexClusters(Map, CellGrouping)`, over the grouping the caller has just composed |
| `PaintedCellBuilder.buildPaintedCellForSystem(OwnerMapClusters, SystemKey, ShapedCell)` | `new PaintedCellBuilder(MapPaints, SystemOccupancy)`, then `buildPaintedCell(SystemKey, ShapedCell)`: the builder stands up on the paints and the `SystemOccupancy`, with no build around it. `OwnerMapClusters.createPaintedCellBuilder()` makes one over a build's own |
| `ClusterGroupBuilder.buildClusterGroup(OwnerMapClusters, CellGeometryCache, CellGrouping, String, List)` and `buildAllClusterGroups(OwnerMapClusters, CellGeometryCache, CellGrouping)` | `new ClusterGroupBuilder(MapPaints, CellGrouping, Map, SystemFillStates)`, then `buildClusterGroup(SystemOwner, List)`: the owner is named whole rather than by an ID beside it. `OwnerMapClusters.createClusterGroupBuilder(CellGrouping, Map)` makes one over a build's own, and `OwnerMapBuilder` builds every owner's group |
| `MapStyling(RenderStyle, OwnerCategories, ColourPair, ColourPair, ColourPair)` | `MapStyling(RenderStyle, ClusterBorderTrace, OwnerCategories, ColourPair, ColourPair, ColourPair)`: the build's border trace rides with its theme, so a cluster group is never traced under a live read of its own |
| `OwnerMapBuilder.buildClusters(CellGeometryCache, ViewReading, ContentInputs, ResolvedOwners)`, reading the styling itself | `buildClusters(CellGeometryCache, ViewReading, ContentInputs, boolean, ResolvedOwners, MapStyling)`: the rebuild reads the styling once for everything it builds, and samples whether the anchor overlay is shown beside the picks |
| `RibbonPlanInputs(HolderPass, BlocPaletteReader, OwnerAffiliation, RibbonPlanRules)` and `RibbonPlanInputs.createForPass(HolderPass, OwnerAffiliation, RibbonPlanRules)` | `RibbonPlanInputs(HolderPass, OwnerAffiliation, RibbonPlanRules)`. `palettes()` answers the pass's own, so a band cannot be coloured through anything the fills were not |
| `HolderRankingRules.createByLowestId(Predicate)` and `createOpenContestByLowestId()` | Gone: every live ranking breaks a tie by proximity through `createByMarketProximity`. A reader with no geometry builds `new HolderRankingRules(Comparator.naturalOrder(), candidacy)` |
| `KmuErrorReporter.noop()` | `KmuErrorReporter.createLoggingReporter(Class)`: a caught failure is always written somewhere |
| `StarsectorMarketUiContextTracker` extending `BaseCampaignEventListener` | Implementing `ColonyInteractionListener`, the interface the listener manager delivers market events to |
| `kmu.mods.nexerelin.alliances.AllianceFingerprint.compute(List)` and `NexerelinAlliances.computeAllianceFingerprint()` | Gone: `PoliticalMapStalenessSource` compares the alliance records themselves between polls. `kmu.mods.nexerelin.alliances` is gone |
| `PoliticalMapLayer(List, OwnerPaintedView)`, `PoliticalMapStanding(MapLayerViewRegistry)`, `PoliticalMapInstaller.installAll(SectorAPI, MapLayerViewRegistry)` and `PoliticalMapStalenessSource(SectorMapMachinery)` | Each also takes the `AllianceSource` the poll reads, which `MapLayers` binds |
| `BlocAffiliation(HolderGrouping)` | `BlocAffiliation(FactionAlliances)`: who stands together is read off the alliance memberships, never off a paint grouping |
| `SystemDominationTooltip` and `SystemClaimTooltip` constructors taking the alliance set as a `HolderGroupingSource` | Taking a `PoliticalBoxSeams`, whose `FactionAllianceSource` is asked about the hovered sector |
| `PoliticalMapCellTooltip(ClaimDecreeReader, FactionAllianceSource, ContestWordingSource)`, `VANILLA_CLAIM_DECREE_READER` and the protected `claimDecreeReader` | `PoliticalMapCellTooltip(PoliticalBoxSeams)`. A running game's boxes take `PoliticalBoxSeams.LIVE`; the seams are private to the box |
| `BlocRelations.affiliation()` and `friendliness()` | `blocAffiliation()` and `factionSetFriendliness()`. A box asks the pair through `selectSide(ContestSide, String, List, Function)` and `isFactionFriendlyWith(String, Set)` |
| `StandingBlockRules(Predicate, BlocAffiliation, FactionSetFriendliness, Function)` | `StandingBlockRules(Predicate, BlocRelations, Function)` |
| `ColonyKindLookup`, `ColonyDiscoveryLookup` and `OpenlyKnownColonyLookup` | One `ColonyFactsLookup`, folded in one walk by `readFactsIn(Colonies, ColonyKnowledge)` |
| `ColonySightings` and `StructureObservations` | KMLib's `RecordedObservations<ColonyObservation>` and `RecordedObservations<StructureObservation>`, answering an `Optional`. `ColonyKnowledge`'s constructors take the former |
| `DominanceStatsRead` and `ClaimStatsRead` | One `ownermap.picker.OwnerStatsRead<S>` record. An empty read is `OwnerStatsRead.createEmptyRead()` |
| `BorderTraceStyle(EdgeInsetRule, double, double, double)` | `BorderTraceStyle(EdgeInset, double, double)`: the trace carries the same `EdgeInset` the cells are shaped by |
| `KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_SORT_NAME` and its `strings.json` key | `KmuOwnerMapStringKeys.OWNER_MAP_CTL_SORT_NAME`. A vocabulary's name mode is `OwnerMetricSortMode.createNameMode(List)` |
| `FactionAllianceSource.readAlliances()`, `FactionAllianceRegistry.readAlliances()` and `ColonyKnowledge.observingUnderTheFog()` | Each takes the `SectorAPI` whose alliances are asked for |
| `StarsectorMarketUiContextResolver()`, `StarsectorInteractionDialogPickerOpener()` and `StarsectorConditionPickerFeedbackSink()`, reading the running game's sector | Gone: each takes the `SectorAPI` the console command resolves once |
| `KmuConditionPickerEditor(KmuConditionService, KmuConditionPickerDialogOpener)`, its three-argument form, and `KmuConditionPickerDialogDelegate(KmuConditionPickerActionHandler)` and `(KmuConditionPickerActionHandler, float, float)` | Each also takes the `KmuConditionPickerFeedbackSink` a picker reports to |
| `MapHover(SystemKey, List)` | `MapHover(SystemKey, StarSystemAPI, List)`: the hover carries the live system its key named when the cursor reached the cell |
| `MapHoverPublisher(MapHoverState, ModelviewMatrixReader, CellArrivalAnnouncer, BooleanSupplier)` | Also takes the `Function<SystemKey, StarSystemAPI>` it finds the hovered system with, asked once per cell reached |
| `ClusterDrawLists.getStyledCellByCellKey()` | `getFusedCells()` and `getLoneCells()`, parted once per rebuild. `OwnerMapClusters` and `PaintedCellStore` keep `getStyledCellByCellKey()`, now read-only: write through `putPaintedCell` and `removePaintedCell` |
| `BaseSidebarHost` | A host implements `composeTabStyle()` and reads `resolveTabStyle()`, which keeps the style until the settings revision or the sector's settled faces move |
| `resolveDeepestHeldLevelFor(SectorAPI, StarSystemAPI)` on `SystemCellTooltip`, `PoliticalMapCellTooltip`, `SystemClaimTooltip` and `SystemDominationTooltip` | Gone: the press reads `deepestHeldLevel()` off the composed body the paint holds, so a layer states its depth only in `composeBody` |
| `SystemStandings.rankByDominationScore(StarSystemAPI, DominancePass)` | `rankByDominationScore(StarSystemAPI, DominancePass, Map)`, over the weights the caller read, so a box ranking a system and explaining its scores weighs each market once |
| `CellTooltipEntryLine`'s twelve-part constructor, with `labelText()`, `redactedWordLengths()`, `labelFinding()` and `hasRedactedName()` | `CellTooltipEntryLine(CellTooltipMark, CellTooltipLineName, CellTooltipIndexPlace, String, CellTooltipQualifier, CellTooltipLineValue, boolean)`. The name is a sealed `CellTooltipLineName`, `Spoken` or `Withheld`, read through `selectByCase(Function, Function)`, so no line both says and withholds its name |
| `valueText()`, `countedValue()`, `valueWorkingText()` and `isValueUncounted()` on `CellTooltipEntryLine` | The same four on `value()`, a `CellTooltipLineValue` |
| `MapLayerRegistry`, `LiveMapLayerArrangement` and `MapLayerScreens`, static, with their reset hooks | Instances `MapLayers.registerAll()` builds, returning the `MapLayerScreens` built over the other two. The entry point seats them on each sector through `SectorMapMachineryIndex.installMachineryOn(SectorAPI, InstalledMachinery...)`, and a pass reads them with `MapLayerScreens.resolveScreensIn(SectorMapMachinery)` |
| `MapLayerRegistry.getDrawnLayer()`, `isDrawnLayer(MapLayer)` and `isDrawnLayerOn(ScreenLayerPicks, MapLayer)` | `MapLayerScreens.resolveDrawnLayer()` and `ScreenLayerPicks.isDrawingLayer(MapLayer)` |
| `MapLayerScreens.registerIntelScreen(IntelScreenView)` and `LiveMapLayerArrangement.registerArrangementSelection(MapLayerArrangementSelection)` | Constructor arguments: `MapLayerScreens(MapLayerRegistry, LiveMapLayerArrangement, IntelScreenView)` and `LiveMapLayerArrangement(MapLayerArrangementSelection)` |
| `ScreenLayerPicks(ActiveLayerSelection, ControlBackedMapLayerVisibility, ScreenMemoryScope)` and `PersistedActiveLayerSelection(ScreenMemoryScope)` | Each also takes the `MapLayerRegistry` its screen picks from, and the picks the `LiveMapLayerArrangement` too |
| `BaseSidebarHost(ScreenLayerPicks, boolean, ScreenClaim)` and `BaseSidebarHost(SidebarFoldSelection, ScreenLayerPicks, ScreenClaim)` | `BaseSidebarHost(ScreenMemoryScope, boolean, ScreenClaim)`, which reads that screen's picks off the live sector, and `BaseSidebarHost(SidebarFoldSelection, Supplier, ScreenClaim)` |
| `MapLayerArrangementDialog.openDialog()` and `BarOpeners.buildOpenerSpec(TabStyle)` | `openDialog(ScreenLayerPicks)` and `buildOpenerSpec(TabStyle, ScreenLayerPicks)`: the picks carry the roster and the store |
| `SplitFillBuilder.ClusterFill` and `SplitFillBuilder.traceFill(boolean, FillSplit, String, Color)` | `TracedFill.ClusterFill` and `traceFill(boolean, FillSplit, String)`. A fill set to no colour takes `TracedFill.UNPAINTED` without being traced |
| `KmuConditionPickerEntry`'s constructors | `KmuConditionPickerEntry.Builder(String, String, KmuConditionPickerEntryState)`, a method per optional part, then `build()` |
| `KmuConditionPickerLocation(String, String, KmuPickerFaction, String, String, String, String)` | `KmuConditionPickerLocation.Builder()`, or `createUnknownLocation()` for a location nothing is known of |
| `StarsectorEditableMarket.getMarket()`, reached by a cast | `KmuEditableMarket.findStarsectorMarket()` and `findCondition(String)`, which every implementation of the port provides |

##### Moved to KMLib

Code that knows nothing of KMU now lives in KMLib, where any mod built on it can reach it.

| Before | After |
| --- | --- |
| `kmu.KmuToggledFeature` | `kmlib.starsector.startup.ToggledFeature` |
| `ObservationStore`, `ObservationCodec` and `RecordedObservations` in `base.visibility.observations` | `kmlib.starsector.memory.observations` |
| `kmu.starsector.listeners.MarketTransferListener` | `kmlib.starsector.listeners.MarketTransferListener` |
| `kmu.mods.nexerelin.NexerelinMarketTransferRelay` and `kmu.mods.nexerelin.NexerelinInvasionListenerInstaller` | Gone: KMLib puts its own relay on each loaded sector where Nexerelin is enabled |
| `base.render.ForeignMapPassWarning(BooleanSupplier, Supplier, Logger)` | `kmlib.starsector.ui.map.probes.ForeignMapPassWarning(BooleanSupplier, Supplier, Logger, String, String)`, handed its two messages |
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
| None | `PassClaimReaders.openBreakdownReaderOver(HolderPass, ClaimBreakdownReaderSource)` and `openBreakdownReaderOver(SectorPassIndex, ColonyVisibility, ClaimBreakdownReaderSource)` |
| `ClaimsHolderProvider.INSTANCE`, `ClaimAugmentedHolderProvider.INSTANCE` and `SystemClaimTooltip.INSTANCE` | Built by each view from the one KMLib `ClaimBreakdownReaderSource` it reads claims through: `new ClaimsHolderProvider(ClaimReaderSource)`, `ClaimAugmentedHolderProvider.createReadingThrough(ClaimReaderSource)` and `SystemClaimTooltip.createReadingThrough(ClaimBreakdownReaderSource)`. `DominancePaintedView.resolveClaimReaderSource()` states the Factions and Alliances views' source. |
| `ClaimedSystemRibbonPlanner.createForPass(RibbonPlanInputs)` and `HeldOrClaimedSystemRibbonPlanner.createForPass(RibbonPlanInputs)` | Take the view's `ClaimBreakdownReaderSource` |
| `PoliticalMapCellTooltip.VANILLA_CLAIM_BREAKDOWN_READER` and its `claimBreakdownReader` | `VANILLA_CLAIM_DECREE_READER` and `claimDecreeReader`, a KMLib `ClaimDecreeReader`; `SystemDominationTooltip`'s constructors take one |
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
