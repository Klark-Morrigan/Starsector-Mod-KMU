# Third-party mod colony threat surface - research notes

Companion to `research-vanilla.md`. Same `file:line` convention: paths are
under `c:\a_Games\Starsector\.sources-cache\` unless otherwise noted. All
quoted source is the CFR-decompiled `.java` view of the relevant mod JAR; no
`.class`/bytecode fallback was needed for the mods that matter to feature
#21.

The currently-installed mod list was scanned against the threat-shaped
vanilla base classes (`HostileActivityFactor`, `BaseHostileActivityFactor`,
`OffensiveFleetIntel`, `GenericRaidFGI`, `RaidIntel`, `FleetGroupIntel`,
`BaseEventIntel`) plus the looser `BaseIntelPlugin`. The matrix in section 2
summarises which mods contribute a colony-threat surface KMU can enumerate
and which are dead ends despite suggestive naming.

## Index

1. [Executive summary](#1-executive-summary)
2. [Cross-mod base-class matrix](#2-cross-mod-base-class-matrix)
3. [Common patterns and enumeration seams](#3-common-patterns-and-enumeration-seams)
4. [Per-mod sections](#4-per-mod-sections)
   - 4.1 [Tahlan Shipworks - Legio siege](#41-tahlan-shipworks)
   - 4.2 [Domain Phase Lab - mercenary attack](#42-domain-phase-lab)
   - 4.3 [Secrets of the Frontier - Dustkeeper Contingency](#43-secrets-of-the-frontier)
   - 4.4 [Industrial.Evolution - Privateer raids and warning beacon](#44-industrialevolution)
   - 4.5 [MagicLib - BountyBoardIntelPlugin (UI precedent only)](#45-magiclib)
   - 4.6 [stelnet - filter / class enumeration precedent](#46-stelnet)
   - 4.7 [Captain's Log - logger, not aggregator](#47-captains-log)
5. [Mods checked with nothing relevant](#5-mods-checked-with-nothing-relevant)
6. [KMU implications](#6-kmu-implications)


## 1. Executive summary

Only two installed mods plug into vanilla's `HostileActivityEventIntel`
threat-factor pipeline:

- **Domain Phase Lab** -> `dpl_HostileActivityFactor`
  (`BaseHostileActivityFactor`) + `dpl_MercenaryAttack` (`GenericRaidFGI`).
- **Secrets of the Frontier** -> `SotfDustkeeperHAFactor`
  (`BaseHostileActivityFactor`).

A third, **Tahlan Shipworks**, runs an independent `RaidIntel`-shaped siege
pipeline (`LegioSiegeMissionIntel` / `LegioSiegeManager`) that *can* target
the player but only does so as a side-effect of "any system hostile to the
Legio". It does not register a `HostileActivityFactor`.

**Industrial.Evolution** has one player-relevant raid intel
(`PrivateerBaseRaidIntel extends RaidIntel`) spawned by the vanilla Privateer
Base industry. It targets any market hostile to the base's owner, so again
the player only inherits it transitively.

The remaining mods in the candidate list - EmergentThreats IX/Vice, Knights
of Ludd, Hostile Intercept, Random Assortment of Things, Domain Explorarium
Expansion, Hiver Swarm, Star Federation, Grand Colonies - either ship no
campaign intel at all or ship intel that is mission/quest/UI-only, despite
their suggestive naming. Details in section 5.

MagicLib's `BountyBoardIntelPlugin` and stelnet's filter system are
**UI/structure references only**; neither is a threat source. They are worth
reading because they demonstrate the "provider list + selection list + detail
pane" layout KMU will want, and because stelnet's class-and-tag intel filter
model is exactly the soft-dep-tolerant enumeration shape KMU needs.

The cleanest enumeration seam is therefore:

```java
HostileActivityEventIntel hae = HostileActivityEventIntel.get();
if (hae != null) {
    for (EventFactor f : hae.getFactors()) {
        if (f instanceof HostileActivityFactor haf) { ... }
    }
}
```

Anything not on that list (Tahlan Legio, IndEvo Privateer, the few
`OffensiveFleetIntel`/`RaidIntel` instances in mods that don't subclass
`HostileActivityFactor`) has to be picked up by walking
`Global.getSector().getIntelManager().getIntel(...)` and runtime-filtering
by base class. See section 3.


## 2. Cross-mod base-class matrix

| Mod (installed)               | `HostileActivityFactor` impl                   | `GenericRaidFGI` subclass            | `RaidIntel` subclass                                                    | Other threat intel             |
|-------------------------------|------------------------------------------------|--------------------------------------|--------------------------------------------------------------------------|---------------------------------|
| Tahlan Shipworks              | -                                              | -                                    | `LegioSiegeMissionIntel`                                                  | -                               |
| Domain Phase Lab              | `dpl_HostileActivityFactor`                    | `dpl_MercenaryAttack`                | -                                                                        | -                               |
| Secrets of the Frontier       | `SotfDustkeeperHAFactor`                       | -                                    | -                                                                        | quest intel (out of scope)      |
| Industrial.Evolution (IndEvo) | -                                              | -                                    | `PrivateerBaseRaidIntel` (vanilla industry-spawned, not player-targeted) | meteor / shipping / yards intel |
| EmergentThreats IX Revival    | - (source-only mod, no intel classes)          | -                                    | -                                                                        | -                               |
| EmergentThreats Vice          | - (source-only mod, no intel classes)          | -                                    | -                                                                        | -                               |
| Knights of Ludd               | -                                              | -                                    | -                                                                        | Zea\* quest/lore intel          |
| Hostile Intercept             | -                                              | -                                    | -                                                                        | UI rings + autopause only       |
| Random Assortment of Things   | -                                              | -                                    | -                                                                        | mission intel, no colony threats|
| Domain Explorarium Expansion  | -                                              | -                                    | -                                                                        | no intel                        |
| Hiver Swarm                   | -                                              | -                                    | -                                                                        | no intel (world gen + hullmods) |
| The Star Federation           | -                                              | -                                    | -                                                                        | no intel (ship pack)            |
| Grand Colonies                | -                                              | -                                    | -                                                                        | no intel (UI / industry panel)  |
| Captain's Log                 | -                                              | -                                    | -                                                                        | logger, not aggregator          |
| stelnet                       | -                                              | -                                    | -                                                                        | -                               |
| MagicLib                      | -                                              | -                                    | -                                                                        | `BountyBoardIntelPlugin` (UI)   |

"Other threat intel" is the catch-all for things that don't sit on a
threat base class but still might surface in a colony-threat view (Tahlan
Legio is the only entry that arguably belongs there in addition to its
`RaidIntel` row).


## 3. Common patterns and enumeration seams

### 3.1 Pattern A - `HostileActivityFactor` plug-in (preferred)

Both Domain Phase Lab and Secrets of the Frontier use the same shape:

1. Subclass `BaseHostileActivityFactor`.
2. Construct themselves in their mod-plugin code and pass the current
   `HostileActivityEventIntel` to the super constructor.
3. Register a campaign listener (so they get progress ticks).
4. The factor is *added to the vanilla aggregator* via
   `HostileActivityEventIntel.addFactor(factor)` -
   `<core>\intel\events\HostileActivityEventIntel.java:870-878` (mods do
   not call this directly in practice; vanilla's `setup()` /
   `notifyEnding()` cycle plus `Global.getSector().getListenerManager()`
   wiring picks them up).

For the KMU report this is the gold-standard seam: enumerate
`HostileActivityEventIntel.get().getFactors()` and filter by
`HostileActivityFactor`. The interface
(`<core>\intel\events\HostileActivityFactor.java:15-:54`) already exposes
everything KMU needs per-factor:

- `getId()`
- `getNameForThreatList(boolean first)` and `getNameColorForThreatList()`
- `getEffectMagnitude(StarSystemAPI)` -> per-system intensity
- `getMaxNumFleets(StarSystemAPI)`, `getSpawnFrequency(StarSystemAPI)`
- `getEventFrequency(HostileActivityEventIntel, BaseEventIntel.EventStageData)`
  -> ETA proxy
- `addStageDescriptionForEvent(...)` -> tooltip surface

Vanilla already renders this exact "per-system danger + top three threat
names" table in
`<core>\intel\events\HostileActivityEventIntel.java:240-278`. KMU's value
is *re-layout and map overlay*, not "expose what nobody else does".

### 3.2 Pattern B - `GenericRaidFGI` / `RaidIntel` spawned by a mod

Domain Phase Lab (`dpl_MercenaryAttack`,
`.sources-cache\mods\Domain Phase Lab-1.7.2\jars\dpl_phase_lab\data\scripts\crisis\dpl_MercenaryAttack.java:15`),
Tahlan (`LegioSiegeMissionIntel`,
`.sources-cache\mods\tahlan\jars\TahlanShipworks\org\niatahl\tahlan\campaign\siege\LegioSiegeMissionIntel.java:41`)
and IndEvo (`PrivateerBaseRaidIntel`,
`.sources-cache\mods\IndEvo\jars\IndEvo\indevo\industries\privateer\intel\PrivateerBaseRaidIntel.java:20`)
all spawn long-running raid/siege intel that ends up in the intel manager
alongside vanilla raids.

These are reachable via:

```java
for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
    if (intel instanceof GenericRaidFGI fgi) { /* per-raid */ }
    if (intel instanceof RaidIntel raid)     { /* per-raid */ }
}
```

`GenericRaidFGI` exposes `params.playerTargeted`
(`<core>\intel\group\GenericRaidFGI.java:496`, `:504`, `:561`) and
`getTargetSystem()`
(`<core>\intel\group\GenericRaidFGI.java:471`). That is the most reliable
"this raid is aimed at me" signal across mods because vanilla itself uses
it for the "colony threat" comm sound
(`<core>\intel\group\GenericRaidFGI.java:496-:499`,
`getSoundColonyThreat()`).

For plain `RaidIntel` subclasses (Tahlan, IndEvo) there is no
`playerTargeted` flag; KMU has to test
`raid.getSystem()`/`raid.getTargetMarket()` against
`Misc.getPlayerMarkets(false)`.

### 3.3 Pattern C - vanilla `OffensiveFleetIntel` is mod-territory in practice

Among installed mods only Nexerelin (not installed in this profile, but
cached) actually subclasses `OffensiveFleetIntel` -
`.sources-cache\mods\Nexerelin-0.12.1e\jars\ExerelinCore\exerelin\campaign\intel\fleets\OffensiveFleetIntel.java:99`,
`SatBombIntel.java:42`, `InvasionIntel.java:66`,
`NexRaidIntel.java:47`. Vanilla itself does *not* expose
`OffensiveFleetIntel` to mods at all (the class is in Nex's
`exerelin.campaign.intel.fleets` package, not in `starfarer.api`). For
KMU's mod-agnostic enumeration that means: only enumerate
`OffensiveFleetIntel` if it ends up being installed; do not assume vanilla
provides it.

### 3.4 Pattern D - generic intel-manager walk + tag/class filter (stelnet style)

stelnet's `IntelIsClass` and `FactionIsRaiding`
(`.sources-cache\mods\stelnet\stelnet\stelnet\filter\IntelIsClass.java:9-:48`,
`.sources-cache\mods\stelnet\stelnet\stelnet\filter\FactionIsRaiding.java:9-:38`)
are the cleanest soft-dep-safe enumeration pattern: hold the desired class
as a `Class<?>` and use `Class#isInstance`. KMU should do the same with
classes resolved via `Class.forName(..., false, classLoader)` so that a
missing mod degrades to "this filter matches nothing" instead of a hard
`NoClassDefFoundError`.


## 4. Per-mod sections

### 4.1 Tahlan Shipworks

JAR: `mods\tahlan\jars\TahlanShipworks.jar`. Decompiles cleanly.

**Threat surface**: `LegioSiegeMissionIntel`
(`.sources-cache\mods\tahlan\jars\TahlanShipworks\org\niatahl\tahlan\campaign\siege\LegioSiegeMissionIntel.java:41-:79`).
Five-stage `RaidIntel`:

- `LegioSiegeMissionStage1Organize` (orgDur ~15-30 days, 1 day in dev mode)
- `LegioSiegeMissionStage2Assemble`
- `LegioSiegeMissionStage3Travel`
- `LegioSiegeMissionStage4Construct` (the bite - lands a base)
- `LegioSiegeMissionStage5Defend`

Constructor: `(FactionAPI faction, MarketAPI from, StarSystemAPI target,
float fleetPoints)`
(`LegioSiegeMissionIntel.java:54`). The `target` is a
`StarSystemAPI`, not a `MarketAPI`. Public `getFaction()` at `:88`. No
public getter for `target` or `from`; both are `protected`. The class is
`public` so KMU can `isInstance`-check it, but field access requires
reflection or the stage-walk via inherited `RaidIntel.getCurrentStage()`.

**Picks targets** in `LegioSiegeManager.pickTarget()`
(`.sources-cache\mods\tahlan\jars\TahlanShipworks\org\niatahl\tahlan\campaign\siege\LegioSiegeManager.java:118-:139`):
weighted-pick over every market hostile to Legio. If the player is at war
with Legio (likely once the player has any colonies, given Legio's default
relations), player systems are eligible.

**Cadence**: spawn timer in `LegioSiegeManager`
(`LegioSiegeManager.java:73-:81`). Base 360 days down to a 180-day floor,
shortened by 30 days per cycle past 206. `getInstance()` exposed via
`$tahlan_LegioRaidBaseManager` memory key
(`LegioSiegeManager.java:23,:36-:39`).

**Soft-dep risk**:
- Class is `public`, package
  `org.niatahl.tahlan.campaign.siege.LegioSiegeMissionIntel`.
- Field stability: `protected MarketAPI from`, `protected boolean
  reachedTarget`, `protected LegioRaidSetupOutcome outcome` -
  these are `protected` not `public`, so KMU should *not* reach into them.
- Use `instanceof LegioSiegeMissionIntel` + `RaidIntel.getCurrentStage()`
  + `RaidIntel.getSystem()` (inherited).

### 4.2 Domain Phase Lab

JAR: `mods\Domain Phase Lab-1.7.2\jars\dpl_phase_lab.jar`. Decompiles
cleanly.

**Threat surfaces** (two):

1. `dpl_HostileActivityFactor`
   (`.sources-cache\mods\Domain Phase Lab-1.7.2\jars\dpl_phase_lab\data\scripts\crisis\dpl_HostileActivityFactor.java:37-:39`)
   - `extends BaseHostileActivityFactor implements FleetGroupIntel.FGIEventListener`.
   - Constructor takes the live `HostileActivityEventIntel` -
     `:46-:49`. Adds itself to the listener manager so it ticks.
   - Gated on the existence of `Global.getSector().getEconomy().getMarket("dpl_security")`
     (the DPL security industry market) -
     `:57-:60`. If the security market is gone or the faction
     `"dpl_phase_lab"` is absent, progress is zero, factor goes gray.
   - Player-visible name fed to vanilla's threat list comes from
     `BaseHostileActivityFactor.getNameForThreatList(boolean)` unless DPL
     overrides; the override in `dpl_HostileActivityFactor` is not part of
     the snippet we needed but the class inherits the vanilla one.

2. `dpl_MercenaryAttack`
   (`.sources-cache\mods\Domain Phase Lab-1.7.2\jars\dpl_phase_lab\data\scripts\crisis\dpl_MercenaryAttack.java:15-:58`)
   - `extends GenericRaidFGI`. Spawned by the factor when it fires.
   - Memory key `$dpl_Mercenary_ref` (`:18`); `get()` via the inherited
     `GenericRaidFGI.get(KEY)` helper
     (`<core>\intel\group\GenericRaidFGI.java:48-:50`).
   - Forces the attacking fleet to faction `"independent"` and tags it with
     `$dpl_Mercenary_fleet` (`:53-:54`). KMU's "who is attacking" label
     should use this tag rather than the faction id, otherwise the threat
     looks like a random Independent.

**Lifecycle / progress getters** worth using:
- `GenericRaidFGI.getCurrentAction()` / `getActions()`
  (`<core>\intel\group\FleetGroupIntel.java:540-:558`,
  `:641`) for stage.
- `GenericRaidFGI.getTargetSystem()`
  (`<core>\intel\group\GenericRaidFGI.java:471`) - the system the
  raid will hit (this is a `protected` accessor; KMU must walk
  `getActions()`/`FGRaidAction.getWhere()` instead to stay on public API).
- `getParams().playerTargeted` -
  `<core>\intel\group\GenericRaidFGI.java:561` (public field on
  `GenericRaidParams`).

**Manager / registry**: the factor is itself registered with
`Global.getSector().getListenerManager()`
(`dpl_HostileActivityFactor.java:48`), but KMU's correct entry point is
still `HostileActivityEventIntel.get().getFactors()`. No separate "all
active DPL threats" accessor.

**Soft-dep risk**: classes are `public`, package
`data.scripts.crisis.dpl_HostileActivityFactor`. Lowercase-`d` package
prefix is unusual but stable across DPL releases (it has been
`data.scripts.crisis` since 1.x). Use reflective class-lookup; if Domain
Phase Lab is absent the lookup returns `null` and the filter drops out.

### 4.3 Secrets of the Frontier

JAR: `mods\secretsofthefrontier\jars\secretsofthefrontier.jar`. Decompiles
cleanly.

**Threat surface**: `SotfDustkeeperHAFactor`
(`.sources-cache\mods\secretsofthefrontier\jars\secretsofthefrontier\data\scripts\campaign\plugins\dustkeepers\SotfDustkeeperHAFactor.java:20-:90`).
- `extends BaseHostileActivityFactor`. Adds itself to the listener manager
  in its constructor (`:24`).
- `getNameForThreatList(...)` returns `"Dustkeeper Contingency"` (`:39-:41`).
- `getDescColor(...)` and `getNameColor(...)` pull the faction colour from
  `Global.getSector().getFaction("sotf_dustkeepers")`
  (`:46-:48`, `:64-:69`). KMU should mimic this rather than hard-coding
  a colour.
- `getMaxNumFleets(StarSystemAPI)` reads a settings key
  `"sotf_dustkeeperHAmaxFleets"` (`:71-:73`).
- `getSpawnInHyperProbability(...)` returns 0 (`:75-:77`) - the Dustkeeper
  factor never spawns hyper fleets, only in-system patrols. KMU's tooltip
  copy needs to reflect that for this factor specifically.

`SotfHopeForHallowhallEventIntel`
(`.sources-cache\mods\secretsofthefrontier\jars\secretsofthefrontier\data\scripts\campaign\missions\hallowhall\SotfHopeForHallowhallEventIntel.java:62-:70`)
is `extends BaseEventIntel` but it is a *quest-state* event (tracks player
relationship with the Dustkeepers, awards proxy patrols when player
colonies reach size 4+). It does spawn defensive attacks via `startAttack`
(`:524`) but those are quest-driven, not colony-threat. KMU should *not*
list this as a threat factor.

**Manager / registry**: same answer as DPL - the factor is plumbed into
the vanilla aggregator, so enumerate via `HostileActivityEventIntel`.

**Soft-dep risk**: class is `public`,
`data.scripts.campaign.plugins.dustkeepers.SotfDustkeeperHAFactor`. Faction
id `"sotf_dustkeepers"` is hard-coded in the class; KMU should not depend
on it directly - go through the factor's own colour getters.

### 4.4 Industrial.Evolution

JAR: `mods\IndEvo\jars\IndEvo.jar`. Decompiles cleanly.

**Threat surface**: `PrivateerBaseRaidIntel`
(`.sources-cache\mods\IndEvo\jars\IndEvo\indevo\industries\privateer\intel\PrivateerBaseRaidIntel.java:20-:60`).
- `extends RaidIntel`.
- Spawned by `PrivateerBase.startRaid(target, baseRaidFP)`
  (`.sources-cache\mods\IndEvo\jars\IndEvo\indevo\industries\privateer\industry\PrivateerBase.java:470-:499`).
- Targets *any* `StarSystemAPI` containing markets hostile to the base's
  owning faction (`:476-:480`). The player inherits this transitively.
- Tags spawned fleets with memory keys `$isWarFleet`, `$isRaider` and
  optionally `$isPirate` (`:45-:48`).

`MeteorShowerLocationIntel`
(`.sources-cache\mods\IndEvo\jars\IndEvo\indevo\exploration\meteor\intel\MeteorShowerLocationIntel.java:20`)
and `MeteorShowerSpawningLocationIntel` exist but are environmental, not
colony-threat. Excluded.

**Soft-dep risk**:
- `PrivateerBaseRaidIntel` is `public`; safe to `isInstance`.
- Constructor signature
  `(StarSystemAPI, FactionAPI, RaidIntel.RaidDelegate)` is unusual (vanilla
  `RaidIntel` takes the same triple) and stable.
- To detect "this raid is aimed at the player", do the standard
  `RaidIntel.getSystem()` -> `Misc.getMarketsInLocation(system, "player")`
  check.

### 4.5 MagicLib

JAR: `mods\MagicLib\jars\MagicLib.jar`. Decompiles to Kotlin-style bytecode
plus readable Java.

**Not a threat source** - listed here because the user asked for it as a
layout reference.

`BountyBoardIntelPlugin`
(`.sources-cache\mods\MagicLib\jars\MagicLib\org\magiclib\bounty\intel\BountyBoardIntelPlugin.java:43-:283`)
is the relevant precedent:

- Extends `MagicRefreshableBaseIntelPlugin` (not vanilla `BaseIntelPlugin`)
  to get a refresh hook (`:217-:218`).
- Uses a `large description` layout - `hasLargeDescription()` returns true
  (`:71-:74`). Vanilla intel items render in the small description pane
  by default; `large` gives you the whole right-hand area.
- Layout in `layoutPanel(CustomPanelAPI, float width, float height)`
  (`:205-:264`):
  - 300px-wide list panel on the left (`BountyListPanelPlugin`,
    `:214-:216`).
  - Text/detail panel filling the remaining width on the right
    (`:232-:240`).
  - List click swaps the right-hand panel via the
    `bountyList.addListener(...)` callback (`:241`,
    `layoutPanel$lambda$7` at `:311-:319`).
- Bounty *source* is `PROVIDERS` - a `List<BountyBoardProvider>` aggregated
  across plug-in mods (`:62`,
  `:219-:230`). Each provider exposes `getBounties()` returning
  `List<BountyInfo>`.
- Tags itself "Bounties" via `getIntelTags(...)` (`:291-:295`) so it shows
  up in vanilla's intel filter sidebar.

For KMU the takeaway is the *shape*: provider list + selection list +
detail panel, large description. The threat equivalent would be:
provider == one mod / vanilla factor + per-system view, selection ==
threatened system or threat factor, detail == per-system raid list with
ETAs.

### 4.6 stelnet

JAR: `mods\stelnet\stelnet.jar`. Decompiles cleanly.

**Not a threat source** - filter-shape precedent only.

Relevant pieces:

- `ExplorationHelper.getFilterableIntel()`
  (`.sources-cache\mods\stelnet\stelnet\stelnet\board\exploration\ExplorationHelper.java:20-:26`)
  walks `Global.getSector().getIntelManager().getIntel()` and reduces by
  tag (`AnyHasTag("Exploration")`) and class (`IntelIsClass`).
- `IntelIsClass`
  (`.sources-cache\mods\stelnet\stelnet\stelnet\filter\IntelIsClass.java:9-:48`)
  holds a `Class<?>` and uses `Class#isInstance` - the soft-dep pattern KMU
  wants. Hold the class object as `Class<?>` (nullable). Resolve via
  `Class.forName(name, false, getClass().getClassLoader())`; a `null` from
  a `try`/`catch (ClassNotFoundException)` means "this filter is inert".
- `FactionIsRaiding`
  (`.sources-cache\mods\stelnet\stelnet\stelnet\filter\FactionIsRaiding.java:9-:38`)
  uses `FactionAPI#getCustomBoolean("makesPirateBases")` plus a hardcoded
  Luddic Path check. KMU's "who threatens us" listing can use the same
  custom-boolean trick instead of a faction-id allowlist.

### 4.7 Captain's Log

JAR: `mods\Captain's Log-0.2.0\CaptainsLog.jar`. Decompiles cleanly.

**Not a threat aggregator**. Captain's Log creates *its own* intel items
for ruins (`RuinsIntel`), comm relays (`CommRelayIntel`), salvageable
ships (`SalvageableShipIntel`), megastructures (`MegastructureIntel`) etc.
- `.sources-cache\mods\Captain's Log-0.2.0\CaptainsLog\CaptainsLog\campaign\intel\automated\*.java`.

The closest thing to "aggregator" behaviour is in
`CaptainsLogEveryFrame.removeFleetLogEntries(...)`
(`.sources-cache\mods\Captain's Log-0.2.0\CaptainsLog\CaptainsLog\scripts\CaptainsLogEveryFrame.java:49-:81`)
which walks `intelManager.getIntel(BreadcrumbIntel.class)` to cull
completed entries. It is not a model for "consolidated colony threats".


## 5. Mods checked with nothing relevant

Brief justification for each so the next sweep doesn't re-check them:

- **EmergentThreats IX Revival** and **EmergentThreats Vice** - source-only
  mods that ship loose `.java` files under
  `mods\EmergentThreats_*\data\hullmods\...` and `data\scripts\...`. Their
  content is hullmod / weapon / shipsystem code (e.g.
  `AquilonMicroMissile.java`, `SignalMasker.java`,
  `LightOfSindria.java`). No `IntelInfoPlugin`, no `HostileActivityFactor`,
  no `RaidIntel`. Despite the name they do not add campaign-level threats.
- **Hostile Intercept** - jar at
  `.sources-cache\mods\hostileIntercept\jars\HostileIntercept\hostileIntercept\`
  contains `commands/*.java`, `scripts/Autopause.java`,
  `scripts/HostilityRings.java`, `scripts/InterceptRings.java`. Pure
  campaign-map ring rendering + autopause hooks; no intel surface.
- **Random Assortment of Things** - jar contains 11 intel classes
  (`assortment_of_things\...`) including `RapidResponseIntel`,
  `WarpCatalystMissionIntel`, `ProjectGilgameshIntel`, `ExoshipIntel`,
  `BountyFleetIntel`, `SettlementIntel`, `AbyssWarningBeaconIntel`,
  `ArtifactIntel`, `ArchivistIntel`, `GenesisRefightintel`,
  `ExoshipRemainsIntel`. All are mission/quest/event intel; none subclass
  `HostileActivityFactor` or target player colonies. Grep for
  `HostileActivityFactor|OffensiveFleetIntel|playerMarket|raidMarket` over
  the RAT cache returns no matches.
- **Domain Explorarium Expansion** - jar contains no `*Intel*.java` and no
  matches for the threat base classes. It is a sector-content mod
  (drones, derelicts) that does not register colony threats.
- **Knights of Ludd** - intel classes are `ZeaAbilityIntel`,
  `ZeaLoreIntel`, `ZeaMechanicIntel`, `ZeaTriTachBreadcrumbIntel`. All
  quest/lore. No `HostileActivityFactor`, no `RaidIntel`.
- **Hiver Swarm** - source-only mod, files limited to ship/hullmod/world
  generation (`HIVER_BioHull.java`, `HIVER_gen.java`, `HIVERmodPlugin.java`,
  star-system generators). Hiver threats are realized as in-system fleets
  spawned by world gen, not as intel items. They do not surface in any
  player-colony enumeration KMU can subscribe to.
- **Secrets of the Frontier (quest intel)** - separate from the
  `SotfDustkeeperHAFactor` covered in 4.3, SotF also ships
  `SotfSierraConvIntel`, `SotfSiriusIntel`, `SotfAMemoryIntel`,
  `SotfWaywardStarIntel`, `SotfHopeForHallowhallEventIntel`. All are
  quest-state intel and do not represent a colony-threat surface.
- **The Star Federation** - jar contents are all hullmods, ship systems,
  weapon effects, fleet generators (`FedGen.java`, `Fed_Octavia.java`).
  No intel of any kind.
- **Grand Colonies** - the only `Intel|raid|invasion` hit in its jar is
  `IndustryPanelReplacer.java`, a UI overlay. No campaign intel.

These should all be left off the KMU report's "checked sources" list with
a one-line note ("checked, nothing relevant"); duplicating the rationale
here is enough.


## 6. KMU implications

1. **Primary source of truth = vanilla's `HostileActivityEventIntel`.** It
   already aggregates every mod that uses the
   `HostileActivityFactor` plug-in shape (DPL, SotF Dustkeeper, plus
   whatever isn't installed but will be in other profiles).
   `HostileActivityEventIntel.get().getFactors()` -> filter to
   `HostileActivityFactor` is the mod-agnostic enumeration.
2. **Per-system "danger" is already computed by vanilla.** The HAE intel's
   `HAEStarSystemDangerData` rollup
   (`<core>\intel\events\HostileActivityEventIntel.java:240-:278`) hands
   KMU the colour, top-three threat names, sortMag, etc. The map overlay
   in feature #21 can reuse that data directly rather than re-rolling it.
3. **Out-of-band raid/siege intel still has to be picked up by walking the
   intel manager.** Tahlan Legio (`LegioSiegeMissionIntel` -> `RaidIntel`)
   and IndEvo Privateer (`PrivateerBaseRaidIntel` -> `RaidIntel`) do not
   register as `HostileActivityFactor`. They surface only via
   `getIntelManager().getIntel()` plus an `instanceof`-based filter.
4. **`playerTargeted` is the cleanest "is this aimed at me" signal but
   only on `GenericRaidFGI`.** For plain `RaidIntel` (Tahlan, IndEvo) KMU
   must compare `raid.getSystem()` against
   `Misc.getPlayerMarkets(false)`.
5. **Soft-dep pattern**: hold optional mod classes as `Class<?>` resolved
   reflectively at startup, exactly like stelnet's `IntelIsClass`. Drop
   silently when null. Do not import mod symbols directly.
6. **UI shape**: MagicLib's `BountyBoardIntelPlugin` is the closest
   precedent. Large description, list-panel on left (300px), detail panel
   on right, listener callback to swap the detail panel.
   `getIntelTags(...)` to inject the KMU report into vanilla's intel
   filter sidebar.
7. **Avoid duplicating vanilla's threat list.** Vanilla already shows
   "system name | danger | top threat names" in the HAE intel.
   Feature #21 needs to add the *map overlay* and the *cross-mod
   un-aggregated raid list*, not re-implement the per-system breakdown.
