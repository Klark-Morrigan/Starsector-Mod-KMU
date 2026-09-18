# Vanilla colony threat model - research notes

Source of truth for feature #21
(consolidated colony threat intel item + map overlay).
All `file:line` references point into `c:\a_Games\Starsector\.sources-cache\` unless otherwise noted;
the canonical prefix used in this document is

```plaintext
.sources-cache\starsector-core\starfarer.api\com\fs\starfarer\api\impl\campaign\
```

abbreviated below as `<core>\`.

The `starfarer.api.jar` decompiled cleanly with CFR 0.152 -
every reference here is to `.java` source,
no bytecode fallback was needed.

## Index

1. [Threat-class catalog](#1-threat-class-catalog)
2. [Per-threat field / API map](#2-per-threat-field--api-map)
3. [Per-colony threat surface](#3-per-colony-threat-surface)
4. [Conditions vs. events](#4-conditions-vs-events)
5. [Stage / progress model (`BaseEventIntel`, `HostileActivityEventIntel`)](#5-stage--progress-model)
6. [Intel sidebar tags vanilla uses](#6-intel-sidebar-tags-vanilla-uses)
7. [Open questions / known gaps](#7-open-questions--known-gaps)


## 1. Threat-class catalog

Vanilla splits "colony threat" intel across three structural shapes:

- **One sector-wide event** that aggregates many factors:
  `HostileActivityEventIntel` (the "Colony Crises" item).
  Each player system gets a per-system "danger" computed from the same factors.
- **Per-event raid intel** that spawns when a faction physically dispatches fleets at a colony:
  `HegemonyInspectionIntel`,
  `PunitiveExpeditionIntel`,
  pirate raids modelled via the `PirateBaseIntel` -> `PirateActivityIntel` -> `RaidIntel` chain,
  Luddic Path `LuddicPathCellsIntel` incidents.
- **Standalone intel** that doesn't follow the event/raid shape:
  `FactionHostilityIntel` (open war between two AI factions).

### 1.1 `HostileActivityEventIntel` (Colony Crises)

File:
`<core>\intel\events\HostileActivityEventIntel.java:77` (class decl).

Singleton-ish:
the live instance is stashed under sector memory key `"$hae_ref"`,
accessible via `HostileActivityEventIntel.get()` -
`HostileActivityEventIntel.java:81`,
`:92-:94`.

Factors / activities registered in `setup()`
(`HostileActivityEventIntel.java:102-:146`):

| Activity factor                          | Bound causes (events that increase the factor)                                              |
|-----------------------------------------|---------------------------------------------------------------------------------------------|
| `PirateHostileActivityFactor`           | `KantasProtectionPirateActivityCause2`, `StandardPirateActivityCause2`, `PirateBasePirateActivityCause2`, `KantasWrathPirateActivityCause2` |
| `LuddicPathHostileActivityFactor`       | `LuddicPathAgreementHostileActivityCause2`, `StandardLuddicPathActivityCause2`              |
| `PerseanLeagueHostileActivityFactor`    | `StandardPerseanLeagueActivityCause`                                                        |
| `TriTachyonHostileActivityFactor`       | `TriTachyonStandardActivityCause`                                                           |
| `LuddicChurchHostileActivityFactor`     | `LuddicChurchStandardActivityCause` (the "Knights of Ludd" line on the UI - see `LuddicChurchHostileActivityFactor.java:96-:98`) |
| `SindrianDiktatHostileActivityFactor`   | `SindrianDiktatStandardActivityCause`                                                       |
| `HegemonyHostileActivityFactor`         | `HegemonyAICoresActivityCause` (the HAE "AI core suspicion" surrogate; separate from the raid-shaped `HegemonyInspectionIntel`) |
| `RemnantHostileActivityFactor`          | `RemnantNexusActivityCause`                                                                 |

Non-activity (display/blowback) factors also added in `setup()`:

- `HAColonyDefensesFactor` (defenses reduce progress)
- `HAShipsDestroyedFactorHint` and `HAShipsDestroyedFactor`
  (kills near a player system reduce progress - see `reportBattleOccurred` `HostileActivityEventIntel.java:687-:752`)
- `HABlowbackFactor` (carries forward "saved" progress)
- `HAPirateBaseDestroyedFactor`,
  `HAPatherBaseDestroyedFactor`
  (triggered from `reportBattleOccurred` when a relevant base's station fleet is destroyed - `:697-:730`)

UI bookkeeping classes nested inside HAE:

- `HAEFactorDangerData`
  (`HostileActivityEventIntel.java:980-:983`) -
  one factor + its magnitude in a given system.
- `HAEStarSystemDangerData` (`:997-:1003`) -
  one system,
  its `maxMag`,
  `totalMag`,
  sorted list of factor data.
  Returned by `computePlayerSystemDangerData()` (`:481-:502`) and `computeDangerData(StarSystemAPI)` (`:504-:529`).
- `HAERandomEventData` (`:985-:995`) -
  the rolled "next major event" payload.

Stages
(`enum Stage`, `HostileActivityEventIntel.java:1005-:1014`):
`START, MINOR_EVENT, HA_EVENT, HA_1, HA_2, INCREASED_DEFENSES, HA_3, HA_4`.
Thresholds set in `setup()`:
`MINOR_EVENT` at 300,
`HA_EVENT` at 600 (see `:111-:115`).
`MAX_PROGRESS = 600`,
`ESCALATE_PROGRESS = 550`,
`RESET_MIN/MAX = 0/400` (`:84-:87`).

### 1.2 `HegemonyInspectionIntel` (AI core inspection)

File:
`<core>\intel\inspection\HegemonyInspectionIntel.java:42-:107`.

Extends `RaidIntel` and uses 5 stages:
`HIOrganizeStage`,
`HIAssembleStage`,
`HITravelStage`,
`HIActionStage`,
`HIReturnStage` (constructor at `:85-:104`).

Spawned by `HegemonyInspectionManager.checkInspection()` -
`<core>\intel\inspection\HegemonyInspectionManager.java:48-:58`.
The manager sums `getAICoreUseValue(market)` over player-owned non-hyperspace markets,
rolls suspicion,
fires `createInspection()` (`:60-:101`) when suspicion crosses the threshold
(initially 250, doubled per attempt, capped at 1000).

Lives under sector memory key `"$core_hegemonyInspectionManager"` (`HegemonyInspectionManager.java:17`).

Targeting state:
`target` (`MarketAPI`),
`from` (`MarketAPI`),
`targetFaction`,
`expectedCores` (`List<String>`),
`outcome`,
`orders`
(`AntiInspectionOrders.COMPLY | RESIST | ...`) (`HegemonyInspectionIntel.java:49-:60`).

### 1.3 `PunitiveExpeditionIntel` (faction punitive expedition)

File:
`<core>\intel\punitive\PunitiveExpeditionIntel.java:45-:99`.

Also extends `RaidIntel`;
same `Organize/Assemble/Travel/Action/Return` shape.
Differences from inspections:

- `goal`
  (`PunExGoal` enum at `PunitiveExpeditionManager.java:393-:398`):
  `RAID_PRODUCTION`,
  `RAID_SPACEPORT`,
  `BOMBARD`.
- `bestReason`
  (`PunExReason` at `PunitiveExpeditionManager.java:400-:409`) with `PunExType` `ANTI_COMPETITION | ANTI_FREE_PORT | TERRITORIAL` (`:411-:416`).
- `targetIndustry` is set when the goal is industry-specific
  (constructor at `PunitiveExpeditionIntel.java:64-:99`).

Triggered by `PunitiveExpeditionManager`
(file `<core>\intel\punitive\PunitiveExpeditionManager.java`).
Key fields:
`MAX_CONCURRENT`,
`PROB_TIMEOUT_PER_SENT`,
per-faction `PunExData` (`:380-:391`) with `anger`,
`threshold`,
`numAttempts`.
Lives under memory key `"$core_punitiveExpeditionManager"` (`:30`).

### 1.4 `PirateBaseIntel` + `PirateActivityIntel`

`<core>\intel\bases\PirateBaseIntel.java:65-:99`.
The base intel is what the player can hunt;
it advances tier
(`PirateBaseTier` - `PirateBaseIntel.java:1202` getter) and picks a target system
(`StarSystemAPI target`, getter at `:1063-:1065`).
It then publishes a spinoff `PirateActivityIntel(system, this)` -
`<core>\intel\bases\PirateActivityIntel.java:31-:47` whenever the target includes a player market.

`PirateActivityIntel.advance()` (`PirateActivityIntel.java:77-:90`) adds the `pirate_activity` market condition to every affected market each tick,
and removes it on `notifyEnding()` (`:68-:74`).

`PirateBaseIntel.getAffectedMarkets(StarSystemAPI)` -
`PirateBaseIntel.java:1074-:1081` -
lists per-base affected markets in a given system;
useful for "given this market,
which bases hit it".

Static lookups:

- `PirateBaseIntel.getIntelFor(StarSystemAPI)`
  (referenced from `HostileActivityEventIntel.java:698`) returns the active base intel for a system,
  or null.
- `Global.getSector().getIntelManager().getIntel(PirateBaseIntel.class)` returns all of them.

### 1.5 Luddic Path cells / sabotage

`<core>\intel\bases\LuddicPathCellsIntel.java:43-:87` -
one intel per market that has Pather cells.
Constructor sets the `pather_cells` market condition (`:78-:80`).
Stored in the intel manager and queried with `LuddicPathCellsIntel.getCellsForMarket(MarketAPI)` (`LuddicPathCellsIntel.java:114-:125`) and `LuddicPathCellsIntel.getCellsForBase(LuddicPathBaseIntel, boolean)` (`:103-:112`).

Per-cell threat surface:

- `incidentType`
  (`IncidentType` enum at `LuddicPathCellsIntel.java:713-:...`) -
  values include `REDUCED_STABILITY`,
  `INDUSTRY_SABOTAGE`,
  `PLANETBUSTER`
  (see picker at `:547-:558`, dispatch at `:593-:600`).
- `incidentTracker` (`IntervalUtil`) at `:60` drives the next-incident roll
  and is the source for "ETA until next sabotage" in the UI.
- Successful sabotage hooks into `RecentUnrest.get(market).add(...)` (`:595`)
  and disrupts an industry (`:598`).

The associated base is `LuddicPathBaseIntel`
(`<core>\intel\bases\LuddicPathBaseIntel.java`) and is destroyed-detected the same way as pirate bases
(HAE `reportBattleOccurred` - `HostileActivityEventIntel.java:712-:728`).

### 1.6 `FactionHostilityIntel`

File:
`<core>\intel\FactionHostilityIntel.java:22-:49`.
Not strictly a "colony threat" -
it tracks open war between two AI factions -
but the fallout (raids, blockades) does target colonies.
Lifetime is driven by `FactionHostilityManager` (same package) over `CHECK_INTERVAL = 60.0f` days with `END_PROB = 0.25f` (`:26-:27`).
Exposes `getOne()`,
`getTwo()`,
`getId()` (`:71-:81`).

### 1.7 Things that look adjacent but are NOT colony-threat intel

For completeness,
so we don't add them to the consolidated item:

- `HyperspaceTopographyEventIntel` (`<core>\intel\events\ht\`) -
  per-fleet,
  not per-colony.
- `TriTachyonCommerceRaiding` (`<core>\intel\events\ttcr\`) -
  factor bookkeeping;
  surfaces via HAE,
  not as standalone intel.
- `CommerceBountyManager` -
  bounties on player fleets,
  not colony threats.


## 2. Per-threat field / API map

For each threat the consolidated intel item will need to read:
controlling class,
key fields,
query API,
listener hook.
The table below captures the load-bearing bits.

### 2.1 `HostileActivityEventIntel`

- Class:
  `<core>\intel\events\HostileActivityEventIntel.java:77`.
- Lookup:
  `HostileActivityEventIntel.get()` (memory `"$hae_ref"`, `:92-:94`) OR `Global.getSector().getIntelManager().getFirstIntel(HostileActivityEventIntel.class)`.
- Progress / max progress:
  inherited from `BaseEventIntel.getProgress()`
  (`<core>\intel\events\BaseEventIntel.java:656-:658`) and `getMaxProgress()` (`:457-:459`).
  HAE max is `600` (`HostileActivityEventIntel.java:84`).
- Stage thresholds:
  `getStages()` -> `List<EventStageData>` (`BaseEventIntel.java:465-:467`);
  per-stage `progress`,
  `wasEverReached`,
  `randomized`,
  `progressToRollAt`,
  `progressToResetAt`,
  `rollData` (`BaseEventIntel.java:818-:843`).
- Current last-reached stage:
  `getLastActiveStage(boolean)` (`BaseEventIntel.java:509-:519`) and `isStageActive(Object)` (`:501-:507`).
- Per-system danger:
  `computePlayerSystemDangerData()`
  (`HostileActivityEventIntel.java:481-:502`) returns `List<HAEStarSystemDangerData>` sorted by `sortMag` then `totalMag`.
  Each entry has `system`,
  `maxMag`,
  `totalMag`,
  `sortMag` and `List<HAEFactorDangerData>` (factor + magnitude).
- Single-system version:
  `computeDangerData(StarSystemAPI)` (`:504-:529`).
- Raw total magnitude in a system:
  `getTotalActivityMagnitude(system)` (`:613-:628`).
- Numeric -> human bucket:
  `getDanger(float)` (`:531-:548`) returning `WarSimScript.LocationDanger`
  (`NONE, MINIMAL, LOW, MEDIUM, HIGH, EXTREME`).
  Stringified by `getDangerString(...)` (`:550-:576`) and coloured by `getDangerColour(...)` (`:578-:587`).
- Factor surfaces:
  `getActivityOfClass(Class)` (`:473-:479`),
  `getActivityCause(Class activity, Class cause)` (`:448-:458`),
  `getFactors()`
  (inherited - `BaseEventIntel.java:588-:590`).
- Listener hooks:
  - `EconomyAPI.EconomyUpdateListener`
    (HAE itself - `:79`, `economyUpdated()` `:662-:664`).
  - `FleetEventListener.reportBattleOccurred(...)` (`:687-:752`) -
    where "ships destroyed near a player system" reduces progress.
  - `ColonyCrisesSetupListener`
    (sector listener manager, `<core>\..\listeners\ColonyCrisesSetupListener.java:1-:10`) fired at the end of `setup()` (`HostileActivityEventIntel.java:145`).
  - Standard `BaseIntelPlugin.advanceImpl(float)` ticks per frame;
    the parent `BaseEventIntel.reportEconomyTick(int)` adds monthly progress (`BaseEventIntel.java:642-:654`).
- UI artifacts:
  - Vanilla intel tag:
    `"Colony threats"` added in `getIntelTags(SectorMapAPI)`
    (`HostileActivityEventIntel.java:863-:867`).
  - Display name:
    `"Colony Crises"` (`:415-:417`).
  - Bar is rendered via `BaseEventIntel.createLargeDescription(...)` (`BaseEventIntel.java:95-:185`) using `EventProgressBarAPI`.

### 2.2 `HegemonyInspectionIntel`

- Class:
  `<core>\intel\inspection\HegemonyInspectionIntel.java:42`.
- Lookup:
  `Global.getSector().getIntelManager().getIntel(HegemonyInspectionIntel.class)` (multiple can exist).
- Target / source / faction:
  `getTarget()` `:121-:123`,
  `getFrom()` `:125-:127`,
  the parent `RaidIntel.getFaction()` (Hegemony),
  `targetFaction` field at `:53`,
  `expectedCores` `List<String>` at `:55` (getter `:143-:145`).
- Player flags:
  `isPlayerTargeted()` (inherited `RaidIntel.java:140-:143`),
  `isEnteredSystem()` `:155-:157`.
- ETA:
  `RaidIntel.getETA()` (`RaidIntel.java:221-:241`) sums remaining days across `OrganizeStage`/`AssembleStage`/`TravelStage` before the action stage.
  Per-stage primitives:
  `RaidStage.getElapsed()` and `getMaxDays()` (`RaidIntel.java:702-:704`).
- Current stage:
  `getCurrentStage()` (`RaidIntel.java:73-:75`) returns index into `stages`.
  Stage objects
  (`getStages` is internal but `getOrganizeStage()`, `getAssembleStage()`, `getActionStage()` exist - `RaidIntel.java:85-:107`).
- Outcome:
  `HegemonyInspectionOutcome` field,
  getter `:163-:165`.
  Set when the inspection ends.
- Listener hooks:
  - `InspectionEndedListener` registered on the intel itself
    (`HegemonyInspectionIntel.java:59-:115`).
  - Standard intel updates via `sendUpdateIfPlayerHasIntel(MADE_HOSTILE_UPDATE | ENTERED_SYSTEM_UPDATE | OUTCOME_UPDATE, ...)` (`:46-:48`, `:182-:192`, `:200-:202`).

### 2.3 `PunitiveExpeditionIntel`

- Class:
  `<core>\intel\punitive\PunitiveExpeditionIntel.java:45`.
- Lookup:
  `IntelManager.getIntel(PunitiveExpeditionIntel.class)` (many).
- Target / source:
  `getTarget()` (`:111-:113`),
  `getFrom()` (`:119-:121`),
  `getTargetFaction()` (`:115-:117`),
  `targetIndustry` field at `:59`.
- Goal / reason:
  `goal` (`PunExGoal`) at `:53`,
  `bestReason` (`PunExReason`) at `:58`.
  No public getters -
  read via reflection if absolutely needed,
  or instead derive from name string from `getName()` (`:150-:167`, which encodes outcome).
- ETA / stage:
  same `RaidIntel.getETA()` and `getCurrentStage()` as inspections.
- Outcome:
  `PunExOutcome` enum field at `:56` -
  `TASK_FORCE_DEFEATED, COLONY_NO_LONGER_EXISTS, RAID_FAIL, BOMBARD_FAIL, AVERTED, SUCCESS` (see usage at `:153-:164`, `:196-:202`).
- Listener hooks:
  outcome/entered-system updates
  (`:50-:51`, `sendOutcomeUpdate()` `:140-:142`, `sendEnteredSystemUpdate()` `:144-:147`).

### 2.4 `PirateBaseIntel` / `PirateActivityIntel`

- `PirateBaseIntel`
  (`<core>\intel\bases\PirateBaseIntel.java:65`):
  - `getMarket()` (`:991-:993`) -
    the base's host market (the station).
  - `getTarget()` (`:1063-:1065`) -
    the system being raided.
  - `getAffectedMarkets(StarSystemAPI)` (`:1074-:1081`) -
    markets that the base currently hits in a given system.
  - `getTier()` (`:1202-:...`) -
    `PirateBaseTier` enum used for fleet scaling and bounty (`setBounty()` at `:995-:1041`).
  - Static lookup by system:
    `PirateBaseIntel.getIntelFor(StarSystemAPI)`
    (used at `HostileActivityEventIntel.java:698`).
- `PirateActivityIntel`
  (`<core>\intel\bases\PirateActivityIntel.java:26`):
  - `getSource()` (`:57-:59`) -> `PirateBaseIntel`.
  - Adds the `pirate_activity` market condition to affected markets in `advance()` (`:77-:90`) and removes it in `notifyEnding()` (`:68-:74`).
- Listener hooks:
  `BaseIntelPlugin.advance(...)` ticks,
  plus the global `PiracyRespiteScript` toggle
  (referenced at `PirateActivityIntel.java:87`).

### 2.5 `LuddicPathCellsIntel`

- Class:
  `<core>\intel\bases\LuddicPathCellsIntel.java:43`.
- Lookups:
  `LuddicPathCellsIntel.getCellsForMarket(MarketAPI)` (`:114-:125`),
  `getCellsForBase(LuddicPathBaseIntel, boolean)` (`:103-:112`),
  `getClosestBase(MarketAPI)` (`:89-:101`).
- Target / source:
  `getMarket()` (`:127-:...`).
- Severity / progress:
  `sleeper` (`:57`),
  `incidentType` (`:64`),
  `prevIncident` (`:66`),
  `prevIncidentSucceeded` (`:67`),
  `numIncidentAttempts` (`:62`),
  `incidentTracker` (`:60`).
- Update tokens:
  `UPDATE_DISSOLVED`,
  `UPDATE_DISRUPTED`,
  `INCIDENT_PREP`,
  `INCIDENT_PREVENTED`,
  `INCIDENT_HAPPENED` (`:52-:56`).
- Listener hooks:
  - Implements `FleetEventListener` (`:46`).
  - `PatherCellListener.reportCellsDisrupted(...)` -
    `<core>\..\listeners\PatherCellListener.java:1-:10`.
- Incident pick weights:
  `<core>\intel\bases\LuddicPathCellsIntel.java:547-:558`
  (`REDUCED_STABILITY` weighted 10, `INDUSTRY_SABOTAGE` weighted 10, `PLANETBUSTER` rare).
- Side effects:
  `RecentUnrest.get(market).add(3, "...sabotage")` for stability hits (`:593-:596`);
  industry disruption for sabotage (`:598`).

### 2.6 `FactionHostilityIntel`

- Class:
  `<core>\intel\FactionHostilityIntel.java:22`.
- Lookup:
  `IntelManager.getIntel(FactionHostilityIntel.class)`.
- Fields:
  `getOne()`,
  `getTwo()`,
  `getId()` (`:71-:81`).
- Listener hooks:
  only standard `BaseIntelPlugin`.
  Manager polls every `CHECK_INTERVAL = 60` days and rolls `END_PROB = 0.25` to end hostilities (`advanceImpl(...)` `:60-:69`).


## 3. Per-colony threat surface

There is no single vanilla method "given a `MarketAPI`,
give me every threat".
The pieces have to be assembled.
The cleanest pattern (used by HAE itself):

1. For HAE per-system risk,
   call `HostileActivityEventIntel.get().computeDangerData(market.getStarSystem())` and inspect the returned `HAEStarSystemDangerData.factorData` for per-factor magnitudes
   (`HostileActivityEventIntel.java:504-:529`).
2. For "which colonies sit in this system?":
   `Misc.getMarketsInLocation(system, "player")` -
   `<core>\util\Misc.java:713-:732`
   (used by HAE itself at `HostileActivityEventIntel.java:260` and `:322-:327`).
3. For "all player colonies anywhere":
   `Misc.getPlayerMarkets(boolean includeNonPlayerFaction)` -
   `<core>\util\Misc.java:750-:762`.
4. For "all systems with at least one player market":
   `Misc.getPlayerSystems(boolean)` -
   `<core>\util\Misc.java:764-:...`.
5. For pirate bases that hit this market:
   - `IntelManager.getIntel(PirateBaseIntel.class)`,
     then for each entry check `intel.getTarget() == market.getStarSystem()` AND `intel.getAffectedMarkets(market.getStarSystem()).contains(market)`.
6. For Pather cells on this market:
   `LuddicPathCellsIntel.getCellsForMarket(market)` (`LuddicPathCellsIntel.java:114-:125`).
7. For pending raids targeting this market:
   - Inspections:
     `IntelManager.getIntel(HegemonyInspectionIntel.class)`,
     filter by `intel.getTarget() == market`.
   - Punitive expeditions:
     same pattern with `PunitiveExpeditionIntel`.
8. For market condition state (already-applied threats):
   - `market.hasCondition("pirate_activity")` / `market.hasCondition("pather_cells")` / `market.hasCondition("hostile_activity")` / `market.hasCondition("recent_unrest")` -
     constants at `<core>\ids\Conditions.java:8-:13`,
     `:53`.
   - `market.getStability()` / `market.getStabilityValue()` -
     `<core>\..\econ\MarketAPI.java:105` (the consolidated stability rating).

Sketch helper that we'd want on the KMU side (pseudo-Kotlin):

```kotlin
data class MarketThreatSnapshot(
    val market: MarketAPI,
    val haeMag: Float,                       // 0.0 .. 1.0+ from HAE
    val haeFactors: List<HAEFactorDangerData>,
    val pirateBases: List<PirateBaseIntel>,
    val patherCells: LuddicPathCellsIntel?,
    val incomingInspection: HegemonyInspectionIntel?,
    val incomingPunitive: List<PunitiveExpeditionIntel>,
    val activeConditions: List<String>       // hostile_activity, pirate_activity, ...
)
```

Construction reads from the APIs above;
no encapsulation breaches required.


## 4. Conditions vs. events

The threat surface has two distinct axes that the consolidated intel item needs to keep separate:

### 4.1 Ongoing intel events (forward-looking)

These have ETAs,
stages,
outcomes;
cause flashing "new" intel updates.

- `HostileActivityEventIntel`
  (sector-wide, but `computeDangerData` slices per system).
- `HegemonyInspectionIntel` (per dispatch).
- `PunitiveExpeditionIntel` (per dispatch).
- `LuddicPathCellsIntel`
  (per market; `incidentTracker` drives ETA).
- `PirateBaseIntel`
  (per base; its tier and target system are the forward-looking bits).
- `FactionHostilityIntel` (per faction pair).

### 4.2 Current condition penalties (retrospective)

These are *now*-state modifiers on the market and show up in the colony screen condition list,
not as intel updates.
They typically don't have an ETA;
they have a strength (the magnitude of the stat mod).

- `hostile_activity` market condition -
  added/removed by HAE `cleanUpHostileActivityConditions()`
  (`HostileActivityEventIntel.java:666-:671`) but the condition stat itself lives in `<core>\econ\impl\HostileActivity*.java`
  (these classes were not in the glob; the constant ID is at `<core>\ids\Conditions.java:8`).
- `pirate_activity` -
  added by `PirateActivityIntel.advance()` (`PirateActivityIntel.java:86-:89`),
  removed on `notifyEnding()` (`:68-:74`).
  Constant at `Conditions.java:12`.
- `pather_cells` -
  added by `LuddicPathCellsIntel` constructor (`LuddicPathCellsIntel.java:78-:80`).
  Constant at `Conditions.java:13`.
- `recent_unrest` -
  applied via `RecentUnrest.get(market).add(...)` from sabotage and player hostile acts (`LuddicPathCellsIntel.java:595`).
  Constant at `Conditions.java:53`.
- Stability itself -
  aggregate;
  `MarketAPI.getStability()` (`MutableStat` of stability modifiers) and `getStabilityValue()` (`MarketAPI.java:105`).

For the consolidated intel item:
render events with their ETAs and progress bars;
render condition penalties as a current-state list with the condition's icon and tooltip
(vanilla colony screen already does this - we can reuse the condition's plugin description via `MarketCondition.getPlugin().createTooltip(...)`).


## 5. Stage / progress model

### 5.1 `BaseEventIntel` (HAE's parent)

File:
`<core>\intel\events\BaseEventIntel.java:29-:909`.

State:

- `progress: int` (`:33`) -
  current points.
- `maxProgress: int`
  (`:34`, default 1000; HAE overrides to 600).
- `stages: List<EventStageData>` (`:35`).
- `factors: List<EventFactor>` (`:37`).
- `progressDeltaRemainder: float` (`:39`) -
  sub-tick accumulator so fractional monthly progress doesn't get lost.

Each `EventStageData` (`BaseEventIntel.java:818-:898`) carries:

- `id: Object` (typically an enum value).
- `progress: int` -
  the threshold at which the stage becomes active.
- `wasEverReached: boolean`.
- `isOneOffEvent`,
  `isRepeatable`.
- `randomized: boolean` plus `progressToResetAt`,
  `progressToRollAt`,
  `rollData` -
  drives the "roll a major event when crossing threshold,
  reset progress when crossing back down" mechanic.
- `iconSize: StageIconSize` (MEDIUM/LARGE) for the bar marker.

Key methods we'd touch for rendering a progress bar:

- `getProgress()` / `setProgress(int)` (`:656-:678`).
- `getMaxProgress()` (`:457-:459`).
- `getStages()` (`:465-:467`).
- `getLastActiveStage(boolean includeOneOffEvents)` (`:509-:519`) -
  the "where are we now" indicator.
- `isStageActive(Object stageId)` / `isStageActiveAndLast(...)` (`:473-:507`).
- `getMonthlyProgress()` (`:625-:639`) -
  signed projection of next month's delta (negative = trending down).
  HAE shortcuts negative deltas to 0 in `reportEconomyTick(...)` (`:642-:654`).
- `getMaxMonthlyProgress()` -
  HAE overrides with the settings.json values `ha_maxMonthlyProgress` / `ha_maxMonthlyProgressEasy`
  (`HostileActivityEventIntel.java:937-:943`).

Rendering reference -
vanilla draws its own bar via `TooltipMakerAPI.addEventProgressBar(this, 100.0f)`
and stage markers via `addEventStageMarker(EventStageDisplayData)` (`BaseEventIntel.java:101-:118`),
using `bar.getXCoordinateForProgress(int)` to place markers.
We'd reuse this API for our consolidated intel rather than redrawing.

### 5.2 `RaidIntel` (inspections / punitive expeditions)

File:
`<core>\intel\raid\RaidIntel.java:49-:712`.

State:

- `stages: List<RaidStage>` (`:49`) -
  typically `OrganizeStage -> AssembleStage -> TravelStage -> ActionStage -> ReturnStage`.
- `currentStage: int`,
  `failStage: int`
  (private, accessed via `getCurrentStage()` `:73-:75` and `getFailStage()` `:81-:83`).
- `extraDays: float` (`:117-:123`) -
  slack added to ETAs.

Each `RaidStage` (`:691-:705`) exposes `getStatus()` returning `RaidStageStatus.ONGOING|SUCCESS|FAILURE`,
`getElapsed()`,
`getMaxDays()`,
`getExtraDaysUsed()`.

ETA computation:
`getETA()` (`RaidIntel.java:221-:241`) sums remaining days in `OrganizeStage`,
`AssembleStage` (fixed 20-day cap),
and `TravelStage`
(computed via `RouteLocationCalculator.getTravelDays(...)`),
but only forward of the current stage;
stops at the action stage.

`isPlayerTargeted()` (`:140-:143`) is the cheap "this raid is aimed at us" flag that downstream UI uses to colour the entry red.

For a progress-bar style rendering of an inspection/expedition:
stage order is fixed,
so a five-segment progress bar with the current segment filled by `elapsed/maxDays` works well.
`getETA()` is the user-facing "arrives in N days" number.


## 6. Intel sidebar tags vanilla uses

Tag constants live at `<core>\ids\Tags.java`,
with the relevant slice:

| Constant                          | String value          | Source (`Tags.java` line)         |
|----------------------------------|----------------------|----------------------------------|
| `INTEL_COLONIES`                 | `"Colony threats"`   | `:235`                           |
| `INTEL_MILITARY`                 | `"Military"`         | `:231`                           |
| `INTEL_HOSTILITIES`              | `"Hostilities"`      | `:230`                           |
| `INTEL_BOUNTY`                   | `"Bounties"`         | `:214`                           |
| `INTEL_EXPLORATION`              | `"Exploration"`      | `:219`                           |
| `INTEL_MAJOR_EVENT`              | `"Major events"`     | `:215`                           |
| `INTEL_IMPORTANT`                | `"Important"`        | `:212`                           |

Observed usage on the threat classes:

- `HostileActivityEventIntel.getIntelTags(...)` adds `"Colony threats"` (`:863-:867`).
- `HegemonyInspectionIntel.getIntelTags(...)` adds `"Military"` + `"Colony threats"` + the inspecting faction ID (`:392-:396`).
- `PunitiveExpeditionIntel.getIntelTags(...)` adds `"Military"` + `"Colony threats"` + faction ID (`:351-:355`).
- `PirateActivityIntel.getIntelTags(...)` adds `"pirates"` and `"Colony threats"` when player markets exist in-system (`:160-:166`).
- `LuddicPathCellsIntel.getIntelTags(...)` adds `"luddic_path"` and `"Colony threats"` when player-owned (`:463-:468`).
- `PirateBaseIntel.getIntelTags(...)` adds `"Bounties"`,
  `"Exploration"`,
  conditionally `"Colony threats"`,
  plus the host market faction (`:809-:823`).
- `LuddicPathBaseIntel.getIntelTags(...)` -
  same shape as `PirateBaseIntel` (`:532-:545`).
- `FactionHostilityIntel.getIntelTags(...)` uses `"Hostilities"` plus both faction IDs (`:163-:167`) -
  notably NOT `"Colony threats"`.

**Recommendation for the consolidated intel item:** add `"Colony threats"`
(matches vanilla; lets the player find it under the existing tab) and optionally `"Important"` (`Tags.INTEL_IMPORTANT`) so it floats to the top.
Do not invent a new tag -
the existing `"Colony threats"` filter already acts as a "threats" tab in the sidebar.


## 7. Open questions / known gaps

These need follow-up research before locking the data model:

1. The HAE `hostile_activity` market condition has a plugin class
   (likely `<core>\econ\impl\HostileActivity.java` in the obfuscated `starfarer_obf.jar`, not present in `starfarer.api.jar` and not in the decompiled cache snapshot).
   For "how much stability does HAE cost on this colony right now" we'd want the condition's stat mod magnitude.
   Either decompile the obf jar or read the value indirectly via `market.getStability().getFlatMods()` keyed by the condition id.
2. `HegemonyInspectionOutcome` and `PunExOutcome` enums weren't read for every value;
   the report names them by usage.
   If we render outcome strings we should grab the full enum list to make sure no case is missed.
3. `FactionFleetExpedition` was named in the brief but doesn't appear in `starfarer.api.jar`.
   The vanilla equivalent is `PunitiveExpeditionIntel`;
   mods (Nex etc.) may add their own and ideally we'd discover them via `IntelManager.getIntel(IntelInfoPlugin.class)` + duck-typed checks,
   not by hard-naming the class.
4. The exact set of static (non-event) condition penalties we care about for the "threat list" view
   (low stability, low accessibility, ground defenses out, etc.) hasn't been fully catalogued from `Conditions.java` -
   feature #21's design should decide the cutoff
   (everything in the sidebar would duplicate the colony screen).
5. The map overlay implementation surface (system rings, market icons) wasn't researched here;
   that lives in the campaign UI layer
   (`SectorMapAPI`, `IntelUIAPI.showOnMap(...)`) -
   HAE already uses `ui.showOnMap(d.system.getHyperspaceAnchor())`
   (`HostileActivityEventIntel.java:319-:328`) as a precedent.
