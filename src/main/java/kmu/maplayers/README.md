# Map layers (`maplayers`)

A map layer is one overlay over the campaign map. A control box floating over the map carries a
strip of tabs, one per layer, and exactly one layer is active at a time - the same model as the
map's own Sector / System tabs. Selecting a tab decides what, if anything, is painted over the
sector, and opens that layer's own controls beneath the tabs.

The box draws on both screens that show the sector map: the full map screen (M) and the map preview
(the "visor") embedded in the intel screen.

Part of Klark Morrigan's Utilities; see the
[mod README](../../../../../README.md) for project context.

## Index

- [The layers](#the-layers)
- [Two screens, two picks](#two-screens-two-picks)
- [What is per screen](#what-is-per-screen)
- [Where each part lives](#where-each-part-lives)

## The layers

| Layer | Paints | Body |
| --- | --- | --- |
| **No Layer** | nothing - the map reads as vanilla | empty |
| **Political map** | the sector coloured by who controls each system | map-wide options, the view radio, the bloc spotlight picker, and the active view's own toggles |

No Layer leads the strip as a first-class tab rather than an off switch, so the strip always shows
what is and is not drawn and an empty map reads as a choice. The political map is the pick an
untouched save resolves to, so the overlay is up the first time the sector map opens. Each tab also
answers a LunaLib-rebindable shortcut, printed on the tab, on both screens the box draws; since the
picks are per-screen, a shortcut moves only the tab of the screen it was pressed on.

## Two screens, two picks

The same map widget draws on the sector map and inside the visor, so "which layer is active" cannot
be one shared value: it would paint the sector map's pick onto the intel screen and ignore the tab
the player is looking at. Each screen holds its own `PersistedActiveLayerSelection` under its own
key, and `MapLayerRegistry.isActive` resolves which is live per frame from which screen is up.

```mermaid
flowchart TD
    F([Frame]) --> Q{Is the intel<br/>screen up?}
    Q -- yes --> I[Intel screen's pick]
    Q -- no --> M[Sector map's pick]
    I --> A[Active layer]
    M --> A
    A --> O([What the overlay paints])
```

Switching tabs on one screen leaves the other where it was. Both picks persist; a save written
before the split stored one shared pick, which `migrateLegacyActiveLayerKey` fans into both keys on
load.

## What is per screen

Only the box's own state. Everything the controls set is shared, so the two screens cannot disagree
about what the overlay means.

| State | Scope |
| --- | --- |
| Which tab is lit | per screen, persisted |
| Whether the box is folded to its rail | per screen, persisted |
| How far the body is scrolled | per screen, for the session |
| Political-map view, bloc spotlight, sort, columns, and every other control value | shared, persisted once |
| Appearance settings | shared, in LunaLib |

## Where each part lives

- **`base/layer`** - the layer framework: `MapLayer` (id, tab label, body controls, default
  shortcut), `MapLayerRegistry` (roster, both screens' picks, save migrations), `NoLayer`.
- **`base/render`** - `MapLayerRenderer`, the seam a layer draws through, and the terrain surface
  that owns the map's render pass (`SectorMapLayerTerrainPlugin`, with the starscape pair that
  reaches the same draw while the Starscape filter is on). The surface asks the active layer for a
  renderer and hands it the frame, so it names no layer; a layer that only switches (No Layer)
  supplies none, which is read as nothing to draw.
- **[Cell geometry](base/geometry/README.md)** - the cells, edges, and clusters any painting layer
  is shaped out of, partitioned from the drawn systems and cached against them.
- **[Cluster-name overlay](base/labels/README.md)** - where a name is placed across a cluster and
  how it is drawn. What the name reads and what shade it takes arrive from the layer as functions
  of a grouping key, so the overlay names nothing itself.
- **[The sidebar](base/sidebar/README.md)** - the control box: the per-screen hosts, placement,
  fold persistence, and how it is drawn over and routed ahead of the vanilla screens.
- **[Political map](politicalmap/README.md)** - the one layer that paints, its three views, and the
  draw pipeline behind them.
- **`MapLayers`** - the composition root, the single place every concrete layer and political-map
  view is named and registered, so the framework below stays ignorant of which ones exist.
