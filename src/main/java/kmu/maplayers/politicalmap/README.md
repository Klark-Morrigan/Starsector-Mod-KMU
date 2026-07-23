# Political map (`politicalmap`)

An on-map overlay. It colours the sector by who controls it. Each bloc's territory shows as a
filled, bordered, named region over the campaign map.

The political map is one of the layers the on-map bar offers. When you pick it, a view-selector
radio appears at the top. The radio changes *what* "controls" means. It does not change how the
map is drawn.

Part of Klark Morrigan's Utilities; see the
[mod README](../../../../../../README.md) for project context.

## Index

- [What the player sees](#what-the-player-sees)
- [The views](#the-views)
- [Claim extensions](#claim-extensions)
- [How a view drives the pipeline](#how-a-view-drives-the-pipeline)
- [Where each part lives](#where-each-part-lives)

## What the player sees

For each controlling bloc the overlay draws four things:

- a coloured region,
- a border around it,
- faint seams inside it,
- the bloc's name across it.

A radio at the top of the tab picks the view. A filter picker below it can spotlight one bloc.
When a bloc is spotlighted, the rest fade into a muted background.

The radio always lists the views in the same order: **Factions**, then **Alliances** (only with
Nexerelin), then **Claims**.

## The views

A view is a small set of rules on top of one shared draw pipeline. Each view answers three
questions, and the pipeline paints the answer without knowing which view asked:

- How do factions group into blocs?
- Which systems does each bloc paint?
- How is each bloc styled and named?

| View | Groups by | Paints | Spotlight targets | Needs |
| --- | --- | --- | --- | --- |
| **Factions** | each faction on its own | held territory, per faction | any faction | always (default) |
| **Alliances** | allied factions fused per alliance | held territory, alliances as one bloc | alliances only | Nexerelin |
| **Claims** | each claiming faction on its own | claimed systems, per faction | none | always |

Notes on each:

- **Factions.** The plain case. Every faction is its own bloc. Only genuine independent space
  fades to the muted style. A bloc is named after its faction.
- **Alliances.** Allied factions merge into one coloured, named region per alliance. Unaligned
  factions keep their own border and name. Two toggles fade a non-allied faction: *Mute* dims it,
  *Desaturate* makes it read as background ground. With both off, a lone faction looks exactly as
  it does on the Factions view.
- **Claims.** Shows the vanilla "system claimed by faction" mechanic - the same claim the
  colony-survey panel warns about. Every claimed system is painted solid in its claimant's colours.
  There is no alliance grouping here, and no spotlight.

## Claim extensions

The Factions and Alliances views do not only paint held ground. A system a faction *claims* but
does not *hold* joins that faction's territory drawn empty: inside the border and under the
faction's name, but with no fill. The Claims view instead paints those same claims solid. Why a
claim resolves this way is [ownership resolution](base/politics/ownership/README.md); how the empty
fill is drawn is [territory fills and borders](base/render/territories/README.md).

## How a view drives the pipeline

Every view resolves ownership through one seam. The pipeline reads who-paints-what from that one
source, and never branches on which view is active.

```mermaid
flowchart TD
    V([Selected view]) --> G[Grouping:<br/>how factions form blocs]
    V --> O[Ownership source:<br/>which systems each bloc paints]
    G --> PIPE[Shared draw pipeline]
    O --> PIPE
    PIPE --> S[Shape cells into<br/>bordered territories]
    S --> Fi[Split the fill:<br/>solid / hatched / unfilled]
    Fi --> L[Overlay bloc names]
    L --> MAP([Coloured map])
```

What changes between views is only the two inputs, the grouping and the ownership source; from the
seam on, every view shapes, fills, and labels identically. The sources themselves and the three
fill states are [ownership resolution](base/politics/ownership/README.md).

## Where each part lives

- **[Ownership resolution](base/politics/ownership/README.md)** - the per-view ownership seam, the
  three sources, the three fill states, and the claim mechanic.
- **[Territory fills and borders](base/render/territories/README.md)** - how cells become each
  bloc's coloured region, border, seams, and split fill.
- **[Render style layer](base/render/style/README.md)** - how player settings become each
  territory's colours, widths, and opacities.
- **[Cluster-name overlay](base/render/labels/README.md)** - how bloc names are placed and drawn.

The rest of `base` carries the supporting parts: `politics` (grouping and the held/claim resolvers),
`geometry`, `visibility`, `refresh`, `hover`, `tooltip`, and `sidebar`. The class that names and
orders the views is `kmu.maplayers.MapLayers`, one level up.
