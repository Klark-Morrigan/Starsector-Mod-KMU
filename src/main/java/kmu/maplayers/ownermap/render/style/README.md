# Render style layer (`render.style`)

The single home for *how an owner map is styled* -
what turns a player's LunaLib choices into the concrete colours,
widths,
and line patterns each cluster group draws in.
Everything here is read once per map rebuild and baked into the flat draw packets,
so the renderer downstream stays a pure GL loop with no knowledge of settings.

Part of [the owner-map tier](../../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../../README.md) for project context.

## Index

- [Layout](#layout)
- [The categories: how a layer divides its cells](#the-categories-how-a-layer-divides-its-cells)
- [The selections: which slot an element is pointed at](#the-selections-which-slot-an-element-is-pointed-at)
- [The reader: the one seam](#the-reader-the-one-seam)
- [The resolvers: choices into colours](#the-resolvers-choices-into-colours)
- [What is not here](#what-is-not-here)

## Layout

*What the player chose* and *what that means in paint* are split across two packages:

- [`base.theme`](../../../base/theme/README.md) -
  the choices as inert value types (`RenderStyle` and its tiers),
  in the framework rather than here,
  since a theme's shape belongs to no one layer.
  This package reads them and never writes back.
- this package -
  the behaviour,
  all of it the owner map's,
  plus the two pieces of owner-map *vocabulary* the theme holds openly:
  the set it is keyed by,
  and the palette slots an element can be pointed at.
  One category set,
  one selection set,
  one reader that populates the theme,
  and the resolvers that turn a selection plus an owner's recede into concrete shades.

## The categories: how a layer divides its cells

Which categories a map divides its cells into is the painting layer's declaration,
`OwnerCategories`,
and the tier never names one.
It states only the roles a category plays:
the categories and their styles,
read once per rebuild;
each owned category's name style;
the *full-strength* category,
whose fill opacity a desaturated owner holds;
and the category an unowned cell falls to,
settled or empty.
Which category an owner draws in is the owner reading's answer rather than the declaration's,
since it can depend on the snapshot the reading was taken over.
The theme keys on the open `MapStyleCategory`,
so a hazard or relay layer declares its own set alongside its own painting code.

KMU's own layers share one declaration,
with its styles,
beside the rest of their answers,
which [the owner seams](../../owners/README.md) link to.

An unowned category has no owner and so no palette to choose a shade from:
it paints in the shared neutral colour and exposes no colour field at all,
leaving each element's opacity as its only on/off -
the sole shift that colour ever takes is the value lift a spotlight-spared cell gets,
described below.
A settled unowned cell draws a fill and an outline
(something stands there, so it reads as occupied rather than as a bare ring);
an empty one draws an outline alone,
since filling it would wash every corner of the sector nothing else holds.
Neither has an inner seam:
unowned cells never fuse into clusters,
so there are no province divisions to stroke.

The split the two unowned roles draw is **presence against absence** -
is anything here,
or is this the backdrop.
A layer whose rule gives nobody a settled system leaves it unowned,
for reasons of its own.
That cell is settled,
so it takes the settled category.
The alternative -
reading emptiness off the owner map -
hides a populated system behind the uninhabited-systems checkbox,
which is the map erasing what is there.

The fixed border channel is deliberately *not* here and not tunable at all:
it decides where fills meet rather than how they look,
so it lives on the shaping that applies it,
as `base.geometry.CellShaper.BORDER_INSET_DISTANCE`.

## The selections: which slot an element is pointed at

`OwnerPaletteSlot` is this map's answer to the theme's open `ElementPaintSelection` -
`PRIMARY` or `SECONDARY`,
naming which of the two slots of an owner's palette an element reads.
A selection is not a colour:
it says *where to look*,
and stays unresolved until an owner is in hand,
because one theme serves every owner on the map.
The theme holds one per element and never asks what it means;
it decides only whether an element paints at all,
which it reads off the selection being absent.

It is deliberately narrower than `OwnerPaletteChoice`,
the settings-side wire format,
which needs a third option ("No color") the render side must not be able to hold:
a style says "paints nothing" by carrying no selection,
so an explicit no-colour *value* would read as drawable to every caller that tests for one.
`resolvePaintSelectionOf` is the one crossing between the two,
mapping NONE -
and an unresolved setting -
onto no selection at all.

Turning a selection into the shade it names is `MapPalettes` below -
the only place in this package where the word *shade* means a concrete colour rather than a slot.
It is also where a selection belonging to some other layer's option set resolves to no shade rather than to a wrong one.

## The reader: the one seam

`RenderStyleReader` is the ONE place the theme is read,
almost all of it out of LunaLib.
A new knob is read here and lands on the matching theme record -
never fetched ad hoc in a builder,
which is what lets an incremental re-shape restyle against the same snapshot the full build used.
It reads the global tier itself and asks the layer's declaration for the category styles,
which that declaration reads off knobs of its own.

The single exception is whether the uninhabited outline draws at all:
that is the overlay sidebar's checkbox
(`UninhabitedOutlinePreference`, per-save sector memory),
because a LunaLib field would duplicate that control on the settings screen.
Its opacity and width stay LunaLib knobs.

That one input is passed in rather than read:
it travels with the rest of the sidebar preferences in the rebuild's `ContentInputs`,
sampled once per rebuild.
Read here it would be a second reading of a value the rebuild has already decided it is owed a rebuild for,
free to disagree with it.

`MapStyling` is what one rebuild makes of the read:
the theme,
the border trace,
the layer's categories,
and the three palettes an unowned cell can paint in,
resolved once by `MapStyling.readFromLunaSettings`.
The rebuild reads it once and hands it to everything it builds -
the fills or the border-tracing overlay,
and the names over either -
so no two parts of one map are styled by two readings of the same knobs.

## The resolvers: choices into colours

Rules that both the fills and the cluster-name labels read,
so a name can never drift from the space it labels.
All of them are pure.
The one thing they cannot work out for themselves is what colour an owner is,
so the owner reading hands it in rather than a rule reaching for it,
and every rule here stays pure over the shades it is handed.

- `MapPalettes` -
  resolves a paint selection plus a recede into concrete shades:
  which palette,
  which slot,
  and what a desaturated subject recolours to.
  Stated over a palette rather than over an owner,
  so an ownerless cell recolours by the same rule an owner does -
  and an ownerless cell gets that palette from `resolveNeutralPalette`,
  the one statement of "no owner means the neutral colour in both slots".
  Every palette here is a `ColourPair`,
  which names nothing about where the shades came from.
  The neutral an unowned cell paints in,
  and the shades a receded owner sinks toward,
  are the owner reading's answers rather than anything this package reads for itself.
- `OwnerStyleResolver` -
  resolves the shared per-owner decision
  (the category an owner draws in and the adjustment it draws under) into an `OwnerStyleDecision`,
  off the owner reading - except the spotlit owner, which draws in the full-strength category.
- `UnownedStyleResolver` -
  the counterpart for a cell with no owner,
  which has no owner to carry a decision:
  whether the cell is settled, which the layer's declaration turns into its category,
  and whether the pass's recede reaches it
  (a settled cell yes, an empty one no, and a settled one the spotlit owner lives in no either).
  Classifies off the inhabited and spotlit-presence sets of the build's `ResolvedOwners`
  (`inhabitedSystemKeys`, `spotlitPresenceSystemKeys`),
  never off the owner map that sent the cell here.
  The source answers both in one call,
  so a cell drawn as uninhabited is never one the spotlight spared.
- `OwnerStyling` -
  maps that decision onto the pass's actual theme,
  giving the concrete bundle plus adjustment an owner draws under.
  It owns the one field that crosses between bundles:
  a desaturated owner's fill is held at the *full-strength* category's opacity,
  so a desaturated surface reads as one uniform grey rather than splitting into two weights of empty.
- `ResolvedOwnerPaint` -
  carries that bundle and adjustment down to the shades,
  and is the one read every element is painted through:
  `pickPaintOf` picks the element's slot out of the palette at its own opacity scaled by the mute.
  The desaturation swap is applied as the record is resolved rather than per element,
  since it is one decision for the whole owner.
  A cell nobody holds resolves one over shades stated outright -
  the neutral, or the neutral lifted where a spotlight spared it -
  so a lone cell's fill and outline are painted by the very rule an owner's are.

The decision and the mapping are deliberately two steps.
`OwnerStyleResolver` stays theme-free so the label path and the fill path resolve the same answer;
`OwnerStyling` is where a theme is finally required.
The fill path asks for the whole paint through `OwnerMapBuildInputs.resolveOwnerPaintOf`,
which composes all of it off the one reading a build was baked under;
the label path,
which needs a name's colour rather than an element's paint,
reads `OwnerStyleResolver` directly.

## What is not here

*Muting and desaturation* are a separate,
dynamic axis:
an `ElementStyleAdjustment` applied on top of the resolved style,
per owner or per ownerless cell.
The adjustment itself -
the pair of knobs and the rule for unioning two of them -
is the framework's,
in [`base.theme`](../../../base/theme/README.md);
this layer owns the desaturation *mechanism* (the palette swap in `MapPalettes`)
and the rule for which unowned cells it reaches (`UnownedStyleResolver`).

A spotlight separates its subject from its backdrop with **two** palettes,
both resolved once per pass by `MapPalettes` and both moving value alone:
`resolveDesaturationPalette` sinks the reading's recede shades toward black by `desaturationDarkening`,
and `resolvePresencePalette` lifts a spared unowned cell's neutral toward white by `presenceLightening`.
Either alone leaves the two too close to tell apart
wherever a reading's neutral and recede shades start out the same grey,
since a cell that is merely not receded then sits exactly where the background began.
The lift is a wash toward white on RGB,
never a recolour:
a spared cell has no owner to borrow a hue from,
and one would state an ownership the layer sparing it is reporting it does not have.

The *policy* of which owner recedes is the owner reading's,
and how far is the preferences' one package up in `ownermap` (`RecedePreferences` and the views' own backdrops).
*Baking* the resolved style into the draw packets is [`render.clusters`](../clusters/README.md) -
its `PaintedCellBuilder` and `ClusterGroupBuilder`.
What makes the "once per map rebuild" above actually happen -
which settings change is noticed,
and how it reaches this layer -
is [the caching notes](../../../../../../../../docs/dev/caching.md).
