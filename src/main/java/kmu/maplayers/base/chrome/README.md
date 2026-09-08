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

What writes the bar arrangement - which layers carry a tab, and in what order. Opened from the bar
and from nowhere else, and it is the only way a tab taken off the bar comes back. Every change is
recorded as it is made rather than drafted, so the one way out says **Apply** and there is no Cancel
beside it.

Like the toggle above it, the dialog is a reach into a widget tree somebody else built: the game
publishes no route to a custom modal from these screens, so the panel is stood in the core UI's own
tree and supplies its own modality. It simply does not open where that reach comes up empty.

The mechanics - the editor, the reach, the claim rule, the row and the box it stands in - are
[`arrange`'s own](arrange/README.md).
