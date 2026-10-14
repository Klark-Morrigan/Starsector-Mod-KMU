# 更新日志

本文件记录 KMU 的所有重要变更。格式遵循 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## 索引

- [未发布](#unreleased)
- [0.2.1](#021---2026-10-06)
- [0.2.0](#020---2026-10-05)
- [0.1.2](#012---2026-09-16)
- [0.1.1](#011---2026-09-15)
- [0.1.0](#010---2026-09-14)

## [Unreleased]

### 修复

- **聚焦某个势力时，政治地图会跟随殖民地易手**，而不再显示旧的所有者，直到你移动聚焦。
- **聚焦选择器也会跟随殖民地易手**，因此其中的数字与地图一致。
- **两个势力权重相同的星系在其中一方取得它时会重绘**，而不必等到下一次完整重绘。
- **市场条件管理器 (MCM) 不再在你已离开的殖民地上打开。**

### 变更

- **小幅优化**。政治地图重绘时减少了重复工作：势力配色、每个所有者的绘制方式和边界设置在每次重绘时只读取一次，而不再按星系或单元逐一读取。

### 面向开发者

<details>
<summary>内部实现与地图图层框架</summary>

#### 新增

- **`ColonyKind.isUngoverned()`**：某处是否住着不受任何权力管辖的人，供每个区分有人管辖与无人管辖殖民地的读取方使用。
- **`FactionStanding.isWeighed()`**：本次计算是否为该势力的殖民地计算过权重，按种类分支的读取方据此询问，而不必检查密封类型。
- **`FactionTooltipLine.resolveCrestMark(SectorAPI, String)`**：势力所在行开头的标记，供标记为该势力的行使用。
- **`HolderRankingRules.createByMarketProximity(StarSystemAPI, Colonies, ColonyKnowledge, HolderGrouping)`**：实际排名规则唯一的构建处，包括对中立势力的排除和按市场远近的平局裁定。地图填充和刷新轮询都按它排名。
- **`HolderPass.palettes()`** 和 **`DominancePass.palettes()`**：本次计算唯一的配色读取，会为本次计算记住每个集团的所有者和配色，因此一次重建中的每个解析和每条色带都通过同一个对象为集团着色。
- **`KmuErrorReporter.createLoggingReporter(Class)`**：把每个被捕获的失败连同其原因以警告级别写入报告方所属类的日志。
- **`MapLayerRefreshBoard.getGroupingRevision()`**：星系被标记为过期的次数，任何取出操作都不会让它回退。无法取出该集合的读取方（例如侧边栏选择器的缓存）以它为键。
- **`MapStyling.readFromLunaSettings(OwnerCategories, OwnerReading, ContentInputs)`**：一次构建的绘制方案，只读取一次，再交给构建中按它着色的每个部分。
- **`MapStyling.resolveUnownedStyle(boolean)`**：无所有者单元所用的样式，使地图填充与边界描绘以同一方式查找它。
- **`PoliticalHolderView`**，政治地图的每个视图都实现它：联盟集合的修订号（作为视图的内容修订号）、争夺中谁与谁站在一起、持有者类别，以及势力读取。
- **`SmoothingStageSink`** 和 **`BorderSmoothing.smoothBorderLoops(List, BorderSmoothingStyle, SmoothingStageSink)`**：平滑会报告实际运行的每一道平滑处理，因此展示各阶段的调用方按地图绘制时的顺序和开关展示它们。`ClusterBorderStageCollector` 即为其一。

#### 修复

- **政治地图*宣称*视图的悬停框读取交给它的星域**：状态行与宣称机制共用一次扫描，而不再让宣称读取正在运行的游戏中的星域。
- **市场条件管理器 (MCM) 会把它捕获后继续执行的失败写入日志**，此前这些失败会被丢弃。

#### 公共契约变更（**破坏性**）

以下变更都不影响玩家：每个 LunaLib 字段 ID 和保存在星域内存中的每个值都保持原有拼写，因此设置和存档可原样沿用。它们影响的是基于 KMU 框架构建地图图层的 Mod。

##### 包

所有者地图层的核心对所有者是什么一无所知。绘制持有者的图层所共享的部分位于 `kmu.maplayers.ownermap.owners.holders` 下：`base` 用于各类持有者，其选择器位于 `base.picker`；`factions` 把一个势力作为一个所有者，`alliances` 把一组势力作为一个所有者。核心不得导入该子树。

| 之前 | 之后 |
| --- | --- |
| `ownermap.holding`、`ownermap.owners.holders`、`RibbonPlanInputs`、`BlocPaletteReader`、`SectorBlocPalettes`、`HolderCategories`、`OwnerMapCategory` 和 `ColonyCellRibbons` | `ownermap.owners.holders.base`，下列类型除外 |
| `BlocPickerAssembly`、`BlocStanding`、`BlocStandingReader`、`BlocStandingSortMode` 和 `SizedBlocMetrics` | `ownermap.owners.holders.base.picker` |
| `SystemStatusRow` 以及 `ownermap.tooltip` 中的殖民地行 | `politicalmap.tooltip`，与读取它们的政治地图悬停框放在一起 |
| `ownermap.tooltip` 中的势力行 | `ownermap.owners.holders.factions` |
| `ownermap.tooltip.TermTooltipLine` | `base.tooltip.content.TermTooltipLine`。`ownermap.tooltip` 已移除 |
| `ownermap.sidebar.BodyControlTarget` | `base.sidebar.BodyControlTarget` |
| `ownermap.render.clusters.MapStyling` | `ownermap.render.style.MapStyling`，与读取它所经由的主题读取器放在一起 |
| `BlocAffiliation`、`ContestSide`、`ContestSides` 和 `BlocRelations` | `ownermap.owners.holders.alliances` |
| `BlocFriendliness` 及其 `areBlocsFriendly(Set, Set)` | KMLib 的 `kmlib.starsector.factions.relation.FactionSetFriendliness`，及其 `areSetsFriendly(Set, Set)` 和 `createForSector(SectorAPI)` |
| `base.labels.anchor.DiagnosticPalette` | `base.render.DiagnosticPalette`，供标签与区域的调试显示共用 |
| `base.refresh.MapSubstrateStalenessSource` | `base.visibility.colonies.ColonySightingsPoll`，不可继承，与它写入的登记放在一起。其扫描为 `recordSightingsByInhabitants()`，不再实现 `MapLayerStalenessSource` |
| `politicalmap.dominance.standings.StandingFraction` | `politicalmap.dominance.tooltip.StandingFraction`，与读取它的悬停框放在一起 |
| `kmu.mods.nexerelin.alliances.AllianceGroupingFactory` | `ownermap.owners.holders.alliances.AllianceGroupingFactory`：它不读取任何 Nexerelin 类型 |
| `kmu.ui.context` 和 `kmu.ui.geometry.KmuUiSize` | `kmu.conditions.ui.context` 和 `kmu.conditions.ui.picker.render.KmuUiSize`，位于它们唯一服务的功能中。`kmu.ui` 已移除 |
| `KmuStringKeys` 的字符串 ID | `kmu.util` 中每个读取方一个键类：`KmuMapStringKeys`、`KmuOwnerMapStringKeys`、`KmuPoliticalMapStringKeys` 和 `KmuMarketConditionStringKeys`。`KmuStringKeys` 保留字符串类别、`get(String)`、`format(String, Object...)` 和 LunaLib 设置的一对键 |

构建禁止 `kmu.maplayers.base` 导入 `kmu.mods`，禁止 `kmu.conditions` 与 `kmu.maplayers` 相互导入，并禁止 `kmu.settings` 和 `kmu.util` 导入这两个功能。

##### 重命名

| 之前 | 之后 |
| --- | --- |
| `SelectableBloc`、`SelectableBlocCache`、`RankedBloc`、`BlocMetrics`、`PaintingBlocMetrics`、`BlocMetricSortMode`、`BlocSortModeComposer`、`BlocPickerRead`、`BlocPresenceIndex`、`BlocPresenceIndexBuilder`、`BlocStatsRead`、`SpotlitBlocs`、`ResolvedBlocPaint`、`BlocNameStyles` 和 `BlocPresence` | 各名称及其成员中的 `Bloc` 换为 `Owner` |
| `FactionNameFormatChoice`、`FactionPaletteSlot`、`FactionlessStyleResolver` 和 `kmu.settings.FactionPaletteChoice` | `OwnerNameFormatChoice`、`OwnerPaletteSlot`、`UnownedStyleResolver` 和 `OwnerPaletteChoice` |
| `HolderOwnerReading` | `FactionOwnerReading`，以及为绘制联盟的图层建立在其上的 `AllianceOwnerReading` |
| `OwnerMapCategory`、`OwnerMapInhabitation` 和 `politicalmap.holders.DefaultHolderProvider` | `HolderCategory`、`HolderInhabitation` 和 `DominanceHolderProvider` |
| `OwnerMapCache` 与 `OwnerMapDrawables` 的 `getFactionLabels()`、`OwnerMapBuildInputs.resolveBlocPaintOf(String, SystemOwner)` 和 `OwnerStyleResolver.resolveBlocStyleDecision(String, OwnerReading, OwnerCategories, ContentInputs)` | `getOwnerLabels()`、`resolveOwnerPaintOf(SystemOwner)` 和 `resolveOwnerStyleDecision(String, OwnerReading, OwnerCategories, ContentInputs)`。所有者的 ID 取自 `SystemOwner` 本身 |
| `KmuStringKeys.OWNER_MAP_CTL_FACTION_NAMES` 及其 `strings.json` 键 | `KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_FACTION_NAMES`，政治地图自己的标题 |
| 九个 `KmuStringKeys.OWNER_MAP_TOOLTIP_*` 常量及其 `strings.json` 键 | `KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_*`：只有政治地图的悬停框读取它们 |
| `ownermap.owners.OwnerPalette` | KMLib 的 `kmlib.colour.ColourPair`，势力的配色本就是它。`SectorBlocPalettes.adoptFactionPalette(FactionPalette)` 已移除 |
| `HolderPass.over(SectorAPI, ColonyReadRules, HolderGrouping)` | `HolderPass.createOver(SectorAPI, ColonyReadRules, HolderGrouping)` |
| `SystemColonyReading.readColoniesIn(SectorAPI, StarSystemAPI, Colonies, ColonyKnowledge)` | `readColonyReadingOf(SectorAPI, StarSystemAPI, Colonies, ColonyKnowledge)` |
| `SharedBlocSortModes.declareMarketSizeMode(List)` | `SizedBlocMetrics.createMarketSizeMode(List)` |

##### 接缝

| 之前 | 之后 |
| --- | --- |
| `OwnerPaintedView` 的 `resolveBlocPickerRead(SectorAPI, ColonyReadRules)`、`buildBlocPickerRead(SectorAPI, HolderGrouping, OwnerReading, BlocStatsRead, ListSortModes)` 和 `resolveSelectableBlocGate(HolderGrouping)` | 位于 `HolderPaintedView`，其中 `buildBlocPickerRead` 改为 `readPickerUnderOneGrouping(SectorAPI, Function, ListSortModes)`：只对分组采样一次，并通过视图自身的读取为各行命名。`OwnerPaintedView` 保留 `resolveOwnerPickerRead(SectorAPI)`，默认为空 |
| `HolderPaintedView.resolveContestGrouping()` | `resolveContestAffiliation()`，返回 `OwnerAffiliation` |
| 基于 `BlocAffiliation` 的 `RibbonPlan.planCellRibbon(Optional, List, BlocAffiliation, RibbonPlanRules)` | 基于 `kmu.maplayers.ownermap.owners.OwnerAffiliation`，由 `BlocAffiliation` 实现 |
| `SpotlitBlocs.findPresentSystemKeys(HolderPass, String, Set)` 和 `isBlocPresentIn(HolderPass, String, StarSystemAPI)` | `HolderInhabitation.findSpotlitPresenceSystemKeys(HolderPass, String, Set)` 和 `HolderInhabitation.isBlocPresentIn(HolderPass, String, StarSystemAPI)` |
| `HolderOwnerSource(HolderGrouping, HolderGrouping, HolderProvider, SystemHolderResolveSource, Function)` | `HolderOwnerSource(HolderGrouping, HolderPaintedView)`：其余部分由视图给出 |
| `HolderOwnerSource.grouping()`、`BlocAffiliation.NONE` 和 `BlocAffiliation.areBlocsAllied(String, String)` | `grouping()` 已移除，改由视图的 `resolveGrouping()` 给出。`NONE` 改用 `OwnerAffiliation.NONE` 或 `new BlocAffiliation(HolderGrouping.identity())`。`areBlocsAllied` 改为 `areOwnersAllied` |
| `HolderGrouping.collectBlocIds(Collection)` | 已移除 |
| 公开的 `HolderPass.readKnownColoniesIn(StarSystemAPI)` 和 `readInhabitingColoniesIn(StarSystemAPI)` | 改为包私有。外部读取方改用 `readHabitationIn(StarSystemAPI)` 或 `readKnownColonyFactionIds(StarSystemAPI)` |
| `AllianceBodyControls` | 已移除：`AlliancesView.getViewBodyControls(BodyControlTarget)` 自行绑定共享的退后控件 |
| `SystemColonyReading` 的 `readKindOf(String)`、`isDiscoveredColony(String)`、`isOpenlyKnownColony(String)`、`readConcealmentOf(String, boolean)`、两个 `readQualifierFacts` 重载和 `describeColony(CellTooltipEntryLine, String, ColonyQualifierFacts)` | `describeColony(CellTooltipEntryLine, AccountedColony, String)`，殖民地由 `AccountedColony.createEconomyListedColony(String, boolean)` 或 `createOffEconomyColony(String, boolean)` 构建。已持有种类的调用方将其传给 `describeColony(CellTooltipEntryLine, AccountedColony, ColonyKind, String)` |
| 公开的 `ColonyQualifier`、`ColonyQualifierFacts`、`ColonyConcealment` 和 `SystemColonyReading` 构造函数 | 改为包私有：通过 `SystemColonyReading.describeColony` 描述一行。构造函数以 `BiFunction` 接收殖民地备注，并拒绝空的部分 |
| `RenderStyleReader.readFactionStyle()`、`readIndependentStyle()`、`readDecivilisedStyle()` 和 `readUninhabitedStyle(boolean)` | `HolderCategoryStyleReader` |
| `BlocPresence.collectColouredPresences(Map, BlocPaletteReader)` | 已移除：`ColonyCellRibbons` 自行为计数着色 |
| `NameFormatPreference(String)` | `NameFormatPreference(String, String)`：图层还需给出名称单选框的标题键 |
| `StandingRowResolver.resolveRows(SectorAPI, List, HolderGrouping, FactionAccountResolver)` | 每个悬停框调用一次 `StandingRowResolver.createForBox(SectorAPI, HolderGrouping, FactionAccountResolver)`，再对每个区块调用 `resolveRows(List)` |
| 公开的 `FactionPresentation`，带有 `fullName()` | 改为包私有，带有 `labelText()`。标记为某势力的行改用 `FactionTooltipLine.resolveCrestMark(SectorAPI, String)` |
| 成员列表为空的 `GroupStanding(String, int, List)` | 以 `IllegalArgumentException` 拒绝：势力组只能通过位于该星系的成员出现在其中 |
| `SectorClaims.resolveClaimingHolderIn(StarSystemAPI, HolderPass, ClaimReader, SectorBlocPalettes)` | `resolveClaimingHolderIn(StarSystemAPI, HolderPass, ClaimReader)`：集团的配色取自本次计算自身的 `palettes()` |
| `FilteredPolitics.resolveSpotlitHolder(SectorAPI, HolderGrouping, String)` 和 `SpotlitOwners.readSpotlitOwnerKey()` | `SpotlitOwners.createSpotlitOwner(ColourPair)`，接收取自本次计算自身 `palettes()` 的集团配色，因此宣称的解析无需提及主导包即可聚焦 |
| `ArrangementDialogMapCover`、`CodexMapCover`、`ModalDialogMapCover` 和 `kmu.mods.console.ConsoleMapCover` | 一个 `ScreenClaimMapCover`，读取 `ScreenClaim.isScreenClaimed()` |
| 自行组合可选 Mod 遮挡的 `MapCoverReader.createForLiveScreen()` | `MapCoverReader.registerOptionalModCovers(List)`，由 `MapLayers` 以已安装 Mod 的遮挡调用 |
| 向 Nexerelin 询问是否提供联盟视图的 `VanillaMapLayerToggleAttacher()` | `VanillaMapLayerToggleAttacher.registerAllianceViewAvailability(BooleanSupplier)`，由 `MapLayers` 调用 |
| `DebugBorderTracingBuilder.buildDebugDrawables(CellGeometryCache, ResolvedOwners, OwnerCategories, ContentInputs)` 和 `ClusterLabelStylingSnapshot.resolveForTracing(Map, ViewReading, ContentInputs, FontAtlas)` | `buildDebugDrawables(CellGeometryCache, ResolvedOwners, MapStyling)` 和 `resolveForTracing(Map, MapStyling, OwnerReading, ContentInputs, FontAtlas)`，两者都接收边界描绘重建读取的同一个 `MapStyling`。快照的 `desaturationPalette` 和 `categories` 组件改为其 `mapStyling` |
| 记录类型 `OwnerMapBuildInputs`、`RibbonPlanRules` 和 `RibbonPlan` | 改为不可继承的类，构造函数和访问器不变。`OwnerMapBuildInputs` 会记住每个所有者的绘制方式，只与自身相等；`RibbonPlan` 仍按其色段判断是否相等 |
| `MapStyling(RenderStyle, OwnerCategories, ColourPair, ColourPair, ColourPair)` | `MapStyling(RenderStyle, ClusterBorderTrace, OwnerCategories, ColourPair, ColourPair, ColourPair)`：构建的边界描绘参数随主题一同携带，区域组不会再按各自的实时读取来描绘 |
| 自行读取绘制方案的 `OwnerMapBuilder.buildClusters(CellGeometryCache, ViewReading, ContentInputs, ResolvedOwners)` | `buildClusters(CellGeometryCache, ViewReading, ContentInputs, ResolvedOwners, MapStyling)`：重建只读取一次绘制方案，供它构建的一切使用 |
| `RibbonPlanInputs(HolderPass, BlocPaletteReader, OwnerAffiliation, RibbonPlanRules)` 和 `RibbonPlanInputs.createForPass(HolderPass, OwnerAffiliation, RibbonPlanRules)` | `RibbonPlanInputs(HolderPass, OwnerAffiliation, RibbonPlanRules)`。`palettes()` 返回本次计算自身的配色读取，因此色带只能经由填充所用的同一读取着色 |
| `HolderRankingRules.createByLowestId(Predicate)` 和 `createOpenContestByLowestId()` | 已移除：每个实际排名都经由 `createByMarketProximity` 按远近裁定平局。没有几何信息的读取方改为构建 `new HolderRankingRules(Comparator.naturalOrder(), candidacy)` |
| `KmuErrorReporter.noop()` | `KmuErrorReporter.createLoggingReporter(Class)`：被捕获的失败总会留下记录 |
| 继承 `BaseCampaignEventListener` 的 `StarsectorMarketUiContextTracker` | 实现 `ColonyInteractionListener`，即监听器管理器向其传递市场事件的接口 |

</details>

## [0.2.1] - 2026-10-06

### 修复

- **`mod_info.json` 中含有对象数组的 JSON 字段会放在文件末尾**。有些第三方 Mod 安装工具只是简单地读取第一次出现的 `id` 字段，而不管它位于根层级，还是位于 `dependencies` 下嵌套的记录中。由 **NH4CI** 在 [**Fossic**](https://www.fossic.org/forum.php?mod=redirect&goto=findpost&ptid=21517&pid=405627) 报告。

### 依赖变更

- 已适配 [KMLib 0.5.1](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/releases/tag/0.5.1)。

## [0.2.0] - 2026-10-05

### 修复

- **Fast Rendering `0.9.0` 及更高版本下的崩溃。** 感谢 **Genir**，[Fast Rendering 已实现缺失的 OpenGL 方法](https://github.com/Halke1986/starsector-render/issues/11)，即未安装 Fast Rendering 时由游戏自身应答的那个方法。在 Fast Rendering 下，星图只在 `0.9.1rc1` 及更高版本中跟随光标。
  - **在更早的版本上，星图会停止响应光标，而不是导致游戏崩溃**：没有星系高亮和提示框，不过地图图层侧边栏仍会响应光标。会有通知说明应更新到哪个版本。
- **地图图层中的故障不再导致游戏崩溃。** 出错的图层会停止绘制，直到你下次读取存档，或将 *功能* 中的 **启用地图图层** 关闭后再重新开启；通知会说明是哪个图层停止了绘制，并请你报告此问题。地图、侧边栏和其他图层会继续绘制。
- **政治地图重绘被征服殖民地时出现的故障不再导致游戏崩溃。** 该殖民地会在几秒后地图的下一次检查时重绘。
- **改变了地图界面的游戏更新不再通过地图图层复选框或排列对话框导致游戏崩溃。** 复选框不会出现在该界面上，排列对话框则会关闭。
- **在政治地图的 *宣称* 视图中，在被宣称的星系中建立或失去殖民地后，该星系保持其宣称方的颜色**，而不会在下一次完整重绘之前显示持有者的颜色。
- 开启 *开发* 中的 **反射探测追踪** 后，会重新输出所有关于游戏界面的警告，包括地图图层复选框和星图的警告。

### 新增

- **简体中文。** 第二个压缩包 `KMU-<version>-zh-hans.zip` 中的设置界面、地图图层侧边栏、悬停框、游戏内通知以及 Mod 列表条目均为简体中文。请先将[中文本地化](https://github.com/TruthOriginem/Starsector-Localization-CN)覆盖安装到 `starsector-core`：游戏自带的字体不含中文字符，没有它，每个中文字符都会显示为 `?`。从列表中选择的设置保留英文选项，因此你的设置可以在两个压缩包之间沿用。发布说明和该压缩包中的 `CHANGELOG.md` 也是中文的。
- *地图 - 政治 - 外观* 中的 **荒蛮星系 - 绘制为领土** 设置，默认开启。关闭后，已揭示的荒蛮世界不再被视为其所在星系有人居住：该星系绘制为无人星系，其所有者不再出现在该图层的选择器中，也不计入存在色带和统计中的殖民地规模。该世界本身仍会被发现、列出，并在星系提示框中显示名称。由 **NoticeMeSenpai** 在 [**USC**](https://discord.com/channels/187635036525166592/1549091275167240272/1549750490617741395) 提出请求。

### 变更

- **荒蛮世界** 会被其所在星系中其他势力的殖民地揭示，无论 *地图 - 可见性* 中的 **显示调查程度至少达到以下等级的荒蛮世界** 设为何值。该设置只决定你自己的调查要求：设为 *Seen* 时访问过该星系即可，设为 *Preliminary* 或 *Full* 时则必须调查该世界本身。
- **显示调查程度至少达到以下等级的荒蛮世界** 的默认值为 *Full*，而非 *Seen*，除非你已自行设置过该项。荒蛮世界只有在你调查过它，或其所在星系有人居住时才会显示名称；仅仅飞经并不够。
- **每种语言作为独立的压缩包发布**，例如 `KMU-<version>-en.zip`。发布说明会注明各压缩包对应的语言，更新检查器在此变更前后均可正常工作。
- **当另一个 Mod 或游戏更新破坏了 KMU 所依赖的内容时，KMU 只会损失相应的功能，并以通知说明。** 通知会注明该 Mod 或游戏及两个版本，说明哪项功能失效，并建议更新、降级或等待。每条通知每次游戏期间只显示一次。
  - **游戏更新：** 若更新改变了地图图层所访问的界面，通知会注明失效的那一项功能，例如复选框缺失。
  - **LunaLib：** 若它不再向 KMU 通知设置更改，更改的设置可能要到重启游戏后才会生效。
  - **Nexerelin 联盟：** 更改联盟存储方式的版本不再使游戏中止。在本次游戏的剩余时间内，政治地图会将每个势力视为独立势力。
  - **Nexerelin 殖民地移交：** 移动了 KMU 所监听内容的版本不再导致游戏无法加载。易手的殖民地需要多几秒才会在政治地图上显示。

### 面向开发者

<details>
<summary>内部实现与地图图层框架</summary>

#### 修复

- **Fast Rendering 崩溃：** KMU 通过 KMLib `0.5.0` 读取光标，KMLib 以 `glGetFloat` 读取星图的模型视图矩阵，并在每次游戏期间报告一次过旧的 Fast Rendering 版本。
- **地图图层故障：** 星图的绘制过程及其上方的星系提示框都不捕获失败。`DrawnLayerGuard` 会在该星域上关闭出错的图层，并将该故障连同堆栈跟踪记录到日志。
- **被征服殖民地的重绘：** Nexerelin 从入侵、叛乱或移交对话框内部通知其监听器，而这些地方本身不捕获异常。重绘的失败会被捕获并记录，Nexerelin 在通知监听器之后的操作仍会执行。
- **地图图层复选框与排列对话框：** 两者都深入游戏的界面代码，更改了这些代码的游戏版本可能以两者都未捕获的方式在此失败。该失败会记录在 `starsector.log` 中。
- **宣称视图的重绘：** 增量重绘依据的是星系的持有者，而非其宣称方。
- **市场条件管理器的计数行以灰色绘制可用数与总数。** 游戏不会高亮紧挨前一个单词的片段，而每个分隔符都以空格开头，因此灰色部分从破折号开始。
- **反射探测追踪：** 复选框和星图自身状态的警告未被纳入重置。
- **`data/config/kmu/installations.csv` 中被注释掉的行会被忽略**：实体类型以 `#` 开头的行不会进入表格，与游戏自身表格注释掉行的方式一致。

#### 公共契约变更（**破坏性**）

以下变更都不影响玩家：每个 LunaLib 字段 ID 和保存在星域内存中的每个值都保持原有拼写，因此设置和存档可原样沿用。它们影响的是基于 KMU 框架构建地图图层的 Mod。

##### 层与包

框架分为三层：`kmu.maplayers.base` 是基础层；`kmu.maplayers.ownermap` 为新增，是任何按所有者键为星系着色的图层所基于的管线，不得导入政治地图，也不得导入 `kmu.mods`；`kmu.maplayers.politicalmap` 是 KMU 的政治地图，作为其上的一个图层。

| 之前 | 之后 |
| --- | --- |
| `kmu.maplayers.politicalmap.base` 及其 `holding`、`owners`、`owners.holders`、`picker`、`preferences`、`render`、`ribbon`、`sidebar` 和 `tooltip` | 相同的包，位于 `kmu.maplayers.ownermap` 下 |
| `kmu.maplayers.politicalmap.base` 下政治地图自身的部分 | `kmu.maplayers.politicalmap`：`PoliticalMapLayer`、`PoliticalMapInstaller` 和 `PoliticalMapStanding` 位于该包本身，其规则位于 `dominance`、`claims`、`views`、`holders`、`tooltip`、`refresh` 和 `render` |

##### 重命名

层的类型与成员以所有者和分组命名，而不再以政治地图或联盟命名。

| 之前 | 之后 |
| --- | --- |
| `PoliticalMapView` 和 `PoliticalMapViewRegistry` | `OwnerPaintedView` 和 `MapLayerViewRegistry` |
| `PoliticalMapOverlayRenderer`、`PoliticalMapCache`、`PoliticalMapDrawables`、`PoliticalMapRebuildDecider`、`PoliticalMapBandLayout`、`PoliticalMapCategory`、`PoliticalMapBodyControls`、`PoliticalMapInhabitation` 和 `PoliticalMapHoverHighlightSource` | 各名称中的 `PoliticalMap` 换为 `OwnerMap` |
| `PoliticalMapTerritories` | `OwnerMapClusters` |
| `TerritoryBuilder`、`TerritoryBuildInputs` 和 `FactionTerritoryBuilder` | `OwnerMapBuilder`、`OwnerMapBuildInputs` 和 `ClusterGroupBuilder` |
| `StandingPoliticalMap`、`StalePoliticsDisturbance` 和 `IncrementalPoliticsRefresh` | `StandingOwnerMap`、`StaleOwnerMapDisturbance` 和 `IncrementalOwnerRefresh` |
| `BlocStyleDecision`、`BlocStyleResolver` 和 `BlocStyling` | 各名称中的 `Bloc` 换为 `Owner` |
| `DominantHolder` 和 `PoliticalMapPreviewHighlightRenderer` | `SystemOwner` 和 `SpotlightPreviewHighlightRenderer` |
| `KmuPoliticalMapDiagnosticsSettings`、`KmuPoliticalMapGeometrySettings`、`KmuPoliticalMapHighlightSettings` 和 `KmuPoliticalMapRibbonSettings` | 各名称中的 `PoliticalMap` 换为 `OwnerMap` |
| `KmuPoliticalMapTerritorySettings` | `KmuOwnerMapStyleSettings` |
| `getPoliticalMap*` | `getOwnerMap*` |
| `getPoliticalMapAllianceMutedOpacityModifier()` 和 `shouldDecivilisedSystemsDrawTerritory()` | `getOwnerMapMutedOpacityModifier()` 和 `shouldCountDecivilisedSystemsAsPopulated()` |
| 十九个 `KmuStringKeys.POLITICAL_MAP_*` 常量及其 `strings.json` 键 | `OWNER_MAP_*` |
| `HolderGrouping.allianceNameByBlocId`、`resolveAllianceName(String)`、`isAlliance(String)` 和 `hasAnyAlliance()` | `groupNameByBlocId`、`resolveGroupName(String)`、`isGroupedBloc(String)` 和 `hasAnyGroupedBloc()` |
| `ContentInputs.allianceRecedeAdjustment` | `viewRecedeAdjustment` |
| `SystemOccupancy.getHolderBySystemKey()`、`readHolderOf(SystemKey)`、`recordHolderOf(SystemKey, DominantHolder)` 和 `selectUnheldSystemKeysAmong(Set)` | `getOwnerBySystemKey()`、`readOwnerOf(SystemKey)`、`recordOwnerOf(SystemKey, SystemOwner)` 和 `selectUnownedSystemKeysAmong(Set)` |
| `SystemOccupancy.selectUnheldSystemKeysIn(Map, Set)` | `SystemOwner.selectUnownedSystemKeysAmong(Map, Set)` |
| `ClusterLabelStylingSnapshot.holderBySystemKey` | `ownerBySystemKey` |
| `DominancePass.over(HolderPass, DominanceRules)`、`over(SectorAPI, DominanceRules, ColonyVisibility, HolderGrouping)` 和 `MarketProximityTieBreak.forSystem(StarSystemAPI, Colonies, ColonyKnowledge, HolderGrouping)` | `createOver(HolderPass, DominanceRules)`、`createOver(SectorAPI, DominanceRules, ColonyReadRules, HolderGrouping)` 和 `createForSystem(StarSystemAPI, Colonies, ColonyKnowledge, HolderGrouping)` |

##### 视图

视图通过一次采样得到的同一份读取结果回答每个星系的所有者，因此其各部分不会互相矛盾。

| 之前 | 之后 |
| --- | --- |
| `OwnerPaintedView` 的 `resolveGrouping()`、`resolveHolderProvider()`、`resolveRibbonPlanner(RibbonPlanInputs)`、`shouldUseIndependentStyle(String, HolderGrouping, ElementStyleAdjustment)`、`resolveBlocStyleAdjustment(String, HolderGrouping, ContentInputs)`、`resolveName(String, HolderGrouping, SectorAPI, FactionNameFormatChoice)` 和 `computeAllianceContentRevision(MapLayerRefreshBoard)` | `resolveViewReading(SectorAPI)`，必需：由视图、其 `OwnerReading` 及其 `OwnerSource` 组成的 `ViewReading` |
| 无 | `resolveCategories()`，必需：单元划分为哪些类别 |
| 无 | `resolveViewRecedeAdjustment(ScreenMemoryScope)`，默认不退后任何内容 |
| 接受分组的 `buildBlocPickerRead(SectorAPI, HolderGrouping, BlocStatsRead, ListSortModes)` | 还接受视图的 `OwnerReading` |
| 绘制持有者的视图 | 实现 `kmu.maplayers.ownermap.owners.holders.HolderPaintedView`，该接口将其 `resolveGrouping()`、`resolveContestGrouping()`、`resolveHolderProvider()`、`resolveSystemHolderResolveSource()`、`resolveRibbonPlanner(RibbonPlanInputs)` 和 `resolveOwnerReading(SectorAPI, HolderGrouping)` 组装为 `resolveViewReading(SectorAPI)`。`DominancePaintedView` 和 `ClaimsView` 都是持有者视图。 |
| `ViewGrouping` | `ViewReading`，携带读取结果和所有者来源 |

##### 所有者来源

该层不读取殖民地，由图层的 `OwnerSource` 基于该层交给它的 `SectorWalk` 读取。

| 之前 | 之后 |
| --- | --- |
| 无 | `kmu.maplayers.ownermap.owners.OwnerSource`：`resolveOwners(SectorWalk, String)`、`openSystemResolve(SectorWalk, String)` 和 `resolveRibbonPlanner(SectorWalk, RibbonPlanRules)`。`SectorWalk.readReadingOpenedBy(Object, Class, Supplier)` 为每个来源在每次遍历中保存一份读取结果。 |
| `ResolvedHolding` | `owners` 中的 `ResolvedOwners`：所有者、斜线填充和不填充的星系、有人居住的星系，以及聚焦所有者的存在位置 |
| 无 | `HolderOwnerSource`，绘制持有者的图层所用的来源，每次遍历基于一个 `HolderPass` |
| `SystemHolderResolveSource.openResolveOver(SectorAPI, HolderGrouping)` | 接受批次的 `HolderPass`，`SystemHolderResolve` 只回答 `resolveHolderIn(StarSystemAPI)` |
| `ClaimsView` 按持有者重新推导被标记的星系 | `ClaimSystemHolderResolve`，按宣称方，通过 `SectorClaims.resolveClaimingHolderIn(StarSystemAPI, HolderPass, ClaimReader, SectorBlocPalettes)` |
| `OwnerMapBuilder.resolveHolding(HolderPass, PoliticalMapView, ContentInputs)` | `resolveOwners(OwnerSource, SectorWalk, ContentInputs)`，`buildClusters(CellGeometryCache, ViewReading, ContentInputs, ResolvedOwners)` 接受 `ViewReading` 和 `ResolvedOwners` |
| 接受诊断用提供者和逐星系解析来源的 `OwnerMapCache` | 一个 `CellSeedRule`，`OwnerMapLayerRenderer.createForLiveScreen(SectorMapMachinery, String, MapLayerViewRegistry, OwnerMapBodyPreferences, OwnerMapPreviewHighlight, MapLayerHoverGates, Supplier, CellSeedRule)` 随之改变 |
| 接受解析来源的 `IncrementalOwnerRefresh.applyStaleOwnerUpdates(SectorAPI, StandingOwnerMap, Set, SystemHolderResolveSource)` | 批次的 `SectorWalk` |
| `CellRibbonsBaker.createForPass(PoliticalMapTerritories, CellGeometryCache, HolderPass, List)` 和 `CellRibbonSource.createForPass(HolderPass, PoliticalMapView, RibbonBakeSurface)` | 接受 `SectorWalk` |
| `DebugBorderTracingBuilder.buildDebugDrawables(CellGeometryCache, SectorAPI, ContentInputs)` | 接受已解析的所有者和类别 |
| `ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(StandingClusterAnchors, RevisedCellGeometry, SectorAPI, PoliticalMapView, ContentInputs)` | `rebuildDiagnosticClusterAnchors(StandingClusterAnchors, RevisedCellGeometry, ClusterLabelStylingSnapshot)`，样式由 `ClusterLabelStylingSnapshot.resolveForTracing(Map, ViewReading, ContentInputs, FontAtlas)` 解析 |
| 无 | `SpotlitBlocs.isBlocPresentIn(HolderPass, String, StarSystemAPI)` |
| 无 | `kmu.maplayers.base.geometry.CellSeedRule`，附带 `SEED_DRAWN_SYSTEMS`。`CellGeometryCache.updateFromSector(MapVisibilityPass, CellSeedRule, Collection, CellSeedInputs)` 和 `PartitionSites.collectSitesFrom(MapVisibilityPass, CellSeedRule, Collection)` 接受该规则，`DrawnSystemPositions.collectLivePositions(MapVisibilityPass, Predicate)` 新增一个接受成员规则的重载。 |

##### 所有者读取与样式

该层向图层询问其所有者，而不再读取势力。

| 之前 | 之后 |
| --- | --- |
| 无 | `kmu.maplayers.ownermap.owners.OwnerReading`：每个所有者的色调、名称、徽记、退后和类别。`HolderOwnerReading` 是政治地图的实现。 |
| 无 | `kmu.maplayers.ownermap.render.style.OwnerCategories`：存在哪些类别及各类别的样式。`HolderCategories` 声明四个 `OwnerMapCategory` 值。 |
| `MapPalettes`、`MapStyling`、`ResolvedBlocPaint`、`BlocPaletteReader`、`BlocPresence` 和标签样式中 KMLib 的 `FactionPalette` | `OwnerPalette` |
| `SystemOwner.factionId`、其两个颜色分量和 `mapFactionIdBySystemKey(Map)` | `ownerId`、一个 `palette` 和 `mapOwnerIdBySystemKey(Map)`。`resolvePalette()` 已移除。 |
| `SystemOwner.resolveForBloc(SectorAPI, HolderGrouping, String)` | `SectorBlocPalettes.resolveOwnerOf(String)` |
| 携带 `ViewGrouping` 的 `ClusterLabelStylingSnapshot` | 携带类别和读取结果 |
| 说明所有者是否采用非势力团体样式的 `OwnerStyleDecision` | 所有者绘制时所用的类别。`OwnerStyleResolver.resolveBlocStyleDecision(String, OwnerReading, OwnerCategories, ContentInputs)` 和 `OwnerStyling.resolveFrom(RenderStyle, OwnerCategories, OwnerStyleDecision)` 接受读取结果和类别。 |
| `RenderStyleReader.readRenderStyle(boolean)` | 接受图层的类别和采样的偏好设置 |
| `BlocNameStyles.readFromLunaSettings()` | 已移除：`BlocNameStyles` 为每个类别一个名称样式 |
| `FactionlessStyleResolver.resolveCategoryOf(Set, SystemKey)` | `isSettledSystem(Set, SystemKey)` |
| 接受星域的 `ClusterAnchorsBuilder.rebuildClusterAnchors(StandingClusterAnchors, RevisedCellGeometry, SectorAPI, ClusterLabelStylingSnapshot)` | 不再接受星域 |
| 无 | `OwnerMapBuildInputs.wasBuilt()`：对首次构建失败后所用的占位对象返回 false |

##### 帧序列

每个着色图层都运行同一个帧序列，由框架所有。

| 之前 | 之后 |
| --- | --- |
| `PoliticalMapLayerRenderer` | `kmu.maplayers.base.render.SequencedMapLayerRenderer`，基于图层的 `MapLayerFrameParts`：让位读取、一个 `MapFrameCache`、一个 `MapFrameCompositor`、该图层的 `MapLayerHoverGates` 及其悬停框 |
| `OwnerMapLayerRenderer.createForLiveScreen(SectorMapMachinery, String)` | 将所有者地图的各部分组合为一个渲染器 |
| `OwnerMapCache.refresh(PoliticalMapView, ScreenMemoryScope)` | `refreshDrawLists(OwnerPaintedView, ScreenMemoryScope)`，`resolveHoverTargets()` 为新增 |
| `MapLayerRegistry.resolveDrawnMapRenderer(SectorMapMachinery)` | 位于 `kmu.maplayers.base.render` 的 `DrawnLayerGuard.resolveGuardIn(SectorMapMachinery)`，会关闭抛出异常的图层 |

##### 图层状态与偏好设置

图层拥有自己的状态，因此两个图层不会意外共用它。

| 之前 | 之后 |
| --- | --- |
| `MapLayerViewRegistry` 的静态成员和 `getActiveView()` | 基于图层的存档键、视图、默认视图和宿主标签页构建的实例，通过 `resolveActiveViewOn(ScreenLayerPicks)` 作答 |
| 图层持有的按星域划分的部件 | `SectorMapMachinery.resolveLayerMachinery(String, Class, Supplier)` |
| `SelectableBlocCache.resolveBlocCacheIn(SectorMapMachinery)` | 接受图层 ID |
| `FilterSelectionHeal.healStaleSelectionAgainstActiveView()` | 接受星域和注册表。`healStaleSelectionAgainstLiveSector(MapLayerViewRegistry)` 供未持有星域的调用方使用。 |
| `OwnerMapBodyControls.buildViewSelector(ScreenMemoryScope)` | 接受其主体所构建的星域 |
| 作为单例的 `PoliticalMapLayer` | 通过其视图构造 |
| 作为静态成员的 `NameFormatPreference`、`UninhabitedOutlinePreference` 和 `RecedePreferences`，以及 `RecedePreferences.FILTER` 和 `ALLIANCE_NON_ALLIED` | 基于图层所命名的键的实例，作为 `OwnerMapBodyPreferences` 交给 `OwnerMapLayerRenderer.createForLiveScreen(SectorMapMachinery, String, MapLayerViewRegistry, OwnerMapBodyPreferences, OwnerMapPreviewHighlight, MapLayerHoverGates, Supplier, CellSeedRule)`、`OwnerMapCache`、`OwnerMapRebuildDecider` 和 `ContentInputs.sampleForView(OwnerPaintedView, OwnerMapBodyPreferences, ScreenMemoryScope)` |
| `FactionNameFormatChoice.fromKeyOrDefault(String, FactionNameFormatChoice)` | 基于 `PersistedChoice` 的 KMLib `PersistedChoices.fromKey(PersistedChoice[], String, PersistedChoice)` |
| 图层之间共用的 `MapLayerSectorWatcher` | 抽象类：每个图层安装自己的子类，因为引擎按确切类移除瞬态脚本 |

##### 悬停、移交与政治地图

迁移到框架上、合并，或回答更丰富问题的政治地图部件。

| 之前 | 之后 |
| --- | --- |
| `PoliticalMapHoverGates` | 每个图层的 `kmu.maplayers.base.hover.MapLayerHoverGates`，以及用于所有者地图开关的 `SharedOwnerMapHoverGates`。`MapLayerHoverGates.isCursorReadNeeded()` 为默认方法。 |
| 实现 Nexerelin 的 `InvasionListener` 的 `PoliticalMapMarketTransferListener` | 实现 KMU 自身的 `kmu.starsector.listeners.MarketTransferListener`，会收到 Nexerelin 在其星域移交的每个殖民地的通知 |
| `HolderPass.openClaimReaderThrough(ClaimReaderSource)` | 政治地图的 `PassClaimReaders.openClaimReaderOver(HolderPass, ClaimReaderSource)` |
| `ColonyQualifierFacts.isHoldingTheClaim` | `leadingFinding`：置于其他结论之前、已措辞完毕的结论，或为 null。`SystemColonyReading.readQualifierFacts(String, boolean, boolean, String)` 和 `readQualifierFacts(String, ColonyKind, boolean, boolean, String)` 负责组装它。 |
| `FactionTooltipLine.buildCountedFactionLine(SectorAPI, String, int)` | 接受该势力是否参与权重计算 |
| `SystemStandingsTooltip` 和 `SystemClaimContestTooltip` | 并入 `SystemDominationTooltip` 和 `SystemClaimTooltip` |
| `LiveVisibilityClaimBreakdownReader` | 已移除：宣称悬停框基于悬停所在的星域打开其读取器 |
| `DominancePass.readFromLunaSettings(SectorAPI, HolderGrouping)` 和 `readFromLunaSettings(SectorAPI)` | 已移除 |
| `FilteredPolitics.resolveFilteredHolder(HolderPass, String)` 和 `resolveFilteredHolder(DominancePass, String)` | 返回 `HolderResolution` |
| `CellTooltipQualifier` 的规范构造函数 | 在 `findingText` 与 `trailingWordText` 之间接受一个 `findingColour`，通过 `drawsFindingIn(Color)` 设置。未设置时，结论以悬停框的金色显示。 |

</details>

## [0.1.2] - 2026-09-16

### 新增

- **设置** 新增一条总体说明。
- 较大的 **设置** 标签页新增说明，列出其中的内容。

### 变更

- **星系提示框** 仅在安装 **Nexerelin** 时使用 *争夺* 一词。未安装时，势力显示为 *在场*。

## [0.1.1] - 2026-09-15

- 已适配 [KMLib 0.3.0](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/releases/tag/0.3.0) 和 [KMLib 0.3.1](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/releases/tag/0.3.1)。
- 项目改为采用 **LGPL-3.0-only** 许可证。

### 修复

- **斜线填充** 的线条在低分辨率下缩小视图时出现裁切和重叠。斜线宽度现在按斜线间距的百分比设置，而非以像素为单位，因此图案在任何缩放级别下都能保持比例。*地图 - 开发* 中的 **斜线宽度** 设置也随之更改单位（由 0.5-100 像素变为 5-90%），并保留你原先设置的数值。由 **Vexlia** 在 [**USC**](https://discord.com/channels/187635036525166592/1549091275167240272/1549148937540210718) 报告。

## [0.1.0] - 2026-09-14

首个标记版本，因此没有可供对比的先前版本。

### 新增

- **星图图层** 功能：
  - **政治地图**：
    - **势力** 视图。
    - **联盟** 视图。
    - **宣称** 视图。
  - 星系与市场可见性规则。
  - 势力存在色带。
  - 注入到地图筛选栏中的地图图层开关。
  - 星图界面和情报信息界面上的可折叠侧边栏。
  - 星系提示框。
  - **Fast Rendering** 兼容性。
  - **Nexerelin** 兼容性。
  - **Random Assortment of Things** 兼容性。
  - 基于 **LunaLib** 的设置。
  - *kmu_profiling* 控制台命令。
