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
- [KMU terms](#kmu-terms)
  - [Features and screens](#features-and-screens)
  - [The political map](#the-political-map)
  - [Terms borrowed from HOI4](#terms-borrowed-from-hoi4)
  - [Hovering and tooltips](#hovering-and-tooltips)
  - [Settings](#settings)
  - [Settings tabs](#settings-tabs)
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
| Patrol (the fleet) | 巡逻队 | `rules.csv` |
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

## KMU terms

KMU's own concepts, settled when the bundle was first written.
Where a row builds on a vanilla word, the note says which.

### Features and screens

| English | 简体中文 | Note |
| --- | --- | --- |
| Klark Morrigan's Utilities (KMU) (the mod's name) | Klark Morrigan 的实用工具 (KMU) | `KMU` is the brand: kept in every locale, never translated |
| KMU | KMU | |
| Sector Map Layers (the feature) | 星图图层 | from 星图 |
| map layers | 地图图层 | also drawn on the intel screen's map, hence 地图 rather than 星图 |
| Map layers (filter row tick box) | 地图图层 | sits beside 星景 and 续航距离 |
| Political Map | 政治地图 | |
| No Layer | 无图层 | |
| map screen | 星图界面 | the screen Tab opens; vanilla's tab reads 星图 |
| overlay | 叠加层 | |
| overlay control box | 叠加层控制框 | |
| sidebar | 侧边栏 | |
| tab | 标签页 | |
| tab bar | 标签栏 | |
| filter row | 筛选栏 | the row holding 星景, 续航距离 and 名称 |
| Arrange Map Layers | 排列地图图层 | |
| Up / Down (arrange dialog) | 上移 / 下移 | highlighted inside the dialog's hint |
| uncheck | 取消勾选 | highlighted inside the dialog's hint |
| Market Condition Manager (MCM) | 市场条件管理器 (MCM) | from 殖民地条件 |
| market condition | 市场条件 | |
| suppressed (condition) | 被压制 | |
| world units | 世界单位 | |
| owned by (a planet's faction) | 属于 | the game's own text has no such phrase; the faction's name follows after a space |
| list separators (the condition manager's lines) | ` ； ` between groups, ` ， ` between items | an ASCII space either side, so the highlighted counts and names beside them still highlight |

### The political map

| English | 简体中文 | Note |
| --- | --- | --- |
| Factions (view) | 势力 | |
| Alliances (view) | 联盟 | Nexerelin's alliances; vanilla also uses 联盟 for the Persean League |
| Claims (view) | 宣称 | |
| claim | 宣称 | |
| claim holder | 宣称方 | |
| unconditional claim | 无条件宣称 | |
| system holder | 星系持有方 | |
| domination, dominance | 主导 | |
| Dominated by | 主导方 | |
| Contested by | 争夺方 | |
| Present (tooltip section) | 在场 | |
| presence | 存在 | vanilla paraphrases presence as 殖民地; KMU needs a noun |
| presence ribbon, presence band | 存在色带 | |
| territory | 领土 | |
| core territory | 核心领土 | HOI4's own word; see [terms borrowed from HOI4](#terms-borrowed-from-hoi4) |
| cluster (a run of one owner's systems, and the name drawn over it) | 区域 | |
| cell | 单元 | one system's area on the map, never a UI control |
| border | 边界 | |
| outer border | 外边界（国界） | the national border |
| inner border | 内边界（省界） | the province seam |
| fill | 填充 | |
| hatch fill | 斜线填充 | |
| faction systems | 势力星系 | |
| independent systems | 非势力团体星系 | from the independents' name, 非势力团体 |
| decivilised | 荒蛮 | from 荒蛮之地 |
| decivilised system, world, colony | 荒蛮星系, 荒蛮世界, 荒蛮殖民地 | |
| derelict | 废弃空间站 | from the condition's name |
| uninhabited systems | 无人星系 | |
| unpopulated | 无人居住 | |
| non-territorial | 非领土 | |
| non-political | 非政治 | |
| non-allied factions | 非同盟势力 | |
| Allied with / Friendly with | 与……结盟 / 与……友好 | 友好 is the reputation level |
| bloc | 集团 | a faction, or an alliance standing as one |
| spotlight | 聚焦 | |
| recede | 退后 | |
| Muted / Desaturated | 淡化 / 去色 | |
| Rest of the sector | 星域其余部分 | |
| stability, size, patrols (score factors) | 稳定性, 规模, 巡逻队 | vanilla words |
| Small / Medium / Large (patrols) | 小型 / 中型 / 大型 | |
| Same-faction market bonus | 同势力市场加成 | |
| Military (claim factor) | 军事 | |
| (core) | （核心） | HOI4's own word |
| (fixed) | （固定） | |
| Full / Short / No (faction names) | 全称 / 简称 / 无 | |
| faction names | 势力名称 | |
| Columns | 列 | |
| Name, Domination, Presence, Score, Market size, Claims, Attitude (sort) | 名称, 主导, 存在, 得分, 市场规模, 宣称, 关系 | Attitude follows vanilla's 关系 |

### Terms borrowed from HOI4

The political map takes some of its vocabulary from Hearts of Iron IV,
so those terms take HOI4's own official Simplified Chinese,
and a player who knows HOI4 recognises them.
Each row was read off HOI4's `localisation/simp_chinese` files by pairing them with `localisation/english` on the same key.

| English | 简体中文 | HOI4 key, file | KMU meaning |
| --- | --- | --- | --- |
| core territory | 核心领土 | `MODIFIER_ATTACK_BONUS_AGAINST_A_COUNTRY_ON_ITS_CORES`, `modifiers`: "Attack on their core territory" is 我国对其核心领土进攻 | a system its faction claims unconditionally |
| core | 核心 | `PEACE_CONFERENCE_PROVINCE_TOOLTIP_CORES`, `peace`: "Cores:" is 核心： | the tooltip marker `（核心）` |
| non-core | 非核心 | `non_core`, `core` | not used yet |
| core state | 核心地区 | `TRIGGER_STATE_CORE_OF_COUNTRY`, `triggers`: "Is a core of X" is 是X的核心地区 | not used; KMU's unit is a star system, not a state |

HOI4 names a collection of cores without 的 ("Chinese core states" is 中国核心地区),
which is how the tooltip heading reads: the faction's name, then 核心领土.

### Hovering and tooltips

| English | 简体中文 | Note |
| --- | --- | --- |
| hover | 悬停 | |
| hover tooltip | 悬停提示框 | vanilla avoids the word "tooltip" |
| hover box | 悬停框 | |
| hover effects | 悬停效果 | |
| hover highlight | 悬停高亮 | |
| halo | 光晕 | |
| cell wash | 单元覆色 | |
| connecting lines (label to value) | 连接线 | |
| redacted text | 隐去文字 | |
| collapse to factions | 折叠为势力 | |
| expand system composition, market stats, patrol details | 展开星系构成, 展开市场数据, 展开巡逻详情 | |
| + N more | + 还有 N 项 | |
| last seen | 上次观测 | |
| abandoned, hidden, undiscovered, unlisted (qualifiers) | 废弃, 隐藏, 未发现, 未列出 | |

### Settings

| English | 简体中文 | Note |
| --- | --- | --- |
| Available settings | 可用设置 | the heading of each tab's contents note |
| Default | 默认值 | written `[默认值：X]` |
| true / false (Boolean defaults) | 开启 / 关闭 | |
| Enable X | 启用 X | |
| opacity | 不透明度 | |
| thickness, width | 粗细, 宽度 | |
| in pixels | 单位为像素 | |
| in seconds | 单位为秒 | |
| in world units | 单位为世界单位 | |
| Ignored while X is off | [X 关闭时忽略此项] | |
| Applies only while X is on | [仅在 X 开启时生效] | |
| The settings below apply only while X is on | 以下设置仅在 [X] 开启时生效 | |
| Visibility overrides (SPOILERS) | 可见性覆盖（剧透） | |

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

Every entry added to the root [CHANGELOG.md](../../CHANGELOG.md) is added to [this bundle's](CHANGELOG.md) in the same pull request:
`LocaleParityIntegrationTests` holds the translation to the root one point for point,
so a pull request adding a point the translation lacks fails.
How to translate it -
version headings as written, the same sections and points, one line each -
and the words its headings take are
[KMLib's](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#translating-the-changelog),
which every KM mod's translated changelog follows.
KMU's own entries use the terms above, the settings named as the Chinese settings screen names them.
