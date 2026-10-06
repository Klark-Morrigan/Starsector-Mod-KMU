# 更新日志

本文件记录 KMU 的所有重要变更。格式遵循 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## 索引

- [未发布](#unreleased)
- [0.2.0](#020---2026-10-05)
- [0.1.2](#012---2026-09-16)
- [0.1.1](#011---2026-09-15)
- [0.1.0](#010---2026-09-14)

## [Unreleased]

### 依赖变更

- 已适配 [KMLib 0.5.1](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/releases/tag/0.5.1)。

## [0.2.0] - 2026-10-05

### 修复

- **Fast Rendering `0.9.0` 及更高版本下的崩溃。** 感谢 **Genir**，[Fast Rendering 已实现缺失的 OpenGL 方法](https://github.com/Halke1986/starsector-render/issues/11)，即未安装 Fast Rendering 时由游戏自身应答的那个方法。在 Fast Rendering 下，星图只在 `v0.9.1rc1` 及更高版本中跟随光标。
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
| `getPoliticalMapAllianceMutedOpacityModifier` 和 `shouldDecivilisedSystemsDrawTerritory` | `getOwnerMapMutedOpacityModifier` 和 `shouldCountDecivilisedSystemsAsPopulated` |
| 十九个 `KmuStringKeys.POLITICAL_MAP_*` 常量及其 `strings.json` 键 | `OWNER_MAP_*` |
| `HolderGrouping.allianceNameByBlocId`、`resolveAllianceName`、`isAlliance` 和 `hasAnyAlliance` | `groupNameByBlocId`、`resolveGroupName`、`isGroupedBloc` 和 `hasAnyGroupedBloc` |
| `ContentInputs.allianceRecedeAdjustment` | `viewRecedeAdjustment` |
| `SystemOccupancy.getHolderBySystemKey`、`readHolderOf`、`recordHolderOf` 和 `selectUnheldSystemKeysAmong` | `getOwnerBySystemKey`、`readOwnerOf`、`recordOwnerOf` 和 `selectUnownedSystemKeysAmong` |
| `SystemOccupancy.selectUnheldSystemKeysIn` | `SystemOwner.selectUnownedSystemKeysAmong` |
| `ClusterLabelStylingSnapshot.holderBySystemKey` | `ownerBySystemKey` |
| `DominancePass.over` 和 `MarketProximityTieBreak.forSystem` | `createOver` 和 `createForSystem` |

##### 视图

视图通过一次采样得到的同一份读取结果回答每个星系的所有者，因此其各部分不会互相矛盾。

| 之前 | 之后 |
| --- | --- |
| `OwnerPaintedView` 的 `resolveGrouping()`、`resolveHolderProvider()`、`resolveRibbonPlanner(RibbonPlanInputs)`、`shouldUseIndependentStyle`、`resolveBlocStyleAdjustment`、`resolveName` 和 `computeAllianceContentRevision` | `resolveViewReading(SectorAPI)`，必需：由视图、其 `OwnerReading` 及其 `OwnerSource` 组成的 `ViewReading` |
| 无 | `resolveCategories()`，必需：单元划分为哪些类别 |
| 无 | `resolveViewRecedeAdjustment(ScreenMemoryScope)`，默认不退后任何内容 |
| 接受分组的 `buildBlocPickerRead` | 还接受视图的 `OwnerReading` |
| 绘制持有者的视图 | 实现 `kmu.maplayers.ownermap.owners.holders.HolderPaintedView`，该接口将其 `resolveGrouping()`、`resolveContestGrouping()`、`resolveHolderProvider()`、`resolveSystemHolderResolveSource()`、`resolveRibbonPlanner(RibbonPlanInputs)` 和 `resolveOwnerReading(SectorAPI, HolderGrouping)` 组装为 `resolveViewReading`。`DominancePaintedView` 和 `ClaimsView` 都是持有者视图。 |
| `ViewGrouping` | `ViewReading`，携带读取结果和所有者来源 |

##### 所有者来源

该层不读取殖民地，由图层的 `OwnerSource` 基于该层交给它的 `SectorWalk` 读取。

| 之前 | 之后 |
| --- | --- |
| 无 | `kmu.maplayers.ownermap.owners.OwnerSource`：`resolveOwners`、`openSystemResolve` 和 `resolveRibbonPlanner`。`SectorWalk.readReadingOpenedBy` 为每个来源在每次遍历中保存一份读取结果。 |
| `ResolvedHolding` | `owners` 中的 `ResolvedOwners`：所有者、斜线填充和不填充的星系、有人居住的星系，以及聚焦所有者的存在位置 |
| 无 | `HolderOwnerSource`，绘制持有者的图层所用的来源，每次遍历基于一个 `HolderPass` |
| `SystemHolderResolveSource.openResolveOver` | 接受批次的 `HolderPass`，`SystemHolderResolve` 只回答 `resolveHolderIn` |
| `ClaimsView` 按持有者重新推导被标记的星系 | `ClaimSystemHolderResolve`，按宣称方，通过 `SectorClaims.resolveClaimingHolderIn` |
| `OwnerMapBuilder.resolveHolding` | `resolveOwners(OwnerSource, SectorWalk, ContentInputs)`，`buildClusters` 接受 `ViewReading` 和 `ResolvedOwners` |
| 接受诊断用提供者和逐星系解析来源的 `OwnerMapCache` | 一个 `CellSeedRule`，`OwnerMapLayerRenderer.createForLiveScreen` 随之改变 |
| 接受解析来源的 `IncrementalOwnerRefresh.applyStaleOwnerUpdates` | 批次的 `SectorWalk` |
| `CellRibbonsBaker.createForPass` 和 `CellRibbonSource.createForPass` | 接受 `SectorWalk` |
| `DebugBorderTracingBuilder.buildDebugDrawables` | 接受已解析的所有者和类别 |
| `ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector` | `rebuildDiagnosticClusterAnchors`，样式由 `ClusterLabelStylingSnapshot.resolveForTracing` 解析 |
| 无 | `SpotlitBlocs.isBlocPresentIn` |
| 无 | `kmu.maplayers.base.geometry.CellSeedRule`，附带 `SEED_DRAWN_SYSTEMS`。`CellGeometryCache.updateFromSector` 和 `PartitionSites.collectSitesFrom` 接受该规则，`DrawnSystemPositions.collectLivePositions` 新增一个接受成员规则的重载。 |

##### 所有者读取与样式

该层向图层询问其所有者，而不再读取势力。

| 之前 | 之后 |
| --- | --- |
| 无 | `kmu.maplayers.ownermap.owners.OwnerReading`：每个所有者的色调、名称、徽记、退后和类别。`HolderOwnerReading` 是政治地图的实现。 |
| 无 | `kmu.maplayers.ownermap.render.style.OwnerCategories`：存在哪些类别及各类别的样式。`HolderCategories` 声明四个 `OwnerMapCategory` 值。 |
| `MapPalettes`、`MapStyling`、`ResolvedBlocPaint`、`BlocPaletteReader`、`BlocPresence` 和标签样式中 KMLib 的 `FactionPalette` | `OwnerPalette` |
| `SystemOwner.factionId`、其两个颜色分量和 `mapFactionIdBySystemKey` | `ownerId`、一个 `palette` 和 `mapOwnerIdBySystemKey`。`resolvePalette` 已移除。 |
| `SystemOwner.resolveForBloc` | `SectorBlocPalettes.resolveOwnerOf` |
| 携带 `ViewGrouping` 的 `ClusterLabelStylingSnapshot` | 携带类别和读取结果 |
| 说明所有者是否采用非势力团体样式的 `OwnerStyleDecision` | 所有者绘制时所用的类别。`OwnerStyleResolver.resolveBlocStyleDecision` 和 `OwnerStyling.resolveFrom` 接受读取结果和类别。 |
| `RenderStyleReader.readRenderStyle` | 接受图层的类别和采样的偏好设置 |
| `BlocNameStyles.readFromLunaSettings` | 已移除：`BlocNameStyles` 为每个类别一个名称样式 |
| `FactionlessStyleResolver.resolveCategoryOf` | `isSettledSystem` |
| 接受星域的 `ClusterAnchorsBuilder.rebuildClusterAnchors` | 不再接受星域 |
| 无 | `OwnerMapBuildInputs.wasBuilt()`：对首次构建失败后所用的占位对象返回 false |

##### 帧序列

每个着色图层都运行同一个帧序列，由框架所有。

| 之前 | 之后 |
| --- | --- |
| `PoliticalMapLayerRenderer` | `kmu.maplayers.base.render.SequencedMapLayerRenderer`，基于图层的 `MapLayerFrameParts`：让位读取、一个 `MapFrameCache`、一个 `MapFrameCompositor`、该图层的 `MapLayerHoverGates` 及其悬停框 |
| `OwnerMapLayerRenderer.createForLiveScreen` | 将所有者地图的各部分组合为一个渲染器 |
| `OwnerMapCache.refresh` | `refreshDrawLists`，`resolveHoverTargets` 为新增 |
| `MapLayerRegistry.resolveDrawnMapRenderer` | 位于 `kmu.maplayers.base.render` 的 `DrawnLayerGuard.resolveGuardIn(machinery)`，会关闭抛出异常的图层 |

##### 图层状态与偏好设置

图层拥有自己的状态，因此两个图层不会意外共用它。

| 之前 | 之后 |
| --- | --- |
| `MapLayerViewRegistry` 的静态成员和 `getActiveView()` | 基于图层的存档键、视图、默认视图和宿主标签页构建的实例，通过 `resolveActiveViewOn(ScreenLayerPicks)` 作答 |
| 图层持有的按星域划分的部件 | `SectorMapMachinery.resolveLayerMachinery(layerId, type, make)` |
| `SelectableBlocCache.resolveBlocCacheIn` | 接受图层 ID |
| `FilterSelectionHeal.healStaleSelectionAgainstActiveView` | 接受星域和注册表。`healStaleSelectionAgainstLiveSector(registry)` 供未持有星域的调用方使用。 |
| `OwnerMapBodyControls.buildViewSelector` | 接受其主体所构建的星域 |
| 作为单例的 `PoliticalMapLayer` | 通过其视图构造 |
| 作为静态成员的 `NameFormatPreference`、`UninhabitedOutlinePreference` 和 `RecedePreferences`，以及 `RecedePreferences.FILTER` 和 `ALLIANCE_NON_ALLIED` | 基于图层所命名的键的实例，作为 `OwnerMapBodyPreferences` 交给 `OwnerMapLayerRenderer.createForLiveScreen`、`OwnerMapCache`、`OwnerMapRebuildDecider` 和 `ContentInputs.sampleForView` |
| `FactionNameFormatChoice.fromKeyOrDefault` | 基于 `PersistedChoice` 的 KMLib `PersistedChoices.fromKey` |
| 图层之间共用的 `MapLayerSectorWatcher` | 抽象类：每个图层安装自己的子类，因为引擎按确切类移除瞬态脚本 |

##### 悬停、移交与政治地图

迁移到框架上、合并，或回答更丰富问题的政治地图部件。

| 之前 | 之后 |
| --- | --- |
| `PoliticalMapHoverGates` | 每个图层的 `kmu.maplayers.base.hover.MapLayerHoverGates`，以及用于所有者地图开关的 `SharedOwnerMapHoverGates`。`MapLayerHoverGates.isCursorReadNeeded()` 为默认方法。 |
| 实现 Nexerelin 的 `InvasionListener` 的 `PoliticalMapMarketTransferListener` | 实现 KMU 自身的 `kmu.starsector.listeners.MarketTransferListener`，会收到 Nexerelin 在其星域移交的每个殖民地的通知 |
| `HolderPass.openClaimReaderThrough` | 政治地图的 `PassClaimReaders.openClaimReaderOver` |
| `ColonyQualifierFacts.isHoldingTheClaim` | `leadingFinding`：置于其他结论之前、已措辞完毕的结论，或为 null。`SystemColonyReading.readQualifierFacts` 负责组装它。 |
| `FactionTooltipLine.buildCountedFactionLine` | 接受该势力是否参与权重计算 |
| `SystemStandingsTooltip` 和 `SystemClaimContestTooltip` | 并入 `SystemDominationTooltip` 和 `SystemClaimTooltip` |
| `LiveVisibilityClaimBreakdownReader` | 已移除：宣称悬停框基于悬停所在的星域打开其读取器 |
| `DominancePass.readFromLunaSettings` | 已移除 |
| `FilteredPolitics.resolveFilteredHolder` | 返回 `HolderResolution` |
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
