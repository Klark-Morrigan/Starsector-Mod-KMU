# Territory fills and borders (`render.territories`)

The production draw of the political map: each faction's coloured region, its national border,
and the per-cell province seams and factionless outlines. This is the base layer the labels
overlay sits over - what the player reads as "who holds what".

Part of Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../README.md) for project context.

## Index

- [Building: cells into territories](#building-cells-into-territories)
- [Frontiers against empty space](#frontiers-against-empty-space)
- [The draw packets](#the-draw-packets)
- [Spotlit footprint: split fill and contested borders](#spotlit-footprint-split-fill-and-contested-borders)
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

## Frontiers against empty space

A border edge is shaped by what sits across it. Against another organised entity
(a rival faction or an alliance) the edge keeps the mutual midline plus its inward
border channel, so two territories meet and push back evenly. Against *empty*
space - an uncontrolled star with no owner, whether never-settled or decivilised -
the edge is instead pushed outward past the midline toward that star and capped a
fixed distance short of it, so the faction colour flows around the dead star and
leaves it a pocket rather than cutting off at the halfway line. The owned fill's
push-out and the factionless cell's own pulled-in outline both target that one
keep-out line, so they abut without a gap or overlap.

Two player settings on the **Political map - visuals** tab drive it, both under
Territory reach: *Uncontrolled systems give way to faction territory* toggles the
whole behaviour (off reverts every empty-facing edge to the midline cut), and
*Frontier keep-out* sets how close territory may reach to the star, sizing the
pocket. When two systems sit closer than twice the keep-out radius the push clamps
to the midline, so colonies never overlap and a star squeezed between two rivals
keeps an unclaimed lens between them.

## The draw packets

`PoliticalMapTerritories` is the built state a rebuild produces and an incremental refresh edits in
place: the two draw lists (`FactionTerritory` per owned faction, `StyledCell` per cell) plus the
retained ownership, theme, and filter inputs a re-shape needs. `FactionTerritory` carries one
faction's fill triangles, contested-hatch segments, contested borders, and border loops;
`StyledCell` carries one cell's fill, outline, and interior seams.

## Spotlit footprint: split fill and contested borders

When the filter spotlights one bloc, its whole footprint - the systems it dominates plus the ones
it merely contests - clusters into a single `FactionTerritory` under one national frontier, and the
fill splits per cell: solid where the bloc dominates, a pre-clipped diagonal hatch where it is only
present ("mine, but contested"). Every interior edge that touches a contested cell - the
solid/hatch transitions and the divisions between two hatched cells - is stroked in the frontier's
own border colour and width (the `contestedBorders` run), so each contested cell reads as a bounded
territory instead of dissolving into the hatch. Two dominated cells share no such edge, so they
fuse with only the faint per-cell province seam between them and the solid region stays one nation.

Those contested-touching edges are drawn twice: once as the faint per-cell province seam (the cell
build is contested-agnostic, so it emits every interior edge) and once as the bold contested border
laid over it. The overdraw is deliberate - it keeps the per-cell seam build free of any
contested-awareness at the cost of a few doubled line segments, which is invisible against the rest
of the overlay and never a measurable share of the per-frame draw.

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
