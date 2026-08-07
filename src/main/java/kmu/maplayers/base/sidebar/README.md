# The map layer sidebar (`base/sidebar`)

The control box that carries the map layer tabs and the active layer's body controls. One panel
definition serves both screens that show the sector map; a `SidebarHost` supplies the per-screen
answers, and everything else - layout, paint, input - runs against that role.

Part of [map layers](../../README.md); see the
[mod README](../../../../../../../README.md) for project context.

## Index

- [Hosts: what differs per screen](#hosts-what-differs-per-screen)
- [Placement: one resolve, two consumers](#placement-one-resolve-two-consumers)
- [Drawing and input on a screen with no seam](#drawing-and-input-on-a-screen-with-no-seam)
- [Fold persistence](#fold-persistence)
- [Picker state](#picker-state)
- [Styling](#styling)
- [What is not here](#what-is-not-here)

## Hosts: what differs per screen

`SidebarHost` is the per-screen role: the gate, the anchor, the look, the framed edges, the panel
controller, the fold selection, the layer selection, and the view-state text. `BaseSidebarHost` holds
the plumbing common to both (controller, fold selection, layer selection, the per-load reseed, and
the shortcut jump), leaving each concrete host only the genuine differences.

| | `MapSidebarHost` | `IntelSidebarHost` |
| --- | --- | --- |
| Gate | `CampaignMapView.isSectorMapShowing()` | non-null `getMapVisorRect()` |
| Anchor | screen top-left, by the padding settings | the visor rect's top-left, flush left, below the top-padding setting |
| Height cap | bottom padding setting | the visor's bottom edge |
| Framed edges | `BoxEdge.ALL` | `TOP`, `RIGHT`, and `BOTTOM` until the box reaches the visor bottom |
| Tab band | `HEADER_BAND_HEIGHT` 19 | `HEADER_BAND_HEIGHT` 17 |
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

Consuming happens pre-core (see below), so a KM shortcut wins over whatever the screen underneath
binds to the same key. On the intel screen that matters: item action buttons bind `T`, `U`, and `G`,
and the tag filter uses `Q` and `Ctrl+S`. The defaults (`N`, `P`) avoid all of them, and the
LunaLib Keycode fields are the way out of any clash a mod's intel item introduces.

Both gates ask the same question and nothing beyond it - is there a live canvas under the panel. The
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

That roster is the whole of "every host": `KMU_ModPlugin` walks it for the per-load fold reseed and
for both listener registrations, which is why `restoreFoldFromSave` sits on `SidebarHost` rather than
only on `BaseSidebarHost`. A new screen is added to the roster and is reseeded, registered, and
answered for from that one edit.

Border edges are decided twice for one reason: `layoutBorderEdges()` is what the layout reserves
inset space for, `decideBorderEdges()` is what gets stroked. The left is dropped in both (reserving
it would gap the box off the visor); the bottom keeps its reserved inset but drops its stroke once
flush, within `BOTTOM_FLUSH_TOLERANCE`, so a shared border does not double the visor's own frame.

## Placement: one resolve, two consumers

`LiveSidebarPlacement` builds the placement from the live screen, the settings, and the calling
screen's `ActiveLayerSelection`. Both the render and the input pass resolve through it each frame
rather than caching or each computing its own: a settings change landing between the two passes
would otherwise move the drawn box out from under the hit-test.

The two entry points (`resolveMapPlacement`, `resolveIntelPlacement`) differ only in the anchor
padding; `computeIntelPadding` converts the visor rect into top-left-anchored padding, and is
package-private so the anchor maths is testable without a live sector. The `TabStyle` is the host's
and is injected, so the band a strip stands in stays with the rest of that host's look rather than
being half here. Both return `null` when the tab font cannot load - the face taken off that injected
style, so the tabs are snapped to the face they are painted in - since layout snaps tabs to measured
text; callers then draw and consume nothing.

The layer selector is a single `ControlSpec.Tabs` whose action selects the layer at the clicked
index, so the switch rides on the control and no tab callback is threaded through the input pass.
Each resolve also clamps the controller's stored scroll offset to the freshly laid-out overflow.

## Drawing and input on a screen with no seam

Both screens are vanilla core-UI surfaces with nowhere to attach a mod panel, so the panel is
painted in UI coordinates and its input claimed ahead of the screen.

`SidebarRenderer` is a `CampaignUIRenderingListener` drawing in `renderInUICoordsAboveUIAndTooltips`
- the only pass composited after the opaque core-UI screen, so the earlier passes are covered by the
screen itself. It gates on the host, advances the collapse, offers the settled fold, resolves the
placement, steps the panel's input motions against it, and hands off to KMLib's `TabPanelRenderer`.

The animations run either side of the layout, which is why the frame's elapsed time is read once and
spent on both sides: the fold has to advance *before* the placement, since it sizes it, and the input
motions - the hover fades, the tabs' press lifts, and their hotkey blinks - *after* it, since what
the pointer is on (a tab, or the collapse handle) is resolved against the very placement being drawn
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
the previous save's scroll offset in the same move.

## Picker state

A layer whose body carries a sortable, column-laid list needs somewhere to keep how that list is
ranked, wrapped, and filtered. `SortSelection`, `ColumnSelection`, and `FilterSelection` are those
stores, and `SortSelectionBinder`, `ColumnSelectionBinder`, and `FilterSelectionBinder` are what
tie each to the widget that changes it. Three stores and their three binders is the whole of this
half of the package.

The stores are leaves: they hold the raw stored keys and nothing that resolves one. What a
filtered-to id points at stays with the layer that offers the choices, and a stored sort or
column key only means something to the model that owns it, so this package carries the storage
without learning what any one layer's list holds.

The picker itself is not here at all - `ListPickerControl`, `SelectableListItem`, `ListPickerStore`,
`RevisionMemo`, and the sort and column model behind them (`ListSortMode`, `ListSortModes`,
`ListSort`, `SortDirection`, `ListColumns`, and the two selector controls) are KMLib's
(`kmlib.starsector.ui.widgets.lists`), since a sortable, column-laid, spotlight-picking list knows
nothing about a map. The split is that **KMLib owns the model, the composition, and the resolution
rule; KMU owns where the answer is kept.**

The keys are why the split falls where it does. They are the frozen `$kmu_political_*` spellings
below - save state this mod cannot move and a shared library has no business holding.

`FilterSelection` holds one selected id per opaque scope, so each scope keeps its own choice and
switching scopes neither clears nor cross-reads another's. Beyond the read, pick, and clear it
heals a stored id a caller-supplied predicate no longer accepts (for a save whose selection stopped
being on offer between sessions) and carries a pre-per-scope save's single shared slot into a scope
slot on load. Binding that predicate to a live source of what is selectable *now* is the reading
layer's, since the source is exactly the knowledge these classes refuse.

`FilterSelectionBinder` is the one binder that also builds, because the picker's three ties resolve
at one point: it reads the scope's spotlighted id on the way in, resolves the columns caption out of
this mod's strings, and routes each of the picker's three reported picks to the slot that keeps it -
the item pick to `FilterSelection` under that scope, the other two through the binders beside it. A
layer that composed the picker itself would have to name all three slots, which is exactly the
knowledge these binders exist to hold, so a calling layer hands over its items, its sort, its column
count, and whatever it pairs beside the sort selector, and names no store at all.

`SelectableBlocCache` (the political map's) is where a layer holds the resolved list between frames,
over KMLib's `RevisionMemo`; what invalidates it is the layer's own judgement. [The caching
notes](../../../../../../../docs/dev/caching.md) own that model in full.

| Key | Holds |
| --- | --- |
| `$kmu_political_sort_mode` | the picked sort mode's key, absent until first picked |
| `$kmu_political_sort_direction` | the picked direction (`asc` / `desc`), absent until first flipped |
| `$kmu_political_list_columns` | the picked column count's key, absent until first picked |
| `$kmu_political_filter_bloc_<scope>` | one scope's filtered-to id, absent while un-filtered |
| `$kmu_political_filter_bloc` | the pre-per-scope shared slot, read once by the load migration and retired |

The keys read as the political map's because this state shipped alongside it, before the framework
was carved out; like the fold keys they are save-serialised identities and frozen, so renaming one
would reset every existing save to the default.

The sort and column stores raise no refresh: both values are read on the per-frame body build, so
the next frame re-sorts or re-wraps on its own, and nothing on the map depends on either.
`FilterSelection` is the exception - its value changes what a layer paints, so a landed pick or
clear raises `MapLayerCommonRefreshSignal.FILTER`; the heal and the migration run on load before
anything paints, so they move values without raising it.

## Styling

A host's `resolveWidgetStyle()` answers with the look each frame, built through
`style/SidebarStyles` from live values: a black body fill faded by the opacity setting, the frame
colour, the dark, base, and bright accents of the player's colour scheme, the insignia body face, and
the host's own tab style. Nothing of it is held between frames - every shade reads the running game's
colours and the player's live settings - and nothing of it is persisted.

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

The look also says how the panel *sounds*, and the sidebar takes the engine's own scheme through
`SidebarStyles.SIDEBAR_SOUND_SCHEME`. It is the one part of the look the panel's controller is
handed directly rather than rebuilt each frame - the moments it answers are pointer events, not paint
passes - so both sides read that one constant.

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
whose body is empty is its row and nothing else - no frame, no handle. The row needs none: its fills
are opaque surfaces in their own right, so it reads the same over the body, over the bare map, or over
whatever the panel floats on.

The frame colour rides in `WidgetStyle`'s `BoxColours` beside the body fill, apart from the
`AccentColours` the controls wash and label with, so a host whose surrounding chrome is drawn in
another shade can match it without recolouring its controls. The map passes its scheme's base accent
for both, floating free with no neighbouring chrome to match; the intel panel frames itself in the
scheme's dark step, since its border abuts the visor's and the vanilla chrome there is framed in the
dark member of the three-colour set each of its controls is built from - two boxes sharing an edge in
two shades read as one dropped on the other. Its controls stay on the scheme's base either way -
which is the whole point of the frame being its own knob. The two framings therefore differ by which
step of one scheme they take, never by which palette.

That one `TabStyle` carries a strip end to end - band height, `TabPalette`, `HotkeyStyle`, and the
orbitron face - so the value the layout snapped tabs against is the value the renderer paints them
from and a snapped tab width cannot part from the text drawn into it. The palette holds both flavours
of tab paint: an absolute `TabLook` per `TabLookState` (unselected, selected, hovered) and a relative
`TabWash` per `TabWashState` (clicked), the pulse lifting whichever look the tab has settled on.

The three looks are not three shades but one at the engine's three glow amounts, worked out by
`VanillaTabFills` from the two settings colours a vanilla tab is painted with (`buttonBgDark` and
`buttonText`) rather than sampled off one - so a restyled install moves this strip exactly as it moves
the tabs above it, and the fills answer to settings and not to the player faction because vanilla's
own tabs take no faction colour. A tab rests unlit, the shown tab lights at `SELECTED_GLOW`, and the
tab under the pointer at the full `POINTED_GLOW`. That the pointer's is the brighter of the two lit
amounts is load-bearing: nothing else marks the shown tab, no bar capping it, so a pointed-at tab has
to outshine it rather than match it.

The **labels** are not lit at all. The engine parts a resting tab's text from a lit tab's by *colour*,
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

Hovering is a look rather than a lift because a tab lands on one shade whatever it was showing
before, which no fraction applied to each tab's own fill could produce. A tab travels onto that shade
rather than switching to it, paced by `HoverFade.DEFAULT_DURATIONS`.

A press rides the `clicked` wash up over whatever look the tab has settled on and **holds there until
the button comes up**, the way a vanilla tab does: a press is an act the player is still making, so
its length comes from the act rather than from a duration of ours, and only the rise and the fall are
paced. The release is unaimed - a press begun on a tab and let go over a neighbour, over the map, or
off the panel entirely still ends that tab's lift, because what it reported was the press. That wash
travels along the glow (the label colour half-way to white, `VanillaTabFills.resolveGlowColour`)
rather than toward white: the engine brightens a tab by adding its own glow, so a lift aimed at white
would be the one shade on the strip moving in a direction none of the fills do, and most visible
exactly when the player is looking at it.

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
chrome its shades are painted onto, and the hotkey convention that comes with that chrome; every
other value in the tab style is shared. Both faces are named through KMLib's `StarsectorFont` enum
rather than by atlas basename.

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
choice mean" rules stay a pure lookup a test can pin, with no live GL or screen needed. The handle
travels between those shades on the same fade the tabs use, so the gold choice - one colour passed
twice - answers a hover by its accent wash alone while the panel-accent choice brightens the glyph
with it; that choice resolves through whichever scheme is set, which is why the two rows sit together
on the settings screen.

The border width, opacity, collapse seconds, and both anchors' paddings are LunaLib fields read
through `kmu.settings.KmuMapLayerSettings`, as are the colour scheme and the chevron colour - those
two in their own "Overlay sidebar - colours" section below the box's dimensions.

## What is not here

The *panel widget itself* - frame, tab strip, scrollbar, collapse handle, control widgets, and the
`TabPanelController` that holds scroll, collapse, hover, and click-pulse state - is KMLib
(`kmlib.starsector.ui.widgets`, `.input`, `.render.gl`), as is the *spotlight picker* with its item
seam, its sort and column model, and the list memo behind it (`.widgets.lists`, see
[Picker state](#picker-state)); this package supplies only the wiring KMLib cannot know. The *layer
roster and each screen's active pick*, including the save migrations
behind them, are `base/layer`'s (`MapLayerRegistry`), summarised in
[map layers](../../README.md). The *body composition* the panel lays out belongs to whichever
layer is active - for the political map, [`politicalmap`](../../politicalmap/README.md) and its
`base/sidebar` controls - though it is here that KMLib's picker is bound to the save (see
[Picker state](#picker-state)). What the political map keeps of its own there is what the picker
refuses to know: which items are on offer, what invalidates that list, and the recede toggles it
pairs with the sort. Both listeners and the per-load reseed are registered in `KMU_ModPlugin`.
