# Arranging the layer bar (`chrome/arrange`)

The modal that writes the bar arrangement -
which layers carry a tab,
and in what order.
Opened from the bar and from nowhere else:
no key is bound to it,
a bar that is on screen needing no second way in.

Part of [the map chrome](../README.md),
in Klark Morrigan's Utilities;
see [the map layers framework](../../../README.md) for the layer roster and the arrangement this writes,
and the [mod README](../../../../../../../../README.md) for project context.

## Index

- [How the package divides](#how-the-package-divides)
- [The claim is the whole screen](#the-claim-is-the-whole-screen)
- [Raised and painted part company](#raised-and-painted-part-company)
- [Everything testable is outside the panel](#everything-testable-is-outside-the-panel)
- [Element, never cell](#element-never-cell)
- [What is not here](#what-is-not-here)

## How the package divides

The seven that do the work,
split along one line:
what needs a running game,
and what does not.
The package holds three more -
the row,
the action and the claim rule -
each named below where what it carries is the subject.

| Type | Owns |
| --- | --- |
| `MapLayerArrangementDialog` | what is being arranged, when it may start, what ends it, and asking the layers to follow the bar it left |
| `ArrangementDialogPanel` | the surface - the panel in the core UI, its claim, its paint, its lifetime |
| `ArrangementDialogFade` | whether the dialog holds the screen, and how far onto it the box is painted |
| `MapLayerArrangementEditor` | the rows, the three things a press can do to one, and when it refuses |
| `MapLayerArrangementDialogBody` | the furniture around the column, and the two areas no widget paints |
| `ArrangementRowWidgets` | one row's name and its three controls |
| `ArrangementBoxLayout` | every measurement, and the positions derived from them |

The reasoning behind each sits in its own class Javadoc;
what follows is only what none of them can say alone.

The dialog and its panel divide on **what is being arranged** against **what it is arranged on**.
The dialog holds the editor and decides when the player may start and stop;
the panel holds the vanilla widget,
the clock and the frame hooks.
Neither reaches into the other:
the panel is handed a builder when the widgets need replacing,
and reports back the two things it cannot rule on -
a press on the way out,
and a screen gone out from under it -
because whether either ends the arrangement is a question about an arrangement rather than about a surface.

The same line settles where an arrangement reaches the layers themselves.
A tab taken off the bar [stands its layer down](../../layer/README.md#what-a-hidden-tab-stands-down),
and it is the dialog that asks for it after each press -
not the editor,
which is deliberately reachable with no running game,
and standing a layer up on a sector needs one.

`MapLayerArrangementRow` and `ArrangementRowAction` pass between the halves.
The action is its own type rather than nested in either,
since the column puts it on a button and the editor acts on it -
nesting it would make one of those two the other's owner.
Outward,
the panel reports in KMLib's `OverlayPresence`,
the shape anything raised over a screen answers in -
so whatever stands aside for this dialog stands aside for a vanilla modal by the same reading.

## The claim is the whole screen

The game supplies no modality for a panel stood in the core UI -
it dims nothing behind one and goes on dispatching to what is under it -
so the dialog consumes every event it is handed.
Every event,
with no exception carved out for its own box,
because the engine hands a custom panel's events to its children first and its plugin afterwards:
a press one of the dialog's controls acted on arrives at the claim rule already consumed,
and the skip that leaves a consumed event alone is the whole of what protects the column.

That order is why the first reading was wrong rather than merely cautious.
Excepting the box by position left the events inside it untouched for *everything* under the dialog,
not only for the dialog -
so a star beneath the dim went on raising its own tooltip while the same star out on the open map,
where the claim did reach,
answered nothing at all.

## Raised and painted part company

The dialog fades in and out at the pace of the game's own prompts,
because the sidebar dissolves in step with whatever claims the screen
and a box arriving whole would snap it away instead.
That fade is the dialog's own,
the game fading nothing it did not raise,
and it is written onto the panel as its opacity -
which the engine multiplies into the alpha it hands every widget in the panel
and the plugin's render hook alike,
so the box,
its controls and the dim beneath them move as one piece.

What that costs is that the panel outlives the press.
From the press until the end of the fall the dialog is on screen and must claim nothing,
or it eats the click that follows;
so `isRaised` moves on the press and the fraction moves over the frames after it.
That is the one place this parts company with a vanilla modal,
which the game holds raised until its fade has run
because that is how long it keeps it in the tree intercepting -
the shared shape carries the flag precisely so each answerer can say which frames it covers.
The panel comes off at the end of the fall;
a reopen during it keeps the panel and comes up from where the fade stood.

## Everything testable is outside the panel

A `CustomUIPanelPlugin` exists only inside a panel the game built,
over a core UI that exists only while the game is running.
Anything left inside one can be checked only by opening the dialog and clicking,
so the package is arranged to leave as little there as possible -
and the four things pulled out are the four that were each,
at some point,
the part nothing had verified:

- **`ArrangementDialogEventResponse`** is the claim rule -
  including that a dismissed dialog still on screen claims nothing -
  so the dialog's whole modality is a function of one event rather than a branch inside a render hook.
  See [what the claim covers](#the-claim-is-the-whole-screen).
- **`ArrangementDialogFade`** is the clock,
  so that raised moves on the press and the paint does not is a fact about two fields
  rather than something watched for on screen.
- **`MapLayerArrangementEditor.applyRowAction`** is the mapping from a press to what it does.
  A case wired to the wrong one of the three is silent -
  the button works,
  it simply does the other thing.
- **`ArrangementBoxLayout`** is the arithmetic,
  because where an element sits and how tall the box stands are questions with answers,
  and a class that also needs a running game to build a panel can only answer them by being looked at.
- **`ArrangementDialogPanel.isMapShowing`** is the one reading in this mod that fails *closed*,
  and the direction is the whole of it:
  a reach that raises means the widget tree cannot be walked,
  and a dialog that cannot find the screen it was opened over comes down rather than standing on it.
  It takes the reach rather than making it,
  so only the raise needs a screen nothing can be built on.

What is left in the panel's own plugin is glue:
it reads those answers and acts.
One rule of its own stays unverified,
and is the last thing here that could only be checked by opening the dialog -
a panel whose detach did not take goes on being advanced by whoever still holds it,
with nothing left to paint through,
so the advance stands down before writing through a panel it no longer has.

The same line explains the seam at the other end.
The editor is seeded once and is thereafter its own source of truth -
re-reading the store between clicks would read back what it just wrote,
and re-reading the roster would let a mod registering a layer mid-dialog shuffle the row under the player's pointer.

## Element, never cell

Under `kmu.maplayers.base` that other word means a system's polygon on the map -
the geometry that shapes one,
the edges it is classified by,
the hit test that finds one under the pointer.
This package borrowed it for the vanilla elements its box is laid out from,
which is a table's word for something that is not one:
the head and the foot are part of no row at all.
The engine already names them -
a `createUIElement` returns an element -
so this tree says *element*,
and `enforcePackageVocabulary` in [`build.gradle`](../../../../../../../../build.gradle) holds it to that,
with this file exempted so the rule can name the word it forbids.

The vocabulary matters here beyond tidiness,
because an element has two left edges.
The engine draws an element's contents inset from the element's own edge,
so a placement and a drawn position are different numbers -
which is why the frame once stood closer to the buttons on the right than to the words on the left.
`ArrangementBoxLayout` names that inset once and takes both of the box's edges from it;
`resolveElementWidth` is the other half,
and its Javadoc records the one case that must not use it.

## What is not here

- **The bar this writes,
  and the store behind it** -
  `ArrangedLayers` and the layer roster are [the framework's](../../../README.md).
- **The other reach into somebody else's widget tree** -
  the tick box appended to the vanilla filter row is [the chrome package's](../README.md).
- **The sidebar the dialog is opened from** -
  [`base/sidebar`](../../sidebar/README.md).
