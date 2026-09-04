# The map layer sidebar (`base/sidebar`)

The control box that carries the map layer tabs and the active layer's body controls. One panel
definition serves both screens that show the sector map; a `SidebarHost` supplies the per-screen
answers, and everything else - layout, paint, input - runs against that role.

Part of [map layers](../../README.md); see the
[mod README](../../../../../../../README.md) for project context.

## Index

- [Hosts: what differs per screen](#hosts-what-differs-per-screen)
- [Placement: one resolve, two consumers](#placement-one-resolve-two-consumers)
- [Drawing and input outside the widget tree](#drawing-and-input-outside-the-widget-tree)
- [Fold persistence](#fold-persistence)
- [Picker state](#picker-state)
- [Styling](#styling)
- [What is not here](#what-is-not-here)

## Hosts: what differs per screen

`SidebarHost` is the per-screen role: the screen gate, the anchor, the look, the framed edges, the
panel controller, the fold selection, the layer selection, and the view-state text. `BaseSidebarHost`
holds the plumbing common to both (controller, fold selection, layer selection, the per-load reseed,
the shortcut jump, and the composed live gate), leaving each concrete host only the genuine
differences.

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

Keys are not in that table because the panel offers the same tabs wherever it draws, so
`BaseSidebarHost.handleKeyPress` serves both: a bound key jumps that host's own pick to its layer
and is consumed, and any other key falls through. Because the pick is per-screen, a shortcut moves
only the tab of the screen it was pressed on.

Which key that is comes from the layer - `resolveShortcutKeycode`, asked per frame - rather than from a
settings read here, so the row hints and the key claim carry a layer whose rebinding lives in another
mod's settings file. A non-positive answer is unbound and both halves go inert for that layer: no hint
is printed and no press matches it, its tab keeping its place in the row. Nothing here supplies a key
in its place. The claim only compares numbers, but the hint indexes LWJGL's name table, which has no
range check of its own: `LiveSidebarPlacement` bounds the keycode before naming it, so one layer
answering nonsense costs itself a hint rather than taking the tab row down.

Consuming happens pre-core (see below), so a KM shortcut wins over whatever the screen underneath
binds to the same key. On the intel screen that matters: item action buttons bind `T`, `U`, and `G`,
and the tag filter uses `Q` and `Ctrl+S`. The shipped settings table seeds KMU's own two tabs on `N`
and `P`, which avoid all of them, and the LunaLib Keycode rows those layers read are the way out of
any clash a mod's intel item introduces.

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
The show-or-hide ramp splits the pair the same way and for the same reason,
the panel dissolving with the overlay it drives rather than cutting away from over it, and the two
fades multiply. Both compositions ask the claim first and the screen last, so the read that walks
live widgets is skipped while the panel is standing down anyway.

That the panel goes with the layers at all is folded in here rather than at the renderer and the
input listener separately, for the reason the pick is folded into the active-layer answer rather than
into each pass driven by it: the sidebar is part of what the layers put on a screen, so it leaves
with the rest of that footprint on one read taken where the gate already is. Which screen a host
reads is chosen once, in its constructor: `ScreenLayerPicks` carries that screen's tab and its hiding
together, so no host can be wired to one screen's tab and another's hiding.

`describeViewState()` carries the pick into the view-state log, prefixing the host's own screen state
with `layers hidden` once the ramp is out and `layers hiding` while it is running - different bug
reports, so they are worded apart. A host supplies only its screen's half, through
`describeHostScreenViewState()`.

What can claim the screen, and why any of it stands the panel down rather than being ordered above
it, is [`ScreenClaim`](runtime/ScreenClaim.java)'s to state. What belongs here is the rest of the
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
modal as `ModalDialogMapCover`, the codex as `CodexMapCover` - see [map layers](../../README.md) on
`base/hover/cover`, where all three sit beside the sidebar's.

Both screen gates ask the same question and nothing beyond it - is there a live canvas under the panel. The
visor rect is absent when the intel tab is not showing, when a sibling sub-tab (Planets, Factions)
holds the column, or when a large-description item has blanked the preview, so it is both gate and
anchor. Which look that canvas wears is not asked on either screen: the layers paint through
[several terrain surfaces](../render/README.md), two of which draw in Starscape mode, so the
overlay these controls drive is under them in either look.

Both are per-screen reads rather than the host-blind `MapPresence` seam KMLib offers, because each
host anchors its panel to its own screen - "a map is up somewhere" cannot place a box.

`IntelSidebarHost` reaches the concrete intel panel through KMLib's `IntelScreenView` seam, which
fails closed - an unresolvable link hides the sidebar rather than throwing on a live screen.

`SidebarHosts` is the roster, and where a question about "the sidebar" with no screen attached to it
is put to all of them: `isPointOverAnySidebar` answers whether a point in UI coordinates lands on a
live panel, for code reached through hooks that never name the screen that invoked them. It asks each
host's `isOverlayShowing()` before its placement, in that order, because only the intel host's
placement goes null off its screen - the on-map host hangs its panel from the screen corner and
resolves a box wherever it is asked. What the point is tested against is the placement's own
`containsPoint`, so the body-plus-notch footprint is KMLib's answer and not a second copy here.

That roster is the whole of "every host": `SidebarInstaller` walks it for the per-load fold reseed
and for both listener registrations, which is why `restoreFoldFromSave` sits on `SidebarHost` rather than
only on `BaseSidebarHost`. A new screen is added to the roster and is reseeded, registered, and
answered for from that one edit.

Border edges are decided twice for one reason: `layoutBorderEdges()` is what the layout reserves
inset space for, `decideBorderEdges()` is what gets stroked. The left is dropped in both (reserving
it would gap the box off the visor); the bottom keeps its reserved inset but drops its stroke once
flush, within `BOTTOM_FLUSH_TOLERANCE`, so a shared border does not double the visor's own frame.

The reserved left edge also decides where the tab row starts, since the row is laid at the body's
content edge rather than at the box's outer one - so the map row, framed on all four sides, stands
one border in, and the intel row, its left dropped, stands at the anchor. Both follow the one inset
rather than each being placed, which is why aligning the map row against the vanilla tabs above it
moved no intel pixel.

## Placement: one resolve, two consumers

`LiveSidebarPlacement` builds the placement from the live screen, the settings, and the calling
screen's `ActiveLayerSelection`. Both the render and the input pass resolve through it each frame
rather than caching or each computing its own: a settings change landing between the two passes
would otherwise move the drawn box out from under the hit-test.

The two entry points (`resolveMapPlacement`, `resolveIntelPlacement`) differ only in the anchor
padding; `computeIntelPadding` converts the visor rect into top-left-anchored padding, and is
package-private so the anchor maths stands apart from the sector read around it. The `TabStyle` is the host's
and is injected, so the band a strip stands in stays with the rest of that host's look rather than
being half here. Both return `null` when the tab font cannot load - the face taken off that injected
style, so the tabs are snapped to the face they are painted in - since layout snaps tabs to measured
text; callers then draw and consume nothing.

The layer selector is a single `ControlSpec.Tabs` whose action selects the layer at the clicked
index, so the switch rides on the control and no tab callback is threaded through the input pass.
Each resolve also clamps the controller's stored scroll offset to the freshly laid-out overflow.

## Drawing and input outside the widget tree

The panel is painted in UI coordinates and its input claimed ahead of the screen, rather than
attached to either screen as a mod panel of its own.

Not for want of a seam, which is worth stating because the arrangement below looks like one forced
on it. A `CustomPanelAPI` can be made a child of the vanilla map widget, and both draws over the map
surface and takes input there - confirmed in play, on both of that screen's tabs. Painting here is a
standing decision rather than a constraint: the toolkit and the hit-testing that goes with it exist
already, and moving onto that seam is backlogged. Whether the intel screen's own map behaves the
same way has not been tested.

`SidebarRenderer` is a `CampaignUIRenderingListener` drawing in `renderInUICoordsAboveUIAndTooltips`
- the only pass composited after the opaque core-UI screen, so the earlier passes are covered by the
screen itself. It gates on the host, advances the collapse, offers the settled fold, resolves the
placement, steps the panel's input motions against it, and hands off to KMLib's `TabPanelRenderer`.

Drawing last wins against the core UI's tooltips too, which the panel does not want: a tooltip the
cursor raises where the panel overlaps it is drawn underneath and reads as cut off at the panel edge.
So the same pass finishes by repainting it on top - `VanillaMapTooltipProbe` locates the tooltip the core
UI is showing, and KMLib's `CoreUiComponentRepainter` draws it again clipped to
`TabPanelPlacement.computeOuterBound`. The clip is the panel's footprint rather than the tooltip's,
so only the hidden part is drawn twice and the tooltip reads at one opacity across the panel edge. A
repaint that cannot be made (the read broke, or the draw threw) leaves the tooltip where vanilla drew
it and warns once a session, so the failure costs the occlusion it was there to fix and nothing more.

Both are ports rather than direct calls, and that is what keeps the pass separable from the game: the
live binding reaches a core-UI draw entry point by name and writes to GL, neither of which exists
outside a running one, while whether a repaint happens, which region it is clipped to, and how a
failed draw is survived are decisions that hold anywhere.
`ReflectiveCoreUiComponentRepainter.INSTANCE` is the binding the plugin wires in.

The animations run either side of the layout, which is why the frame's elapsed time is read once and
spent on both sides: the fold has to advance *before* the placement, since it sizes it, and the input
motions - the hover fades, the press lifts of the tabs and of the body's cells, and the tabs' hotkey
blinks - *after* it, since what
the pointer is on (a tab, a body control's cell, or the collapse handle) is resolved against the very placement being drawn
rather than latched from the last pointer event. A latched hover goes stale whenever the panel moves
under a still cursor, which the handle feels most: the panel folds out from under a still pointer and
the notch stays lit for a handle no longer beneath it. The triggered motions need no placement at all
- a click and a keypress have been and gone - but ride the same call so one frame's time is charged
to every motion, off one pair of paces.

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
nothing at all would read as a panel that missed it. A layer's tab sits at its
registry index, the tabs row being built from the same registry in the same order, so the index the
binder matched is the index blinked.

Neither pass has an error state: when a signal blocks the panel it is simply absent. That makes
`SidebarRenderer`'s deduped view-state log (host state, screen size, resolved box, opacity) the only
way to answer "why hidden" or "drawn where".

## Fold persistence

`SidebarFoldSelection` is where a fold is read from and recorded to; `PersistedSidebarFold` is the
sector-memory implementation, one instance per host with its own key and opening default.

| Key | Holds |
| --- | --- |
| `$kmu_political_map_sidebar_docked` | `MapSidebarHost`'s resting fold, default expanded |
| `$kmu_political_intel_sidebar_docked` | `IntelSidebarHost`'s resting fold, default docked |

Both keys are save-serialised identities and frozen once shipped; renaming one returns every
existing save to that host's default.

Only a settled end is recorded. `SidebarRenderer.resolveSettledFold` reads the collapse fraction and
the fully-expanded flag and yields `null` mid-fold, so the in-flight animation is never written.
`recordFold` compares against sector memory itself rather than a cached value - a cache would still
hold the previous save's fold and read the new save's first frame as a change, and a write attempted
before the sector exists is simply dropped and retried next frame. A save that has never been folded
is left untouched, so the key appears only once the player moves the panel.

Hosts are process-lifetime singletons, so `BaseSidebarHost.restoreFoldFromSave` replaces the
controller per load with one constructed at the stored fold. Replacing rather than mutating avoids
reaching into the collapse animation (the two ends are the widget's own two constructors) and clears
the previous save's scroll offset in the same move. It is also where the panel's sound scheme is
composed from the player's levels, the seed built at class load having no settings mod to read yet -
see [Styling](#styling).

## Picker state

A layer whose body carries a sortable, column-laid list needs somewhere to keep how that list is
ranked, wrapped, and filtered. `SortSelection`, `ColumnSelection`, and `FilterSelection` are those
stores, and `SortSelectionBinder`, `ColumnSelectionBinder`, and `FilterSelectionBinder` are what
tie each to the widget that changes it. `FilterHoverSlot` stands beside them holding where the
pointer rests rather than what was picked - not a fourth store, since what it holds belongs to a
sector rather than to a save. Three stores, three binders and the hover slot is the whole of this
half of the package.

The stores are leaves: they hold the raw stored keys and nothing that resolves one. What a
filtered-to id points at stays with the layer that offers the choices, and a stored sort or
column key only means something to the model that owns it, so this package carries the storage
without learning what any one layer's list holds.

The picker itself is not here at all - `ListPickerControl`, `SelectableListItem` (an id, a label, a
crest, and whether the row reads back), `ListPickerStore`, `RevisionMemo`, and the sort and column
model behind them (`ListSortMode`, `ListSortModes`, `ListSort`, `ListPicker`, `SortDirection`,
`ListColumns`, and the two selector controls) are KMLib's (`kmlib.starsector.ui.widgets.lists`),
since a sortable, column-laid, spotlight-picking list knows nothing about a map. The split is that
**KMLib owns the model, the composition, and the resolution rule; KMU owns where the answer is
kept** - a row states *that* it reads back, KMLib decides how far back that reads.

The keys are why the split falls where it does. They are the frozen `$kmu_map_*` spellings below -
save state this mod cannot move and a shared library has no business holding.

`FilterSelection` and `SortSelection` both hold their answer per opaque scope, so each scope keeps
its own and switching scopes neither clears nor cross-reads another's. The filter is scoped because
an id read under the wrong scope names nothing; the sort because scopes rank their rows by different
vocabularies, so a shared mode key would resolve against nothing under half of them and make every
switch look like a reset. `ColumnSelection` stays one shared slot: how many columns a list wraps
across is a layout preference, not a statement about what the list holds, so it means the same thing
under every scope.

Beyond the read, pick, and clear, `FilterSelection` heals a stored id a caller-supplied predicate no
longer accepts - a selection that stopped being on offer, whether between sessions or while the game
runs. Binding that predicate to a live source of what is selectable *now* is the reading layer's,
since the source is exactly the knowledge these classes refuse; so is deciding at which moments the
offer can have moved. The predicate is asked only when a stored id is there to judge, so binding it
to an expensive source costs nothing on a scope holding no pick.

`FilterHoverSlot` is the transient counterpart: the same per-scope shape, holding the id a pointer
rests on instead of the id that was picked, so a reading layer can preview what picking it would
spotlight. It persists nothing and raises no refresh, and both follow from what a hover is - a place
the pointer happens to be this frame, previewed over paint that is already on the map, where a pick
has the reading layer rebuild everything it draws.

Which is why it is the one thing here that is not stored at all. A hover names a bloc one sector's
walk surfaced and is read back against that sector's presence, so it is a fact about a sector rather
than about a save or the process - and it is held by that sector's `MapLayerInstallation`, resolved
through `FilterHoverSlot.resolveHoverSlotIn`. A load disposes the installation and the hover goes
with it, so no key has to be healed and no discard written; what stays the caller's is clearing
within a sector's life, at the pointer leaving a row and at a panel standing down without a leave
ever being reported, since neither is visible from here.

`FilterSelectionBinder` is the one binder that also builds, because the picker's three ties resolve
at one point: it reads the scope's spotlighted id and the scope's stored sort on the way in,
resolves the columns caption out of this mod's strings, and routes each of the picker's three
reported picks to the slot that keeps it - the item pick to `FilterSelection` under that scope, the
other two through the binders beside it, the sort under that same scope. The row the pointer rests
on routes the same way, into `FilterHoverSlot` under that scope, and it is the one report that
raises nothing and persists nothing: a preview is drawn over paint already on the map. One scope
covers every one of those answers, so a layer cannot bind its filter, its sort and its preview to
different slots. A layer that composed
the picker itself would have to name all three slots, which is exactly the knowledge these binders
exist to hold, so a calling layer hands over its `ListPicker`, its column count, and whatever it
pairs beside the sort selector, and names no store at all.

The sector arrives as the whole `MapLayerInstallation` rather than as the refresh board alone,
because two of those writers are that sector's: the board an item pick repaints through, and the
hover slot a previewed row is recorded in. Handed over side by side they would be two chances to
pair one sector's board with another's slot; derived from one installation at the build they cannot
disagree. Both are taken *at the build* rather than when a report lands, since a build runs while
the sector is live and a report can arrive after a load has disposed it - and asking a disposed
installation for machinery quietly makes a second copy that answers for a sector nothing draws.

The picker arrives wildcarded (`ListPicker<?>`), because what a layer ranks its rows by is that
layer's own, and this is where the wildcard is captured - once for the mod, rather than in each
layer, since the capture needs both the selection slot and the sort binder to finish the job. The
stored sort is resolved here for the same reason: resolving it needs the vocabulary, which only
arrives inside the bundle. An offers-nothing picker is answered before that resolution, since it
carries no fallback mode to land on.

`SelectableBlocCache` (the political map's) is where a layer holds its resolved picker between
frames, over KMLib's `RevisionMemo`; what invalidates it is the layer's own judgement. It is held
the way the hover slot above is, by the sector's `MapLayerInstallation` through
`SelectableBlocCache.resolveBlocCacheIn`. [The caching
notes](../../../../../../../docs/dev/caching.md) own that model in full.

| Key | Holds |
| --- | --- |
| `$kmu_map_sort_mode_<scope>` | one scope's picked sort mode key, absent until first picked |
| `$kmu_map_sort_direction_<scope>` | one scope's picked direction (`asc` / `desc`), absent until first flipped |
| `$kmu_map_list_columns` | the picked column count's key, absent until first picked |
| `$kmu_map_filter_bloc_<scope>` | one scope's filtered-to id, absent while un-filtered |

The prefixes are layer-neutral because every map layer's picker stores through these classes - one
naming a layer would have every other layer persisting under it. Like the fold keys they are
save-serialised identities and frozen, so renaming one would reset every existing save to the
default.

The sort and column stores raise no refresh: both values are read on the per-frame body build, so
the next frame re-sorts or re-wraps on its own, and nothing on the map depends on either.
`FilterSelection` is the exception among the persisted stores - its value changes what a layer
paints, so a landed pick or clear raises `MapLayerCommonRefreshSignal.FILTER`; the heal runs on load
before anything paints, so it clears without raising it.

## Styling

A host's `resolveWidgetStyle()` answers with the look each frame, built through
`style/SidebarStyles` from live values: a black body fill faded by the opacity setting, the frame
colour, the dark, base, and bright accents of the player's colour scheme, the wash the pointer lifts
a body control by and the light a press lifts it further by, the insignia body face, and
the host's own tab style. Nothing of it is held between frames - every shade reads the running game's
colours and the player's live settings - and nothing of it is persisted.

The opacity setting fades the body and its chrome, not the words. A translucent panel exists so the
map shows through the pane, not so the reading is half-composited with whatever happens to be behind
it, and the cost falls hardest on text carrying a colour of its own: a value in a relation's shade
gives up that shade toward the backdrop while the greys it is meant to be told apart from barely
move, so a faded reading does not merely dim, it flattens the distinctions the colour was for. The
words still leave with the panel - `PanelAlpha` keeps the look's translucency and the panel's arrival
apart, and the text takes the second alone. That is why the setting floors at 50%: the body is what
the words are read against, and one faded much past half leaves solid text standing on open map.

The hover wash is that same base accent at a lesser strength than the selected wash carries, so a
control under the pointer reads as lit without reading as picked, and a lit one under the pointer
lifts past both. It is a shade only: how fast a cell travels onto it is the panel's one input pace,
shared with the tabs and the handle, because a panel answering the pointer at two speeds reads as
two panels.

The press light is the bright step of that same accent, composed here for the same reason the wash is
and spent over it rather than in place of it. Both come from one resolved set, so the panel's answer
to a click is more of what the control already wears; why it is a second channel at all is the
library's own, stated where the treatment is named.

Which palette those accents come from is the player's `SidebarColourSchemeChoice`, and it is one
choice for all three of the panel's colour reads - the frame, the control accents, and the tab row's
chrome rule. They move together because a panel framed in one palette and ruled in another reads
worse than either of the looks it is made of, which is what per-colour knobs would offer as a
combination. The default scheme is the fixed UI palette (`buttonBgDark`, `buttonText`, and the
near-white tooltip title above it) rather than the player faction's own three: the sidebar is drawn
among vanilla chrome, every scrap of which answers to that palette whatever faction the player flies,
so a faction-coloured panel is the one thing on the screen that moves when nothing around it does. On
a stock install the two resolve to the same shades and the choice shows itself only once a faction
recolours - which is the case it exists for. `Chrome grey` is the third, dropping the accent hue
entirely; it is also the one scheme with no engine dark to take, since the fixed palette's dark role
is the button teal, so it steps its own grey down instead.

The look also says how the panel *sounds*, and `SidebarStyles.buildSidebarSoundScheme` composes that:
the engine's own roles - a vanilla button's press and its mouseover, and the id vanilla scrolls its
own readouts with - at the levels the player set on the `Map - Sound` settings tab. The roles are
vanilla's because the panel is drawn among vanilla chrome; the levels are not, because what the engine
mixed its mouseover for is a screen carrying a handful of hit targets and this panel packs a column of
them. One level per kind of thing the pointer can reach - the panel's own furniture, a control with
one answer to give, one of many alike - pitched so a sweep crossing a listed column does not chatter.
A balance pulled to nothing everywhere composes a scheme naming no arrival role at all, silence being
something a look states rather than a cue played at zero.

The wheel moving the list carries a level of its own beside that balance rather than inside it, and
is silenced the same way by its own slider alone. It is not one of the kinds above because nobody
reached anything - the content moved instead - and it answers the movement whole: one turn of the
wheel, one sound, however many rows went past the cursor. The press is the one moment with no slider
behind it, keeping the engine's own level, being a single act the player asked for.

It is the one part of the look the panel's controller is handed directly rather than reading off the
widget style each frame - the moments it answers are pointer events, not paint passes. The controller
therefore takes a scheme composed at the moment it is built: the constructor's seed takes the
library's own balance, being built during class initialisation where reaching for the settings mod
would tie this mod's load order to it and being a controller nothing can be heard through, and
`restoreFoldFromSave` composes from the sliders for the controller a player actually reaches.

Both hosts build through that one file rather than each spelling its look out, so the parts the two
screens share cannot drift into two spellings of them. Where they do differ, they choose between
named factories rather than passing the difference as an argument: `buildStripTabStyle` /
`buildRaisedButtonTabStyle` for the tab row, `buildAccentFramedStyle` / `buildChromeFramedStyle` for
the panel around it. The differences are vanilla conventions and not free choices - a strip's key is
underlined and a button's is bare - so naming the two ends is what keeps a host from composing a look
vanilla has no counterpart for. The file sits beside `SidebarPalettes` and out of the render pass for
the same reason: a look is a value, so building one needs no live GL context.

That fill is the body's alone. The tab row stands *on* the framed box rather than inside it, the way
a strip of tabs sits on the panel it selects, so nothing of the body reaches behind the tabs and a tab
whose body is empty is its row and nothing else - no frame, no handle. The row needs none, and each
chrome carries its own surface to do without one: the strip's fills are opaque in their own right,
and a raised button stands on a black backing at three-quarter alpha that its own chrome lays down.
Either way the row reads the same over the body, over the bare map, or over whatever the panel floats
on - which is the requirement, the two chromes meeting it differently being the point of their being
two.

The frame colour rides in `WidgetStyle`'s `BoxColours` beside the body fill, apart from the
`AccentColours` the controls wash and label with, so a host whose surrounding chrome is drawn in
another shade can match it without recolouring its controls. The map passes its scheme's base accent
for both, floating free with no neighbouring chrome to match; the intel panel frames itself in the
scheme's dark step, since its border abuts the visor's and the vanilla chrome there is framed in the
dark member of the three-colour set each of its controls is built from - two boxes sharing an edge in
two shades read as one dropped on the other. Its controls stay on the scheme's base either way -
which is the whole point of the frame being its own knob. The two framings therefore differ by which
step of one scheme they take, never by which palette.

That one `TabStyle` carries a strip end to end - band height, `TabBox`, `TabPalette`, `HotkeyStyle`, and
the face - so the value the layout sized tabs against is the value the renderer paints them
from and a tab's width cannot part from the text drawn into it. That holds for the size as well
as the atlas: `TabsControlLayout.layoutHeaderControl` measures at the size its style names, keeping
`TAB_FONT_SIZE` as the baseline for a body tabs row, which carries no style.

The `TabBox` is what decides whether a label is measured at all. The strip stands its tabs in the sector
map's own box - 130 x 18, neighbours parted by a pixel, inside a band that reserves one more than the tab
is tall for the line the row sits on - so the row spans the same width whatever its tabs say and a renamed
tab moves nothing. The raised-button row stays `TabBox.SNAPPED`, its buttons being laid inside the tabs the
layout measured and taking their own channel from within them. A parted row rules no seams: a divider marks
where two tabs meet, and parted tabs never do. What it paints there instead is its own backing - vanilla's
row shows a dark panel through that pixel, and a strip floating over the map would show the map through it.
The palette holds that backing plus both flavours
of tab paint: an absolute `TabLook` per `TabLookState` (unselected, selected, hovered) and a relative
`TabWash` per `TabWashState` (clicked), the pulse lifting whichever look the tab has settled on.

A palette is the chrome's own, built by the factory named for it - `createMapTabPalette` for the strip,
`createRaisedButtonPalette` for the buttons. The two chromes cannot share one, because the engine's own
tab and its own button are painted from different colours by different rules: a tab from the button
roles in settings, taking no faction tint at all, and a button from the whole three-step accent and
nothing else. A single palette worn by both would have one of them copying shades its vanilla
counterpart never wears, which is exactly what the intel row did while it was built to a description
of that screen rather than to its source.

The **strip's** three looks are not three shades but one at the engine's three glow amounts, worked out
by `VanillaTabFills` from the two settings colours a vanilla tab is painted with (`buttonBgDark` and
`buttonText`) rather than sampled off one - so a restyled install moves this strip exactly as it moves
the tabs above it, and the fills answer to settings and not to the player faction because vanilla's
own tabs take no faction colour. A tab rests unlit, the shown tab lights at `SELECTED_GLOW`, and the
tab under the pointer at the full `POINTED_GLOW`. That the pointer's is the brighter of the two lit
amounts is load-bearing: nothing else marks the shown tab, no bar capping it, so a pointed-at tab has
to outshine it rather than match it.

Its **labels** are not lit at all. The engine parts a resting tab's text from a lit tab's by *colour*,
switching between two of its own roles rather than brightening one: `buttonText` (170, 222, 255), the
blue every button's text is, while the tab is untouched, and `standardTextColor` (220, 220, 220), the
grey the rest of the interface reads in, once the tab is shown or pointed at. Both lit states take the
grey; their fills already stand at different glows, so that is what tells them apart.

That was arrived at by measurement rather than by reasoning, after two rules derived from the fill's
glow both came out wrong on screen. A lit vanilla tab's label samples at `#a0b1bc`, and solving for
the glyph's coverage over the fill beneath gives three irreconcilable answers against `buttonText`
(0.91 / 0.59 / 0.47) and one consistent answer against `standardTextColor` (0.62 / 0.60 / 0.65). A
computed label was the error both times: brightening a colour that already sits at 255 blue can only
push it sideways into cyan, and no amount tuned into that rule would have escaped it. Only the shown
tab's label is measured; the pointed-at one taking the same grey is the smaller claim, pinned equal in
KMLib so a divergence has to be deliberate.

A faction-tinted label, which is what this replaced first, would additionally have recoloured the
strip with the player's faction where the engine's own tabs take no faction colour at all.

The **buttons** invert that shape: theirs is a constant frame around a changing interior, where a tab is
a fill that moves whole. Every button carries the same two hairlines - the outer in the accent's dark
step, the inner in black - over the backing, whatever state it is in, and only the interior quad inside
them answers to the look. That is the engine's own arrangement, and mistaking it is what made the first
pass read as a foreign box: an outline that brightened with its fill was the one thing on the row moving
that the row it was drawn to match holds still.

Their two settled interiors come from `VanillaButtonFills`: the button being shown wears the dark step
composited onto the backing, and a resting one wears *nothing* - that same shade at zero alpha, so the
state is a value the palette states rather than a quad the paint pass learns to skip. Their labels take
the accent's base step untouched and its bright step once shown. There is no third interior, because
what the pointer does is not a shade a button settles on: it adds **the base accent** at `POINTED_GLOW`
(0.17) over whichever of the two the button is wearing, which is why it lives on the palette's
`TabHover` and not among its looks. The weight is fitted rather than read - the engine's own constant
sits behind an obfuscated widget - but fitted twice, from two pairs each sampled within one frame: an
unshown vanilla button over a flat map fill moves `#1b1d1b` to `#364144` (+27, +36, +41, so 0.161), and
the shown one moves `#17424f` to `#346a7c` (+29, +40, +45, so 0.176). Both sets of deltas carry the base
accent's own proportions rather than the equal channels white would add. The bound key's gold moves by
that identical +27, +36, +41, which says it is one light over the whole button rather than a fill rule.

The black under them is read the same way: over that flat `#505850` fill an unshown button's interior
samples `#1b1d1b`, which is the fill kept at 0.337 on every channel - untinted, so what vanilla lays
there is plain black at about two thirds and nothing else. `BACKING_ALPHA` takes that 0.665, and it is
why the map still shows through an unshown button rather than the row reading as a bar laid across the
screen.

That resting interior is why a look's alpha became load-bearing. Every fill on the strip is opaque, so a
fade between two looks had never had to carry alpha; `TabLook.computeBlendedLook` now interpolates all
four channels through `Colours.blendTowards`, which is a no-op on the strip only for as long as
`VanillaTabFills` keeps returning opaque shades.

What a pointer does is the chrome's own rule, stated on the palette as a `TabHover`, and the two rules
reach the screen differently. A strip's tabs **converge**: both the resting and the shown tab land on one
named shade, which no fraction applied to each tab's own fill could produce, and which is what lets a row
marking selection by fill alone still light whatever the pointer is on. A button row's do not: the engine
**adds light** - its own base accent - over the finished button, so the shown button and an unshown one
light by the same amount from different places and stay as far apart as their settled shades left them.

Added over the top rather than mixed into the fill, because a button's interior is not always painted. A
shade blended into a surface arrives diluted by however much of that surface is actually there, so the
same amount would read at full strength on the shown button and at a fraction of it on an unshown one -
where the engine's own two move by the same step. Drawn additively, both do, and so does the label the
pass crosses. It stops at the interior: the two hairlines are the part of a button that never moves, and
light spilling onto them would shift the very edge the constant frame exists to hold still.

Which is why the palette answers on two channels and each rule uses one - a shade rule adds no light, a
light rule leaves the look alone - so a tab is never brightened twice. Either way it travels onto its lit
state rather than switching to it, paced by `HoverFade.DEFAULT_DURATIONS`.

A press rides the `clicked` wash up over whatever look the tab has settled on and **holds there until
the button comes up**, the way a vanilla tab does: a press is an act the player is still making, so
its length comes from the act rather than from a duration of ours, and only the rise and the fall are
paced. The release is unaimed - a press begun on a tab and let go over a neighbour, over the map, or
off the panel entirely still ends that tab's lift, because what it reported was the press. On the strip
that wash travels along the glow (the label colour half-way to white,
`VanillaTabFills.resolveGlowColour`) rather than toward white: the engine brightens a tab by adding its
own glow, so a lift aimed at white would be the one shade on the strip moving in a direction none of the
fills do, and most visible exactly when the player is looking at it.

On the intel screen that press **paints nothing**, by the same imitation: vanilla's tab headers hold a
lit shade while the button is down, its intel buttons take no press state at all and answer a click with
their sound alone. So the raised-button palette states its `clicked` lift at zero strength rather than
having the chrome opt out of the channel. The envelope still runs - the press sound is gated on there
having been a held lift to let go of, not on the wash having painted anything - so an invisible press is
still a press that sounds, and it comes out that way precisely because the controller cannot see the
palette and so cannot skip a pulse it would paint nothing with. The two screens therefore swap which
gesture carries the feedback: the map's click leads and its blink echoes, intel's click is silent and
its blink carries the press. Neither moves channel for it.

`HoverFade.DEFAULT_DURATIONS` is a pair rather than one value, and the two halves are not equal: a tab
arrives at the shade it is heading for in half the time it takes to let go of one. A rise answers
something the player just did and has to land under the gesture that asked for it, while a fall
answers nothing and reads better unhurried - at equal paces the whole motion feels like the slower
half. Every *travel* the panel makes takes that pair, so the tabs, the notch, and the two ends of a
press lift cannot end up at different rhythms.

The press is the one motion whose *length* is not ours to set. Its rise and its fall take the pair
like everything else, but between them it waits on the button, so a press held for a second lasts a
second. That is the point of it: the lift reports an act the player is still making, and a duration
of ours would end it while they were still making it.

A bound key's blink takes no wash of its own: it carries its tab onto that same hovered shade and
back, so it rides the look channel with the hover and the two compose by the greater of them - which
is why a key pressed for the tab already under the pointer shows nothing, the blink reaching only
where the hover already stands. It is the one motion off that shared pair, running at
`TabPanelController.HOTKEY_BLINK_DURATIONS`: a blink is a strike rather than a travel, confirming a
key pressed away from the panel, so it lands and is gone however leisurely the rest of the panel
moves. Paced with the travels it reads as one more thing moving at the speed everything else moves
at, which is the opposite of what a keypress needs to say. All three animations are the controller's, which holds no colour: it
reports two fractions per tab - one look, one lift - and the paint pass binds them to the palette, so
it is handed a look already blended and a lift already scaled - so both tab chromes animate alike,
neither of them having any timing to compute. What the two screens set apart is the band height, the
chrome its shades are painted onto, and the four things that travel with that chrome because vanilla
keeps them together: the palette its shades come from, the face it is lettered in, the ring around
that face, and the hotkey convention. The timings, the
channels, and the order they resolve in are shared, which is why one animator serves either row. Every face is named through KMLib's `StarsectorFont` enum
rather than by atlas basename.

The strip is lettered in `VANILLA_ORBITRON_20AA` scaled to the tab size, the face the vanilla tabs it
sits beneath are set in; the buttons in `VANILLA_VICTOR_10` at that atlas's own size, the pixel face
the intel screen's map toggles are set in. A pixel face is crisp at one size only, so the button row
takes its native 9 - the line height its atlas states, not the 10 its name carries - rather than the
strip's 15; scaled, it would read as a blurred copy of the row it was drawn to match. Its capitals come with the atlas: every glyph sits on the same 5x5 cell with
lowercase included and no descenders, so a mixed-case label needs no upper-casing pass and the width
it is snapped to is the width it draws at.

The ring comes with the face. `victor10`'s atlas is hard pixels - every one of them fully on or fully
off, with no anti-aliased edge - so over a live visor its strokes have nothing but the map to read
against. The buttons take `TextHalo.createBlackHairline()`, a black copy laid a pixel out on each of
the four sides at half strength, and the strip takes `TextHalo.NONE`, a smooth face at size having
weight enough and reading muddier for a ring around it. A ring rather than a drop shadow: an offset
copy falls to one side, announcing a light source the flat chrome has none of, and leaves the opposite
edge as bare as it found it. `TabLabelRenderer` lays the whole group down once per side and then in
place, shifting the box the runs centre in rather than each draw site, so the key's underline travels
with the text it marks; the runs are separate single-colour drawables, which is what lets one resolve
serve every pass and what makes their colour a live draw-time knob rather than something baked into a
run. The ring's shade is set once for all four copies, and the key's underline reads its colour off the
key glyph it marks rather than off the style, so no copy can be laid down in two colours.

Where a tab says which key it answers to is `TabShortcutText`'s call, following the engine's rule: a
single-glyph key whose letter already stands in the label lights that letter where it is, and only a
key with nowhere to land is spelt out after it as `Label  [K]`. It answers in runs, and both passes
read that one answer - the layout measures the runs end to end, the paint pass walks them and colours
each - so a tab cannot be sized for one presentation and drawn in the other. Both of this mod's tabs
light in place (the N of "No Layer", the P of "Political Map"), so neither carries a bracketed key.

Each screen takes the `HotkeyStyle` its chrome's vanilla counterpart uses, which is why each tab
factory hands out the pair rather than taking the convention as an argument. The map's strip takes `createUnderlined()`: the vanilla Sector/System
tabs it sits below mark their key wherever it falls, so a key marked by colour alone reads as a
mismatch against the row above. The intel screen's buttons take `createPlain()`, its own map toggles
lighting their key by colour and nothing else. The line is a quad the style places under the key's
drawn box, not part of the measured display string, so drawing it or not moves no tab. The gold is
`buttonShortcut` under either, the role the engine's own buttons light their keys with, rather than
the prose-highlight `hColor` the stock install happens to give the same value.

`style/SidebarPalettes` maps both of the player's colour choices to shades: the
`SidebarColourSchemeChoice` to the panel's three accent steps, and the `NotchChevronColourChoice` to the
chevron's resting and lit shades. It is kept out of the renderer so the "which colour does this
choice mean" rules stay a pure lookup, with no live GL or screen needed. The handle
travels between those shades on the same fade the tabs use, so the gold choice - one colour passed
twice - answers a hover by its accent wash alone while the panel-accent choice brightens the glyph
with it; that choice resolves through whichever scheme is set, which is why the two rows sit together
on the settings screen.

Each chrome also carries how hard its labels read. Both rows are lettered in atlases that carry no
antialiasing of their own, which the font loader hands over interpolated, so each is drawn between the
two samplings and where between is a look rather than a number. It is one knob per row, not one for the
panel: each is read against the vanilla chrome it stands beside - the intel row against that screen's
raised buttons, the map row against the Sector/System tabs a tab-height above it - so a single value
would always be wrong for one of them. The amount rides on the `TabStyle` like the face and the ring do,
rather than being pushed into the draw pass, which is what keeps the two rows from sharing one.

The border width, opacity, collapse seconds, and both anchors' paddings are LunaLib fields read
through `kmu.settings.KmuMapLayerSettings`, as are the colour scheme and the chevron colour - those
two in their own "Overlay sidebar - colours" section below the box's dimensions. The three arrival
levels and the list-scroll level are fields of the same class on the `Map - Sound` tab, which is
where the volume half of the look is stated rather than beside the shades.

## What is not here

The *panel widget itself* - frame, tab strip, scrollbar, collapse handle, control widgets, and the
`TabPanelController` that holds scroll, collapse, hover, and click-pulse state - is KMLib
(`kmlib.starsector.ui.widgets`, `.input`, `.render.gl`), as is the *spotlight picker* with its item
seam, its sort and column model, and the list memo behind it (`.widgets.lists`, see
[Picker state](#picker-state)); this package supplies only the wiring KMLib cannot know. The *layer
roster and each screen's active pick*, including the save migrations
behind them, are `base/layer`'s (`MapLayerRegistry` and `MapLayerScreens`), summarised in
[map layers](../../README.md). The *body composition* the panel lays out belongs to whichever
layer is active - for the political map, [`politicalmap`](../../politicalmap/README.md) and its
`base/sidebar` controls - though it is here that KMLib's picker is bound to the save (see
[Picker state](#picker-state)). What the political map keeps of its own there is what the picker
refuses to know: which items are on offer, what invalidates that list, and the recede toggles it
pairs with the sort. Both listeners and the per-load reseed are registered in `SidebarInstaller`.
