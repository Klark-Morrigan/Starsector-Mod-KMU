# 更新日志

本文件记录 KMU 的所有重要变更。格式遵循 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## 索引

- [未发布](#unreleased)
- [0.1.2](#012---2026-09-16)
- [0.1.1](#011---2026-09-15)
- [0.1.0](#010---2026-09-14)

## [Unreleased]

### 修复

- **Fast Rendering `0.9.0` 及更高版本下的崩溃。** 感谢 **Genir**，[Fast Rendering 已实现缺失的 OpenGL 方法](https://github.com/Halke1986/starsector-render/issues/11)，即未安装 Fast Rendering 时由游戏自身应答的那个方法。在 Fast Rendering 下，星图只在 `v0.9.1rc1` 及更高版本中跟随光标。
  - **在更早的版本上，星图会停止响应光标，而不是导致游戏崩溃**：没有星系高亮和提示框，不过地图图层侧边栏仍会响应光标。会有通知说明应更新到哪个版本。
- **地图图层中的故障不再导致游戏崩溃。** 出错的图层会停止绘制，直到你下次读取存档，或将 *功能* 中的 **启用地图图层** 关闭后再重新开启；通知会说明是哪个图层停止了绘制，并请你报告此问题。地图、侧边栏和其他图层会继续绘制。
- **政治地图重绘被征服殖民地时出现的故障不再导致游戏崩溃。** 该殖民地会在几秒后地图的下一次检查时重绘。
- **改变了地图界面的游戏更新不再通过地图图层复选框或排列对话框导致游戏崩溃。** 复选框不会出现在该界面上，排列对话框则会关闭。
- **在政治地图的 *宣称* 视图中，在被宣称的星系中建立或失去殖民地后，该星系保持其宣称方的颜色**，而不会在下一次完整重绘之前显示持有者的颜色。
- **市场条件管理器的计数行按预期以灰色绘制可用数与总数。**
- 开启 *开发* 中的 **反射探测追踪** 后，会重新输出所有关于游戏界面的警告，包括地图图层复选框和星图的警告。

### 新增

- **简体中文。** 第二个压缩包 `KMU-<version>-zh-hans.zip` 中的设置界面、地图图层侧边栏、悬停框、游戏内通知以及 Mod 列表条目均为简体中文。请先将[中文本地化](https://github.com/TruthOriginem/Starsector-Localization-CN)覆盖安装到 `starsector-core`：游戏自带的字体不含中文字符，没有它，每个中文字符都会显示为 `?`。从列表中选择的设置保留英文选项，因此你的设置可以在两个压缩包之间沿用。发布说明和该压缩包中的 `CHANGELOG.md` 也是中文的。
- *地图 - 政治 - 外观* 中的 **荒蛮星系 - 绘制为领土** 设置，默认开启。关闭后，已揭示的荒蛮世界不再被视为其所在星系有人居住：该星系绘制为无人星系，其所有者不再出现在该图层的选择器中，也不计入存在色带和统计中的殖民地规模。该世界本身仍会被发现、列出，并在星系提示框中显示名称。由 **NoticeMeSenpai** 在 [**USC**](https://discord.com/channels/187635036525166592/1549091275167240272/1549750490617741395) 提出请求。
- **地图标签、悬停框和地图图层侧边栏改用能显示其文字的字体绘制。** 当某个字体无法绘制某个名称或词语时（例如语言包的字体缺少部分字符），这些文字会改用同一字体的较小字号或游戏的默认字体绘制，而不再显示为问号。安装中文本地化后，地图上的势力名称会降低一级字号。英文安装下没有任何变化。

### 变更

- **Mod 列表中的 Mod 名称为 Klark Morrigan 的实用工具 (KMU)。** 更新检查器显示相同的名称。
- **每种语言作为独立的压缩包发布**，例如 `KMU-<version>-en.zip`。发布说明会注明各压缩包对应的语言，更新检查器在此变更前后均可正常工作。
- **荒蛮世界** 会被其所在星系中其他势力的殖民地揭示，无论 *地图 - 可见性* 中的 **显示调查程度至少达到以下等级的荒蛮世界** 设为何值。该设置只决定你自己的调查要求：设为 *Seen* 时访问过该星系即可，设为 *Preliminary* 或 *Full* 时则必须调查该世界本身。
- **显示调查程度至少达到以下等级的荒蛮世界** 的默认值为 *Full*，而非 *Seen*，除非你已自行设置过该项。荒蛮世界只有在你调查过它，或其所在星系有人居住时才会显示名称；仅仅飞经并不够。
- **当另一个 Mod 或游戏更新破坏了 KMU 所依赖的内容时，KMU 只会损失相应的功能，并以通知说明。** 通知会注明该 Mod 或游戏及两个版本，说明哪项功能失效，并建议更新、降级或等待。每条通知每次游戏期间只显示一次。
  - **Nexerelin 联盟：** 更改联盟存储方式的版本不再使游戏中止。在本次游戏的剩余时间内，政治地图会将每个势力视为独立势力。
  - **LunaLib：** 若它不再向 KMU 通知设置更改，更改的设置可能要到重启游戏后才会生效。
  - **游戏更新：** 若更新改变了地图图层所访问的界面，通知会注明失效的那一项功能，例如复选框缺失。
- **在启动时修改了 KMU 所绑定内容的 Mod 不再导致游戏无法加载。** 只会损失受影响的部分，并在 `starsector.log` 中记录一行。对 Nexerelin 而言，损失的是战役中途殖民地易手时政治地图的重绘。

### 面向开发者

<details>
<summary>内部实现与地图图层框架</summary>

#### 修复

- **Fast Rendering 崩溃：** KMU 通过 KMLib `0.5.0` 读取光标，KMLib 以 `glGetFloat` 读取星图的模型视图矩阵，并在每次游戏期间报告一次过旧的 Fast Rendering 版本。
- **地图图层故障：** 星图的绘制过程及其上方的星系提示框都不捕获失败。`DrawnLayerGuard` 会在该星域上关闭出错的图层，并将该故障连同堆栈跟踪记录到日志。
- **被征服殖民地的重绘：** Nexerelin 从入侵、叛乱或移交对话框内部通知其监听器，而这些地方本身不捕获异常。重绘的失败会被捕获并记录，Nexerelin 在通知监听器之后的操作仍会执行。
- **地图图层复选框与排列对话框：** 两者都深入游戏的界面代码，更改了这些代码的游戏版本可能以两者都未捕获的方式在此失败。该失败会记录在 `starsector.log` 中。
- **宣称视图的重绘：** 增量重绘依据的是星系的持有者，而非其宣称方。
- **计数行：** 游戏不会高亮紧挨前一个单词的片段，而每个分隔符都以空格开头。灰色部分从破折号开始。
- **反射探测追踪：** 复选框和星图自身状态的警告未被纳入重置。
- **`data/config/kmu/installations.csv` 中被注释掉的行会被忽略**：实体类型以 `#` 开头的行不会进入表格，与游戏自身表格注释掉行的方式一致。

#### 变更

- **启动绑定：** KMU 启动装配的每个环节本已在各自的边界内运行，该边界现在也会捕获链接错误，即被移动或重命名的绑定所表现出的失败形式。KMU 的 Nexerelin 监听器实现了 Nexerelin 自身的一个接口，因此改变该接口的版本会使每次加载失败。

</details>

### 公共契约变更（**破坏性**）

以下变更都不影响玩家：每个 LunaLib 字段 ID 和保存在星域内存中的每个值都保持原有拼写，因此设置和存档可原样沿用。它们影响的是基于 KMU 框架构建地图图层的 Mod。

- **地图图层框架分为三层**：
  - `kmu.maplayers.base`：基础层。
  - `kmu.maplayers.ownermap`：新增，任何按所有者键为星系着色的图层所基于的管线，所有者键可以是势力、势力组，或图层用来标识星系的任何其他值。它不得导入政治地图，也不得导入 `kmu.mods`。
  - `kmu.maplayers.politicalmap`：KMU 的政治地图，构建于该层之上的一个图层。
- **移动的包**：
  - `kmu.maplayers.politicalmap.base` 下的所有内容移至 `kmu.maplayers.ownermap` 下：
    - `holding`、`owners` 和 `owners.holders`：读取星域，以及从中读出的所有者。
    - `picker` 和 `preferences`：集团选择器的模型，以及图层按存档保存的主体选项。
    - `render`，连同 `clusters`、`hover`、`labels`、`ribbon` 和 `style`。
    - `ribbon`、`sidebar` 和 `tooltip`。
  - 政治地图自身的部分除外，它们位于 `kmu.maplayers.politicalmap`：
    - `PoliticalMapLayer`、`PoliticalMapInstaller` 和 `PoliticalMapStanding`，位于该包本身。
    - 其规则，位于 `dominance`（含 `standings` 和 `weighting`）、`claims`、`views`、`holders`、`tooltip`、`refresh` 和 `render`。
- **随移动而重命名的层类型**：
  - `PoliticalMapView` 改为 `OwnerPaintedView`，`PoliticalMapViewRegistry` 改为 `MapLayerViewRegistry`。
  - 以下类型将 `PoliticalMap` 换为 `OwnerMap`：
    - `PoliticalMapOverlayRenderer`、`PoliticalMapCache`、`PoliticalMapDrawables` 和 `PoliticalMapRebuildDecider`。
    - `PoliticalMapBandLayout`、`PoliticalMapCategory`、`PoliticalMapBodyControls`、`PoliticalMapInhabitation` 和 `PoliticalMapHoverHighlightSource`。
  - 区域构建：
    - `PoliticalMapTerritories` 改为 `OwnerMapClusters`。
    - `TerritoryBuilder` 改为 `OwnerMapBuilder`，`TerritoryBuildInputs` 改为 `OwnerMapBuildInputs`。
    - `FactionTerritoryBuilder` 改为 `ClusterGroupBuilder`。
  - 刷新：
    - `StandingPoliticalMap` 改为 `StandingOwnerMap`，`StalePoliticsDisturbance` 改为 `StaleOwnerMapDisturbance`。
    - `IncrementalPoliticsRefresh` 改为 `IncrementalOwnerRefresh`。
  - `BlocStyleDecision`、`BlocStyleResolver` 和 `BlocStyling` 将 `Bloc` 换为 `Owner`。
  - `DominantHolder` 改为 `SystemOwner`，`PoliticalMapPreviewHighlightRenderer` 改为 `SpotlightPreviewHighlightRenderer`。
- **`OwnerPaintedView` 就其所有者向视图询问两件事**：
  - `resolveViewReading(SectorAPI)` 为新增且必需。它返回一个 `ViewReading`——视图、其 `OwnerReading` 及其 `OwnerSource`——三者在视图实时读取内容的同一次采样下一并解析。
  - `resolveCategories()` 为新增且必需：单元划分为哪些类别。
  - `resolveViewRecedeAdjustment(ScreenMemoryScope)` 为新增，默认不退后任何内容。
  - `resolveGrouping()`、`resolveHolderProvider()`、`resolveRibbonPlanner(RibbonPlanInputs)`、`shouldUseIndependentStyle`、`resolveBlocStyleAdjustment`、`resolveName` 和 `computeAllianceContentRevision` 已从该接缝移除。
  - `buildBlocPickerRead` 在分组之外还接受视图的 `OwnerReading`。
- **绘制持有者的视图实现 `kmu.maplayers.ownermap.owners.holders.HolderPaintedView`**：它声明 `resolveGrouping()`、`resolveContestGrouping()`、`resolveHolderProvider()`、`resolveSystemHolderResolveSource()`、`resolveRibbonPlanner(RibbonPlanInputs)` 和 `resolveOwnerReading(SectorAPI, HolderGrouping)`，该接口在分组的同一次采样下由它们组装出 `resolveViewReading`。`DominancePaintedView` 和 `ClaimsView` 都是持有者视图。
- **该层不再读取殖民地，由图层的来源读取**：
  - `kmu.maplayers.ownermap.owners.OwnerSource` 为重建解析每个星系的所有者（`resolveOwners`），为增量批次打开逐星系的 `SystemOwnerResolve`（`openSystemResolve`），并为烘焙提供色带规划器（`resolveRibbonPlanner`），每项都基于该层交给它的 `SectorWalk`——重建唯一的 `SectorPassIndex`，以及切分单元时所用的可见性规则。`SectorWalk.readReadingOpenedBy` 保存来源在该遍历上打开的读取结果，每个来源一份，因此来源在两次遍历之间不持有任何状态。
  - `owners` 中的 `ResolvedOwners` 是来源对整个星域的回答：所有者、斜线填充和不填充的星系、有人居住的星系，以及聚焦所有者的存在位置。`ResolvedHolding` 已移除。
  - `HolderOwnerSource` 是绘制持有者的图层所用的来源。它每次遍历打开一个 `HolderPass`，由该遍历保存，并通过图层的 `HolderProvider` 和 `SystemHolderResolve` 基于该通道回答所有问题。
  - `SystemHolderResolveSource.openResolveOver` 接受批次的 `HolderPass`，`SystemHolderResolve` 只回答 `resolveHolderIn`。
  - `ClaimsView` 通过 `ClaimSystemHolderResolve` 按宣称方重新推导被标记的星系：`SectorClaims.resolveClaimingHolderIn` 按 `resolveClaimingHolderBySystemKey` 应用于整个星域的同一规则回答单个星系。
  - `OwnerMapBuilder.resolveHolding` 改为 `resolveOwners(OwnerSource, SectorWalk, ContentInputs)`，`buildClusters` 接受 `ViewReading` 和 `ResolvedOwners`，取代通道、视图和持有结果。
  - `OwnerMapCache` 接受一个 `CellSeedRule`，取代诊断用提供者和逐星系解析来源，`OwnerMapLayerRenderer.createForLiveScreen` 随之改变。诊断叠加层通过当前视图自身的来源读取所有者。
  - `IncrementalOwnerRefresh.applyStaleOwnerUpdates` 接受批次的 `SectorWalk`，不再接受解析来源；批次向现有构建解析时所用的来源询问。
  - `CellRibbonsBaker.createForPass` 和 `CellRibbonSource.createForPass` 接受 `SectorWalk`，烘焙向构建的所有者来源索取规划器。
  - `DebugBorderTracingBuilder.buildDebugDrawables` 接受已解析的所有者和类别。`ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector` 改为 `rebuildDiagnosticClusterAnchors`，接受 `ClusterLabelStylingSnapshot.resolveForTracing` 为所追踪的所有者解析出的样式。
  - `SystemOccupancy` 以所有者表述：`getHolderBySystemKey`、`readHolderOf`、`recordHolderOf` 和 `selectUnheldSystemKeysAmong` 改为 `getOwnerBySystemKey`、`readOwnerOf`、`recordOwnerOf` 和 `selectUnownedSystemKeysAmong`。`SystemOccupancy.selectUnheldSystemKeysIn` 改为 `SystemOwner.selectUnownedSystemKeysAmong`，`SpotlitBlocs.isBlocPresentIn` 为新增。
  - `ClusterLabelStylingSnapshot.holderBySystemKey` 改为 `ownerBySystemKey`。
- **哪些星系生成单元由图层自行声明**：`kmu.maplayers.base.geometry.CellSeedRule` 为新增，`SEED_DRAWN_SYSTEMS` 是底层自身的规则。`CellGeometryCache.updateFromSector` 和 `PartitionSites.collectSitesFrom` 接受该规则，`DrawnSystemPositions.collectLivePositions` 新增一个接受成员规则的重载。
- **该层向图层询问其所有者**：
  - `kmu.maplayers.ownermap.owners.OwnerReading` 回答每个所有者的色调、名称、徽记、退后和类别，以及无主或已退后单元所依据的色调，每次重建解析一次。`HolderOwnerReading` 是政治地图的实现。
  - `kmu.maplayers.ownermap.render.style.OwnerCategories` 声明存在哪些类别及各类别的样式、每个有主类别的名称样式、全强度类别，以及无主单元归入的类别。`HolderCategories` 声明四个 `OwnerMapCategory` 值。
  - `OwnerPalette` 是该层的一对色调，在 `MapPalettes`、`MapStyling`、`ResolvedBlocPaint`、`BlocPaletteReader`、`BlocPresence` 和标签样式中取代 KMLib 的 `FactionPalette`。
  - `SystemOwner` 由一个所有者 ID 和一个 `OwnerPalette` 组成：`factionId` 改为 `ownerId`，两个颜色分量合并为一个 `palette`，`resolvePalette` 已移除，`mapFactionIdBySystemKey` 改为 `mapOwnerIdBySystemKey`。`resolveForBloc` 改为 `SectorBlocPalettes.resolveOwnerOf`。
  - `ViewGrouping` 改为 `ViewReading`，在视图之外还携带读取结果和所有者来源。`ClusterLabelStylingSnapshot` 改为携带类别和读取结果以取代它。
  - `OwnerStyleDecision` 携带所有者绘制时所用的类别，而非其是否采用非势力团体样式；`OwnerStyleResolver.resolveBlocStyleDecision` 和 `OwnerStyling.resolveFrom` 接受读取结果和类别。
  - `RenderStyleReader.readRenderStyle` 接受图层的类别和采样的偏好设置；`BlocNameStyles` 为每个类别一个名称样式，`readFromLunaSettings` 已移除；`FactionlessStyleResolver.resolveCategoryOf` 改为 `isSettledSystem`；`ClusterAnchorsBuilder.rebuildClusterAnchors` 不再接受星域参数。
  - `OwnerMapBuildInputs.wasBuilt()` 为新增：对首次构建失败后所用的占位对象返回 false，增量刷新不会合并到该占位对象中。
- **帧序列归框架所有**：
  - `PoliticalMapLayerRenderer` 已移除。`kmu.maplayers.base.render.SequencedMapLayerRenderer` 基于图层提供的 `MapLayerFrameParts`（让位读取、一个 `MapFrameCache`、一个 `MapFrameCompositor`、该图层的 `MapLayerHoverGates` 及其悬停框）运行每个着色图层都要运行的帧：让位、刷新、每个渲染过程的光标读取、每个层带的着色，以及悬停框。
  - `OwnerMapLayerRenderer.createForLiveScreen` 将所有者地图的各部分组合为一个整体并返回。
  - `OwnerMapCache` 是所有者地图的 `MapFrameCache`：`refresh` 改为 `refreshDrawLists`，`resolveHoverTargets` 为新增。
- **图层的状态归其自身所有**：
  - `MapLayerViewRegistry` 是图层基于自身的存档键、视图、默认视图和宿主标签页构建的实例，取代静态成员。`getActiveView()` 已移除：`resolveActiveViewOn(ScreenLayerPicks)` 针对一帧读取一次的界面作答。
  - 图层持有的按星域划分的部件通过 `SectorMapMachinery.resolveLayerMachinery(layerId, type, make)` 获取。
  - `SelectableBlocCache.resolveBlocCacheIn` 接受图层 ID。
  - `FilterSelectionHeal.healStaleSelectionAgainstActiveView` 接受星域及其据以修复的注册表；`healStaleSelectionAgainstLiveSector(registry)` 是供未持有星域的调用方使用的入口。
  - `OwnerMapBodyControls.buildViewSelector` 接受其主体所构建的星域，切换视图时据此修复聚焦。
  - `PoliticalMapLayer` 通过其视图构造，而不是单例。
- **主体偏好设置归图层所有**：
  - `NameFormatPreference`、`UninhabitedOutlinePreference` 和 `RecedePreferences` 是基于图层所命名的键的实例，作为 `OwnerMapBodyPreferences` 一并交给该层。
    - 它们的静态成员已移除，`RecedePreferences.FILTER` 和 `ALLIANCE_NON_ALLIED` 集合也已移除。
    - `OwnerMapLayerRenderer.createForLiveScreen`、`OwnerMapCache`、`OwnerMapRebuildDecider` 和 `ContentInputs.sampleForView` 接受它们。
  - `ContentInputs.allianceRecedeAdjustment` 改为 `viewRecedeAdjustment`。
  - `FactionNameFormatChoice.fromKeyOrDefault` 已移除：该选项是 KMLib 的 `PersistedChoice`，通过 `PersistedChoices.fromKey` 读回。
- **分组与机制改用该层自身的术语**：
  - `HolderGrouping`：
    - `allianceNameByBlocId` 改为 `groupNameByBlocId`，`resolveAllianceName` 改为 `resolveGroupName`。
    - `isAlliance` 改为 `isGroupedBloc`，`hasAnyAlliance` 改为 `hasAnyGroupedBloc`。
  - `HolderPass.openClaimReaderThrough` 改为政治地图的 `PassClaimReaders.openClaimReaderOver`。
  - `ColonyQualifierFacts.isHoldingTheClaim` 改为 `leadingFinding`：着色图层置于其他所有结论之前陈述的结论，已措辞完毕，或为 null。`SystemColonyReading.readQualifierFacts` 负责组装它。
  - `FactionTooltipLine.buildCountedFactionLine` 接受该势力是否参与权重计算，未参与的势力以低调方式绘制。
- **轮询、悬停门控与殖民地移交**：
  - `MapLayerSectorWatcher` 为抽象类。图层安装自己的子类，因为引擎按确切类移除瞬态脚本，共用一个类会使一个图层的轮询把另一个图层的轮询逐出。
  - 悬停门控：
    - `PoliticalMapHoverGates` 已移除：图层通过 `kmu.maplayers.base.hover.MapLayerHoverGates` 回答其自身的光标开关，`SharedOwnerMapHoverGates` 则依据那一组共享的所有者地图悬停开关来回答。
    - `MapLayerHoverGates.isCursorReadNeeded()` 为默认方法。
  - 殖民地移交：
    - `PoliticalMapMarketTransferListener` 实现 KMU 自身的 `kmu.starsector.listeners.MarketTransferListener`，而非 Nexerelin 的 `InvasionListener`。
    - 在星域上注册的该类型监听器会收到 Nexerelin 在该星域移交的每个殖民地的通知。
- **政治地图的悬停框与主导计算过程**：
  - `SystemStandingsTooltip` 和 `SystemClaimContestTooltip` 已移除，分别并入其唯一的子类 `SystemDominationTooltip` 和 `SystemClaimTooltip`。`LiveVisibilityClaimBreakdownReader` 也已移除：宣称悬停框基于悬停所在的星域打开其读取器。
  - `DominancePass.over` 改为 `createOver`，`MarketProximityTieBreak.forSystem` 改为 `createForSystem`。`DominancePass.readFromLunaSettings` 已移除。
  - `FilteredPolitics.resolveFilteredHolder` 返回 `HolderResolution`。
- **设置读取器与字符串键**：
  - 读取器：
    - `KmuPoliticalMapDiagnosticsSettings`、`KmuPoliticalMapGeometrySettings`、`KmuPoliticalMapHighlightSettings` 和 `KmuPoliticalMapRibbonSettings` 将 `PoliticalMap` 换为 `OwnerMap`。
    - `KmuPoliticalMapTerritorySettings` 改为 `KmuOwnerMapStyleSettings`。
  - 取值方法：
    - `getPoliticalMap*` 改为 `getOwnerMap*`。
    - `getPoliticalMapAllianceMutedOpacityModifier` 除外，它改为 `getOwnerMapMutedOpacityModifier`。
    - 以及 `shouldDecivilisedSystemsDrawTerritory`，它改为 `shouldCountDecivilisedSystemsAsPopulated`。
  - 该层用于标注其控件的十九个 `KmuStringKeys.POLITICAL_MAP_*` 常量改为 `OWNER_MAP_*`，其 `strings.json` 键也随之更改。
- **悬停框状态可以为其结论着色**：`CellTooltipQualifier` 携带一个 `findingColour`，通过 `drawsFindingIn(Color)` 设置，用于颜色本身即为事实的结论，例如关系等级。未设置时，结论以悬停框的金色显示。规范构造函数在 `findingText` 与 `trailingWordText` 之间接受该参数。
- **渲染过程通过其星域的守卫访问已绘制的图层**：`MapLayerRegistry.resolveDrawnMapRenderer` 已移除。位于 `kmu.maplayers.base.render` 的 `DrawnLayerGuard.resolveGuardIn(machinery)` 将该星域上已绘制图层的渲染器交给渲染过程的工作，并在该星域上关闭从中抛出异常的图层。

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
