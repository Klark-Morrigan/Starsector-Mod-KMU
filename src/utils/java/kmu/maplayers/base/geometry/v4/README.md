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
- [The channel](#the-channel)
- [What the resolution decides](#what-the-resolution-decides)

## The pipeline

| Step | Class | In | Out |
| --- | --- | --- | --- |
| the cells' own frontier | `BareVoid` | each cell's adjacency-tagged edges | the lines between cell and void |
| cut lines at crossings | `SegmentCrossings` | lines | lines meeting only at their ends |
| weld and order | `PlanarArrangement` | those lines | a graph knowing the turn order at each vertex |
| close the faces | `FaceWalk` | that graph | every piece, each labelled per edge |
| read the shore | `LandableFrontage` | a piece | its runs of border, by cell |
| cut the channel | `PieceShaper` | a piece, an `EdgeInset` | its rings pulled off what they face, crossings and all |
| make it drawable | `PieceRegions` | those rings | bodies and holes, resolved and smoothed |

The last two are drawing rather than geometry, and the partition does not change under them:
the shaping is paint over pieces that already exist, and under `EdgeInsetRule.NOWHERE` what it hands back is the piece itself.
They are in the table because leaving them out is what made the inset look finished when it was not - see [the channel](#the-channel).

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

## The channel

The pieces above are the true division, meeting along shared lines with nothing between them.
The channel is paint over that: each edge pulled off whatever lies across it, by `EdgeInset` - which edges, and how deep.
Only the frame is nothing, being the edge of the sector rather than the edge of anything, so a piece running up to it has nothing to stand off from.

**The inset alone is not drawable**, and this is the part that looks finished and is not.
A piece of void is all necks, and a miter of anything that pinches to a neck crosses itself there;
on the shipped fixtures roughly a quarter of the rings come out crossed.
A crossed ring fills to something other than its outline and strokes a line through its own interior - visible only once it is painted the way the window paints it, translucent under an opaque stroke.

So a piece goes through what a cluster border goes through after its own inset, by the same passes and the same profile:
resolve to the positive-winding envelope, smooth, resolve again.
One profile over the cells and the void beside them is what makes the two sides of a channel round alike.

**A piece narrower than two channels is gone, not thin.**
The miter does not shrink such a piece; it folds the ring over, and the folded ring winds the wrong way.
The resolve will not catch that - handed a lone ring it takes that ring's winding for the plane's and hands it back as a fill - so the fold is caught while the raw ring is still there to compare against, by `PolygonOffsets.hasInsetCollapsed`.

## What the resolution decides

`boundSegments` decides how smoothly a cell's arc is drawn.
It does **not** decide which corners the cells have -
KMLib lays the corner two neighbours share from the two sites and the reach, and puts back any its seed cut away.

What it does set is the resolution everything here is judged at,
which is `SectorGeometryParameters.measureBoundSagitta()`:
the welding tolerance the walk closes rings at, and the floor below which a piece is too small to be a piece.
Read it rather than restating it - a pass holding a number that was right at one setting goes on judging at a resolution the map no longer has.
