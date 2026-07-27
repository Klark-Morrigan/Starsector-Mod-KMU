# The theme records (`render.style.theme`)

The player's authored choices, as value types. Everything here is a record (plus one enum) with no
behaviour beyond `ElementStyle.isDrawn` - what the player picked, held in the shape the rest of the
map reads it in.

Part of [the render style layer](../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../../README.md) for project context.

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
both one level up in [`render.style`](../README.md). This package has no dependency back on that
one, which is what keeps it a leaf: a record here can be read by anything without dragging the
settings layer in behind it.
