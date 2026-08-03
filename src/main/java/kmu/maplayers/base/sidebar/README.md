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

`SidebarHost` is the per-screen role: the gate, the anchor, the framed edges, the panel controller,
the fold selection, the layer selection, and the view-state text. `BaseSidebarHost` holds the
plumbing common to both (controller, fold selection, layer selection, the per-load reseed, and the
shortcut jump), leaving each concrete host only the genuine differences.

| | `MapSidebarHost` | `IntelSidebarHost` |
| --- | --- | --- |
| Gate | `CampaignMapView.isSectorMapWithStarscapeOff()` | non-null `getMapVisorRect()` and `!isMapStarscapeModeOn()` |
| Anchor | screen top-left, by the padding settings | the visor rect's top-left, flush left, below the top-padding setting |
| Height cap | bottom padding setting | the visor's bottom edge |
| Framed edges | `BoxEdge.ALL` | `TOP`, `RIGHT`, and `BOTTOM` until the box reaches the visor bottom |
| Fold default | expanded | docked |

Keys are not in that table because the panel offers the same tabs wherever it draws, so
`BaseSidebarHost.handleKeyPress` serves both: a bound key jumps that host's own pick to its layer
and is consumed, and any other key falls through. Because the pick is per-screen, a shortcut moves
only the tab of the screen it was pressed on.

Consuming happens pre-core (see below), so a KM shortcut wins over whatever the screen underneath
binds to the same key. On the intel screen that matters: item action buttons bind `T`, `U`, and `G`,
and the tag filter uses `Q` and `Ctrl+S`. The defaults (`N`, `P`) avoid all of them, and the
LunaLib Keycode fields are the way out of any clash a mod's intel item introduces.

Both gates ask the same question - is there a live canvas under the panel. The visor rect is absent
when the intel tab is not showing, when a sibling sub-tab (Planets, Factions) holds the column, or
when a large-description item has blanked the preview, so it is both gate and anchor. Starscape
mode is the second half on either screen: the game paints the starfield in place of the map and
suppresses the terrain layers the political overlay rides. Each screen carries its own starscape
flag, so the intel host reads the visor's, not the campaign map's.

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
padding and the `TabStyle` tab-band height; `computeIntelPadding` converts the visor rect into
top-left-anchored padding, and is package-private so the anchor maths is testable without a live
sector. Both return `null` when the tab font cannot load, since layout snaps tabs to measured text;
callers then draw and consume nothing.

The layer selector is a single `ControlSpec.Tabs` whose action selects the layer at the clicked
index, so the switch rides on the control and no tab callback is threaded through the input pass.
Each resolve also clamps the controller's stored scroll offset to the freshly laid-out overflow.

## Drawing and input on a screen with no seam

Both screens are vanilla core-UI surfaces with nowhere to attach a mod panel, so the panel is
painted in UI coordinates and its input claimed ahead of the screen.

`SidebarRenderer` is a `CampaignUIRenderingListener` drawing in `renderInUICoordsAboveUIAndTooltips`
- the only pass composited after the opaque core-UI screen, so the earlier passes are covered by the
screen itself. It gates on the host, advances the collapse, offers the settled fold, resolves the
placement, and hands off to KMLib's `TabPanelRenderer`.

The collapse advances off `System.nanoTime()`, not campaign time: these screens are open on a paused
game where `advance()` does not tick, so a game-time delta would freeze a half-folded panel. The
frame clock is zeroed whenever the panel is hidden, so a re-open advances by nothing rather than by
the whole interval the screen was shut.

`SidebarInput` is a `CampaignInputListener` acting in `processCampaignInputPreCore` at priority
1000, because a render pass cannot consume events; consuming pre-core stops a click reaching the
screen. Key events route to the host only while `isFullyExpanded()` - a docked or animating panel is
not presenting its tabs, so its hotkeys stay inert and the key falls through. Off the gate it
cancels any dangling drag, so a grab left over from an overlay closing mid-drag cannot persist.

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
ranked, wrapped, and filtered - and the widgets that change those choices. `SortSelection`,
`ColumnSelection`, and `FilterSelection` are the stores. `FilterPickerControl` is the picker the
pieces compose into, `SelectableListItem` the seam its rows are drawn from, and
`SelectableItemCache` the memo a layer holds its list in.

The stores are leaves: they hold the raw stored keys and nothing that resolves one. What a
filtered-to id points at stays with the layer that offers the choices, and a stored sort or
column key only means something to the model that owns it, so this package carries the storage
without learning what any one layer's list holds.

The sort and column *model* is not here at all - `ListSortMode`, `ListSortModes`, `ListSort`,
`SortDirection`, `ListColumns` and the two selector controls are KMLib's
(`kmlib.starsector.ui.widgets.lists`), since a sortable list with a persisted mode and a
column-count segment selector knows nothing about a map. The split is that **KMLib owns the model and the
resolution rule; KMU owns where the answer is kept.**
`SortSelectionBinder` and `ColumnSelectionBinder` are the join: each reads its
store's raw keys into the model's resolution and writes a picked value back out, so a reading
layer asks for "the stored sort over my modes" exactly as it did while the model lived here.

The keys are why the split falls where it does. They are the frozen `$kmu_political_*` spellings
below - save state this mod cannot move and a shared library has no business holding.

`SortSelectorControl` and `ColumnsSelectorControl` draw those models. The sort selector is a
re-firing radio over the caller's modes - a click on an unlit row switches to that mode at its
default direction, a re-click of the lit row flips the direction - with each row's trailing
triangle previewing the order picking it would give. The columns selector is an ordinary
two-segment radio, inert on a re-pick. Neither reaches a save: each reports its pick, and
`FilterPickerControl` is where those picks are bound to the binders above and where the columns
caption is resolved out of this mod's strings.

`FilterSelection` holds one selected id per opaque scope, so each scope keeps its own choice and
switching scopes neither clears nor cross-reads another's. Beyond the read, pick, and clear it
heals a stored id a caller-supplied predicate no longer accepts (for a save whose selection stopped
being on offer between sessions) and carries a pre-per-scope save's single shared slot into a scope
slot on load. Binding that predicate to a live source of what is selectable *now* is the reading
layer's, since the source is exactly the knowledge these classes refuse.

`FilterPickerControl` is the spotlight picker those pieces compose into: a section rule, the
columns selector, a row pairing the sort selector with whatever the caller pairs beside it, then a
deselectable icon-radio list wired straight to `FilterSelection` - so a pick spotlights a row and a
re-pick clears the filter. Two things keep it layer-neutral. `SelectableListItem` is the seam its
rows are drawn from - an id, a label, a crest, and nothing else - which a layer implements on its
own item type, so the list ranks through the layer's own comparators and nothing is copied into a
framework value on the way in. And the right half of the sort row is a parameter: pairing something
with the sort is a layout decision this package can hold, but what sits there is not, and a layer
with nothing to pair passes none.

`SelectableItemCache` is where a layer holds the resolved list between frames. A body build runs
twice a frame (render and hit-test) and a picker list is typically a full pass over whatever the
layer scores its items from, so the list is rebuilt only when a caller-supplied revision moves.
What belongs in that revision is the caller's judgement and is the whole of the invalidation
contract; the key also carries the sector identity, weakly held, so a save reloaded in the same
session recomputes rather than serving the previous save's items. [The caching
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

`SidebarRenderer.buildStyle` composes the `WidgetStyle` each frame from live values: a black body
fill faded by the opacity setting, the player faction's base and bright accents, the insignia body
face, and `LiveSidebarPlacement.buildMapTabStyle()` for the tabs.

That one `TabStyle` carries a strip end to end - band height, `VanillaTabColours` scheme,
`HotkeyStyle`, and the orbitron face - so the value the layout snapped tabs against is the value the
renderer paints them from and a snapped tab width cannot part from the text drawn into it. The two
screens differ only in band height (`MAP_HEADER_BAND_HEIGHT` / `INTEL_HEADER_BAND_HEIGHT`), which the
paint pass does not read. Both faces are named through KMLib's `StarsectorFont` enum rather than by
atlas basename.

Both screens take `HotkeyStyle.createUnderlined()`: the vanilla Sector/System tabs the on-map strip
sits below draw a line under the bracketed key, so a bare key reads as a mismatch against the row
above. The line is a quad the style places under the key's drawn box, not part of the measured
display string, so adding it moves no tab.

`style/SidebarPalettes` maps the player's `NotchChevronColourChoice` to the chevron's resting and
hovered shades. It is kept out of the renderer so the "which colour does this choice mean" rules
stay a pure lookup a test can pin, with no live GL or screen needed.

The border width, opacity, chevron colour, collapse seconds, and both anchors' paddings are LunaLib
fields read through `kmu.settings.KmuLunaSettings`.

## What is not here

The *panel widget itself* - frame, tab strip, scrollbar, collapse handle, control widgets, and the
`TabPanelController` that holds scroll and collapse state - is KMLib
(`kmlib.starsector.ui.widgets`, `.input`, `.render.gl`), as is the *list sort and column model*
and its two selectors (`.widgets.lists`, see [Picker state](#picker-state)); this package supplies only the wiring
KMLib cannot know. The *layer roster and each screen's active pick*, including the save migrations
behind them, are `base/layer`'s (`MapLayerRegistry`), summarised in
[map layers](../../README.md). The *body composition* the panel lays out belongs to whichever
layer is active - for the political map, [`politicalmap`](../../politicalmap/README.md) and its
`base/sidebar` controls - though the spotlight picker a body embeds is this package's, and it is
here that KMLib's two selectors are bound to the save (see [Picker state](#picker-state)). What
the political map keeps of its own there is what the picker refuses to know: which blocs are on
offer, what invalidates that list, and the recede toggles it pairs with the sort. Both listeners and the per-load reseed are registered in `KMU_ModPlugin`.
