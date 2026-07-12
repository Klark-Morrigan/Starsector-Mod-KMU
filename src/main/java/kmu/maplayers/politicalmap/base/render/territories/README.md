# Territory fills and borders (`render.territories`)

The production draw of the political map: each faction's coloured region, its national border,
and the per-cell province seams and factionless outlines. This is the base layer the labels
overlay sits over - what the player reads as "who holds what".

Part of Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../README.md) for project context.

## Index

- [Building: cells into territories](#building-cells-into-territories)
- [The draw packets](#the-draw-packets)
- [Rendering](#rendering)
- [What is not here](#what-is-not-here)

## Building: cells into territories

`TerritoryBuilder` turns the cached cells and the current ownership into the draw packets: it
resolves who holds each system, shapes the cells into merged clusters, traces one national border
per cluster, and bakes each element's colours, opacities, and widths by cascading the
`render.style` theme with each bloc's recede adjustment. `BorderSmoothing` sands spikes and rounds
corners of those borders; `VertexRuns` flattens shaped cells into GL vertex runs. The per-cell and
per-faction build steps are the shared primitives an incremental re-shape reuses, so a full
rebuild and a single-system refresh style a cell identically.

## The draw packets

`PoliticalMapTerritories` is the built state a rebuild produces and an incremental refresh edits in
place: the two draw lists (`FactionTerritory` per owned faction, `StyledCell` per cell) plus the
retained ownership, theme, and filter inputs a re-shape needs. `FactionTerritory` carries one
faction's fill triangles, contested-hatch segments, and border loops; `StyledCell` carries one
cell's fill, outline, and interior seams.

## Rendering

`TerritoryRenderer` is a pure GL loop over an already-baked `PoliticalMapTerritories`: it scales
world coordinates into map space and strokes/fills the flattened runs, with no knowledge of
settings or how the runs were shaped.

## What is not here

The *theme and the styling resolvers* (what colour/width each category and bloc draws in) live in
`render.style`; this package consumes them, it does not decide them. The *faction-name overlay*
that sits on top is `render.labels`. The border-ring trace shared with the label anchor search
(`render.PoliticalBorderTrace`) stays at the `render` root because more than one concern uses it;
the low-level GL run emission is a generic helper in KMLib (`kmlib.opengl.GlRuns`). The
*incremental refresh* that folds per-system ownership changes into the packets is
`render.IncrementalPoliticsRefresh`, at the render root alongside the plugin and the per-frame
cache that drives it - the composition root that wires these feature packages together.
