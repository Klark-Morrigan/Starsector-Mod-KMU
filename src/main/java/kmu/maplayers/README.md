# Map layers (`maplayers`)

A map layer is one overlay over the campaign map.
A control box floating over the map carries a strip of tabs,
one per layer,
and exactly one layer is active at a time -
the same model as the map's own Sector / System tabs.
Selecting a tab decides what,
if anything,
is painted over the sector,
and opens that layer's own controls beneath the tabs.

The box draws on both screens that show the sector map:
the full map screen (M) and the map preview (the "visor") embedded in the intel screen.

Part of Klark Morrigan's Utilities;
see the [mod README](../../../../../README.md) for project context.

## Index

- [The layers](#the-layers)
- [A tab off the bar stands down](#a-tab-off-the-bar-stands-down)
- [The one-way arrow](#the-one-way-arrow)
- [The vocabulary](#the-vocabulary)
- [Two screens, two picks](#two-screens-two-picks)
- [What is per screen](#what-is-per-screen)
- [Where each part lives](#where-each-part-lives)

## The layers

| Layer | Paints | Body |
| --- | --- | --- |
| **No Layer** | nothing - the map reads as vanilla | empty |
| **Political map** | the sector coloured by who controls each system | map-wide options, the view radio, the bloc spotlight picker, and the active view's own toggles |

No Layer leads the strip as a first-class tab rather than an off switch,
so an empty map reads as a choice -
on every screen that has no tick box of its own on the game's filter row.
Where that box stands it does the same job more discoverably,
so the tab is withheld from that screen's strip,
and a pick already sitting on it follows the tabs onto the leading one that screen does offer.
It comes back wherever there is no box,
which is any screen the reach could not be made on.
The political map is the pick an untouched save resolves to,
so the overlay is up the first time the sector map opens.
A tab may also answer a shortcut,
printed on the tab,
on both screens the box draws;
since the picks are per-screen,
a shortcut moves only the tab of the screen it was pressed on.
KMU's two tabs take theirs from LunaLib rows the player can rebind,
and a tab with no key simply has no hint and answers no press.

## A tab off the bar stands down

The bar is arranged in [the arranging dialog](base/chrome/README.md#the-arranging-dialog),
and a tab taken off it stops costing.
Being registered and standing on a sector are two different things,
and this is where they part company:

| | What it is | What a hidden layer keeps |
| --- | --- | --- |
| **Registered** | a place in the roster, and an id a stored pick resolves against | kept - the dialog has to list the layer to offer it back, and a save naming it must not read as an id from a build that dropped the layer |
| **Standing** | what a layer runs on one sector: its listeners, its polls, its save heals | lost, on every sector |

A layer states the pair -
how to stand up on a sector,
how to stand back down -
or states neither,
which is a layer with no sector wiring and so one that is simply always standing.
Asked on load,
so a layer hidden in the store never stands up at all,
and again whenever the player writes an arrangement.
The details are [the layer framework's](base/layer/README.md#what-a-hidden-tab-stands-down).

**The hidden set drives it,
not the order.** An id entering the hidden list stands one layer down
and an id leaving it stands one up;
a reorder moves no id between the two,
so dragging a tab a place up the column wires nothing and unwires nothing.

The mod-wide switch keeps its own job beside all of that:
every layer down at once whatever the bar says,
and the path a player uninstalls KMU from a save through.
It also stays the *only* settings row about layers,
and this is why there is no second one per layer beside it.
Such a row could speak only for KMU's own layers,
would move a tab at the next launch rather than at the click since the roster is filled at load,
and could not be undone from the bar -
a layer it switched off never joins the roster the dialog lists,
so the way back would be a screen the player has to remember going to.
The bar asks the same question in the place where the answer shows,
and asks it of every registered layer whoever ships it.

## The one-way arrow

Nothing under `kmu.maplayers.base` may import `kmu.maplayers.politicalmap`.
`base` is the substrate every layer sits on,
so an import in that direction would make the framework depend on one of its own layers -
and a second layer could then only be written by reaching into the first one's drawer,
which is the state this tree was carved out of.

The compiler is happy either way round,
so the rule is a build gate rather than a review question:
`enforcePackageLayering`,
declared in [build.gradle](../../../../../build.gradle) and implemented in Common-Java.
It reads every source set,
not just `src/main` -
a suite reaching across for a real political type is the shortest way to make it compile,
and the sector-geometry viewer under `src/utils` sits in `base.geometry` itself.

One arrow inside `base` is gated the same way,
and for the same reason:
`base/visibility` splits into what may be shown of a colony (`colonies`)
and which systems the map draws (`systems`),
the second composed out of the first.
The reverse import is what would make "may this be shown" depend on whether anything is being drawn,
so each half is a package of its own -
the gate closes a package root,
and a parent root closes its own children with it.

A fourth sits beside the first:
`structures` keeps what was observed of the built things in orbit that are not colonies,
which conceal a different fact for a different reason.

Under all of them is `observations`,
which states how old the news about a concealed fact is,
in the same words whatever the fact is about.
It is gated against importing any of the families above,
which is what keeps it from learning what a colony is -
a shared rule that named one family would be that family's rule with extra callers.

## The vocabulary

`base` and a layer deliberately use different words for the same thing,
and the translation happens at the boundary between them.
That is what keeps the framework honest:
if `base` said "faction",
a second layer could not use it without lying about what it paints.

Read this table left to right as "what the framework calls it" -> "what the political map calls the thing it hands over".

| `base` says | political map says | Means |
| --- | --- | --- |
| **owner** | **holder** (`DominantHolder`, `HolderProvider`) | what a cell is attributed to; two cells fuse only if it matches. Opaque to `base` - a faction id under the factions view, an alliance id under alliances, a claimant under claims |
| **unowned** | factionless, uninhabited, decivilised | a cell no owner is attributed to. It never fuses, and draws its own lone outline. Unowned is not the same as empty: a view whose holding rule admits only some markets (claims, for the reasons its own package sets out) leaves settled systems unowned, so the factionless split is read from the pass's inhabited-system set, not from the absent owner |
| **cluster** (`StyledCluster`) | one body of a **territory** | the merged shape connected same-owner cells form, inside one traced border. A lone cell is a cluster of one |
| **cluster group** (`StyledClusterGroup`) | **territory** | everything one owner paints: its clusters, plus the paints they all share. "Territory" is the political word for *all* of a bloc's cells, which may be several disjoint clusters - so a territory is a cluster group, never a cluster |
| **cluster border** | national border, frontier | the inset ring around a cluster. `base` never calls it national - nothing about a border is political |
| **interior seam** | province line | the fused edge between two same-owner cells, drawn faint or not at all |
| **fill state** | held / contested / drawn-empty | how a cell inside one cluster paints: `SOLID`, `HATCHED`, `UNFILLED`. The layer decides which system is which; `base` only paints it |
| **`ElementPaintSelection`** | `FactionPaletteSlot` | the player's colour pick, held opaquely by the theme and *unresolved*: it names where to look, not a colour, since one theme serves every bloc. `base` asks only "is it absent" (paints nothing); how many options exist is the layer's business. `FactionPaletteChoice` is the settings-side wire format behind it, and is deliberately not an `ElementPaintSelection` - its "No color" would otherwise read as drawable |
| **shade** | **shade** | the concrete `Color` a selection resolves to once a bloc's palette is in hand. Never a synonym for the selection: `MapPalettes` is where the one becomes the other |

The right-hand column is a build gate too,
for the reason the arrow above is one.
A leaked import stops compiling the day a package moves;
a leaked noun in a Javadoc survives every move in silence,
and it is what a reader of `base` actually reads -
so `enforcePackageVocabulary`,
declared beside the layering rule in [build.gradle](../../../../../build.gradle),
fails the build when anything under `base` says *territory*,
*national border*,
*contested fill* or *bloc*,
in prose or in a name.
Faction,
alliance and claim are left off that list on purpose:
this file and the `geometry` and `labels` READMEs name them as examples of what an opaque owner could be,
which is the argument for the opacity rather than a leak of it.

One word is forbidden mod-wide rather than only under `base`,
and for a different reason:
*ground* is not a layering leak,
it is simply unclear.
It stood for a cell,
a cluster's fill,
a whole territory and the receded backdrop in neighbouring sentences of the same file,
while every one of those words was available.
Say what is meant instead.

The handful of files that may still say one of the political words are named in `build.gradle` rather than marked in place,
so an exemption is something a reviewer reads in the diff.
All of them name an identifier rather than make a claim about the framework -
the `filter_bloc_` store key,
which composes into the `$kmu_map_filter_bloc*` sector-memory keys and
whose spelling is fixed by every save already holding one,
and the political class names the geometry viewer lists as pipeline stages it does not exercise.

Two words that are **not** synonyms,
despite looking alike:

- A **cluster** is one connected component.
  A political **territory** may be several -
  a faction's homeland and its far-flung colony are two clusters under one territory,
  which is why each gets its own label rather than one name stranded between them.
  The two draw records keep that distinction rather than leaving it to the reader:
  geometry hangs off `StyledCluster`,
  one per body,
  and paint off the `StyledClusterGroup` around them,
  so a record covering several bodies cannot be mistaken for one body's.
- An **owner** is a render input;
  a **holder** is a computed political fact
  (dominant faction by market weight, or claimant, or alliance).
  They coincide only because the political map feeds one into the other.

## Two screens, two picks

The same map widget draws on the sector map and inside the visor,
so "which layer is active" cannot be one shared value:
it would paint the sector map's pick onto the intel screen
and ignore the tab the player is looking at.
Each screen holds its own `PersistedActiveLayerSelection` under its own key in `MapLayerScreens`,
and `MapLayerRegistry.isDrawnLayer` resolves which is live per frame from which screen is up.

A screen's key is composed rather than spelled:
`ScreenMemoryScope` appends that screen's segment to a preference's own base key (`$kmu_political_active_layer` + `map`),
and `MapLayerScreens` names the two screens as the two scopes every one of their keys resolves through.
One shape for every per-screen slot,
and a screen the mod does not name has nowhere to be spelled.

The base key belongs to the holder that owns it,
and the screen to the caller:
a holder takes a scope and resolves its own key under it.
That is the only arrangement open to the holders `base/layer` may not import -
the political map's own preferences -
so it is the arrangement all of them use,
rather than a central list of keys that could only ever hold some of them.

A holder does not compose the key itself.
It declares `AddressedMemoryFlag` or `AddressedMemoryString` with its base key (and, for a flag, its default)
and names an address on every read and write;
the two build the sector-memory slot behind it.
`MemoryKeyAddress` is what an address is:
`ScreenMemoryScope` partitions by screen,
the sidebar's `ScreenSelectionSlot` by the mod whose store it is and by screen,
and a picker's `SelectionSlot` by those two and by the scope its list was listed under.
So a holder states what it stores and never how the key is spelled,
and the segments cannot end up ordered one way in one holder and another way in the next.

The build enforces it.
`enforceRestrictedCalls` contains the three ways into a memory slot -
`new SectorMemoryFlag(`,
`new SectorMemoryString(` and `SectorMemoryAccess.readSectorMemory()` -
to the holders that compose a key through a scope,
so a preference added with a bare key fails the build
rather than quietly sharing one slot between both panels.

```mermaid
flowchart TD
    F([Frame]) --> Q{Is the intel<br/>screen up?}
    Q -- yes --> I[Intel screen's pick]
    Q -- no --> M[Sector map's pick]
    I --> A[Active layer]
    M --> A
    A --> O([What the overlay paints])
```

Switching tabs on one screen leaves the other where it was.
Both picks persist;
a save holding no pick,
or one naming a layer no longer registered,
resolves to the default the roster answers -
the first registered layer that offered itself as one.

## What is per screen

The box's own state,
and the picks made on the picker inside it -
a pick is something the player did to one panel,
so it is filed against that panel.

| State | Scope |
| --- | --- |
| Which tab is lit | per screen, persisted |
| Whether the layers are shown at all | per screen, persisted |
| Whether the box is folded to its rail | per screen, persisted |
| How far the body is scrolled | per screen, for the session |
| The picker's bloc spotlight, its sort, and its column count | per screen, persisted |
| The name format, the recede styles, and the uninhabited outline | per screen, persisted |
| Political-map view | per screen, persisted |
| How the bar is arranged - the tab order, and the tabs taken off it | shared, per user in common data |
| Appearance and sound settings | shared, in LunaLib |

The arrangement row is the one thing here that is not the save's.
Which tab a screen is on is a fact about one campaign;
how the bar itself is laid out is a preference about the interface,
the same whichever campaign is loaded -
so it goes where a preference goes,
and a player who ordered their bar once does not order it again per save.

## Where each part lives

- **[The layer framework](base/layer/README.md)** -
  what a layer is (`MapLayer`),
  the roster and its arrival order (`MapLayerRegistry`),
  each screen's tab and show-or-hide pick under its own frozen keys
  (`MapLayerScreens`, `ScreenLayerPicks`, `ControlBackedMapLayerVisibility`),
  which of the registered layers a screen is offered as tabs (`ScreenLayerTabs`),
  and the bar arrangement laid over the roster
  (`MapLayerArrangement`, `ArrangedLayers`).
  It is also what a layer runs on a sector while its tab is on the bar
  (`MapLayerStanding`, `MapLayerStandings`),
  so a tab the player took off stops costing.
  Nothing in it paints,
  and none of it knows what a layer paints.
- **[The arranging dialog](base/chrome/README.md#the-arranging-dialog)** -
  what writes that arrangement,
  opened from the bar and from nowhere else.
  Its rules,
  its modality and the box it stands in are [the map chrome README](base/chrome/README.md)'s.
- **[Installed machinery](base/machinery/README.md)** -
  one sector's map machinery as a thing a caller can hold,
  since everything the layers draw is derived from one sector
  and everything under that drawing is keyed by bare system id.
  `SectorMapMachinery` holds the refresh board,
  the motion tracker,
  the hover holder and the sector itself;
  what a *layer* derives from the sector goes in through `InstalledMachinery`,
  so machinery owns those lifetimes without naming the packages downstream of it.
  `SectorMapMachineryIndex` indexes one per sector and settles the lifetime -
  replace on reinstall,
  release on removal,
  discard every one on load.
- **[The render surface](base/render/README.md)** -
  `MapLayerRenderer`,
  the seam a layer draws through -
  its overlay and the hover box over one cell of it -
  and the terrain that owns the map's render pass.
  It asks the drawn layer for a renderer and hands it the frame,
  so it names no layer;
  a layer that only switches (No Layer) supplies none,
  which is read as nothing to draw.
  Which sector it draws it finds through its own terrain entity,
  that being the one handle a plugin rebuilt from the save has.
  More than one terrain surface can paint one frame,
  so the per-frame work behind a layer is claimed rather than assumed:
  `MapFramePreparationClaim` grants it to the first surface of that sector to reach each frame,
  one claim per machinery so two maps drawing in one frame each get their own.
  The cursor read is the exception,
  being taken per pass so the surface that drew last owns the answer.
  Where a second surface is a minimap its owner has parked off screen,
  the compatibility mode stops it rendering
  rather than arbitrating between the two passes it would otherwise contribute.
- **[Clusters](base/render/clusters/README.md)** -
  the shape work under that surface:
  turning shaped cells and opaque owner ids into borders,
  fills,
  and GL-ready runs.
  The cluster-border trace,
  the smoothing passes,
  the vertex packing,
  and the split fill that puts several fills inside one border -
  none of which interprets a key.
- **[What the map may say](base/visibility/README.md)** -
  what a layer is allowed to state about a place,
  as against what the sector holds.
  Which star systems are drawn at all
  (`MapVisibility` and the pass, rules, fingerprint and positions around it),
  and beneath that the three disjoint families of place -
  a colony (it carries a market),
  a structure (it carries `Tags.OBJECTIVE`),
  and a market-less installation -
  each with its own knowledge and its own rule rather than a union type over places.
  The register they share is there too:
  one store per family key,
  the recency triad every concealed fact is stated in,
  and the single note a row is dated by.
  Which sector fact reaches which surface,
  and which way each default errs,
  are that README's.
- **[Map build profiling](base/profiling/README.md)** -
  the counters a rebuild's stages add to and the terms a rebuild step registers its section on,
  beneath both the geometry and the render so neither imports the other to name them.
- **[Cell geometry](base/geometry/README.md)** -
  the cells,
  edges,
  and clusters any painting layer is shaped out of,
  partitioned from the drawn systems and cached against them.
- **[Cluster-name overlay](base/labels/README.md)** -
  where a name is placed across a cluster and how it is drawn.
  What the name reads and what shade it takes arrive from the layer as functions of an owner,
  so the overlay names nothing itself.
- **[The theme records](base/theme/README.md)** -
  the player's appearance choices as inert value types,
  read once per rebuild,
  plus the `ElementStyleAdjustment` a rebuild lays over one of them to recede an element.
  The per-category tier is keyed on an open interface,
  so a layer brings its own categories and its own reader to populate them.
- **[Hover](base/hover/README.md)** -
  what the cursor is over and what the map says back:
  the published hover one pass writes and the later ones read,
  the parks a frame owes it,
  the moment the cursor reaches a cell,
  and which frames may answer the cursor at all.
- **[Map covers](base/hover/cover/README.md)** -
  whether anything is drawn over the map where the cursor rests,
  which a layer asks before resolving a hover at all:
  the set,
  its order,
  and which of them fail open.
- **[The hover box](base/tooltip/README.md)** -
  the box floating beside the cursor:
  what a layer states about a cell,
  how deep the player asked the box to read,
  and how a statement becomes a box that fits the screen.
- **[Refresh](base/refresh/README.md)** -
  what says a cached overlay has gone stale
  (`MapLayerRefreshSignal`, `MapLayerRefreshBoard`),
  the throttled poll that finds the changes the engine announces to nobody
  (`StalenessPollLoop` over a `MapLayerStalenessSource`),
  why the two scripts that drive it are two classes,
  and the one poll that is nobody's layer -
  the substrate's sweep of what each system's inhabitants can see.
  The signals,
  who declares which,
  and the four rebuild paths they drive are [the caching notes](../../../../../docs/dev/caching.md).
- **[The sidebar](base/sidebar/README.md)** -
  the control box:
  placement,
  the opener past its last tab,
  and the state its lists and its fold are kept in.
  Two leaves under it -
  [`runtime`](base/sidebar/runtime/README.md) for what differs per screen,
  what stands the panel down,
  and how it is drawn over and routed ahead of the vanilla screens;
  and [`style`](base/sidebar/style/README.md) for what it is painted in
  and how loudly it answers the pointer.
- **[Map chrome](base/chrome/README.md)** -
  the controls the player moves the layers with from outside the sidebar:
  the tick box appended to the vanilla filter row,
  the dialog the layer bar is arranged in,
  and the standing heal that keeps each screen's pick on a tab its bar still offers.
- **[Political map](politicalmap/README.md)** -
  the one layer that paints,
  its three views,
  and the draw pipeline behind them.
- **`MapLayers`** -
  the composition root,
  the single place every concrete layer,
  political-map view,
  specially treated entity and mod-supplied world fact is named and registered,
  so the framework below stays ignorant of which ones exist.
  The live alliance set is one such fact:
  it is wired past the same mod-enabled gate the alliances view is chosen behind,
  so an install without that mod reads a rule with nothing registered.
