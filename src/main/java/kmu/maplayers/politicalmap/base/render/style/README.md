# Render style layer (`render.style`)

The single home for *how the political map is styled* - what turns a player's LunaLib choices
into the concrete colours, widths, and line patterns each territory draws in. Everything here is
read once per map rebuild and baked into the flat draw packets, so the renderer downstream stays
a pure GL loop with no knowledge of settings.

Part of [the political map](../../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../README.md) for project context.

## Index

- [Layout](#layout)
- [The reader: the one seam](#the-reader-the-one-seam)
- [The resolvers: choices into colours](#the-resolvers-choices-into-colours)
- [What is not here](#what-is-not-here)

## Layout

The layer splits along the line between *what the player chose* and *what that means in paint*:

- [`theme`](theme/README.md) - the choices as inert value types (`RenderStyle` and its tiers).
  A leaf: it depends on nothing here, so a record can be read without dragging the settings layer
  in behind it.
- this package - the behaviour. One reader that populates the theme, and the resolvers that turn a
  choice plus a bloc's recede into concrete shades. Plus `PoliticalMapStyle`, the fixed
  border-channel geometry that is deliberately *not* player-tunable, since it decides where fills
  meet rather than how they look.

## The reader: the one seam

`RenderStyleReader` is the ONE place the theme is read, almost all of it out of LunaLib. A new knob
is read here and lands on the matching theme record - never fetched ad hoc in a builder, which is
what lets an incremental re-shape restyle against the same snapshot the full build used.

The single exception is whether the uninhabited outline draws at all: that is the overlay sidebar's
checkbox (`UninhabitedOutlinePreference`, per-save sector memory), because a LunaLib field would
duplicate that control on the settings screen. Its opacity and width stay LunaLib knobs.

## The resolvers: choices into colours

Pure rules that both the fills and the cluster-name labels read, so a name can never drift from
the space it labels:

- `MapPalettes` - resolves a style choice plus a bloc's recede into concrete shades: which
  palette, which slot, and what a desaturated bloc recolours to.
- `BlocStyleResolver` - resolves the shared per-bloc decision (independent-vs-faction style and
  the adjustment a bloc draws under) into a `BlocStyleDecision`.
- `BlocStyling` - maps that decision onto the pass's actual theme, giving the concrete bundle
  plus adjustment a bloc draws under. It owns the one field that crosses between bundles: a
  desaturated bloc's fill is held at the *faction* opacity, so a desaturated surface reads as
  one uniform grey rather than splitting into two weights of empty.

The decision and the mapping are deliberately two steps. `BlocStyleResolver` stays theme-free so
the label path and the fill path resolve the same answer; `BlocStyling` is where a theme is
finally required. Callers ask for the pair through `PoliticalMapTerritories.resolveBlocStyling`,
which composes both off one retained snapshot.

## What is not here

*Muting and desaturation* are a separate, dynamic axis: a per-bloc `BlocStyleAdjustment` applied
on top of the resolved style. This layer owns the desaturation *mechanism* (the palette swap in
`MapPalettes`); the *policy* of which bloc recedes and by how much lives one package up in
`politicalmap.base` (`RecedePreferences` and the views). *Baking* the resolved style into the draw
packets is [`render.territories`](../territories/README.md) - its `StyledCellBuilder` and
`FactionTerritoryBuilder`. What makes the "once per map rebuild" above actually happen - which
settings change is noticed, and how it reaches this layer - is
[the caching notes](../../../../../../../../../docs/dev/caching.md).
