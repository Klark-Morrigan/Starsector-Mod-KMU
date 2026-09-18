# Political Map Layer Reference

Research date:
2026-05-17

Scope:
feasibility and API surface for a sector-map overlay that paints faction territory in faction colours.
Two views are in scope:

1. A full-sector layer with every faction shown,
   with the option to highlight one selected faction.
2. A per-faction view (one faction at a time).

Sources read directly:
extracted `starsector-core/starfarer.api.zip`,
`Nexerelin-0.12.1e/jars/ExerelinCore.jar`,
`LazyLib.jar`,
`MagicLib.jar`
(both classes and bundled `.java`/`.kt`),
plus a quick survey of `stelnet.jar`.

## Index

- [What "the starmap" actually is](#what-the-starmap-actually-is)
- [Render-surface options](#render-surface-options)
- [Recommended approach](#recommended-approach)
- [Intel screen as the selector UI](#intel-screen-as-the-selector-ui)
- [Library support (LazyLib, MagicLib)](#library-support-lazylib-magiclib)
- [Data sources for territory](#data-sources-for-territory)
- [Shape strategy candidates](#shape-strategy-candidates)
  - [Visual model (under Candidate A)](#visual-model-under-candidate-a)
  - [Presence tiers (under Candidate A)](#presence-tiers-under-candidate-a)
- [State, persistence, lifecycle](#state-persistence-lifecycle)
- [Political history (Civ 3-style timeline)](#political-history-civ-3-style-timeline)
- [Risks and gotchas](#risks-and-gotchas)
- [Open questions](#open-questions)

## What "the starmap" actually is

The "M" key opens `CoreUITabId.MAP` ([CoreUITabId.java]).
There is no public "map layers" API.
The map screen is the same campaign engine view,
rendered with a zoomed-out viewport on the hyperspace location.
Any drawing that the campaign engine does — fleets,
planets,
terrain,
custom entities — appears there.

That means the lever we have is **draw something in hyperspace world space at each render call**,
and the M-key map shows it for free.

Relevant types:

- `CampaignEngineLayers` ([CampaignEngineLayers.java]) lists the per-frame render layers;
  `TERRAIN_1..TERRAIN_10`,
  `BELOW_STATIONS`,
  `ABOVE`,
  etc. A political tint belongs under everything else,
  so `TERRAIN_1` or `TERRAIN_2`.
- `CustomCampaignEntityPlugin.render(CampaignEngineLayers, ViewportAPI)` ([CustomCampaignEntityPlugin.java]) is called once per layer per frame.
  The entity is the anchor;
  rendering happens at world coords,
  no translation applied.
- `LocationAPI.addCustomEntity(...)` and the `custom_entities.json` "type" entry install one.
  The plugin class is reflection-loaded from that JSON.
- `CampaignTerrainPlugin`
  (heavier; used for `BaseTerrain` subclasses) is the vanilla pattern for sector-wide "fog" things.
  Overkill here — terrain ties into sensor/fog/interdiction logic that we do not want.
- `CampaignUIRenderingListener` ([CampaignUIRenderingListener.java]) gives `renderInUICoordsBelowUI`,
  `renderInUICoordsAboveUIBelowTooltips`,
  and `renderInUICoordsAboveUIAndTooltips` callbacks.
  UI coords only — you would have to re-project hyperspace world → screen yourself
  and re-implement clipping under the map chrome.
  Not recommended for world-space tinting;
  useful for adding a legend or HUD bar over the map.

## Render-surface options

| # | Approach                                  | World-space | Effort | Notes |
|---|-------------------------------------------|-------------|--------|-------|
| 1 | Per-system invisible custom entity in hyperspace, `render()` paints a disc/hull | yes | low    | Recommended. Renders on M-map and on zoomed-out campaign view. |
| 2 | One global custom entity with huge `getRenderRange()` | yes | low    | Engine still culls; one entity off-screen means no draw. Per-system avoids this. |
| 3 | Custom `CampaignTerrainPlugin` per faction | yes        | medium | Honest "territory" with shape, but pulls in terrain semantics we don't want. |
| 4 | `CampaignUIRenderingListener` over the map | no         | medium | Need manual world→screen projection. Good for legend/overlay UI only. |
| 5 | Render only inside an intel `createLargeDescription` panel | n/a        | low    | Already needed for the per-faction selector — see [Intel screen as the selector UI](#intel-screen-as-the-selector-ui). |

## Recommended approach

Two surfaces,
one shared drawing routine:

1. **World overlay** — one anchor entity per inhabited system in hyperspace
   (`addCustomEntity` of a `kmu_political_marker` type, tagged `Tags.NON_CLICKABLE`, no sprite).
   Its plugin's `render()` implements the layered draw from [Visual model](#visual-model-under-candidate-a) -
   Voronoi cell at low alpha,
   dog-bone connectors per faction,
   blip stack on top.
   Skip render if `viewport.getAlphaMult() == 0` and `isRenderWhenViewportAlphaMultIsZero()` is false -
   that flag is the public switch for "should this draw during map transitions".
2. **Intel panel** — a `BaseIntelPlugin` with `hasLargeDescription() = true`.
   `createLargeDescription(CustomPanelAPI, w, h)` builds a left-side faction list (buttons) and a right-side `CustomPanelAPI`
   whose plugin reuses the same draw routine in panel-local coords.

Selecting a faction in the intel panel writes `Sector.getMemoryWithoutUpdate().set("$kmu_political_highlight_faction", id)`.
The world-overlay plugins read that each frame.

**Highlight view (v1).** When set,
only render systems where the selected faction has *any* presence (live or decivilised, any tier).
The standard visual model applies within those systems -
same cell,
same blip stack,
same connectors.
Systems with no presence from the selected faction draw nothing at all.
A more "pure" highlight view
(e.g. desaturate non-selected factions, isolate the selected faction's stripe within mixed systems) is deferred past v1.

## Intel screen as the selector UI

Pattern lifted from vanilla `MapMarkerIntel` ([MapMarkerIntel.java]) and `FactionHostilityIntel` ([FactionHostilityIntel.java]);
per-faction list construction follows Nexerelin's `Nex_FactionDirectory` (in `ExerelinCore.jar`).

- Implement `BaseIntelPlugin` ([BaseIntelPlugin.java]):
  - `getIcon()` — small icon for the intel list.
  - `createIntelInfo(...)` — list row (title + one-line summary).
  - `hasLargeDescription() = true` and `createLargeDescription(...)` — full panel with the political map.
  - `getIntelTags(SectorMapAPI)` — return a custom tag like `"Political map"`
    so it gets its own filter chip in the intel sidebar.
  - `getMapLocation(SectorMapAPI)` — return `null`
    (no marker on the intel inset map; the intel itself *is* the map).
  - `getFactionForUIColors()` — for the highlight colour;
    can be the currently selected faction or `null` (white).
- Add the intel once per save in `ModPlugin.onGameLoad(boolean)` via `Sector.getIntelManager().addIntel(plugin, true)` (true = no popup).
- For the panel layout:
  - `panel.createUIElement(...)` for the faction list (left),
    one `addButton` per faction with `FactionAPI.getBaseUIColor()` / `getDarkUIColor()`.
  - `panel.createCustomPanel(w, h, mapPanelPlugin)` for the map area;
    the plugin implements `CustomUIPanelPlugin` ([CustomUIPanelPlugin.java]) and does the GL drawing in `render(alphaMult)` using `position.getX/Y/Width/Height`.
    `ExampleCustomUIPanel` ([ExampleCustomUIPanel.java]) is the canonical skeleton.
- Re-open behavior:
  `IntelUIAPI.recreateIntelUI()` after the user clicks a faction button.

stelnet (`stelnet.jar`) is the reference for "tabbed sub-views inside the intel screen" if a tab layout is wanted later;
for now,
a single intel item with a custom large description is enough.
MagicLib's `org.magiclib.bounty.intel.BountyBoardIntelPlugin` (Kotlin source bundled in the jar) is a closer real-world precedent:
it builds a multi-section intel screen with a filter bar,
a scrolling list,
and a detail panel — the same shape this feature needs.

## Library support (LazyLib, MagicLib)

KMU currently only depends on lazywizard's Console
(used in `kmu/mods/console/commands/...`);
`mod_info.json` declares no `dependencies` block.
Adding this feature is the right moment to decide
whether to take a hard or soft dep on the two ubiquitous libs.

What each lib actually provides for this feature:

- **LazyLib** (`org.lazywizard.lazylib`)
  - `opengl/DrawUtils.drawCircle(cx, cy, r, segments, filled)` — exactly the primitive needed for the per-system disc.
    Removes the need to hand-roll the trig + `glBegin(GL_TRIANGLE_FAN)` loop.
  - `opengl/ColorUtils.glColour(Color, alphaMult, overrideOriginalAlpha)` — sets `glColor4ub` from an AWT `Color`.
    When the third arg is `true` the final alpha is `alphaMult * 255` directly;
    when `false` it multiplies the source colour's alpha by `alphaMult`.
    Removes the byte-cast boilerplate seen in `ExampleCustomUIPanel`.
  - `MathUtils` — distance/point-along helpers;
    **no convex-hull or Voronoi primitive**.
    If we go with per-constellation hulls in a later version,
    that algorithm is still ours to write.
  - `campaign/CampaignUtils` — relationship/faction helpers;
    nothing the overlay strictly needs,
    but useful if we later filter by hostility.
- **MagicLib** (`org/magiclib/...`)
  - `org.magiclib.achievements.CampaignCustomRenderer` exists and uses exactly the pattern we want
    (`BaseCustomEntityPlugin` + `render(CampaignEngineLayers, ViewportAPI)` + huge `getRenderRange`),
    but it is declared `internal` (Kotlin) and scoped to achievements.
    **Confirms the pattern**,
    does not give us a reusable surface.
  - `data.scripts.util.MagicSettings` — JSON-backed mod config helper
    (`MagicSettings.json` in `data/config/`).
    Lets us add an enabled/disabled toggle without pulling in LunaLib.
  - `data.scripts.util.MagicUI` and `data.scripts.util.MagicRender` are combat-screen oriented;
    not relevant to the campaign overlay.
  - `BountyBoardIntelPlugin` — see above,
    reference for the intel screen.

Recommendation:
take a **hard dep on LazyLib** (use `DrawUtils` and `ColorUtils`),
and a **soft dep on MagicLib** gated at load time
(`Global.getSettings().getModManager().isModEnabled("MagicLib")`).
MagicLib buys us `MagicSettings` for the toggle and the `BountyBoardIntelPlugin` reference,
but nothing on the critical path;
LazyLib's draw primitives are on the critical path and trivial to depend on.

If the dep stance is "no new deps for a v1",
roll the disc draw inline (8-line trig loop) and put the toggle in `Sector.getMemoryWithoutUpdate()` behind a console command — defer libs to v2.

## Data sources for territory

- `Sector.getAllFactions()` then filter to factions with at least one market:
  `Misc.getFactionMarkets(faction)` ([Misc.java]).
- For each `StarSystemAPI` in `Sector.getStarSystems()`:
  - `Misc.getMarketsInLocation(system)` ([Misc.java:960]) gives all markets;
  - hyperspace location = `system.getHyperspaceAnchor().getLocation()` (or `system.getLocation()`).

### Dominance rule

Per system,
per faction:

1. **Primary metric.** Sum `MarketAPI.getSize()` over that faction's markets in the system.
   Decivilised entities do not appear in `Sector.getEconomy().getMarketsCopy()`
   (vanilla removes them on deciv, see [Decivilised entities are factionless](#decivilised-entities-are-factionless)) and are not represented on the political layer at all -
   so no exclusion branch is needed here.
2. **Lexicographic tie-breaker.** Build a sorted list of each tied faction's market sizes,
   with planets ranked above stations at the same size value
   (so a size-4 planet beats a size-4 station).
   Compare the lists element-wise;
   the first index where they differ decides.
3. **Final tie-breaker.** Faction ID ascending.
   Deterministic,
   never matters in practice.

The result of this step is `Map<SystemId, SystemPolitics>` where `SystemPolitics` carries:

- `dominantFactionId: String` (the winner above)
- `presentFactionIds: List<FactionPresence>` sorted descending by metric,
  one entry per faction with any presence (live or decivilised)
- where `FactionPresence` records the faction ID and its top tier
  (see [Presence tiers](#presence-tiers-under-candidate-a))
- Faction colour:
  `FactionAPI.getBaseUIColor()` (full saturation),
  `getDarkUIColor()` (panel chrome).
  The "highlight" view uses base for the selected faction
  and a heavily desaturated/multiplied tint for the rest.
- Optional grouping:
  `LocationAPI.getConstellation()` ([LocationAPI.java:130]) to draw per-constellation hulls instead of per-system discs.

## Shape strategy candidates

What the per-system anchor plugin actually draws.
Orthogonal to the render surface choice in [Recommended approach](#recommended-approach) -
all three candidates use the same per-system custom-entity anchor.

### Candidate A: Voronoi cells (leading)

Partition hyperspace by perpendicular bisectors between inhabited systems,
so each system "owns" the area closer to it than to any other inhabited system.
Color each cell by its system's dominant faction.

Why this is the leading candidate:

- Contiguity emerges for free.
  Two same-faction adjacent systems share a midline that has no colour change across it -
  the eye reads one region.
  Different factions across a midline get a crisp HOI4-style border.
- Static geometry,
  dynamic colours.
  System positions never move,
  so the diagram is built once at game load (or first map open) and cached for the life of the save.
  Market ownership changes only invalidate the `cellId -> factionId` lookup;
  polygons stay put.
  This matches the cache pipeline already implied by the fingerprint poll in [State, persistence, lifecycle](#state-persistence-lifecycle).
- No tunable radius.
  Disc-based approaches need a radius parameter that trades "discs overlap into mush" against "discs leave gaps";
  Voronoi has no such knob.

Design constraints to nail down:

1. **Max-cell radius clip.** Pure Voronoi extends convex-hull cells to infinity.
   Between far-apart constellations a single system would claim the void.
   Clip each cell with a max radius
   (start at ~6000 units, on the order of half the typical inter-constellation gap in vanilla).
   Implement as half-plane intersection followed by intersect with a regular n-gon approximating the bounding disc.
   Deep void stays black;
   isolated systems get a bounded disc-ish cell.
2. **Seed set.** Inhabited systems only
   (anything in `Sector.getEconomy().getMarketsCopy()` whose `MarketAPI.getStarSystem()` is unique).
   Uninhabited systems do not seed cells -
   they fall inside the nearest inhabited system's cell,
   which reads as "wilderness inside that faction's sphere of influence".
   Alternative
   ("all systems, neutral grey for uninhabited") is noisier and rejected unless playtest demands.
3. **Algorithm.** Half-plane intersection per site:
   for each seed S,
   intersect the half-planes `{p | dist(p, S) <= dist(p, T)}` for every other seed T,
   then intersect with the max-radius polygon.
   O(n^2) per site,
   O(n^3) total.
   With ~50-150 inhabited systems across vanilla+Nex this is sub-millisecond,
   runs once at game load.
   ~80 lines,
   no external dependency.
   Fortune's algorithm is not worth the complexity at this n.
4. **Render.** Each cell is a convex polygon.
   `GL_TRIANGLE_FAN` from the seed (or centroid) gives a filled cell;
   optional `GL_LINE_LOOP` outline in the dark UI colour reads as a border.
   Each per-system anchor draws its own cell.

Trade-off to be aware of:
Voronoi cells *imply* the faction claims all the space up to the midline,
including any uninhabited systems inside the cell.
That is accurate to HOI4-style political cartography
but is a stronger statement than "this faction has a market in this system".
To soften this we keep the cell fill subdued
and let the per-system blip stack carry the dominant signal
(see [Visual model](#visual-model-under-candidate-a)).

### Visual model (under Candidate A)

The cell is the *quiet* background layer;
the system blips are the *prominent* foreground.
Players read territory at a glance from cell silhouette,
then read mixed-faction systems from the blip stack.

Per system,
draw in this order (back to front):

1. **Province fill.** The Voronoi cell,
   filled in the dominant faction's `FactionAPI.getBaseUIColor()` at a low alpha (start ~0.20-0.25).
   Low alpha keeps the layer perceptual background
   and limits the muddying that compounds when neighbouring cells of different colours meet at a midline.
   Per-tier alpha modifier -
   see [Presence tiers](#presence-tiers-under-candidate-a).
2. **Dog-bone connectors.** For each ordered pair of inhabited systems that share a constellation
   (or are within an adjacency-distance threshold - constellation is simpler and probably enough),
   and each faction that has *any* presence in **both** systems,
   draw a capsule between the system positions in that faction's colour.
   Connectors are per-faction,
   not per-system:
   Hegemony presence in A and B yields one Hegemony capsule even if Tri-T also has presence in both
   (which yields a second Tri-T capsule, drawn in their order in the blip stack so the dominant pair's connector lands underneath).
   Capsule alpha matches province fill.
3. **Blip stack.** At the system position,
   stack one filled circle per faction with presence,
   **largest first at the bottom**.
   Order is descending by the dominance metric,
   so the dominant faction is the bottom disc and minority factions sit visibly on top.
   - Bottom (dominant) disc radius = `R_base` (start ~60 world units).
   - Each subsequent disc radius shrinks by a fixed step
     (e.g. `0.75 * previous` or `R_base * (1 - 0.2 * index)`, exact curve tuned in playtest).
     Stack stops growing once radius drops below a floor.
   - Result:
     a one-faction system reads as a single disc;
     a two-faction system reads as a bullseye with the minor faction's colour visible as a smaller centered disc on top of the dominant disc.
   - Blip alpha is high (start ~0.85+) so the layer reads as a definite marker,
     not a tint.

Render-state notes:

- Standard alpha blend
  (`GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA`).
  Additive blending would brighten overlaps unrealistically.
- Overlap is unavoidable across cells (where two factions' connectors cross) and within the blip stack.
  Keep fill/connector alpha low to minimise colour drift in those overlaps;
  the user has explicitly accepted some drift in exchange for the layering.
- Render order across systems:
  cells first (all systems),
  then all connectors,
  then all blip stacks.
  This requires the anchor-plugin approach to either (a) use distinct `CampaignEngineLayers` slots for each pass,
  or (b) accept per-system-local ordering.
  Option (a) is cleaner:
  cell pass on `TERRAIN_1`,
  connectors on `TERRAIN_2`,
  blips on `BELOW_STATIONS`.
  Vanilla's `CampaignEngineLayers` exposes enough slots for this.

### Presence tiers (under Candidate A)

A single faction's presence in a system is classified into the highest tier it qualifies for.
The tier controls how that faction's contribution is rendered (cell fill style, blip fill style)
but not its position in the dominance order.

| Tier | Trigger                                  | Cell fill     | Blip fill     |
|------|------------------------------------------|---------------|---------------|
| 1    | Any live planet market                   | Solid         | Solid         |
| 2    | Live stations only (no live planet)      | Weaker solid  | Weaker solid  |
| 3    | RAT/KMO settlements only (player-only)   | Outline only  | Outline only  |

"Weaker" means lower alpha
(e.g. tier 2 = 0.65 of tier 1's alpha; exact ratios tuned in playtest).

For each (system, faction),
evaluate tiers 1 -> 3 and take the first that matches.
Tier informs render;
dominance metric (above) is independent.

Stripes / diagonal patterns are intentionally *not* used by the political layer.
They are reserved for a future warfare/invasion overlay that may share threat-tracking code with feature 21 -
the political layer wants the visual vocabulary kept clean for that later use.

#### Decivilised entities are factionless

`DecivTracker.decivilize`,
in the game install's `starfarer.api.zip`,
sets the market faction to `"neutral"`,
sets all connected entities' factions to `"neutral"`,
and removes the market from the economy.
Abandoned stations created during deciv inherit the same neutral faction.
**Vanilla does not preserve the original owning faction.** Rather than recovering it via a `ColonyDecivListener` hook,
the political layer treats decivilised planets and abandoned stations as out-of-scope:
they do not seed Voronoi cells,
do not appear in the blip stack,
do not trigger dog-bone connectors.
Players see ruins through sensors and intel;
the political map stays focused on living territory.

This sidesteps the historic-faction bookkeeping entirely -
no listener,
no entity-memory key,
no save-compat surface.

### Candidate B: Per-system disc

Each system draws a filled disc in its faction colour.
Radius chosen so clustered systems' discs overlap into a blob,
isolated systems read as spots.

- Pros:
  trivial implementation
  (`DrawUtils.drawCircle` from LazyLib or inline 8-line trig loop),
  tunable visual density.
- Cons:
  needs a radius parameter;
  adjacent same-faction systems either overlap messily or leave a visible seam;
  different factions' overlapping discs additively blend into a muddy mid-tone unless explicitly handled.
- Use if Candidate A's algorithmic cost is unacceptable for v1
  and the "soft influence" reading is preferred over hard borders.

### Candidate C: Disc plus dog-bone connector

Candidate B plus:
for each pair of same-faction systems within a distance threshold (or sharing a constellation),
draw a fat capsule between them at the same radius as the discs.
Two adjacent Hegemony systems read as disc-capsule-disc,
alpha-blending into one contiguous blob.

- Pros:
  cheap (a quad per edge),
  gives contiguous-territory feel without any hull algorithm,
  fully world-space.
- Cons:
  still needs the radius parameter and the adjacency threshold;
  three-way faction junctions can produce overlapping capsules of different colours that blend poorly;
  the visual is less precise than Voronoi when factions interleave within a constellation.

### Selection

Lead with **Candidate A (Voronoi)** in the plan.
The geometry pipeline
(`computeCells()` at load -> `Map<SystemId, ConvexPolygon>`) and the per-frame colour lookup
(`Map<SystemId, FactionId>` from the dominance cache) are independent;
if Voronoi proves too aggressive in playtest,
the render layer can swap to Candidate B/C without touching the data pipeline.

## State, persistence, lifecycle

- One-time wiring:
  register `custom_entities.json` entry `kmu_political_marker` mapping to the render plugin class.
  Done in `data/`,
  no code.
- Per-save wiring
  (in `KMU_ModPlugin.onGameLoad(boolean newGame)`):
  - Ensure exactly one anchor entity exists per inhabited system in hyperspace
    (idempotent — match by ID `kmu_pm_<systemId>`).
  - Re-create or repair the intel item if missing.
  - Refresh the anchor set and dominant-faction caches when the political picture changes.
    No vanilla listener fires on `MarketAPI.setFactionId`,
    so listeners alone cannot cover Nex transfers,
    raid takeovers,
    or mod-driven faction flips.
    Use a hybrid:
    - **Fingerprint poll (correctness backstop).** An `EveryFrameScript` hashes `systemId -> dominantFactionId` over `Sector.getEconomy().getMarketsCopy()`
      once every **1.0s** of in-game time.
      If the hash changes,
      rebuild the per-system colour/center cache.
      Cheap
      (a few hundred string hashes + XOR fold) and catches everything,
      including events we have no listener for.
    - **Listener prods (snappy response).** Wire `PlayerColonizationListener` (founding/abandoning),
      `ColonyDecivListener.reportColonyAboutToBeDecivilized` (decivilisation),
      and `ColonySizeChangeListener` (size flips that change dominance) to call `invalidate()` so common player actions refresh next frame instead of waiting up to a second.
    - For Nex-aware refresh,
      optionally listen for the `Nex_OnMarketTransferred` rules-based event (soft dep).
      The fingerprint poll catches Nex transfers anyway;
      the listener just shortens latency.
- Selected-faction state:
  `MemoryAPI` key `$kmu_political_highlight_faction`,
  scope = sector memory (`Sector.getMemoryWithoutUpdate()`).
  No XStream changes needed.
- Toggle for the world overlay (on/off):
  same memory map,
  key `$kmu_political_overlay_enabled`.
  Hotkey is optional;
  intel-screen toggle is enough for v1.

## Political history (Civ 3-style timeline)

A future timeline UI lets the player scrub through the political map's history.
Forward-looking research only -
the v1 feature does **not** include the UI;
v1 only records history so a later step can render the timeline.

The recorded data is the minimum needed to re-derive `SystemPolitics` for any past month.
Storage is a **change log**,
not a snapshot stream:
in Starsector the rate of political change is low
(vanilla generates ~zero monthly changes; Nex at peak war maybe a few systems per month; total lifetime change events even in a long campaign rarely exceed a few hundred).
Capturing one full snapshot per month would store the same state ~600 times over a 50-year run;
capturing only changes drops the entire history to ~2 KB.

### Model: change log with periodic snapshots

Four pieces,
all under one `PoliticalHistory` POJO held in `Sector.getPersistentData()`:

1. **Stable index tables (append-only):**
   - `factionIds: List<String>` -
     every faction observed by the political map.
     Faction's stable index = position in this list,
     encoded as one byte (factions <256 in practice).
     New factions append;
     entries are never removed or reordered.
   - `systemIds: List<String>` -
     same,
     for systems first seen as inhabited.
     One byte if <256 systems (typical),
     short above.
     A system that never becomes inhabited never enters the table.
2. **`events: List<ChangeEvent>`** -
   the full timeline.
   Each event:
   `{monthIndex: int, systemIndex: short, encodedPolitics: byte[]}`.
3. **`snapshotMonths: List<Integer>`** -
   the month indices at which the recorder emitted a **full-state burst** instead of a diff.
   One event per currently-inhabited system,
   all sharing that monthIndex.
   The first entry is the initial-capture month
   (see [Trigger and capture](#trigger-and-capture));
   subsequent entries land every 60 in-game months thereafter.
   Used for two things:
   - **Seek fast path.** To render month K,
     start from the latest `snapshotMonths` entry `<= K`,
     then apply events strictly between that snapshot's month and K.
   - **Corruption recovery.** If events between two snapshots become unreadable,
     the next snapshot resets state.
     Worst-case data loss between snapshots:
     60 months of history fidelity.
4. **Sentinel for uninhabited.** A system that loses its last market emits an event with a one-byte `encodedPolitics` of `0xFF`.
   The seek loop interprets this as "remove from the map".

### Encoding: per-event politics bytes

```plaintext
[dominantFactionIndex: 1 byte]
[presenceCount: 1 byte]   // 0 means "presence = [dominant only]"
[for i in 1..presenceCount:
  ((factionIndex << 2) | tier) : 1 byte]   // tier fits in 2 bits (3 tiers)
```

Typical one-faction system:
2 bytes encoded.
Two-faction:
3 bytes.
Three+:
4-5 bytes.
Plus the event header
(`monthIndex` int + `systemIndex` short = 6 bytes) = ~8-11 bytes per change event.

### Trigger and capture

`EconomyTickListener.reportEconomyMonthEnd()`
([EconomyTickListener.java](c:/a_Games/Starsector/.sources-cache/starsector-core/starfarer.api/com/fs/starfarer/api/campaign/listeners/EconomyTickListener.java)) fires once per in-game month with no args.
Register the history recorder via `Sector.getListenerManager().addListener(...)`.

Each tick:

1. Recompute the live `Map<SystemId, SystemPolitics>` from the current markets
   (this is the same cache the live political layer already maintains - see [State, persistence, lifecycle](#state-persistence-lifecycle)).
2. Determine capture mode for this tick:
   - **First boot**,
     `snapshotMonths` is empty -> snapshot mode.
     Take the full-state burst now,
     before the 60-month countdown begins.
   - `currentMonthIndex - snapshotMonths.last() >= 60` -> snapshot mode.
   - Otherwise -> diff mode.
3. **Snapshot mode:** for every inhabited system in the live map,
   append a `ChangeEvent` at `currentMonthIndex` carrying the encoded current politics.
   Append `currentMonthIndex` to `snapshotMonths`.
4. **Diff mode:** diff the live map against the previous month's map
   (held in memory only; reconstructable from the event log on load).
   For each system whose politics changed -
   including newly inhabited and newly uninhabited -
   append one `ChangeEvent` at `currentMonthIndex`.
5. Update the in-memory "previous map" to the live map for next tick.

`currentMonthIndex` = `clock.getCycle() * 12 + clock.getMonth()`.
Diff-mode months with no changes contribute zero events.

### Seek (rendering a past month)

```plaintext
baseMonth = largest entry in snapshotMonths where entry <= targetMonth
state = empty Map<SystemId, SystemPolitics>
for event in events starting from the first with monthIndex == baseMonth:
    if event.monthIndex > targetMonth: break
    if event.encodedPolitics == [0xFF]:
        state.remove(systemIds[event.systemIndex])
    else:
        state.put(systemIds[event.systemIndex], decode(event.encodedPolitics))
render(state)
```

Replaying from the latest snapshot bounds work to one snapshot burst (~200 events) plus up to 60 months of diffs (~tens of events).
A few hundred map operations -
sub-millisecond,
comfortable for every slider drag.
No reconstruction cache needed.

For binary search into `events` by `monthIndex`,
keep `events` sorted by `monthIndex` (it is, by construction - appends only).
A short `Map<MonthIndex, EventIndex>` mapping snapshot months to their first event index can be built lazily on first seek.

### Storage estimate

- Index tables:
  `factionIds` ~30 strings * ~20 chars = ~600 bytes;
  `systemIds` ~200 strings * ~25 chars = ~5 KB.
  Dominant cost,
  but these are also strings already in the save elsewhere -
  XStream reference deduplication may collapse them.
- Snapshot bursts:
  ~200 inhabited systems * ~10 bytes/event = ~2 KB per snapshot.
  50-year campaign = 10 snapshots = ~20 KB.
- Diff events between snapshots:
  maybe 200-500 total over the campaign * ~10 bytes = ~5 KB.
- `snapshotMonths`:
  10 ints = trivial.
- **Total:
  ~30 KB worst case** for a long campaign.
  A few KB for a short one.
  Still negligible alongside a typical Starsector save.

### Persistence

`Sector.getPersistentData()` keyed by `kmu_political_history`.
The value is a `PoliticalHistory` POJO holding `factionIds`,
`systemIds`,
`events`,
and `snapshotMonths`.
`ChangeEvent` is a small POJO with a `byte[]` payload -
XStream handles `byte[]` efficiently.
Add `PoliticalHistory` and `ChangeEvent` to the XStream alias list in the mod plugin
so the save tag stays short and stable across renames.

Corruption resilience is provided by the 5-year snapshot interval:
if events between two snapshots become unreadable,
the next snapshot re-establishes full state.
Worst-case fidelity loss is one snapshot interval (60 months);
average loss is half that.
The save layer can detect corruption by checking that every event's `encodedPolitics` either has `length == 1 && bytes[0] == 0xFF` or has `presenceCount + 2 == length` -
any mismatch means truncate the log from that event onward and rely on the next snapshot.

### Out of scope for v1 (forward-links)

- The actual timeline UI (scrubber, playback).
  v1 records only;
  the UI is a later feature.
- Capturing warfare/invasion state (feature 21).
  When that feature lands,
  decide whether it shares this event log or maintains its own.
  Lean toward separate streams keyed under separate `PersistentDataAPI` keys -
  the encoding versions of the two features should evolve independently.
- Faction relationships at the time of capture -
  the political map does not render relations;
  capturing them is scope creep until a timeline view demands it.
- Save-format migration.
  The encoding has no version byte;
  once the timeline UI ships,
  add one
  (1 byte at the head of each `encodedPolitics` payload) so future tweaks stay backward-compatible.

## Risks and gotchas

- `render()` is called per-entity per-layer per-frame.
  Keep it allocation-free — pre-compute the per-system colour/center on `MarketsChangedListener` callbacks,
  not in `render`.
- Always reset GL state you touch
  (`glColour`, blend mode, texture enable).
  The terrain layers especially are sensitive — vanilla terrains expect texture disabled before they restore state.
- Per-entity `getRenderRange()` controls culling.
  For a small disc this can be `200f`;
  for a constellation-sized hull,
  return the radius of the hull plus padding.
- Hyperspace vs system view:
  only render when `entity.getContainingLocation().isHyperspace()`.
  The anchor lives in hyperspace;
  do not also draw inside individual star systems.
- Save compat:
  custom entities are serialised by ID;
  renaming the `custom_entities.json` type ID will break loaded saves.
  Pick the ID once.
- Decivilised / Pather / Remnant / pirate factions all return a base UI colour.
  Decide whether to draw them
  (probably yes for Pather/pirate; skip no-market factions).
- KMU is declared `utility: true` and has no LunaLib dependency in its `mod_info.json`.
  Either keep config in `data/config/kmu_political.json` read at app load,
  or add LunaLib as an optional dep — match whatever pattern the existing KMU features already use before introducing a new config surface.

## Open questions

- Should pirate "bases"
  (non-market `Tags.PIRATE_BASE` entities) count as territory,
  or only proper markets?
- Where should the on/off toggle for the world overlay live:
  intel panel-only,
  hotkey,
  or a small button injected via `CampaignUIRenderingListener`?
- Connector adjacency rule:
  same-constellation only,
  or distance-based with a threshold?
  Constellation is simpler and matches Voronoi's natural grouping;
  distance might catch cross-constellation neighbours that read as adjacent visually.
- Exact alpha/radius/step constants for the visual model
  ([Visual model](#visual-model-under-candidate-a)) -
  placeholders given,
  tuned in playtest.
- Highlight-view v2:
  the "pure" highlight view
  (desaturate non-selected, isolate selected faction's stripe in mixed systems) -
  shape to be decided once v1 ships.

Resolved (recorded in this doc):

- Shape strategy:
  Voronoi cells (Candidate A) leading,
  with the layered visual model.
  Disc / dog-bone preserved as fallback candidates.
- Mixed-faction systems:
  dominance picks cell colour;
  full presence stack on the blips;
  per-faction dog-bone connectors based on presence not dominance.
  See [Visual model](#visual-model-under-candidate-a).
- Dominance metric:
  sum of `MarketAPI.getSize()` over markets,
  with lexicographic tie-break
  (planets ranked above stations at equal size).
  See [Dominance rule](#dominance-rule).
- Presence tiers (3):
  live planets > live stations > player settlements.
  See [Presence tiers](#presence-tiers-under-candidate-a).
- Decivilised planets and abandoned stations are out of scope for the political layer -
  vanilla wipes their faction and they convey no political signal.
  See [Decivilised entities are factionless](#decivilised-entities-are-factionless).
- Stripes / diagonal patterns reserved for a future warfare/invasion overlay (potential feature 21 intersection);
  not used here.

[CoreUITabId.java]:
../../../../../../starsector-core/starfarer.api.zip [CampaignEngineLayers.java]:
../../../../../../starsector-core/starfarer.api.zip [CustomCampaignEntityPlugin.java]:
../../../../../../starsector-core/starfarer.api.zip [CampaignUIRenderingListener.java]:
../../../../../../starsector-core/starfarer.api.zip [CustomUIPanelPlugin.java]:
../../../../../../starsector-core/starfarer.api.zip [ExampleCustomUIPanel.java]:
../../../../../../starsector-core/starfarer.api.zip [MapMarkerIntel.java]:
../../../../../../starsector-core/starfarer.api.zip [FactionHostilityIntel.java]:
../../../../../../starsector-core/starfarer.api.zip [BaseIntelPlugin.java]:
../../../../../../starsector-core/starfarer.api.zip [Misc.java]:
../../../../../../starsector-core/starfarer.api.zip [LocationAPI.java]:
../../../../../../starsector-core/starfarer.api.zip
