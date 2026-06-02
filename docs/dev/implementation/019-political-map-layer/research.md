# Political Map Layer Reference

Research date: 2026-05-17

Scope: feasibility and API surface for a sector-map overlay that paints faction
territory in faction colors. Two views are in scope:

1. A full-sector layer with every faction shown, with the option to highlight
   one selected faction.
2. A per-faction view (one faction at a time).

Sources read directly: extracted `starsector-core/starfarer.api.zip`,
`Nexerelin-0.12.1e/jars/ExerelinCore.jar`, `LazyLib.jar`, `MagicLib.jar`
(both classes and bundled `.java`/`.kt`), plus a quick survey of `stelnet.jar`.

## Index

- [What "the starmap" actually is](#what-the-starmap-actually-is)
- [Render-surface options](#render-surface-options)
- [Recommended approach](#recommended-approach)
- [Intel screen as the selector UI](#intel-screen-as-the-selector-ui)
- [Library support (LazyLib, MagicLib)](#library-support-lazylib-magiclib)
- [Data sources for territory](#data-sources-for-territory)
- [State, persistence, lifecycle](#state-persistence-lifecycle)
- [Risks and gotchas](#risks-and-gotchas)
- [Open questions](#open-questions)

## What "the starmap" actually is

The "M" key opens `CoreUITabId.MAP` ([CoreUITabId.java]). There is no public
"map layers" API. The map screen is the same campaign engine view, rendered with
a zoomed-out viewport on the hyperspace location. Any drawing that the campaign
engine does — fleets, planets, terrain, custom entities — appears there.

That means the lever we have is **draw something in hyperspace world space at
each render call**, and the M-key map shows it for free.

Relevant types:

- `CampaignEngineLayers` ([CampaignEngineLayers.java]) lists the per-frame
  render layers; `TERRAIN_1..TERRAIN_10`, `BELOW_STATIONS`, `ABOVE`, etc. A
  political tint belongs under everything else, so `TERRAIN_1` or `TERRAIN_2`.
- `CustomCampaignEntityPlugin.render(CampaignEngineLayers, ViewportAPI)`
  ([CustomCampaignEntityPlugin.java]) is called once per layer per frame. The
  entity is the anchor; rendering happens at world coords, no translation
  applied.
- `LocationAPI.addCustomEntity(...)` and the `custom_entities.json` "type" entry
  install one. The plugin class is reflection-loaded from that JSON.
- `CampaignTerrainPlugin` (heavier; used for `BaseTerrain` subclasses) is the
  vanilla pattern for sector-wide "fog" things. Overkill here — terrain ties
  into sensor/fog/interdiction logic that we do not want.
- `CampaignUIRenderingListener` ([CampaignUIRenderingListener.java]) gives
  `renderInUICoordsBelowUI`, `renderInUICoordsAboveUIBelowTooltips`, and
  `renderInUICoordsAboveUIAndTooltips` callbacks. UI coords only — you would have to
  re-project hyperspace world → screen yourself and re-implement clipping under
  the map chrome. Not recommended for world-space tinting; useful for adding a
  legend or HUD bar over the map.

## Render-surface options

| # | Approach                                  | World-space | Effort | Notes |
|---|-------------------------------------------|-------------|--------|-------|
| 1 | Per-system invisible custom entity in hyperspace, `render()` paints a disc/hull | yes | low    | Recommended. Renders on M-map and on zoomed-out campaign view. |
| 2 | One global custom entity with huge `getRenderRange()` | yes | low    | Engine still culls; one entity off-screen means no draw. Per-system avoids this. |
| 3 | Custom `CampaignTerrainPlugin` per faction | yes        | medium | Honest "territory" with shape, but pulls in terrain semantics we don't want. |
| 4 | `CampaignUIRenderingListener` over the map | no         | medium | Need manual world→screen projection. Good for legend/overlay UI only. |
| 5 | Render only inside an intel `createLargeDescription` panel | n/a        | low    | Already needed for the per-faction selector — see [Intel screen as the selector UI](#intel-screen-as-the-selector-ui). |

## Recommended approach

Two surfaces, one shared drawing routine:

1. **World overlay** — one anchor entity per inhabited system in hyperspace
   (`addCustomEntity` of a `kmu_political_marker` type, tagged
   `Tags.NON_CLICKABLE`, no sprite). Its plugin's `render()` paints a filled
   disc (or buffered hull around the local jump points) using the
   system-dominant faction's `FactionAPI.getBaseUIColor()`. Skip render if
   `viewport.getAlphaMult() == 0` and `isRenderWhenViewportAlphaMultIsZero()`
   is false — that flag is the public switch for "should this draw during map
   transitions".
2. **Intel panel** — a `BaseIntelPlugin` with `hasLargeDescription() = true`.
   `createLargeDescription(CustomPanelAPI, w, h)` builds a left-side faction
   list (buttons) and a right-side `CustomPanelAPI` whose plugin reuses the
   same draw routine in panel-local coords.

Selecting a faction in the intel panel writes
`Sector.getMemoryWithoutUpdate().set("$kmu_political_highlight_faction", id)`.
The world-overlay plugins read that each frame: if set, dim everything that is
not that faction; if null, draw all factions at full opacity.

This gives both views from one drawing function and one state field.

## Intel screen as the selector UI

Pattern lifted from vanilla `MapMarkerIntel` ([MapMarkerIntel.java]) and
`FactionHostilityIntel` ([FactionHostilityIntel.java]); per-faction list
construction follows Nexerelin's `Nex_FactionDirectory` (in
`ExerelinCore.jar`).

- Implement `BaseIntelPlugin` ([BaseIntelPlugin.java]):
  - `getIcon()` — small icon for the intel list.
  - `createIntelInfo(...)` — list row (title + one-line summary).
  - `hasLargeDescription() = true` and `createLargeDescription(...)` — full
    panel with the political map.
  - `getIntelTags(SectorMapAPI)` — return a custom tag like
    `"Political map"` so it gets its own filter chip in the intel sidebar.
  - `getMapLocation(SectorMapAPI)` — return `null` (no marker on the intel
    inset map; the intel itself *is* the map).
  - `getFactionForUIColors()` — for the highlight color; can be the currently
    selected faction or `null` (white).
- Add the intel once per save in `ModPlugin.onGameLoad(boolean)` via
  `Sector.getIntelManager().addIntel(plugin, true)` (true = no popup).
- For the panel layout:
  - `panel.createUIElement(...)` for the faction list (left), one
    `addButton` per faction with `FactionAPI.getBaseUIColor()` /
    `getDarkUIColor()`.
  - `panel.createCustomPanel(w, h, mapPanelPlugin)` for the map area; the
    plugin implements `CustomUIPanelPlugin` ([CustomUIPanelPlugin.java]) and
    does the GL drawing in `render(alphaMult)` using
    `position.getX/Y/Width/Height`. `ExampleCustomUIPanel`
    ([ExampleCustomUIPanel.java]) is the canonical skeleton.
- Re-open behavior: `IntelUIAPI.recreateIntelUI()` after the user clicks a
  faction button.

stelnet (`stelnet.jar`) is the reference for "tabbed sub-views inside the
intel screen" if a tab layout is wanted later; for now, a single intel item
with a custom large description is enough. MagicLib's
`org.magiclib.bounty.intel.BountyBoardIntelPlugin` (Kotlin source bundled
in the jar) is a closer real-world precedent: it builds a multi-section
intel screen with a filter bar, a scrolling list, and a detail panel — the
same shape this feature needs.

## Library support (LazyLib, MagicLib)

KMU currently only depends on lazywizard's Console (used in
`kmu/console/...`); `mod_info.json` declares no `dependencies` block. Adding
this feature is the right moment to decide whether to take a hard or soft
dep on the two ubiquitous libs.

What each lib actually provides for this feature:

- **LazyLib** (`org.lazywizard.lazylib`)
  - `opengl/DrawUtils.drawCircle(cx, cy, r, segments, filled)` — exactly the
    primitive needed for the per-system disc. Removes the need to hand-roll
    the trig + `glBegin(GL_TRIANGLE_FAN)` loop.
  - `opengl/ColorUtils.glColor(Color, alphaMult, overrideOriginalAlpha)` —
    sets `glColor4ub` from an AWT `Color`. When the third arg is `true` the
    final alpha is `alphaMult * 255` directly; when `false` it multiplies the
    source color's alpha by `alphaMult`. Removes the byte-cast boilerplate
    seen in `ExampleCustomUIPanel`.
  - `MathUtils` — distance/point-along helpers; **no convex-hull or Voronoi
    primitive**. If we go with per-constellation hulls in a later version,
    that algorithm is still ours to write.
  - `campaign/CampaignUtils` — relationship/faction helpers; nothing the
    overlay strictly needs, but useful if we later filter by hostility.
- **MagicLib** (`org/magiclib/...`)
  - `org.magiclib.achievements.CampaignCustomRenderer` exists and uses
    exactly the pattern we want (`BaseCustomEntityPlugin` +
    `render(CampaignEngineLayers, ViewportAPI)` + huge `getRenderRange`),
    but it is declared `internal` (Kotlin) and scoped to achievements.
    **Confirms the pattern**, does not give us a reusable surface.
  - `data.scripts.util.MagicSettings` — JSON-backed mod config helper
    (`MagicSettings.json` in `data/config/`). Lets us add an
    enabled/disabled toggle without pulling in LunaLib.
  - `data.scripts.util.MagicUI` and `data.scripts.util.MagicRender` are
    combat-screen oriented; not relevant to the campaign overlay.
  - `BountyBoardIntelPlugin` — see above, reference for the intel screen.

Recommendation: take a **hard dep on LazyLib** (use `DrawUtils` and
`ColorUtils`), and a **soft dep on MagicLib** gated at load time
(`Global.getSettings().getModManager().isModEnabled("MagicLib")`). MagicLib
buys us `MagicSettings` for the toggle and the `BountyBoardIntelPlugin`
reference, but nothing on the critical path; LazyLib's draw primitives
are on the critical path and trivial to depend on.

If the dep stance is "no new deps for a v1", roll the disc draw inline
(8-line trig loop) and put the toggle in `Sector.getMemoryWithoutUpdate()`
behind a console command — defer libs to v2.

## Data sources for territory

- `Sector.getAllFactions()` then filter to factions with at least one market:
  `Misc.getFactionMarkets(faction)` ([Misc.java]).
- For each `StarSystemAPI` in `Sector.getStarSystems()`:
  - `Misc.getMarketsInLocation(system)` ([Misc.java:960]) gives all markets;
  - dominant faction = max by total market size, tie-broken to player
    (`Misc.getMarketsInLocation(loc, factionId)` is also available);
  - hyperspace location = `system.getHyperspaceAnchor().getLocation()` (or
    `system.getLocation()`).
- Faction color: `FactionAPI.getBaseUIColor()` (full saturation),
  `getDarkUIColor()` (panel chrome). The "highlight" view uses base for the
  selected faction and a heavily desaturated/multiplied tint for the rest.
- Optional grouping: `LocationAPI.getConstellation()` ([LocationAPI.java:130])
  to draw per-constellation hulls instead of per-system discs.

## State, persistence, lifecycle

- One-time wiring: register `custom_entities.json` entry
  `kmu_political_marker` mapping to the render plugin class. Done in `data/`,
  no code.
- Per-save wiring (in `KMU_ModPlugin.onGameLoad(boolean newGame)`):
  - Ensure exactly one anchor entity exists per inhabited system in
    hyperspace (idempotent — match by id `kmu_pm_<systemId>`).
  - Re-create or repair the intel item if missing.
  - Refresh the anchor set and dominant-faction caches when the political
    picture changes. No vanilla listener fires on `MarketAPI.setFactionId`,
    so listeners alone cannot cover Nex transfers, raid takeovers, or
    mod-driven faction flips. Use a hybrid:
    - **Fingerprint poll (correctness backstop).** An `EveryFrameScript`
      hashes `systemId -> dominantFactionId` over
      `Sector.getEconomy().getMarketsCopy()` once every **1.0s** of in-game
      time. If the hash changes, rebuild the per-system color/center cache.
      Cheap (a few hundred string hashes + XOR fold) and catches everything,
      including events we have no listener for.
    - **Listener prods (snappy response).** Wire
      `PlayerColonizationListener` (founding/abandoning),
      `ColonyDecivListener.reportColonyAboutToBeDecivilized`
      (decivilisation), and `ColonySizeChangeListener` (size flips that
      change dominance) to call `invalidate()` so common player actions
      refresh next frame instead of waiting up to a second.
    - For Nex-aware refresh, optionally listen for the
      `Nex_OnMarketTransferred` rules-based event (soft dep). The
      fingerprint poll catches Nex transfers anyway; the listener just
      shortens latency.
- Selected-faction state: `MemoryAPI` key `$kmu_political_highlight_faction`,
  scope = sector memory (`Sector.getMemoryWithoutUpdate()`). No XStream
  changes needed.
- Toggle for the world overlay (on/off): same memory map, key
  `$kmu_political_overlay_enabled`. Hotkey is optional; intel-screen toggle
  is enough for v1.

## Risks and gotchas

- `render()` is called per-entity per-layer per-frame. Keep it allocation-free
  — pre-compute the per-system color/center on
  `MarketsChangedListener` callbacks, not in `render`.
- Always reset GL state you touch (`glColor`, blend mode, texture enable).
  The terrain layers especially are sensitive — vanilla terrains expect
  texture disabled before they restore state.
- Per-entity `getRenderRange()` controls culling. For a small disc this can
  be `200f`; for a constellation-sized hull, return the radius of the
  hull plus padding.
- Hyperspace vs system view: only render when
  `entity.getContainingLocation().isHyperspace()`. The anchor lives in
  hyperspace; do not also draw inside individual star systems.
- Save compat: custom entities are serialised by id; renaming the
  `custom_entities.json` type id will break loaded saves. Pick the id once.
- Decivilised / Pather / Remnant / pirate factions all return a base UI
  colour. Decide whether to draw them (probably yes for Pather/pirate; skip
  no-market factions).
- KMU is declared `utility: true` and has no LunaLib dependency in its
  `mod_info.json`. Either keep config in `data/config/kmu_political.json`
  read at app load, or add LunaLib as an optional dep — match whatever
  pattern the existing KMU features already use before introducing a new
  config surface.

## Open questions

- Per-system disc or per-constellation hull for v1?
- Should contested systems (multiple factions with similar weight) draw a
  split disc, or just the dominant colour?
- Should pirate "bases" (non-market `Tags.PIRATE_BASE` entities) count as
  territory, or only proper markets?
- Where should the on/off toggle for the world overlay live: intel
  panel-only, hotkey, or a small button injected via
  `CampaignUIRenderingListener`?

[CoreUITabId.java]: ../../../../../../starsector-core/starfarer.api.zip
[CampaignEngineLayers.java]: ../../../../../../starsector-core/starfarer.api.zip
[CustomCampaignEntityPlugin.java]: ../../../../../../starsector-core/starfarer.api.zip
[CampaignUIRenderingListener.java]: ../../../../../../starsector-core/starfarer.api.zip
[CustomUIPanelPlugin.java]: ../../../../../../starsector-core/starfarer.api.zip
[ExampleCustomUIPanel.java]: ../../../../../../starsector-core/starfarer.api.zip
[MapMarkerIntel.java]: ../../../../../../starsector-core/starfarer.api.zip
[FactionHostilityIntel.java]: ../../../../../../starsector-core/starfarer.api.zip
[BaseIntelPlugin.java]: ../../../../../../starsector-core/starfarer.api.zip
[Misc.java]: ../../../../../../starsector-core/starfarer.api.zip
[LocationAPI.java]: ../../../../../../starsector-core/starfarer.api.zip
