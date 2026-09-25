# The theme records (`base.theme`)

The player's authored choices,
as value types:
what the player picked,
held in the shape the rest of the map reads it in -
plus `ElementStyleAdjustment`,
the one value here a rebuild decides rather than the player,
laid over a style to push an element into the background.
Everything here is a record apart from three interfaces.
Two are places a layer's own vocabulary plugs in rather than types this package populates:
`MapStyleCategory`,
the key the per-category bundles are held under,
and `ElementPaintSelection`,
the colour pick an element carries.
The third,
`HatchStroke`,
is sealed over this package's own records -
see [the two tiers](#the-two-tiers).
What little behaviour the records carry answers only from their own components -
`ElementStyle.isDrawn`,
`RenderStyle.categoryStyle`,
`ElementStyleAdjustment`'s union and mute,
and the per-layer width and alpha `HoverGlowStyle` derives from its stack and pulse.
None of it reads a setting,
resolves a colour,
or touches geometry.

A leaf of the framework rather than of any one layer:
the *tiers* describe how a map is styled -
sector-wide knobs plus one bundle per category -
and say nothing about what a bundle is painting.

The *categories* are where a layer's own vocabulary meets those tiers,
and they are not declared here.
`MapStyleCategory` is the open key -
memberless,
since a category is only ever looked up -
and each layer declares its own constants of it beside the code that paints them;
the owner-painted tier's four are `OwnerMapCategory` in [`ownermap.render.style`](../../ownermap/render/style/README.md).
`RenderStyle` keys on the interface,
so a layer whose cells divide some other way brings its own set
rather than inheriting one tier's vocabulary of owners.

Part of [the map-layer framework](../../README.md);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [The two tiers](#the-two-tiers)
- [The element unit](#the-element-unit)
- [The adjustment](#the-adjustment)
- [What is not here](#what-is-not-here)

## The two tiers

A `RenderStyle` is the whole theme,
in two tiers:

- `GlobalStyle` -
  sector-wide,
  identical for every cluster:
  the hatched-fill `HatchStyle`,
  the cluster-border `BorderSmoothingStyle`
  (a `SpikeSandingStyle` and a `CornerRoundingStyle`, each carrying its own pass's gate and the shape that pass works to),
  two `HoverHighlightStyle` tiers
  (each a `HoverGlowStyle` for the halo and a `HoverWashStyle` for the cells inside it) -
  one for the cursor's answer on a single cell,
  one for a whole set of cells lit at once -
  and the two spotlight strengths -
  how far a receded cluster sinks toward black,
  and how far a cell the spotlight spares lifts toward white.
  Those two are a pair rather than one knob and a counterpart:
  a layer separating its subject from its backdrop in one hue needs both ends to move,
  since sinking the backdrop alone leaves anything it started level with reading as part of it.
- `Map<MapStyleCategory, CategoryStyle>` -
  one bundle per category.
  Keying on a type rather than holding one hardcoded field per category is what lets the theme carry the bundles as one map the builders index,
  and lets the set of categories be the painting layer's rather than the framework's.
  A plain hash map:
  it is filled once per rebuild,
  and an `EnumMap` would need the key to be one layer's enum.

A new sector-wide knob belongs on the matching `GlobalStyle` sub-record,
never fetched ad hoc at a call site -
and where that sub-record is itself split by pass,
on the half the pass that reads the knob is handed.
Splitting `BorderSmoothingStyle` that way is what lets each smoothing pass take only its own half,
so a sanding number cannot reach the rounding pass and back again.

Two of those sub-records are the same type,
the one place the tier carries a record twice:
a highlight is a halo plus a wash whatever resolved the shapes under it,
so the cursor's and the preview's differ only in the weights they are read at -
the preview's halo being `HoverGlowStyle.NO_GLOW`,
a weight of nothing rather than a slot it lacks.
`GlobalStyle` carries why they are two tiers rather than one and a multiplier,
and why only one of them blooms.

`HatchStyle` splits along the same line,
by *when* each part is decided rather than by which pass reads it:
its `pattern` shapes the clipped line geometry and is baked into the drawables,
while its `stroke` is read per frame at the emit.
The pattern is KMLib's own `HatchPattern` -
the whole of what a cut depends on,
and therefore exactly what `Hatching.computeHatchRun` takes -
so the cut is handed the pattern alone and never holds a per-frame number it could come to bake.
The stroke is the one sealed interface here -
`GlLineHatchStroke` today -
because how a hatch reaches the screen decides which numbers it needs at all,
and a flat record carrying every substrate's fields would leave combinations nothing can draw representable.
The renderer dispatches on which arrived and reads only what that one carries.

The split is *when*, not *whose*:
the line stroke's width is a fraction of the pattern's baked spacing,
so the frame that reads it resolves the two together.
Stated in pixels it would be a screen-space number over world-space geometry,
and the share of the gap it inked would change with every zoom -
solid at one end of the range and shard-clipped just short of it.

## The element unit

`ElementStyle` is the unit every drawn element shares -
a fill,
a border,
a seam,
a cluster name:
an `ElementPaintSelection` paired with the opacity it paints at.
The pair travels as one value rather than as two parallel components each bundle has to spell out
and each reader has to keep in step.

Widths stay outside it,
on `CategoryStyle` -
only the two borders have one.

The selection is the player's *unresolved* pick,
which is the whole of what the noun buys:
it is resolved against a cluster's actual shades late,
at draw time,
because one bundle serves many clusters.
That resolution is not here;
see below.

## The adjustment

`ElementStyleAdjustment` is the second axis on the same unit:
an opacity multiplier and a desaturate flag,
applied on top of whatever the style says
so the draw code can push an element into the background without knowing why it was asked to.
It is a separate record rather than more components on `ElementStyle`
because the two have different lifetimes -
a style is what the player authored and outlives a rebuild,
an adjustment is what one rebuild decided about one subject.

`NONE` is the identity,
and `mergeRecede` is the union:
the strongest mute and the OR of desaturate.
Combining multiplicatively would over-dim a subject every extra reason applies,
so the union is what keeps a subject receding for two reasons dimming once,
and what makes folding a reason in twice a no-op.
That rule is here rather than at each site precisely because it is easy to get subtly wrong
and there is no reason for two callers to answer it differently.

*Why* a subject recedes stays with whoever has the reason -
this tier owns only the shape and the combination rule.

## What is not here

Nothing here reads a setting,
resolves a colour,
or names a category.
*The categories themselves* are `OwnerMapCategory`,
*populating* these records from LunaLib is `RenderStyleReader`,
and *turning a selection into a concrete shade* is `MapPalettes`,
all three in [`ownermap.render.style`](../../ownermap/render/style/README.md).
All three stay out of this package because all three belong to one way of painting,
which is by owner:
the four categories are a division of the cells by their owner,
the reader's knobs and its per-save uninhabited-outline preference are the owner-painted tier's,
and the palettes resolve a bloc's recede.
They sit in `ownermap` rather than in any one layer for the same reason,
since every owner-painted layer divides and shades its cells that way.
A layer that paints by something other than an owner would bring its own categories
and its own reader rather than share those.

This package has no dependency back on any of the three,
which is what keeps it a leaf:
a record here can be read by anything without dragging one layer's categories or the settings layer in behind it.
