# Political Map Overlay (Sector Map)

## Index

- [Problem](#problem)
- [Baseline Behavior](#baseline-behavior)
- [Initial Scope](#initial-scope)
- [Out of Scope](#out-of-scope)
- [Approach Options](#approach-options)
  - [Sidebar surface](#sidebar-surface)
  - [Click handling](#click-handling)
- [Decisions](#decisions)
  - [Surfaces and ownership](#surfaces-and-ownership)
  - [Decivilized markers (neutral)](#decivilized-markers-neutral)
  - [Toggle state contract](#toggle-state-contract)
  - [Sub-view detection](#sub-view-detection)
  - [Sidebar visibility contract](#sidebar-visibility-contract)
  - [Lifecycle](#lifecycle)
- [Tests](#tests)
- [Open Questions](#open-questions)
- [Risks](#risks)
- [Companion: API research notes](../019-political-map/research.md)

## Problem

The sector map (the `M` / Sector view) filters inhabited systems,
but nothing that reads as *territorial control*. A player
cannot glance at the map and see which faction dominates which region,
where borders fall, or where factions interleave. The information
exists in the economy (every market has an owner and a size) but is
never aggregated into a spatial, at-a-glance picture.

Feature 022 paints a HOI4/Civ-style political layer over the existing
sector map: faction-colored regions, per-system presence markers, and
connectors between same-faction systems, all derived from live market
ownership. The layer is toggled on and off by the player from a small
custom sidebar drawn over the sector map.

The constraint that shapes the whole feature: **vanilla's map tab bar
and core UI tabs are not extensible.** There is no public "add a map
layer" or "add a map tab" API. So the toggle cannot be a new vanilla
tab or chip alongside `Starscape` / `Names` / `Inhabited`; it has to be
a mod-drawn control rendered over the map in UI coordinates.

## Baseline Behavior

- Render a **world-space political overlay** on the hyperspace sector
  view, using the full visual model designed in feature 019: Voronoi
  cells filled in the dominant faction's color, per-system blip stacks
  showing every faction present, and dog-bone connectors between
  same-faction systems. See
  [research: Visual model](../019-political-map/research.md#visual-model-under-candidate-a),
  [research: Presence tiers](../019-political-map/research.md#presence-tiers-under-candidate-a),
  and [research: Shape strategy - Candidate A](../019-political-map/research.md#candidate-a-voronoi-cells-leading).
- Derive territory from live market ownership using the dominance rule
  and presence tiers in
  [research: Data sources for territory](../019-political-map/research.md#data-sources-for-territory).
  Decivilised planets and abandoned stations are factionless, so they
  hold no *territory* - they never seed a faction-colored region, join
  the dominance computation, or trigger connectors.
- Show **known decivilised planets** as neutral, unaffiliated markers
  in their system's blip stack (a grey "dead colony here" dot), gated so
  the marker appears exactly when vanilla would show the planet's
  decivilised status to the player (see
  [Decivilized markers (neutral)](#decivilized-markers-neutral)). This
  overrides feature 019's blanket exclusion of
  decivilised entities
  ([research: Decivilised entities are factionless](../019-political-map/research.md#decivilised-entities-are-factionless)):
  022 still excludes them from *territory*, but surfaces them as a
  faction-less point of interest. See
  [Decivilized markers (neutral)](#decivilized-markers-neutral).
- Draw a **small custom sidebar** over the sector map carrying a single
  on/off control for the overlay. The sidebar is mod-rendered in UI
  coordinates, not a vanilla tab.
- The overlay starts **hidden**. Clicking the sidebar toggle shows it;
  clicking again hides it. The choice persists in the save.
- The overlay and sidebar appear only while the player is viewing the
  sector map, and never during an interaction dialog. When the map is
  closed, both are gone with zero per-frame cost beyond a cheap
  tab-state check.
- The overlay reflects the *current* political picture: when market
  ownership changes (Nexerelin transfer, raid takeover, player
  colonisation, decivilisation), the colors update without a reload,
  on the refresh cadence from
  [research: State, persistence, lifecycle](../019-political-map/research.md#state-persistence-lifecycle).
- With no inhabited systems (impossible in practice, but the degenerate
  case), the overlay draws nothing and the toggle is inert - no errors,
  no leaked GL state.

## Initial Scope

- The **world overlay**: per-inhabited-system anchor entities in
  hyperspace whose render routine paints the Voronoi cell, connectors,
  and blip stack, exactly as specified by feature 019's recommended
  approach
  ([research: Recommended approach](../019-political-map/research.md#recommended-approach)).
  The overlay's render is gated on the toggle state.
- The **data pipeline**: dominance computation, presence-tier
  classification, and the Voronoi cell geometry, computed once and
  cached, invalidated by the fingerprint poll plus listener prods from
  [research: State, persistence, lifecycle](../019-political-map/research.md#state-persistence-lifecycle).
- **Neutral decivilised markers**: known (player-visible) decivilised
  planets rendered as faction-less blips, derived from a separate scan
  (they are not in the economy), excluded from territory. See
  [Decivilized markers (neutral)](#decivilized-markers-neutral).
- The **sidebar**: one mod-rendered panel over the sector map with a
  single on/off toggle, drawn in UI coordinates via
  `CampaignUIRenderingListener`, gated to the map tab.
- **Click handling** for the sidebar toggle (see
  [Click handling](#click-handling)).
- The **toggle state**, persisted in sector memory (see
  [Toggle state contract](#toggle-state-contract)).
- Sidebar label / tooltip text stored in
  [data/strings/strings.json](../../../../data/strings/strings.json) and
  resolved through `kmu.util.KmuStrings`, never inlined as Java string
  literals at the use site.

## Out of Scope

- **Faction highlight / per-faction selection.** The sidebar is on/off
  only. The overlay always shows all factions in dominance colors.
  Feature 019's highlight view (select a faction, isolate or desaturate
  the rest) is deferred to a later feature, as is any faction list in
  the sidebar.
- **The intel-screen selector.** Feature 019 proposed a `BaseIntelPlugin`
  large-description panel as the selector UI. That is replaced here by
  the sidebar and is not built in this feature.
- **Political history recording and the timeline scrubber.** The Civ-3
  style change log, snapshots, and persistence
  ([research: Political history](../019-political-map/research.md#political-history-civ-3-style-timeline))
  are a separate later feature. Feature 022 renders the *live* picture
  only and writes no history.
- **System-view (`W`) overlay.** The layer is hyperspace-anchored, so it
  renders only on the Sector view. No per-system political drawing.
- **Decivilised *territory*.** Decivilised planets never form a
  faction-colored region, count toward dominance, or draw connectors -
  they are unowned. They *are* shown as neutral markers when surveyed
  (see [Decivilized markers (neutral)](#decivilized-markers-neutral));
  it is only their participation as *territory* that is out of scope.
- **Recovering a decivilised planet's former owner.** Vanilla wipes the
  owning faction on deciv ([research](../019-political-map/research.md#decivilised-entities-are-factionless));
  022 does not reconstruct it. Markers are neutral, full stop - no
  "formerly Hegemony" coloring.
- **Stripe / diagonal-pattern rendering.** Reserved for a future
  warfare/invasion overlay; the political layer keeps a clean visual
  vocabulary.
- **Hotkey toggle.** The sidebar is the sole toggle surface for v1; a
  keybind can be added later without changing the state contract.

## Approach Options

The world overlay and data pipeline are settled by feature 019. The new
decisions for 022 are how to surface the toggle and how to read its
clicks, since vanilla offers no extension point on the map chrome.

### Sidebar surface

| Option | What it does | Cost | Outcome |
|---|---|---|---|
| A. `CampaignUIRenderingListener` panel | Draw a small panel over the map in UI coords (`renderInUICoordsAboveUIBelowTooltips`), gated to `CoreUITabId.MAP` | Low | A visible, discoverable on-map control with no vanilla-tab tampering. Recommended. |
| B. Hotkey only, no visible UI | Bind a key that flips the toggle; no drawn control | Very low | Cheapest, but undiscoverable - violates the user-visible intent of a "sidebar on the map". |
| C. Intel-screen panel (feature 019) | Put the toggle inside a `BaseIntelPlugin` large description | Medium | Discoverable but off the map; the user explicitly wants the control *on* the sector map. Rejected for v1. |

Recommendation: **Option A.** It is the only option that satisfies "a
small sidebar on the sector map" without touching vanilla tabs. The
render hooks are confirmed present
([CampaignUIRenderingListener.java:9](../../../../../../starsector-core/starfarer.api.zip)),
and `CampaignUIAPI.getCurrentCoreTab()` returns `CoreUITabId.MAP` so the
panel can be shown only on the map. Vanilla's `SlipstreamVisibilityManager`
is a precedent for keying behaviour off `CoreUITabId.MAP`.

### Click handling

`CampaignUIRenderingListener` only *renders*; it delivers no input
events. A mod-drawn control must read its own clicks.

| Option | What it does | Cost | Outcome |
|---|---|---|---|
| A. LWJGL `Mouse` polling | An `EveryFrameScript` polls `org.lwjgl.input.Mouse` each frame, edge-triggered on button-down, hit-tested against the sidebar rect, gated to map-open | Low | Standard campaign-HUD-button pattern. Recommended. |
| B. `CustomUIPanelAPI` injected into the map | Reuse vanilla's button/panel input handling | High / unknown | No public seam to inject a custom panel into the vanilla map view; would fight the map's own input. Rejected. |

Recommendation: **Option A.** Poll `Mouse` in the same per-frame script
that already runs for cache invalidation, edge-trigger on the
left-button transition, and only consume the click when the cursor is
inside the sidebar rect *and* the map tab is open and no dialog is
showing. This is well-trodden modding ground; the only real risk is
double-consuming a click that vanilla also acts on, mitigated by the
tight hit-test (the sidebar occupies empty map margin).

## Decisions

### Surfaces and ownership

- Live in KMU under a dedicated package (e.g. `kmu.politicalmap`). KMU
  already owns campaign-map and market tooling.
- Two render surfaces, one shared data cache:
  - **World overlay** - per-inhabited-system anchor entities of type
    `kmu_political_marker` in hyperspace, registered via
    `custom_entities.json`, tagged non-clickable, no sprite. Their
    `render(CampaignEngineLayers, ViewportAPI)` paints cell / connectors
    / blips on the layer slots chosen in
    [research: Visual model render-state notes](../019-political-map/research.md#visual-model-under-candidate-a).
    The entity type id is permanent once shipped (custom entities
    serialise by id; renaming breaks saves).
  - **Sidebar** - a `CampaignUIRenderingListener` that draws the panel
    in UI coords and an `EveryFrameScript` that reads its clicks.
- Both surfaces read the same toggle key and the same cached
  `Map<SystemId, SystemPolitics>`; neither owns the other.
- All identifiers prefixed `kmu_political_` so they cannot collide with
  vanilla, KMO, or third-party mods.

### Decivilized markers (neutral)

Surveyed decivilised planets are surfaced as a faction-less presence,
distinct from the faction territory pipeline.

- **Detection.** Decivilised markets are not in
  `Sector.getEconomy().getMarketsCopy()`, so they are found by scanning
  planets per system (`StarSystemAPI.getPlanets()`) for a market with
  `hasCondition("decivilized")`. `isPlanetConditionMarketOnly()` alone is
  insufficient - every uninhabited planet has a condition-only market for
  hazard / atmosphere; the `"decivilized"` condition is what marks a
  *former colony*. (Full-destroy and `removeColony` paths may omit the
  condition but set the `$wasCivilized` memory key; treat that key as a
  secondary marker if those cases need covering.)
- **Survey gate.** A marker draws only when the player would already know
  the planet is decivilised in vanilla - the overlay must neither reveal
  a dead colony the player has not learned about, nor hide one vanilla
  already shows. **Vanilla's own visibility is the bar; we mirror it, we
  do not second-guess it.** Whatever survey requirement vanilla puts on
  the `decivilized` condition is the intended one - if vanilla surfaces
  it on sensor contact, so do we; if vanilla hides it until charted, so
  do we. The gate is two independent parts:

  ```
  hasBeenEncountered = market.getSurveyLevel() != MarketAPI.SurveyLevel.NONE
  cond               = market.getSpecificCondition("decivilized")
  isRevealed         = cond != null && (!cond.requiresSurveying() || cond.isSurveyed())
  shouldDrawMarker   = hasBeenEncountered && isRevealed
  ```

  - `hasBeenEncountered` guards against drawing markers for systems the
    player has never visited (a planet stays at `SurveyLevel.NONE` until
    first contact). This is the only "has the player been here" gate; it
    is not a depth requirement.
  - `isRevealed` is vanilla's per-condition visibility, verbatim. A
    colony that decivilises *during play* has its ruins condition
    force-marked surveyed by the deciv process, so it reveals
    immediately; a *procgen* dead world follows whatever survey rule the
    condition carries.

  There is deliberately no `SurveyLevel.FULL` requirement: imposing one
  would hide decivilised planets that vanilla already shows at a lower
  level, which is exactly the second-guessing this gate avoids. Survey
  levels run `NONE -> SEEN -> PRELIMINARY -> FULL` for reference, but the
  gate keys off condition visibility, not a fixed tier.
- **Color.** The neutral faction's UI color
  (`getFaction("neutral").getBaseUIColor()`), so the marker reads as
  unaffiliated and stays consistent with how live factions are colored.
- **Render treatment.** A blip in the system's blip stack only. Marker
  planets:
  - do **not** seed Voronoi cells (no faction = no claimed area),
  - do **not** enter the dominance metric or presence tiers,
  - do **not** spawn dog-bone connectors.
- **Systems with only decivilised planets.** Such a system is not
  "inhabited" and seeds no cell, but still shows its neutral marker(s).
  It appears as a grey dot inside whichever live faction's cell contains
  it (or in the void), never as territory of its own.
- **Cadence.** The decivilised set changes on the same months-to-never
  cadence as ownership; fold its scan into the same cached pipeline and
  invalidate it on the existing fingerprint poll plus the
  `ColonyDecivListener` prod from
  [research: State, persistence, lifecycle](../019-political-map/research.md#state-persistence-lifecycle).
  Visibility changes are caught by the fingerprint (include each
  decivilised planet's marker-visibility result in the hash) so a freshly
  surveyed ruin appears without a reload.

### Toggle state contract

- The overlay on/off flag lives in sector memory
  (`Sector.getMemoryWithoutUpdate()`) under key
  `$kmu_political_overlay_enabled`, default unset = **hidden**. Sector
  memory persists in the save, so the player's choice survives reload.
  No XStream changes.
- The world anchors' `render` early-returns when the flag is off (no GL
  state touched). The sidebar toggle is the sole writer of the flag.

### Sub-view detection

The map tab has two sub-views - Sector (hyperspace) and System (`W`) -
and **no public API distinguishes them**: `CoreUITabId` has a single
`MAP` value, `CampaignUIAPI` exposes no sub-view accessor, and the
`ViewportAPI` handed to the render hooks carries only geometry, not the
location being shown. The discriminator instead comes from the render
engine itself:

- A hyperspace-anchored `CustomCampaignEntityPlugin.render()` is only
  invoked when the view is showing hyperspace. The Sector sub-view shows
  hyperspace; the System sub-view shows a star system's interior, where
  hyperspace entities are culled and never render.
- So an always-on, invisible **probe anchor** in hyperspace stamps
  `lastHyperFrame = <current frame>` inside its `render()` (no visible
  draw). The sidebar's UI-coords listener runs later in the same frame
  and reads the stamp: `tab == MAP && lastHyperFrame == currentFrame`
  means the Sector sub-view is active; a stale stamp means the System
  sub-view.
- The probe is independent of the overlay toggle (it stamps even when
  the overlay is hidden), so sub-view detection works regardless of
  overlay state. It can be a dedicated anchor or the per-system anchor
  set re-used for the stamp; either way the stamp write is the only work
  in the off state.

This avoids reflection into obfuscated map UI classes - the engine's own
location culling is the source of truth.

### Sidebar visibility contract

| Condition | Sidebar drawn | Overlay drawn |
|---|---|---|
| Not on the map tab (`getCurrentCoreTab() != MAP`) | no | no |
| On the map tab, dialog showing (`isShowingDialog()`) | no | no |
| On the map tab, System sub-view (probe stamp stale) | no | no |
| On the map tab, Sector sub-view, toggle **off** | yes | no |
| On the map tab, Sector sub-view, toggle **on** | yes | yes |

- Hiding the sidebar on the System sub-view (rather than greying it) is
  the chosen default: the overlay is meaningless there, so the cleanest
  read is "the control belongs to the Sector view". Greying-with-tooltip
  is the fallback if playtest shows the sidebar vanishing is confusing.
- Every frame the sidebar draws, it must fully restore any GL state it
  touches (color, blend, texture-enable) so vanilla map chrome and
  tooltips render unaffected.

### Lifecycle

- One-time wiring (in `data/`): register the `kmu_political_marker` type
  in `custom_entities.json`. No code.
- Per-save wiring (in `KMU_ModPlugin.onGameLoad(boolean)`):
  - Ensure exactly one anchor entity per inhabited system in hyperspace,
    idempotent by id `kmu_pm_<systemId>`.
  - Ensure the always-on hyperspace probe anchor exists (idempotent by a
    fixed id) for sub-view detection (see
    [Sub-view detection](#sub-view-detection)).
  - Register the sidebar `CampaignUIRenderingListener`.
  - Register the per-frame script that (a) polls the sidebar click and
    (b) drives cache invalidation (fingerprint poll + listener prods per
    [research: State, persistence, lifecycle](../019-political-map/research.md#state-persistence-lifecycle)).
  - Build the Voronoi geometry cache and the dominance / presence cache.
- Disabling or uninstalling: the only persisted artifact is the sector
  memory flag and the anchor entities. Anchors are inert when the flag
  is off; removing the mod drops the custom-entity plugin class, so the
  standard custom-entity uninstall caveat applies (see
  [Risks](#risks)).

## Tests

- Unit: with the toggle off, the world-overlay render routine performs
  no draw calls and touches no GL state.
- Unit: clicking the sidebar rect flips `$kmu_political_overlay_enabled`;
  a click outside the rect does not.
- Unit: the sidebar visibility predicate matches the
  [Sidebar visibility contract](#sidebar-visibility-contract) table for
  each `(currentCoreTab, isShowingDialog, subView, toggle)` combination,
  where `subView` is driven by the probe stamp freshness.
- Unit: the probe's `render` stamps the current frame; a stamp older
  than the current frame reads as the System sub-view.
- Unit: click polling is edge-triggered - a held button flips the toggle
  exactly once, not once per frame.
- Unit: the dominance rule picks the expected faction for hand-built
  market sets, including the lexicographic and faction-id tie-breaks
  ([research: Dominance rule](../019-political-map/research.md#dominance-rule)).
- Unit: presence-tier classification returns the highest matching tier
  for planet-only, station-only, and settlement-only systems
  ([research: Presence tiers](../019-political-map/research.md#presence-tiers-under-candidate-a)).
- Unit: a faction's live markets seed cells and dominance, while
  decivilised / abandoned entities contribute no faction presence and
  seed no cell.
- Unit: a planet yields a neutral marker only when both gate parts hold -
  `hasBeenEncountered` (`getSurveyLevel() != NONE`) and `isRevealed`
  (`!requiresSurveying() || isSurveyed()`). Each part failing alone
  (never-encountered at `NONE`; encountered but condition still hidden)
  yields no marker.
- Unit: an uninhabited planet with a condition-only market but no
  `"decivilized"` condition yields no marker (guards against treating
  every rock as a dead colony).
- Unit: a system whose only presence is a surveyed decivilised planet
  seeds no Voronoi cell and joins no dominance, but emits its neutral
  marker.
- Unit (wiring): the per-frame script is registered as an
  `EveryFrameScript`; the cache invalidates when the dominance
  fingerprint changes and stays put when it does not.
- Unit: with zero inhabited systems the pipeline yields an empty cache
  and the overlay draws nothing.

## Open Questions

Resolved (recorded here):

- **Toggle surface.** A `CampaignUIRenderingListener`-drawn sidebar on
  the map, gated to `CoreUITabId.MAP`. Resolves feature 019's open
  question on where the toggle lives.
- **Sidebar scope.** On/off only; no faction highlight or selection in
  v1.
- **History recording.** Out of scope for this feature.
- **Sub-view detection.** No public API distinguishes the Sector and
  System sub-views, but the hyperspace probe anchor does it
  deterministically (see [Sub-view detection](#sub-view-detection)). The
  sidebar is hidden on the System sub-view by default.

Still open:

- **Decivilised condition default survey requirement.** The marker gate
  reads vanilla's per-condition visibility
  (`requiresSurveying()` / `isSurveyed()`) directly, so it stays correct
  whatever that default is - no code branch depends on the answer. The
  open part is purely informational: confirm in-game *when* a deciv
  marker first appears (on sensor contact vs after a full survey) so the
  documented player-facing behaviour matches reality. The
  `hasBeenEncountered && isRevealed` gate does not change either way.
- **Sidebar placement and footprint.** Exact screen anchor (left margin
  vs right), size, and whether it scales with UI scale settings. Tuned
  in playtest.
- **Exact visual constants** (alpha, radius, step) for the overlay -
  inherited from feature 019's still-open tuning placeholders
  ([research: Open questions](../019-political-map/research.md#open-questions)).
- **Pirate / non-market bases as territory** - inherited open question
  from feature 019; does not block the sidebar work.

## Risks

- **No input API for the drawn sidebar.** Click handling is hand-rolled
  LWJGL `Mouse` polling, which can mis-read a click that vanilla also
  consumes, or miss a click during a frame hitch. Mitigated by a tight
  hit-test on empty map margin and edge-triggered detection; covered by
  the edge-trigger and hit-test tests.
- **GL state leakage.** Both render surfaces draw raw GL each frame over
  vanilla UI. Failing to restore color / blend / texture-enable corrupts
  the map chrome or tooltips. Every draw path must reset the state it
  touches; this is a hard requirement, not a nicety.
- **Per-frame cost.** The sidebar's visibility check and click poll run
  every campaign frame. Keep them to a tab-state read plus a rect test;
  do no allocation or market scanning in the per-frame path - that work
  belongs to the cached pipeline invalidated on its own cadence.
- **Custom-entity save compatibility.** `kmu_political_marker`'s type id
  is serialised into saves; renaming it after release breaks loaded
  saves, and removing the mod jar drops the plugin class
  (`ClassNotFoundException` risk on load for saves with live anchors).
  Pick the id once; document the uninstall path.
- **Map sub-view detection depends on render-order assumptions.**
  `getCurrentCoreTab()` returns `MAP` for both sub-views, so the sidebar
  relies on the probe-anchor stamp (see
  [Sub-view detection](#sub-view-detection)). This assumes the
  world-render pass (which fires the probe `render`) runs before the
  UI-coords pass within the same frame, and that the map's Sector
  sub-view renders hyperspace custom entities at all. Both hold in the
  vanilla pipeline today but are not contractual; pin with an
  in-game check during implementation. Worst case if the assumption
  breaks is a sidebar that shows on the System sub-view too - a harmless
  inert toggle, the same fallback as before.
- **Scope creep toward feature 019's full design.** Highlight views,
  the intel selector, and history recording are all one step away and
  all explicitly deferred. Holding the [Out of Scope](#out-of-scope)
  line is what keeps 022 shippable.
