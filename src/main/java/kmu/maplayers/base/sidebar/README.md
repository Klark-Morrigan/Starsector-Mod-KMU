# The map layer sidebar (`base/sidebar`)

The control box that carries the map layer tabs and the active layer's body controls.
One panel definition serves both screens that show the sector map;
a `SidebarHost` supplies the per-screen answers,
and everything else -
layout,
paint,
input -
runs against that role.

Part of [map layers](../../README.md);
see the [mod README](../../../../../../../README.md) for project context.

This file owns what the panel *is*:
where it lands,
the opener past its last tab,
and the state its lists and its fold are kept in.
Two leaves own the rest.

| | |
| --- | --- |
| [`runtime/`](runtime/README.md) | What differs per screen, what stands the panel down, and the two passes that draw it and take its input |
| [`style/`](style/README.md) | What it is painted in, and how loudly it answers the pointer |

## Index

- [Placement: one resolve a frame, published to every pass](#placement-one-resolve-a-frame-published-to-every-pass)
- [The opener on the band](#the-opener-on-the-band)
- [Fold persistence](#fold-persistence)
- [Picker state](#picker-state)
- [What is not here](#what-is-not-here)

## Placement: one resolve a frame, published to every pass

`LiveSidebarPlacement` builds the placement from the live screen,
the settings,
and the calling screen's `ActiveLayerSelection`.
The draw asks for it once a frame,
through `SidebarHost.refreshPlacement()`,
and what it lays out is published as the panel on screen;
the input pass and the hover cover then read that through `getDrawnPlacement()`
rather than each resolving one of their own.
Hit-testing the drawn box is the point:
a settings change landing between two passes that each resolved would move the box out from under the pointer.
It is also three layouts a frame saved -
a full one measures every tab and every body label -
and the cover asks its question every frame the cursor moves,
over every host on the roster.
The draw drops the published placement as it stands down,
so nothing hit-tests a panel that has left the screen.

The two entry points
(`resolveMapPlacement`, `resolveIntelPlacement`) differ only in the anchor padding;
`computeIntelPadding` converts the visor rect into top-left-anchored padding,
and is package-private so the anchor maths stands apart from the sector read around it.
The `TabStyle` is the host's and is injected,
so the band a strip stands in stays with the rest of that host's look rather than being half here.
Both return `null` when either of the panel's two faces cannot load,
since layout snaps every row to measured text;
callers then draw and consume nothing.

Those two faces are why the placement hands the layout a `StripTextMeasurers` pair rather than one measurer.
A panel is not lettered in a single atlas:
the band reads in the tab face taken off that injected style,
and the controls beneath it read in the body face (`SidebarStyles.resolveBodyFont`).
Measuring a row through the face it is *not* drawn in sizes it against letters it never wears,
and the box,
framed to its widest row,
inherits the error.
Because the two hosts state different tab faces -
the sector map's condensed orbitron against the intel screen's pixel face -
a strip snapped wholly to the tab face would stand the same body at a different width on each screen.
The pair is built through `StripTextMeasurers.loadFaceMeasurers`,
whose two parameters are differently typed so the faces cannot arrive the wrong way round.

The layer selector is a single `ControlSpec.Tabs`
whose action selects the layer at the clicked index,
so the switch rides on the control and no tab callback is threaded through the input pass.
The draw also clamps the controller's stored scroll offset to the freshly laid-out overflow,
since it is the pass that owns the frame -
the layout itself only reads that offset,
and a hit-test that wrote it would be correcting state it had no part in moving.

## The opener on the band

Past the last tab stands the bar's opener,
[`BarOpeners`](BarOpeners.java) -
the one way into [the dialog the bar is arranged in](../chrome/arrange/MapLayerArrangementDialog.java),
there being no key bound to it.
What it shows and does lives there rather than with the layout that places it:
the placement asks for a button,
and this answers which button that is,
so a mark or an asset changing is a change to the control and nothing about how a band is laid out.
It is KMLib's band button (`TabPanelPlacement.bandButton`) rather than a segment of the tabs control,
which is the one thing it may not be:
that control is indexed by layer everywhere it is read -
the click that selects,
the lit tab,
and `BaseSidebarHost.handleKeyPress`'s shortcut walk -
so a cell in it that is not a layer would move all three one along.
Outside it,
the row's indexing is untouched and a press on the opener switches nothing.

It is laid and hit through the same header call the tabs are,
and drawn in **the host's own tab style**,
so it stands in the chrome and colours of the strip it sits on
rather than reading as furniture from another screen.
`SidebarStyles.buildBandButtonTabStyle` changes exactly one thing about that style:
the box.
A tab row states a box wide enough for the longest layer name it will ever carry -
a fixed 130 on the sector map -
and the opener is as wide as the one thing it shows,
so its box is rebuilt at the image's own width while its height
and the channel to its neighbour stay the row's.
The band height is not its to choose either;
the layout pins it to the panel's,
the band being the room the panel was given.

**It shows a mark,
not a word** -
the game's own `graphics/factions/storage.png`,
scaled to the tab height with the box following the resulting width.
The bar it arranges is right beside it,
so a label would only repeat what the picture says,
and it needs no bundle entry to say it in every language the game ships in.
The proportions are read off the sprite rather than written down,
so a differently-shaped asset needs no correction here;
an asset that will not resolve falls back to a square,
leaving a pressable control with nothing drawn in it rather than one that has silently left the bar.

**The mark itself answers the pointer.** It fills its box,
so the chrome under it -
the fill a tab lights by -
is covered by the very image that would be showing it;
what lights instead is the picture,
washed by the shade the button's own word would read in (`BandButtonPlacement.resolveIconTint`).
Nothing new is chosen here:
the shade is the host's own tab style,
so the mark travels as the labels beside it do,
and the sprite states no tint of its own,
so the wash is the whole of its colour.
Nothing else either -
no press lift and no lit state,
since what the button opens covers the screen on the frame it is pressed,
so a lift would run under a dialog and be seen by nobody.

It rides in the drawn band,
so the fold wipes it with the tabs,
`containsPoint` claims it from the map underneath,
and `computeOuterBound` reaches it.

**It stands only where there is something to arrange**,
which `LiveSidebarPlacement.resolveOpenerSpec` decides:
more than one painting layer on the roster,
or the band flies no button at all.
A door onto an empty room is worse than no door -
the player who opens it learns the feature is empty rather than that it is not theirs yet,
one row having nowhere to move that changes which layer paints
and a hide the dialog's last-tab guard refuses.
Whether the band carries a button is the placement's rather than the control's,
the control still answering only what it shows and does.

The count is **the roster's and never the offered row's**.
That row shrinks as the player takes tabs off the bar
and this button is the only way one comes back,
so an opener that left once the row got short would strand the arrangement that shortened it.
`NoLayer` is not counted either,
being a tab whose job is to draw nothing -
which is what makes the rule bite on an install carrying KMU alone.

One thing that withdraws,
on the intel screen only:
while a single painting layer is registered,
the empty view's tab can no longer be taken off the bar.
That is the whole of the cost -
a tab the player can leave unpicked,
one click from the layer beside it -
and it comes back the moment a second layer registers.

`kmu_map_dev_ui_controls_layersArrangementButton_isAlwaysShown` overrides the count outright,
and is asked before it.
Shipped off,
it is how the box is reached at all on an install carrying KMU alone -
every install until a second layer ships -
so the dialog can be opened and worked on where the rule above would hide it.
A dev row rather than a visuals one for the same reason as the filter-row hatch beside it:
nothing here is set to taste,
it is a hatch onto something otherwise correctly out of the way.

Its words are the framework's own chrome rather than a layer's,
so `KmuStrings` is read here -
the same distinction the settings already make,
and why this is not the bundle leak the tab label was.

## Fold persistence

`SidebarFoldSelection` is where a fold is read from and recorded to;
`PersistedSidebarFold` is the sector-memory implementation,
one instance per host with its own opening default.
Its key is one base key resolved through the screen's `ScreenMemoryScope`,
so the slot has the shape every per-screen key does.
A host does not build it:
it hands `BaseSidebarHost` its screen's picks and its opening default,
and the fold is composed there from those same picks -
so the fold and the picks cannot be built for two different screens,
which would draw one screen's panel while folding another's.

| Key | Holds |
| --- | --- |
| `$kmu_political_sidebar_docked_map` | `MapSidebarHost`'s resting fold, default expanded |
| `$kmu_political_sidebar_docked_intel` | `IntelSidebarHost`'s resting fold, default docked |

The base key is a save-serialised identity and frozen once shipped;
renaming it returns every existing save to its host's default.

Only a settled end is recorded.
`SidebarRenderer.resolveSettledFold` reads the collapse fraction
and the fully-expanded flag and yields `null` mid-fold,
so the in-flight animation is never written.
`recordFold` compares against sector memory itself rather than a cached value -
a cache would still hold the previous save's fold and read the new save's first frame as a change,
and a write attempted before the sector exists is simply dropped and retried next frame.
A save that has never been folded is left untouched,
so the key appears only once the player moves the panel.

Hosts are process-lifetime singletons,
so `BaseSidebarHost.restoreFoldFromSave` replaces the controller per load with one constructed at the stored fold.
Replacing rather than mutating avoids reaching into the collapse animation
(the two ends are the widget's own two constructors) and clears the previous save's scroll offset in the same move.
It is also where the panel's sound scheme is composed from the player's levels,
the seed built at class load having no settings mod to read yet -
see [the look](style/README.md#how-the-panel-sounds).

## Picker state

A layer whose body carries a sortable,
column-laid list needs somewhere to keep how that list is ranked,
wrapped,
and filtered.
`SortSelection`,
`ColumnSelection`,
and `FilterSelection` are those stores,
and `SortSelectionBinder`,
`ColumnSelectionBinder`,
and `FilterSelectionBinder` are what tie each to the widget that changes it.
`ScreenSelectionSlot` and `SelectionSlot` are what they are addressed by,
over the `MapLayerStoreNamespace` that says whose answers a slot holds,
and `FilterHoverSlot` stands beside them under a `PickerScope`,
holding where the pointer rests rather than what was picked -
which is no fourth store:
what it holds belongs to a sector rather than to a save.
Three stores,
three binders,
the three addresses they take and the hover slot is the whole of this half of the package.

The stores are leaves:
they hold the raw stored keys and nothing that resolves one.
What a filtered-to ID points at stays with the layer that offers the choices,
and a stored sort or column key only means something to the model that owns it,
so this package carries the storage without learning what any one layer's list holds.

The picker itself is not here at all -
`ListPickerControl`,
`SelectableListItem`
(an ID, a label, a crest, and whether the row reads back),
`ListPickerStore`,
`RevisionMemo`,
and the sort and column model behind them
(`ListSortMode`, `ListSortModes`, `ListSort`, `ListPicker`, `SortDirection`, `ListColumns`, and the two selector controls) are KMLib's (`kmlib.starsector.ui.widgets.lists`),
since a sortable,
column-laid,
spotlight-picking list knows nothing about a map.
The split is that **KMLib owns the model,
the composition,
and the resolution rule;
KMU owns where the answer is kept** -
a row states *that* it reads back,
KMLib decides how far back that reads.

The keys are why the split falls where it does.
They are save state a shared library has no business holding -
and they are not this package's to spell either:
a store holds its own key and nothing about whose it is,
so the leading segment arrives with the address.

That is `MapLayerStoreNamespace`,
the third axis:
which mod's picker is asking.
Without it the three stores are shared in the wrong sense,
since two mods choosing the scope string `factions` write the same key.
It has no default and is derived from nothing -
a namespace standing in from a layer ID would part one mod's two layers,
which the scope already does correctly,
and a fixed fallback would put every consumer that forgot to name itself back in one shared namespace.
It carries its own separator,
so what it composes is exactly what the holding mod already ships.
This mod's own is `KmuMod.MAP_STORE_NAMESPACE`,
the frozen `$kmu_map_` prefix of every key below,
named there because whose picks these are is a fact about the mod rather than about the map layers.

`FilterSelection` and `SortSelection` both hold their answer per `SelectionSlot` -
a namespace,
a screen and one opaque scope,
the three that compose the key.
The scope because an ID read under the wrong scope names nothing
and a mode key read under the wrong one resolves against nothing,
making every switch look like a reset;
the screen because a pick is something the player did to one panel;
the namespace because one mod's picker has no business reading a pick made on another's.
All three are one value rather than loose arguments,
since a slot crossed one way and not the other compiles
and reads as the feature working right up until the player sets the same preference twice.

`ColumnSelection` takes `ScreenSelectionSlot` -
the namespace and the screen,
which is `SelectionSlot` minus the scope and the address it narrows.
How many columns a list wraps across is a layout preference,
not a statement about what the list holds,
so it means the same thing under every scope -
but a panel is a fixed width the list was laid out inside,
and the two panels are two widths.
Dropping the scope is what made the namespace load-bearing here first:
with nothing else parting two mods' counts,
a foreign picker's column pick re-wrapped this one's list.

The screen is an axis no picker is handed.
A pick is reported by a widget that knows only that it was clicked,
and the binder files it under the slot it built that picker for -
captured at the build for the reason the refresh board is,
since a report can land after the player has moved to the other screen.

Beyond the read,
pick,
and clear,
`FilterSelection` heals a stored ID a caller-supplied predicate no longer accepts -
a selection that stopped being on offer,
whether between sessions or while the game runs.
Binding that predicate to a live source of what is selectable *now* is the reading layer's,
since the source is exactly the knowledge these classes refuse;
so is deciding at which moments the offer can have moved.
The predicate is asked only when a stored ID is there to judge,
so binding it to an expensive source costs nothing on a slot holding no pick.

One heal is one screen's,
so a caller owing both runs it twice.
That is deliberate:
what lapsed is a fact about the sector rather than about a panel,
so a bloc that stopped being on offer stopped being on offer wherever it was picked -
and a screen healed only when it is next up would meanwhile go on receding the sector behind a bloc the player cannot unpick from the panel they are on.
The screens are walked off `MapLayerScreens`,
which is where how many there are is known.

`FilterHoverSlot` is the transient counterpart:
holding the ID a pointer rests on instead of the ID that was picked,
so a reading layer can preview what picking it would spotlight.
It persists nothing and raises no refresh,
and both follow from what a hover is -
a place the pointer happens to be this frame,
previewed over paint that is already on the map,
where a pick has the reading layer rebuild everything it draws.

Its address is `PickerScope` -
the mod and the list,
`SelectionSlot` without the screen.
**The one tie of the four that takes no screen**,
since only one screen is ever up to preview on
and the pass that reads a hover back is drawing that screen with no pick of its own to resolve one from.
The mod half is not optional even so:
the scope ID is opaque and every consumer picks its own,
so two mods listing under `factions` would preview each other's rows -
the persisted stores' collision arriving unpersisted and per sector.
A scope is resolved off the slot the picker was built for (`PickerScope.resolveScopeOf`)
rather than composed beside it,
so the list a hover previews and the list a pick files under cannot come apart.
It is a map key rather than a stored one,
so it is the value itself that is compared and there is no key here to spell.

Which is why it is the one thing here that is not stored at all.
A hover names a bloc one sector's walk surfaced and is read back against that sector's presence,
so it is a fact about a sector rather than about a save or the process -
and it is held by that sector's `SectorMapMachinery`,
resolved through `FilterHoverSlot.resolveHoverSlotIn`.
A load disposes the machinery and the hover goes with it,
so no key has to be healed and no discard written;
what stays the caller's is clearing within a sector's life,
at the pointer leaving a row and at a panel standing down without a leave ever being reported,
since neither is visible from here.

`FilterSelectionBinder` is the one binder that also builds,
because the picker's three ties resolve at one point:
it reads the scope's spotlighted ID and the scope's stored sort on the way in,
resolves the columns caption out of this mod's strings,
and routes each of the picker's three reported picks to the slot that keeps it -
the item pick to `FilterSelection` under that scope,
the other two through the binders beside it,
the sort under that same scope.
The row the pointer rests on routes the same way,
into `FilterHoverSlot` under the `PickerScope` that slot resolves to,
and it is the one report that raises nothing and persists nothing:
a preview is drawn over paint already on the map.
One slot covers every one of those answers,
so a layer cannot bind its filter,
its sort and its preview to different lists.
A layer that composed the picker itself would have to name all three slots,
which is exactly the knowledge these binders exist to hold,
so a calling layer hands over its `ListPicker`,
its column count,
and whatever it pairs beside the sort selector,
and names no store at all.

The sector arrives as the whole `SectorMapMachinery` rather than as the refresh board alone,
because two of those writers are that sector's:
the board an item pick repaints through,
and the hover slot a previewed row is recorded in.
Handed over side by side they would be two chances to pair one sector's board with another's slot;
derived from one machinery at the build they cannot disagree.
Both are taken *at the build* rather than when a report lands,
since a build runs while the sector is live and a report can arrive after a load has disposed it -
and asking a disposed machinery for machinery quietly makes a second copy that answers for a sector nothing draws.

The picker arrives wildcarded (`ListPicker<?>`),
because what a layer ranks its rows by is that layer's own,
and this is where the wildcard is captured -
once for the mod,
rather than in each layer,
since the capture needs both the selection slot and the sort binder to finish the job.
The stored sort is resolved here for the same reason:
resolving it needs the vocabulary,
which only arrives inside the bundle.
An offers-nothing picker is answered before that resolution,
since it carries no fallback mode to land on.

`SelectableBlocCache` (the political map's) is where a layer holds its resolved picker between frames,
over KMLib's `RevisionMemo`;
what invalidates it is the layer's own judgement.
It is held the way the hover slot above is,
by the sector's `SectorMapMachinery` through `SelectableBlocCache.resolveBlocCacheIn`.
[The caching notes](../../../../../../../docs/dev/caching.md) own that model in full.

Each store's own key,
and what the address composes it into -
shown under this mod's namespace,
since these are the spellings every existing save holds:

| Store key | Composed | Holds |
| --- | --- | --- |
| `sort_mode_` | `$kmu_map_sort_mode_<scope>_<screen>` | one screen's picked sort mode key in one scope, absent until first picked |
| `sort_direction_` | `$kmu_map_sort_direction_<scope>_<screen>` | one screen's picked direction (`asc` / `desc`) in one scope, absent until first flipped |
| `list_columns` | `$kmu_map_list_columns_<screen>` | one screen's picked column count key, absent until first picked |
| `filter_bloc_` | `$kmu_map_filter_bloc_<scope>_<screen>` | one screen's filtered-to ID in one scope, absent while un-filtered |

The store keys are layer-neutral because every map layer's picker stores through these classes -
one naming a layer would have every other layer persisting under it -
and mod-neutral for the reason the namespace exists.
Like the fold keys the composed spellings are save-serialised identities and frozen,
so renaming any segment of one would reset every existing save to the default.
The namespace composes first and the screen segment last,
through the same `ScreenMemoryScope` the fold resolves through,
so every per-screen key in the mod has one shape.

The sort and column stores raise no refresh:
both values are read on the per-frame body build,
so the next frame re-sorts or re-wraps on its own,
and nothing on the map depends on either.
`FilterSelection` is the exception among the persisted stores -
its value changes what a layer paints,
so a landed pick or clear raises `MapLayerCommonRefreshSignal.FILTER`;
the heal runs on load before anything paints,
so it clears without raising it.

## What is not here

The *panel widget itself* -
frame,
tab strip,
scrollbar,
collapse handle,
control widgets,
and the `TabPanelController` that holds scroll,
collapse,
hover,
and click-pulse state -
is KMLib
(`kmlib.starsector.ui.widgets`, `.input`, `.render.gl`),
as is the *spotlight picker* with its item seam,
its sort and column model,
and the list memo behind it
(`.widgets.lists`, see [Picker state](#picker-state));
this package supplies only the wiring KMLib cannot know.
The *layer roster and each screen's active pick*,
including the stale-id fallback behind them,
are [`base/layer`](../layer/README.md)'s
(`MapLayerRegistry` and `MapLayerScreens`).
The *body composition* the panel lays out belongs to whichever layer is active -
for the political map,
[`politicalmap`](../../politicalmap/README.md) and its `base/sidebar` controls -
though it is here that KMLib's picker is bound to the save (see [Picker state](#picker-state)).
What the political map keeps of its own there is what the picker refuses to know:
which items are on offer,
what invalidates that list,
and the recede toggles it pairs with the sort.
Both listeners and the per-load reseed are registered in `SidebarInstaller`.
