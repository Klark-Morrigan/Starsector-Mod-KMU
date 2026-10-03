# 更新日志

本文件记录 KMU 的所有重要变更。格式遵循 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## 索引

- [未发布](#unreleased)
- [0.1.2](#012---2026-09-16)
- [0.1.1](#011---2026-09-15)
- [0.1.0](#010---2026-09-14)

## [Unreleased]

### 新增

- **简体中文**。发布版本附带第二个压缩包 `KMU-<version>-zh-hans.zip`，其中的设置界面、地图图层侧边栏、悬停框、游戏内通知以及启动器 Mod 列表中的条目均为简体中文。使用前需先将[中文本地化](https://github.com/TruthOriginem/Starsector-Localization-CN)覆盖安装到 `starsector-core`：游戏自带的字体不含中文字符，没有中文本地化，每个中文字符都会显示为 `?`。从固定选项列表中选择的设置，其选项保持英文，因为 LunaLib 保存的是选项的标签，所以你的设置可以在英文版和中文版压缩包之间沿用。发布说明中也附有每个版本变更的中文版本，中文压缩包中的 `CHANGELOG.md` 也是中文的。
- *地图 - 政治 - 外观* 中的 **荒蛮星系 - 绘制为领土** 设置，默认开启。关闭后，已揭示的荒蛮世界不再被视为其所在星系有人居住：该星系会绘制为无人星系而非领土，其所有者不再出现在该图层的选择器中，也不计入存在色带和统计中的殖民地规模。该世界本身仍会被发现、仍会列出，并仍会在星系提示框中显示名称。由 **NoticeMeSenpai** 在 [**USC**](https://discord.com/channels/187635036525166592/1549091275167240272/1549750490617741395) 提出请求。
- **地图标签、悬停框和地图图层侧边栏改用能显示其文字的字体绘制**。当 KMU 请求的字体无法绘制某个势力、星系或殖民地的名称，或 KMU 显示的某个词语时（例如语言包的字体缺少部分字符），这些文字会改用同一字体的较小字号或游戏自身的默认字体绘制，而不再显示为问号。安装中文本地化后，地图上的势力名称会从最大字号（中文本地化未替换这一字号，保留了游戏自带的版本）降为次一级字号。英文安装下没有任何变化。

### 变更

- **Mod 列表中的 Mod 名称为 Klark Morrigan 的实用工具 (KMU)**，使游戏内其他地方及其设置中使用的缩写与全称并列显示。更新检查器显示相同的名称。
- **发布压缩包以其语言命名**：`KMU-<version>-en.zip`。KMU 的每种译文语言都以单独的压缩包发布在同一发布页面上，发布说明会注明各压缩包对应的语言。更新检查器在此变更后仍能正常工作：已安装的旧版本仍会收到此版本的发布通知。
- **荒蛮世界** 现在会被同一星系中其他势力的殖民地揭示，无论 *地图 - 可见性* 中的 **显示调查程度至少达到以下等级的荒蛮世界** 设为何值。该设置现在只决定你自己的调查要求：设为 *Seen* 时访问过该星系即可，设为 *Preliminary* 或 *Full* 时则必须调查该世界本身。此前，较高的两个等级也不接受邻近殖民地提供的信息，导致一个对住在其旁的所有人都显而易见的世界仍不会出现在地图上。
- **显示调查程度至少达到以下等级的荒蛮世界** 的默认值现为 *Full*，而非 *Seen*。除非你已自行设置过该项（此时保留你的值），否则荒蛮世界只有在你调查过它，或其所在星系已有人居住时才会显示名称。仅仅飞经不再会使其出现在地图上。由于邻近殖民地提供的信息在每个等级下都有效，常见情况仍能覆盖，而调查孤立的世界现在也变得值得。
- **修改了 KMU 所绑定内容的 Mod 不再导致游戏无法加载**。KMU 启动装配的每个环节本已各自在独立的边界内运行，因此某个环节失败只会损失它自己，不影响其他部分，但这仅限于以抛出异常的方式失败的情况。若另一个 Mod 移动或重命名了该环节所绑定的内容，失败会以链接错误的形式出现，而边界不会捕获它，于是一个这样的 Mod 就会使整个加载失败。现在两者都会被捕获。最典型的例子是 Nexerelin：KMU 注册了一个实现 Nexerelin 自身接口的监听器，以便在战役中途殖民地易手时重绘政治地图；此前，若某个 Nexerelin 版本改变了该接口，只要两个 Mod 同时启用，每次加载都会失败。现在只会损失该监听器，并记录在 `starsector.log` 中，地图的其余部分不受影响。
- **若 LunaLib 版本导致 KMU 无法获知设置更改，会在游戏内报告**。KMU 会请求 LunaLib 在你更改设置时通知它，从而无需重新加载即可应用更改。若 LunaLib 拒绝，每次游戏期间会显示一次通知，注明 LunaLib 及其版本，并说明由此造成的影响：更改的设置可能要到重启游戏后才会生效。KMU 的其余部分照常工作。此前这只会写入 `starsector.log`，因此设置不起作用时没有任何原因提示。
- **Nexerelin 版本更改其联盟的存储方式时，不再使游戏中止**。政治地图每隔几秒检查一次是否有联盟成立或解散，而此前在检查过程中无法读取 Nexerelin 联盟的失败不会被捕获。现在，在本次游戏的剩余时间内，地图会将每个势力视为独立势力，并显示一次通知说明此情况，其他一切照常工作。
- **若游戏更新改变了地图图层从游戏界面读取的内容，会在游戏内报告**。地图图层通过深入游戏自身的代码来判断地图是否已打开、在筛选栏上放置其复选框、打开排列对话框、为游戏自身的对话框、数据百科和星系提示框让位、使其上层带保持在星云之上，以及在星景筛选开启时进行绘制，而游戏更新可能会改变这些代码。此前发生这种情况时，各项功能都会静默降级；现在会显示通知，注明 Starsector 以及 KMU 所针对的游戏版本和你正在运行的版本，说明哪项功能失效（每条通知一项，例如复选框缺失或图层不在地图上显示），并建议更新或降级游戏，或等待 KMU 更新。每条通知每次游戏期间只显示一次，其他一切照常工作。此前唯一的痕迹是 `starsector.log` 中的一行记录。
- **Fast Rendering** 版本不匹配现在会在游戏内报告，而不再导致游戏崩溃。KMU 从 Fast Rendering 的内部实现中读取光标在星图上的位置，而这些内部实现会随其版本变化。这可能以三种方式出错：KMU 查找的部分已不存在；从游戏中调用时被拒绝；或在 Fast Rendering 自己的线程上调用时被拒绝。从 Fast Rendering 0.8.9 起，第二种情况最为关键：该版本声明了所有入口点，并拒绝其未实现的那些，因此在绘制地图之前都察觉不到版本不匹配，而一旦绘制，它就会从 KMU 自身的代码内部导致游戏崩溃。现在三种情况都会被捕获。若无法再读取光标位置，每次游戏期间会显示一次通知，注明 Fast Rendering 及两个版本，星图会继续绘制，但不再直接响应光标：没有星系高亮和提示框，不过地图图层侧边栏仍会响应光标。其他一切均不受影响，你的存档也不会被改动。此前，版本不匹配会中止地图的渲染过程，并在错误信息中指向 KM 的代码，使 Fast Rendering 的版本不匹配看起来像是 KMU 的 bug。
- **每项设置的描述都以单独一行的默认值结尾。** 只有当片段两侧的字符是空白或 ASCII 标点时，游戏才会高亮该片段，因此在句号不属于这两类的语言中，结束句子的默认值无法高亮。换行符视为空白。

### 修复

- **`data/config/kmu/installations.csv` 中被注释掉的行会被忽略**。实体类型以 `#` 开头的行（游戏自身的表格即以此方式注释掉一行）此前会被读取为以该注释命名的类型的条目。现在它不会进入表格，与用于分隔文件内容的空行一样。
- **政治地图重绘被征服殖民地时出现的故障不再导致游戏崩溃**。Nexerelin 会在其自身移交流程的末尾通知 KMU 殖民地易手，通知发自入侵、叛乱或移交对话框内部，而这些地方本身不捕获异常，因此政治地图重绘该殖民地时的失败会传到引擎，并以指向 Nexerelin 的错误使游戏崩溃。现在该失败会被捕获并记录，Nexerelin 在通知其监听器之后的所有操作仍会照常执行。该殖民地会在几秒后地图的下一次检查时重绘。
- **地图图层中的故障不再导致游戏崩溃**。此前星图的绘制过程及其上方的星系提示框都不捕获失败，因此正在绘制的图层中的任何故障都会在地图打开时使游戏崩溃。现在出错的图层会停止绘制，直到你下次读取存档，或将 *功能* 中的 **启用地图图层** 关闭后再重新开启，该故障及其堆栈跟踪会记录在 `starsector.log` 中。地图、侧边栏和其他图层会继续绘制。
- **改变了地图界面的游戏更新不再通过地图图层复选框或排列对话框导致游戏崩溃**。两者都通过深入游戏自身的界面代码来确定自己的位置，而更改了这些代码的游戏版本可能会在此处以两者都未捕获的方式失败，在地图打开时使游戏崩溃。现在复选框不会出现在该界面上，并在 `starsector.log` 中记录一行，排列对话框则会关闭。
- **在政治地图的 *宣称* 视图中，在被宣称的星系中建立或失去殖民地后，该星系保持其宣称方的颜色**。地图会在几秒内自行重绘发生变化的星系，而在 *宣称* 视图中，这次重绘依据的是谁持有该星系而非谁宣称它，因此该星系在下一次完整重绘之前一直显示持有者的颜色。
- **开启 *开发* 中的 **反射探测追踪** 后会重新输出所有关于游戏界面的警告**。此前地图图层复选框和星图自身状态的警告被遗漏，因此在其中之一出错后再开启追踪，不会增加任何解释原因的记录。
- **市场条件管理器的计数行以灰色绘制可用数与总数。** 它们及其前面的分隔符本应显示为灰色，却以正文颜色绘制：游戏不会高亮紧挨前一个单词的片段，而每个分隔符都以空格开头。灰色部分从破折号开始。

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
