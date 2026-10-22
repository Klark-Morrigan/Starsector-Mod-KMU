# KMU in Simplified Chinese (简体中文)

The terminology reference for KMU's `zh-hans` bundle:
the words the Chinese core localisation already uses for vanilla concepts,
and the translations settled for KMU's own.
Check a term here before translating a new string,
and add a row when a new term is settled.
Every table keeps its rows in alphabetical order of the English column, ignoring case.
A long section splits its table by topic;
a term that fits no topic opens a new one, with its own heading and index entry.
How bundles are built and checked is in the root README's
[Localisation](../../README.md#localisation) section.

This reference builds on
[KMLib's](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md),
the base every KM mod's Chinese bundle follows:
the rules, the vanilla words the shared library's own text touches,
and the terms it draws itself, the compatibility notice above all.
A term KMLib's reference lists is not repeated here,
so read both for the full picture.

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
    - [The mod](#the-mod)
    - [Map layers](#map-layers)
    - [Screens and controls](#screens-and-controls)
    - [Market Condition Manager](#market-condition-manager)
    - [Compatibility](#compatibility)
  - [The political map](#the-political-map)
    - [Views](#views)
    - [Ownership, claims and dominance](#ownership-claims-and-dominance)
    - [Factions and blocs](#factions-and-blocs)
    - [Systems](#systems)
    - [Scoring](#scoring)
    - [Drawing](#drawing)
    - [The sidebar](#the-sidebar)
    - [In the changelog: the map and its controls](#in-the-changelog-the-map-and-its-controls)
    - [In the changelog: code](#in-the-changelog-code)
  - [Terms borrowed from HOI4](#terms-borrowed-from-hoi4)
  - [Hovering and tooltips](#hovering-and-tooltips)
  - [Settings](#settings)
    - [Descriptions](#descriptions)
    - [Setting names](#setting-names)
    - [Option values](#option-values)
  - [Settings tabs](#settings-tabs)
  - [Console commands](#console-commands)
  - [The forum banner](#the-forum-banner)
- [Adding a string](#adding-a-string)
- [Translating the changelog](#translating-the-changelog)

## What this folder holds

| File | What it is |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | A full translation of the root [CHANGELOG.md](../../CHANGELOG.md): every version, every section. The Chinese zip ships it, and each release's notes show its section for the version. |
| [commands.csv](commands.csv) | The console commands' table: the help Console Commands prints for each `kmu_` command. Only the `help` column is translated; the rest is what a player types and what the console runs. Each help carries the English after it, by [KMLib's rule for console text](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#rules). |
| [LunaSettings.csv](LunaSettings.csv) | The LunaLib settings screen: tab names, captions, setting names and descriptions. |
| [mod_info.json](mod_info.json) | The launcher's mod list entry: `name` and `description` only, merged over the base. |
| [strings.json](strings.json) | Every string KMU draws in game: the sidebar, hover boxes, dialogs, notices and the Market Condition Manager, and what its console command prints, whose `console_` strings carry the English after them as the help does. |

Players of this bundle are expected to have the
[Chinese core localisation](https://github.com/TruthOriginem/Starsector-Localization-CN)
laid over `starsector-core`, as [manifest.json](../manifest.json) records under `coreLocalisation`.
The game's own fonts hold no Chinese characters;
the core localisation replaces them, and without it every Chinese character draws as `?`.

## Rules

[KMLib's rules](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#rules)
hold for every KM mod's bundle, KMU's included.
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
The vanilla words KMLib's own text touches are in
[KMLib's vanilla terms](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#vanilla-terms).

### Screens and the map

| English | 简体中文 | Source |
| --- | --- | --- |
| Codex | 数据百科 | `starfarer.api.jar`, "Codex Update" is 数据百科更新 |
| Exploration (map filter) | 探索状态 | map filter row |
| Fuel range (map filter) | 续航距离 | map filter row; its tooltip is 显示燃料可用范围 |
| Inhabited (map filter) | 有人居住 | map filter row |
| Intel (an item, not the screen) | 情报 | `BlueprintIntel`, among 65 places; the screen is 情报信息 |
| Intel (the screen, its tab) | 情报信息 | `starfarer_obf.jar` tab bar; the screen is 情报信息界面 |
| Legend | 图例 | map filter row |
| Map (the screen, its tab) | 星图 | `starfarer_obf.jar` tab bar; "open map" is 打开星图 |
| Names (map filter) | 名称 | map filter row |
| Radar | 雷达 | story text in `rules.csv` and `market_conditions.csv`; vanilla draws no radar label |
| Starscape (map filter) | 星景 | `starfarer_obf.jar` map filter row |
| System map | 星系地图 | `starfarer_obf.jar`, "Go to system map" is 进入星系地图 |

### Space

| English | 简体中文 | Source |
| --- | --- | --- |
| Black hole | 黑洞 | `planets.json` `black_hole` |
| Entity | 实体 | `custom_entities.json`, "Unidentified Entity" is 不明实体 |
| Nebula | 星云 | `starfarer.api.jar` |
| Orbital Station | 轨道空间站 | `industries.csv` |
| Planet | 行星 | `starfarer.api.jar` |
| Sensors, sensor range | 传感器, 探测范围 | `starfarer_obf.jar` fleet tooltip, "Sensor Range" is 探测范围 |
| Slipstream | 滑流 | `SlipstreamTerrainPlugin`; also 超空间滑流 |
| Star (the body) | 恒星 | `starfarer.api.jar` planet dialog; `planets.json` names each kind, as 黄矮星 |
| Station | 空间站 | `starfarer.api.jar`; Jangala Station is `Jangala 空间站` |
| Terrain | 地形 | `SlipstreamTerrainPlugin`; "special terrain" is 特殊地形 |
| Unvisited star system | 未访问过的星系 | `starfarer_obf.jar` map legend |

### Colonies and markets

| English | 简体中文 | Source |
| --- | --- | --- |
| a faction's presence in a system | 殖民地, by paraphrase | "has no presence in this system" is 该星系中并没有……的殖民地 |
| Abandoned Station (condition) | 废弃空间站 | `market_conditions.csv` |
| Can not be colonized | 无法殖民 | `PlanetSurveyPanel`; vanilla has no positive form |
| Colony conditions | 殖民地条件 | `starfarer_obf.jar` |
| Colony size | 殖民地规模 | `starfarer_obf.jar` |
| Decivilized (condition) | 荒蛮之地 | `market_conditions.csv` |
| Decivilized Subpopulation (condition) | 法外之地 | `market_conditions.csv` |
| Docked (at a market) | 停靠 | `starfarer_obf.jar` refit, "Must be docked at a market" |
| Industry (a colony building) | 工业设施 | `IndustryListPanel`, "Industries on %s:"; 产业 only where the English means an economic sector |
| Market size | 市场规模 | `starfarer_obf.jar` |
| Militarized (ships only) | 军事化 | `hull_mods.csv` `militarized_subsystems`, the Militarized Subsystems hullmod (军事化子系统) for civilian hulls; vanilla uses the word for ships alone, so it never names [KMU's militarised station](#the-political-map) |
| Military (facilities) | 军事设施 | `HAColonyDefensesFactor` in `starfarer.api.jar` |
| Military Base | 军事基地 | `industries.csv` |
| Patrol (the fleet) | 巡逻队 | `rules.csv`; 巡逻队 in 95 places, 巡逻舰队 in 6 |
| Patrol HQ | 巡逻队总部 | `industries.csv` |
| Pirate | 海盗 | Pirate Station is 海盗空间站 |
| Planetary conditions | 行星状况 | `starfarer_obf.jar` |
| Population | 人口 | `starfarer_obf.jar` |
| Population center (a large hub) | 人口中心 | `market_conditions.csv` `population_10`, "a dominant population center"; vanilla uses it for large hubs, as 主要的人口中心, so it never names [KMU's population center](#the-political-map) |
| Size | 规模 | `starfarer_obf.jar` |
| Stability | 稳定性 | `starfarer_obf.jar`, "Stability: %s" is 稳定性：%s |

### Factions and relations

| English | 简体中文 | Source |
| --- | --- | --- |
| Hegemony | 霸主 | `hegemony.faction` `displayName` |
| Independent (the faction's name) | 非势力团体 | `independent.faction` `displayName` |
| Known allies | 已知的盟友 | `starfarer_obf.jar` faction screen |
| Known enemies | 已知的敌人 | `starfarer_obf.jar` faction screen |
| Luddic Church | 卢德教会 | `luddic_church.faction` `displayName`; its long name is 银河救赎教会 |
| Neutral (the faction's name) | 中立 | `neutral.faction` `displayName` |
| Relationship | 关系 | `starfarer_obf.jar` |
| Tri-Tachyon | 速子科技 | `tritachyon.faction` `displayName`; its long name is 三一速子科技公司 |
| Unclaimed (territory) | 无宣称 | `PerseanLeagueHostileActivityFactor`, "unclaimed territory" is 无宣称领土 |
| Your faction (the player's) | 你的势力 | `starfarer_obf.jar` flag picker, "Select a flag to represent your faction"; vanilla never writes 玩家势力, which KMU keeps for the player faction as a category, as the settings write Mod 玩家势力 for modded player factions |

The nine reputation levels, from `RepLevel` in `starfarer.api.jar`:

| Vengeful | Hostile | Inhospitable | Suspicious | Neutral | Favorable | Welcoming | Friendly | Cooperative |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 仇恨 | 敌对 | 冷淡 | 怀疑 | 中立 | 良好 | 欢迎 | 友好 | 合作 |

### Surveys

| English | 简体中文 | Source |
| --- | --- | --- |
| Full survey data | 完整调查数据 | `starfarer_obf.jar`; vanilla uses both 全面 and 完整 for "full" |
| Fully surveyed | 已全面调查 | `starfarer_obf.jar` planet list |
| Preliminary survey completed | 已初步调查 | `starfarer_obf.jar` planet list |
| Preliminary survey data | 初步调查数据 | `Misc` in `starfarer.api.jar` |
| Survey | 调查 | "Survey Data" is 调查数据 |
| Unsurveyed | 未被调查 | `starfarer_obf.jar` planet list |

### Time

| English | 简体中文 | Source |
| --- | --- | --- |
| 1 day ago | 1 天前 | `Misc` |
| N days ago | N 天前 | `Misc` |
| Today | 今天 | `Misc` in `starfarer.api.jar` |

### Game and mods

| English | 简体中文 | Source |
| --- | --- | --- |
| Launcher | 启动器 | `StarfarerLauncher` in `starfarer_obf.jar` |
| Sound volume | 音量 | `starfarer_obf.jar` title settings, "Sound volume:" is 音量： |
| Starsector | 远行星号 | `starfarer_obf.jar` title, "Welcome to Starsector!" is 欢迎来到远行星号！; most of the game keeps the Latin name |

## Vanilla mechanics without a name

Mechanics the game runs but never names to the player.
No vanilla text holds them, in English or Chinese, so there is nothing to pair a translation against.
KMU names them, and the code column says where vanilla keeps each one.

| English | 简体中文 | Vanilla code | Note |
| --- | --- | --- | --- |
| dynamic claim | 动态宣称 | `Misc.getClaimingFaction`, where no unconditional claim is set | 动态, changing as things change, the opposite of 无条件 |
| hyperspace anchor | 超空间锚点 | `StarSystemAPI.getHyperspaceAnchor()` | a system's point in hyperspace |
| sector memory | 星域记忆 | the game's `$` memory keys | where unconditional claims are set; from 星域 |
| unconditional claim | 无条件宣称 | the `$claimingFaction` memory key, `MemFlags.CLAIMING_FACTION` | wins over any dynamic claim |
| unowned (a market the Neutral faction holds) | 无主 | a market owned by the faction `neutral`, named 中立 | a market's ownership only, never a system's, which is unpopulated, decivilised or settled instead; silent on whether anyone lives there; written beside 市场, and with 中立 where it first appears; not 未受控制, which reads as dominance, and not 无主之地, vanilla's label for Uninhabited |

## KMU terms

KMU's own concepts, settled when the bundle was first written.
Where a row builds on a vanilla word, the note says which.
The words of a release and of the Fossic thread are
[KMLib's forum terms](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#forum-terms),
which every KM mod's shares.

### Features and screens

#### The mod

| English | 简体中文 | Note |
| --- | --- | --- |
| headline feature | 首发功能 | 首发, the first to ship, with 功能 as in the Features tab |
| Klark Morrigan's Utilities (KMU) (the mod's name) | Klark Morrigan 的实用工具 (KMU) | `KMU` is the brand: kept in every locale, never translated |
| KMU | KMU | the brand, kept in every locale |
| read-only (by design) | 只读 | the standard computing word |
| underlying mechanics | 底层机制 | 底层, the layer beneath, with 机制 for mechanics |

#### Map layers

| English | 简体中文 | Note |
| --- | --- | --- |
| Arrange Map Layers | 排列地图图层 | 排列, to put in order, before 地图图层 |
| info-layer | 信息图层 | from 图层: a layer that shows information |
| load a save | 读取存档 | from vanilla's 读取最近的存档 |
| map layers | 地图图层 | also drawn on the intel screen's map, hence 地图 rather than 星图 |
| Map layers (filter row tick box) | 地图图层 | sits beside 星景 and 续航距离 |
| No Layer | 无图层 | 无, none, before 图层: the tab that shows no layer |
| Political Map | 政治地图 | 政治 for political, with 地图 as in 地图图层, since it is a layer |
| Sector Map Layers (the feature) | 星图图层 | from 星图 |
| switched off (a map layer that failed) | 已关闭 | the notice's word; 关闭 as for the map layers switched off and back on (关闭后重新开启) |
| uncheck | 取消勾选 | highlighted inside the dialog's hint |
| Up / Down (arrange dialog) | 上移 / 下移 | highlighted inside the dialog's hint |
| view (of the political map) | 视图 | as in 联盟视图 |
| world units | 世界单位 | literal: a distance in game space, kept apart from 像素 |

#### Screens and controls

| English | 简体中文 | Note |
| --- | --- | --- |
| control (a tick box, switch or button) | 控件 | as in 地图控件, the Map - Dev header for the map controls; not 星图控件, the map itself |
| faction filter | 势力筛选列表 | 势力 before 筛选列表, the filter list |
| filter list, sortable filter list | 筛选列表, 可排序的筛选列表 | the sidebar's list of blocs |
| filter row | 筛选栏 | the row holding 星景, 续航距离 and 名称 |
| map screen | 星图界面 | the screen Tab opens; vanilla's tab reads 星图 |
| Map sidebar (the sidebar's name, wherever the player reads it) | 地图侧边栏 | 地图 as in 地图图层, whose controls it holds on the map screen and the intel screen; one name, never 叠加层侧边栏, 地图图层侧边栏, 叠加层控制框 or 可折叠侧边栏 |
| mouseover detection | 鼠标悬停检测 | from 悬停 |
| overlay | 叠加层 | the usual Chinese UI word for a layer drawn over another |
| screen | 界面 | as in 星图界面 and 情报信息界面 |
| sector map widget (the map, on whichever screen draws it) | 星图控件 | from 星图, with 控件, the control word; the settings and the notices already use it; not 地图控件, the Map - Dev header |
| sidebar | 侧边栏 | the usual Chinese UI word |
| Starscape mode, in it, with it off (the map with vanilla's Starscape filter ticked, or not) | 星景模式; 在星景模式下, 关闭星景模式时 | vanilla's 星景 with 模式, mode: the map's look rather than the button that switches it; not 星景视图, since 视图 is a political map view |
| tab | 标签页 | the usual Chinese UI word; 标签 alone would read as a label |
| tab bar | 标签栏 | from 标签页, with 栏 for a bar, as in 筛选栏 |

#### Market Condition Manager

| English | 简体中文 | Note |
| --- | --- | --- |
| list separators (the condition manager's lines) | ` ； ` between groups, ` ， ` between items | an ASCII space either side, so the highlighted counts and names beside them still highlight |
| market condition | 市场条件 | from vanilla's 殖民地条件, with 市场 since the manager works on markets |
| Market Condition Manager (MCM) | 市场条件管理器 (MCM) | from 殖民地条件 |
| Metadata (the hover box's last section) | 元数据 | the usual computing word |
| owned by (a planet's faction) | 属于 | the game's own text has no such phrase; the faction's name follows after a space |
| placeable (a condition the manager may add) | 可放置的 | 可, can be, with 放置, to place |
| Present / Absent (a condition on the market) | 存在 / 不存在 | as the summary's 个存在 writes it |
| `source`, `id`, `icon` (metadata lines) | 来源, ID, 图标 | the usual words; `ID` stays Latin, as KMLib's reference writes 字符串 ID |
| suppressed (condition) | 被压制 | the plain passive for a condition held down but not removed |
| true / false (a metadata line's answer) | 是 / 否 | a yes-or-no fact; a settings switch says 开启 / 关闭 instead, as [Descriptions](#descriptions) writes it |

#### Compatibility

| English | 简体中文 | Note |
| --- | --- | --- |
| campaign radar (where Random Assortment of Things puts its minimap) | 战役雷达 | from vanilla's 雷达 |
| compatibility mode | 兼容模式 | 兼容 as in the Map - Compatibility tab's 兼容性, with 模式 for mode |
| compatibility patch | 兼容补丁 | 补丁, the usual word for a patch, after 兼容 |
| docked (Random Assortment of Things hiding its minimap) | 停靠 | vanilla's word for docking at a market |
| minimap (Random Assortment of Things') | 小地图 | the usual Chinese word for a minimap, as the settings write it |

### The political map

Three separate axes run through the political map, each with words of its own:

- **Ownership** belongs to a market:
  the faction holding it, or 无主 for a market the Neutral faction (中立) holds.
  A colony that changes hands (易手) moves on this axis alone, never on dominance's.
- **Claim** belongs to a system, and is strictly vanilla's mechanic:
  宣称, with 宣称方 for its holder and vanilla's 无宣称 for a system nobody claims.
- **Dominance** belongs to a system, and is KMU's own measure of which faction prevails there:
  主导, with 主导方 for the faction dominating and 星系持有方 for the system holder it makes.

The axes are independent:
one system can be dominated and claimed at once, by the same faction or by two.
So a word of one axis never stands in for another:
主导 never says claimed, 宣称 never says dominated, and 无主 never says unclaimed.

#### Views

| English | 简体中文 | Note |
| --- | --- | --- |
| Alliances (view) | 联盟 | Nexerelin's alliances; vanilla also uses 联盟 for the Persean League |
| Claims (view) | 宣称 | vanilla's word for a claim, as in 无宣称领土; the view shows vanilla's claims |
| domination views (Factions and Alliances) | 主导视图 | from 主导 and 视图 |
| Factions (view) | 势力 | vanilla's 势力 |
| Nexerelin (as a search word) | 势力争霸 | the name its Chinese build carries on Fossic; text keeps Nexerelin in Latin, per the rules, so the name stands only where a player searches, as a thread tag |

#### Ownership, claims and dominance

| English | 简体中文 | Note |
| --- | --- | --- |
| (core) | （核心） | HOI4's own word |
| claim | 宣称 | vanilla's word, as in 无宣称领土 |
| claim holder | 宣称方 | from 宣称, with 方 for the party holding it |
| contest (who holds a system, weighed between blocs) | 争夺 | to contend for, as in 争夺方 |
| Contested by | 争夺方 | 争夺, to contend for, with 方 for the party |
| core territory | 核心领土 | HOI4's own word; see [terms borrowed from HOI4](#terms-borrowed-from-hoi4) |
| Dominated by | 主导方 | from 主导, with 方 for the party |
| domination, dominance | 主导 | to prevail; kept apart from 宣称, the claim, and from 控制, a control |
| presence | 存在 | vanilla paraphrases presence as 殖民地; KMU needs a noun |
| Present (tooltip section) | 在场 | the plain word for being there, kept apart from the noun 存在 |
| solidified control | 稳固的控制 | 稳固, firm and settled: dominance nobody contests, not a claim |
| system claim (the mechanic, a claim on a whole system) | 星系宣称 | from vanilla's 星系 and 宣称; how the Claims view and the forum intro name the mechanic |
| system holder | 星系持有方 | 持有, to hold, with 方 for the party; kept apart from 宣称方 |
| territory | 领土 | the plain word, and vanilla's own in 无宣称领土 |

#### Factions and blocs

| English | 简体中文 | Note |
| --- | --- | --- |
| affiliation | 归属 | to belong to: the faction a market answers to |
| Allied with / Friendly with | 与……结盟 / 与……友好 | 友好 is the reputation level |
| balance of power | 力量平衡 | the usual Chinese phrase |
| bloc | 集团 | a faction, or an alliance standing as one |
| holder (an owner holding a system through its colonies) | 持有者 | 持有, to hold, with 者 for the one doing it; the kind of owner, kept apart from 星系持有方, one system's holder |
| non-allied factions | 非同盟势力 | 非, not, before 同盟, allied, and vanilla's 势力 |
| owner (the key an owner-painted map layer paints a system by) | 所有者 | the plain word for the one a thing belongs to; a faction, a bloc or any other key, which is why it is not 势力 |
| political entity | 政治实体 | the usual Chinese phrase |
| rename (an alliance) | 改名 | the plain word for changing a name |

#### Systems

| English | 简体中文 | Note |
| --- | --- | --- |
| decivilised | 荒蛮 | from 荒蛮之地 |
| decivilised system, world, colony | 荒蛮星系, 荒蛮世界, 荒蛮殖民地 | from 荒蛮, with 星系, 世界 and vanilla's 殖民地 |
| derelict | 废弃空间站 | from the condition's name |
| faction systems | 势力星系 | vanilla's 势力 and 星系 |
| hidden system | 隐藏星系 | from 隐藏 |
| independent systems | 非势力团体星系 | from the independents' name, 非势力团体 |
| non-market entities | 非市场实体 | from vanilla's 实体 |
| non-political | 非政治 | 非, not, before 政治 |
| populated systems | 有人星系 | from vanilla's 有人居住 |
| population center (any populated market, whatever its size) | 有人居住的市场; 有人居住市场 before another noun | never 人口中心, which reads as a large hub |
| uninhabited systems | 无人星系 | 无人, nobody, before vanilla's 星系 |
| unpopulated | 无人居住 | the negative of vanilla's 有人居住, Inhabited |

#### Scoring

| English | 简体中文 | Note |
| --- | --- | --- |
| (fixed) | （固定） | the plain word for a value that does not vary |
| domination algorithm | 主导算法 | from 主导, with 算法, algorithm |
| militarised (a station colony with an orbital station built) | 空间站殖民地建有轨道空间站, by paraphrase | never 军事化, which reads as the ship hullmod; 轨道空间站 is vanilla's Orbital Station |
| Military (claim factor) | 军事 | vanilla's 军事, as in 军事基地 |
| military industry (a claim factor) | 军事设施 | vanilla's word |
| Same-faction market bonus | 同势力市场加成 | 加成, the usual game word for a bonus |
| Small / Medium / Large (patrols) | 小型 / 中型 / 大型 | vanilla's patrol sizes, as in 大型巡逻队 for heavy patrols |
| stability, size, patrols (score factors) | 稳定性, 规模, 巡逻队 | vanilla words |
| weight (what a colony counts for in a contest) | 权重 | the usual word for a weighting, which earlier entries already use |

#### Drawing

| English | 简体中文 | Note |
| --- | --- | --- |
| border | 边界 | the plain word; the outer and inner borders add 国界 and 省界 |
| border gore | 边界地狱 | the players' word for tangled borders |
| border tracing (the dev overlay showing each smoothing stage) | 边界描绘 | the Dev tab's own header for it |
| cell | 单元 | one system's area on the map, never a UI control |
| cluster (a run of one owner's systems, and the name drawn over it) | 区域 | a region, since the run reads as one area on the map |
| fill | 填充 | the usual graphics word |
| fill state (how one system fills inside its cluster: solid fill, hatch fill or no fill) | 填充状态 | 填充 with 状态, a state |
| hatch fill | 斜线填充 | 斜线, diagonal lines, before 填充 |
| inner border | 内边界（省界） | the province seam |
| nebulae (the map's, which an overlay part paints below or above) | 地图星云 | vanilla's 星云 with 地图, as the settings and the compatibility notices write it |
| no fill | 不填充 | the negative of 填充 |
| outer border | 外边界（国界） | the national border |
| outline (an uninhabited or decivilised system's border) | 轮廓 | the plain word, as the settings write it |
| paint (a system, the map) | 上色 | to colour in, as the map does a territory |
| presence ribbon, presence band | 存在色带 | from 存在, with 色带 for a coloured band |
| ring (the loop inside a cell's border a band runs along) | 环线 | a closed loop line, as a ring road is; not 圆环, which says round, while a cell's ring follows its polygon; kept apart from 边界, the border itself |
| solid fill | 实色填充 | 实色, solid colour, before 填充, as the settings write it |

#### The sidebar

| English | 简体中文 | Note |
| --- | --- | --- |
| Columns | 列 | the plain word |
| faction names | 势力名称 | vanilla's 势力 with 名称, as in the Names filter |
| Full / Short / No (faction names) | 全称 / 简称 / 无 | the usual words for a full and a short name |
| Muted / Desaturated | 淡化 / 去色 | 淡化, to fade, and 去色, to strip colour, the usual image-editing word |
| Name, Domination, Presence, Score, Market size, Claims, Attitude (sort) | 名称, 主导, 存在, 得分, 市场规模, 宣称, 关系 | Attitude follows vanilla's 关系 |
| picker (the sidebar list a bloc is spotlit from) | 选择器 | the usual UI word for a control a choice is made in |
| recede | 退后 | to step back, which a receded bloc does on the map |
| Rest of the sector | 星域其余部分 | vanilla's 星域 with 其余部分, the part remaining |
| spotlight | 聚焦 | to focus on, which spotlighting a bloc does |

#### In the changelog: the map and its controls

| English | 简体中文 | Note |
| --- | --- | --- |
| caption (the words beside a sidebar control) | 标题 | the plain word for a control's heading text |
| category (the kind a map sorts a cell into, which picks its style) | 类别 | the plain word for a class something is sorted into |
| cluster group (one owner's clusters drawn together) | 区域组 | from 区域, a cluster, with 组, a group |
| cover (anything drawn over the map where the cursor rests) | 遮挡 | to block from view, which a cover does to the cells under it |
| cursor | 光标 | the usual UI word for the pointer on screen |
| dev display (an overlay a Dev setting draws over the map) | 调试显示 | 调试, the usual word for debugging, with 显示, a display |
| frame (one drawing of the screen) | 帧 | the usual graphics word; per frame is 每帧 |
| gate (the switch a smoothing pass runs under) | 开关 | the plain word for a switch, as the settings' own toggles are |
| paint (everything one owner's elements draw from) | 绘制方式 | 绘制, to draw, with 方式, a manner; kept apart from 上色, colouring a territory |
| paint scheme (the theme and palettes one build styles by) | 绘制方案 | 绘制, to draw, with 方案, a scheme |
| radio (a row of options with one lit) | 单选框 | the usual UI word for a one-of-many control |
| rebuild (the map's drawing worked out again) | 重建 | the usual word for building something again |
| recede control | 退后控件 | 退后 with 控件, the usual UI word for a control |
| redraw (the map drawn again) | 重绘 | 重, again, with 绘, to draw; the word earlier entries already use |
| run (one owner's stretch of a band) | 色段 | 色, colour, with 段, a segment |
| shades (an owner's two colours) | 配色 | as in vanilla's 界面配色, a set of colours worn together |
| smoothing (the sanding and rounding of a border) | 平滑 | the usual graphics word; one of its passes is 一道平滑处理 |
| stage (a border's shape after one smoothing pass) | 阶段 | the plain word for one step of a process |
| style decision (the category and recede one owner draws under) | 样式决定 | 样式, a style, with 决定, a decision |
| theme (every style the map is drawn in) | 主题 | the usual UI word for a set of styles worn together |
| tier (of the map layers framework) | 层 | the plain word for a layer of a structure, as in 三层; kept apart from 图层, a map layer |
| unowned cell (a cell no owner on the layer holds) | 无所有者单元 | 无, no, with 所有者 and 单元; kept apart from 无主, a market the Neutral faction holds |

#### In the changelog: code

| English | 简体中文 | Note |
| --- | --- | --- |
| alliance record (one alliance as plain data) | 联盟记录 | 联盟 with 记录, a record |
| alliance set (every alliance at one moment) | 联盟集合 | 联盟 with 集合, a set |
| bake (a band worked out once at rebuild, for every frame to draw) | 烘焙 | the usual graphics word for working something out ahead of drawing it |
| body (what a hover box states beneath its heading) | 主体 | the plain word for the main part of something |
| breakdown (the parts a colony's weight is summed from) | 权重明细 | 权重, weight, with 明细, an itemised account |
| cast (reaching a concrete type through an interface) | 类型转换 | the usual computing word for a type conversion |
| compose (a hover box reading the sector into what it states) | 组合 | the plain word for putting parts together |
| consumer (what a fit is run for: the drawn names, the anchor overlay) | 使用方 | 使用, to use, with 方, a party, as in 读取方 for a reader |
| entry point (the class the game constructs a mod from) | 入口 | the plain word for a way in |
| fit (placing every cluster's name inside its border) | 拟合 | the usual word for fitting a shape to the room it is given |
| footprint (one faction's markets folded into totals) | 足迹 | the plain word for a footprint, as the type's name |
| grouping (how factions fold into blocs) | 分组 | the plain word for sorting things into groups |
| instance, static (one object made and handed out, against state held by the class itself) | 实例, 静态 | the usual computing words for both |
| moved to KMLib (a changelog heading), forward to (a method handing its call on) | 移至 KMLib, 转交给 | 移至, moved to; 转交, to pass on, kept apart from 转发器, KMLib's relay |
| overlay cache (what an owner-painted layer keeps built between frames) | 叠加层缓存 | 叠加层, the overlay, with 缓存, the usual computing word for a cache |
| part (sort cells by their form) | 划分 | the plain word for dividing things into kinds |
| pass (one rebuild's reading of the sector) | 本次计算 | 本次, this time, with 计算, a working-out; the pass is the reading one rebuild works from |
| plan (a band's runs, before they are laid round its ring) | 计划 | the plain word for a plan |
| port (the interface a feature's own code reaches the game through) | 端口 | the usual word for a port in a ports-and-adapters design |
| prefix (the part of a profiling row's name before its first dot) | 前缀 | the plain computing word |
| press (a strike of the detail key) | 按键 | the plain word for pressing a key, as the Map - Keybinds tab uses it |
| reading (an owner's looks and names) | 读取 | the plain word for reading something off; a reader is 读取方 |
| refresh poll (the periodic check that marks changed systems) | 刷新轮询 | 刷新, to refresh, with 轮询, the usual computing word for polling |
| register (a record the map keeps in sector memory) | 登记 | the plain word for writing something into a record |
| remark (the note a hover line carries about a colony) | 备注 | the plain word for a note added to something |
| reset hook (a call that empties held state back to its start) | 重置钩子 | 重置, to reset, with 钩子, a hook, as KMLib's reference writes it |
| resolver (a function answering one value per owner, such as a name's shade) | 解析函数 | 解析, to resolve, with 函数, a function |
| sealed type (a type whose every case is named where it is declared) | 密封类型 | the usual Java word, 密封, sealed |
| seat (hand a sector's machinery something it holds from its first moment) | 安放 | the plain word for setting something in its place |
| settled face (the font a sector's text was checked against and kept) | 已确定的字体 | 已确定, settled, with 字体, a font |
| stale, drain (a system marked for redrawing, and taking every such mark off the set) | 过期, 取出 | the plain words for out of date and for taking something out |
| sweep (one walk of the whole sector) | 扫描 | the usual word for a pass over everything |
| tie-break (how a dead heat between blocs is settled) | 平局裁定 | 平局, a draw, with 裁定, a ruling |

### Terms borrowed from HOI4

The political map takes some of its vocabulary from Hearts of Iron IV,
so those terms take HOI4's own official Simplified Chinese,
and a player who knows HOI4 recognises them.
Each row was read off HOI4's `localisation/simp_chinese` files by pairing them with `localisation/english` on the same key.

| English | 简体中文 | HOI4 key, file | KMU meaning |
| --- | --- | --- | --- |
| core | 核心 | `PEACE_CONFERENCE_PROVINCE_TOOLTIP_CORES`, `peace`: "Cores:" is 核心： | the tooltip marker `（核心）` |
| core territory | 核心领土 | `MODIFIER_ATTACK_BONUS_AGAINST_A_COUNTRY_ON_ITS_CORES`, `modifiers`: "Attack on their core territory" is 我国对其核心领土进攻 | a system its faction claims unconditionally |

HOI4 names a collection of cores without 的 ("Chinese core states" is 中国核心地区),
which is how the tooltip heading reads: the faction's name, then 核心领土.

### Hovering and tooltips

| English | 简体中文 | Note |
| --- | --- | --- |
| + N more | + 还有 N 项 | 还有, there are still, with 项 for items |
| abandoned, hidden, undiscovered, unlisted (qualifiers) | 废弃, 隐藏, 未发现, 未列出 | 废弃 from 废弃空间站 and 隐藏 from vanilla's Hidden; 未发现 and 未列出 are not yet found and not listed |
| cell wash | 单元覆色 | from 单元, with 覆色, colour laid over |
| collapse to factions | 折叠为势力 | 折叠, the usual word for collapsing a list |
| connecting lines (label to value) | 连接线 | the plain word |
| expand system composition, market stats, patrol details | 展开星系构成, 展开市场数据, 展开巡逻详情 | 展开, the usual word for expanding, the pair of 折叠 |
| halo | 光晕 | the plain word for a glow around something |
| help tooltip | 帮助提示框 | 帮助, help, with 提示框 |
| hover | 悬停 | the usual Chinese UI word |
| hover box | 悬停框 | from 悬停, with 框 for a box |
| hover effects | 悬停效果 | from 悬停, with 效果 for effects |
| hover highlight | 悬停高亮 | from 悬停, with 高亮, the usual word for highlighting |
| hover tooltip | 悬停提示框 | vanilla avoids the word "tooltip" |
| last seen | 上次观测 | 观测, to observe: what the player knows was seen |
| redacted text | 隐去文字 | 隐去, to hide away, as a redaction hides without removing |
| system tooltip | 星系提示框 | vanilla's 星系 with 提示框 |
| tooltip | 提示框 | short for 悬停提示框, as KMU's strings write it |

### Settings

#### Descriptions

| English | 简体中文 | Note |
| --- | --- | --- |
| (adjustable in settings), (can be disabled in settings) | （可在设置中调整）, （可在设置中关闭） | the forum posts' grey asides, in full-width parentheses; 调整, adjust, and 关闭, switch off, as a setting's 开启 / 关闭 |
| Applies only while X is on | [仅在 X 开启时生效] | bracketed as the English is; 生效, takes effect |
| Available settings | 可用设置 | the heading of each tab's contents note |
| Default | 默认值 | written `[默认值：X]` |
| Enable X | 启用 X | the usual Chinese UI word |
| Ignored while X is off | [X 关闭时忽略此项] | bracketed as the English is, so LunaLib highlights it; 忽略此项, ignore this item |
| in pixels | 单位为像素 | 单位为, the unit is, the usual way a setting states its unit |
| in seconds | 单位为秒 | as in pixels |
| in world units | 单位为世界单位 | as in pixels, with 世界单位 |
| The settings below apply only while X is on | 以下设置仅在 [X] 开启时生效 | as Applies only while X is on, with X bracketed for the highlight |
| the X tab, the Y section (where a setting sits) | X 标签页, Y 部分 | as the Features tab's note writes 在 [开发] 标签页的 [日志] 部分; each name takes its own noun, so a tab and a section never read as one |
| true / false (Boolean defaults) | 开启 / 关闭 | a switch's on and off, more natural than 真 and 假 |

#### Setting names

| English | 简体中文 | Note |
| --- | --- | --- |
| dev settings | 开发设置 | from the Dev tabs, 开发 |
| Nebula draw order (and its Territory fills, Borders, Presence bands, Faction names) | 星云绘制顺序 (领土填充, 边界, 存在色带, 势力名称) | 星云 with 绘制顺序, the order things are drawn in; the four names are the [drawing](#drawing) words for what each one moves |
| opacity | 不透明度 | the usual graphics word |
| profiling | 性能分析 | the usual computing word, as the Map - Dev tab writes it |
| thickness, width | 粗细, 宽度 | the usual words for a line's weight and a span's width |
| Visibility overrides (SPOILERS) | 可见性覆盖（剧透） | 覆盖, to override, after 可见性; 剧透, the usual word for a spoiler |

#### Option values

| English | 简体中文 | Note |
| --- | --- | --- |
| Below / Above (nebulae) | 之下 / 之上 | as the descriptions write 星云之下 and 星云之上 |
| Gold / Panel accent (chevron) | 金色 / 面板强调色 | 强调色, the usual design word for an accent colour |
| Normal / Fixed (hidden market scaling) | 正常 / 固定 | 固定 as in （固定） |
| Not surveyed / Seen / Preliminary / Full (survey level) | 未被调查 / 已观测 / 已初步调查 / 已全面调查 | vanilla's planet list for three; 已观测 from KMU's 观测 for seen |
| Off / Coarse / Fine (profiling) | 关闭 / 粗略 / 精细 | a switch's 关闭; 粗略 and 精细, rough and detailed |
| Primary / Secondary faction color, No color | 势力主色 / 势力副色, 无颜色 | 主色 and 副色, a palette's main and second colour |
| The name's fitted box / The words themselves | 名称所放入的框 / 文字本身 | as the description words them |
| UI palette / Chrome grey / Player faction (colour scheme) | 界面配色 / 纯灰 / 你的势力 | 界面, vanilla's word for the UI; 纯灰, the plain grey the description names; 你的势力 as vanilla writes it |

### Settings tabs

| English | 简体中文 |
| --- | --- |
| Dev | 开发 |
| Features | 功能 |
| Map - Compatibility | 地图 - 兼容性 |
| Map - Dev | 地图 - 开发 |
| Map - Keybinds | 地图 - 按键 |
| Map - Politics - Domination | 地图 - 政治 - 主导 |
| Map - Politics - Visuals | 地图 - 政治 - 外观 |
| Map - Sound | 地图 - 音效 |
| Map - Visibility | 地图 - 可见性 |
| Map - Visuals | 地图 - 外观 |
| Market Condition Manager (MCM) | 市场条件管理器 (MCM) |

The ` - ` in a tab name is an ASCII hyphen with a space either side, as in English.

### Console commands

The console's own words, and the English copy every console text carries, are in
[KMLib's console commands](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#console-commands).

| English | 简体中文 | Note |
| --- | --- | --- |
| colonisable market | 可殖民市场 | vanilla writes only the negative, 无法殖民 |
| performance measurements | 性能测量 | from 性能, with 测量, to measure |
| section (one stretch of work the profiler times) | 计时段 | 计时, timing, with 段, a stretch |
| self time (a section's own time, without the sections it ran) | 自身耗时 | 自身, its own, with 耗时, time taken |

### The forum banner

The captions of the "always has been" meme on the Fossic thread's banner,
drawn in Microsoft YaHei Bold over `promo/zh-hans/always-has-been.png`.

| English | 简体中文 | Note |
| --- | --- | --- |
| Always has been | 一直都是 | the caption the template carries in Chinese meme use; no full stop, as the English has none |
| map game (the banner's joke) | P社游戏 | players' slang for Paradox grand strategy, the genre "map game" points at; a literal 地图游戏 reads as any game with a map. Written solid, as the slang is, the one exception to spacing Latin inside Chinese |

## Adding a string

1. Look the term up here and in
   [KMLib's reference](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md).
   A vanilla concept missing from both is looked up in the core localisation before it is translated:
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
