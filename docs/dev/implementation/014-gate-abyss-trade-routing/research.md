# Research: Vanilla Seams for Gate and Abyss Trade Routing

Findings from reading the current `starfarer.api` sources.
Each section answers one of the Open Questions in [problem.md](problem.md#open-questions).
Line numbers refer to the files inside the installed `starsector-core/starfarer.api.zip`.

## Index

- [Trade Fleet Manager Class](#trade-fleet-manager-class)
- [Spawn Seam for Virtual Edges](#spawn-seam-for-virtual-edges)
- [Gate Awareness in the AI Pathfinder](#gate-awareness-in-the-ai-pathfinder)
- [Abyssal Travel via AI](#abyssal-travel-via-ai)
- [Gate Activation State API](#gate-activation-state-api)
- [Gate Topology: Clique, Not Pairs](#gate-topology-clique-not-pairs)
- [Nexerelin Coexistence](#nexerelin-coexistence)
- [Economy Tick Seam](#economy-tick-seam)
- [Accessibility Composition: Vanilla and Mods](#accessibility-composition-vanilla-and-mods)
- [Intra-System Trade Fleets in Vanilla](#intra-system-trade-fleets-in-vanilla)
- [Vanilla Reuse Taxonomy](#vanilla-reuse-taxonomy)
- [Implications for the Plan](#implications-for-the-plan)

## Trade Fleet Manager Class

The class is `com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetRouteManager`,
not `EconomyFleetManager`.
It extends `BaseRouteFleetManager` and implements `FleetEventListener`.
Vanilla constructs and registers it through `RouteManager`,
which is a singleton holding any number of named route sources keyed by `SOURCE_ID` (vanilla uses `"econ"`).

Key reusable surface on `EconomyFleetRouteManager`:

- `addRouteFleetIfPossible()` (line 106) -
  the per-tick spawn entry point.
  Picks from,
  picks to,
  builds segments,
  registers route.
- `pickSourceMarket()` (line 245),
  `pickDestMarket(MarketAPI)` (line 272) -
  market selection;
  weighted by size,
  supply/demand.
- `static createTradeRouteFleet(RouteData, Random)` (line 541) -
  pure fleet-shape factory.
  Reusable by our own manager so trade fleets look identical to vanilla.
- `spawnFleet(RouteData)` (line 505) -
  calls the factory,
  attaches `EconomyFleetAssignmentAI`,
  registers listeners.

## Spawn Seam for Virtual Edges

The vanilla design supports parallel route sources by id,
so the clean integration is a separate `BaseRouteFleetManager` subclass registered as `"kmu_trade"`.
No subclassing of `EconomyFleetRouteManager` and no monkey-patching.

Route segments accept arbitrary `SectorEntityToken` for from and to,
so a virtual edge can be modelled as a multi-segment route
where one segment runs market to gate and the next runs dest-gate to dest-market -
the same shape vanilla uses for normal trade routes.

## Gate Awareness in the AI Pathfinder

**Vanilla AI pathfinding does not see gates.** Confirmed in `RouteLocationCalculator.findJumpPointToUse` (line 421):
it iterates `location.getEntities(JumpPointAPI.class)` and explicitly filters out wormholes and unstable points.
Gates are `BaseCustomEntityPlugin` instances,
not `JumpPointAPI`,
so they are invisible to that routine.

`FleetAssignment` has no `GATE_TRANSIT` value
(`com/fs/starfarer/api/campaign/FleetAssignment.java`).
A vanilla `GO_TO_LOCATION` assignment whose target sits in a gate-only- reachable system will route the fleet through any conventional jump point in the source system,
never through the gate.
If no such jump point exists,
the AI cannot reach the target at all.

The implication for option B in [problem.md](problem.md#approach-options) is that the AI cannot itself transit a gate.
We must perform the gate hop manually in a `RouteFleetAssignmentAI` subclass that,
on arrival proximity to the source gate,
removes the fleet from the source location,
adds it to the destination location at the destination gate,
fires `GateEntityPlugin.showBeingUsed(transitDistLY)` on both gates for the visual,
and notifies registered `GateTransitListener`s via the listener manager.
Then a normal `GO_TO_LOCATION` segment carries the fleet from the dest gate to the dest market.

This is meaningfully cheaper than the "campaign-level `EveryFrameScript` that spawns
and steers fleets manually" fallback the problem doc hedges against.
We keep `RouteManager`,
`RouteSegment`,
and the vanilla fleet factory;
the only custom piece is the boundary-crossing assignment.

## Abyssal Travel via AI

For `IN_HYPER_TRANSIT`,
`RouteFleetAssignmentAI.addTravelAssignment` (line 351) issues a plain `GO_TO_LOCATION` to `current.to`.
The fleet's normal hyperspace autopilot handles the path,
including crossing abyssal terrain.
No custom path AI is required for abyssal travel between two systems that both have hyperspace jump points.

`SYSTEM_CUT_OFF_FROM_HYPER` systems by definition have no hyperspace-reachable jump point,
so the AI cannot enter them on its own.
The same boundary-teleport pattern as the gate case applies:
drop the fleet at the system's edge entity,
then `GO_TO_LOCATION` to the market.

**Intra-abyssal-system trade works in vanilla unchanged.** Two colonies in the same abyssal system are picked as a same-econGroup pair like any other,
`RouteSegment` produces an IN_SYSTEM travel segment,
the fleet spawns in-system via the path described in [Intra-System Trade Fleets in Vanilla](#intra-system-trade-fleets-in-vanilla),
and flies sublight between them.
The abyssal cutoff is only a problem when the route needs to *enter* or *leave* the system through hyperspace.
Feature 003's abyssal scope is therefore strictly cross-system -
abyssal-to-non-abyssal stable seams and abyssal-to-abyssal localised seams across different systems
([problem.md Initial Scope](problem.md#initial-scope) bullet 2).
Same-system abyssal trade is already vanilla behaviour and needs no contribution from this feature.

## Gate Activation State API

`GateEntityPlugin.isActive(SectorEntityToken)` (line 71) is a stable public static:

```java
public static boolean isActive(SectorEntityToken gate) {
    return gate.getCustomPlugin() instanceof GateEntityPlugin
        && ((GateEntityPlugin)gate.getCustomPlugin()).isActive();
}
```

No memory-key reading required.
Gates are enumerated by entity id `Entities.GATE` (active) and `Entities.INACTIVE_GATE` (inactive),
or by tag `Tags.GATE`.

Related public surface that we will use:

- `GateEntityPlugin.areGatesActive()` -
  global gate-network on/off switch (Janus device or `$gatesActive` memory).
- `GateEntityPlugin.showBeingUsed(float transitDistLY)` -
  drives the visual ring on a transit.
- `com.fs.starfarer.api.campaign.listeners.GateTransitListener.reportFleetTransitingGate(fleet, gateFrom, gateTo)`
  - the notification hook to fire after teleporting a synthetic fleet so other mods see the transit.

## Gate Topology: Clique, Not Pairs

Vanilla treats every active gate as reachable from every other active gate;
the player's gate menu offers all other active gates as destinations.
There is no pre-paired gate data structure.

For our virtual-edge graph this means the gate contribution is a clique on the set of currently-active gates,
weighted per edge by whatever heuristic we choose -
**not** an edge list keyed by gate pairs.
The problem doc's "active gate pair" phrasing should be reworded.

## Nexerelin Coexistence

A code-level grep across the installed Nexerelin 0.12.1e finds zero references to `GateEntityPlugin`
and no touchpoints on gate transit logic -
only a `questSkip.json` mention.
Risk of fighting Nexerelin on the gate routing specifically is low.

The `MutableStat` accessibility-injection concern from the problem doc is unrelated to gates and remains open:
Nexerelin patches several accessibility hooks.
Mitigation is the same as already noted -
distinct,
prefixed modifier ids and read-only access to vanilla stats.

## Economy Tick Seam

Starsector exposes `com.fs.starfarer.api.campaign.listeners.EconomyTickListener` with two callbacks:

- `reportEconomyTick(int iterIndex)` -
  fires several times per in-game month
  (vanilla `numIter` for the economy iteration loop).
- `reportEconomyMonthEnd()` -
  fires once per month after the final iteration.

Listeners are registered through `Global.getSector().getListenerManager().addListener(...)`.
The seam is widely used by other mods
(e.g. niko_morePlanetaryConditions `MPC_incomeTallyListener` and KMO's settlement manager placeholder in [KmoSettlementManager.kt](../../../../../KMO/src/main/kotlin/kmo/settlements/KmoSettlementManager.kt)),
so it is the standard pattern and does not require an `EveryFrameScript`+days-accumulator workaround.

For feature 003:

- Gate activation state changes infrequently
  (player-driven Janus activation, occasional gate-network on/off).
  Cut-off-system topology is essentially static across a session.
  Both inputs to the virtual-edge graph change on a months-to-never cadence,
  not a sub-tick cadence.
- The right hook is therefore **`reportEconomyMonthEnd`**,
  not `reportEconomyTick`.
  Sub-tick recomputes would do identical work several times per month for zero player-visible difference.
- Implementing `EconomyTickListener` directly (no `EveryFrameScript`) lets the wiring test be a pure type-and- registration assertion:
  "registered with the listener manager,
  does not implement `EveryFrameScript`."

No KMU-side economy-tick abstraction exists today.
KMO has a placeholder in `KmoSettlementManager` referencing the same callbacks but unimplemented;
the two features can converge on the listener manager independently.

## Accessibility Composition: Vanilla and Mods

What already drives `MarketAPI.getAccessibilityMod()`,
so the feature can layer on top without double-counting or colliding on modifier ids.

`getAccessibilityMod()` returns a `MutableStat` -
a stacking modifier surface with flat and multiplicative contributions,
each keyed by a string id.
Modifiers persist on `MarketAPI` across save/load.
Anyone can add,
remove,
or inspect modifiers via `modifyFlat(id, amount, desc)`,
`modifyMult(id, amount, desc)`,
and `unmodify(id)` / `unmodifyFlat(id)`.
Globally unique feature-prefixed ids are therefore mandatory.

**Vanilla contributors** are config-driven,
not graph-driven.
From `starsector-core/data/config/settings.json`:

| Contributor | Config key | Value |
|---|---|---|
| Base accessibility | `accessibilityBaseValue` | `0.5` |
| Missing spaceport penalty | `accessibilityNoSpaceport` | `-1` |
| Same-faction bonus | `accessibilitySameFactionBonus` | `+0.5` |
| Distance from sector CoM | `accessibilityDistFromCOM` | `-1` per 50 LY |
| Shipping connections | `accessibilityPerUnitShipping` | `+0.1` per unit |
| All-hostile environment | `accessibilityLossWhenAllHostile` | `-1` |

Vanilla does **not** model in-system neighbour effects:
no per-neighbour bonus,
no per-neighbour malus,
no graduated hostile-pressure scale.
The `accessibilityLossWhenAllHostile` term is binary -
all surrounding factions hostile,
full `-1`;
any non-hostile present,
nothing.
There is no gradient between those two states.

The Free Port market condition (`free_market`) does **not** modify accessibility directly in vanilla -
it only changes trade rules and faction opinion effects.

**Nexerelin 0.12.1e** adds exactly one accessibility hook:
the Outlaw faction condition (`OutlawFCSubplugin`),
which calls `market.getAccessibilityMod().modifyFlat(id, 0.1f, "Outlaw")`.
Nexerelin does not touch gate logic and does not add route- or neighbour-based accessibility.

**Other installed mods.** Survey of ~89 `getAccessibilityMod()` call sites across `mods/`:
HMI,
Trails of Tooth and Claw,
EmergentThreats_IX,
Eusan Nation,
GMDA,
CTB Stellar Convenience Store,
Armada Armatura.
All follow the same pattern -
flat modifier with a feature-specific id prefix -
and none compete on the in-system-neighbour signal.

**Implications:**

- The `kmu_trade_` id prefix avoids all known collisions.
- The feature's planned **in-system neighbour effects**
  (graduated bonus for non-hostile in-system markets, graduated malus for hostile in-system markets) are greenfield.
  They are not duplicated by vanilla or any surveyed mod.
- Vanilla's `accessibilitySameFactionBonus` is the closest semantic neighbour to our planned own-faction in-system floor.
  It is a flat single-owner check,
  not per-neighbour-count,
  so it is not a duplicate.
  The combined effect
  (vanilla `+0.5` plus our additive floor) must be tuned conservatively to avoid stacking into nonsense values.
- Vanilla's `accessibilityLossWhenAllHostile` is the closest semantic neighbour to our planned hostile in-system malus.
  It is binary;
  our contribution fills the missing gradient between "any non-hostile present"
  and "zero non-hostile present",
  so it is not a duplicate either,
  but it must compose sensibly with the binary term.
- Only markets are signal sources;
  **fleets are not**.
  Tying accessibility to live fleet positions would either force a per-tick recompute (kills the cadence decision) or use a stale snapshot the player can no longer see on the map.

## Intra-System Trade Fleets in Vanilla

Vanilla spawns **visible,
in-system** trade fleets between two markets in the same star system.
They are not invisible,
not collapsed bookkeeping,
and not handled through hyperspace at all.

Selection side,
`EconomyFleetRouteManager.pickDestMarket(MarketAPI)`:

- Candidate set is `economy.getMarketsInGroup(from.getEconGroup())` -
  grouped by `EconGroup`,
  not by star system.
  Two markets in the same system but different econ groups are *not* paired;
  two markets in different systems within the same group are.
- Exclusion filters are limited to `market == from`,
  `market.isHidden()`,
  and `!market.hasSpaceport()`.
  There is no same-system filter,
  no preference,
  no penalty.

Execution side,
`RouteFleetAssignmentAI`
(in `com/fs/starfarer/api/impl/campaign/procgen/themes/`):

- `getTravelState(segment)` (line 46-48) returns `TravelState.IN_SYSTEM` whenever `segment.isInSystem()` is true.
  The `IN_HYPER_TRANSIT` / `LEAVING_SYSTEM` / `ENTERING_SYSTEM` states are never reached for same-system segments.
  The fleet never enters hyperspace.
- `getLocationForState(IN_SYSTEM)` (line 70) returns `segment.from.getContainingLocation()` -
  the star system itself.
  `giveInitialAssignments` adds the fleet to that system as a normal entity (line 83).
- `addTravelAssignment` for the IN_SYSTEM case (line 314-317) interpolates the fleet's initial position between `current.from` and `current.to` by `current.getTransitProgress()`,
  then issues `fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, current.to, 10000f, "traveling", ...)` (line 351).
  Standard in-system movement;
  the fleet flies entity-to-entity at sublight.

Spawn gating,
`RouteManager.shouldSpawn` (line 662-679) and `getInterpolatedHyperLocation` (line 391-396):

- For an in-system segment,
  `getInterpolatedHyperLocation` returns `current.from.getLocationInHyperspace()` -
  the **system's** hyperspace coordinate.
- A player inside that system has the same hyperspace coordinate;
  `Misc.getDistanceLY` returns ~0 LY,
  which is below `SPAWN_DIST_LY = 1.6`.
  Spawn fires whenever the player enters the system.
- Despawn uses the same hyperspace coordinate;
  the fleet despawns when the player leaves the system far enough.

**Consequence for feature 003.** Vanilla *already* presents visible in-system trade traffic when the player is in a system with multiple markets in the same econ group.
The in-system neighbour effect
(`Initial Scope` bucket 3 in [problem.md](problem.md#initial-scope)) is therefore not adding visible traffic that does not already exist;
it is **adding the accessibility-signal interpretation** of those neighbours (graduated bonus/malus per neighbour),
which vanilla does not model.
The visible-fleet behaviour is vanilla's,
the accessibility-stat behaviour is the feature's.
There is no visibility gap for this feature to fill.

## Vanilla Reuse Taxonomy

Layered map of what the feature consumes vs. reimplements.
Stable public API on top,
opinionated impl-package surface in the middle,
greenfield policy at the bottom.

**Stable public API -
consume directly,
low change risk:**

- `FleetFactoryV3` + `FleetParamsV3` -
  fleet construction.
  Same factory vanilla uses;
  produces fleets indistinguishable from vanilla trade traffic when given `FleetTypes.TRADE` / `TRADE_SMALL` / `TRADE_SMUGGLER`.
- `RouteManager` + `RouteData` + `RouteSegment` -
  per-source route registry.
  Registering under our own `SOURCE_ID = "kmu_trade"` reuses spawn-distance gating (`SPAWN_DIST_LY = 1.6`),
  despawn (`DESPAWN_DIST_LY_*` 3-4 LY),
  persistence,
  and intel hooks.
- `RouteFleetAssignmentAI` (interface) -
  implement to walk an arbitrary segment list.
  Boundary-teleport for gate/abyss transit is a method override,
  not a parallel campaign driver.
- `FleetTypes.TRADE*` constants -
  tagging fleets correctly gives free pirate interdiction,
  patrol escort,
  cargo-drop on defeat,
  and intel colouring.
- `CargoAPI` / `SubmarketAPI` -
  load/unload at markets;
  vanilla storage and black-market submarkets work unchanged.
- `Misc.getShippingDisruption(MarketAPI)` + `ShippingDisruption` events -
  fleet losses propagate to accessibility / income penalties automatically.
- `MutableStat` on `MarketAPI.getAccessibilityMod()` -
  the injection surface for this feature's contributions.
  See [Accessibility Composition: Vanilla and Mods](#accessibility-composition-vanilla-and-mods).

**`starfarer.api.impl.*` -
source-public,
Alex reserves the right to change.
Use,
but pin versions in tests:**

- `BaseRouteFleetManager` -
  subclass for the per-tick spawn loop.
  Inherits market-timeout tracking,
  max-fleet cap,
  source filtering.
  Override `spawnFleet()`,
  `pickSourceMarket()`,
  `pickDestMarket()`.
- `EconomyFleetRouteManager.createTradeRouteFleet(RouteData, Random)` -
  static fleet-shape factory.
  Reusable directly so our synthetic fleets read as identical trade traffic.
- `EconomyFleetAssignmentAI` -
  reference implementation for the 10-segment trade cycle.
  Pattern reference,
  not a base class -
  our segment shape differs (gate/abyss boundary segments).

**Greenfield policy -
vanilla has no equivalent or vanilla's equivalent is the gap being filled:**

- Source/destination selection that includes gate-linked and cut-off markets.
  Vanilla's econ-group + size-weight selection is the constraint we route around.
- Virtual-edge graph over `(active gates, cut-off markets, in-system neighbours)`.
  No vanilla counterpart.
- Boundary-teleport assignment for gate / cut-off-system entry.
  Vanilla AI does not route through gates or into `SYSTEM_CUT_OFF_FROM_HYPER` systems;
  see [Gate Awareness in the AI Pathfinder](#gate-awareness-in-the-ai-pathfinder)
  and [Abyssal Travel via AI](#abyssal-travel-via-ai).
- In-system neighbour effects (graduated bonus/malus).
  Vanilla's `accessibilitySameFactionBonus` and `accessibilityLossWhenAllHostile` are single-owner and binary respectively,
  not per-neighbour-count.

## Implications for the Plan

- The fleet-side seam is a fresh `BaseRouteFleetManager` subclass with `SOURCE_ID = "kmu_trade"`,
  registered alongside vanilla's `"econ"`.
  Vanilla's market pickers and the static `createTradeRouteFleet` factory are reusable.
- Gate transit and cut-off-system entry both reduce to the same "teleport at the boundary" pattern inside a `RouteFleetAssignmentAI` subclass.
  Option B is feasible without hand-rolling a campaign-level fleet driver.
- Active-gate state is queryable through a stable public API;
  no memory-key scraping.
- The gate accessibility graph is a clique on active gates,
  not a set of pairs.
- Recompute cadence is `EconomyTickListener.reportEconomyMonthEnd` on a feature-owned listener;
  not `EveryFrameScript`,
  not `reportEconomyTick`.
  See [Economy Tick Seam](#economy-tick-seam).
- All feature contributions are flat `MutableStat` modifiers on `MarketAPI.getAccessibilityMod()` with `kmu_trade_`-prefixed ids;
  vanilla and surveyed mod contributors do not collide
  and the in-system-neighbour signal is greenfield.
  See [Accessibility Composition: Vanilla and Mods](#accessibility-composition-vanilla-and-mods).
