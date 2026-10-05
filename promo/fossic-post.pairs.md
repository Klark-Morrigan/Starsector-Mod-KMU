# Fossic post, line by line

The [Fossic post](fossic-post.bbcode) joined with the [Fractal Softworks post](forum-post.bbcode) it translates,
as plain text for reviewing wording.
Change this file with either post.

## Index

- [How to read it](#how-to-read-it)
- [Posting form](#posting-form)
- [Header](#header)
- [Chinese edition notice](#chinese-edition-notice)
- [Questions](#questions)
- [Download and dependencies](#download-and-dependencies)
- [Installing and uninstalling](#installing-and-uninstalling)
- [Compatible mods](#compatible-mods)
- [Sector Map Layers](#sector-map-layers)
  - [Examples](#examples)
  - [Sector examples](#sector-examples)
  - [How to access map layers](#how-to-access-map-layers)
- [Available map layers](#available-map-layers)
  - [Political Map](#political-map)
  - [Political Map examples](#political-map-examples)
  - [Political Map views](#political-map-views)
- [Spoiler protection](#spoiler-protection)
- [Settings](#settings)
- [Console commands](#console-commands)
- [Compatibility](#compatibility)
- [Feedback](#feedback)
- [Post-release roadmap](#post-release-roadmap)
- [AI usage disclaimer](#ai-usage-disclaimer)
- [Thanks](#thanks)

## How to read it

Sections follow the Fossic post's order,
and every line of both posts appears once, split at sentence boundaries.

- **EN** is the Fractal Softworks line.
  A line only the Fossic post carries gives an English back-translation instead, marked as one.
- **ZH** is the Fossic line, or "none" where only the Fractal Softworks post carries it.
- **Both** is a line the two posts carry identically: an image, or a command's syntax.
- **Notes** name the [terminology reference](../localisation/zh-hans/README.md) section holding each term the line uses,
  and say why a line is in only one post.
  Search the linked section for the term to find its row.

Images are forum attachments on Fossic and GitHub links on Fractal Softworks,
for the reason the [source notes][source-notes] give.
Links in the Fossic post point at Fossic threads, listed in the same notes.

## Posting form

Fossic only: the text inputs of the board's posting form.
The [source notes][source-notes] list KMU's value for every input; what each input takes is KMLib's
[Fossic thread doc](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/docs/dev/fossic-thread.md).

- EN (back-translation): `[0.98a][testing] Political Map - Sector Map Layers - KMU`
  - ZH: `[0.98a][测试] 政治地图 - 星图图层 - KMU`
  - Notes: the title.
    The board has no pre-release prefix;
    its threads mark testing in the title,
    per the [source notes][source-notes].
    No mod version: the form's Mod版本 shows it in the board list and the download panel,
    and a title without it needs no edit per release.
    Political Map and Sector Map Layers in [features and screens][kmu-features].
    Testing in [KMLib's forum terms][kmlib-forum].
- EN: Political Map - Sector Map Layers - KMU
  - ZH: 政治地图 - 星图图层 - KMU
  - Notes: the Mod英文名 and Mod中文名 inputs.
- EN: Political map overlay over the sector map (map screen, intel screen, etc.) painting factions, alliances (with Nexerelin), and system claims.
  - ZH: 星图上的政治地图叠加层（星图界面、情报信息界面等），为势力、联盟（需 Nexerelin）和星系宣称上色。
  - Notes: the Mod简短介绍 input; this one is Fossic only, written for the form.
    Political Map, overlay and map screen in [features and screens][kmu-features]; Map and Intel in [screens and the map][kmu-screens].
    "The sector map" is vanilla's 星图, the map itself, whichever screen shows it.
    Paint, Factions, Alliances and claim in [the political map][kmu-politics]; system claim in [the political map][kmu-politics] too.
- EN (back-translation): Install the Chinese localisation first
  - ZH: 需先安装中文汉化
  - Notes: the Mod索引备注 input, shown after the version in Fossic's mod index.
- EN (back-translation): KMU `<version>` Simplified Chinese edition
  - ZH: KMU `<<version>>` 简体中文版
  - Notes: the 显示名称 of the zip's Mod发布文件 row.
- EN (back-translation): mod release, sector map, Political Map, Nexerelin, KMU
  - ZH: mod发布, 星图, 政治地图, 势力争霸, KMU
  - Notes: the 主题标签 input, five at most: the words a player would search for.
    Mod release in [KMLib's forum terms][kmlib-forum]; Map in [screens and the map][kmu-screens];
    Political Map in [features and screens][kmu-features]; Nexerelin in [the political map][kmu-politics].

## Header

- EN: image `promo/en/always-has-been.png`
  - ZH: image `promo/zh-hans/always-has-been.png`
  - Notes: captioned "Wait, Starsector is a map game?" and "Always has been" in English,
    and 等等，远行星号是P社游戏？ and 一直都是 in Chinese; the captions are in the images, not the post text.
    The Fractal Softworks post opens with it; the Fossic post puts it after the title, and uses its own
    Chinese variant. The [source notes][source-notes] give why.
    Starsector in [game and mods][kmu-mods]; Paradox game and always has been in [the forum banner][kmu-banner].
- EN: Political Map
  - ZH: 政治地图
  - Notes: Political Map in [features and screens][kmu-features].
- EN: as the headline feature of Sector Map Layers
  - ZH: 星图图层的首发功能
  - Notes: Sector Map Layers in [features and screens][kmu-features].
    Headline feature in [features and screens][kmu-features].
- EN: This is the pre-release stage intended for public testing and feedback collection.
  - ZH: 本 Mod 目前处于预发布阶段，用于公开测试与收集反馈。
  - Notes: Mod in [game and mods][kmu-mods].
    Pre-release in [KMLib's forum terms][kmlib-forum].
- EN: You can try this mod early and share your experiences, so I can improve it.
  - ZH: 欢迎提前试用并分享你的体验，帮助我改进它。

## Chinese edition notice

Fossic only.
The English README's Languages section says the same to an English reader.

- EN (back-translation): This thread is KMU's Simplified Chinese edition, released by the author together with the English one: not a repost, and not a third-party translation.
  - ZH: 本帖是 KMU 的简体中文版，由作者与英文版一同发布，不是搬运，也不是第三方汉化。
  - Notes: 搬运 and 汉化 are the names of Fossic's repost and translation boards.
    Repost, translation and Simplified Chinese edition in [KMLib's forum terms][kmlib-forum].
- EN (back-translation): The two editions have the same content and differ only in their text; settings carry over between them.
  - ZH: 两个版本内容相同，只有文字不同，设置可以互通。
- EN (back-translation): If you find wording that reads unnaturally or terms that do not match, please point it out in this thread.
  - ZH: 如发现译文不通顺或术语不一致，欢迎在本帖回复指出。
  - Notes: Fossic only: an invitation to correct the translation, where its readers are.

## Questions

- EN: Are you struggling to keep track of faction presence and growth?
  - ZH: 你是否苦于跟不上各势力的扩张与消长？
  - Notes: Faction in [factions and relations][kmu-factions].
    "Presence and growth" is 扩张与消长 (expansion, waxing and waning),
    not presence's 存在 from [the political map][kmu-politics], which reads as the map term.
- EN: Do you want to know which systems are claimed and by who before you even take the trip there?
  - ZH: 你是否想在出发之前就知道哪些星系已被宣称、被谁宣称？
  - Notes: Star system in [space][kmu-space]; claim in [the political map][kmu-politics].
- EN: Do you want to paint the map with your conquests?
  - ZH: 你是否想用自己的征服为星图上色？
  - Notes: Map in [screens and the map][kmu-screens].
    Paint in [the political map][kmu-politics].
- EN: Do you just want to see all that border gore factions in your mod list produce?
  - ZH: 你是否只是想看看你 Mod 列表里的各路势力制造出的边界地狱？
  - Notes: border in [the political map][kmu-politics]; Mod in [game and mods][kmu-mods].
    Border gore in [the political map][kmu-politics].
- EN: Do you want to play Bad Apple?
  - ZH: none
  - Notes: Fractal Softworks only; the [source notes][source-notes] give why.

## Download and dependencies

- EN: Install with TriOS (badge, English zip)
  - ZH: none
  - Notes: Fractal Softworks only; the [source notes][source-notes] give why, and the manual install line below stands in for it.
- EN: KMU downloads (badge, links the GitHub release)
  - ZH: none
  - Notes: Fractal Softworks only, as above.
- EN: KMLib downloads (badge, links the GitHub release)
  - ZH: none
  - Notes: Fractal Softworks only, as above.
- EN (back-translation): Download
  - ZH: 下载
  - Notes: Fossic only: the download is a forum attachment, by the board's rules, per the [source notes][source-notes].
- EN (back-translation): the download panel
  - ZH: none
  - Notes: Fossic only, and no text in either post: the posting form inserts the panel from the zip's
    Mod发布文件 row, where the post marks it.
- EN (back-translation): KMU `<version>` (Simplified Chinese edition): `KMU-<version>-zh-hans.zip`, attached. For 0.98a-RC8.
  - ZH: KMU `<<version>>`（简体中文版）：`[attach]KMU-<<version>>-zh-hans.zip[/attach]`。适用于 0.98a-RC8。
  - Notes: Fossic only, as above; the tag puts the attached zip's download link in the line, per the [source notes][source-notes].
    Attachment in [KMLib's forum terms][kmlib-forum].
- EN (back-translation): Source and English edition (links the GitHub repository)
  - ZH: 源码与英文版
  - Notes: Fossic only: GitHub appears once, as a source line, per the [source notes][source-notes].
- EN (back-translation): The KMU thread on the Fractal Softworks forum
  - ZH: Fractal Softworks 论坛上的 KMU 帖子
  - Notes: Fossic only: the link to the English thread. Its counterpart in the Fractal Softworks post
    is the Simplified Chinese line, which links this thread once it exists.
    Forum thread in [KMLib's forum terms][kmlib-forum].
- EN: Simplified Chinese (简体中文) is available at Fossic.
  - ZH: none
  - Notes: Fractal Softworks only: the link to this thread, whose URL fills a placeholder after the first post,
    per the [source notes][source-notes].
- EN: This mod requires LazyLib, LunaLib.
  - ZH: 前置
  - Notes: a heading over the core localisation, LazyLib and LunaLib as a list, each linking its Fossic thread.
    Prerequisite, Fossic's word for a dependency in [KMLib's forum terms][kmlib-forum].
- EN (back-translation): Starsector Chinese localisation (over starsector-core)
  - ZH: 远行星号中文汉化（覆盖到 starsector-core）
  - Notes: Fossic only, heading the list; the [source notes][source-notes] give why.
    The reference's [opening section][kmu-holds] names the core localisation in English only.
    The Chinese core localisation in [KMLib's forum terms][kmlib-forum]; Starsector in [game and mods][kmu-mods].
- EN (back-translation): KMLib (Klark Morrigan's Library): `KMLib-<kmlib-version>-zh-hans.zip`, attached. The KMLib version KMU needs is written in mod_info.json; the attachment here is always the one that goes with the current KMU.
  - ZH: KMLib（Klark Morrigan 的程序库）：`[attach]KMLib-<<kmlib-version>>-zh-hans.zip[/attach]`。KMU 所需的 KMLib 版本写在 mod_info.json 中，本帖附件始终是与当前 KMU 配套的版本。
  - Notes: Fossic only: KMLib has no Fossic thread, so its zip rides in this one, per the [source notes][source-notes].
    The Fractal Softworks post reaches KMLib through TriOS and its download badge.
    The name is KMLib's own Chinese mod list name.
- EN (back-translation): Installation: unzip each mod into the starsector/mods folder, then enable them in the game launcher.
  - ZH: 安装：把每个 Mod 解压到 starsector/mods 文件夹，然后在游戏启动器中启用它们。
  - Notes: Fossic only, standing in for TriOS, per the [source notes][source-notes].
    Launcher in [game and mods][kmu-mods].

## Installing and uninstalling

- EN: It's safe to install mid-run,
  - ZH: 可在游戏中途安装；
  - Notes: Install mid-run in [KMLib's forum terms][kmlib-forum].
- EN: and there's a short uninstall procedure you will need to follow if you decide to keep your save after uninstalling the mod.
  - ZH: 若卸载后仍想保留存档，需要先执行一个简短的卸载步骤。
  - Notes: Save in [game and mods][kmu-mods].
    Uninstall and uninstall procedure in [KMLib's forum terms][kmlib-forum].

## Compatible mods

- EN: This mod is made compatible with Nexerelin, Fast Rendering, Random Assortment of Things.
  - ZH: 本 Mod 已与 Nexerelin、Fast Rendering、Random Assortment of Things 做了兼容。
  - Notes: proper nouns stay Latin, per the [rules][kmu-rules]; compatibility as in the Map - Compatibility tab in [settings tabs][kmu-tabs].

## Sector Map Layers

- EN: Sector Map Layers is an overlay for the map screen (Tab, Q), the intel screen (E, 1), the minimap in the bottom right corner of the screen (replaces the vanilla radar) if you have Random Assortment of Things installed.
  - ZH: 星图图层是叠加在星图界面 (Tab, Q) 和情报信息界面 (E, 1) 上的叠加层；若安装了 Random Assortment of Things，也会显示在屏幕右下角的小地图上（它替换了原版的雷达）。
  - Notes: Sector Map Layers, map screen and overlay in [features and screens][kmu-features]; Intel in [screens and the map][kmu-screens].
    Minimap in [features and screens][kmu-features]; radar in [screens and the map][kmu-screens]; vanilla in [KMLib's forum terms][kmlib-forum].
- EN: Everything is calculated and drawn off of game data, and it gets updated as the state of the sector changes.
  - ZH: 一切都根据游戏数据计算并绘制，并随星域局势的变化而更新。
  - Notes: Sector in [space][kmu-space].
- EN: Map layers don't alter any of the data being used to draw it, making map layers read-only by design.
  - ZH: 地图图层不会修改用于绘制它的任何数据，因此地图图层在设计上是只读的。
  - Notes: map layers in [features and screens][kmu-features].
    Read-only in [features and screens][kmu-features].

### Examples

- EN: Examples
  - ZH: 示例
- EN: image `promo/en/map-screen.png`
  - ZH: image `promo/zh-hans/map-screen.png`
- EN: Map screen.
  - ZH: 星图界面。
  - Notes: map screen in [features and screens][kmu-features].
- EN: image `promo/en/intel-screen.png`
  - ZH: image `promo/zh-hans/intel-screen.png`
- EN: Intel screen.
  - ZH: 情报信息界面。
  - Notes: Intel in [screens and the map][kmu-screens].
- EN: image `promo/en/random-assortment-of-things-minimap.png`
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: Random Assortment of Things minimap.
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: Sector examples
  - ZH: 星域示例
- EN: image `promo/en/sector-1-vanilla.png`
  - ZH: image `promo/zh-hans/sector-1-vanilla-nexerelin.png`
- EN: Vanilla.
  - ZH: 原版加 Nexerelin。
  - Notes: the Chinese screenshot is its own, vanilla with Nexerelin, so its caption differs.
    Vanilla in [KMLib's forum terms][kmlib-forum]; Nexerelin stays Latin, per the [rules][kmu-rules].
- EN: image `promo/en/sector-2-modded-light.png`
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: Lightly modded.
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: image `promo/en/sector-3-modded-heavy.png`
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: Heavily modded.
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: How to access map layers
  - ZH: 如何打开地图图层
- EN: KMU comes with Sector Map Layers feature turned on by default.
  - ZH: KMU 的星图图层功能默认开启。
- EN: After loading a save or starting a new game, when you open the map screen or the intel screen, you'll see a new Map layers button among map filter buttons.
  - ZH: 读取存档或开始新游戏后，打开星图界面或情报信息界面，地图筛选栏中会多出一个地图图层按钮。
  - Notes: Save in [game and mods][kmu-mods]; map screen, Map layers (filter row tick box) and filter row in [features and screens][kmu-features].
- EN: Clicking that button or pressing (M) enables the map layer overlay and its sidebar.
  - ZH: 点击它或按 (M) 即可开启地图图层叠加层及其侧边栏。
  - Notes: overlay and sidebar in [features and screens][kmu-features].
- EN: image `promo/en/ui-toggle-map.png`
  - ZH: image `promo/zh-hans/ui-toggle-map.png`
- EN: Map screen.
  - ZH: 星图界面。
- EN: image `promo/en/ui-toggle-intel.png`
  - ZH: image `promo/zh-hans/ui-toggle-intel.png`
- EN: Intel screen.
  - ZH: 情报信息界面。
- EN: The main control interface of map layers is the collapsible sidebar that lists all available layers and related knobs and toggles.
  - ZH: 地图图层的主要控制界面是可折叠的侧边栏，其中列出了所有可用图层以及相关的控件与开关。
  - Notes: sidebar in [features and screens][kmu-features].
    Control in [features and screens][kmu-features].

## Available map layers

- EN: Available map layers
  - ZH: 可用的地图图层

### Political Map

- EN: Political Map (P)
  - ZH: 政治地图 (P)
- EN: Focuses on populated colonies (markets) and their affiliation.
  - ZH: 关注有人居住的殖民地（市场）及其归属。
  - Notes: Colony and Market in [colonies and markets][kmu-colonies]; 有人居住 is vanilla's Inhabited filter in [screens and the map][kmu-screens].
    Affiliation in [the political map][kmu-politics].
- EN: Only they paint the map.
  - ZH: 只有它们会为地图上色。
  - Notes: Paint in [the political map][kmu-politics].
- EN: Decivilised markets are treated as populated (can be disabled in settings) and aligned with Neutral faction (politically disorganised as contrasted by the Independent faction).
  - ZH: 荒蛮市场视为有人居住（可在设置中关闭），归入中立势力（政治上无组织，与非势力团体相对）。
  - Notes: decivilised in [the political map][kmu-politics]; Independent in [factions and relations][kmu-factions].
    The Neutral faction in [factions and relations][kmu-factions].
- EN: Systems only with decivilised markets are painted in their own style (adjustable in settings).
  - ZH: 只有荒蛮市场的星系以独立的样式绘制（可在设置中调整）。
- EN: Markets with Abandoned Station condition are treated as unpopulated and produce no map paint.
  - ZH: 带有废弃空间站条件的市场视为无人居住，不为地图上色。
  - Notes: Abandoned Station in [colonies and markets][kmu-colonies]; unpopulated in [the political map][kmu-politics].
- EN: All non-market entities produce no map paint.
  - ZH: 所有非市场实体都不为地图上色。
  - Notes: Non-market entities in [the political map][kmu-politics]; entity in [space][kmu-space].
- EN: Examples
  - ZH: 示例
- EN: image `promo/en/domination-factions-unpopulated.png`
  - ZH: image `promo/zh-hans/domination-factions-unpopulated.png`
- EN: Unclaimed unpopulated systems are drawn very faintly just to show where systems touch.
  - ZH: 无宣称的无人星系只以极淡的线条绘制，仅用于显示星系之间的相接位置。
  - Notes: uninhabited systems in [the political map][kmu-politics], which the English calls unpopulated here.
- EN: Those outlines of unpopulated systems can be toggled on and off independently from populated systems,
  - ZH: 无人星系的这些轮廓可以独立于有人星系开关；
  - Notes: Populated systems and outline in [the political map][kmu-politics].
- EN: which are always drawn even when there's no organised faction in the system (e.g. all colonies in a system are decivilised).
  - ZH: 有人星系始终绘制，即使星系中没有任何有组织的势力（例如星系内所有殖民地都已荒蛮）。
- EN: You can also adjust their visual style independently from faction and decivilised systems.
  - ZH: 你也可以独立于势力星系和荒蛮星系调整它们的外观样式。
  - Notes: faction systems and decivilised system in [the political map][kmu-politics].
- EN: Any given system can be painted by only a single faction, so each system draws a presence ribbon hugging its border that represents each populated colony with the color of the faction holding them.
  - ZH: 每个星系只能由一个势力上色，因此每个星系会沿其边界绘制一条存在色带，以持有各殖民地的势力颜色表示每个有人居住的殖民地。
  - Notes: presence ribbon and border in [the political map][kmu-politics].
- EN: Ribbons are shortened to represent solidified control when only a single faction or alliance is present in a system, while that presence is in no violation of unconditional claims.
  - ZH: 当星系中只有一个势力或联盟在场，且这种存在不违反任何无条件宣称时，色带会缩短，以表示稳固的控制。
  - Notes: Present in [the political map][kmu-politics]; unconditional claim in [vanilla mechanics without a name][kmu-unnamed].
    Solidified control in [the political map][kmu-politics].
- EN: Examples
  - ZH: 示例
- EN: image `promo/en/domination-factions-presence-ribbon.png`
  - ZH: image `promo/zh-hans/domination-factions-presence-ribbon.png`
- EN: Presence ribbons: Samarra draws one showing 3 Hegemony markets and 1 Independent market.
  - ZH: 存在色带：Samarra 的色带显示 3 个霸主市场和 1 个非势力团体市场。
  - Notes: faction names are the core localisation's own, read from its faction files, the same on every edition.
    Hegemony in [factions and relations][kmu-factions].
- EN: Expandable system tooltips provide detailed information on how each market affects the balance of power.
  - ZH: 可展开的星系提示框详细说明每个市场如何影响力量平衡。
  - Notes: System tooltip in [hovering and tooltips][kmu-hover]; balance of power in [the political map][kmu-politics].
- EN: Examples
  - ZH: 示例
- EN: image `promo/en/domination-factions-tooltip-lists-neutral-markets.png`
  - ZH: image `promo/zh-hans/domination-factions-tooltip-lists-neutral-markets.png`
- EN: Tooltips list Neutral (unowned) markets.
  - ZH: 提示框会列出中立（无主）市场。
  - Notes: Unowned in [vanilla mechanics without a name][kmu-unnamed].
- EN: image `promo/en/domination-factions-tooltip-lists-not-contributing-markets.png`
  - ZH: image `promo/zh-hans/domination-factions-tooltip-lists-not-contributing-markets.png`
- EN: Tooltips list markets producing no domination score.
  - ZH: 提示框会列出不产生主导得分的市场。
  - Notes: domination and Score in [the political map][kmu-politics].
- EN: Factions holding any population centers are listed in a sortable filter.
  - ZH: 持有任何有人居住市场的势力都会列在一个可排序的筛选列表中。
  - Notes: Population center in [the political map][kmu-politics]; sortable filter in [features and screens][kmu-features].
    "Population center" is any populated market here, so not vanilla's 人口中心, which reads as a large hub.
- EN: Selecting any of them spotlights their presence across the sector.
  - ZH: 选中其中任意一个，即可聚焦其在整个星域的存在。
  - Notes: spotlight and presence in [the political map][kmu-politics]; Sector in [space][kmu-space].
- EN: Neutral is listed there alongside organised factions, so selecting it spotlights every system holding decivilised colonies.
  - ZH: 中立与有组织的势力并列其中，选中它即可聚焦所有持有荒蛮殖民地的星系。
  - Notes: decivilised colony in [the political map][kmu-politics].
- EN: In domination views (Factions and Alliances) solid fill represents domination, hatched fill represents presence in a system dominated by somebody else, no fill represents claims on unpopulated systems.
  - ZH: 在主导视图（势力与联盟）中，实色填充表示主导，斜线填充表示在由他人主导的星系中的存在，不填充表示对无人星系的宣称。
  - Notes: Factions, Alliances, fill and hatch fill in [the political map][kmu-politics].
    Domination views, solid fill and no fill in [the political map][kmu-politics]; view in [features and screens][kmu-features].
- EN: On the Claims view all systems are painted with solid fill.
  - ZH: 在宣称视图中，所有星系均以实色填充绘制。

### Political Map examples

- EN: Examples
  - ZH: 示例
- EN: image `promo/en/domination-factions-filter.png`
  - ZH: image `promo/zh-hans/domination-factions-filter.png`
- EN: Faction filter, expanded to 2 columns, sorted by faction attitude toward the player.
  - ZH: 势力筛选列表，展开为 2 列，按势力对玩家的关系排序。
  - Notes: Attitude in [factions and relations][kmu-factions]; Columns in [the political map][kmu-politics].
    Faction filter in [features and screens][kmu-features].
- EN: image `promo/en/domination-factions-luddic-church.png`
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: Alliances view filtered by the Luddic Church alliance.
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: image `promo/en/domination-factions-pirates.png`
  - ZH: image `promo/zh-hans/domination-factions-pirates.png`
- EN: Factions view filtered by Pirates.
  - ZH: 按海盗筛选的势力视图。
  - Notes: Pirate in [colonies and markets][kmu-colonies].
- EN: image `promo/en/domination-factions-unpopulated-claimed.png`
  - ZH: image `promo/zh-hans/domination-factions-unpopulated-claimed.png`
- EN: Unpopulated systems get no paint in domination views.
  - ZH: 在主导视图中，无人星系不上色。
- EN: Tia star system is shown as Hegemony-owned because Hegemony holds an unconditional claim for it.
  - ZH: Tia 星系显示为霸主所有，因为霸主对它持有无条件宣称。

### Political Map views

- EN: The Political Map comes with 3 views:
  - ZH: 政治地图提供 3 种视图：
  - Notes: View in [features and screens][kmu-features].
- EN: Factions - paints faction territory based on a custom domination algorithm (highly customisable in settings) that weighs markets sizes, stability, whether a market is hidden, presence of an orbital station (or if a station is militarised), and the number of patrol fleets generated (by their sizes).
  - ZH: 势力：根据自定义的主导算法（可在设置中大幅调整）为势力领土上色。该算法综合考虑市场规模、稳定性、市场是否隐藏、是否有轨道空间站（以及空间站殖民地是否建有轨道空间站），以及生成的巡逻队数量（按其规模）。
  - Notes: territory in [the political map][kmu-politics]; Market size, Stability, Hidden and Orbital Station in [colonies and markets][kmu-colonies].
    Domination algorithm and militarised in [the political map][kmu-politics]; patrol in [colonies and markets][kmu-colonies].
    "Militarised" is described rather than named: 军事化 is vanilla's word for a ship hullmod.
- EN: Examples
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: image `promo/en/domination-factions-penelope_star.png`
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: Luddic Church unconditionally claims Penelope's Star but isn't present there,
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: and per domination rules actually present faction (Independent) reads as the holder.
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: The claim is still listed in the tooltip.
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: Alliances (only visible with Nexerelin installed) - same as Factions but allied factions stand as a single political entity with their holdings combined.
  - ZH: 联盟（仅在安装了 Nexerelin 时可见）：与势力视图相同，但结盟的势力作为单一政治实体出现，其持有合并计算。
  - Notes: Alliances in [the political map][kmu-politics].
    Political entity in [the political map][kmu-politics].
- EN: Examples
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: image `promo/en/domination-alliances-tooltip.png`
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: Tooltips in the Alliances view combine domination score of present alliance members.
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: Claims - paints faction territory based on the vanilla system claim mechanic - dynamic or unconditional.
  - ZH: 宣称：根据原版的星系宣称机制为势力领土上色，包括动态宣称与无条件宣称。
  - Notes: Claims in [the political map][kmu-politics].
    Unconditional claim and dynamic claim in [vanilla mechanics without a name][kmu-unnamed].
- EN: Unconditional claims are set via sector memory and are the highest authority.
  - ZH: 无条件宣称通过星域记忆设置，具有最高权威。
  - Notes: Sector memory in [vanilla mechanics without a name][kmu-unnamed].
- EN: Unless a claim is unconditional, system claim is resolved dynamically - the single biggest market (that participates in the economy) wins, boosted by the presence of same-faction markets (of any kind) and any military industry constructed.
  - ZH: 除非宣称是无条件的，否则星系宣称会动态判定：参与经济的最大单个市场胜出，同势力市场（任何类型）的存在和已建成的军事设施会给予加成。
  - Notes: Same-faction market bonus and Military (claim factor) in [the political map][kmu-politics].
    Military industry in [the political map][kmu-politics].
- EN: The player faction and non-territorial factions cannot lay claims dynamically.
  - ZH: 你的势力和非领土势力无法动态宣称。
  - Notes: non-territorial in [vanilla mechanics without a name][kmu-unnamed].
    The player faction in [factions and relations][kmu-factions].
- EN: If you filter by a non-territorial faction, unclaimed systems with their presence will be spotlit.
  - ZH: 若按非领土势力筛选，则会聚焦其在场的无宣称星系。
- EN: Examples
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: image `promo/en/claims-cored-but-somebody-else-is-present.png`
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: Luddic Church isn't present in Penelope's Star system, holds an unconditional claim on it,
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: and there's a non-territorial faction (Independent) present.
  - ZH: none
  - Notes: no Chinese screenshot yet, so the Fossic post leaves this out.
- EN: Spoiler protection
  - ZH: 防剧透
  - Notes: spoilers as in Visibility overrides (SPOILERS) in [settings][kmu-settings].
- EN: To avoid spoilers, map layers operate on a vanilla-derived visibility system (each component is available in settings):
  - ZH: 为避免剧透，地图图层基于一套源自原版的可见性系统运作（每一项都可在设置中调整）：
  - Notes: visibility as in the Map - Visibility tab in [settings tabs][kmu-tabs].
- EN: Hidden systems (with no hyperspace anchor generated off of a star, nebula, or a black hole) are not revealed unless there's a visible population center present or it holds an active gate.
  - ZH: 隐藏星系（没有由恒星、星云或黑洞生成的超空间锚点）不会显示，除非其中有可见的有人居住的市场，或持有激活的星门。
  - Notes: Hyperspace, Nebula and Gate in [space][kmu-space]; Hidden in [colonies and markets][kmu-colonies].
    Hidden system in [the political map][kmu-politics]; hyperspace anchor in [vanilla mechanics without a name][kmu-unnamed]; star and black hole in [space][kmu-space]; population center in [the political map][kmu-politics].
- EN: Entities marked as discoverable (hidden on the system map until in range of player fleet sensors) are not revealed.
  - ZH: 标记为可发现的实体（在玩家舰队探测范围之外时不显示在星系地图上）不会显示。
  - Notes: fleet in KMLib's [what a failed binding costs][kmlib-costs].
    Discoverable in [vanilla mechanics without a name][kmu-unnamed]; entity and sensors in [space][kmu-space]; system map in [screens and the map][kmu-screens].
- EN: They are also listed on Claims tooltips because vanilla uses them in claim calculations, but are redacted.
  - ZH: 由于原版在宣称计算中会用到它们，它们也会列在宣称提示框中，但会被隐去。
  - Notes: redacted text in [hovering and tooltips][kmu-hover].
- EN: Markets marked as hidden are not revealed if there are no visible colonies held by other non-allied factions, or until the player visits their system.
  - ZH: 标记为隐藏的市场不会显示，除非其他非同盟势力在该星系持有可见的殖民地，或玩家已到访该星系。
  - Notes: non-allied factions in [the political map][kmu-politics].
- EN: They are also listed on Claims tooltips because vanilla uses them in claim calculations, but are redacted.
  - ZH: 由于原版在宣称计算中会用到它们，它们也会列在宣称提示框中，但会被隐去。
- EN: Decivilised markets are not revealed unless they start so, or until the player performs a full survey on them (adjustable in settings).
  - ZH: 荒蛮市场不会显示，除非它们一开始就是荒蛮的，或玩家已对其完成全面调查（可在设置中调整）。
  - Notes: Fully surveyed in [surveys][kmu-surveys]; the grey aside marks a setting, as the other bullets do, and is in [settings][kmu-settings].
- EN: These markets are also revealed if there are visible colonies held by other non-allied factions.
  - ZH: 若其他非同盟势力在该星系持有可见的殖民地，这些市场也会显示。
  - Notes: non-allied factions in [the political map][kmu-politics].
- EN: Unowned markets with the Abandoned Station condition follow visibility rules of hidden markets.
  - ZH: 带有废弃空间站条件的无主市场遵循隐藏市场的可见性规则。
  - Notes: Unowned in [vanilla mechanics without a name][kmu-unnamed].

## Settings

- EN: This mod comes with a suite of LunaLib settings that allow you to tweak, visuals, sound volume, keybinds, underlying mechanics.
  - ZH: 本 Mod 附带一整套 LunaLib 设置，可调整外观、音量、按键以及底层机制。
  - Notes: 外观 and 按键 as in the tab names in [settings tabs][kmu-tabs].
    The tab is 音效 (sound); the post says 音量 because the English says volume.
    Volume in [game and mods][kmu-mods]; underlying mechanics in [features and screens][kmu-features].

## Console commands

- EN: Console commands
  - ZH: 控制台指令
  - Notes: Console commands in [console commands][kmu-console].
- EN: If you've got Console Commands installed you can make use of the following commands.
  - ZH: 若安装了 Console Commands，可以使用以下指令。
- EN: Provided by KMU:
  - ZH: 由 KMU 提供：
- Both: `kmu_profiling [tree|flat|walks] [namespace=<prefix>] [top=<count>] [perframe] [reset]`
- EN: Writes full or scoped performance measurements to game logs on demand.
  - ZH: 按需将完整或指定范围的性能测量写入游戏日志。
  - Notes: log as in Log verbosity in KMLib's [settings][kmlib-settings].
    Performance measurements and game logs in [console commands][kmu-console].
- EN: Calls exceeding the budget are logged automatically without this command.
  - ZH: 超出预算的调用会自动记录，无需此指令。
- EN: Requires profiling to be enabled in settings.
  - ZH: 需要在设置中启用性能分析。
  - Notes: Enable X in [settings][kmu-settings].
    Profiling in [settings][kmu-settings].
- EN: Provided by KMLib:
  - ZH: 由 KMLib 提供：
- Both: `kmlib_activate_gate <id>`
- EN: Activates the gate with that `id` in the current system.
  - ZH: 激活当前星系中该 `id` 的星门。
  - Notes: Gate in [space][kmu-space].
- Both: `kmlib_colonise [entity-id] [faction-id]`
- EN: Founds a colony.
  - ZH: 建立殖民地。
- EN: [entity-id] defaults to the nearest colonisable market.
  - ZH: [entity-id] 默认为最近的可殖民市场。
  - Notes: Colonisable market in [console commands][kmu-console].
- EN: [faction-id] defaults to the player faction.
  - ZH: [faction-id] 默认为你的势力。
- Both: `kmlib_list_factions [markets|hidden|discoverable|no_markets] [no_holdings] [no_attitude] [to_log]`
- EN: Lists every faction with what it holds - how many places, how many hidden, how many discoverable, and the systems they sit in.
  - ZH: 列出每个势力及其持有：地点数量、隐藏数量、可发现数量，以及所在的星系。
- EN: By default no filter is applied.
  - ZH: 默认不应用任何筛选。
- Both: `kmlib_list_map_spoilers`
- EN: Lists faction-owned systems as a tree of system, entities and factions, flagging cut-off systems and undiscovered markets.
  - ZH: 以星系、实体、势力的树状结构列出势力所有的星系，并标出被截断的星系和未发现的市场。
  - Notes: undiscovered in [hovering and tooltips][kmu-hover].
    Cut-off systems in [console commands][kmu-console].
- Both: `kmlib_list_system_entities [gates]`
- EN: Lists the current system's entities as an orbit tree, then the unorbited ones and fleets with coordinates.
  - ZH: 以轨道树列出当前星系的实体，然后是无轨道的实体以及带坐标的舰队。
- EN: By default the [gates] filter is not applied.
  - ZH: 默认不应用 [gates] 筛选。
- Both: `kmlib_spawn <gate|jump_point> [orbit_focus_id] [speed] [jitter=<frac>]`
- EN: Spawns a gate or a jump point at the fleet position.
  - ZH: 在舰队位置生成星门或跳跃点。
  - Notes: 跳跃点 is the jump-point label in KMLib's own strings.
    Jump point in [space][kmu-space].
- EN: [orbit_focus_id] defaults to system center.
  - ZH: [orbit_focus_id] 默认为星系中心。
- EN: [speed] is derived from radius by default.
  - ZH: [speed] 默认由半径推算。
- EN: [jitter] defaults to 0.25.
  - ZH: [jitter] 默认为 0.25。
- Both: `kmlib_transfer_market [entity-id] [faction-id]`
- EN: Hands an existing colony to another owner.
  - ZH: 将现有殖民地移交给另一所有者。
- EN: [entity-id] defaults to the nearest colonisable market.
  - ZH: [entity-id] 默认为最近的可殖民市场。
- EN: [faction-id] defaults to the player faction.
  - ZH: [faction-id] 默认为你的势力。

## Compatibility

- EN: Compatibility
  - ZH: 兼容性
  - Notes: as the Map - Compatibility tab in [settings tabs][kmu-tabs].
- EN: KMU injects map layers into the map widget as map-only terrain.
  - ZH: KMU 将地图图层作为仅在地图上显示的地形注入星图控件。
  - Notes: Sector map widget in [features and screens][kmu-features]; terrain in [space][kmu-space].
- EN: And to be visible in the Starscape mode, the starscape terrain variant is tagged as a slipstream.
  - ZH: 为了在星景模式下也能显示，星景地形变体被标记为滑流。
  - Notes: Starscape in [screens and the map][kmu-screens].
    Slipstream in [space][kmu-space].
- EN: Custom terrain is the only thing that has to be serialised into saves as Java class references, and that's why a clean uninstall is required for disabling the mod.
  - ZH: 自定义地形是唯一必须以 Java 类引用的形式序列化进存档的内容，这也是禁用本 Mod 前需要干净卸载的原因。
  - Notes: Save in [game and mods][kmu-mods].
    Uninstall in [KMLib's forum terms][kmlib-forum].
- EN: The game reuses the map widget, and so do many mods.
  - ZH: 游戏会复用星图控件，许多 Mod 也是如此。
- EN: Those custom placements of the widget get map layers for free, but they should come with no mouseover detection and no controls (borrowing selections from the map screen as the main instance) without a compatibility patch.
  - ZH: 这些自定义放置的星图控件会免费获得地图图层，但在没有兼容补丁的情况下，它们没有鼠标悬停检测，也没有控件（图层选择沿用星图界面这一主实例的选择）。
  - Notes: Compatibility patch and mouseover detection in [features and screens][kmu-features].
- EN: Additionally, the mod has been made specifically compatible with:
  - ZH: 此外，本 Mod 专门与以下 Mod 做了兼容：
- EN: Fast Rendering from v0.9.1rc1 on, which answers the map's cursor read itself.
  - ZH: Fast Rendering v0.9.1rc1 及更高版本，它会自行应答星图的光标读取。
- EN: On an older release the map stops following the cursor, and a notice names the release to update to.
  - ZH: 在更早的版本上，星图不再跟随光标，并会有通知说明应更新到哪个版本。
- EN: Nexerelin to support its alliance mechanics.
  - ZH: Nexerelin，以支持其联盟机制。
  - Notes: Alliances in [the political map][kmu-politics].
- EN: Random Assortment of Things and its minimap replacement.
  - ZH: Random Assortment of Things 及其小地图替换。
- EN: The minimap draws the map widget via a tooltip in the corner of the screen.
  - ZH: 小地图通过屏幕角落的一个提示框绘制星图控件。
- EN: This mod comes with a RAT compatibility mode enabled by default that scopes mouse detection to the widget.
  - ZH: 本 Mod 附带默认开启的 RAT 兼容模式，将鼠标检测限定在该星图控件范围内。
  - Notes: Compatibility mode in [features and screens][kmu-features].
- EN: Layer selection is inherited from the map screen.
  - ZH: 图层选择沿用星图界面。
- EN: As a bonus, the map widget is made invisible when it's docked off-screen (docking is RAT's way of hiding the map widget when the player docks at markets or opens any UI screens) - that disables any related rendering and should provide a marginal performance improvement (more so when in hyperspace) in the menus that don't show the sector map.
  - ZH: 此外，当星图控件停靠到屏幕外时（停靠是 RAT 在玩家停靠市场或打开任何界面时隐藏星图控件的方式），本 Mod 会将其设为不可见，从而跳过相关渲染，在不显示星图的菜单中应能带来少许性能提升（在超空间中更明显）。
  - Notes: Hyperspace in [space][kmu-space].
    Dock in [features and screens][kmu-features].

## Feedback

- EN: While playing with this pre-release, please report if you think it relates to this mod:
  - ZH: 在试用本预发布版的过程中，如果你认为以下情况与本 Mod 有关，请反馈：
  - Notes: Pre-release in [KMLib's forum terms][kmlib-forum].
- EN: what kind of visual or technical issues you've got,
  - ZH: 遇到了哪些视觉或技术问题，
- EN: how's your performance,
  - ZH: 性能表现如何，
- EN: how's your user experience,
  - ZH: 使用体验如何，
- EN: if you see mod incompatibilities or if the map ignores any modded content,
  - ZH: 是否发现 Mod 不兼容，或地图忽略了某些 Mod 内容，
- EN: if the map spoils anything you think it's not supposed to.
  - ZH: 地图是否剧透了你认为不该显示的内容。
- EN: Diagnostics options:
  - ZH: 诊断选项：
- EN: The DEBUG logging level in both KMLib and KMU dev settings. To write all internal mod logs.
  - ZH: KMLib 与 KMU 开发设置中的 DEBUG 日志详细程度，用于写入 Mod 的全部内部日志。
  - Notes: Log verbosity in KMLib's [settings][kmlib-settings].
    Dev settings in [settings][kmu-settings].
- EN: A reflection logs toggle in KMU dev settings. To trace issues interacting with vanilla UI.
  - ZH: KMU 开发设置中的反射日志开关，用于追踪与原版界面交互时的问题。
  - Notes: reflection in KMLib's [settings][kmlib-settings].
- EN: Profiling toggle and level in KMU map dev settings combined with usage of kmu_profiling console command. To trace where performance drops occur.
  - ZH: KMU 地图开发设置中的性能分析开关与级别，配合 kmu_profiling 控制台指令使用，用于定位性能下降的位置。
  - Notes: Map - Dev in [settings tabs][kmu-tabs].
    Profiling in [settings][kmu-settings].
- EN: You can try breaking the mod or just play as you normally would.
  - ZH: 你可以尝试把这个 Mod 玩坏，也可以像平时一样正常游玩。
- EN: And feel free to share suggestions, ideas, requests - those will affect this mod's direction and priorities.
  - ZH: 也欢迎分享建议、想法和需求，它们会影响本 Mod 的方向与优先级。
- EN: While I have ideated and planned out some info-layers I want to implement next, and some of their conception - that covers my own vision, and I'd like to hear what's yours.
  - ZH: 我已经构思并规划了接下来想实现的一些信息图层及其大致设计，但那只代表我自己的想法，我也想听听你的。
  - Notes: Info-layer in [features and screens][kmu-features].

## Post-release roadmap

Fractal Softworks only: every line below has no Chinese counterpart.
The [source notes][source-notes] give why.

- EN: Post-release roadmap
- EN: Vanilla intel integration into the visibility system.
- EN: Map layer help tooltips.
- EN: Faction and alliance tooltips off of the sortable filter list.
- EN: Filling in wedges and gaps between system cells.
- EN: Processing non-market entities and showing what's visible on system tooltips.
- EN: Incorporating some modded entities into the domination algorithm, like watchtowers and artillery implemented in Industrial Evolution.
- EN: A diplomatic map derived from the political map but focused on relations.
- EN: An Enemies view that paints the map by the lowest relations in a system, and a Friends view that paints only systems with factions above a chosen threshold.
- EN: Targets player faction relations by default, and applying the faction filter applies the focus to a specific faction.
- EN: Presence ribbons are painted with relation colors instead of faction colors.
- EN: The political map is extended with Friends and Enemies filter recede modes - when a faction filter is applied, the rest of the sector paints relations instead of being muted or desaturated.
- EN: An infrastructure map focused on non-market entities. For example a comm relay network view that shows detected network coverage.
- EN: An economy map focused on markets, industries, and commodities.
- EN: A faction management layer focused on player faction markets and fleets.
- EN: A framework for modders defining and supplying their own custom layers based on existing ones or brand new.

## AI usage disclaimer

- EN: AI usage disclaimer
  - ZH: AI 使用声明
- EN: this mod has been made with usage of LLMs - for research, brainstorming, code generation, code review, code refactoring, translation.
  - ZH: 本 Mod 的制作使用了大语言模型（LLM），用于调研、头脑风暴、代码生成、代码审查、代码重构和翻译。
- EN: Each step was performed with human oversight and playtesting, with as much human code review as can be reasonably performed for a mod project.
  - ZH: 每一步都在人工监督和实机测试下完成，并尽 Mod 项目所能进行了人工代码审查。
- EN: The Simplified Chinese translation has been done using the following technique.
  - ZH: 简体中文翻译按以下方法完成。
- EN: Vanilla vocabulary was matched to the Chinese core localisation, and other mods' vocabulary to their own Chinese versions.
  - ZH: 原版词汇对照中文汉化逐一匹配，其他 Mod 的词汇对照其各自的中文版匹配。
- EN: Forum vocabulary was collected as well, and all of it was gathered into a translation reference that records the rationale for each term.
  - ZH: 论坛相关词汇也一并收集，全部汇总为一份翻译参考，并为每个术语注明选词理由。
- EN: KMLib's and KMU's own vocabulary was translated by the intent behind each term.
  - ZH: KMLib 与 KMU 自身的词汇依据每个术语背后的含义翻译。
- EN: The mod author reviewed all the collected vocabulary against those rationales, and the translations were applied in several passes for consistency.
  - ZH: 收集的全部词汇均经 Mod 作者对照选词理由审阅，译文分多轮应用，以求前后一致。
  - Notes: the translation paragraph. Translation reference, mod author and core localisation in
    [KMLib's forum terms][kmlib-forum]; vanilla in the same section.

## Thanks

- EN: Thanks:
  - ZH: 致谢：
- EN: To the Fractal Softworks team for bringing us Starsector and continuously improving it.
  - ZH: 感谢 Fractal Softworks 团队带来《远行星号》并持续改进它。
  - Notes: Starsector in [game and mods][kmu-mods].
- EN: To the modding community for producing awesome and inspiring mods that bring me back to Starsector every time.
  - ZH: 感谢 Mod 社区创作了众多精彩而富有启发的 Mod，让我一次次回到这个游戏。
- EN: To developers and maintainers of LazyLib, LunaLib, Console Commands for providing features this mod relies on.
  - ZH: 感谢 LazyLib、LunaLib、Console Commands 的开发者与维护者提供了本 Mod 所依赖的功能。
- EN: To developers and maintainers of Nexerelin for bringing the politics of the sector to life.
  - ZH: 感谢 Nexerelin 的开发者与维护者让星域的政治变得鲜活。
- EN: To Genir for implementing a more stable compatibility solution in Fast Rendering.
  - ZH: 感谢 Genir 在 Fast Rendering 中实现了更稳定的兼容方案。
- EN (back-translation): To the 远星汉化组 (the Chinese localisation team): this Chinese edition's fonts and vanilla terms come from their localisation.
  - ZH: 感谢远星汉化组：本中文版所用的字体和原版术语都出自他们的汉化。
  - Notes: Fossic only; the line states its own reason.
- EN: To awesome folks over at Fractal Softworks forums, at r/Starsector, and in Discord communities for feedback and support.
  - ZH: 感谢 Fractal Softworks 论坛、r/Starsector 和各 Discord 社区的朋友们给予的反馈与支持。

[source-notes]: fossic-post.bbcode
[kmu-holds]: ../localisation/zh-hans/README.md#what-this-folder-holds
[kmu-unnamed]: ../localisation/zh-hans/README.md#vanilla-mechanics-without-a-name
[kmu-rules]: ../localisation/zh-hans/README.md#rules
[kmu-screens]: ../localisation/zh-hans/README.md#screens-and-the-map
[kmu-space]: ../localisation/zh-hans/README.md#space
[kmu-colonies]: ../localisation/zh-hans/README.md#colonies-and-markets
[kmu-factions]: ../localisation/zh-hans/README.md#factions-and-relations
[kmu-surveys]: ../localisation/zh-hans/README.md#surveys
[kmu-mods]: ../localisation/zh-hans/README.md#game-and-mods
[kmu-features]: ../localisation/zh-hans/README.md#features-and-screens
[kmu-politics]: ../localisation/zh-hans/README.md#the-political-map
[kmu-hover]: ../localisation/zh-hans/README.md#hovering-and-tooltips
[kmu-settings]: ../localisation/zh-hans/README.md#settings
[kmu-tabs]: ../localisation/zh-hans/README.md#settings-tabs
[kmu-console]: ../localisation/zh-hans/README.md#console-commands
[kmu-banner]: ../localisation/zh-hans/README.md#the-forum-banner
[kmlib-forum]: https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#forum-terms
[kmlib-costs]: https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#what-a-failed-binding-costs
[kmlib-settings]: https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md#settings
