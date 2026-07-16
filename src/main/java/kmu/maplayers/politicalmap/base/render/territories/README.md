# Territory fills and borders (`render.territories`)

The production draw of the political map: each faction's coloured region, its national border,
and the per-cell province seams and factionless outlines. This is the base layer the labels
overlay sits over - what the player reads as "who holds what".

Part of Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../README.md) for project context.

## Index

- [Building: cells into territories](#building-cells-into-territories)
- [Borders against empty space](#borders-against-empty-space)
- [The draw packets](#the-draw-packets)
- [Spotlit footprint: the split fill](#spotlit-footprint-the-split-fill)
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

## Borders against empty space

Every border edge is shaped the same way, whatever sits across it. Against another
organised entity (a rival faction or an alliance) and against *empty* space alike -
an uncontrolled star with no owner, whether never-settled or decivilised - the edge
keeps the mutual midline plus its inward border channel. So a faction cuts off
halfway to a dead star exactly as it does halfway to a rival, and the dead star's
own cell draws its inset outline in the neutral style on the far side of that
channel.

The two player settings under Territory reach on the **Political map - visuals**
tab - *Uncontrolled systems give way to faction territory* and *Frontier keep-out* -
describe an asymmetric treatment of empty space that the draw does not yet apply, so
changing them does not move a border.

## The draw packets

`PoliticalMapTerritories` is the built state a rebuild produces and an incremental refresh edits in
place: the two draw lists (`FactionTerritory` per owned faction, `StyledCell` per cell) plus the
retained ownership, theme, and filter inputs a re-shape needs. `FactionTerritory` carries one
faction's fill triangles, contested-hatch segments, and border loops; `StyledCell` carries one
cell's fill, outline, and interior seams.

## Spotlit footprint: the split fill

When the filter spotlights one bloc, its whole footprint - the systems it dominates plus the ones
it merely contests - clusters into a single `FactionTerritory` under one national frontier, and the
fill splits in two: solid where the bloc dominates, a pre-clipped diagonal hatch where it is only
present ("mine, but contested").

Each of those two states fills from its own traced rings, not from its members' individual cells.
The footprint's one grouping key is suffixed per state, so the border tracer - which fuses same-key
cells - traces the dominant members as one region and the contested members as another, while every
system outside the footprint keeps its real key and the two regions' outer edge therefore lands
exactly where the national border draws it. Each state names the other's members as *coincident*
neighbours, so the boundary they share insets by nothing and the solid and hatched fills abut on the
raw cell edge. Filling per cell instead would truncate each member's kept edges against its own
inset boundary edges, and two members meeting at a corner against a rival would pull their shared
edge back by different amounts - opening an unfilled wedge on the more-receded side.

The footprint's interior divisions carry no geometry of their own: the whole footprint shares one
grouping key, so a solid/hatch transition is an interior seam like any other and its two cells
already stroke it in the province style. Giving those divisions the frontier's own border style
instead would put a heavy line under the faction's name label, which the label has to stay legible
over, and would need raw cell edges to draw - untrimmed, so they overshoot the inset frontier and
poke out into the border channel. The province seam has neither problem: it is faint, and the cell
shaper truncates it where it runs into a pulled-in border.

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
