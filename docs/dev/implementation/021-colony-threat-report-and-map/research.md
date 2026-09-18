# Colony Threat Report + Map Overlay - Research

Research date:
2026-06-03

Scope:
feasibility and API surface for a consolidated **colony-threat intel item** + **map overlay** that highlights threatened player systems on both the sector map
and the intel-screen embedded map.
Map-rendering primitives were covered for feature 19
([019-political-map-layer/research.md][19]) and are reused here without re-derivation.

Sources read directly:
extracted `starfarer.api.zip`,
`ExerelinCore.jar` (`mods\Nexerelin-0.12.1e\jars\`),
and the JARs of every installed mod with a plausible colony-threat surface
(Tahlan, Domain Phase Lab, Secrets of the Frontier, IndustrialEvolution, EmergentThreats IX/Vice, Knights of Ludd, Hostile Intercept, Random Assortment of Things, Domain Explorarium, Hiver Swarm, Star Federation, Grand Colonies, Captain's Log, stelnet, MagicLib).
Deep dives are split out:

- [research-vanilla.md][rv] -
  vanilla threat catalog + per-threat field map
- [research-nexerelin.md][rn] -
  Nexerelin offensive-fleet + rebellion surface
- [research-other-mods.md][ro] -
  third-party mod threat surfaces

This top-level document captures the cross-cutting findings
and the load-bearing decisions for `problem.md` / `plan.md`.

## Index

- [TL;DR](#tldr)
- [Threat-surface taxonomy](#threat-surface-taxonomy)
- [Vanilla threats](#vanilla-threats)
- [Nexerelin threats](#nexerelin-threats)
- [Third-party mod threats](#third-party-mod-threats)
- [Per-colony threat query strategy](#per-colony-threat-query-strategy)
- [Stage, progress, and ETA model](#stage-progress-and-eta-model)
- [Intel-screen UI shape](#intel-screen-ui-shape)
- [Map overlay surface](#map-overlay-surface)
- [State, persistence, lifecycle](#state-persistence-lifecycle)
- [Soft-dep posture](#soft-dep-posture)
- [Risks and gotchas](#risks-and-gotchas)
- [Open questions](#open-questions)

## TL;DR

1. **Vanilla already aggregates most colony threats** under `HostileActivityEventIntel`
   (the "Colony Crises" item - [research-vanilla.md#11][rv-hae]).
   Its per-system rollup `HAEStarSystemDangerData` is the right backbone for the map overlay -
   reuse it instead of re-computing.
2. **Three structural shapes exist** and the intel item has to render all three:
   (a) the aggregated HAE event,
   (b) per-dispatch raid intels
   (`HegemonyInspectionIntel`, `PunitiveExpeditionIntel`, Nex's `OffensiveFleetIntel` hierarchy, IndEvo/Tahlan raids),
   and (c) per-market bookkeeping intels
   (`PirateBaseIntel`/`PirateActivityIntel`, `LuddicPathCellsIntel`, Nex `RebellionIntel`, Nex `GroundBattleIntel`).
3. **Tag-based discovery works as a safety net.** Every vanilla
   and Nex threat intel that targets player colonies tags itself with the literal string `"Colony threats"`
   (`Tags.INTEL_COLONIES`, [research-vanilla.md#6][rv-tags] / [research-nexerelin.md#2][rn-tag]).
   Filter by tag for completeness,
   dispatch by class for rich fields.
4. **Per-colony index does not exist anywhere.** Neither vanilla nor Nex keeps a `Map<MarketAPI, List<Threat>>`.
   KMU walks the intel manager and post-filters by `intel.getTarget() == market`.
   The lists are small (tens of items at sector scale);
   a one-frame cache is enough.
5. **Nexerelin is the largest contributor** when present.
   `OffensiveFleetIntel` is a stable base class with `getTarget()`,
   `getETA()`,
   `getCurrentStage()`,
   `getFP()`,
   `getOutcome()`,
   `getType()` ([research-nexerelin.md#31][rn-of]).
   `RebellionIntel` exposes `getOngoingEvent(market)` and `RebellionCreator` exposes `getRebellionPoints(market)` (0..100) for the "risk building" gauge.
6. **Only DPL,
   SotF,
   Tahlan,
   and IndEvo contribute among installed mods.** DPL
   and SotF plug into vanilla HAE via `HostileActivityFactor` and are picked up for free;
   Tahlan and IndEvo emit `RaidIntel`-shaped items that need a class-walk to discover.
   EmergentThreats,
   Knights of Ludd,
   Hiver Swarm,
   RAT,
   etc. ship no colony-threat intel despite suggestive names
   ([research-other-mods.md#5][ro-deadends]).
7. **Stage and progress models are split.** HAE uses `BaseEventIntel.progress`/`stages` with a progress-bar API (`addEventProgressBar`);
   raids (vanilla + Nex) use `RaidIntel.getETA()` over a fixed 5-stage `Organize -> Assemble -> Travel -> Action -> Return`.
   The intel item renders these two shapes side by side rather than collapsing them.
8. **UI precedent**:
   vanilla's HAE `createLargeDescription` + `GroundBattleIntel.createLargeDescription` (Nex) + MagicLib's `BountyBoardIntelPlugin`
   (left-side list 300px / right-side detail) together give the layout.
   We pick the BountyBoard shape for the left-list-right-detail substrate;
   mode dispatch and progress bars come from the vanilla/Nex precedents.
9. **Map overlay reuses feature 19's anchor-entity render pipeline.** Per-inhabited-system hyperspace anchor + per-frame `render()` paints a threat-coloured ring scaled by HAE `getTotalActivityMagnitude(system)` plus a raid-count indicator.
   No new draw infrastructure;
   same allocation-free cache pattern as the political layer.

## Threat-surface taxonomy

The consolidated intel item operates over **four conceptually distinct threat axes**,
each rendered with its own widget:

| Axis | Description | Vanilla example | Nex example | Mod example |
|------|-------------|-----------------|-------------|-------------|
| **Aggregated pressure** | sector-wide trend, slow burn, has stages | `HostileActivityEventIntel` | `NexHostileActivityEventIntel` (subclass) | DPL / SotF factors plug into HAE |
| **Imminent dispatch** | a fleet is en route now | `HegemonyInspectionIntel`, `PunitiveExpeditionIntel` | `InvasionIntel`, `NexRaidIntel`, `SatBombIntel`, `CounterInvasionIntel`, `BaseStrikeIntel`, `BlockadeWrapperIntel` | `LegioSiegeMissionIntel` (Tahlan), `PrivateerBaseRaidIntel` (IndEvo), `dpl_MercenaryAttack` (DPL) |
| **Resident hostile** | already in the system, drip-fed events | `PirateBaseIntel`/`PirateActivityIntel`, `LuddicPathCellsIntel` | - | - |
| **Internal unrest** | risk grows on the colony itself | low stability, `recent_unrest` | `RebellionIntel`, rebellion-points gauge | - |

Each axis answers a different question and the UI should not collapse them into one bar:

- Aggregated pressure -> "where are we on the crisis arc,
  what's the next stage threshold".
  Renders as the HAE progress bar.
- Imminent dispatch -> "who is coming,
  when,
  with what force".
  Renders as a stage tracker + ETA per intel item.
- Resident hostile -> "this is happening *now* and won't stop until we hit the source".
  Renders as a static row with the source base and the per-tick effect.
- Internal unrest -> "this is the colony's own fault".
  Renders as a per-market score / countdown.

## Vanilla threats

Full catalog in [research-vanilla.md#1][rv-cat].
Headlines:

- **`HostileActivityEventIntel`** is the single aggregator ([research-vanilla.md#11][rv-hae]).
  Reach it via `HostileActivityEventIntel.get()` (sector memory key `"$hae_ref"`).
  Per-system risk via `computeDangerData(StarSystemAPI)` ([research-vanilla.md#21][rv-hae-api]) returns `HAEStarSystemDangerData { system, maxMag, totalMag, sortMag, List<HAEFactorDangerData> }`.
  This is the substrate for both the intel rows AND the map overlay.
- **`HegemonyInspectionIntel`** + **`PunitiveExpeditionIntel`** are the vanilla raid-shaped dispatches
  ([research-vanilla.md#12][rv-hi] / [#13][rv-punex]).
  Both extend `RaidIntel`,
  share the `Organize/Assemble/Travel/Action/Return` stage layout and `getETA()`.
- **`PirateBaseIntel`** -> spinoff **`PirateActivityIntel`** keep the `pirate_activity` market condition fresh ([research-vanilla.md#14][rv-pirate]).
  Per-system lookup `PirateBaseIntel.getIntelFor(StarSystemAPI)`.
- **`LuddicPathCellsIntel`** is per-market ([research-vanilla.md#15][rv-path]);
  use `LuddicPathCellsIntel.getCellsForMarket(market)`.
  `incidentTracker` drives the next-sabotage ETA.
- **`FactionHostilityIntel`**
  ([research-vanilla.md#16][rv-hostility]) is AI vs AI war;
  not strictly per-colony but informs the "who might come next" sidebar.
  Tag is `"Hostilities"`,
  NOT `"Colony threats"`.

Static (condition) penalties to surface as colony-state,
not events:
`hostile_activity`,
`pirate_activity`,
`pather_cells`,
`recent_unrest`,
plus the aggregate `MarketAPI.getStability()` value ([research-vanilla.md#42][rv-cond]).

## Nexerelin threats

Full catalog in [research-nexerelin.md#3][rn-cat].
Headlines:

- **`OffensiveFleetIntel`** is the common base for every Nex offensive ([research-nexerelin.md#31][rn-of]).
  Concrete subclasses worth listing with distinct severity icons:
  `InvasionIntel` (occupy),
  `SatBombIntel` (destroy - render at maximum severity),
  `NexRaidIntel` (disrupt),
  `BaseStrikeIntel` (anti-pirate; rarely player-targeted),
  `CounterInvasionIntel`
  (sticky - `setAbortIfNonHostile(false)`),
  `BlockadeWrapperIntel`,
  `ColonyExpeditionIntel`
  (null `getTarget()` until landing - **null-guard required**).
- **`RebellionIntel`** is the internal-unrest channel
  ([research-nexerelin.md#310][rn-reb] / [#6][rn-reb-deep]).
  `RebellionIntel.getOngoingEvent(market)` gives the active rebellion;
  `RebellionCreator.getInstance().getRebellionPoints(market)` gives the per-market 0..100 risk gauge that builds up before a rebellion fires.
  The accurate per-day rate has to replicate `(1 - numOngoing/MAX_ONGOING)` scaling;
  the raw `getRebellionIncrement` is not what `processMarket` actually applies.
- **`GroundBattleIntel`** ([research-nexerelin.md#39][rn-gb]) is the active-occupation channel that takes over after an `InvasionIntel` lands.
  Per-market accessor `GroundBattleIntel.getOngoing(market)`.
- **`VengeanceFleetIntel` is NOT a colony threat** -
  it chases the player *fleet* ([research-nexerelin.md#311][rn-veng]).
  Either omit,
  or surface under a separate "personal" tab.
  Do not list under any market.
- **Diplomacy context**:
  `DiplomacyManager.getFactionsAtWarWithFaction()`
  + `DiplomacyBrain.hasCeasefireWith()` + `DiplomacyBrain.getCeasefires()` ([research-nexerelin.md#5][rn-dip]) lets the intel item show "no fleet is en route,
    but Hegemony is at war and there is no ceasefire" as a forward-looking warning.

## Third-party mod threats

Full catalog in [research-other-mods.md#4][ro-permod].
Headlines:

- **Domain Phase Lab** and **Secrets of the Frontier** plug into vanilla HAE via `BaseHostileActivityFactor` subclasses
  (`dpl_HostileActivityFactor`, `SotfDustkeeperHAFactor`).
  They are picked up for free by enumerating `HAE.get().getFactors()`.
  DPL also emits a `GenericRaidFGI` (`dpl_MercenaryAttack`) when its factor fires.
- **Tahlan Shipworks** runs an independent siege pipeline
  (`LegioSiegeMissionIntel extends RaidIntel`, [research-other-mods.md#41][ro-tahlan]).
  Does NOT register as a `HostileActivityFactor`.
  Targets any system hostile to Legio;
  player inherits when at war.
- **IndustrialEvolution** has `PrivateerBaseRaidIntel extends RaidIntel`
  ([research-other-mods.md#44][ro-indevo]),
  spawned by the vanilla Privateer Base industry against any hostile market.
  Player inherits transitively.
- **EmergentThreats IX/Vice,
  Knights of Ludd,
  Hostile Intercept,
  RAT,
  Domain Explorarium,
  Hiver Swarm,
  Star Federation,
  Grand Colonies,
  Captain's Log** all have **no colony-threat surface** despite suggestive names
  ([research-other-mods.md#5][ro-deadends]).

## Per-colony threat query strategy

There is no built-in `getThreatsForMarket(MarketAPI)`.
KMU's adapter assembles one from these primitives,
in this order:

```java
List<Threat> threatsFor(MarketAPI m) =
    HAE.computeDangerData(m.getStarSystem()).factorData     // aggregated
  + getIntel(HegemonyInspectionIntel.class)  where target == m
  + getIntel(PunitiveExpeditionIntel.class)  where target == m
  + getIntel(PirateBaseIntel.class)          where target == m.system AND affectedMarkets contains m
  + LuddicPathCellsIntel.getCellsForMarket(m)
  + if (Nex): getIntel(OffensiveFleetIntel.class) where target == m
  + if (Nex): RebellionIntel.getOngoingEvent(m), GroundBattleIntel.getOngoing(m)
  + getIntel(IntelInfoPlugin.class) where tags contains "Colony threats"
        AND not already collected   // safety net for unknown mods
```

The safety-net sweep at the end is what lets KMU pick up `LegioSiege`,
`PrivateerBaseRaid`,
`dpl_MercenaryAttack`,
plus any future mod that follows the same convention.
Render those generically as "raid against {market} from {system},
ETA {days},
stage {n/5}".

Cache the assembled list per market for one frame (clear on `advance`).
Sector-scale list size is tens of items;
allocation-free is overkill,
but do not rebuild every per-frame `render()` call.

## Stage, progress, and ETA model

Two stage models exist and the intel item renders both:

- **`BaseEventIntel`** ([research-vanilla.md#51][rv-bei]) -
  `progress`/`maxProgress` (HAE's max is 600),
  `List<EventStageData>` with per-stage thresholds,
  `getLastActiveStage()`,
  `getMonthlyProgress()` for the signed projection.
  Vanilla draws this via `TooltipMakerAPI.addEventProgressBar(this, 100f)` + `addEventStageMarker(...)`.
  **Reuse the vanilla bar widget** -
  do not redraw.
- **`RaidIntel`** ([research-vanilla.md#52][rv-raid]) -
  fixed 5-stage layout `OrganizeStage -> AssembleStage -> TravelStage -> ActionStage -> ReturnStage`.
  `getCurrentStage()` is an int index;
  `RaidStage.getElapsed()`/`getMaxDays()` lets us draw "stage 3/5,
  17 days remaining".
  `getETA()` gives the user-facing arrival countdown.

For Nex `OffensiveFleetIntel`,
both contracts inherit from the vanilla `RaidIntel` base;
the same widget covers it ([research-nexerelin.md#31][rn-of]).

## Intel-screen UI shape

Three precedents combine into the recommended layout:

1. **MagicLib `BountyBoardIntelPlugin`** ([research-other-mods.md#45][ro-magic])
   - the closest existing "large description with 300px left list + right detail panel + listener swap".
     Lift the layout skeleton.
2. **Nex `GroundBattleIntel.createLargeDescription`** ([research-nexerelin.md#71][rn-ui])
   - mode dispatcher pattern
     (the right pane swaps based on a `viewMode` enum).
     Adopt for "Summary | Threats | Forecast" tabs inside the detail pane.
3. **Vanilla `HostileActivityEventIntel.createLargeDescription` / `BaseEventIntel.createLargeDescription`** -
   the progress-bar rendering ([research-vanilla.md#51][rv-bei]).
   Reuse the bar API verbatim.

Concrete shape for the consolidated KMU intel item (working name `KmuColonyThreatIntel`):

- `BaseIntelPlugin`,
  `hasLargeDescription() = true`,
  `hasSmallDescription() = false`.
- `getIntelTags()` returns `"Colony threats"` + `Tags.INTEL_IMPORTANT`
  (so it floats to the top of the existing tab).
- `getMapLocation()` returns `null`
  (the intel itself is *also* a map selector - see next section).
- `createIntelInfo` (list row):
  icon + "Colony Threats" + a one-line summary like "{N} systems threatened,
  {M} active raids,
  {K} rebellions" with colour driven by the worst per-system danger.
- `createLargeDescription`:
  - **left panel** (300px wide, scrollable):
    one row per player-owned system,
    sorted by danger desc.
    Row shows system name,
    dominant threat colour,
    raid-count badge,
    ETA-to-next badge.
  - **right panel** (rest):
    the selected system's detail.
    Top section is the HAE progress bar for that system's factor contributions
    (we synthesize, the global HAE bar is sector-wide).
    Middle section is the list of active raids/invasions/inspections with per-row stage tracker + ETA.
    Bottom section is the resident hostiles (pirate base, Pather cells) + condition penalties.
  - "Show on map" button per row -> `IntelUIAPI.showOnMap(anchor)`
    and `recreateIntelUI()` after selection changes.

Add once per save in `KMU_ModPlugin.onGameLoad(boolean)` via `Sector.getIntelManager().addIntel(plugin, true)` (true = no popup).
Same lifecycle KMU uses elsewhere.

## Map overlay surface

Reuse the feature 19 render pipeline ([019/research][19]):
one hyperspace anchor entity per inhabited player system,
a `CustomCampaignEntityPlugin` whose `render(layer, viewport)` draws under `TERRAIN_2`.

For threats specifically:

- **Disc colour**:
  blend by dominant threat faction colour.
  Map keys derived from HAE's `HAEStarSystemDangerData.factorData[0].factor` (highest-magnitude factor) -> `FactionAPI.getBaseUIColor()` of the factor's owning faction.
- **Ring radius / opacity**:
  scale by HAE's `getTotalActivityMagnitude(system)` ([research-vanilla.md#21][rv-hae-api]).
- **Per-system raid badge**:
  pulsing inner dot if any `OffensiveFleetIntel`/`RaidIntel` has `getCurrentStage() >= Travel`
  and `getETA() < 60` days targeting any market in the system.
- **Suppress** when `viewport.getAlphaMult() == 0` and `isRenderWhenViewportAlphaMultIsZero()` is false (same toggle as political layer).
- **Vanilla precedent**:
  HAE itself uses `ui.showOnMap(d.system.getHyperspaceAnchor())` in `tableRowClicked(...)` to focus the map.
  Same call in our intel rows.

The map and intel surfaces share **one cache**:
the same `Map<StarSystemAPI, SystemThreatSnapshot>` that the intel item uses to render the left-side list feeds the render plugin's per-system colour.
Recompute on the same fingerprint-poll + listener-prods pattern that feature 19 already uses for the political layer ([019/research][19]).
Use the existing HAE listener hooks too:
`EconomyAware`,
`FleetEventListener.reportBattleOccurred`,
and the `ColonyCrisesSetupListener` end-of-`setup()` callback ([research-vanilla.md#21][rv-hae-api]).

## State, persistence, lifecycle

- **One-time wiring**:
  same `custom_entities.json` pattern as the political layer (`kmu_threat_marker`).
  Per-save:
  ensure one anchor per inhabited player system in `onGameLoad`,
  idempotent (`kmu_tm_<systemId>`).
- **Intel item**:
  ensure exactly one in `onGameLoad`.
  ID by class via `Sector.getIntelManager().getFirstIntel(KmuColonyThreatIntel.class)`.
- **Selected-system state** (for the intel right-pane):
  sector memory `$kmu_threat_selected_system_id` (String).
  Cleared when the system disappears.
- **Cache**:
  in-memory only,
  never persisted.
  Rebuilt on the fingerprint poll (1.0s) + listener prods.
- **XStream**:
  no changes needed -
  we hold no Nex types in persistent state.
  The adapter always projects into KMU-owned value types.

## Soft-dep posture

Aligned with the project's existing posture
(LunaLib hard, LazyLib/MagicLib optional - see CLAUDE.md guidance):

- **LazyLib**:
  same recommendation as feature 19
  (use `DrawUtils.drawCircle` for ring + ramp),
  hard dep is fine since KMU already pulls LazyLib for the political layer.
- **Nexerelin**:
  **optional**.
  Hide every Nex symbol behind one adapter
  (`KmuNexThreatAdapter` - shape sketched in [research-nexerelin.md#84][rn-adapter]).
  Gate the adapter on `Global.getSettings().getModManager().isModEnabled("nexerelin")`.
  Never persist Nex types in KMU's save data.
- **Tahlan / IndEvo / DPL / SotF**:
  **discovered by class probing**,
  not declared.
  Resolve via `Class.forName(name, false, classLoader)` at startup
  (stelnet's `IntelIsClass` pattern - [research-other-mods.md#34][ro-stelnet]);
  a null lookup means the filter is inert.
  KMU never imports these mod symbols.
- **MagicLib**:
  optional.
  Used as a UI reference;
  no runtime dependency required
  (MagicSettings toggle is a nice-to-have).

## Risks and gotchas

- **`render()` allocation-freeness**:
  same rule as feature 19 -
  cache per-system colour/threat off the hot path;
  never call `Misc.getPlayerMarkets` in `render`.
- **`ColonyExpeditionIntel.getTarget()` is null** until the colony lands ([research-nexerelin.md#38][rn-colexp]).
  Null-guard,
  or route this subclass to its target *planet* instead of a market.
- **`RebellionIntel.getRebellionIncrement` is NOT the applied rate**
  ([research-nexerelin.md#67][rn-reb-eta]) -
  it ignores `(1 - numOngoing/MAX_ONGOING)`.
  KMU should replicate that scaling for ETA.
- **`hostile_activity` stat-plugin is in `starfarer_obf.jar`** ([research-vanilla.md#71][rv-gap-cond]) -
  only the constant ID is in the API zip.
  Read the magnitude indirectly via `MarketAPI.getStability().getFlatMods()` keyed by condition id.
- **Vengeance fleets** chase the player fleet,
  not a colony.
  If we ever surface them at all,
  do it in a separate "personal threats" view -
  never under a market row.
- **`PunExGoal`/`PunExReason` have no public getters**
  ([research-vanilla.md#23][rv-punex-api]).
  Either accept that the row shows the vanilla `getName()` string,
  or reflect.
  Recommend accepting the name string;
  reflection here is fragile and the gain is small.
- **`BlockadeWrapperIntel`** delegates much display to a wrapped FGI
  ([research-nexerelin.md#36][rn-blockade]).
  Wrap `getCurrentStage()` / `getETA()` calls in try/catch when iterating Nex offensives.
- **Save compat**:
  `kmu_threat_marker` entity type ID and intel class name become save-stable surfaces -
  pick once.

## Open questions

These need a design decision before `plan.md` is locked:

1. **Should HAE's sector-wide event get its own row,
   or only its per-system slices?** Argument for both:
   the player still wants to see "Colony Crises stage HA_2,
   540/600" once even when surfaced per-system.
2. **Should the resident-hostile axis (pirate base, Pather cells) show as a row in the per-system detail,
   or as a separate top-level section?** Vanilla mixes them into HAE;
   the intel item could too.
3. **`FactionHostilityIntel` and the diplomacy "they are at war and have the means" inference** -
   is that on the threats screen,
   or is it a separate "geopolitics" view?
4. **Should `HegemonyInspectionIntel` always be surfaced,
   even when Nex is absent?** Almost certainly yes;
   flagged for confirmation.
5. **`ColonyExpeditionIntel` (NPC colony planting)** -
   threat or not?
   It threatens to claim a contested system,
   not to attack an existing colony.
   Suggest:
   surface under a separate "system claim" sub-view only when the targeted planet shares a constellation with a player market.
6. **Tahlan / IndEvo raids** -
   surface generically (faction + ETA + stage) under the per-system detail,
   or hide them on the grounds that they're not player-aimed by design?
   Recommend surface,
   gated on "system contains a player market AND raid.getSystem() == that system".
7. **Map overlay vs. existing HAE map markers** -
   HAE already paints per-system danger on its own intel-screen map.
   Should KMU's overlay render on the campaign view only,
   or both?
   Recommend both:
   campaign view for at-a-glance,
   intel-screen embedded map for the deep-dive.
8. **Per-system selector position on the intel screen** -
   left column (BountyBoard pattern) or top row of tabs?
   Recommend left column;
   readers scan vertically and the system count can grow.

---

[19]: ../019-political-map-layer/research.md
[rv]: research-vanilla.md
[rn]: research-nexerelin.md
[ro]: research-other-mods.md
[rv-cat]: research-vanilla.md#1-threat-class-catalog
[rv-hae]: research-vanilla.md#11-hostileactivityeventintel-colony-crises
[rv-hi]: research-vanilla.md#12-hegemonyinspectionintel-ai-core-inspection
[rv-punex]: research-vanilla.md#13-punitiveexpeditionintel-faction-punitive-expedition
[rv-pirate]: research-vanilla.md#14-piratebaseintel--pirateactivityintel
[rv-path]: research-vanilla.md#15-luddic-path-cells--sabotage
[rv-hostility]: research-vanilla.md#16-factionhostilityintel
[rv-hae-api]: research-vanilla.md#21-hostileactivityeventintel
[rv-punex-api]: research-vanilla.md#23-punitiveexpeditionintel
[rv-cond]: research-vanilla.md#42-current-condition-penalties-retrospective
[rv-bei]: research-vanilla.md#51-baseeventintel-haes-parent
[rv-raid]: research-vanilla.md#52-raidintel-inspections--punitive-expeditions
[rv-tags]: research-vanilla.md#6-intel-sidebar-tags-vanilla-uses
[rv-gap-cond]: research-vanilla.md#7-open-questions--known-gaps
[rn-tag]: research-nexerelin.md#2-the-colony-threats-intel-tag
[rn-cat]: research-nexerelin.md#3-catalog-of-nex-threat-intels
[rn-of]: research-nexerelin.md#31-offensivefleetintel-hierarchy
[rn-blockade]: research-nexerelin.md#36-blockadewrapperintel--nexblockadefgi
[rn-colexp]: research-nexerelin.md#38-colonyexpeditionintel
[rn-gb]: research-nexerelin.md#39-groundbattleintel
[rn-reb]: research-nexerelin.md#310-rebellionintel
[rn-veng]: research-nexerelin.md#311-vengeancefleetintel-player-fleet-not-colony
[rn-dip]: research-nexerelin.md#5-diplomacy--hostility-context
[rn-reb-deep]: research-nexerelin.md#6-rebellion--unrest-deep-dive
[rn-reb-eta]: research-nexerelin.md#9-gaps-and-open-questions
[rn-ui]: research-nexerelin.md#71-intelinfoplugin-large-description-pattern
[rn-adapter]: research-nexerelin.md#84-recommended-adapter-shape
[ro-permod]: research-other-mods.md#4-per-mod-sections
[ro-tahlan]: research-other-mods.md#41-tahlan-shipworks
[ro-indevo]: research-other-mods.md#44-industrialevolution
[ro-magic]: research-other-mods.md#45-magiclib
[ro-stelnet]: research-other-mods.md#46-stelnet
[ro-deadends]: research-other-mods.md#5-mods-checked-with-nothing-relevant
