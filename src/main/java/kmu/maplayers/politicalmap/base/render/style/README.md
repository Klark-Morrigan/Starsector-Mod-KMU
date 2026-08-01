# Render style layer (`render.style`)

The single home for *how the political map is styled* - what turns a player's LunaLib choices
into the concrete colours, widths, and line patterns each territory draws in. Everything here is
read once per map rebuild and baked into the flat draw packets, so the renderer downstream stays
a pure GL loop with no knowledge of settings.

Part of [the political map](../../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../README.md) for project context.

## Index

- [Layout](#layout)
- [The categories: how this map divides the ground](#the-categories-how-this-map-divides-the-ground)
- [The selections: which slot an element is pointed at](#the-selections-which-slot-an-element-is-pointed-at)
- [The reader: the one seam](#the-reader-the-one-seam)
- [The resolvers: choices into colours](#the-resolvers-choices-into-colours)
- [What is not here](#what-is-not-here)

## Layout

*What the player chose* and *what that means in paint* are split across two packages:

- [`base.theme`](../../../../base/theme/README.md) - the choices as inert value types
  (`RenderStyle` and its tiers), in the framework rather than here, since a theme's shape is not
  political. This package reads them and never writes back.
- this package - the behaviour, all of it political, plus the two pieces of political *vocabulary*
  the theme holds openly: the set it is keyed by, and the palette slots an element can be pointed
  at. One category set, one selection set, one reader that populates the theme, and the resolvers
  that turn a selection plus a bloc's recede into concrete shades.

## The categories: how this map divides the ground

`PoliticalMapCategory` is the four ways this map divides the sector - `FACTION`, `INDEPENDENT`,
`DECIVILISED`, `UNINHABITED` - and the keys the theme's per-category bundles are held under. It
lives here rather than in `base.theme` because dividing ground by *who holds it* is this layer's
reading of the sector: the theme keys on the open `MapStyleCategory`, so a hazard or trade layer
would declare its own set alongside its own painting code. An enum, so this side's own lookups
stay a closed set the compiler checks.

Two of the four are owned and carry a full fill/border/seam style. The other two have no owner and
so no faction palette to choose a shade from: both paint in the shared neutral colour and expose no
colour field at all, leaving each element's opacity as its only on/off. Decivilised ground draws a
fill and an outline (a dead colony is settled ground, so it reads as occupied rather than as a bare
ring); uninhabited ground draws an outline alone, since filling it would wash every corner of the
sector nothing else holds. Neither has an inner seam: factionless cells never fuse into clusters,
so there are no province divisions to stroke.

The fixed border channel is deliberately *not* here and not tunable at all: it decides where fills
meet rather than how they look, so it lives on the shaping that applies it, as
`base.geometry.CellShaper.BORDER_INSET_DISTANCE`.

## The selections: which slot an element is pointed at

`FactionPaletteSlot` is this map's answer to the theme's open `ElementPaintSelection` - `PRIMARY`
or `SECONDARY`, naming which of the two slots of a bloc's palette an element reads. A selection is
not a colour: it says *where to look*, and stays unresolved until a bloc is in hand, because one
theme serves every bloc on the map. The theme holds one per element and never asks what it means;
it decides only whether an element paints at all, which it reads off the selection being absent.

It is deliberately narrower than `FactionPaletteChoice`, the settings-side wire format, which needs
a third option ("No color") the render side must not be able to hold: a style says "paints nothing"
by carrying no selection, so an explicit no-colour *value* would read as drawable to every caller
that tests for one. `resolvePaintSelectionOf` is the one crossing between the two, mapping NONE -
and an unresolved setting - onto no selection at all.

Turning a selection into the shade it names is `MapPalettes` below - the only place in this
package where the word *shade* means a concrete colour rather than a slot. It is also where a
selection belonging to some other layer's option set resolves to no shade rather than to a wrong
one.

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

- `MapPalettes` - resolves a paint selection plus a recede into concrete shades: which palette,
  which slot, and what a desaturated subject recolours to. Stated over a palette rather than over
  an owner, so ownerless ground recolours by the same rule a bloc does - and ownerless ground gets
  that palette from `resolveNeutralPalette`, the one statement of "no holder means the neutral
  colour in both slots".
- `BlocStyleResolver` - resolves the shared per-bloc decision (independent-vs-faction style and
  the adjustment a bloc draws under) into a `BlocStyleDecision`.
- `FactionlessStyleResolver` - the counterpart for ground with no owner, which has no bloc to carry
  a decision: which factionless category it draws in, and whether the pass's recede reaches it
  (decivilised ground yes, uninhabited ground no).
- `BlocStyling` - maps that decision onto the pass's actual theme, giving the concrete bundle
  plus adjustment a bloc draws under. It owns the one field that crosses between bundles: a
  desaturated bloc's fill is held at the *faction* opacity, so a desaturated surface reads as
  one uniform grey rather than splitting into two weights of empty.

The decision and the mapping are deliberately two steps. `BlocStyleResolver` stays theme-free so
the label path and the fill path resolve the same answer; `BlocStyling` is where a theme is
finally required. Callers ask for the pair through `PoliticalMapTerritories.resolveBlocStyling`,
which composes both off one retained snapshot.

## What is not here

*Muting and desaturation* are a separate, dynamic axis: a `BlocStyleAdjustment` applied on top of
the resolved style, per bloc or per piece of ownerless ground. This layer owns the desaturation
*mechanism* (the palette swap in `MapPalettes`) and the rule for which factionless ground it
reaches (`FactionlessStyleResolver`); the *policy* of which bloc recedes and by how much lives one
package up in `politicalmap.base` (`RecedePreferences` and the views). *Baking* the resolved style into the draw
packets is [`render.territories`](../territories/README.md) - its `StyledCellBuilder` and
`FactionTerritoryBuilder`. What makes the "once per map rebuild" above actually happen - which
settings change is noticed, and how it reaches this layer - is
[the caching notes](../../../../../../../../../docs/dev/caching.md).
