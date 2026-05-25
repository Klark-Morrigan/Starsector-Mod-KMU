# Notes - PCP Window Color Override By Controlling Faction

Partial research pointer for feature 011 (PCP = Planetary Condition
Picker, feature 010). The goal is to recolour **the whole picker
window** - frame border, title bar, OK / Cancel button row, and the
interior chrome we paint ourselves - to the controlling faction's
palette so the player sees at a glance whose market they're editing.

The picker is a `CustomDialogDelegate` and barely paints any chrome of
its own (no `addButton` calls inside `KmuConditionPickerDialogDelegate`;
the body is icon grid + a couple of paragraphs). So the visible
"window" the player would notice changing colour is almost entirely
**vanilla-owned outer chrome**, not the delegate interior. The
research below has to focus on reaching that outer chrome - the
intel-style "paint the body with `faction.baseUIColor`" recipe alone
will not move the needle.

## What "faction palette" means in Starsector

Every `FactionAPI` exposes two colours the vanilla UI uses:

- `faction.baseUIColor` - text / border / accent colour.
- `faction.darkUIColor` - fill / background colour.

Plus `faction.logo`, `faction.crest`, and the brand colour pulled from
`faction.getCustom().getJSONObject("baseUIColor")` if a mod overrides
it. Vanilla's intel chrome (`BaseIntelPlugin.addDeleteButton`,
`BaseMissionIntel`'s Accept / Abandon buttons,
`BaseEventIntel.beginTable2`) all source the pair through
`getFactionForUIColors()` and pass them into `TooltipMakerAPI.addButton`
/ table builders.

## Where the vanilla "what palette do I use?" hook DOES exist

`BaseIntelPlugin.getFactionForUIColors()` returns the faction whose
palette every internal chrome call should pull from. Defaults to the
player faction; overridden in `FactionCommissionIntel`,
`LuddicPathBaseIntel`, `FactionHostilityIntel` for faction-specific
records.

`CustomDialogDelegate` has no equivalent. The interface only exposes
`createCustomDialog / hasCancelButton / getConfirmText / getCancelText /
customDialogConfirm / customDialogCancel / getCustomPanelPlugin`. The
dialog frame, title, and OK / Cancel row are painted by vanilla code
that we cannot pass colours into through any public method.

## What the picker actually has the player look at

Audit of `KmuConditionPickerDialogDelegate.createCustomDialog`:

- `headerBody` - one or two paragraphs (location, summary). Tinted via
  `addPara(color, ...)` and `HighlightedParagraph`.
- `gridBody` - the icon grid (`KmuConditionIconGrid` /
  `KmuConditionPickerContainer`). A panel with a faint background
  tint and per-row icons / labels.
- No `addButton` calls. The confirm / cancel actions are produced by
  vanilla based on `hasCancelButton()` / `getConfirmText()` etc.

The visible "chrome" on screen is therefore roughly:

| Surface                               | Painted by                | Reach today  |
| ------------------------------------- | ------------------------- | ------------ |
| Outer dialog frame / border           | Vanilla (engine)          | None (public)|
| Title bar (if shown)                  | Vanilla (engine)          | None (public)|
| OK / Cancel button row                | Vanilla (engine)          | None (public)|
| Header paragraphs (location, summary) | Our delegate              | Full         |
| Icon grid container                   | `KmuConditionPickerContainer` | Full     |
| Per-icon label / state colour         | `KmuConditionIconGrid` etc. | Full       |

The first three rows are the ones that would actually communicate
"this market belongs to the Hegemony" / "this market belongs to a
pirate base" at a glance. The bottom three rows are what we can reach
through the public API.

## The real research target: outer chrome

For the feature to feel like a "whole-window" recolour, we need a
mechanism to influence the first three rows. None of them have a
public hook; the candidates are:

1. **Reflection on the dialog UI tree.** Starsector's interaction-
   dialog and custom-dialog implementations live in obfuscated
   classes. RAT does this kind of thing - it reaches into the dialog
   tree to attach overlays and rebind colours. Worth reading their
   reflection helpers (see `docs/dev/research/...` in the KMU
   research baseline; RAT-Frontiers reference in KMO docs has
   pointers too). The KMU research baseline already says "use
   reflection only for discovering or attaching to existing
   Starsector UI panels, and keep that reflection behind small
   KMU-owned helpers" - this fits that posture.
2. **AOTD / VOK interceptors.** The research baseline cites AOTD/VOK
   as a UI-architecture reference. Their dialog interceptor /
   listener shape is the closest reference for "swap colours on an
   existing vanilla panel". Read their condition or dialog overlay
   code before designing our own.
3. **LunaLib custom dialogs.** LunaLib ships its own panel /
   element types (`LunaElement`, dialog variants) which may already
   expose a constructor-time faction or palette override. If so,
   migrating the picker onto a LunaLib dialog is a much smaller
   change than reflecting into the vanilla one. LunaLib is a
   "planned later" dependency per the feature-010 research baseline;
   feature 011 is a reasonable forcing function to revisit that.
4. **Replace the vanilla custom dialog entirely.** Render the picker
   as a `CustomUIPanelPlugin` mounted on a campaign overlay rather
   than going through `showCustomDialog`. Costs the most (we have to
   reimplement OK / Cancel chrome ourselves) but gives full control
   over every pixel. Mention for completeness; only consider if 1-3
   all dead-end.

The order above is also a recommended research order: cheapest to
most expensive in implementation cost.

## What the interior recolour still buys us (cheap win first)

Even before the outer-chrome research lands, we can do the interior
recolour pass:

1. **Owning-faction resolver.** Helper reading the controlling faction
   off the target market (`market.faction`, or `market.factionId` ->
   `Global.getSector().getFaction(...)` when we only have the id).
   Default to player faction when the market or lookup fails.
2. **Thread the faction through the delegate.** Constructor-inject a
   `Supplier<FactionAPI>` (re-resolved per `createCustomDialog` so a
   relation flip or transfer mid-campaign re-paints on next open).
   Use `faction.baseUIColor / darkUIColor` in:
   - `KmuConditionPickerContainer` background tint.
   - `KmuConditionPickerSummaryParagraphFactory` accent highlights.
   - Per-icon labels in `KmuConditionIconGrid` (where they currently
     use `Misc.getBasePlayerColor` or similar).
3. **Semantic colours stay.** `Misc.getNegativeHighlightColor()` /
   `getHighlightColor()` carry meaning (red = suppressed,
   yellow = highlight). Do not flip them with the faction or the
   picker becomes unreadable under e.g. a pirate (gold) theme.

This produces a partial recolour that the outer-chrome work (when it
lands) layers on top of. Worth shipping standalone so feature 011 is
not blocked by reflection research.

## Reference call sites

`BaseIntelPlugin` subclasses for the threading pattern:

- `BaseIntelPlugin.java:297` - default `getFactionForUIColors` impl.
- `BaseIntelPlugin.java:338` - `addDeleteButton` showing the
  `baseUIColor / darkUIColor` pair on `TooltipMakerAPI.addButton`.
- `BaseMissionIntel.java:245, 255` - mission Accept / Abandon buttons
  using the same recipe.
- `FactionCommissionIntel.java:275` - one-line override example.

KMO mirror: `KmoSettlementIntel.createSmallDescription` uses the same
`addButton` recipe but does not override `getFactionForUIColors`
because settlements are always player-owned.

## Open questions for full research

- **Outer chrome reach.** Which of the four mechanisms above actually
  works on the obfuscated custom-dialog frame? Concrete reproduction
  on a throwaway test panel before designing feature 011's shape.
- **LunaLib custom-dialog audit.** Does LunaLib already publish a
  themable replacement for `CustomDialogDelegate`? If yes, the
  reflection path is moot. (Reopens the LunaLib dependency
  conversation from the feature-010 research baseline.)
- **Resolver reuse.** The political-map-layer (feature 019) almost
  certainly needs the same controlling-faction resolver. Plan to
  lift it into a shared KMU util the first time it has two consumers.
- **Hostile-faction readability.** Under a fully red faction palette
  (pirates) plus a suppressed condition (also red), does the picker
  stay legible? May need a contrast-bumping helper rather than
  passing `faction.baseUIColor` raw.
