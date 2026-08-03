# The theme records (`base.theme`)

The player's authored choices, as value types: what the player picked, held in the shape the rest
of the map reads it in - plus `ElementStyleAdjustment`, the one value here a rebuild decides rather
than the player, laid over a style to push an element into the background. Everything here is a
record apart from three interfaces. Two are places a
layer's own vocabulary plugs in rather than types this package populates: `MapStyleCategory`, the
key the per-category bundles are held under, and `ElementPaintSelection`, the colour pick an
element carries. The third, `HatchStroke`, is sealed over this package's own records - see
[the two tiers](#the-two-tiers). What little behaviour the records carry answers only from their
own components - `ElementStyle.isDrawn`, `RenderStyle.categoryStyle`, `ElementStyleAdjustment`'s
union and mute, and the per-layer width and alpha `HoverGlowStyle` derives from its stack and
pulse. None of it reads a setting, resolves a colour, or touches geometry.

A leaf of the framework rather than of any one layer: the *tiers* describe how a map is styled -
sector-wide knobs plus one bundle per category - and say nothing about what a bundle is painting.

The *categories* are where a layer's own vocabulary meets those tiers, and they are not declared
here. `MapStyleCategory` is the open key - memberless, since a category is only ever looked up -
and each layer declares its own constants of it beside the code that paints them; the political
map's four are `PoliticalMapCategory` in
[`politicalmap.base.render.style`](../../politicalmap/base/render/style/README.md). `RenderStyle`
keys on the interface, so a layer whose ground divides some other way brings its own set rather
than inheriting a vocabulary of who holds the ground.

Part of [the map-layer framework](../../README.md); see the
[mod README](../../../../../../../README.md) for project context.

## Index

- [The two tiers](#the-two-tiers)
- [The element unit](#the-element-unit)
- [The adjustment](#the-adjustment)
- [What is not here](#what-is-not-here)

## The two tiers

A `RenderStyle` is the whole theme, in two tiers:

- `GlobalStyle` - sector-wide, identical for every cluster: the hatched-fill `HatchStyle`, the
  cluster-border `BorderSmoothingStyle` (a `SpikeSandingStyle` and a `CornerRoundingStyle`, each
  carrying its own pass's gate and the shape that pass works to), the `HoverHighlightStyle`
  (itself a `HoverGlowStyle` for the frontier halo and a `HoverWashStyle` for the hovered cell),
  and the desaturation profile.
- `Map<MapStyleCategory, CategoryStyle>` - one bundle per category. Keying on a type rather than
  holding one hardcoded field per category is what lets the theme carry the bundles as one map the
  builders index, and lets the set of categories be the painting layer's rather than the
  framework's. A plain hash map: it is filled once per rebuild, and an `EnumMap` would need the
  key to be one layer's enum.

A new sector-wide knob belongs on the matching `GlobalStyle` sub-record, never fetched ad hoc at a
call site - and where that sub-record is itself split by pass, on the half the pass that reads the
knob is handed. Splitting `BorderSmoothingStyle` that way is what lets each smoothing pass take
only its own half, so a sanding number cannot reach the rounding pass and back again.

`HatchStyle` splits along the same line, by *when* each part is decided rather than by which pass
reads it: `spacing`, `angleRadians` and `joinToleranceFraction` shape the clipped line geometry and
are baked into the drawables, while `stroke` is read per frame at the emit. The stroke is the one
sealed interface here - `GlLineHatchStroke` today - because how a hatch reaches the screen decides
which
numbers it needs at all, and a flat record carrying every substrate's fields would leave
combinations nothing can draw representable. The renderer dispatches on which arrived and reads
only what that one carries.

## The element unit

`ElementStyle` is the unit every drawn element shares - a fill, a border, a seam, a cluster name:
an `ElementPaintSelection` paired with the opacity it paints at. The pair travels as one value
rather than as two parallel components each bundle has to spell out and each reader has to keep in
step.

Widths stay outside it, on `CategoryStyle` - only the two borders have one.

The selection is the player's *unresolved* pick, which is the whole of what the noun buys: it is
resolved against a cluster's actual shades late, at draw time, because one bundle serves many
clusters. That resolution is not here; see below.

## The adjustment

`ElementStyleAdjustment` is the second axis on the same unit: an opacity multiplier and a
desaturate flag, applied on top of whatever the style says so the draw code can push an element
into the background without knowing why it was asked to. It is a separate record rather than more
components on `ElementStyle` because the two have different lifetimes - a style is what the player
authored and outlives a rebuild, an adjustment is what one rebuild decided about one subject.

`NONE` is the identity, and `mergeRecede` is the union: the strongest mute and the OR of
desaturate. Combining multiplicatively would over-dim a subject every extra reason applies, so the
union is what keeps a subject receding for two reasons dimming once, and what makes folding a
reason in twice a no-op. That rule is here rather than at each site precisely because it is easy to
get subtly wrong and there is no reason for two callers to answer it differently.

*Why* a subject recedes stays with whoever has the reason - this tier owns only the shape and the
combination rule.

## What is not here

Nothing here reads a setting, resolves a colour, or names a category. *The categories themselves*
are `PoliticalMapCategory`, *populating* these records from LunaLib is `RenderStyleReader`, and
*turning a selection into a concrete shade* is `MapPalettes`, all three in the political map's own
[`render.style`](../../politicalmap/base/render/style/README.md). All three stay there because all
three name factions: the four categories are a division of the ground by who holds it, the
reader's knobs and its per-save uninhabited-outline preference are the political map's, and the
palettes resolve a bloc's recede. A second layer populating these records would bring its own
categories and its own reader rather than share those.

This package has no dependency back on any of the three, which is what keeps it a leaf: a record
here can be read by anything without dragging one layer's categories or the settings layer in
behind it.
