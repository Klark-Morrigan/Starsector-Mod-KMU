# The layer framework (`maplayers/base/layer`)

What a map layer is, what the roster of them is, what each screen has been set to, and which of the
registered layers that screen is offered as tabs. Everything here is settled before anything is
drawn: no class in this package paints, and none of them knows what a layer paints.

Part of [the map layers framework](../../README.md); see there for the tier arrow, the shared
vocabulary and the per-screen key scheme these types compose their keys through.

## Index

- [What a layer is](#what-a-layer-is)
- [The roster](#the-roster)
- [A screen's two picks](#a-screens-two-picks)
- [Which tabs a screen is offered](#which-tabs-a-screen-is-offered)
- [The bar arrangement](#the-bar-arrangement)
- [What a hidden tab stands down](#what-a-hidden-tab-stands-down)

## What a layer is

`MapLayer` is the seam: an id, a tab label, the body controls that open beneath the tabs, and a
shortcut key.

A layer is registered once for the process while what it draws with belongs to one sector, so it
holds no renderer. It is asked for the one belonging to the installation being drawn, and
`MapLayerRegistry` passes that installation through rather than resolving one of its own. What it
*runs* on a sector is held the same way round and for the same reason: a layer states a
`MapLayerStanding` rather than registering anything itself, and is asked to stand up or down against
a named sector.

A layer also letters and binds its own tab. `resolveTabLabelText` hands the bar drawn text rather
than a strings key, and `resolveShortcutKeycode` hands it the key in force rather than a settings
field to read - only the mod that declares a layer holds the bundle its name lives in and the file
its rebinding is stored in, and the bar carries whatever is registered. Both are asked per frame, so
a rename or a rebind shows on the next one. KMU's own two layers answer out of `KmuStrings` and the
`Map - Keybinds` settings tab themselves.

## The roster

`MapLayerRegistry` holds every registered layer, and answers what is in play on the screen showing
this frame.

Registration is one layer at a time and accumulates, so a mod that depends on KMU registers its own
as it loads and lands to the right of the layers it was built on. That arrival order is the whole of
the row's order - no layer states a rank, none being in a position to see the row it stands in - and
the default pick falls out of it: the first layer that offers itself (`isOfferedAsDefaultPick`), so
`NoLayer` leads the strip while declining and the political map is what a fresh save opens on.

Two *different* layers under one id are arbitrated rather than tabbed twice, both tabs otherwise
reading and writing the one stored pick that names them. Nothing is settled at load, a mod
registering after KMU's own load has returned being the ordinary case rather than the exception.

## A screen's two picks

`MapLayerScreens` names the two screens, holds each one's picks under its own frozen keys, and
answers which screen is up. `ScreenMemoryScope` is a screen's segment of a key and the one place one
is composed; `MemoryKeyAddress`, with `AddressedMemoryFlag` and `AddressedMemoryString`, is what a
preference is partitioned by and the two holders that store one slot per partition. The key scheme
itself is [the hub's](../../README.md#two-screens-two-picks).

A screen's tab, its show-or-hide pick and its scope travel together as one `ScreenLayerPicks`, so
nothing can read one screen's tab against another's hiding.

`MapLayerVisibility` is the show-or-hide pick and the fade between the two. It is folded into the
active-layer answer rather than read by each consumer: a hidden screen resolves to no active layer
once its fade is out, which every pass driven by that pick already draws nothing for. The sidebar is
the one part of the footprint outside that answer, since it draws whether or not a layer is picked,
so it reads the same pick at [its own gate](../sidebar/README.md) - two reads for the whole visible
footprint.

What either read gets is `ControlBackedMapLayerVisibility`: a stored hide is acted on only while
that screen has a control able to take it back, and read as shown until it has one, the stored
choice untouched and honoured again the moment there is a control for it. Which screens have one is
[the map chrome README](../chrome/README.md)'s answer, and the session's rather than the save's.

## Which tabs a screen is offered

`ScreenLayerTabs` lays the player's own arrangement over the roster and then withholds `NoLayer`
from a screen carrying a control of its own, in one read the strip and the shortcut walk both take -
two lists would switch to the layer one along from the tab they lit. It never withholds the last tab
standing, a row emptied by hiding and a row emptied by withholding being the same unusable bar.

Both subtractions are from the strip and never from the roster, a stored pick being an id resolved
against it, so a hidden layer stays registered and its id goes on resolving. What it does lose is its
standing on a sector, [below](#what-a-hidden-tab-stands-down).

What becomes of a pick the row no longer offers a tab for is `healPickOntoOfferedTabs`, stated in
[the map chrome README](../chrome/README.md#the-pick-follows-the-bar) beside the pass that asks it.

`readOfferedTabsRevision` is that row's ingredients as one `OfferedTabsRevision`, for a standing pass
that has to ask whether the row could have moved without paying to build it. It is written beside
`resolveTabbedLayers` rather than assembled by whoever wants it: an ingredient added to the row and
not to the revision is a change no holder would see, so the two are one file's business and each
ingredient has a case of its own.

## The bar arrangement

What the player makes of that row, held apart from the roster and living here beside it.
`MapLayerArrangement` is their own order and the ids they took off the bar, kept per user in the
game's common data by `PersistedMapLayerArrangement` rather than in the save: which tab a screen is
on is a fact about one campaign, while how the bar is laid out is a preference about the interface.

It is a preference laid over whatever is registered rather than a roster of its own, and
`ArrangedLayers` is that laying: an id nothing registers is skipped, a registered layer the store
does not name is appended in registration order, an id named twice is placed once, and an
arrangement that would leave no tab at all keeps the leading one - a bar with no tabs having no way
back to itself. So a mod installed, removed or renamed costs the player nothing and needs no
migration, and a file that cannot be read is worth exactly the unarranged row.

Which store answers is bound at the composition root and read through `LiveMapLayerArrangement`, so
nothing on the frame path names a file and an install that bound none reads as unarranged. What is
bound in play is `SessionHeldMapLayerArrangement` over the stored one, the row being assembled on
every frame the sidebar draws and every key it routes, which is more often than a file may be
opened. An arrangement recorded through that holding is what the next frame reads, rather than what
the next start does.

[The arranging dialog](../chrome/README.md#the-arranging-dialog) is what writes it.

## What a hidden tab stands down

`MapLayer.resolveStanding` is what a layer runs on a sector while its tab is on the bar and how to
take it back - a save heal, a listener, a poll. A layer with no sector wiring states none, and is
simply always standing.

`MapLayerStandings` decides when each half is owed, and reads one thing to decide it: the hidden set.
An id entering it stands that layer down on every installed sector, an id leaving it stands the layer
up, and a reorder moves no id between the two - which is what keeps a drag from tearing a layer's
listeners down and building them again. Each layer is diffed against what it was last applied as,
the same shape `KmuToggledFeature` compares a settings switch by.

It is asked on load, so a layer hidden in the store never stands up on a sector at all, and again
from [the arranging dialog](../chrome/README.md#the-arranging-dialog) whenever a press changes the
bar. Every layer goes behind its own failure boundary: an install carrying another mod's layer calls
a stranger's code here, and one that throws on the way up is not a reason for the tabs after it to go
unwired.

Standing is per sector, so what is standing where is `StandingLayers`, held as
[installed machinery](../installation/README.md#standing-a-layer-up-on-one-sector) and beginning and
ending with the sector it describes.

The order matters against the strip: a stood-down layer must never be a screen's pick, or the render
pass asks a layer with no machinery behind it to paint. `healPickOntoOfferedTabs` is exactly that
guarantee - a pick follows the tabs the bar offers, and a hidden layer offers none.
