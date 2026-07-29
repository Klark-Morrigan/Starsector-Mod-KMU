# Caching in KMU

What KMU keeps between frames, what makes each piece of it stale, and how narrowly
each rebuild is scoped. Almost all of it belongs to the political map, which is
the one feature that derives an expensive drawing from live campaign state and
then repaints it every frame.

The shared library's caches are a separate and much simpler problem (font assets
and pure derivations, none of which can go stale); they are documented in
[KMLib's caching notes](https://github.com/<owner>/KMLib/blob/main/docs/dev/caching.md),
including the `Fingerprints` primitive this document's revision counters fold
through.

## Index

- [The shape of the problem](#the-shape-of-the-problem)
- [Layer 1: what notices a change](#layer-1-what-notices-a-change)
- [Layer 2: the signals](#layer-2-the-signals)
- [Layer 3: the caches](#layer-3-the-caches)
  - [The overlay cache](#the-overlay-cache)
  - [Cell geometry](#cell-geometry)
  - [Territories and draw lists](#territories-and-draw-lists)
  - [Hover lookups](#hover-lookups)
  - [Sidebar bloc lists](#sidebar-bloc-lists)
  - [Per-rebuild memos](#per-rebuild-memos)
- [The four rebuild paths](#the-four-rebuild-paths)
- [Persistence: none of it is saved](#persistence-none-of-it-is-saved)
- [Rules](#rules)
- [Related reading](#related-reading)

## The shape of the problem

Drawing the political map from scratch means partitioning every drawn system into
Voronoi cells, resolving who dominates each system out of the economy, classifying
every cell edge as an interior seam or a national border, shaping each cell into
its cluster polygon, tracing the cluster border rings, and fitting a name into
each cluster. The partition alone is O(n^3) in the number of systems.

The map is repainted every frame it is open, and the layer renderer's refresh runs
on that same per-frame cadence. So the design is the inverse of "recompute what is
shown": everything drawn is held, and the work per frame is deciding which small
part of it - usually none - has to be built again.

Three properties make that safe:

- **System positions are fixed for the life of a save.** So the cell partition is
  built once and only reconciled when the *set* of drawn systems changes. Systems
  that move are the exception, and are excluded from the partition rather than
  chased (see `MovingSystems`).
- **Ownership changes are local.** Dominance is decided per system from the
  colonies seated in it, so a colony event can only shift its own system - which
  makes a targeted re-shape of that system and its neighbours possible instead of
  a whole-map rebuild.
- **Every input that can change announces itself as a counter.** So checking
  whether anything is stale is a handful of int compares, never a scan.

## Layer 1: what notices a change

Two mechanisms, deliberately overlapping.

**Event listeners** ([`refresh/listeners/`](../../src/main/java/kmu/maplayers/politicalmap/base/refresh/listeners/))
react to the economy events the engine does fire: colonisation, colony resize,
decivilisation, market discovery, market transfer. Each resolves the market's
seated system and marks exactly that system stale, through the one shared
translation in `MarketPoliticsRefresh` so every listener applies the same guard and
emits the same log line.

**The sector watcher**
([`PoliticalMapSectorWatcher`](../../src/main/java/kmu/maplayers/politicalmap/base/refresh/PoliticalMapSectorWatcher.java))
catches everything the engine fires no event for - a gate activating, a system
being cut off, a dead colony surveyed, an AI faction quietly capturing a colony.
It takes one cheap snapshot per poll
([`PoliticalMapSectorSnapshot`](../../src/main/java/kmu/maplayers/politicalmap/base/refresh/PoliticalMapSectorSnapshot.java))
holding a scalar fingerprint of *which* systems are drawn and a map of *who* holds
each, then reacts to each half on its own axis: the fingerprint moving means the
geometry is stale (whole-map, since the partition depends on every site), while the
owner map is diffed to mark exactly the systems whose owner changed. It also tracks
the moving-system set and fingerprints the live alliance set.

The overlap is intentional. Both feed the same stale-system set, so a change a
listener already marked and one the watcher's diff re-discovers collapse into a
single re-shape - and a change no listener ever saw is still caught, just one poll
later.

## Layer 2: the signals

Every producer above writes into one of these; every cache below reads them. None
of them is a boolean - a counter composes and cannot be cleared out from under a
second reader.

| Signal | Home | Bumped by | Read by |
| --- | --- | --- | --- |
| `geometryRevision` | [`PoliticalMapRefresh`](../../src/main/java/kmu/maplayers/politicalmap/base/refresh/PoliticalMapRefresh.java) | the drawn-system set or moving-system set changing | the geometry cache |
| stale-system id set | `PoliticalMapRefresh` | colony events + the watcher's owner diff | the incremental politics refresh |
| `allianceRevision` | `PoliticalMapRefresh` | the alliance-set fingerprint moving | the alliances view only |
| `recedeStyleRevision` | `PoliticalMapRefresh` | the Mute / Desaturate sidebar toggles | the pipeline, under any view (the receded blocs and decivilised ground), plus the alliances view for its own non-allied recede |
| `filterRevision` | `PoliticalMapRefresh` | picking or clearing the spotlight bloc | the pipeline, under any view |
| `mapStyleRevision` | `PoliticalMapRefresh` | the uninhabited-outline and name-format toggles | the pipeline, under any view |
| `settingsRevision` | [`KmuLunaSettings`](../../src/main/java/kmu/settings/KmuLunaSettings.java) | any LunaLib settings change | the territories rebuild |
| content revision | each `PoliticalMapView` | the view's own live inputs, folded via `Fingerprints` | the territories rebuild |

Two of those deserve their reason stated.

The **sidebar toggles** (recede, filter, spotlight, outline, name format) live in
sector memory rather than as LunaLib fields, so flipping one does *not* bump
`settingsRevision`. Each setter bumps its own counter instead, which is what makes
those toggles repaint the overlay live despite never touching the settings screen.

The **content revision** is the seam that keeps the shared pipeline from naming any
concrete view: a view folds its own live inputs into one int, so the alliances view
can invalidate on an alliance-membership change while the faction view - which
samples nothing live - returns a constant and never forces a rebuild on its own.

## Layer 3: the caches

### The overlay cache

[`PoliticalMapCache`](../../src/main/java/kmu/maplayers/politicalmap/base/render/PoliticalMapCache.java)
is the owner: the political map's painter holds one and asks it to `refresh` each
frame the map is open. It holds both halves (geometry and built draw lists) plus the
revisions each half was built against, and rebuilds only the stale half.

It is also where rebuild faults are contained: a failing rebuild is logged once
rather than per frame, leaves the cached revisions un-advanced so the next frame
retries, and installs an empty placeholder so the renderer never dereferences a
null draw list. The last good draw lists stay on screen in the meantime.

### Cell geometry

[`PoliticalMapGeometryCache`](../../src/main/java/kmu/maplayers/base/geometry/PoliticalMapGeometryCache.java)
holds the raw Voronoi cells keyed by system id. It is updated by *diffing* the
reachable set against what it holds and rebuilding only the affected cells: adding
or removing one site changes that site's cell and the cells within twice the cell
radius, and every farther cell is provably untouched.

Two seed inputs bypass the diff and force a full reseed, because each changes every
cell: the frontier resolution and the cell reach. So do the dev reveal overrides,
which change *which* systems seed a cell at all.

The cache holds raw cells, not shaped outlines. The inset that gives a cluster its
border channel is ownership-dependent, so it belongs to the render pass - which is
what lets ownership change without touching this cache at all.

### Territories and draw lists

[`PoliticalMapTerritories`](../../src/main/java/kmu/maplayers/politicalmap/base/render/territories/PoliticalMapTerritories.java)
holds what a full rebuild produced: the styled cells and faction territories the
renderer paints, plus the derivation inputs an incremental re-shape needs - the
styling, the view grouping, the filter snapshot, and who holds each system.

Retaining those inputs is what makes the incremental path *correct* rather than
merely cheap:
[`IncrementalPoliticsRefresh`](../../src/main/java/kmu/maplayers/politicalmap/base/render/IncrementalPoliticsRefresh.java)
re-classifies a handful of cells against exactly the ownership and styles the full
build used, through the same builder primitives, so an incrementally-updated map is
indistinguishable from a rebuilt one.

### Hover lookups

[`SystemClusterIndex`](../../src/main/java/kmu/maplayers/base/geometry/SystemClusterIndex.java)
indexes each cluster by its members once per rebuild, so the hover highlight
resolves a hit system to its whole contiguous territory with a lookup instead of
re-running the cluster search per frame. Deriving it from the same clustering the
map drew is what keeps the highlighted region identical to the region that carries
the name.

### Sidebar bloc lists

[`SelectableBlocCache`](../../src/main/java/kmu/maplayers/politicalmap/base/sidebar/SelectableBlocCache.java)
memoises the selected view's picker options with their stats. The picker resolves
its options twice a frame (render and hit-test) and each resolve is a full grouped
dominance pass over the sector, so the memo is what keeps the sidebar from
rescanning the economy several times a frame.

Its key is unusual in two ways worth knowing:

- It includes the **sector identity**, held through a `WeakReference`, so a save
  reloaded in the same session recomputes against the loaded economy instead of
  serving the previous save's blocs - and a cached sector never outlives its
  unload.
- It deliberately **excludes** the filter revision. The list is which blocs are
  selectable, not which one is spotlighted, so picking or clearing a filter moves
  the lit row without invalidating the list.

Because the economy can drift between rebuild triggers, a displayed metric can lag
until the next settings, view, or alliance change - the same cadence the overlay's
own rebuild reconciles on, so the picker's numbers and the painted map stay in step
with each other even when both lag the economy slightly.

### Per-rebuild memos

Smaller memos live for one rebuild and die with it. `ClusterLabelStyling` resolves
each bloc's style decision and name estimator once per bloc id rather than once per
cluster, since every cluster of a bloc shares one name and one style; the label font
and the name-format choice are likewise read once per rebuild rather than per label.

The map's name labels are the one place KMU mints its own GL text. Each
[`Label`](../../src/main/java/kmu/maplayers/politicalmap/base/render/labels/Label.java)
owns a `DrawableString` and its vertex buffer, and `LabelsBuilder` **disposes** the
standing list whenever it rebuilds. This is the opposite lifetime to KMLib's
process-lifetime glyph cache, and the two must not be crossed: text that a rebuild
disposes has to be minted by the builder that disposes it, never fetched from a
shared cache that would keep handing out the disposed buffer.

## The four rebuild paths

Per frame, in increasing cost:

1. **Nothing changed** - a few int compares against the cached revisions, and the
   standing draw lists are handed to the renderer. This is the overwhelmingly
   common path.
2. **Systems marked stale** - the stale set is drained and only those systems and
   their neighbours are re-derived and re-shaped, with the two affected factions'
   territories rebuilt.
3. **Content changed** (a setting, a sidebar toggle, a view's own live input) - the
   territories are rebuilt in full over the standing geometry. The cells are
   untouched, since ownership and styling do not move a border.
4. **Geometry changed** (the drawn set, a seed input, a reveal override) - the
   partition is reconciled, which forces a full territories rebuild after it.

## Persistence: none of it is saved

Everything described here is derived from the sector and holds record types
XStream cannot serialise. None of it can reach a save: the cache hangs off the
political map's layer renderer, which is reached through the registered layer and
so is never serialised - no transient marking required.

That lifetime is longer than a sector's, though, since a player can load a second
save without restarting. Nothing else would notice the change of sector: the
revision counters are process-wide and do not move across a load, and the geometry
cache reconciles by diffing system *ids*, so a system present in both saves at a
different position reads as unchanged. So the cache is discarded whole on load
rather than reconciled - `discardStateFromPreviousSave`, called from `onGameLoad`
beside the sidebar folds, which are process-lifetime singletons for the same
reason. Every revision then starts at a rebuild-forcing seed, so the first frame
against the new sector rebuilds both halves from scratch.

What *is* persisted is only the player's choices that feed it - the active layer,
the sidebar fold, the spotlight selection, the shared toggles - and those live in
sector memory as small scalars.
[`PoliticalMapSaveMigrations`](../../src/main/java/kmu/maplayers/politicalmap/base/PoliticalMapSaveMigrations.java)
is how one of those keys is migrated when its shape changes, since an old save
carries the previous shape.

## Rules

- Derived state is transient. If it can be rebuilt from the sector, it must not be
  written into the save - a persisted class name is a migration liability, and a
  persisted derivation is a stale one.
- Announce a change with a monotonic counter, never a boolean flag. Several
  consumers read at different cadences, and the first reader must not be able to
  clear the signal for the rest.
- Mark the narrowest thing that changed. A colony event names its own system; only
  a change to the drawn *set* is allowed to invalidate the partition.
- The per-frame path must stay int compares. Any new live input needs a revision
  of its own folded into a fingerprint - never a per-frame scan to detect change.
- An incremental update must read back the same inputs the full build used, and go
  through the same primitives, so the two cannot diverge.
- Whoever mints a GL buffer disposes it. Do not route disposable text through a
  shared, never-evicting cache.

## Related reading

- [KMLib caching notes](https://github.com/<owner>/KMLib/blob/main/docs/dev/caching.md) -
  the font and glyph caches KMU draws through, and the `Fingerprints` primitive.
- [Political map](../../src/main/java/kmu/maplayers/politicalmap/README.md) - what
  the overlay shows and how it is drawn.
- [Cell geometry](../../src/main/java/kmu/maplayers/base/geometry/README.md) -
  the cells, edges, and clusters the geometry cache holds.
- [Territory fills and borders](../../src/main/java/kmu/maplayers/politicalmap/base/render/territories/README.md) -
  what a territories rebuild actually produces.
