# Map chrome (`maplayers/base/chrome`)

The controls the player moves the layers with from outside the sidebar. Two of them live here, and
they are the only places the overlay reaches into a widget tree somebody else built: a tick box
appended to the vanilla filter row, and the dialog the layer bar is arranged in. A standing heal
lives here beside them, because what those two controls leave behind is a pick that no longer
matches the bar.

Part of [the map layers framework](../../README.md); see there for the layer roster, the bar
arrangement these two write, and the sidebar the dialog is opened from.

## Index

- [The filter-row toggle](#the-filter-row-toggle)
  - [Failing open, and what it costs](#failing-open-and-what-it-costs)
  - [The hatch and the key](#the-hatch-and-the-key)
- [The pick follows the bar](#the-pick-follows-the-bar)
- [The arranging dialog](#the-arranging-dialog)

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

The same word withholds the No Layer tab from that screen, so a box going up can leave that screen's
pick on a tab it has stopped offering. Settling that is [the heal below](#the-pick-follows-the-bar)
rather than the upkeep's: a pick is stranded by a row the player arranged as readily as by a box.

The upkeep holds each screen's box rather than only recording that one went up, and writes what it
shows from the pick every frame: the pick moves under a standing box - the heal does it, and so does
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

## The pick follows the bar

Two ways the bar and the map come to disagree, both a few clicks apart, and one rule heals both.
Take the tab off the layer a screen is painting and that layer goes on painting with no tab lit, the
one tab left standing showing unselected. Put the tab back on a screen whose own control has taken
the No Layer tab over and the pick is still the empty view, the only tab there standing unlit over an
empty map.

**A pick that is not among the tabs its screen offers moves to the leading one, and that screen's own
show-or-hide control is set to whether that tab paints.** Both halves, because the two controls have
to end up saying one thing: landing on the empty view stands the control down so its box reads empty
too, and landing on a layer that paints stands the control back up so the tab that just lit has
something under it. The row it asks about is the offered one - the arrangement over the roster, then
the withholding, then the last-tab-standing guard - which is what makes the two cases one question,
and what explains why the second bites on the sector map alone: there the No Layer tab is taken by
the withholding, while on a screen with no box that tab stands, so a pick resting on it is a choice
and is left alone.

This reverses a rule the framework used to state - that hiding a tab is not switching a layer off, so
a save holding a hidden layer as its pick still painted it. That reading defended the pick and was
right about the store, a hidden id being no more a lost one than a hidden layer is an unregistered
one. What it missed is that the player is looking at a bar: a layer painting from a tab that is not
there is a map nothing on screen accounts for, and the only way back to it is a dialog they have to
remember to open. The pick is preserved in the one way that shows, which is by following the tabs.

`MapLayerPickUpkeep` asks it per frame, for every screen, rather than at the moment a row changes.
One dialog moves both screens' rows, a screen nobody is looking at still has to be right when they
next look, and the pass that lays a row out is a layout and may not write. Registered per sector by
`MapChromeInstaller` beside the box's own pass and behind a guarded step of its own, so a load that
loses the box keeps the heal; it runs while paused, every screen the bar draws on pausing the
campaign. A heal leaves the pick on a tab the row offers, so the next frame finds nothing to do and
nothing is written until something moves again.

## The arranging dialog

What writes the bar arrangement - which layers carry a tab, and in what order. Opened from the bar
and from nowhere else, and it is the only way a tab taken off the bar comes back. Every change is
recorded as it is made rather than drafted, so the one way out says **Apply** and there is no Cancel
beside it.

Like the toggle above it, the dialog is a reach into a widget tree somebody else built: the game
publishes no route to a custom modal from these screens, so the panel is stood in the core UI's own
tree and supplies its own modality. It simply does not open where that reach comes up empty.

The mechanics - the editor, the reach, the claim rule, the row and the box it stands in - are
[`arrange`'s own](arrange/README.md).
