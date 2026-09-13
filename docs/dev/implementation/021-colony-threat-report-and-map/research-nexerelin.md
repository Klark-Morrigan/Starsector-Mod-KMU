# Nexerelin Colony-Threat Surface — Research

Research for KMU feature #21
(consolidated "colony threats" intel + sector map overlay).
Source:
decompiled `ExerelinCore.jar` cached at `c:\a_Games\Starsector\.sources-cache\mods\Nexerelin-0.12.1e\jars\ExerelinCore\`.
All `file:line` refs below are relative to that root unless noted.

Nex version used:
**0.12.1e**
(jar path `mods\Nexerelin-0.12.1e\jars\ExerelinCore.jar`).

## Index

1. [TL;DR](#1-tldr)
2. [The "Colony threats" intel tag](#2-the-colony-threats-intel-tag)
3. [Catalog of Nex threat intels](#3-catalog-of-nex-threat-intels)
   - 3.1 [OffensiveFleetIntel hierarchy](#31-offensivefleetintel-hierarchy)
   - 3.2 [InvasionIntel](#32-invasionintel)
   - 3.3 [NexRaidIntel](#33-nexraidintel)
   - 3.4 [BaseStrikeIntel](#34-basestrikeintel)
   - 3.5 [SatBombIntel](#35-satbombintel)
   - 3.6 [BlockadeWrapperIntel / NexBlockadeFGI](#36-blockadewrapperintel--nexblockadefgi)
   - 3.7 [CounterInvasionIntel](#37-counterinvasionintel)
   - 3.8 [ColonyExpeditionIntel](#38-colonyexpeditionintel)
   - 3.9 [GroundBattleIntel](#39-groundbattleintel)
   - 3.10 [RebellionIntel](#310-rebellionintel)
   - 3.11 [VengeanceFleetIntel (player-fleet, not colony)](#311-vengeancefleetintel-player-fleet-not-colony)
   - 3.12 [HegemonyInspectionIntel / MercPackageIntel](#312-hegemonyinspectionintel--mercpackageintel)
4. [Per-colony threat queries](#4-per-colony-threat-queries)
5. [Diplomacy / hostility context](#5-diplomacy--hostility-context)
6. [Rebellion / unrest deep-dive](#6-rebellion--unrest-deep-dive)
7. [Map / intel UI precedents](#7-map--intel-ui-precedents)
8. [Soft-dep posture and reflection risk](#8-soft-dep-posture-and-reflection-risk)
9. [Gaps and open questions](#9-gaps-and-open-questions)

---

## 1. TL;DR

- Nex already tags every threat intel item with the **string literal `"Colony threats"`**
  (`OffensiveFleetIntel.java:489-498`, `RebellionIntel.java:1485-1499`, `GroundBattleIntel.java:2191-2200`, `CounterInvasionIntel.java:104`, `ColonyExpeditionIntel.java:432`, `MercPackageIntel.java:135`, `NexRaidIntel.java:307`).
  The cleanest KMU integration is to (a) filter `Global.getSector().getIntelManager()` by that tag,
  or (b) iterate `OffensiveFleetIntel.class` / `RebellionIntel.class` directly
  and post-filter by target market.
- The common base class is `exerelin.campaign.intel.fleets.OffensiveFleetIntel` (`OffensiveFleetIntel.java:57`),
  which gives us `getTarget()` (MarketAPI),
  `getFaction()` (attacker),
  `getETA()` / `getCurrentStage()` (inherited from vanilla `RaidIntel`),
  `getFP()` / `getBaseFP()` / `getRaidStr()`,
  and `getType()` returning a stable string id
  (`"raid"`, `"satbomb"`, `"defense"`, `"blockade"`, plus subclass-specific overrides).
- `InvasionFleetManager.getManager().getActiveIntelCopy()` (`InvasionFleetManager.java:1177`) returns every live `OffensiveFleetIntel`.
  It is **not** indexed by target market — KMU must loop and filter on `intel.getTarget() == market`.
- `RebellionIntel.getOngoingEvent(MarketAPI)` (`RebellionIntel.java:1565`) is a clean per-market accessor for rebellions.
- `GroundBattleIntel.getOngoing(MarketAPI)` (`GroundBattleIntel.java:2222`) is the per-market accessor for ongoing ground battles.
- `RebellionCreator.getInstance().getRebellionPoints(market)` (`RebellionCreator.java:149`) yields the per-market progress toward the next rebellion (0-100).
  This is the "coming soon" signal for revolt risk.
- `DiplomacyManager.getFactionsAtWarWithFaction(...)` (`DiplomacyManager.java:832-836`) returns hostile factions.
  Pair with `DiplomacyBrain.hasCeasefireWith(...)` (`DiplomacyBrain.java:785`) and `DiplomacyBrain.getCeasefires()` (`DiplomacyBrain.java:875`) for cease-fire context.
- **Vengeance fleets target the player FLEET,
  not a colony**
  (`VengeanceFleetIntel.java:262-289` chases `playerFleet`);
  they are not a per-colony threat and should be surfaced separately in KMU (if at all),
  not under the per-market list.

## 2. The "Colony threats" intel tag

Nex centralizes everything we care about under the **string tag `"Colony threats"`** added via `IntelInfoPlugin.getIntelTags(SectorMapAPI)`.
This is the same tag the vanilla intel screen uses for its filter pane,
so the player can already see this category — KMU's job is to add a **richer per-colony summary view** on top.

Tag is added
(only when target is player-owned/commission) by:

| Intel class                                  | File:line                                     |
|----------------------------------------------|-----------------------------------------------|
| `OffensiveFleetIntel` (base)                 | `OffensiveFleetIntel.java:489-498`            |
| `CounterInvasionIntel`                       | `CounterInvasionIntel.java:104`               |
| `ColonyExpeditionIntel`                      | `ColonyExpeditionIntel.java:432`              |
| `NexRaidIntel`                               | `NexRaidIntel.java:307`                       |
| `GroundBattleIntel`                          | `GroundBattleIntel.java:2191-2200`            |
| `RebellionIntel`                             | `RebellionIntel.java:1485-1499`               |
| `MercPackageIntel` (hostile activity)        | `MercPackageIntel.java:135`                   |

Concrete pattern in `OffensiveFleetIntel.getIntelTags` (`OffensiveFleetIntel.java:489-499`):

```
tags.add("Military");
tags.remove("Colony threats");
if (this.targetFaction.isPlayerFaction() || this.target.isPlayerOwned()) {
    tags.add("Colony threats");
}
```

Implication:
any intel Nex itself considers a colony threat is already tagged.
KMU can scan `Global.getSector().getIntelManager().getIntel(...)` and filter by `intel.getIntelTags(null).contains("Colony threats")` for a broad sweep,
then dispatch per concrete class for richer fields.

## 3. Catalog of Nex threat intels

All offensive-fleet flavours extend `exerelin.campaign.intel.fleets.OffensiveFleetIntel` (`OffensiveFleetIntel.java:57-60`),
which extends vanilla `com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel` and implements `RaidIntel.RaidDelegate` + `StrategicActionDelegate`.
Stage / ETA / progress methods come from the vanilla `RaidIntel` base;
Nex adds Nex-specific getters on top.

### 3.1 OffensiveFleetIntel hierarchy

Class:
`exerelin.campaign.intel.fleets.OffensiveFleetIntel` (`OffensiveFleetIntel.java:57`).

Key fields and accessors
(KMU should bind to these — they are the stable contract shared by every offensive flavour):

| Field / method                                | Line                          | Notes |
|-----------------------------------------------|-------------------------------|-------|
| `protected MarketAPI from`                    | `:71`                         | source colony |
| `protected MarketAPI target`                  | `:72`                         | colony under threat |
| `protected FactionAPI targetFaction`          | `:73`                         | snapshot at spawn (does not follow market re-ownership) |
| `protected FactionAPI proxyForFaction`        | `:74`                         | true sponsor when attacker is a proxy |
| `protected OffensiveOutcome outcome`          | `:75`                         | null while live; see enum at `:1034` |
| `protected float fp` / `baseFP`               | `:84-85`                      | adjusted / raw fleet points |
| `protected ActionStage action`                | `:97`                         | current main action; route data lives here |
| `getTarget()` -> MarketAPI                    | `:238`                        |  |
| `getMarketFrom()` -> MarketAPI                | `:234`                        |  |
| `getTargetFaction()` -> FactionAPI            | `:952`                        |  |
| `getProxyForFaction()` -> FactionAPI          | `:956`                        | for "proxy" raids (e.g. Diktat HA) |
| `getFP()` / `getBaseFP()`                     | `:226-232`                    | strength |
| `getRaidStr()`                                | `:609`                        | effective ground / raid strength |
| `getRaidFPAdjusted()`                         | `:588`                        |  |
| `getOutcome()` -> OffensiveOutcome            | `:222`                        | null = live |
| `getType()` -> String                         | abstract `:242`               | `"raid"`, `"satbomb"`, `"defense"`, `"blockade"`, plus invasion override returning `"invasion"` (`InvasionIntel.java:564`); see also `BlockadeWrapperIntel.java:178` |
| `getEventType()` -> `InvasionFleetManager.EventType` | `:914`                | enum: INVASION, RAID, RESPAWN, BASE_STRIKE, SAT_BOMB, BLOCKADE, DEFENSE, OTHER (`InvasionFleetManager.java:1276-1284`) |
| `getETA()` (from vanilla `RaidIntel`)         | inherited; used `:389`        | days until current stage completes |
| `getCurrentStage()` (vanilla `RaidIntel`)     | inherited; used `:253,415`    | int stage index; stages list is `this.stages` |
| `getStrategicActionDaysRemaining()`           | `:919-921`                    | returns `getETA()` |
| `OffensiveOutcome` enum                       | `:1034-1052`                  | SUCCESS, FAIL, NO_LONGER_HOSTILE, MARKET_NO_LONGER_EXISTS, RETREAT_BEFORE_ACTION, NOT_ENOUGH_REACHED, TASK_FORCE_DEFEATED, OTHER |
| `BUTTON_AUTO_DEF_FLEET` constant              | `:64`                         | UI hook for "request defense" |

Stage list
(built in subclass `init()` — see e.g. `InvasionIntel.java:111-130`):
typically `NexOrganizeStage -> InvAssembleStage/NexRaidAssembleStage -> NexTravelStage -> action stage (InvActionStage / SatBombActionStage / BaseStrikeActionStage / NexRaidActionStage) -> WaitStage/NexReturnStage`.
KMU treats `getCurrentStage()` as the "how close is it" indicator
(0 = organizing, last = returning / done) and `getETA()` as the day count.

Advance / lifecycle:
`OffensiveFleetIntel.advanceImpl(float)` (`:578-581`) calls `checkForTermination()` then delegates to `RaidIntel.advanceImpl`.
Updates happen via the standard intel tick — KMU does not need a separate listener;
reading these fields on demand from a KMU intel/script is enough.

### 3.2 InvasionIntel

Class:
`exerelin.campaign.intel.invasion.InvasionIntel` (`InvasionIntel.java:65-68`);
extends `OffensiveFleetIntel`,
implements `GroundBattleCampaignListener`.

Type id:
`"invasion"` (`InvasionIntel.java:564`).

Extra fields KMU may want:

- `marinesTotal` (`InvasionIntel.java:80`) — total marine count budgeted.
- `groundBattle` (`InvasionIntel.java:83`) — the `GroundBattleIntel` once the invasion has landed.
- `isPlayerTargeted()`
  (`InvActionStage.java:403`, called from `InvasionIntel.java:146, 607`) — true when target faction is player / commission.

ETA + stage signals come from the parent.
The action stage is `InvActionStage` (`InvasionIntel.java:126`).
When it lands it spawns a `GroundBattleIntel`,
which then becomes the live "we are being invaded" intel for the duration of the ground battle.

### 3.3 NexRaidIntel

Class:
`exerelin.campaign.intel.raid.NexRaidIntel` (`NexRaidIntel.java`);
extends `OffensiveFleetIntel`.
Type id:
`"raid"` (`NexRaidIntel.java:197`).
Adds the `"Colony threats"` tag inline (`NexRaidIntel.java:307`).
Action stage:
`exerelin.campaign.intel.raid.NexRaidActionStage`.
The raid hits a specific market and disrupts industries.

### 3.4 BaseStrikeIntel

Class:
`exerelin.campaign.intel.raid.BaseStrikeIntel` (`BaseStrikeIntel.java:34-37`);
extends `NexRaidIntel`.
Action stage is `BaseStrikeActionStage`.
Targets pirate / Path bases — only relevant to player colonies if the player owns a pirate-flavoured market,
but KMU should still include it under the "outgoing/incoming" filter so it shows up for `targetFaction.isPlayerFaction()`.

### 3.5 SatBombIntel

Class:
`exerelin.campaign.intel.satbomb.SatBombIntel` (`SatBombIntel.java:42-43`);
extends `OffensiveFleetIntel`.
Type id:
`"satbomb"` (`SatBombIntel.java:115`).
Marks itself "important if targeting player" (`SatBombIntel.java:106-108`).
KMU should render this at the **highest severity** in the per-colony summary — it is the only threat that can destroy the colony outright.

Variant flag:
`isVicVirusBomb()` (`SatBombIntel.java:110`).

### 3.6 BlockadeWrapperIntel / NexBlockadeFGI

Class:
`exerelin.campaign.intel.fleets.BlockadeWrapperIntel` (`BlockadeWrapperIntel.java`);
type id `"blockade"` (`BlockadeWrapperIntel.java:178`).
Wraps a `NexBlockadeFGI` (`BlockadeWrapperIntel.java:185`);
the wrapper extends `OffensiveFleetIntel` but delegates display to the FGI.
The underlying FGI is a `FleetGroupIntel` from vanilla.
KMU can still treat the wrapper as an `OffensiveFleetIntel`-shaped row
(it has `getTarget()`, `getETA()`, etc.).

### 3.7 CounterInvasionIntel

Class:
`exerelin.campaign.intel.invasion.CounterInvasionIntel` (`CounterInvasionIntel.java:20-30`);
extends `InvasionIntel`.
Spawned by `GroundBattleIntel` when a faction tries to retake a market it just lost in a ground battle
(constructor takes the trigger `GroundBattleIntel`).
Adds the `"Colony threats"` tag (`CounterInvasionIntel.java:104`).
`setAbortIfNonHostile(false)` (`:29`) — won't auto-cancel on peace,
worth highlighting in KMU's "stickiness" indicator.

### 3.8 ColonyExpeditionIntel

Class:
`exerelin.campaign.intel.colony.ColonyExpeditionIntel` (`ColonyExpeditionIntel.java:69-77`);
extends `OffensiveFleetIntel`.
This is an NPC faction **planting a new colony** on an unsettled planet.
It is a per-colony threat only in the sense that it can claim a system the player is interested in — KMU may want to flag it for the **target planet's system**
rather than per existing market.

Key fields:
`planet` (`:78`),
`originalName` / `newName`,
`colonyOutcome` (`:81`),
`hostileMode` (`:82`).
`getTarget()` (`:93`) returns `this.planet.getMarket()`,
which is `null` until the colony lands — **guard for null** when querying `getTarget()` on this subclass.

### 3.9 GroundBattleIntel

Class:
`exerelin.campaign.intel.groundbattle.GroundBattleIntel` (`GroundBattleIntel.java`).
Not an `OffensiveFleetIntel` — extends vanilla `BaseIntelPlugin`.
Created when an invasion successfully reaches its target and starts ground combat.
While live,
this is the dominant "colony threats" entry for that market;
the parent `InvasionIntel` runs in parallel.

Per-market accessor:
`getOngoing(MarketAPI)` (`GroundBattleIntel.java:2222-2229`).
Static list of all live battles:
`getOngoing()` (`:2231-2239`).

Fields KMU needs:
`getMarket()` (`:349`),
`getOutcome()` (`BattleOutcome` enum),
`getSide(boolean attacker)`,
plus `getTurnsSinceLastAction()` (`:2248`) for "stalled" indicators.

### 3.10 RebellionIntel

Class:
`exerelin.campaign.intel.rebellion.RebellionIntel` (`RebellionIntel.java:74-77`);
extends `BaseIntelPlugin` (not `OffensiveFleetIntel`).
Implements `InvasionListener`,
`FleetEventListener`.
See section 6 for full lifecycle.

### 3.11 VengeanceFleetIntel (player-fleet, not colony)

Class:
`exerelin.campaign.intel.fleets.VengeanceFleetIntel` (`VengeanceFleetIntel.java:51-52`);
extends `BaseIntelPlugin`.
**Hunts the player fleet** (`VengeanceFleetIntel.java:262-289`);
the constructor's `market` arg is the **launch market**,
not a threat target.
Does **not** add `"Colony threats"` to its tag set (`VengeanceFleetIntel.java:236-241`).
KMU should keep this out of the per-colony list.
The `RevengeanceManager` (`RevengeanceManager.java`) controls escalation;
player escalation level per faction is `RevengeanceManager.getManager().getVengeanceEscalation(factionId)` (`:190`).

### 3.12 HegemonyInspectionIntel / MercPackageIntel

`HegemonyInspectionIntel` is vanilla,
but Nex wraps it in `DiplomacyBrain.shouldOffensiveBlockCeasefire` etc. — treat it as a known "colony threat" if KMU's mod set includes the Hegemony AI-core inspection plot (vanilla).
`MercPackageIntel` (`MercPackageIntel.java:135`) is a Nex-driven hostile-activity merc package;
tags as Colony threats.
Both can be reached via `Global.getSector().getIntelManager().getIntel(...)` and class filter.

## 4. Per-colony threat queries

There is **no `OffensiveFleetManager.getThreatsForMarket(MarketAPI)`** — Nex never indexed by target market.
The supported patterns are:

**Pattern A — by IntelManager class scan (preferred for KMU).** The intel manager already keeps these lists;
just iterate and post-filter.

```
List<OffensiveFleetIntel> threats = new ArrayList<>();
for (IntelInfoPlugin ii : Global.getSector().getIntelManager()
                                  .getIntel(OffensiveFleetIntel.class)) {
    OffensiveFleetIntel ofi = (OffensiveFleetIntel) ii;
    if (ofi.isEnded() || ofi.isEnding()) continue;
    if (ofi.getOutcome() != null) continue;
    if (ofi.getTarget() != market) continue;
    threats.add(ofi);
}
```

Used in canon form at:
- `CovertOpsManager.java:512-531` (filter offences by target faction)
- `DiplomacyBrain.java:470-488`
  (gather every live offensive to check ceasefire safety)
- `SpecialForcesRouteAI.java:90`
- `RespawnInvasionIntel.java:54`

**Pattern B — InvasionFleetManager registry.** `InvasionFleetManager.getManager().getActiveIntelCopy()` (`InvasionFleetManager.java:1177-1179`) returns a `List<OffensiveFleetIntel>` that mirrors the intel-manager list.
Marginally faster (no class scan),
but exposes the same data.
Internal field `protected final List<OffensiveFleetIntel> activeIntel` (`InvasionFleetManager.java:132`).
`MANAGER_MAP_KEY` is `"exerelin_invasionFleetManager"` (`:85`).

**Pattern C — per-class static accessors (cleanest where they exist).**
- `RebellionIntel.getOngoingEvent(MarketAPI)` (`RebellionIntel.java:1565-1572`)
- `RebellionIntel.isOngoing(MarketAPI)` (`:1574-1576`)
- `GroundBattleIntel.getOngoing(MarketAPI)` (`:2222-2229`)
- `GroundBattleIntel.getOngoing()` (`:2231-2239`)

**Pattern D — intel tags.** Filter `Global.getSector().getIntelManager().getIntel()` by `intel.getIntelTags(null).contains("Colony threats")`.
This is the most forward-compatible
(any future Nex threat class that adds the tag is auto-included) but loses per-class fields
unless KMU dispatches on `instanceof` afterwards.

Recommended for KMU:
**Pattern A scoped to `OffensiveFleetIntel.class`** for offensive fleets,
**Pattern C** for rebellion / ground battle,
and **Pattern D** as a safety net to catch new Nex threat classes we did not know about at compile time.

## 5. Diplomacy / hostility context

The threat list isn't useful without "who *could* attack me".
Nex exposes this through `DiplomacyManager` (static helpers) and `DiplomacyBrain` (per-faction AI state).

Key static accessors on `exerelin.campaign.DiplomacyManager`:

- `getManager()` (`DiplomacyManager.java:810`) — singleton.
- `getFactionsAtWarWithFaction(String, boolean includePirates, boolean includeTemplars, boolean mustAllowCeasefire)` (`DiplomacyManager.java:832-834`) — hostile factions.
- `getFactionsAtWarWithFaction(FactionAPI, ...)` (`DiplomacyManager.java:836-838`) — same,
  FactionAPI overload.
- `getFactionsOfAtBestRepWithFaction(FactionAPI, RepLevel, ...)` (`DiplomacyManager.java:840-...`) — generalised hostility filter.
- `getWarWeariness(String factionId)` / `getWarWeariness(String, boolean useEnemyCountModifier)` (`DiplomacyManager.java:876-880`) — higher weariness => less likely to press an offensive;
  useful as a "threat will probably fizzle" indicator.

Per-faction `DiplomacyBrain` (the AI deciding whether to attack):

- `DiplomacyManager.getManager().getDiplomacyBrain(String factionId)`
  (used at `InterventionAction.java:60`, `DiplomacyManager.java:439`).
- `DiplomacyBrain.hasCeasefireWith(String otherFactionId)` (`DiplomacyBrain.java:785-787`) — pending cease-fire blocks new offensives.
- `DiplomacyBrain.getCeasefires()` -> `Map<String, Float>` (`DiplomacyBrain.java:875-877`) — values are days remaining on each cease-fire (initial value 150f, `:782`).
- `DiplomacyBrain.getRecentWars()` -> `Map<String, Float>` (`DiplomacyBrain.java:879-881`).
- `DiplomacyBrain.getOurStrength()` / `getEnemyStrength()` (`DiplomacyBrain.java:883-889`) — cached.
- Static helpers `DiplomacyBrain.getFactionStrength(String)` (`:840-855`) and `getFactionEnemyStrength(String)` (`:857-867`) — size-weighted,
  cheap to call ad hoc.

For a "this faction is hostile **and** has the means to project force at my colonies" signal:
combine `DiplomacyManager.getFactionsAtWarWithFaction(playerFactionId, ...)` with `DiplomacyBrain.getFactionStrength(enemyId)` and a check that **no** `DiplomacyBrain.hasCeasefireWith(playerFactionId)` for the enemy.

## 6. Rebellion / unrest deep-dive

Class:
`RebellionIntel` (`RebellionIntel.java:74`).
Creator/scheduler:
`RebellionCreator`
(`RebellionCreator.java:29-30`, implements `EveryFrameScript`).

### Lifecycle

1. **Accumulation.** `RebellionCreator.advance` (`:194-206`) walks every market
   once a sector day (interval 1.0 day, `:39`) and calls `processMarket(market, days)` (`:170-192`).
2. **Per-market increment.** `getRebellionIncrement(market)` (`:122-147`) computes points/day from `5 - effectiveStability`
   (effectiveStability is `market.getStabilityValue()`, adjusted by `dissident` condition `:130-132`, original-owner status `:133`, and hard-mode penalty `:134-136`).
   Scaled by `0.2 * NexConfig.rebellionMult`.
3. **Points persisted on the market.** Memory key `"$nex_rebellionPoints"` (`:32`),
   readable via `RebellionCreator.getInstance().getRebellionPoints(market)` (`:149-152`).
   Range:
   0..100.
   **This is the per-colony "rebellion risk" gauge KMU should surface.**
4. **Trigger.** At `>= 100` points,
   `RebellionCreator.createRebellion(market, false)` (`:163-165`) is called and points reset to 0.
5. **Faction selection.** `createRebellion(market, boolean instant)` (`:77-120`) picks a hostile faction
   (uses `DiplomacyManager.getFactionsOfAtBestRepWithFaction(..., RepLevel.INHOSPITABLE, allowPirates, false, false)`),
   with weighting for vengeful relations (+2),
   independents (+2),
   original owner (+10),
   luddic majority (+5 path / +3 church).
6. **`RebellionIntel.init`** (`RebellionIntel.java:157-200`) adds the intel,
   registers itself as a sector listener,
   applies a stability condition
   (`market.addCondition("nex_rebellion_condition")`, `:192`),
   and fires the `UpdateParam.PREP` update.
7. **Resolution.** `RebellionResult` enum
   (REBEL_VICTORY, GOVERNMENT_VICTORY, ...) controls success.
   While running,
   key fields:
   - `getGovtStrength()` / `getRebelStrength()` (`RebellionIntel.java:258-265`)
   - `getGovtFaction()` / `getRebelFaction()` / `getLiberatorFaction()` (`:1586-1597`)
   - `stabilityPenalty` (`:127`) — applied to colony stability while active;
     up to `MAX_STABILITY_PENALTY = 5` (`:95`).
   - Max duration `MAX_DAYS = 730.0f` (`:82`).

### Accessors KMU needs

- `RebellionIntel.getOngoingEvent(MarketAPI)` (`:1565`)
- `RebellionIntel.isOngoing(MarketAPI)` (`:1574`)
- `RebellionCreator.getInstance()` (`:49-51`)
- `RebellionCreator.getInstance().getRebellionPoints(market)` (`:149`)
- `Global.getSector().getIntelManager().getIntel(RebellionIntel.class)` for a global scan
  (used canonically at `RebellionIntel.java:1558-1561` and `SectorManager.java:1062`).

### Pre-rebellion signal

KMU's UI should distinguish:
- **No risk:** `getRebellionPoints == 0` AND `getRebellionIncrement(market) <= 0`.
- **Building:** `0 < getRebellionPoints < 100`.
  Surface the absolute value and the per-day rate so the player can see ETA.
  Rate per day is `getRebellionIncrement(market)` (`:122`) — note it ignores the `numOngoing` cap that `processMarket` applies (`:184-187`),
  so KMU may want to apply that mult itself when showing ETA.
- **Active:** `RebellionIntel.isOngoing(market)`.
  Show `govtStrength`/`rebelStrength`,
  `stabilityPenalty`,
  days-active.

## 7. Map / intel UI precedents

KMU feature #21 wants left-side colony list + right-side per-colony detail.
Nex precedents:

### 7.1 IntelInfoPlugin large-description pattern

The richest Nex intel screens use `createLargeDescription(CustomPanelAPI panel, float width, float height)`.

- `StrategicAI.createLargeDescription`
  (`exerelin/campaign/ai/StrategicAI.java:273-283`) — header in TL,
  scrollable tooltip-maker holding the report.
  Combined with `hasLargeDescription() == true` (`:343`) and `hasSmallDescription() == false` (`:339`).
- `GroundBattleIntel.createLargeDescription` (`GroundBattleIntel.java:2094-2128`) — section heading + multi-mode inner display (UNITS, ABILITIES, INFO, LOG) switched by `this.viewMode`.
  **This is the closest existing Nex precedent** to the KMU "list on the left,
  detail on the right" UX:
  a single `CustomPanelAPI` with mode-driven inner panels.

### 7.2 RuleBased two-pane (faction directory) — not what we want

`Nex_FactionDirectory` (`Nex_FactionDirectory.java:160-180`) and the deprecated `FactionDirectoryDialog` (`FieldOptionsScreenScript.java:58+`) both use an *interaction dialog* (`Nex_VisualCustomPanel.createPanel`) rather than an intel page.
The interaction-dialog substrate is fine if KMU wants a "hotkey opens screen anywhere" experience,
but it sits **on top of** the campaign view and is not what the vanilla intel screen does.

Recommendation for KMU:
implement the consolidated view as an `IntelInfoPlugin` with `hasLargeDescription() == true`,
mirror the `GroundBattleIntel` view-mode pattern (mode enum + dispatcher),
and put the colony list in a left-side `CustomPanelAPI` element using `TooltipMakerAPI.beginImageWithText` rows.
Per-row click sets the selected colony;
right side rebuilds via the same `createLargeDescription` call.

### 7.3 Map overlay precedents

For "highlight threatened systems on the sector map",
Nex precedents are arrow data on intel items:

- `RebellionIntel.getArrowData(SectorMapAPI)` (`RebellionIntel.java:1509-1523`) — draws fleet-source-to-market arrows.
- `OffensiveFleetIntel` inherits vanilla `RaidIntel.getArrowData`,
  which draws the raiding faction's source-to-target arrow automatically.
- `getMapLocation(SectorMapAPI)`
  (`OffensiveFleetIntel` inherits vanilla; `VengeanceFleetIntel.java:255-260` example; `RebellionIntel.java:1505-1507`; `GroundBattleIntel.java:2153`) is how each intel pins itself on the map.

For KMU's "system-level threat overlay",
aggregate per-system by walking all `Colony threats`-tagged intels
and grouping by `intel.getTarget().getStarSystem()` (offensive) / `intel.getMarket().getStarSystem()` (rebellion / ground battle).

## 8. Soft-dep posture and reflection risk

### 8.1 Mod-presence check

Canonical:
`Global.getSettings().getModManager().isModEnabled("nexerelin")`.
Constant:
`exerelin.ExerelinConstants.MOD_ID = "nexerelin"` (`ExerelinConstants.java:7`).
The `Nex_IsModActive` rulecmd (`Nex_IsModActive.java:18`) uses the same call.

### 8.2 Class binding strategy

KMU should keep all Nex symbols behind a single adapter (e.g. `KmuNexThreatAdapter`)
and gate every method on the mod-presence flag.
The adapter is loaded lazily so a player without Nex never triggers a `ClassNotFoundException`.

### 8.3 Per-class stability read

| Symbol                                                | Stability | Notes |
|-------------------------------------------------------|-----------|-------|
| `exerelin.ExerelinConstants.MOD_ID`                    | **stable** | trivial constant, unchanged since Nex 0.9.x |
| `exerelin.campaign.intel.fleets.OffensiveFleetIntel`   | **stable** | base class, large public API surface, widely depended on |
| `OffensiveFleetIntel.getTarget()` / `getFaction()` /  `getETA()` / `getFP()` / `getOutcome()` / `getType()` / `getEventType()` | **stable** | core public contract; renaming would break Nex itself |
| `OffensiveFleetIntel.OffensiveOutcome` enum            | **stable** | persisted enum; renames are save-breaking |
| `exerelin.campaign.fleets.InvasionFleetManager.getManager()` / `getActiveIntelCopy()` / `EventType` | **stable** | persistent singleton, save-key `"exerelin_invasionFleetManager"` |
| `InvasionIntel` / `NexRaidIntel` / `SatBombIntel` / `BaseStrikeIntel` / `BlockadeWrapperIntel` / `CounterInvasionIntel` / `ColonyExpeditionIntel` | **mostly stable** | concrete class names are user-facing in save XML; type-id strings (`"raid"`, `"satbomb"`, `"invasion"`, `"defense"`, `"blockade"`) are also save-stable |
| `RebellionIntel.getOngoingEvent(MarketAPI)` / `isOngoing(MarketAPI)` / `getGovtFaction()` / `getRebelFaction()` / `getGovtStrength()` / `getRebelStrength()` | **stable** | used by Nex's own subsystems; safe to bind |
| `RebellionCreator.getInstance()` / `getRebellionPoints(MarketAPI)` | **stable** | persistent under `"nex_rebellionCreator"` (`RebellionCreator.java:33`) |
| `GroundBattleIntel.getOngoing(MarketAPI)` / `getMarket()` / `getOutcome()` | **stable** | extensively used |
| `DiplomacyManager.getManager()` / `getFactionsAtWarWithFaction(...)` / `getWarWeariness(...)` | **stable** | top-level public surface |
| `DiplomacyBrain.hasCeasefireWith(...)` / `getCeasefires()` / `getRecentWars()` | **moderately stable** | newer-looking API (`recentWars` had a `readResolve` guard `:826`); fine for current Nex but worth guarding with reflection-style `try/catch` if KMU wants to span older Nex versions |
| `VengeanceFleetIntel` / `RevengeanceManager` | **moderately stable** | not a colony threat — only bind if KMU expands scope |
| `MercPackageIntel`, `NexHostileActivityManager`, `NexHostileActivityEventIntel` | **less stable** | hostile-activity subsystem has churned recently; if KMU includes these, do it via the `"Colony threats"` tag scan + `instanceof` so unknown subclasses just degrade to "generic threat" |
| Anything under `exerelin.campaign.intel.groundbattle.plugins.*`, `exerelin.campaign.ai.*`, `exerelin.campaign.intel.hostileactivity.*` | **internal** | do not bind directly |

### 8.4 Recommended adapter shape

```
interface KmuNexThreatAdapter {
    boolean isAvailable();                                // wraps isModEnabled
    List<NexThreat> getThreatsTargeting(MarketAPI market); // wraps Pattern A
    Optional<RebellionStatus> getRebellionStatus(MarketAPI market);
    float getRebellionRiskPoints(MarketAPI market);       // 0..100
    Set<FactionAPI> getHostileFactionsThreatening(FactionAPI owner);
    Optional<CeasefireInfo> getCeasefire(FactionAPI a, FactionAPI b);
}
```

`NexThreat`,
`RebellionStatus`,
`CeasefireInfo` are KMU-owned value types that copy the fields KMU needs out of Nex's mutable objects.
**Never store live `OffensiveFleetIntel` references in KMU's persistent state** — Nex would then be a hard save-dep.

## 9. Gaps and open questions

1. **No per-market index in Nex.** Confirmed:
   `OffensiveFleetManager` has no `getThreatsFor(MarketAPI)`.
   KMU has to scan.
   At sector scale the lists are small (tens of items),
   so this is fine;
   if perf matters,
   cache per frame on the KMU side.
2. **`ColonyExpeditionIntel.getTarget()` can return `null`** until the colony lands (`ColonyExpeditionIntel.java:93-95`).
   KMU must null-guard or special-case this subclass on its target *planet*.
3. **Vengeance fleets** chase the player fleet,
   not a colony.
   Decision for KMU UX:
   surface separately under a "personal" or "fleet" category,
   or omit.
   Not part of the per-colony view.
4. **`getETA()` is on the vanilla `RaidIntel` base.** KMU can call it on `OffensiveFleetIntel` directly without going through Nex-specific API,
   which keeps the soft-dep adapter thinner.
   Same for `getCurrentStage()`.
   Both should be wrapped in try/catch in case a subclass overrides oddly
   (e.g. `BlockadeWrapperIntel` delegates much to its wrapped FGI).
5. **`HegemonyInspectionIntel`** is vanilla.
   KMU should decide whether to surface it under the same UI even when Nex is absent.
   The no-Nex path would scan vanilla intel only;
   the with-Nex path adds Nex flavours via the adapter.
6. **Hostile-activity event factors**
   (Diktat HA, League HA, Luddic HA, etc., under `exerelin.campaign.intel.hostileactivity.*`) wrap vanilla `BaseHostileActivityCause` and produce **fleets**
   rather than full intel items.
   The fleets show up as `NexHostileActivityEventIntel` which extends vanilla `HostileActivityEventIntel`.
   KMU may want to show "background pressure" here,
   but it's strictly informational — not a per-colony threat.
   Recommend:
   defer to a later iteration.
7. **Rebellion ETA.** `getRebellionIncrement(market)` is the per-day gain
   but is *not* the same as the gain `processMarket` actually applies — the latter scales by `(1 - numOngoing/MAX_ONGOING)` only when `points > 0` (`RebellionCreator.java:184-187`).
   KMU should replicate that calculation to give an accurate ETA,
   **not** call the raw increment.
8. **Save compatibility.** Adapter must tolerate older Nex versions that lack `DiplomacyBrain.recentWars`
   (Nex itself fills it in `readResolve`, `:826`).
   For KMU,
   only bind to fields that pre-date Nex 0.11.x to be safe;
   treat newer fields as optional.

---

**Conclusion for design.** The cleanest KMU integration is:

1. Adapter calls `Global.getSettings().getModManager().isModEnabled("nexerelin")`.
2. If enabled,
   iterate `IntelManager.getIntel(OffensiveFleetIntel.class)` + `IntelManager.getIntel(GroundBattleIntel.class)` + `RebellionIntel`,
   filter by target market,
   project into KMU value types.
3. Additionally pull rebellion-risk points
   and hostile-faction list as "anticipated" threats (no intel item yet).
4. UI is a KMU-owned `IntelInfoPlugin` with `hasLargeDescription` modelled on `GroundBattleIntel.createLargeDescription`:
   left-side colony list,
   right-side per-colony per-threat detail.
