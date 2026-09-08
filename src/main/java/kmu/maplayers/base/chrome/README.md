# Map chrome (`maplayers/base/chrome`)

What the mod puts on the game's own map chrome rather than over it. Two controls live here, and they
are the only places the overlay reaches into a widget tree somebody else built: a tick box appended
to the vanilla filter row, and the dialog the layer bar is arranged in.

Part of [the map layers framework](../../README.md); see there for the layer roster, the bar
arrangement these two write, and the sidebar the dialog is opened from.

## Index

- [The filter-row toggle](#the-filter-row-toggle)
  - [Failing open, and what it costs](#failing-open-and-what-it-costs)
  - [The hatch and the key](#the-hatch-and-the-key)
- [The arranging dialog](#the-arranging-dialog)
  - [Where it stands](#where-it-stands)
  - [Modality, supplied here](#modality-supplied-here)
  - [What a row says, and what it shows](#what-a-row-says-and-what-it-shows)
  - [The box it all stands in](#the-box-it-all-stands-in)

## The filter-row toggle

One tick box appended to the vanilla filter row - the strip carrying Starscape, Names and the rest -
showing and moving that screen's show-or-hide pick. So hiding the layers, and the sidebar with them,
is where a player already looks for "show or hide this map furniture".

`MapLayerToggleUpkeep` is the standing pass that keeps the box on whichever row is up, the game
rebuilding its row on every open. It is stood up per sector by `MapChromeInstaller` behind the
feature switch the rest of the overlay is behind, and is transient like the rest of it, two passes
being two boxes over one pick. `MapLayerToggleAttacher` is the write, held behind a seam because
standing a control on another party's widget is a reach into the running game's tree, and
`VanillaMapLayerToggleAttacher` is that reach, over KMLib's `MapFilterToggle`.

### Failing open, and what it costs

Everything here fails open - no row, no room, a shape that no longer builds a drivable button, a read
that throws - to no control, one line in the log, and a map behaving as it did before the box
existed.

Failing open has a second half no log line covers. The upkeep is also the only thing that says a
screen *has* a control, said once a box is actually standing rather than when one is attempted, so a
screen it never writes to goes on showing its layers whatever the save holds. That word is what
`ControlBackedMapLayerVisibility` acts on, and a box is bound to the stored pick underneath it, so it
shows the choice the player made rather than the reading that rule gives everything else.

The same word withholds the No Layer tab from that screen, so the first box to stand on a screen
still set to it moves the pick to the default layer and stores a hide - the map is as blank as it
was, under a box that now says so. Once per screen, on the first box up, and never on a row that
refused one.

The upkeep holds each screen's box rather than only recording that one went up, and writes what it
shows from the pick every frame: the pick moves under a standing box - that move does it, and so does
a hatch closed and reopened over one - and the row offers no way to take a box off and put a fresh
one up in its place.

### The hatch and the key

Whether the box is attempted at all is `kmu_map_dev_ui_filters_mapLayersToggle_isEnabled`, a dev
hatch rather than an appearance knob, since what it governs is the reach and not the look.

Which key ticks it is `kmu_map_keybinds_filters_mapLayersToggle`, default M, read afresh at each
attachment the way the box's words are - so a rebind reaches the next screen the player opens rather
than waiting for the next load - and cleared with Escape leaves the box answering no key at all. Not
a digit, the row's own six being digits and nothing in the game able to say which of them a screen
has already taken.

## The arranging dialog

What writes the bar arrangement, in `arrange`. Opened from the bar and from nowhere else:
no key is bound to it, a bar that is on screen needing no second way in.

`MapLayerArrangementEditor` is the whole of what the dialog *does* - the rows in bar order with hidden
tabs among them, since this is the only way one comes back; **Up** and **Down** buttons that swap a
row with its neighbour, disabled at the ends of their travel; and a toggle that refuses the last tab
still on the bar, which is the same guard `ArrangedLayers` keeps against a hand-edited store, made
here so the click is never offered.

Every change is recorded at once rather than drafted, the bar behind the dialog being the thing
arranged - so the one way out says **Apply** rather than Close, and there is no Cancel beside it.

### Where it stands

`MapLayerArrangementDialog` is the surface: a vanilla `CustomPanelAPI` stood in the core UI's own tree
by KMLib's `CoreUiOverlayPanels`, with nothing painted into the map's render pass. Every published
route to a custom dialog hangs off an interaction dialog and the screens this is opened from have
none, so the core UI tree is what is left - a reach rather than an API, which is why the dialog simply
does not open where that reach comes up empty.

The parent is resolved at each open and never held, the core UI being rebuilt when the player leaves
the screen. The dialog also closes itself when the map goes off screen: the panel hangs from the core
UI rather than from the screen it was opened on, so nothing about leaving that screen takes it down.

### Modality, supplied here

A panel added that way is an ordinary child - nothing dims behind it and nothing stops the screen
underneath being dispatched to - so the dialog supplies its own modality in three parts: it paints its
own backdrop, it claims the input its widgets do not want, and it publishes `isDialogRaised()` for the
map-side gates that stand down under a modal but cannot recognise this one, the game's own
`CoreUiDialogView` knowing a modal by a member a custom panel does not carry.

`ArrangementDialogEventResponse` is the claim rule, apart from the panel plugin that acts on it
because a plugin exists only inside a panel the game built. **Only mouse events inside the dialog's
own box are left alone; everything else is claimed.** That way round rather than "claim everything"
because the order in which the game hands events to a panel's widgets and to its plugin is the game's
business: leaving the box's own events untouched is correct whichever way round it is, while claiming
them first would leave the dialog's buttons dead on a build that dispatches to the plugin first. An
event something else has already consumed is left alone before anything measures it, six of
`InputEventAPI`'s accessors throwing once that has happened.

### What a row says, and what it shows

`ArrangementRowWidgets` is one layer's row. **It shows its state rather than saying it**: the box
carries no word, the line over the column saying what it does once rather than once per row, and a
layer whose tab is off the bar has its name drawn in the muted shade - so the column answers "what
have I taken off" at a glance rather than one box at a time.

The muting is read off the row's own state and never off what the editor will allow to change. The
two part company on exactly one row - the last tab still on the bar, whose box is refused because
taking it off would leave no way back to this dialog - and that row is on the bar, so it draws
unmuted.

Words rather than glyphs on the pair that moves a row, matching how every other button in the game's
UI names what it does, and **no drag**: vanilla furnishes no drag idiom anywhere, so a drag list would
be a plugin painting and hit-testing a column of its own - the GL pass back in the one place this is
built to keep it out of.

### The box it all stands in

`MapLayerArrangementDialogBody` is the furniture around the column - the head, the way out, the rule
around them, and the two areas nothing else paints. The game publishes a rectangle component that
strokes and none that fills, so the frame is a widget while the screen dim and the box's own surface
are drawn from the panel's own `renderBelow` hook, in the panel's coordinates and under every widget
it holds.

`ArrangementBoxLayout` is every measurement and the positions derived from them, kept apart because
arithmetic is checkable and widget calls are not: where a cell sits and how tall the box stands for a
given number of rows are questions with answers, and asking them of a class that also needs a running
game to build a panel means they can only be answered by opening the dialog and looking. The box is as
wide as its parts, so the parts are what is stated and the width is what follows.

Every part of the box is a vanilla element placed by hand rather than one element told to run across,
because an element lays its contents out top to bottom. A row is therefore elements side by side, and
the surface they all stand on is one painted rectangle rather than a fill per element, which would
leave the gaps between them showing the map through.

**Element, never cell.** Under `kmu.maplayers.base` that word means a system's polygon on the map, and
the modal's head and foot are cells of no row in any case - so `arrange` says *element*, the word the
engine's own `createUIElement` gives it, and a build gate holds the tree to it.

**An element is placed for where it draws, not for where it is.** The engine sets an element's
contents in from its own left edge, so one placed at the box's pad draws them further in again - which
is why the frame once stood closer to the buttons on the right than to the words on the left. That
inset is named once and both edges are taken from it: an element reading from the left goes the pad
less the inset, and one reading from the right is measured back from the box's far edge rather than
accumulated rightward from what stands beside it, so the two edges cannot drift apart as a control's
width moves.

The body is built whole in its constructor and replaced whole on every change - the rows move, so a
set of widgets each nudged into a new position would eventually disagree with the order they were
drawn from.
