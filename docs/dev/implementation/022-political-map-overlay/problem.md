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
  - [Dominance rule](#dominance-rule)
  - [Colony size weighting](#colony-size-weighting)
  - [Stability weighting](#stability-weighting)
  - [Station presence weighting](#station-presence-weighting)
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
but nothing that reads as *territorial control*.
A player cannot glance at the map and see which faction dominates which region,
where borders fall,
or where factions interleave.
The information exists in the economy (every market has an owner and a size)
but is never aggregated into a spatial,
at-a-glance picture.

Feature 022 paints a HOI4/Civ-style political layer over the existing sector map:
faction-coloured regions,
per-system presence markers,
and connectors between same-faction systems,
all derived from live market ownership.
The layer is toggled on and off by the player from a small custom sidebar drawn over the sector map.

The constraint that shapes the whole feature:
**vanilla's map tab bar and core UI tabs are not extensible.** There is no public "add a map layer" or "add a map tab" API.
So the toggle cannot be a new vanilla tab or chip alongside `Starscape` / `Names` / `Inhabited`;
it has to be a mod-drawn control rendered over the map in UI coordinates.

## Baseline Behavior

- Render a **world-space political overlay** on the hyperspace sector view,
  using the full visual model designed in feature 019:
  Voronoi cells filled in the dominant faction's colour,
  per-system blip stacks showing every faction present,
  and dog-bone connectors between same-faction systems.
  See [research: Visual model](../019-political-map/research.md#visual-model-under-candidate-a),
  [research: Presence tiers](../019-political-map/research.md#presence-tiers-under-candidate-a),
  and [research: Shape strategy - Candidate A](../019-political-map/research.md#candidate-a-voronoi-cells-leading).
- Derive territory from live market ownership using the dominance rule and presence tiers in [research: Data sources for territory](../019-political-map/research.md#data-sources-for-territory).
  Decivilised planets and abandoned stations are factionless,
  so they hold no *territory* -
  they never seed a faction-coloured region,
  join the dominance computation,
  or trigger connectors.
- Show **known decivilised systems** as neutral,
  unaffiliated cells:
  a system holding a revealed decivilised planet is *inhabited*,
  so it seeds a cell and always draws a neutral outline,
  but takes no faction colour and never counts toward dominance
  (see [Decivilized markers (neutral)](#decivilized-markers-neutral)).
  The cell appears exactly when vanilla would show the planet's decivilised status to the player.
  This overrides feature 019's blanket exclusion of decivilised entities
  ([research: Decivilised entities are factionless](../019-political-map/research.md#decivilised-entities-are-factionless)):
  022 still excludes them from *territory*,
  but surfaces them as a faction-less,
  known-but-unowned region.
- Draw a **small custom sidebar** over the sector map carrying a single on/off control for the overlay.
  The sidebar is mod-rendered in UI coordinates,
  not a vanilla tab.
- The overlay starts **hidden**.
  Clicking the sidebar toggle shows it;
  clicking again hides it.
  The choice persists in the save.
- The overlay and sidebar appear only while the player is viewing the sector map,
  and never during an interaction dialog.
  When the map is closed,
  both are gone with zero per-frame cost beyond a cheap tab-state check.
- The overlay reflects the *current* political picture:
  when market ownership changes
  (Nexerelin transfer, raid takeover, player colonisation, decivilisation),
  the colours update without a reload,
  on the refresh cadence from [research: State, persistence, lifecycle](../019-political-map/research.md#state-persistence-lifecycle).
- With no inhabited systems
  (impossible in practice, but the degenerate case),
  the overlay draws nothing and the toggle is inert -
  no errors,
  no leaked GL state.

## Initial Scope

- The **world overlay**:
  per-inhabited-system anchor entities in hyperspace whose render routine paints the Voronoi cell,
  connectors,
  and blip stack,
  exactly as specified by feature 019's recommended approach
  ([research: Recommended approach](../019-political-map/research.md#recommended-approach)).
  The overlay's render is gated on the toggle state.
- The **data pipeline**:
  dominance computation,
  presence-tier classification,
  and the Voronoi cell geometry,
  computed once and cached,
  invalidated by the fingerprint poll plus listener prods from [research: State, persistence, lifecycle](../019-political-map/research.md#state-persistence-lifecycle).
- **Neutral decivilised systems**:
  a system with a known (player-visible) decivilised planet seeds a cell
  and draws a faction-less neutral outline,
  derived from a separate planet scan
  (decivilised markets are not in the economy),
  excluded from territory.
  See [Decivilized markers (neutral)](#decivilized-markers-neutral).
- The **sidebar**:
  one mod-rendered panel over the sector map with a single on/off toggle,
  drawn in UI coordinates via `CampaignUIRenderingListener`,
  gated to the map tab.
- **Click handling** for the sidebar toggle (see [Click handling](#click-handling)).
- The **toggle state**,
  persisted in sector memory
  (see [Toggle state contract](#toggle-state-contract)).
- Sidebar label / tooltip text stored in
  [data/strings/strings.json](../../../../data/strings/strings.json)
  and resolved through `kmu.util.KmuStringKeys`,
  never inlined as Java string literals at the use site.

## Out of Scope

- **Faction highlight / per-faction selection.** The sidebar is on/off only.
  The overlay always shows all factions in dominance colours.
  Feature 019's highlight view
  (select a faction, isolate or desaturate the rest) is deferred to a later feature,
  as is any faction list in the sidebar.
- **The intel-screen selector.** Feature 019 proposed a `BaseIntelPlugin` large-description panel as the selector UI.
  That is replaced here by the sidebar and is not built in this feature.
- **Political history recording and the timeline scrubber.** The Civ-3 style change log,
  snapshots,
  and persistence
  ([research: Political history](../019-political-map/research.md#political-history-civ-3-style-timeline)) are a separate later feature.
  Feature 022 renders the *live* picture only and writes no history.
- **System-view (`W`) overlay.** The layer is hyperspace-anchored,
  so it renders only on the Sector view.
  No per-system political drawing.
- **Decivilised *territory*.** Decivilised planets never form a faction-coloured region,
  count toward dominance,
  or draw connectors -
  they are unowned.
  Their system *is* shown as a neutral cell when surveyed
  (see [Decivilized markers (neutral)](#decivilized-markers-neutral));
  it is only their participation as *territory* that is out of scope.
- **Recovering a decivilised planet's former owner.** Vanilla wipes the owning faction on deciv
  ([research](../019-political-map/research.md#decivilised-entities-are-factionless));
  022 does not reconstruct it.
  Markers are neutral,
  full stop -
  no "formerly Hegemony" colouring.
- **Stripe / diagonal-pattern rendering.** Reserved for a future warfare/invasion overlay;
  the political layer keeps a clean visual vocabulary.
- **Hotkey toggle.** The sidebar is the sole toggle surface for v1;
  a keybind can be added later without changing the state contract.

## Approach Options

The world overlay and data pipeline are settled by feature 019.
The new decisions for 022 are how to surface the toggle and how to read its clicks,
since vanilla offers no extension point on the map chrome.

### Sidebar surface

| Option | What it does | Cost | Outcome |
|---|---|---|---|
| A. `CampaignUIRenderingListener` panel | Draw a small panel over the map in UI coords (`renderInUICoordsAboveUIBelowTooltips`), gated to `CoreUITabId.MAP` | Low | A visible, discoverable on-map control with no vanilla-tab tampering. Recommended. |
| B. Hotkey only, no visible UI | Bind a key that flips the toggle; no drawn control | Very low | Cheapest, but undiscoverable - violates the user-visible intent of a "sidebar on the map". |
| C. Intel-screen panel (feature 019) | Put the toggle inside a `BaseIntelPlugin` large description | Medium | Discoverable but off the map; the user explicitly wants the control *on* the sector map. Rejected for v1. |

Recommendation:
**Option A.** It is the only option that satisfies "a small sidebar on the sector map" without touching vanilla tabs.
The render hooks are confirmed present
([CampaignUIRenderingListener.java:9](../../../../../../starsector-core/starfarer.api.zip)),
and `CampaignUIAPI.getCurrentCoreTab()` returns `CoreUITabId.MAP` so the panel can be shown only on the map.
Vanilla's `SlipstreamVisibilityManager` is a precedent for keying behaviour off `CoreUITabId.MAP`.

### Click handling

`CampaignUIRenderingListener` only *renders*;
it delivers no input events.
A mod-drawn control must read its own clicks.

| Option | What it does | Cost | Outcome |
|---|---|---|---|
| A. LWJGL `Mouse` polling | An `EveryFrameScript` polls `org.lwjgl.input.Mouse` each frame, edge-triggered on button-down, hit-tested against the sidebar rect, gated to map-open | Low | Standard campaign-HUD-button pattern. Recommended. |
| B. `CustomUIPanelAPI` injected into the map | Reuse vanilla's button/panel input handling | High / unknown | No public seam to inject a custom panel into the vanilla map view; would fight the map's own input. Rejected. |

Recommendation:
**Option A.** Poll `Mouse` in the same per-frame script that already runs for cache invalidation,
edge-trigger on the left-button transition,
and only consume the click when the cursor is inside the sidebar rect *and* the map tab is open and no dialog is showing.
This is well-trodden modding ground;
the only real risk is double-consuming a click that vanilla also acts on,
mitigated by the tight hit-test (the sidebar occupies empty map margin).

## Decisions

### Surfaces and ownership

- Live in KMU under a dedicated package (e.g. `kmu.politicalmap`).
  KMU already owns campaign-map and market tooling.
- Two render surfaces,
  one shared data cache:
  - **World overlay** -
    per-inhabited-system anchor entities of type `kmu_political_marker` in hyperspace,
    registered via `custom_entities.json`,
    tagged non-clickable,
    no sprite.
    Their `render(CampaignEngineLayers, ViewportAPI)` paints cell / connectors / blips on the layer slots chosen in [research: Visual model render-state notes](../019-political-map/research.md#visual-model-under-candidate-a).
    The entity type ID is permanent once shipped
    (custom entities serialise by ID; renaming breaks saves).
  - **Sidebar** -
    a `CampaignUIRenderingListener` that draws the panel in UI coords
    and an `EveryFrameScript` that reads its clicks.
- Both surfaces read the same toggle key and the same cached `Map<SystemId, SystemPolitics>`;
  neither owns the other.
- All identifiers prefixed `kmu_political_` so they cannot collide with vanilla,
  KMO,
  or third-party mods.

### Dominance rule

The owner of a system is decided by a four-level comparison of each faction's footprint,
every level breaking a tie in the one above so the ordering is total and the winner deterministic.
This refines feature 019's element-wise lexicographic proposal
([research: Dominance rule](../019-political-map/research.md#dominance-rule)) into a fixed chain:

1. **Combined market size.** Sum `MarketAPI.getSize()` over the faction's counted markets in the system;
   the largest sum wins.
2. **Largest single market.** At an equal sum,
   the faction holding the single biggest market wins.
3. **Planet-size sum.** At an equal sum and equal biggest market,
   the faction with more size on planets (vs stations) wins,
   ranking planets above stations.
   A market is on a planet when `MarketAPI.getPlanetEntity()` is non-null
   and on a station when it is null.
4. **Faction ID ascending.** The always-decisive backstop,
   so the winner never depends on economy or map iteration order.
   Reaching it requires an exact tie on all three size measures.

Markets whose entity the player has discovered,
that are owned and not condition-only,
feed the footprint -
the same visibility filter the cell colour uses
(see [Surfaces and ownership](#surfaces-and-ownership));
decivilised and abandoned entities are factionless and never participate.

A **hidden** market
(vanilla hidden markets like the Galatia Academy) is no longer disqualified:
once its entity is on the map it still marks its system,
but it folds in at a fixed token size of 1 at every level above -
combined size,
largest single market,
and (if on a planet) planet-size sum -
so a hidden market can flag presence without ever outweighing an openly held colony.
Discovery is still required:
a hidden market on an as-yet-undiscovered entity stays off the map until the player finds it.

The rule stays pure -
it compares plain footprint values with no Starsector types -
and the economy read that builds those footprints,
including the token-size substitution,
is confined to the ownership adapter,
so the rule can be exercised on hand-built inputs.

### Colony size weighting

Sibling rule to the dominance chain above,
and the base every other weighting rides on:
each market's size rating is multiplied by a player-set colony-size weight before stability scales it or a station lifts it,
so the map can be tuned between "raw size rules the map" and "size barely matters".

- **The weight.** A market's base size rating -
  a visible colony's own `getSize()`,
  or a hidden market's fixed presence token -
  is multiplied by the weight before any other factor folds in.
  At 1 (the default) size counts exactly as it does without the rule;
  below 1 the gap between a large and a small colony narrows,
  above 1 it widens,
  and at 0 raw size drops out entirely -
  a colony then holds a system only through its station bonus,
  if any.
- **Hidden markets scale too.** The weight multiplies a hidden market's fixed token the same way it multiplies a visible colony's real size,
  so the two stay in proportion at every weight.
  A hidden market never leaks its true size,
  but the player's dial still moves its token presence up and down with everything else.
- **Order and the fixed-point grid.** The weight is applied to the base rating first,
  then the station bonus is added,
  then the stability fraction scales the sum,
  which rounds once onto the same 1000-units-per-size-point grid the other weightings use -
  so a fractional weight (0.5, 1.5) lands cleanly
  and [the dominance rule](#dominance-rule) stays exact-integer and order-independent.
- **The control,
  no toggle.** Size is the base dominance measure,
  so there is no on/off flag like the stability and station factors carry;
  the weight itself is the control and 1 is its neutral setting.
  It is the LunaLib double `kmu_politicalMapColonySizeWeight`
  ("Political map - domination" tab, "Dominance" header, default 1),
  read once per resolution pass into the same weighting bundle as the stability and station rules.
  Presence is unaffected -
  the weight never adds or removes a footprint entry,
  only rescales its worth.

### Stability weighting

Sibling rule to the dominance chain above:
each market's size rating is scaled by its stability before any footprint math,
so every level of the comparison ranks stability-weighted worth rather than raw size.

- **The weight.** A market contributes `sizeRating * clamp(stability / 10, 0..1)` -
  the raw `getSize()`
  (or the hidden market's token rating of 1, which scales like any other size rating).
  At stability 0 a colony is worth nothing to dominance,
  at 5 half its size,
  at 10 its full size,
  so a destabilised colony holds less of its system than a functioning colony of equal size.
- **Presence is unaffected.** Worth and knowledge are separate axes:
  a stability-0 colony still counts as a known colony,
  keeps its faction's footprint entry,
  marks its system inhabited,
  and paints it when unopposed.
  Only its pull in a *contested* system drops to nothing.
- **Fixed-point grid.** Weights fold as integers -
  1000 weight units per size point at full stability -
  rather than as floats,
  so the rule's comparisons stay exact,
  its ordering total,
  and its faction-id backstop reached only on genuine ties,
  with no epsilon math inside the rule.
- **Live read,
  normal cadence.** Stability comes from `MarketAPI.getStabilityValue()` at footprint-read time,
  clamped into the vanilla 0..10 band (modded markets can sit outside it).
  The periodic snapshot re-derives footprints from the live economy,
  so a stability swing that flips a system's winner repaints on the existing refresh cadence with no extra invalidation hook.
- **Player toggle.** The weighting is gated by the LunaLib boolean `kmu_politicalMapStabilityWeighsDominance`
  ("Political map" tab, "Dominance" header),
  on by default;
  off ranks every market at its full size rating.
  The toggle is read once per resolution pass and threaded into the footprint read as a plain flag,
  so one pass resolves every system under the same rule
  and the domain read stays free of settings access.
  Presence is toggle-independent -
  the weighting never adds or removes footprint entries.

### Station presence weighting

The first military-presence factor layered onto the dominance chain:
a market with an attached defensive station holds more of its system than an otherwise-identical unstationed colony,
so a fortified world reads as the stronger presence even at equal size.

- **The bonus.** A stationed market's size rating gains the player-set station weight in size points -
  `sizeRating + stationWeight`,
  one point by default -
  added *before* the stability fraction scales it,
  so the station bonus shares in a colony's stability collapse rather than sitting outside it:
  a stability-crippled fortress keeps only the stability-scaled remnant of the extra points,
  the same as the rest of its size.
  The weight is applied after the colony-size weight has scaled the base rating,
  and is itself left unscaled by it -
  the station is worth a flat number of points,
  not a multiple of the colony's size.
- **Station detection.** A market owns a station when one of its `getConnectedEntities()` carries the `"station"` tag
  and is not opted out by `"NO_ORBITAL_STATION"` -
  the exact scan vanilla's `OrbitalStation` runs.
  Connected entities are an ownership link the game maintains,
  so a station found this way is the market's own;
  a spatial orbit scan is deliberately not used
  (it would false-positive on rival stations, independents sharing the planet, and abandoned hulks that keep the tag after their market dies).
  No industry IDs are read -
  the entity tag captures vanilla and modded stations alike.
- **Hidden markets.** A hidden market earns a configurable fraction of the station weight -
  `stationWeight * hiddenRate` on its token rating,
  before stability scales the sum -
  so a fortified secret base reads above a bare unstationed hidden market without ever matching an openly held stationed colony.
  The rate defaults to one half.
- **Fixed-point grid,
  exact rule unchanged.** The lifted rating folds through the same 1000-units-per-size-point grid and rounds once,
  so a fractional hidden bonus (half a point = 500 units) lands cleanly
  and [the dominance rule](#dominance-rule) stays exact-integer and order- independent -
  `SystemDominance` is untouched.
- **Player controls.** Gated by the LunaLib boolean `kmu_politicalMapStationWeighsDominance`
  ("Political map - domination" tab, "Dominance" header),
  on by default;
  off drops the bonus and ranks markets by stability-weighted size alone.
  The magnitude is the double `kmu_politicalMapStationWeight` (default 1)
  and the hidden-market fraction is the double `kmu_politicalMapStationHiddenMarketRate` (0..1, default 0.5).
  All read once per resolution pass alongside the stability toggle
  and colony-size weight into one weighting bundle threaded through the footprint read,
  so a pass resolves every system under the same rule
  and the domain read stays free of settings access.
  Presence is toggle-independent -
  the station bonus never adds or removes footprint entries.

### Decivilized markers (neutral)

A revealed decivilised planet makes its system count as *inhabited* on the political map,
but *unaffiliated*.
Inhabited and affiliated are two separate axes:
a dead colony is presence,
not territory.

- **Inhabited but unaffiliated.** A system holding a revealed decivilised planet seeds its own Voronoi cell
  and always draws a neutral outline -
  no fill,
  no faction colour -
  regardless of the `kmu_politicalMapShowUninhabited` setting,
  which governs only genuinely empty space.
  It is excluded from the dominance rule and takes no owner colour,
  so it reads as a known-but-unowned region rather than territory.
- **Admission regardless of access.** Inhabitation alone puts a system on the map:
  `MapVisibility.shouldAppearOnMap` is `StarSystems.isReachable` OR inhabited
  (a discovered colony or a revealed decivilised planet),
  so a transverse-only or abyssal world -
  hidden from the map by its own `star_hidden_on_map` / abyssal tags -
  still appears once it holds a revealed dead colony.
  The hiding tags never veto an inhabited system.
- **Detection.** Decivilised markets are not in `Sector.getEconomy().getMarketsCopy()`,
  so they are found by scanning planets per system (`StarSystemAPI.getPlanets()`) for a market carrying the `"decivilized"` condition (`MarketAPI.getSpecificCondition`).
  `isPlanetConditionMarketOnly()` alone is insufficient -
  every uninhabited planet has a condition-only market for hazard / atmosphere;
  the `"decivilized"` condition is what marks a *former colony*.
- **Survey gate.** A system reveals only when the player would already know the planet is decivilised in vanilla -
  the overlay must neither reveal a dead colony the player has not learned about,
  nor hide one vanilla already shows.
  **Vanilla's own visibility is the bar;
  we mirror it,
  we do not second-guess it.** The gate is two independent parts:

  ```plaintext
  hasBeenEncountered = market.getSurveyLevel() != MarketAPI.SurveyLevel.NONE
  cond               = market.getSpecificCondition("decivilized")
  isRevealed         = cond != null && (!cond.requiresSurveying() || cond.isSurveyed())
  shouldReveal       = hasBeenEncountered && isRevealed
  ```

  - `hasBeenEncountered` guards against revealing systems the player has never visited
    (a planet stays at `SurveyLevel.NONE` until first contact).
    This is the only "has the player been here" gate;
    it is not a depth requirement.
  - `isRevealed` is vanilla's per-condition visibility,
    verbatim.
    A colony that decivilises *during play* has its condition force-marked surveyed by the deciv process,
    so it reveals immediately;
    a *procgen* dead world follows whatever survey rule the condition carries.

  There is deliberately no `SurveyLevel.FULL` requirement:
  imposing one would hide decivilised planets that vanilla already shows at a lower level,
  which is exactly the second-guessing this gate avoids.
  Survey levels run `NONE -> SEEN -> PRELIMINARY -> FULL` for reference,
  but the gate keys off condition visibility,
  not a fixed tier.
- **Color.** The neutral faction's UI colour
  (`getFaction("neutral").getBaseUIColor()`),
  so the cell reads as unaffiliated and stays consistent with how unowned space is drawn.
- **Cadence.** The decivilised set changes on the same months-to-never cadence as ownership;
  its scan folds into the same cached pipeline,
  invalidated by the on-map visibility fingerprint.
  The fingerprint includes each decivilised system -
  both that it is on the map and that it is the unaffiliated,
  always-drawn kind -
  so a freshly surveyed ruin appears without a reload.

### Toggle state contract

- The overlay on/off flag lives in sector memory (`Sector.getMemoryWithoutUpdate()`) under key `$kmu_political_overlay_enabled`,
  default unset = **hidden**.
  Sector memory persists in the save,
  so the player's choice survives reload.
  No XStream changes.
- The world anchors' `render` early-returns when the flag is off (no GL state touched).
  The sidebar toggle is the sole writer of the flag.

### Sub-view detection

The map tab has two sub-views -
Sector (hyperspace) and System (`W`) -
and **no public API distinguishes them**:
`CoreUITabId` has a single `MAP` value,
`CampaignUIAPI` exposes no sub-view accessor,
and the `ViewportAPI` handed to the render hooks carries only geometry,
not the location being shown.
The discriminator instead comes from the render engine itself:

- A hyperspace-anchored `CustomCampaignEntityPlugin.render()` is only invoked when the view is showing hyperspace.
  The Sector sub-view shows hyperspace;
  the System sub-view shows a star system's interior,
  where hyperspace entities are culled and never render.
- So an always-on,
  invisible **probe anchor** in hyperspace stamps `lastHyperFrame = <current frame>` inside its `render()` (no visible draw).
  The sidebar's UI-coords listener runs later in the same frame and reads the stamp:
  `tab == MAP && lastHyperFrame == currentFrame` means the Sector sub-view is active;
  a stale stamp means the System sub-view.
- The probe is independent of the overlay toggle
  (it stamps even when the overlay is hidden),
  so sub-view detection works regardless of overlay state.
  It can be a dedicated anchor or the per-system anchor set re-used for the stamp;
  either way the stamp write is the only work in the off state.

This avoids reflection into obfuscated map UI classes -
the engine's own location culling is the source of truth.

### Sidebar visibility contract

| Condition | Sidebar drawn | Overlay drawn |
|---|---|---|
| Not on the map tab (`getCurrentCoreTab() != MAP`) | no | no |
| On the map tab, dialog showing (`isShowingDialog()`) | no | no |
| On the map tab, System sub-view (probe stamp stale) | no | no |
| On the map tab, Sector sub-view, toggle **off** | yes | no |
| On the map tab, Sector sub-view, toggle **on** | yes | yes |

- Hiding the sidebar on the System sub-view (rather than greying it) is the chosen default:
  the overlay is meaningless there,
  so the cleanest read is "the control belongs to the Sector view".
  Greying-with-tooltip is the fallback if playtest shows the sidebar vanishing is confusing.
- Every frame the sidebar draws,
  it must fully restore any GL state it touches (colour, blend, texture-enable)
  so vanilla map chrome and tooltips render unaffected.

### Lifecycle

- One-time wiring (in `data/`):
  register the `kmu_political_marker` type in `custom_entities.json`.
  No code.
- Per-save wiring
  (in `KMU_ModPlugin.onGameLoad(boolean)`):
  - Ensure exactly one anchor entity per inhabited system in hyperspace,
    idempotent by ID `kmu_pm_<systemId>`.
  - Ensure the always-on hyperspace probe anchor exists (idempotent by a fixed ID) for sub-view detection
    (see [Sub-view detection](#sub-view-detection)).
  - Register the sidebar `CampaignUIRenderingListener`.
  - Register the per-frame script that (a) polls the sidebar click and (b) drives cache invalidation
    (fingerprint poll + listener prods per [research: State, persistence, lifecycle](../019-political-map/research.md#state-persistence-lifecycle)).
  - Build the Voronoi geometry cache and the dominance / presence cache.
- Disabling or uninstalling:
  the only persisted artifact is the sector memory flag and the anchor entities.
  Anchors are inert when the flag is off;
  removing the mod drops the custom-entity plugin class,
  so the standard custom-entity uninstall caveat applies (see [Risks](#risks)).

## Tests

- Unit:
  with the toggle off,
  the world-overlay render routine performs no draw calls and touches no GL state.
- Unit:
  clicking the sidebar rect flips `$kmu_political_overlay_enabled`;
  a click outside the rect does not.
- Unit:
  the sidebar visibility predicate matches the [Sidebar visibility contract](#sidebar-visibility-contract) table for each `(currentCoreTab, isShowingDialog, subView, toggle)` combination,
  where `subView` is driven by the probe stamp freshness.
- Unit:
  the probe's `render` stamps the current frame;
  a stamp older than the current frame reads as the System sub-view.
- Unit:
  click polling is edge-triggered -
  a held button flips the toggle exactly once,
  not once per frame.
- Unit:
  the dominance rule picks the expected faction for hand-built footprints,
  isolating each of the four tie-break levels -
  combined weight,
  heaviest single market,
  planet weight,
  then faction ID (see [Dominance rule](#dominance-rule)).
- Unit:
  the footprint read multiplies each market's base size rating by the colony-size weight -
  a doubled weight doubles a visible market's size and a hidden market's token alike,
  while the flat station point is added after the weight and so is left unscaled
  (see [Colony size weighting](#colony-size-weighting)).
- Unit:
  the footprint read scales each market's weight by stability -
  half stability halves the contribution,
  stability 0 yields a weightless but still-present footprint entry,
  and out-of-band values clamp into 0..10
  (see [Stability weighting](#stability-weighting)).
- Unit:
  a disabled factor skips its work,
  not just its result -
  a zero station weight skips the connected-entity station scan,
  and a rating already zeroed
  (zero colony-size weight, no station bonus) skips the stability read,
  still folding the market in at zero weight for presence.
- Unit:
  presence-tier classification returns the highest matching tier for planet-only,
  station-only,
  and settlement-only systems
  ([research: Presence tiers](../019-political-map/research.md#presence-tiers-under-candidate-a)).
- Unit:
  a faction's live markets seed faction-coloured cells and dominance,
  while decivilised / abandoned entities contribute no faction presence and take no colour -
  a revealed decivilised planet still seeds a neutral cell.
- Unit:
  a planet counts as a revealed decivilised planet only when both gate parts hold -
  `hasBeenEncountered` (`getSurveyLevel() != NONE`) and `isRevealed`
  (`!requiresSurveying() || isSurveyed()`).
  Each part failing alone
  (never-encountered at `NONE`; encountered but condition still hidden) yields no reveal.
- Unit:
  an uninhabited planet with a condition-only market
  but no `"decivilized"` condition is not a revealed decivilised planet
  (guards against treating every rock as a dead colony).
- Unit:
  a system whose only presence is a surveyed decivilised planet seeds a Voronoi cell
  and always draws a neutral outline,
  but never counts toward dominance and takes no faction colour.
- Unit (wiring):
  the per-frame script is registered as an `EveryFrameScript`;
  the cache invalidates when the dominance fingerprint changes and stays put when it does not.
- Unit:
  with zero inhabited systems the pipeline yields an empty cache and the overlay draws nothing.

## Open Questions

Resolved (recorded here):

- **Toggle surface.** A `CampaignUIRenderingListener`-drawn sidebar on the map,
  gated to `CoreUITabId.MAP`.
  Resolves feature 019's open question on where the toggle lives.
- **Sidebar scope.** On/off only;
  no faction highlight or selection in v1.
- **History recording.** Out of scope for this feature.
- **Sub-view detection.** No public API distinguishes the Sector and System sub-views,
  but the hyperspace probe anchor does it deterministically
  (see [Sub-view detection](#sub-view-detection)).
  The sidebar is hidden on the System sub-view by default.

Still open:

- **Decivilised condition default survey requirement.** The marker gate reads vanilla's per-condition visibility
  (`requiresSurveying()` / `isSurveyed()`) directly,
  so it stays correct whatever that default is -
  no code branch depends on the answer.
  The open part is purely informational:
  confirm in-game *when* a deciv marker first appears
  (on sensor contact vs after a full survey) so the documented player-facing behaviour matches reality.
  The `hasBeenEncountered && isRevealed` gate does not change either way.
- **Sidebar placement and footprint.** Exact screen anchor (left margin vs right),
  size,
  and whether it scales with UI scale settings.
  Tuned in playtest.
- **Exact visual constants** (alpha, radius, step) for the overlay -
  inherited from feature 019's still-open tuning placeholders
  ([research: Open questions](../019-political-map/research.md#open-questions)).
- **Pirate / non-market bases as territory** -
  inherited open question from feature 019;
  does not block the sidebar work.

## Risks

- **No input API for the drawn sidebar.** Click handling is hand-rolled LWJGL `Mouse` polling,
  which can mis-read a click that vanilla also consumes,
  or miss a click during a frame hitch.
  Mitigated by a tight hit-test on empty map margin and edge-triggered detection;
  covered by the edge-trigger and hit-test tests.
- **GL state leakage.** Both render surfaces draw raw GL each frame over vanilla UI.
  Failing to restore colour / blend / texture-enable corrupts the map chrome or tooltips.
  Every draw path must reset the state it touches;
  this is a hard requirement,
  not a nicety.
- **Per-frame cost.** The sidebar's visibility check and click poll run every campaign frame.
  Keep them to a tab-state read plus a rect test;
  do no allocation or market scanning in the per-frame path -
  that work belongs to the cached pipeline invalidated on its own cadence.
- **Custom-entity save compatibility.** `kmu_political_marker`'s type ID is serialised into saves;
  renaming it after release breaks loaded saves,
  and removing the mod jar drops the plugin class
  (`ClassNotFoundException` risk on load for saves with live anchors).
  Pick the ID once;
  document the uninstall path.
- **Map sub-view detection depends on render-order assumptions.** `getCurrentCoreTab()` returns `MAP` for both sub-views,
  so the sidebar relies on the probe-anchor stamp
  (see [Sub-view detection](#sub-view-detection)).
  This assumes the world-render pass (which fires the probe `render`) runs before the UI-coords pass within the same frame,
  and that the map's Sector sub-view renders hyperspace custom entities at all.
  Both hold in the vanilla pipeline today but are not contractual;
  pin with an in-game check during implementation.
  Worst case if the assumption breaks is a sidebar that shows on the System sub-view too -
  a harmless inert toggle,
  the same fallback as before.
- **Scope creep toward feature 019's full design.** Highlight views,
  the intel selector,
  and history recording are all one step away and all explicitly deferred.
  Holding the [Out of Scope](#out-of-scope)
  line is what keeps 022 shippable.
