# Arranging the layer bar (`chrome/arrange`)

The modal that writes the bar arrangement - which layers carry a tab, and in what order. Opened from
the bar and from nowhere else: no key is bound to it, a bar that is on screen needing no second way
in.

Part of [the map chrome](../README.md), in Klark Morrigan's Utilities; see
[the map layers framework](../../../README.md) for the layer roster and the arrangement this writes,
and the [mod README](../../../../../../../../README.md) for project context.

## Index

- [What it does](#what-it-does)
- [Where it stands](#where-it-stands)
- [Modality, supplied here](#modality-supplied-here)
- [What a row says, and what it shows](#what-a-row-says-and-what-it-shows)
- [The box it all stands in](#the-box-it-all-stands-in)
- [Element, never cell](#element-never-cell)
- [An element is placed for where it draws](#an-element-is-placed-for-where-it-draws)
- [What is not here](#what-is-not-here)

## What it does

`MapLayerArrangementEditor` is the whole of what the dialog *does* - the rows in bar order with
hidden tabs among them, since this is the only way one comes back; **Up** and **Down** buttons that
swap a row with its neighbour, disabled at the ends of their travel; and a toggle that refuses the
last tab still on the bar, which is the same guard `ArrangedLayers` keeps against a hand-edited
store, made here so the click is never offered.

Every change is recorded at once rather than drafted, the bar behind the dialog being the thing
arranged - so the one way out says **Apply** rather than Close, and there is no Cancel beside it.

## Where it stands

`MapLayerArrangementDialog` is the surface: a vanilla `CustomPanelAPI` stood in the core UI's own
tree by KMLib's `CoreUiOverlayPanels`, with nothing painted into the map's render pass. Every
published route to a custom dialog hangs off an interaction dialog and the screens this is opened
from have none, so the core UI tree is what is left - a reach rather than an API, which is why the
dialog simply does not open where that reach comes up empty.

The parent is resolved at each open and never held, the core UI being rebuilt when the player leaves
the screen. The dialog also closes itself when the map goes off screen: the panel hangs from the core
UI rather than from the screen it was opened on, so nothing about leaving that screen takes it down.

## Modality, supplied here

A panel added that way is an ordinary child - nothing dims behind it and nothing stops the screen
underneath being dispatched to - so the dialog supplies its own modality in three parts: it paints
its own backdrop, it claims the input its widgets do not want, and it publishes `isDialogRaised()`
for the map-side gates that stand down under a modal but cannot recognise this one, the game's own
`CoreUiDialogView` knowing a modal by a member a custom panel does not carry.

`ArrangementDialogEventResponse` is the claim rule, apart from the panel plugin that acts on it
because a plugin exists only inside a panel the game built. **Only mouse events inside the dialog's
own box are left alone; everything else is claimed.** That way round rather than "claim everything"
because the order in which the game hands events to a panel's widgets and to its plugin is the game's
business: leaving the box's own events untouched is correct whichever way round it is, while claiming
them first would leave the dialog's buttons dead on a build that dispatches to the plugin first. An
event something else has already consumed is left alone before anything measures it, six of
`InputEventAPI`'s accessors throwing once that has happened.

## What a row says, and what it shows

`ArrangementRowWidgets` is one layer's row. **It shows its state rather than saying it**: the box
carries no word, the line over the column saying what it does once rather than once per row, and a
layer whose tab is off the bar has its name drawn in the muted shade - so the column answers "what
have I taken off" at a glance rather than one box at a time.

The muting is read off the row's own state and never off what the editor will allow to change. The
two part company on exactly one row - the last tab still on the bar, whose box is refused because
taking it off would leave no way back to this dialog - and that row is on the bar, so it draws
unmuted.

Words rather than glyphs on the pair that moves a row, matching how every other button in the game's
UI names what it does, and **no drag**: vanilla furnishes no drag idiom anywhere, so a drag list
would be a plugin painting and hit-testing a column of its own - the GL pass back in the one place
this is built to keep it out of.

## The box it all stands in

`MapLayerArrangementDialogBody` is the furniture around the column - the head, the way out, the rule
around them, and the two areas nothing else paints. The game publishes a rectangle component that
strokes and none that fills, so the frame is a widget while the screen dim and the box's own surface
are drawn from the panel's own `renderBelow` hook, in the panel's coordinates and under every widget
it holds.

`ArrangementBoxLayout` is every measurement and the positions derived from them, kept apart because
arithmetic is checkable and widget calls are not: where an element sits and how tall the box stands
for a given number of rows are questions with answers, and asking them of a class that also needs a
running game to build a panel means they can only be answered by opening the dialog and looking. The
box is as wide as its parts, so the parts are what is stated and the width is what follows.

Every part of the box is a vanilla element placed by hand rather than one element told to run across,
because an element lays its contents out top to bottom. A row is therefore elements side by side, and
the surface they all stand on is one painted rectangle rather than a fill per element, which would
leave the gaps between them showing the map through.

The body is built whole in its constructor and replaced whole on every change - the rows move, so a
set of widgets each nudged into a new position would eventually disagree with the order they were
drawn from.

## Element, never cell

Under `kmu.maplayers.base` that other word means a system's polygon on the map - the geometry that
shapes one, the edges it is classified by, the hit test that finds one under the pointer. This
package borrowed it for the vanilla elements its box is laid out from, which is a table's word for
something that is not one: the head and the foot are part of no row at all. The engine already names
them - a `createUIElement` returns an element - so this tree says *element*, and
`enforcePackageVocabulary` in the build holds it to that.

The rule is declared in [`build.gradle`](../../../../../../../../build.gradle), with this file
exempted so the rule can name the word it forbids.

## An element is placed for where it draws

The engine sets an element's contents in from its own left edge, so one placed at the box's pad draws
them further in again - which is why the frame once stood closer to the buttons on the right than to
the words on the left. That inset is `VANILLA_ELEMENT_CONTENT_INSET`, named once, and both edges are
taken from it: an element reading from the left goes the pad less the inset, and one reading from the
right is measured back from the box's far edge rather than accumulated rightward from what stands
beside it, so the two edges cannot drift apart as a control's width moves.

## What is not here

- **The bar this writes, and the store behind it** - `ArrangedLayers` and the layer roster are
  [the framework's](../../../README.md).
- **The other reach into somebody else's widget tree** - the tick box appended to the vanilla filter
  row is [the chrome package's](../README.md).
- **The sidebar the dialog is opened from** - [`base/sidebar`](../../sidebar/README.md).
