# KMU in Simplified Chinese (简体中文)

The terminology reference for KMU's `zh-hans` bundle:
the words the Chinese core localisation already uses for vanilla concepts,
and the translations settled for KMU's own.
Check a term here before translating a new string,
and add a row when a new term is settled.
How bundles are built and checked is in the root README's
[Localisation](../../README.md#localisation) section.

This reference builds on
[KMLib's](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md),
the base every KM mod's Chinese bundle follows:
the rules, the vanilla words the shared library's own text touches,
and the terms it draws itself, the compatibility notice above all.
Read both for the full picture.
Where both list a term, they agree.

## Index

- [What this folder holds](#what-this-folder-holds)
- [Rules](#rules)
- [Vanilla terms](#vanilla-terms)
  - [Screens and the map](#screens-and-the-map)
  - [Space](#space)
  - [Colonies and markets](#colonies-and-markets)
  - [Factions and relations](#factions-and-relations)
  - [Surveys](#surveys)
  - [Time](#time)
  - [Game and mods](#game-and-mods)
- [Vanilla mechanics without a name](#vanilla-mechanics-without-a-name)
- [KMU terms](#kmu-terms)
  - [Features and screens](#features-and-screens)
  - [The political map](#the-political-map)
  - [Terms borrowed from HOI4](#terms-borrowed-from-hoi4)
  - [Hovering and tooltips](#hovering-and-tooltips)
  - [Settings](#settings)
  - [Settings tabs](#settings-tabs)
  - [Console commands](#console-commands)
  - [The forum banner](#the-forum-banner)
- [Adding a string](#adding-a-string)
- [Translating the changelog](#translating-the-changelog)

## What this folder holds

| File | What it is |
| --- | --- |
| [strings.json](strings.json) | Every string KMU draws in game: the sidebar, hover boxes, dialogs and notices. |
| [LunaSettings.csv](LunaSettings.csv) | The LunaLib settings screen: tab names, captions, setting names and descriptions. |
| [mod_info.json](mod_info.json) | The launcher's mod list entry: `name` and `description` only, merged over the base. |
| [CHANGELOG.md](CHANGELOG.md) | A full translation of the root [CHANGELOG.md](../../CHANGELOG.md): every version, every section. The Chinese zip ships it, and each release's notes show its section for the version. |

Players of this bundle are expected to have the
[Chinese core localisation](https://github.com/TruthOriginem/Starsector-Localization-CN)
laid over `starsector-core`, as [manifest.json](../manifest.json) records under `coreLocalisation`.
The game's own fonts hold no Chinese characters;
the core localisation replaces them, and without it every Chinese character draws as `?`.

## Rules

[KMLib's rules](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#rules)
hold for every KM mod's bundle, KMU's included:
vanilla's word wins, Radio options and proper nouns stay as written,
full-width punctuation, no em dash, numbered slots where word order moves, an ASCII space wherever a highlighted run touches Chinese, and defaults as `[默认值：X]` on a line of their own.
A Radio's description then lists each option's translation after a blank line.
KMU's own examples of them:
`Galatia 学院` and `Naraka 星系` for Latin proper nouns,
`UI palette` for a quoted Radio option,
`市场条件管理器 (MCM)` for a parenthesised suffix.

KMU adds one:

- **HOI4's word for what KMU borrowed from HOI4.**
  A term the political map takes from Hearts of Iron IV uses HOI4's official Simplified Chinese;
  see [terms borrowed from HOI4](#terms-borrowed-from-hoi4).

## Vanilla terms

What the core localisation writes for each vanilla English term.
Every row was read off the core localisation itself,
by pairing its text with vanilla's at the same location:
the same `strings.json` key, the same CSV row, or the same string constant in the same class of the game jars.
The source column names where it was found.
The core localisation at `startup-optimization` 2026.09.04 was the edition read;
the typeface editions share its text.

### Screens and the map

| English | 简体中文 | Source |
| --- | --- | --- |
| Map (the screen, its tab) | 星图 | `starfarer_obf.jar` tab bar; "open map" is 打开星图 |
| Intel (the screen, its tab) | 情报信息 | `starfarer_obf.jar` tab bar; the screen is 情报信息界面 |
| Codex | 数据百科 | `starfarer.api.jar`, "Codex Update" is 数据百科更新 |
| Starscape (map filter) | 星景 | `starfarer_obf.jar` map filter row |
| Fuel range (map filter) | 续航距离 | map filter row; its tooltip is 显示燃料可用范围 |
| Names (map filter) | 名称 | map filter row |
| Exploration (map filter) | 探索状态 | map filter row |
| Inhabited (map filter) | 有人居住 | map filter row |
| Legend | 图例 | map filter row |
| System map | 星系地图 | `starfarer_obf.jar`, "Go to system map" is 进入星系地图 |
| Intel (an item, not the screen) | 情报 | `BlueprintIntel`, among 65 places; the screen is 情报信息 |
| Radar | 雷达 | story text in `rules.csv` and `market_conditions.csv`; vanilla draws no radar label |

### Space

| English | 简体中文 | Source |
| --- | --- | --- |
| Sector | 星域 | Persean Sector is 英仙座星域 |
| Star system | 星系 | `starfarer.api.jar`; in tooltips as `Naraka 星系` |
| Unvisited star system | 未访问过的星系 | `starfarer_obf.jar` map legend |
| Planet | 行星 | `starfarer.api.jar` |
| Hyperspace | 超空间 | `starfarer_obf.jar` |
| Nebula | 星云 | `starfarer.api.jar` |
| Gate | 星门 | `descriptions.csv`, Active Gate is 激活的星门 |
| Station | 空间站 | `starfarer.api.jar`; Jangala Station is `Jangala 空间站` |
| Orbital Station | 轨道空间站 | `industries.csv` |
| Star (the body) | 恒星 | `starfarer.api.jar` planet dialog; `planets.json` names each kind, as 黄矮星 |
| Black hole | 黑洞 | `planets.json` `black_hole` |
| Jump point | 跳跃点 | `JumpPointInteractionDialogPluginImpl` in `starfarer.api.jar` |
| Slipstream | 滑流 | `SlipstreamTerrainPlugin`; also 超空间滑流 |
| Terrain | 地形 | `SlipstreamTerrainPlugin`; "special terrain" is 特殊地形 |
| Entity | 实体 | `custom_entities.json`, "Unidentified Entity" is 不明实体 |
| Fleet | 舰队 | `strings.json` `fleetInteractionDialog` |
| Sensors, sensor range | 传感器, 探测范围 | `starfarer_obf.jar` fleet tooltip, "Sensor Range" is 探测范围 |

### Colonies and markets

| English | 简体中文 | Source |
| --- | --- | --- |
| Colony | 殖民地 | `starfarer_obf.jar` |
| Colony size | 殖民地规模 | `starfarer_obf.jar` |
| Size | 规模 | `starfarer_obf.jar` |
| Market | 市场 | `starfarer_obf.jar` |
| Market size | 市场规模 | `starfarer_obf.jar` |
| Population | 人口 | `starfarer_obf.jar` |
| Stability | 稳定性 | `starfarer_obf.jar`, "Stability: %s" is 稳定性：%s |
| Colony conditions | 殖民地条件 | `starfarer_obf.jar` |
| Planetary conditions | 行星状况 | `starfarer_obf.jar` |
| Hidden | 隐藏 | `starfarer_obf.jar`; "Comm Relay (hidden)" is 通讯中继站 (隐藏) |
| Outpost | 前哨站 | `market_conditions.csv` |
| Decivilized (condition) | 荒蛮之地 | `market_conditions.csv` |
| Decivilized Subpopulation (condition) | 法外之地 | `market_conditions.csv` |
| Abandoned Station (condition) | 废弃空间站 | `market_conditions.csv` |
| Military Base | 军事基地 | `industries.csv` |
| Patrol HQ | 巡逻队总部 | `industries.csv` |
| Patrol (the fleet) | 巡逻队 | `rules.csv`; 巡逻队 in 95 places, 巡逻舰队 in 6 |
| Industry (a colony building) | 工业设施 | `IndustryListPanel`, "Industries on %s:"; 产业 only where the English means an economic sector |
| Military (facilities) | 军事设施 | `HAColonyDefensesFactor` in `starfarer.api.jar` |
| Militarized (ships only) | 军事化 | `hull_mods.csv` `militarized_subsystems`, the Militarized Subsystems hullmod (军事化子系统) for civilian hulls; vanilla uses the word for ships alone, so it never names [KMU's militarised station](#the-political-map) |
| Commodity | 商品 | `CommodityPanel` in `starfarer_obf.jar` |
| Population center (a large hub) | 人口中心 | `market_conditions.csv` `population_10`, "a dominant population center"; vanilla uses it for large hubs, as 主要的人口中心, so it never names [KMU's population center](#the-political-map) |
| Docked (at a market) | 停靠 | `starfarer_obf.jar` refit, "Must be docked at a market" |
| Can not be colonized | 无法殖民 | `PlanetSurveyPanel`; vanilla has no positive form |
| Pirate | 海盗 | Pirate Station is 海盗空间站 |
| a faction's presence in a system | 殖民地, by paraphrase | "has no presence in this system" is 该星系中并没有……的殖民地 |

### Factions and relations

| English | 简体中文 | Source |
| --- | --- | --- |
| Faction | 势力 | `starfarer.api.jar` |
| Independent (the faction's name) | 非势力团体 | `independent.faction` `displayName` |
| Attitude (the relation number) | 关系 | `starfarer_obf.jar` faction screen, "Attitude: " is 关系： |
| Relationship | 关系 | `starfarer_obf.jar` |
| Known allies | 已知的盟友 | `starfarer_obf.jar` faction screen |
| Known enemies | 已知的敌人 | `starfarer_obf.jar` faction screen |
| Your faction (the player's) | 你的势力 | `starfarer_obf.jar` flag picker, "Select a flag to represent your faction"; vanilla never writes 玩家势力, which KMU keeps for the player faction as a category, as the settings write Mod 玩家势力 for modded player factions |
| Neutral (the faction's name) | 中立 | `neutral.faction` `displayName` |
| Hegemony | 霸主 | `hegemony.faction` `displayName` |
| Luddic Church | 卢德教会 | `luddic_church.faction` `displayName`; its long name is 银河救赎教会 |
| Unclaimed (territory) | 无宣称 | `PerseanLeagueHostileActivityFactor`, "unclaimed territory" is 无宣称领土 |

The nine reputation levels, from `RepLevel` in `starfarer.api.jar`:

| Vengeful | Hostile | Inhospitable | Suspicious | Neutral | Favorable | Welcoming | Friendly | Cooperative |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 仇恨 | 敌对 | 冷淡 | 怀疑 | 中立 | 良好 | 欢迎 | 友好 | 合作 |

### Surveys

| English | 简体中文 | Source |
| --- | --- | --- |
| Survey | 调查 | "Survey Data" is 调查数据 |
| Unsurveyed | 未被调查 | `starfarer_obf.jar` planet list |
| Preliminary survey completed | 已初步调查 | `starfarer_obf.jar` planet list |
| Preliminary survey data | 初步调查数据 | `Misc` in `starfarer.api.jar` |
| Fully surveyed | 已全面调查 | `starfarer_obf.jar` planet list |
| Full survey data | 完整调查数据 | `starfarer_obf.jar`; vanilla uses both 全面 and 完整 for "full" |

### Time

| English | 简体中文 | Source |
| --- | --- | --- |
| Today | 今天 | `Misc` in `starfarer.api.jar` |
| 1 day ago | 1 天前 | `Misc` |
| N days ago | N 天前 | `Misc` |

### Game and mods

| English | 简体中文 | Source |
| --- | --- | --- |
| Mod | Mod | `starfarer_obf.jar`, "Found mod: %s" is 发现 Mod：%s |
| Save (a saved game) | 存档 | "Load last save" is 读取最近的存档 |
| Starsector | 远行星号 | `starfarer_obf.jar` title, "Welcome to Starsector!" is 欢迎来到远行星号！; most of the game keeps the Latin name |
| Launcher | 启动器 | `StarfarerLauncher` in `starfarer_obf.jar` |
| Sound volume | 音量 | `starfarer_obf.jar` title settings, "Sound volume:" is 音量： |

## Vanilla mechanics without a name

Mechanics the game runs but never names to the player.
No vanilla text holds them, in English or Chinese, so there is nothing to pair a translation against.
KMU names them, and the code column says where vanilla keeps each one.

| English | 简体中文 | Vanilla code | Note |
| --- | --- | --- | --- |
| territorial, non-territorial (a faction) | 领土, 非领土 | `territorial` in a faction's `punitiveExpeditionData`, read by `Misc.getClaimingFaction` | only a territorial faction claims a system dynamically; 非领土势力 in prose; 领土 is also the word for territory |
| unconditional claim | 无条件宣称 | the `$claimingFaction` memory key, `MemFlags.CLAIMING_FACTION` | wins over any dynamic claim |
| dynamic claim | 动态宣称 | `Misc.getClaimingFaction`, where no unconditional claim is set | 动态, changing as things change, the opposite of 无条件 |
| sector memory | 星域记忆 | the game's `$` memory keys | where unconditional claims are set; from 星域 |
| discoverable | 可发现 | `SectorEntityToken.isDiscoverable()` | kept off the system map until the player's sensors find it, as the bundle's visibility overrides write it |
| hyperspace anchor | 超空间锚点 | `StarSystemAPI.getHyperspaceAnchor()` | a system's point in hyperspace |
| unowned (a market the Neutral faction holds) | 无主 | a market owned by the faction `neutral`, named 中立 | a market's ownership only, never a system's, which is unpopulated, decivilised or settled instead; silent on whether anyone lives there; written beside 市场, and with 中立 where it first appears; not 未受控制, which reads as dominance, and not 无主之地, vanilla's label for Uninhabited |

## KMU terms

KMU's own concepts, settled when the bundle was first written.
Where a row builds on a vanilla word, the note says which.
The words of a release and of the Fossic thread are
[KMLib's forum terms](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#forum-terms),
which every KM mod's shares.

### Features and screens

| English | 简体中文 | Note |
| --- | --- | --- |
| Klark Morrigan's Utilities (KMU) (the mod's name) | Klark Morrigan 的实用工具 (KMU) | `KMU` is the brand: kept in every locale, never translated |
| KMU | KMU | the brand, kept in every locale |
| Sector Map Layers (the feature) | 星图图层 | from 星图 |
| map layers | 地图图层 | also drawn on the intel screen's map, hence 地图 rather than 星图 |
| Map layers (filter row tick box) | 地图图层 | sits beside 星景 and 续航距离 |
| switched off (a map layer that failed) | 已关闭 | the notice's word; 关闭 as for the map layers switched off and back on (关闭后重新开启) |
| load a save | 读取存档 | from vanilla's 读取最近的存档 |
| Political Map | 政治地图 | 政治 for political, with 地图 as in 地图图层, since it is a layer |
| No Layer | 无图层 | 无, none, before 图层: the tab that shows no layer |
| map screen | 星图界面 | the screen Tab opens; vanilla's tab reads 星图 |
| overlay | 叠加层 | the usual Chinese UI word for a layer drawn over another |
| overlay control box | 叠加层控制框 | from 叠加层, with 控制框 for the box holding its controls |
| sidebar | 侧边栏 | the usual Chinese UI word |
| tab | 标签页 | the usual Chinese UI word; 标签 alone would read as a label |
| tab bar | 标签栏 | from 标签页, with 栏 for a bar, as in 筛选栏 |
| filter row | 筛选栏 | the row holding 星景, 续航距离 and 名称 |
| Arrange Map Layers | 排列地图图层 | 排列, to put in order, before 地图图层 |
| Up / Down (arrange dialog) | 上移 / 下移 | highlighted inside the dialog's hint |
| uncheck | 取消勾选 | highlighted inside the dialog's hint |
| Market Condition Manager (MCM) | 市场条件管理器 (MCM) | from 殖民地条件 |
| market condition | 市场条件 | from vanilla's 殖民地条件, with 市场 since the manager works on markets |
| suppressed (condition) | 被压制 | the plain passive for a condition held down but not removed |
| world units | 世界单位 | literal: a distance in game space, kept apart from 像素 |
| owned by (a planet's faction) | 属于 | the game's own text has no such phrase; the faction's name follows after a space |
| list separators (the condition manager's lines) | ` ； ` between groups, ` ， ` between items | an ASCII space either side, so the highlighted counts and names beside them still highlight |
| screen | 界面 | as in 星图界面 and 情报信息界面 |
| sector map widget (the map, on whichever screen draws it) | 星图控件 | from 星图, with 控件, the control word; the settings and the notices already use it; not 地图控件, the Map - Dev header |
| control (a tick box, switch or button) | 控件 | as in 地图控件, the Map - Dev header for the map controls; not 星图控件, the map itself |
| campaign radar (where Random Assortment of Things puts its minimap) | 战役雷达 | from vanilla's 雷达 |
| minimap (Random Assortment of Things') | 小地图 | the usual Chinese word for a minimap, as the settings write it |
| docked (Random Assortment of Things hiding its minimap) | 停靠 | vanilla's word for docking at a market |
| compatibility mode | 兼容模式 | 兼容 as in the Map - Compatibility tab's 兼容性, with 模式 for mode |
| compatibility patch | 兼容补丁 | 补丁, the usual word for a patch, after 兼容 |
| mouseover detection | 鼠标悬停检测 | from 悬停 |
| view (of the political map) | 视图 | as in 联盟视图 |
| filter list, sortable filter list | 筛选列表, 可排序的筛选列表 | the sidebar's list of blocs |
| faction filter | 势力筛选列表 | 势力 before 筛选列表, the filter list |
| headline feature | 首发功能 | 首发, the first to ship, with 功能 as in the Features tab |
| read-only (by design) | 只读 | the standard computing word |
| underlying mechanics | 底层机制 | 底层, the layer beneath, with 机制 for mechanics |
| info-layer | 信息图层 | from 图层: a layer that shows information |

### The political map

Three separate axes run through the political map, each with words of its own:

- **Ownership** belongs to a market:
  the faction holding it, or 无主 for a market the Neutral faction (中立) holds.
- **Claim** belongs to a system, and is strictly vanilla's mechanic:
  宣称, with 宣称方 for its holder and vanilla's 无宣称 for a system nobody claims.
- **Dominance** belongs to a system, and is KMU's own measure of which faction prevails there:
  主导, with 主导方 for the faction dominating and 星系持有方 for the system holder it makes.

The axes are independent:
one system can be dominated and claimed at once, by the same faction or by two.
So a word of one axis never stands in for another:
主导 never says claimed, 宣称 never says dominated, and 无主 never says unclaimed.

| English | 简体中文 | Note |
| --- | --- | --- |
| Factions (view) | 势力 | vanilla's 势力 |
| Alliances (view) | 联盟 | Nexerelin's alliances; vanilla also uses 联盟 for the Persean League |
| Nexerelin (as a search word) | 势力争霸 | the name its Chinese build carries on Fossic; text keeps Nexerelin in Latin, per the rules, so the name stands only where a player searches, as a thread tag |
| Claims (view) | 宣称 | vanilla's word for a claim, as in 无宣称领土; the view shows vanilla's claims |
| claim | 宣称 | vanilla's word, as in 无宣称领土 |
| system claim (the mechanic, a claim on a whole system) | 星系宣称 | from vanilla's 星系 and 宣称; how the Claims view and the forum intro name the mechanic |
| claim holder | 宣称方 | from 宣称, with 方 for the party holding it |
| system holder | 星系持有方 | 持有, to hold, with 方 for the party; kept apart from 宣称方 |
| domination, dominance | 主导 | to prevail; kept apart from 宣称, the claim, and from 控制, a control |
| Dominated by | 主导方 | from 主导, with 方 for the party |
| Contested by | 争夺方 | 争夺, to contend for, with 方 for the party |
| Present (tooltip section) | 在场 | the plain word for being there, kept apart from the noun 存在 |
| presence | 存在 | vanilla paraphrases presence as 殖民地; KMU needs a noun |
| presence ribbon, presence band | 存在色带 | from 存在, with 色带 for a coloured band |
| territory | 领土 | the plain word, and vanilla's own in 无宣称领土 |
| core territory | 核心领土 | HOI4's own word; see [terms borrowed from HOI4](#terms-borrowed-from-hoi4) |
| cluster (a run of one owner's systems, and the name drawn over it) | 区域 | a region, since the run reads as one area on the map |
| cell | 单元 | one system's area on the map, never a UI control |
| border | 边界 | the plain word; the outer and inner borders add 国界 and 省界 |
| outer border | 外边界（国界） | the national border |
| inner border | 内边界（省界） | the province seam |
| fill | 填充 | the usual graphics word |
| hatch fill | 斜线填充 | 斜线, diagonal lines, before 填充 |
| faction systems | 势力星系 | vanilla's 势力 and 星系 |
| independent systems | 非势力团体星系 | from the independents' name, 非势力团体 |
| decivilised | 荒蛮 | from 荒蛮之地 |
| decivilised system, world, colony | 荒蛮星系, 荒蛮世界, 荒蛮殖民地 | from 荒蛮, with 星系, 世界 and vanilla's 殖民地 |
| derelict | 废弃空间站 | from the condition's name |
| uninhabited systems | 无人星系 | 无人, nobody, before vanilla's 星系 |
| unpopulated | 无人居住 | the negative of vanilla's 有人居住, Inhabited |
| non-political | 非政治 | 非, not, before 政治 |
| non-allied factions | 非同盟势力 | 非, not, before 同盟, allied, and vanilla's 势力 |
| Allied with / Friendly with | 与……结盟 / 与……友好 | 友好 is the reputation level |
| bloc | 集团 | a faction, or an alliance standing as one |
| spotlight | 聚焦 | to focus on, which spotlighting a bloc does |
| recede | 退后 | to step back, which a receded bloc does on the map |
| Muted / Desaturated | 淡化 / 去色 | 淡化, to fade, and 去色, to strip colour, the usual image-editing word |
| Rest of the sector | 星域其余部分 | vanilla's 星域 with 其余部分, the part remaining |
| stability, size, patrols (score factors) | 稳定性, 规模, 巡逻队 | vanilla words |
| Small / Medium / Large (patrols) | 小型 / 中型 / 大型 | vanilla's patrol sizes, as in 大型巡逻队 for heavy patrols |
| Same-faction market bonus | 同势力市场加成 | 加成, the usual game word for a bonus |
| Military (claim factor) | 军事 | vanilla's 军事, as in 军事基地 |
| (core) | （核心） | HOI4's own word |
| (fixed) | （固定） | the plain word for a value that does not vary |
| Full / Short / No (faction names) | 全称 / 简称 / 无 | the usual words for a full and a short name |
| faction names | 势力名称 | vanilla's 势力 with 名称, as in the Names filter |
| Columns | 列 | the plain word |
| Name, Domination, Presence, Score, Market size, Claims, Attitude (sort) | 名称, 主导, 存在, 得分, 市场规模, 宣称, 关系 | Attitude follows vanilla's 关系 |
| paint (a system, the map) | 上色 | to colour in, as the map does a territory |
| affiliation | 归属 | to belong to: the faction a market answers to |
| populated systems | 有人星系 | from vanilla's 有人居住 |
| population center (any populated market, whatever its size) | 有人居住的市场; 有人居住市场 before another noun | never 人口中心, which reads as a large hub |
| outline (an uninhabited or decivilised system's border) | 轮廓 | the plain word, as the settings write it |
| hidden system | 隐藏星系 | from 隐藏 |
| non-market entities | 非市场实体 | from vanilla's 实体 |
| domination algorithm | 主导算法 | from 主导, with 算法, algorithm |
| domination views (Factions and Alliances) | 主导视图 | from 主导 and 视图 |
| solid fill | 实色填充 | 实色, solid colour, before 填充, as the settings write it |
| no fill | 不填充 | the negative of 填充 |
| solidified control | 稳固的控制 | 稳固, firm and settled: dominance nobody contests, not a claim |
| balance of power | 力量平衡 | the usual Chinese phrase |
| political entity | 政治实体 | the usual Chinese phrase |
| military industry (a claim factor) | 军事设施 | vanilla's word |
| militarised (a station colony with an orbital station built) | 空间站殖民地建有轨道空间站, by paraphrase | never 军事化, which reads as the ship hullmod; 轨道空间站 is vanilla's Orbital Station |
| border gore | 边界地狱 | the players' word for tangled borders |

### Terms borrowed from HOI4

The political map takes some of its vocabulary from Hearts of Iron IV,
so those terms take HOI4's own official Simplified Chinese,
and a player who knows HOI4 recognises them.
Each row was read off HOI4's `localisation/simp_chinese` files by pairing them with `localisation/english` on the same key.

| English | 简体中文 | HOI4 key, file | KMU meaning |
| --- | --- | --- | --- |
| core territory | 核心领土 | `MODIFIER_ATTACK_BONUS_AGAINST_A_COUNTRY_ON_ITS_CORES`, `modifiers`: "Attack on their core territory" is 我国对其核心领土进攻 | a system its faction claims unconditionally |
| core | 核心 | `PEACE_CONFERENCE_PROVINCE_TOOLTIP_CORES`, `peace`: "Cores:" is 核心： | the tooltip marker `（核心）` |

HOI4 names a collection of cores without 的 ("Chinese core states" is 中国核心地区),
which is how the tooltip heading reads: the faction's name, then 核心领土.

### Hovering and tooltips

| English | 简体中文 | Note |
| --- | --- | --- |
| hover | 悬停 | the usual Chinese UI word |
| hover tooltip | 悬停提示框 | vanilla avoids the word "tooltip" |
| hover box | 悬停框 | from 悬停, with 框 for a box |
| hover effects | 悬停效果 | from 悬停, with 效果 for effects |
| hover highlight | 悬停高亮 | from 悬停, with 高亮, the usual word for highlighting |
| halo | 光晕 | the plain word for a glow around something |
| cell wash | 单元覆色 | from 单元, with 覆色, colour laid over |
| connecting lines (label to value) | 连接线 | the plain word |
| redacted text | 隐去文字 | 隐去, to hide away, as a redaction hides without removing |
| collapse to factions | 折叠为势力 | 折叠, the usual word for collapsing a list |
| expand system composition, market stats, patrol details | 展开星系构成, 展开市场数据, 展开巡逻详情 | 展开, the usual word for expanding, the pair of 折叠 |
| + N more | + 还有 N 项 | 还有, there are still, with 项 for items |
| last seen | 上次观测 | 观测, to observe: what the player knows was seen |
| abandoned, hidden, undiscovered, unlisted (qualifiers) | 废弃, 隐藏, 未发现, 未列出 | 废弃 from 废弃空间站 and 隐藏 from vanilla's Hidden; 未发现 and 未列出 are not yet found and not listed |
| tooltip | 提示框 | short for 悬停提示框, as KMU's strings write it |
| system tooltip | 星系提示框 | vanilla's 星系 with 提示框 |
| help tooltip | 帮助提示框 | 帮助, help, with 提示框 |

### Settings

| English | 简体中文 | Note |
| --- | --- | --- |
| Available settings | 可用设置 | the heading of each tab's contents note |
| Default | 默认值 | written `[默认值：X]` |
| true / false (Boolean defaults) | 开启 / 关闭 | a switch's on and off, more natural than 真 and 假 |
| Enable X | 启用 X | the usual Chinese UI word |
| opacity | 不透明度 | the usual graphics word |
| thickness, width | 粗细, 宽度 | the usual words for a line's weight and a span's width |
| in pixels | 单位为像素 | 单位为, the unit is, the usual way a setting states its unit |
| in seconds | 单位为秒 | as in pixels |
| in world units | 单位为世界单位 | as in pixels, with 世界单位 |
| Ignored while X is off | [X 关闭时忽略此项] | bracketed as the English is, so LunaLib highlights it; 忽略此项, ignore this item |
| Applies only while X is on | [仅在 X 开启时生效] | bracketed as the English is; 生效, takes effect |
| The settings below apply only while X is on | 以下设置仅在 [X] 开启时生效 | as above, with X bracketed for the highlight |
| Visibility overrides (SPOILERS) | 可见性覆盖（剧透） | 覆盖, to override, after 可见性; 剧透, the usual word for a spoiler |
| (adjustable in settings), (can be disabled in settings) | （可在设置中调整）, （可在设置中关闭） | the forum posts' grey asides, in full-width parentheses; 调整, adjust, and 关闭, switch off, as a setting's 开启 / 关闭 |
| log verbosity, reflection, log levels | 日志详细程度, 反射, 关闭 / 错误 / 警告 / 信息 / 调试 / 全部 | [KMLib's](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#settings) |
| UI palette / Chrome grey / Player faction (colour scheme) | 界面配色 / 纯灰 / 你的势力 | 界面, vanilla's word for the UI; 纯灰, the plain grey the description names; 你的势力 as vanilla writes it |
| Gold / Panel accent (chevron) | 金色 / 面板强调色 | 强调色, the usual design word for an accent colour |
| profiling | 性能分析 | the usual computing word, as the Map - Dev tab writes it |
| dev settings | 开发设置 | from the Dev tabs, 开发 |
| Primary / Secondary faction color, No color | 势力主色 / 势力副色, 无颜色 | 主色 and 副色, a palette's main and second colour |
| The name's fitted box / The words themselves | 名称所放入的框 / 文字本身 | as the description words them |
| Below / Above (nebulae) | 之下 / 之上 | as the descriptions write 星云之下 and 星云之上 |
| Normal / Fixed (hidden market scaling) | 正常 / 固定 | 固定 as in （固定） |
| Not surveyed / Seen / Preliminary / Full (survey level) | 未被调查 / 已观测 / 已初步调查 / 已全面调查 | vanilla's planet list for three; 已观测 from KMU's 观测 for seen |
| Off / Coarse / Fine (profiling) | 关闭 / 粗略 / 精细 | a switch's 关闭; 粗略 and 精细, rough and detailed |

### Settings tabs

| English | 简体中文 |
| --- | --- |
| Features | 功能 |
| Map - Visuals | 地图 - 外观 |
| Map - Politics - Visuals | 地图 - 政治 - 外观 |
| Map - Politics - Domination | 地图 - 政治 - 主导 |
| Map - Sound | 地图 - 音效 |
| Map - Keybinds | 地图 - 按键 |
| Map - Visibility | 地图 - 可见性 |
| Map - Compatibility | 地图 - 兼容性 |
| Map - Dev | 地图 - 开发 |
| Dev | 开发 |
| Market Condition Manager (MCM) | 市场条件管理器 (MCM) |

The ` - ` in a tab name is an ASCII hyphen with a space either side, as in English.

### Console commands

| English | 简体中文 | Note |
| --- | --- | --- |
| console commands | 控制台指令 | Console Commands, the mod, keeps its name |
| performance measurements | 性能测量 | from 性能, with 测量, to measure |
| game logs | 游戏日志 | 日志, KMLib's word for a log |
| colonisable market | 可殖民市场 | vanilla writes only the negative, 无法殖民 |
| cut-off systems | 被截断的星系 | 截断, to cut off, before vanilla's 星系 |

### The forum banner

The captions of the "always has been" meme on the Fossic thread's banner,
drawn in Microsoft YaHei Bold over `promo/zh-hans/always-has-been.png`.

| English | 简体中文 | Note |
| --- | --- | --- |
| map game (the banner's joke) | P社游戏 | players' slang for Paradox grand strategy, the genre "map game" points at; a literal 地图游戏 reads as any game with a map. Written solid, as the slang is, the one exception to spacing Latin inside Chinese |
| Always has been | 一直都是 | the caption the template carries in Chinese meme use; no full stop, as the English has none |

## Adding a string

1. Look the term up here.
   A vanilla concept missing from the [vanilla terms](#vanilla-terms) is looked up in the core localisation before it is translated:
   find the English in vanilla's `data/` or game jars, and read what the core localisation has at the same place.
2. Translate by the [rules](#rules), and add a row for any new term to the [KMU terms](#kmu-terms).
3. Check the characters render.
   Every character must be in the core localisation's font atlases,
   `starsector-core/graphics/fonts/<face>.fnt` on each installed edition,
   or it draws as `?`.
4. Run the tests.
   `LocaleParityIntegrationTests` holds this bundle to the English one,
   and `test` takes every bundle as an input,
   so a change made here alone re-runs it.

## Translating the changelog

Every entry added to the root [CHANGELOG.md](../../CHANGELOG.md) is added to [this bundle's](CHANGELOG.md) in the same pull request.
`LocaleParityIntegrationTests` fails a pull request whose translation lacks a point the root has,
or whose code spans differ from the root's in any section.
[KMLib's reference](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#translating-the-changelog)
says how to translate the changelog and which words its headings take.
Name a setting as the Chinese settings screen names it.
