# Market Condition Manager

## Index

- [Problem](#problem)
- [For Laymen](#for-laymen)
- [Baseline Behavior](#baseline-behavior)
- [Scope](#scope)
- [Library Policy](#library-policy)
- [Risks](#risks)

## Problem

Starsector exposes planetary conditions as informational UI on colony screens,
but there is no direct in-game editor for adding those conditions while testing
or tuning a modded campaign. Console commands can add a condition, but they are
text-driven, easy to mistype, and usually include extra cleanup such as removing
mutually exclusive conditions.

The first feature in Klark Morrigan's Utilities is a colony-screen planetary
condition picker. It starts from the practical problem of wanting a fast,
visual, in-context way to add any planetary condition to the colony currently
being inspected.

## For Laymen

Planets have traits like `Hot`, `Ore: Sparse`, `Habitable`, or modded special
conditions. This feature puts a button on the colony screen. Clicking it shows a
list of all possible planet traits. Bright entries are already on the planet;
dim entries are not. Clicking any entry adds that trait to the planet.

## Baseline Behavior

- Show a `Planetary Conditions` button on a colony market screen.
- Support opening the screen while physically present at the planet.
- Support opening the screen from the faction colonies/outposts ledger.
- Allow editing any colony the screen can inspect, regardless of ownership.
- List every loaded market condition whose spec is planetary.
- Show present conditions with normal UI coloring.
- Show absent conditions with darkened UI coloring.
- Attach condition tooltips to the entries.
- Add the clicked condition to the market without removing conflicts,
  incompatible conditions, or same-group conditions.
- Reapply market conditions after adding one so the campaign state updates.

## Scope

This feature is an editor/debug utility. At this stage, ownership is not a
permission boundary: player, friendly, neutral, and hostile colonies are all
valid targets if their colony screen can be inspected. It does not try to
validate lore, terrain, climate, resource, or mod compatibility rules. It also
does not remove conditions; removal can be a later feature.

## Library Policy

This feature should not avoid libraries for the sake of staying dependency-free.
If a library reduces Starsector UI reflection, provides reliable custom panel
helpers, or simplifies button/tooltip behavior, it is in scope. Any dependency
must still be documented in `mod_info.json`, used for a clear reason, and
covered by the verification steps.

## Risks

- Adding incompatible conditions can create strange colony stats. That is
  intentional for this first utility, but it should remain clearly documented.
- Editing colonies owned by other factions can alter campaign balance and
  faction economies. That is intentional for this editor stage.
- Starsector colony UI classes are internal. The hook will use reflection and
  may need adjustment after a game update.
- Some condition tooltips depend on the condition already existing on a market.
  Absent conditions should fall back to spec-based tooltip text.
