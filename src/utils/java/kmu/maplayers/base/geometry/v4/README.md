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
- [Welded once, before anything is laid](#welded-once-before-anything-is-laid)
- [Frontage](#frontage)
- [The channel](#the-channel)
- [What the resolution decides](#what-the-resolution-decides)

## The pipeline

| Step | Class | In | Out |
| --- | --- | --- | --- |
| the cells' own frontier | `VoidPartition` | each cell's adjacency-tagged edges | the lines between cell and void, and a frame round the sector |
| cut the base at crossings | `SegmentCrossings` | those lines | lines meeting only at their ends |
| weld and order the base | `PlanarArrangement` | those lines, at the sagitta | a graph knowing the turn order at each vertex |
| lay a tier's lines | `LakeTier`, through `CarriedLines` | lines as `CellGap`s, two points and two cells | walls, each end carried through the shore |
| cut them into the base | `SegmentCrossings` | the welded base read back as lines, and the walls | lines meeting only at their ends |
| weld and order again | `PlanarArrangement` | those lines, at rounding | the graph the faces are walked on |
| close the faces | `FaceWalk` | that graph | every piece, each labelled per edge |
| read the shore | `LandableFrontage` | a piece | its runs of border, by cell |
| cut the channel | `PieceShaper` | a piece, an `EdgeInset` | its rings pulled off what they face, crossings and all |
| make it drawable | `PieceRegions` | those rings | bodies and holes, resolved and smoothed |

The last two are drawing rather than geometry, and the partition does not change under them:
the shaping is paint over pieces that already exist, and under `EdgeInsetRule.NOWHERE` what it hands back is the piece itself.
They are in the table because leaving them out is what made the inset look finished when it was not - see [the channel](#the-channel).

With nothing laid, the three rows about laid lines do nothing, and the walk runs on the welded base as it stands.

`LabelledWall` is what goes into the walk - a line carrying an int naming what it lies on; a closed outline goes in as its sides.
`Face` is what comes out, its outline and holes each a `LabelledRing` whose every edge keeps that int.

Nothing here imports v3, and the layering gate holds it to that.
The lake coast is v3's trace and the lake bridges are v3's search,
and both cross as `CellGap`s - the shared package's one value for a straight run between two cells -
handed over by the viewer, which already depends on both.
The coast's reaches are read off the trace by `LakeReaches` in the viewer's own package.
The bridges are v3's search under v3's knobs, bar whether chains and fans are thinned:
that is a rule of the tier laying them and so a switch of v4's own, which v3's laying answers through `layLakeSpans(shouldThinFormations)`.
A version that imported the other would be a layer on top of it rather than a construction beside it,
and the package both versions build on would depend on its own dependents.

Every tier lays its lines through `CarriedLines`, which carries each end through the shore:
what makes a line divide is one fact about the walk, so it is answered once rather than per tier.

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
An edge names the cell whose border it runs along, as that cell's index,
or the line some tier laid, as a negative of that tier's own - the frame, the lake coast and the lake bridges so far.
`EdgeLabels` holds the convention and lists the negatives in use; ask it whether an edge is a cell's rather than testing for the frame, which stopped being the only negative with the first tier.
The frame's number is deliberately not the one KMLib's `VoronoiCellBuilder.BOUND_EDGE` uses:
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

## Welded once, before anything is laid

The frontier reports each shared corner twice, up to a sagitta apart, and the weld at that tolerance is what closes its rings.
A tier's line needs none of that:
its ends are where its tier put them, and the walk cuts it at the shore where its stubs cross.

Welded together with the frontier, the line's ends are pulled up to a sagitta sideways after the cutting has run.
A reach that grazes a cell, which v3's do, passes a polygon corner by less than that,
and the pull swings it across the corner - a crossing the walk cannot turn at, and the whole lake walked out and back as a tree of area nothing.

So `FaceWalk` joins in two stages.
The frontier and the frame are cut and welded on their own and come back as exact lines;
what a tier lays is cut against those and welded at rounding.
Nothing moves after it was cut, which is the invariant the cutting rests on.

## Frontage

Frontage is every stretch of cell border facing void nothing has captured: where anything can land.
A bridge lands nowhere else - and a stretch can be a single point, where a coast only touches a cell between two reaches.

That is the whole of it.
There is no measurement, because the question of HOW something lands - how far, from where, past what - belongs to whatever lays it, and each layer answers it for itself.
It shrinks as the layers go down:
water a coastline closes off is captured, and a border facing captured water faces nothing anything can still arrive from.

TODO: `LandableFrontage` neither shrinks nor offers single points yet.
It reads every piece as open, so a bay behind a reach still counts,
and its runs are edges, so a single point of contact offers nothing.

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
the tolerance the frontier's own corners are welded at, and the floor below which a piece is too small to be a piece.
Laid lines are joined at rounding rather than at it, for the reason [the two-stage weld](#welded-once-before-anything-is-laid) gives,
but the floor is still the sagitta's:
a sliver a laid line closes against the shore thinner than the map is drawn is no more a piece than one the frontier closes on its own,
so the pieces cover the void less exactly those.
Read it rather than restating it - a pass holding a number that was right at one setting goes on judging at a resolution the map no longer has.
