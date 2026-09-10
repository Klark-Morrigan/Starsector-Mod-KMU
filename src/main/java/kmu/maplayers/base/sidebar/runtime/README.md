# The sidebar on a live screen (`maplayers/base/sidebar/runtime`)

What differs between the two screens the sidebar draws on, what stands it down, and the two passes
that put it there - a render listener and an input listener, neither of them a widget in the game's
own tree.

Part of [the map layer sidebar](../README.md), which owns the placement those passes draw and
hit-test, and [the look](../style/README.md) they paint it in.

## Index

- [Hosts: what differs per screen](#hosts-what-differs-per-screen)
- [Keys the panel answers](#keys-the-panel-answers)
- [What stands the panel down](#what-stands-the-panel-down)
- [The screen gates and the roster](#the-screen-gates-and-the-roster)
- [Border edges](#border-edges)
- [Drawing and input outside the widget tree](#drawing-and-input-outside-the-widget-tree)

## Hosts: what differs per screen

`SidebarHost` is the per-screen role: the screen gate, the anchor, the look, the framed edges, the
panel controller, the fold selection, the layer selection, and the view-state text. `BaseSidebarHost`
holds the plumbing common to both (controller, fold selection, layer selection, the per-load reseed,
the shortcut jump, and the composed live gate), leaving each concrete host only the genuine
differences. A concrete host names its screen's picks and its opening fold; everything a real screen's
host would otherwise compose for itself - the persisted fold, the sound player behind the controller -
is composed in the base, which is what keeps the two hosts down to what actually differs.

| | `MapSidebarHost` | `IntelSidebarHost` |
| --- | --- | --- |
| Screen gate | `CampaignMapView.isSectorMapShowing()` | non-null `getMapVisorRect()` |
| Anchor | screen top-left, by the padding settings | the visor rect's top-left, flush left, below the top-padding setting |
| Height cap | bottom padding setting | the visor's bottom edge |
| Framed edges | `BoxEdge.ALL` | `TOP`, `RIGHT`, and `BOTTOM` until the box reaches the visor bottom |
| Tab band | `HEADER_BAND_HEIGHT` 19 | `HEADER_BAND_HEIGHT` 18 |
| Tab chrome | `STRIP`, key underlined | `RAISED_BUTTON`, key bare |
| Frame colour | its scheme's base accent | its scheme's dark step, what the intel chrome is framed in |
| Fold default | expanded | docked |

The look is in that table because it is the host's: `resolveWidgetStyle()` answers what the panel is
painted in and `HEADER_BAND_HEIGHT` how tall its tab row stands, and the same tab style feeds both
the layout and the paint pass. The two screens sit in different company - one floating free on the
map, the other overlaid on the intel visor amid that screen's own chrome - so each should read as
part of what surrounds it, which a shared renderer could only do by naming the screens. What lets
them diverge as far as they do - one a strip framed in its base accent, the other a row of buttons
framed in the dark step - is that neither `SidebarRenderer` nor `LiveSidebarPlacement` holds a screen
test about it: a third screen would be a third host and no renderer change.

Keys are not in that table because the panel offers the same body of tabs wherever it draws, so
`BaseSidebarHost.handleKeyPress` serves both: a bound key jumps that host's own pick to its layer
and is consumed, and any other key falls through. Because the pick is per-screen, a shortcut moves
only the tab of the screen it was pressed on - and so does a tab being withheld, so a key can be
live on one screen's row and silent on the other's.

## Keys the panel answers

Which key that is comes from the layer - `resolveShortcutKeycode`, asked per frame - rather than from a
settings read here, so the row hints and the key claim carry a layer whose rebinding lives in another
mod's settings file. A non-positive answer is unbound and both halves go inert for that layer: no hint
is printed and no press matches it, its tab keeping its place in the row. The claim only compares
numbers, but the hint indexes LWJGL's name table, which has no range check of its own:
`LiveSidebarPlacement` bounds the keycode before naming it, so one layer answering nonsense costs
itself a hint rather than taking the tab row down.

Every key on that pass is a layer's. The bar's own chrome binds none - the dialog the bar is arranged
in is opened from the bar itself, so a key for it would be a second way in to something already on
screen, and the fold that hides the opener is one the player is a click away from undoing.

Consuming happens [pre-core](#drawing-and-input-outside-the-widget-tree), so a KM shortcut wins over
whatever the screen underneath
binds to the same key. On the intel screen that matters: item action buttons bind `T`, `U`, and `G`,
and the tag filter uses `Q` and `Ctrl+S`. The shipped settings table seeds KMU's own two tabs on `N`
and `P`, which avoid all of them, and the LunaLib Keycode rows those layers read are the way out of
any clash a mod's intel item introduces.

## What stands the panel down

Only the *screen* part of that answer is per-host, and there are two answers rather than one.
`BaseSidebarHost.isOverlayShowing()` is the crisp gate - `!screenClaim.isScreenClaimed() &&
screenPicks.layerVisibility().areLayersShown() && isHostScreenShowing()` - and it is what input
routing and hit-testing read, so a claimant that takes the pointer takes it from the panel on the
frame it appears, and a panel switched off stops answering the pointer on the frame it was switched
off.
`resolveOverlayFade()` is what the *draw* reads: what the claim has not taken, multiplied by how much
of that screen's layers is still on it.

They part company only while something is fading. A modal takes every event outside its box from the
frame it is raised, so input cannot wait for its fade; but the modal darkens the screen over that
same fade, and a panel cut away at the first frame of it reads as a snap against a backdrop still
deepening. So the panel keeps painting, thinner each frame, until the modal is fully in. A claimant
with no fade to follow takes both at once, which is right: the console performs no dissolve for the
panel to join, and the codex performs one too brief and too far out of reach to be worth joining.
This mod's own arranging dialog fades at the pace the game's prompts do, and parts the pair at the
*other* end as well: it lets go of input on the press while its box goes on dissolving, so the
panel's input comes back under a box still painted and only its paint waits for that box to go.
The show-or-hide ramp splits the pair the same way and for the same reason,
the panel dissolving with the overlay it drives rather than cutting away from over it, and the two
fades multiply. Both compositions ask the claim first and the screen last, so the read that walks
live widgets is skipped while the panel is standing down anyway.

That the panel goes with the layers at all is folded in here rather than at the renderer and the
input listener separately, for the reason the pick is folded into the active-layer answer rather than
into each pass driven by it: the sidebar is part of what the layers put on a screen, so it leaves
with the rest of that footprint on one read taken where the gate already is. Which screen a host
reads is chosen once, in its constructor: `ScreenLayerPicks` carries that screen's tab, its hiding and
the scope its keys are composed through together, so no host can be wired to one screen's tab and
another's hiding, or store a preference in a screen it is not drawing on.

`describeViewState()` carries the pick into the view-state log, prefixing the host's own screen state
with `layers hidden` once the ramp is out and `layers hiding` while it is running - different bug
reports, so they are worded apart. A host supplies only its screen's half, through
`describeHostScreenViewState()`.

What can claim the screen, and why any of it stands the panel down rather than being ordered above
it, is [`ScreenClaim`](ScreenClaim.java)'s to state. What belongs here is the rest of the
frame that goes with it: once the fade reaches nothing, the renderer's early return zeroes the frame
clock and drops the input motions, and the input listener cancels a dangling drag - so a claim
arriving mid-drag leaves nothing stale behind.

The claim is injected rather than reached for statically, so what a host does under one is settleable
without a game running: each host's `INSTANCE` names the live `ScreenClaim.INSTANCE`, and a suite
hands in a claim it states.

Hiding the panel is only half of what a claimant owes the map, and the other half is not this
package's. Standing the sidebar down takes its cover over the map down with it, so the map's own
hover would read straight through to the cells under whatever claimed the screen - and it is the act
of hiding that does this, so a claimant that hides the panel *cannot* be reported by the panel's own
cover. Each claimant is therefore a cover in its own right too: the console as `ConsoleMapCover`, a
modal as `ModalDialogMapCover`, the codex as `CodexMapCover`, and this mod's own bar-arranging dialog
as `ArrangementDialogMapCover` - see [map layers](../../../README.md) on `base/hover/cover`, where all
four sit beside the sidebar's. That last one has to be read separately from the modal beside it: a
dialog this mod stands in the core UI descends from nothing of the game's, so the walk that recognises
the game's modals answers no on exactly the frames one of ours is up.

## The screen gates and the roster

Both screen gates ask the same question and nothing beyond it - is there a live canvas under the panel. The
visor rect is absent when the intel tab is not showing, when a sibling sub-tab (Planets, Factions)
holds the column, or when a large-description item has blanked the preview, so it is both gate and
anchor. Which look that canvas wears is not asked on either screen: the layers paint through
[several terrain surfaces](../../render/README.md), two of which draw in Starscape mode, so the
overlay these controls drive is under them in either look.

Both are per-screen reads rather than the host-blind `MapPresence` seam KMLib offers, because each
host anchors its panel to its own screen - "a map is up somewhere" cannot place a box.

`IntelSidebarHost` reaches the concrete intel panel through KMLib's `IntelScreenView` seam, which
fails closed - an unresolvable link hides the sidebar rather than throwing on a live screen.

`SidebarHosts` is the roster, and where a question about "the sidebar" with no screen attached to it
is put to all of them: `isPointOverAnySidebar` answers whether a point in UI coordinates lands on a
live panel, for code reached through hooks that never name the screen that invoked them. It asks each
host's `isOverlayShowing()` before its drawn placement, in that order, because only the intel host's
placement goes null off its screen - the on-map host hangs its panel from the screen corner and has a
box to report wherever it is asked. What the point is tested against is the placement's own
`containsPoint`, so the body-plus-notch footprint is KMLib's answer and not a second copy here.

That roster is the whole of "every host": `SidebarInstaller` walks it for the per-load fold reseed
and for both listener registrations, which is why `restoreFoldFromSave` sits on `SidebarHost` rather than
only on `BaseSidebarHost`. A new screen is added to the roster and is reseeded, registered, and
answered for from that one edit.

## Border edges

Border edges are decided twice for one reason: `layoutBorderEdges()` is what the layout reserves
inset space for, `decideBorderEdges()` is what gets stroked. The left is dropped in both (reserving
it would gap the box off the visor); the bottom keeps its reserved inset but drops its stroke once
flush, within `BOTTOM_FLUSH_TOLERANCE`, so a shared border does not double the visor's own frame.

The reserved left edge also decides where the tab row starts, since the row is laid at the body's
content edge rather than at the box's outer one - so the map row, framed on all four sides, stands
one border in, and the intel row, its left dropped, stands at the anchor. Both follow the one inset
rather than each being placed, which is why aligning the map row against the vanilla tabs above it
moved no intel pixel.

## Drawing and input outside the widget tree

The panel is painted in UI coordinates and its input claimed ahead of the screen, rather than
attached to either screen as a mod panel of its own.

Not for want of a seam, which is worth stating because the arrangement below looks like one forced
on it. A `CustomPanelAPI` can be made a child of the vanilla map widget, and both draws over the map
surface and takes input there - confirmed in play, on both of that screen's tabs. Painting here is a
standing decision rather than a constraint: the toolkit and the hit-testing that goes with it exist
already, and moving onto that seam is backlogged. Whether the intel screen's own map behaves the
same way has not been tested.

`SidebarRenderer` is a `CampaignUIRenderingListener` drawing in
`renderInUICoordsAboveUIAndTooltips` - the only pass composited after the opaque core-UI screen, so
the earlier passes are covered by the screen itself. It gates on the host, advances the collapse,
offers the settled fold, resolves the placement, steps the panel's input motions against it, and
hands off to KMLib's `TabPanelRenderer`.

Drawing last wins against the core UI's tooltips too, which the panel does not want: a tooltip the
cursor raises where the panel overlaps it is drawn underneath and reads as cut off at the panel edge.
So the same pass finishes by repainting it on top - `VanillaMapTooltipProbe` locates the tooltip the core
UI is showing, and KMLib's `CoreUiComponentRepainter` draws it again clipped to
`TabPanelPlacement.computeOuterBound`. The clip is the panel's footprint rather than the tooltip's,
so only the hidden part is drawn twice and the tooltip reads at one opacity across the panel edge. A
repaint that cannot be made (the read broke, or the draw threw) leaves the tooltip where vanilla drew
it and warns once a session, so the failure costs the occlusion it was there to fix and nothing more.

It is retried on the next frame rather than stood down, which is a decision and not an oversight. The
reach fails both where the build carries no such draw entry point, which will not change within a
session, and where the component's own draw threw on this frame's state, which will - and the two
arrive as the same undeclared throwable, so they cannot be told apart from here. Standing down on the
pair would spend every later frame's lift to save the cost of one bad frame.

Both are ports rather than direct calls, and that is what keeps the pass separable from the game: the
live binding reaches a core-UI draw entry point by name and writes to GL, neither of which exists
outside a running one, while whether a repaint happens, which region it is clipped to, and how a
failed draw is survived are decisions that hold anywhere.
`ReflectiveCoreUiComponentRepainter.INSTANCE` is the binding the plugin wires in.

The animations run either side of the layout, which is why the frame's elapsed time is read once and
spent on both sides: the fold has to advance *before* the placement, since it sizes it, and the input
motions - the hover fades, the press lifts of the tabs and of the body's cells, and the tabs' hotkey
blinks - *after* it, since what the pointer is on (a tab, a body control's cell, or the collapse
handle) is resolved against the very placement being drawn rather than latched from the last pointer
event. A latched hover goes stale whenever the panel moves under a still cursor, which the handle
feels most: the panel folds out from under a still pointer and the notch stays lit for a handle no
longer beneath it. The triggered motions need no placement at all - a click and a keypress have been
and gone - but ride the same call so one frame's time is charged to every motion, off one pair of
paces.

Both advance off `System.nanoTime()`, not campaign time: these screens are open on a paused game
where `advance()` does not tick, so a game-time delta would freeze a half-folded panel and a
half-lit tab alike. The frame clock is zeroed whenever the panel is hidden, so a re-open advances by
nothing rather than by the whole interval the screen was shut - and the input motions are dropped
with it, since a fade left part-way up (or a pulse left part-way through its cycle) has no elapsed
time to wind down on and would open the next session showing the tail of an interaction the player
never saw begin.

`SidebarInput` is a `CampaignInputListener` acting in `processCampaignInputPreCore` at priority
1000, because a render pass cannot consume events; consuming pre-core stops a click reaching the
screen. Key events route to the host only while `isPresentingTabsOf(placement)` - the panel's own
answer about the placement drawn, not a reading of its fold. The two part on a tab whose body is
empty: it lays out no box and no handle, so a fold left standing by another tab says nothing about
it, and gating on that fold would leave its row drawn in full but dead to every key, with no handle
to expand a body it does not have. Where nothing is drawn at all there is no placement to ask about
and the fold is all that is left, which costs nothing - a key press needs no placement, jumping to a
layer not depending on where the box landed. Off the gate it cancels any dangling drag, so a grab
left over from an overlay closing mid-drag cannot persist.

The same question gates a press on a tab and a tab's hover fade, so all three answer alike. What the
panel claims from the surface behind it is the drawn row plus the framed body plus the handle: the
row stands outside the box, so an event over it is swallowed there rather than falling through to the
map - which is the whole of what a bodyless tab blocks.

A pointer *move* is claimed differently, and the difference is the screen's to notice. A vanilla
control lets go of its hover only on hearing a move that is not on it, and a consumed event is
invisible to the screen, so consuming a move leaves whatever was lit when the pointer crossed onto the
sidebar lit for as long as the pointer rests there. The controller therefore claims a move by parking
the pointer - leaving the event unconsumed and moving it far off every widget - so the map and its
chrome alike conclude the pointer is on nothing of theirs. That claim belongs to KMLib's
`PointerParking` rather than to this listener, which only decides which events reach the controller at
all; presses and the wheel are still consumed outright, an act the sidebar answers being exactly what
the screen must not answer too.

`BaseSidebarHost.handleKeyPress` matches the press to a layer through `TabPanelHotkeys`, selects it,
and blinks that layer's tab. The blink is what tells the player the key landed: a keypress puts
nothing on screen at all, so an unblinked tab would read as a key the panel ignored. Both it and a
tab's press lift follow the press rather than the switch, so a shortcut for the layer already shown
still blinks and a press on the lit tab still lifts - an act the player made that answered with
nothing at all would read as a panel that missed it. A layer's tab sits at its place in the row this
screen is offered - `ScreenLayerTabs.resolveTabbedLayers`, which the layout and this walk both take -
so the index the binder matched is the index blinked, and a tab withheld from this screen answers no
key rather than switching to a layer nothing lit. That one read carries the player's own arrangement
too, so a row they reordered is walked in their order and a tab they took off the bar answers no key
either.

Neither pass has an error state: when a signal blocks the panel it is simply absent. That makes
`SidebarRenderer`'s deduped view-state log (host state, screen size, resolved box, opacity) the only
way to answer "why hidden" or "drawn where".
