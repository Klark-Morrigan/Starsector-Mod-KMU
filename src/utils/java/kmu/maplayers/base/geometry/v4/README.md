# Void pockets, v4

Part of the [geometry viewer](../../../../../../../../README.md)'s prototype tree.

The sector's void as one division rather than as a set of separately traced shapes.
Where v3 walks the void once per kind of thing it is looking for and reconciles the answers afterwards,
this lays every line into a single walk and reads the pieces off it.
Two pieces either side of a line share that line exactly, because nothing was measured twice.

Nothing here ships.
It runs under `gradlew viewSectorGeometry` and under the geometry suites, beside v3, so the two can be compared on screen.

## Index

- [The pipeline](#the-pipeline)
- [Why the cells are the input](#why-the-cells-are-the-input)
- [What a piece is](#what-a-piece-is)
- [The open sea](#the-open-sea)
- [Frontage](#frontage)
- [What the resolution decides](#what-the-resolution-decides)

## The pipeline

| Step | Class | In | Out |
| --- | --- | --- | --- |
| the cells' own frontier | `BareVoid` | each cell's adjacency-tagged edges | the lines between cell and void |
| cut lines at crossings | `SegmentCrossings` | lines | lines meeting only at their ends |
| weld and order | `PlanarArrangement` | those lines | a graph knowing the turn order at each vertex |
| close the faces | `FaceWalk` | that graph | every piece, each labelled per edge |
| read the shore | `LandableFrontage` | a piece | its runs of border, by cell |

`LabelledRing` and `LabelledWall` are what goes into the walk - a ring or a loose line, each edge carrying an int naming what it lies on.
`Face` is what comes out.

## Why the cells are the input

Every cell edge already says what lies across it, and `EdgeTarget.REACH_BOUND` says nothing does:
the cell stopped at its own reach rather than meeting a neighbour.
Those edges and no others are the line between cell and void,
so they are the whole input and no second construction has to rediscover them.

The disc sweep that v3 walks is **not** in this path.
It was, and it was a detour:
it rediscovers by arithmetic a line the cells already carry, and it reports only the enclosed pockets - never the open sea, which is most of the void.

## What a piece is

A `Face` is the ring around one piece, what each of its edges lies on, and the rings of anything cut out of it.

The labels are the part that makes a piece readable rather than merely drawable.
An edge names the cell whose border it runs along, or `BareVoid.THE_FRAME` for the edge of the sector.
That number is deliberately not the one KMLib's `VoronoiCellBuilder.BOUND_EDGE` uses:
both reach a piece's labels and they say opposite things, so sharing a value would let a reader take the edge of the map for somebody's shore.

Bounded or outer is read off the winding rather than carried beside it,
so nothing can label a piece one way and wind it the other.

## The open sea

The sea is a piece like any other, and by far the largest.
It is bounded on the inside by the silhouette round each group of cells and on the outside by a frame laid round the whole sector.

Without that frame the sea is not bounded at all, so it is never closed and never a piece -
and then the pieces do not partition the void, which is the property the whole construction rests on.
The check is that the pieces and the cells together cover the frame exactly once.

A piece that runs around another carries it as a hole.
Drawn from its outline alone, the sea paints over every cell on the map.

## Frontage

Frontage is every stretch of cell border facing void nothing has captured: where anything can land.

That is the whole of it.
There is no measurement, because the question of HOW something lands - how far, from where, past what - belongs to whatever lays it, and each layer answers it for itself.
It shrinks as the layers go down:
water a coastline closes off is captured, and a border facing captured water faces nothing anything can still arrive from.

A run carries both ends of every edge it covers, so consecutive runs share the corner where one cell gives way to the next.
Carrying only each edge's start leaves a notch at every junction.

## What the resolution decides

`boundSegments` decides how smoothly a cell's arc is drawn.
It does **not** decide which corners the cells have -
KMLib lays the corner two neighbours share from the two sites and the reach, and puts back any its seed cut away.

What it does set is the resolution everything here is judged at,
which is `SectorGeometryParameters.measureBoundSagitta()`:
the welding tolerance the walk closes rings at, and the floor below which a piece is too small to be a piece.
Read it rather than restating it - a pass holding a number that was right at one setting goes on judging at a resolution the map no longer has.
