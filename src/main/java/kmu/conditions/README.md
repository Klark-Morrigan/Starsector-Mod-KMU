# Market Condition Manager (`kmu.conditions`)

A picker for adding market conditions to a market by hand,
opened from the console with `kmu_mcm_open`.
It is unfinished and ships switched off:
the `Features` tab's switch (`KmuFeatureSettings.isMarketConditionManagerEnabled`) turns it on.

Part of Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../README.md) for project context.

## Index

- [The packages](#the-packages)
- [How it opens](#how-it-opens)
- [Which conditions are offered](#which-conditions-are-offered)
- [The ports](#the-ports)
- [Switched off](#switched-off)
- [What is not here](#what-is-not-here)

## The packages

| Package | What is in it |
| --- | --- |
| `domain` | the conditions the game knows (`KmuConditionRepository`), the market being edited (`KmuEditableMarket`), which conditions may be offered (`KmuConditionOfferPolicy`), and the service that lists and adds them (`KmuConditionService`) |
| `ui.context` | which market a UI is open on (`KmuMarketUiContext`), the resolver that finds it, and the tracker that remembers it (`StarsectorMarketUiContextTracker`, installed by `MarketUiContextInstaller`) |
| `ui.editor` | the entry point that finds the market and opens an editor over it (`KmuConditionEditorEntryPoint`), the editor seam (`KmuConditionEditor`), and the check that the market is one the editor may open on |
| `ui.picker` | the picker editor: its model, the dialog it opens in, the action a click makes, and how its rows and tooltips are drawn |

## How it opens

`KmuOpenMarketConditionManagerCommand` (`kmu.mods.console.commands`) builds the entry point and asks it to open.
`KmuConditionEditorEntryPoint` resolves which market the player is looking at
through `ui.context`,
checks the editor may open on it,
and hands it to `KmuConditionPickerEditor`.
The picker opens in an interaction dialog (`StarsectorInteractionDialogPickerOpener`):
an info panel naming the market and summing its conditions,
over a grid of condition icons.
Clicking an absent condition adds it to the market (`KmuConditionPickerActionHandler`).

Which market a UI is open on is remembered by `StarsectorMarketUiContextTracker`,
which the composition root installs per save only while the feature is switched on.
It is a `ColonyInteractionListener`,
because the listener manager it is registered with hands market opens and closes only to that interface.

A failure the feature catches and carries on past goes to a `KmuErrorReporter`,
which writes it as a warning under the reporting class's logger.

## Which conditions are offered

Vanilla marks the hand-placeable conditions as planetary,
and `KmuConditionOfferPolicy.planetaryOnly()` is that baseline.
`StarsectorConditionOfferPolicy` is what ships:
with the `Market Condition Manager (MCM)` tab's show-all switch on,
the default,
every condition is offered,
the decivilisation conditions among them;
with it off,
the offer falls back to the planetary set.
The policy is a seam so the service never knows which conditions those are,
or how the choice is configured.

## The ports

The domain names no Starsector type,
with one exception:
`KmuEditableMarket` hands out the game market and live condition behind it,
because the picker reads its location and condition icons from them.
Each port has one live binding beside it,
prefixed `Starsector`:

- `KmuConditionRepository` - `StarsectorConditionRepository`;
- `KmuEditableMarket` - `StarsectorEditableMarket`;
- `KmuConditionOfferPolicy` - `StarsectorConditionOfferPolicy`;
- `KmuConditionEditorTargetValidator` - `StarsectorConditionEditorTargetValidator`;
- `KmuConditionPickerDialogOpener` - `StarsectorInteractionDialogPickerOpener`;
- `KmuConditionPickerFeedbackSink` - `StarsectorConditionPickerFeedbackSink`;
- `KmuConditionTooltipRenderer` - `StarsectorConditionTooltipRenderer`.

## Switched off

The console command is registered by data file,
so it exists to be typed whatever the switch says.
It reads the switch itself,
before any other check,
and names where the switch is.
The per-save wiring is the market tracker alone,
which nothing else reads,
so a player who has not asked for the editor runs none of it.

## What is not here

- **[The optional-mod adapters](../mods/README.md)** -
  the console command that opens the picker.
- **[Settings](../settings/README.md)** -
  the feature switch and the show-all knob.
