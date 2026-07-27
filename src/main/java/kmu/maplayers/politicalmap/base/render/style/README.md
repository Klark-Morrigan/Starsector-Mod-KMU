# Render style layer (`render.style`)

The single home for *how the political map is styled* - what turns a player's LunaLib choices
into the concrete colours, widths, and line patterns each territory draws in. Everything here is
read once per map rebuild and baked into the flat draw packets, so the renderer downstream stays
a pure GL loop with no knowledge of settings.

Part of Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../README.md) for project context.

## Index

- [The theme: player choices, tiered](#the-theme-player-choices-tiered)
- [The resolvers: choices into colours](#the-resolvers-choices-into-colours)
- [What is not here](#what-is-not-here)

## The theme: player choices, tiered

A `RenderStyle` is the whole theme, in two tiers:

- `GlobalStyle` - sector-wide, identical for every territory: the contested-fill `HatchStyle`,
  the national-border `BorderSmoothingStyle`, and the desaturation profile.
- `Map<MapCategory, CategoryStyle>` - one style per category (faction, independent, decivilised,
  uninhabited). Each holds its three drawn elements - fill, outer border, inner seam - as an
  `ElementStyle` (a palette choice paired with the opacity it paints at), plus the width each
  border strokes at.

`RenderStyleReader` is the ONE seam that reads the theme, almost all of it out of LunaLib. A new
sector-wide knob is added to the matching `GlobalStyle` sub-record and read there - never fetched ad
hoc in a builder. The single exception is whether the uninhabited outline draws at all: that is the
overlay sidebar's checkbox (`UninhabitedOutlinePreference`, per-save sector memory), because a
LunaLib field would duplicate that control on the settings screen. Its opacity and width stay
LunaLib knobs.

## The resolvers: choices into colours

Pure rules that both the fills and the cluster-name labels read, so a name can never drift from
the space it labels:

- `MapPalettes` - resolves a style choice plus a bloc's recede into concrete shades: which
  palette, which slot, and what a desaturated bloc recolours to.
- `BlocStyleResolver` - resolves the shared per-bloc decision (independent-vs-faction style and
  the adjustment a bloc draws under) into a `BlocStyleDecision`.

## What is not here

*Muting and desaturation* are a separate, dynamic axis: a per-bloc `BlocStyleAdjustment` applied
on top of the resolved style. This layer owns the desaturation *mechanism* (the palette swap in
`MapPalettes`); the *policy* of which bloc recedes and by how much lives one package up in
`politicalmap.base` (`RecedePreferences` and the views). The cascade that folds theme and
adjustment together, and bakes the result into the draw packets, is
[`render.territories`](../territories/README.md)`.TerritoryBuilder`.
