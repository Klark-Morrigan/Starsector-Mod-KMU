# The theme records (`base.style`)

The player's authored choices, as value types. Everything here is a record (plus one enum) with no
behaviour beyond `ElementStyle.isDrawn` - what the player picked, held in the shape the rest of the
map reads it in.

A leaf of the framework rather than of any one layer: the *tiers* describe how a map is styled -
sector-wide knobs plus one bundle per category - and say nothing about what a bundle is painting.

The *categories* are the exception, and the one place this package is not yet general.
`MapCategory` enumerates the political map's four, so a layer whose ground divides some other way
has no key to hang its own bundles off. Making the key an interface the layer supplies constants
for is what would finish the job; until then a second layer reuses the tiers and inherits these
four names.

Part of [the map-layer framework](../../README.md); see the
[mod README](../../../../../../../README.md) for project context.

## Index

- [The two tiers](#the-two-tiers)
- [The element unit](#the-element-unit)
- [The factionless degenerate case](#the-factionless-degenerate-case)
- [What is not here](#what-is-not-here)

## The two tiers

A `RenderStyle` is the whole theme, in two tiers:

- `GlobalStyle` - sector-wide, identical for every territory: the contested-fill `HatchStyle`, the
  national-border `BorderSmoothingStyle`, the `HoverHighlightStyle` (itself a `HoverGlowStyle` for
  the frontier halo and a `HoverWashStyle` for the hovered cell), and the desaturation profile.
- `Map<MapCategory, CategoryStyle>` - one bundle per category. `MapCategory` is a type rather than
  four hardcoded fields, which is what lets the theme carry the four bundles as one keyed map the
  builders index.

A new sector-wide knob belongs on the matching `GlobalStyle` sub-record, never fetched ad hoc at a
call site.

## The element unit

`ElementStyle` is the unit every drawn element shares - a fill, a border, a seam, a cluster name:
a palette choice paired with the opacity it paints at. The pair travels as one value rather than
as two parallel components each bundle has to spell out and each reader has to keep in step.

Widths stay outside it, on `CategoryStyle` - only the two borders have one.

The palette choice is resolved against a cluster's actual shades late, at draw time, because one
bundle serves many clusters. That resolution is not here; see below.

## The factionless degenerate case

Decivilised and uninhabited ground has no owner, so there is no faction palette to choose a shade
from. Both categories paint in the shared neutral colour and expose no colour field at all,
leaving each element's opacity as its only on/off. Decivilised ground draws a fill and an outline
(a dead colony is settled ground, so it reads as occupied rather than as a bare ring); uninhabited
ground draws an outline alone, since filling it would wash every corner of the sector nothing else
holds. Neither has an inner seam: factionless cells never fuse into clusters, so there are no
province divisions to stroke.

## What is not here

These records are inert - nothing here reads a setting or resolves a colour. *Populating* them
from LunaLib is `RenderStyleReader`, and *turning a choice into a concrete shade* is `MapPalettes`,
both in the political map's own
[`render.style`](../../politicalmap/base/render/style/README.md). Both stay there because both
name factions: the reader's knobs and its per-save uninhabited-outline preference are the political
map's, and the palettes resolve a bloc's recede. A second layer populating these records would
bring its own reader rather than share that one.

This package has no dependency back on either, which is what keeps it a leaf: a record here can be
read by anything without dragging the settings layer in behind it.
