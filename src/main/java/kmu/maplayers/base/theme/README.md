# The theme records (`base.theme`)

The player's authored choices, as value types: what the player picked, held in the shape the rest
of the map reads it in. Everything here is a record (plus the one key interface they are keyed by),
and what little behaviour they carry answers only from their own components -
`ElementStyle.isDrawn`, `RenderStyle.categoryStyle`, and the per-layer width and alpha
`HoverGlowStyle` derives from its stack and pulse. None of it reads a setting, resolves a colour,
or touches geometry.

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
- [What is not here](#what-is-not-here)

## The two tiers

A `RenderStyle` is the whole theme, in two tiers:

- `GlobalStyle` - sector-wide, identical for every territory: the contested-fill `HatchStyle`, the
  national-border `BorderSmoothingStyle` (both smoothing gates and the shape each pass works to),
  the `HoverHighlightStyle` (itself a `HoverGlowStyle` for the frontier halo and a `HoverWashStyle`
  for the hovered cell), and the desaturation profile.
- `Map<MapStyleCategory, CategoryStyle>` - one bundle per category. Keying on a type rather than
  holding one hardcoded field per category is what lets the theme carry the bundles as one map the
  builders index, and lets the set of categories be the painting layer's rather than the
  framework's. A plain hash map: it is filled once per rebuild, and an `EnumMap` would need the
  key to be one layer's enum.

A new sector-wide knob belongs on the matching `GlobalStyle` sub-record, never fetched ad hoc at a
call site.

## The element unit

`ElementStyle` is the unit every drawn element shares - a fill, a border, a seam, a cluster name:
a palette choice paired with the opacity it paints at. The pair travels as one value rather than
as two parallel components each bundle has to spell out and each reader has to keep in step.

Widths stay outside it, on `CategoryStyle` - only the two borders have one.

The palette choice is resolved against a cluster's actual shades late, at draw time, because one
bundle serves many clusters. That resolution is not here; see below.

## What is not here

Nothing here reads a setting, resolves a colour, or names a category. *The categories themselves*
are `PoliticalMapCategory`, *populating* these records from LunaLib is `RenderStyleReader`, and
*turning a choice into a concrete shade* is `MapPalettes`, all three in the political map's own
[`render.style`](../../politicalmap/base/render/style/README.md). All three stay there because all
three name factions: the four categories are a division of the ground by who holds it, the
reader's knobs and its per-save uninhabited-outline preference are the political map's, and the
palettes resolve a bloc's recede. A second layer populating these records would bring its own
categories and its own reader rather than share those.

This package has no dependency back on any of the three, which is what keeps it a leaf: a record
here can be read by anything without dragging one layer's categories or the settings layer in
behind it.
