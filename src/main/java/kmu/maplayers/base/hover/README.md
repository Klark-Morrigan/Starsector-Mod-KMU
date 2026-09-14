# Hover (`maplayers/base/hover`)

What the cursor is over,
and what the map says back.
Whether anything is drawn over the map where the cursor rests -
which a layer asks before resolving a hover at all -
is [the cover package](cover/README.md)'s.

Part of [the map layers framework](../../README.md).

## Index

- [The values](#the-values)
- [The publishing pass](#the-publishing-pass)
- [Parks, and the moment of arrival](#parks-and-the-moment-of-arrival)
- [Which frames may answer the cursor](#which-frames-may-answer-the-cursor)
- [What lights up](#what-lights-up)
- [The gates, and the per-mod mode](#the-gates-and-the-per-mod-mode)

## The values

`MapHover` is the hovered cell and the cluster around it.
`MapHoverState` is the holder the map render pass publishes to and the later UI passes read,
since only that pass can invert a cursor pixel to a world point -
one per sector,
held by [that sector's machinery](../machinery/README.md),
a hover naming its system by `SystemKey`,
and resolved off the running sector by the passes vanilla drives without naming one.
`HoverHighlight` is the loops and triangles one highlight lights up,
whether that is one cell under the cursor or a whole set lit at once.

## The publishing pass

`MapHoverPublisher` is that pass:
it takes the world point KMLib's `MapCursor` resolves,
hit-tests it,
and widens the hit to its cluster -
all over `MapHoverTargets`,
one frame's drawn cell shapes and the clusters they fuse into.
What it owns is the sequencing and the parking:
a cursor that cannot be trusted must clear the hover rather than leave the last frame's standing,
and getting that wrong lights a cell the cursor is not on.
The pixel-to-world inversion underneath is KMLib's.

## Parks, and the moment of arrival

One park is not the pass's to write,
and `MapHoverExpirer` is it:
every guard above lives *inside* a pass,
so the frames with no map pass at all park nothing,
and the last cell any map resolved would be named for the rest of the session by a tooltip that draws from a campaign-wide listener rather than from the map.
That script closes each frame's window,
so a hover lasts exactly as long as some pass keeps publishing it -
stated over publication alone,
since a second reading of which screens may resolve a hover could only come to disagree with the passes.

It also answers the *moment* the cursor reaches a cell,
over KMLib's own `KeyedHoverArrival`:
one latch for the tick the player hears and the line the trace prints,
since both ask the same question
and two would be two chances to disagree about when the cursor got somewhere.
The latch is stepped only by a hit-test that actually ran:
a frame with no draw lists or no readable transform clears the hover like any other park
but leaves the last cell the cursor was *seen* on standing,
since a missing input says nothing about where the cursor went -
a park is a claim about the hover,
and only sometimes a claim about the cursor.

What that tick sounds like is `MapHoverCues` beside it -
the map's own sample and the player's own level,
read live like the switches are,
and no cue at all once that level reaches the bottom of its slider.
The cell rather than the cluster is what the tick is keyed by,
so it answers the same change the hover box does.

## Which frames may answer the cursor

A pass the cursor cannot be located against at all parks the same way
and is checked before any of it:
a map another mod built drives the same hook with its own position and zoom,
and unprojecting against that yields a confident wrong answer rather than a missing one,
which no guard downstream would catch.
Whether to allow it is the `Map - Compatibility` pair of hover permissions,
which between them add the frames the vanilla hosts do not cover:
one admits every pass there is and is off by default,
since granted it is heard where no map is drawn at all;
the other admits only those where the player is looking at the campaign world itself -
no screen open and no dialog up,
which is KMLib's `CampaignScreenView` to answer -
and is on by default,
being inert without a mod that docks a map surface there.
Stated as permissions rather than as one restriction so the tab reads as a set;
the narrower of the two is also what closes such a surface again without naming a mod,
since a mod parks its panel on exactly the conditions that end game space.

`MapHoverPermission` is that rule bound to the two live screen reads,
and is what a running game holds:
the hover pass and the tooltip box both take their answer from it,
so a permission the player grants reaches the lit cell
and the box naming it together rather than one without the other.
It is what both of them hold,
rather than a boolean each composes for itself,
so that sharing is the compiler's to keep.

## What lights up

`HoverHighlightGeometry` resolves what lights up and `HoverHighlightColour` the shade it lights up in,
both over a `HoverHighlightSource` -
the two questions only the layer that owns the clusters can answer:
the loops the hovered cell might sit inside,
and the shade its fill draws in.
`HoverHighlightRenderer` burns the halo and the wash over a resolved pair,
which is what lets a caller that lights a whole set of cells in one owner's own colour reuse the pass whole rather than re-deriving it;
the cursor's own entry there is the one part that composes the two resolves.

`PreviewHighlightGeometry` is that caller's half:
a set of cells resolved elsewhere,
lit over the map already painting without a rebuild,
a refilter,
a re-clustering or a border re-trace.
It joins its cells only where the map joins them -
grouped by which frontier encloses each,
so one cluster's lit cells wash as one shape
while two in rival clusters keep the seam the map draws -
and its halo traces those joined outlines rather than any cluster frontier,
since what a lit set says is the reach of the set.
Both geometries settle "which cluster is this cell in"
and "this cell clamped to that cluster's frontier" through the one `CellFrontierGeometry`,
so a cell reads the same under the cursor as it does inside a lit set.
Both seams extend `PaintedCellShapes`,
the frame's cell shapes themselves,
so the halo can only trace an outline the cursor was actually hit-tested against -
one supplier,
not two that must agree.
Neither geometry smooths anything of its own:
a shape arrives as the ring its cell put ink on
(`base.render.clusters`'s `PaintedCell`, which is what a layer records),
so a cell whose corners its layer rounded is already handed rounded,
and the clip only borrows the rounding baked into the frontier it clamps to.

## The gates, and the per-mod mode

`MapHoverGates` is the settings side:
hovering is switched at three tiers -
a master over the whole map,
a pair under it for the effects and the box separately,
and a pair of the layer's own -
and this answers for the two that reach every layer,
which a layer ANDs its own into.
So one layer's box can go dark while another's stays up,
and one row still silences them all.

`RandomAssortmentOfThingsCompatibilityMode` is the same tab's per-mod switch:
whether the player has left that mod's compatibility mode on *and* the mod is installed with its own minimap replacing the campaign radar,
which is KMLib's `CampaignMinimap` role to answer -
the map surface `MapPresence` cannot report,
since it stands in for the radar rather than opening as a screen,
and answered for that mod in KMLib's own `rat` package.
Both halves,
ANDed,
so an install without that minimap reads one boolean and behaves as it always did.
Named for the mod because the switch is,
while what it asks stays the mod-neutral question the role carries -
so a second mod replacing the radar arrives as its own switch
rather than folded under this one's name.
It narrows what the general switches beside it allow and never widens it:
which frames may answer the cursor at all is theirs,
and the mode only confines -
within a frame they already allow -
to the surface that minimap occupies.
Those are written for the mods nobody here has met,
and a per-mod mode able to override them would make them unreliable as general switches.
The one thing it does outside that hierarchy is not about the cursor at all:
while the minimap is parked off screen it is [switched off](../render/README.md#silencing-a-minimap-parked-off-screen),
a surface nobody can see having no business rendering a sector map behind every screen the player opens.
