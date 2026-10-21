# Update posts

Templates for the short post that announces a release in each mod thread.
KMLib releases are announced in the KMU threads, alone or beside a KMU release.

## Index

- [Content](#content)
- [Fractal Softworks forum](#fractal-softworks-forum)
- [GitHub](#github)
- [Discord](#discord)
- [Fossic](#fossic)

## Content

- **One item per player-facing changelog entry.**
  Take only the top-level sections.
  The `For developers` block stays out of the post.
- **Reword each entry for players.**
  A post item names what the player saw, not what the code did.
  Drop the changelog's `[KMU]` tag; the thread already says which mod it is.
- **Credit a report at the end of its item**, as its own italic sentence.
  Name the reporter and the place, with no link.
  The changelog keeps the link to the report.
- **Name every released mod with its version**, in bold.
  When KMU and KMLib ship together, one post announces both.

Placeholders below are in angle brackets.
`<repo>` is the GitHub repository name, such as `Starsector-Mod-KMLib`.

## Fractal Softworks forum

SMF BBCode.
Each mod name links to its GitHub release.

```bbcode
[b][url=https://github.com/Klark-Morrigan/<repo>/releases/tag/<version>]<mod> <version>[/url][/b] is released. Full changelogs are on GitHub.

[list]
[li]<entry>. [i]- Reported by [b]<reporter>[/b] at [b]<place>[/b].[/i][/li]
[/list]
```

For two mods, the opening reads `[b][url=...]KMU <version>[/url][/b] and [b][url=...]KMLib <version>[/url][/b] are released.`

## GitHub

Markdown.
GitHub turns every newline in a comment into a visible break,
so each paragraph and each item stays on one line.

```markdown
**[<mod> <version>](https://github.com/Klark-Morrigan/<repo>/releases/tag/<version>)** is released. Full changelogs are on GitHub.

- <entry>. _- Reported by **<reporter>** at **<place>**._
```

## Discord

Discord's own Markdown.
Like GitHub, it shows every newline,
so each paragraph and each item stays on one line.

```markdown
**[<mod> <version>](<https://github.com/Klark-Morrigan/<repo>/releases/tag/<version>>)** is released. Full changelogs are on GitHub.

- <entry>. _- Reported by **<reporter>** at **<place>**._
```

- **The angle brackets around the link's address stop Discord from adding a preview** of the release page under the post.
- **A report made on the same server** credits it with "here" in place of the place name.
- **A message holds at most 2000 characters.**
  A longer post splits into one message per mod.

## Fossic

Discuz! BBCode, in Chinese.
Fossic players download from the panel above the first post, not from GitHub.
Upload the new archive to that panel before posting,
because the post says it is already there.

```bbcode
[b]<mod> <version>[/b] 已发布，压缩包已在本帖上方的下载面板中更新。

[list]
[*]修复了<entry>。[i]由 [b]<reporter>[/b] 在 [b]<place>[/b] 报告。[/i]
[/list]
```

- **List items start with `[*]`.**
  Discuz! has no `[li]` tag.
- **Two mods** join with 与: `[b]KMU <version>[/b] 与 [b]KMLib <version>[/b] 已发布`.
- **A release posted the next day** says 已于昨晚发布 ("was released last night") in place of 已发布.
- **A fix item opens with 修复了** ("fixed").
  A repeat fix opens with 第二次修复了 ("fixed for the second time").
- **A report made in the Fossic thread** credits it with 在本帖报告 ("reported in this thread") in place of the place name.
- **Terms come from the vocabulary table** in KMLib's [localisation/zh-hans/README.md](https://github.com/Klark-Morrigan/Starsector-Mod-KMLib/blob/master/localisation/zh-hans/README.md).
- **Check the result in a saved draft** (保存草稿).
  The Discuz! editor has no preview.
