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
- **[The render surface](base/render/README.md)** - `MapLayerRenderer`, the seam a layer draws
  through - its overlay and the hover box over one cell of it - and the terrain that owns the map's
  render pass. It asks the active layer for a renderer and hands it the frame, so it names no layer;
  a layer that only switches (No Layer) supplies none, which is read as nothing to draw.
- **[Regions](base/render/regions/README.md)** - the shape work under that surface: turning shaped
  cells and opaque owner ids into borders, fills, and GL-ready runs. The cluster-border trace,
  the smoothing passes, the vertex packing, and the split fill that puts several fills inside one
  border - none of which interprets a key.
- **`base/visibility`** - which star systems a layer draws at all: `MapVisibility` admits a
  system on either of two paths (reachable and drawn by the vanilla map, or inhabited) and hashes the
  admitted set into the fingerprint that says it moved; `DrawnSystemPositions` exposes that rule as
  one predicate every walk shares, and each drawn system's live hyperspace position.
  `MapVisibilityOverrides` is the pair of widenings a caller may apply to that rule - count
  undiscovered colonies as inhabitation, or admit a system outright - so a layer can widen what is
  drawn without the rule knowing why it wanted to.
- **[Cell geometry](base/geometry/README.md)** - the cells, edges, and clusters any painting layer
  is shaped out of, partitioned from the drawn systems and cached against them.
- **[Cluster-name overlay](base/labels/README.md)** - where a name is placed across a cluster and
  how it is drawn. What the name reads and what shade it takes arrive from the layer as functions
  of an owner, so the overlay names nothing itself.
- **[The theme records](base/theme/README.md)** - the player's appearance choices as inert value
  types, read once per rebuild. The per-category tier is keyed on an open interface, so a layer
  brings its own categories and its own reader to populate them.
- **`base/hover`** - what the cursor is over, and what the map says back. The values are
  `MapHover` (the hovered cell and the cluster around it), `MapHoverState` (the
  shared holder the map render pass publishes to and the later UI passes read, since only that pass
  can invert a cursor pixel to a world point), and `HoverHighlight` (the loops and triangles one
  hover lights up). `HoverHighlightGeometry` resolves that geometry and `HoverHighlightRenderer`
  burns the halo and the wash, both over a `HoverHighlightSource` - the three questions only the
  layer that owns the regions can answer: the extent it painted under the cursor, the loops the
  cell might sit inside, and the shade its ground draws in.
- **`base/tooltip`** - the box floating beside the cursor, in a later UI pass than the map's own.
  `MapLayerCellTooltip` owns the gates every hover box shares (the settings toggle, the sector map
  with the starscape filter off, a hovered cell, stepping aside for the vanilla star tooltip) and
  draws whichever `MapHoverTooltip` the active layer's renderer injects - so a layer with none, and a
  switch-only tab with no renderer at all, show nothing for the same reason. `SystemCellTooltip` is
  the shape a layer's box takes - the hovered system's name over the layer's own body, one look and
  one draw for both - and `CellTooltipRows` the four lines a body may be written in, so two layers'
  boxes differ only in what they say.
- **`base/refresh`** - what says a cached overlay has gone stale, and the throttled poll that
  finds the changes the engine announces to nobody. `MapLayerSectorWatcher` owns the loop
  alone and asks a `MapLayerStalenessSource` what moved since it last asked, so which changes
  count, and which signal each one raises, stay the layer's answer - a signal being a
  `MapLayerRefreshSignal`, declared by the framework or by the layer that alone means anything by
  it. The signals themselves, who declares which, and the four rebuild paths they drive are
  [the caching notes](../../../../../docs/dev/caching.md).
- **[The sidebar](base/sidebar/README.md)** - the control box: the per-screen hosts, placement,
  fold persistence, and how it is drawn over and routed ahead of the vanilla screens.
- **[Political map](politicalmap/README.md)** - the one layer that paints, its three views, and the
  draw pipeline behind them.
- **`MapLayers`** - the composition root, the single place every concrete layer and political-map
  view is named and registered, so the framework below stays ignorant of which ones exist.
