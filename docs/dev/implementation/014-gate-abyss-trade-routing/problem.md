# Gate and Abyss Trade Routing

## Index

- [Problem](#problem)
- [For Laymen](#for-laymen)
- [Baseline Behavior](#baseline-behavior)
- [Initial Scope](#initial-scope)
- [Out of Scope](#out-of-scope)
- [Approach Options](#approach-options)
- [Decisions](#decisions)
- [Tests](#tests)
- [Open Questions](#open-questions)
- [Risks](#risks)
- [Companion: API research notes](research.md)

## Problem

Vanilla Starsector models the economy on the hyperspace graph.
Both `market.accessibility` and trade fleet spawning walk hyperspace distances between markets;
gates and deep-hyperspace ("abyssal") travel are not edges in that graph.
As a result:

- Markets in `SYSTEM_CUT_OFF_FROM_HYPER` systems have effectively zero accessibility even when the player can reach them in seconds through an active gate or a short deep-hyperspace hop.
- Vanilla trade fleets do not spawn routes through gates or into the abyss,
  so cut-off / abyssal player colonies
  and frontier settlements see no external traffic regardless of player effort to make them reachable.
- Mod content that places markets in abyssal systems (and player colonies established there) ends up economically inert,
  which contradicts the in-fiction reality of those systems being player-reachable.

Feature 003 should extend the vanilla accessibility model and trade fleet routing so gate-linked
and abyss-linked markets participate in the economy in proportion to how reachable they actually are.

## For Laymen

Right now the game pretends abyssal
and gate-only-reachable colonies are unreachable for trade purposes,
so they get no merchant traffic and their accessibility (and therefore income) is poor.
This feature teaches the trade system that an active gate or a short deep-hyperspace hop counts as a real route,
so those colonies behave like normal ones.

## Baseline Behavior

- Treat the set of **currently-active gates** as a fully-connected clique:
  every pair of active gates forms a virtual hyperspace edge between their host systems.
  "Active" is defined by the public API `GateEntityPlugin.isActive(SectorEntityToken)` (see [research: Gate Activation State API](research.md#gate-activation-state-api)
  and [research: Gate Topology](research.md#gate-topology-clique-not-pairs)).
- Treat a **short deep-hyperspace distance** between a cut-off system
  and the nearest *non-hostile* non-cut-off system as a virtual edge,
  weighted by that distance.
  Additionally,
  connect cut-off systems to nearby non-hostile cut-off neighbours within a bounded radius,
  so abyssal colonies form localised trade webs in addition to their stable link to the wider sector economy.
- Treat **in-system neighbour markets** as accessibility signals for *every* colony,
  not only abyssal ones:
  a graduated additive bonus from non-hostile markets in the same system
  (own faction contributes a small floor; foreign non-hostile contributes more) and a graduated additive malus from hostile markets in the same system.
  Fleets are not a signal source -
  only markets.
  See [research: Accessibility Composition](research.md#accessibility-composition-vanilla-and-mods)
  for why this is greenfield rather than a duplicate of vanilla's `accessibilitySameFactionBonus` (single owner check, not per-neighbour) and `accessibilityLossWhenAllHostile` (binary, not graduated).
- Recompute affected markets' `accessibility` from a graph that includes these virtual edges,
  without replacing the vanilla computation for markets unaffected by gates or abyssal links.
- Cause real trade fleets to spawn at one end of a virtual edge and visibly fly to the other end -
  through the gate or across deep hyperspace -
  at a reduced rate compared to a normal hyperspace edge of equivalent weight
  (gate traffic is plausible but not infinite; abyssal traffic is rare and risky).
  Fleets must be real campaign fleets,
  not destination-side spawns,
  so the route looks alive.
- Surface the contribution to accessibility in the market tooltip
  so the player can see *why* a cut-off colony now has non-zero accessibility
  ("Gate route to <system>: +X", "Deep hyperspace route: +Y").
- Trade fleets injected by this feature must respect normal destruction,
  interdiction,
  and faction-hostility rules.
  They are real fleets,
  not bookkeeping ghosts.
- Behaviour must degrade gracefully when no gates exist and no abyssal markets exist:
  zero virtual edges,
  zero overhead,
  vanilla behaviour unchanged.

## Initial Scope

- Gate edges between any two activated gates,
  regardless of faction ownership of the gate-host systems.
- Abyssal edges only for markets in systems tagged `SYSTEM_CUT_OFF_FROM_HYPER`:
  one stable edge to the nearest non-hostile non-cut-off market by deep-hyperspace distance,
  plus localised edges to nearest non-hostile cut-off neighbours within a bounded radius / neighbour cap.
- In-system neighbour effects for every colony:
  graduated additive bonus from non-hostile co-system markets
  (own-faction floor, foreign non-hostile larger),
  graduated additive malus from hostile co-system markets.
  Markets only;
  fleets are explicitly out of scope to keep the recompute pure-monthly.
- Accessibility extension as a stat injection through the standard `MutableStat` modifier API on the affected `MarketAPI`,
  keyed by this feature's ID so it is unambiguous in tooltips and removable on disable.
- Synthetic trade fleet spawns through a dedicated `BaseRouteFleetManager` subclass registered with its own `SOURCE_ID` (e.g. `kmu_trade`) alongside vanilla's `econ` source.
  The vanilla class is `EconomyFleetRouteManager`;
  we do not subclass or patch it.
  See [research: Spawn Seam for Virtual Edges](research.md#spawn-seam-for-virtual-edges).

## Out of Scope

- Player-controlled gate construction or activation.
  The feature reads gate state;
  it does not change it.
- Visual fleet path overlays on the campaign map.
  Trade fleets remain invisible in transit as in vanilla.
- Re-pricing commodities at cut-off markets beyond what vanilla derives from the new accessibility values.
- Rewriting `market.accessibility` from scratch.
  The virtual edges feed *into* the vanilla model;
  they do not replace it.
- Custom abyssal-storm or gate-failure events.
- KMO settlement integration.
  KMO benefits from this feature automatically through standard market accessibility;
  no KMO-aware code lives here.

## Approach Options

Vanilla trade fleets are real:
they spawn at an origin market,
fly through hyperspace,
and can be intercepted en route.
They are not bookkeeping ghosts.
"Make cut-off markets feel alive" therefore means real fleets actually using gate or abyssal routes,
not synthetic spawns appearing at the destination.

| Option | What it does | Cost | Outcome |
|---|---|---|---|
| A. Augment vanilla accessibility | Run our own gate/abyss-aware graph walk; apply results as `MutableStat` modifiers on affected markets. Vanilla's walk runs untouched and our contributions stack on top | Moderate | Cut-off / gate-reachable markets get defensible accessibility values and visible tooltip attribution. Fleets do not yet route through gates or the abyss |
| B. A + teach fleet routing | A, plus extend the trade fleet spawner / pathfinder so fleets spawned at one end of a virtual edge actually fly the route (through the gate, or across deep hyperspace) and arrive visibly at the other end | High | Markets feel alive: real merchant fleets use gates and abyssal routes |
| C. Replace `EconomyAPI` / vanilla graph walk | Swap out vanilla's accessibility computation entirely with one that natively knows about gate and abyssal edges | Very high; high save-compat and mod-coexistence risk | Cleanest in theory; in practice it fights every other economy mod and re-derives correctness vanilla already gets right |

Recommendation:

- Ship **A** first as a foundation.
  The graph walk is the durable core;
  it computes "what *should* the contribution be" and is reusable by B.
- Extend to **B** once A is proven stable.
  B is the part that makes it look alive
  and is also the part with the most unknown pathfinder behaviour -
  see [Open Questions](#open-questions).
- Reject **C** — replacing `EconomyAPI` is intrusive,
  hostile to Nexerelin and similar mods,
  and re-derives correctness vanilla already handles.
  A's stat-modifier approach achieves the same player-visible outcome without owning the global economy implementation.

## Decisions

- Live in KMU as a standalone feature
  (`kmu.trade.routing` or similar package).
  KMU already owns market-condition tooling,
  so market-stat manipulation belongs in the same mod.
- Implement as a pure extension:
  vanilla code paths are not monkey-patched;
  the feature contributes through standard `MutableStat` modifiers and the public fleet manager API.
- Virtual edges are recomputed on `EconomyTickListener.reportEconomyMonthEnd`,
  not on `reportEconomyTick` (sub-tick) and not on an `EveryFrameScript`.
  Gate activation state and cut-off-system topology change on a months-to-never cadence;
  sub-tick or per-frame recompute would do identical work for no player-visible benefit.
  See [research: Economy Tick Seam](research.md#economy-tick-seam).
- Tooltip text is resolved through the feature 002 localization provider,
  which has no doc folder in this repo.
- Disabling the feature (config flag) removes all injected stat modifiers
  and stops spawning synthetic fleets on the next economy tick.
  No save-data migration required.
- **Uninstall strategy.** Disabling a feature in settings triggers a save-load cleanup pass that strips this feature's artifacts
  (`kmu_trade_*` stat modifiers, registered route managers, in-flight synthetic fleets) so the player can re-save with no residual mod state.
  This makes the mod safe to uninstall:
  disable the feature,
  load,
  save,
  then remove the jar.
  The cleanup must be idempotent and run before the first economy tick of the loaded session.
- **Modifier ID migration (rename strategy).** Each feature owns two ID sets:
  the **live** IDs it currently writes,
  and a **retired** list of IDs it used to write in prior versions.
  Every save load,
  before the first economy tick,
  the feature strips any modifier whose ID appears in its retired list.
  A rename is then a two-step code change:
  add the new ID to live,
  add the old ID to retired,
  never delete from retired.
  This reuses the same cleanup seam as the uninstall strategy
  and removes the "modifier ids are forever" constraint -
  they are stable only until explicitly retired.
- All identifiers prefixed `kmu_trade_` so they cannot collide with vanilla,
  KMO,
  or third-party mods.

## Tests

- Unit:
  with no active gates and no cut-off markets,
  the feature registers zero virtual edges and zero stat modifiers.
- Unit:
  N active gates produce N*(N-1)/2 virtual edges between their host systems;
  deactivating any gate removes all edges incident to it on the next recompute.
- Unit:
  a cut-off market gets a non-zero abyssal edge to the nearest non-cut-off market,
  with weight monotonic in deep-hyperspace distance.
- Unit:
  accessibility contributions appear as a labelled `MutableStat` modifier with a stable ID,
  so tooltips can name the source.
- Unit:
  disabling the feature removes every injected modifier on the next tick.
- Unit (wiring):
  the recompute driver is registered with the sector listener manager as an `EconomyTickListener`
  and does *not* implement `EveryFrameScript`.
  Pins the seam against accidental migration to per-frame execution.
- Unit (cadence):
  driving the listener through one simulated month invokes recompute exactly once -
  on `reportEconomyMonthEnd`.
  `reportEconomyTick` invocations and frame advances trigger zero recomputes.
- Integration (option B only):
  synthetic trade fleet count per virtual edge over N ticks falls within an expected band relative to vanilla fleets on a comparable hyperspace edge.

## Open Questions

Resolved
(see [research.md](research.md) for sources and line refs):

- **Fleet spawn seam.** Vanilla's class is `EconomyFleetRouteManager`.
  The cleanest integration is our own `BaseRouteFleetManager` subclass with `SOURCE_ID = "kmu_trade"`,
  reusing `EconomyFleetRouteManager.createTradeRouteFleet` as the fleet factory.
  No vanilla subclassing or patching.
  See [Spawn Seam for Virtual Edges](research.md#spawn-seam-for-virtual-edges).
- **Gate awareness in AI pathfinding.** Vanilla AI does not see gates.
  `RouteLocationCalculator.findJumpPointToUse` only walks `JumpPointAPI` and excludes wormholes;
  gates are `BaseCustomEntityPlugin`.
  There is no `GATE_TRANSIT` `FleetAssignment`.
  Synthetic fleets must teleport at the gate boundary inside a `RouteFleetAssignmentAI` subclass,
  firing `GateEntityPlugin.showBeingUsed` for the visual and notifying `GateTransitListener`s.
  This is cheaper than the "campaign-level `EveryFrameScript`" fallback originally feared.
  See [Gate Awareness in the AI Pathfinder](research.md#gate-awareness-in-the-ai-pathfinder).
- **Abyssal pathfinding.** Vanilla `GO_TO_LOCATION` crosses deep hyperspace on its own between systems that both have hyperspace jump points -
  no custom AI needed.
  `SYSTEM_CUT_OFF_FROM_HYPER` systems still require the same boundary-teleport seam as gates.
  See [Abyssal Travel via AI](research.md#abyssal-travel-via-ai).
- **Gate activation API.** Stable public static `GateEntityPlugin.isActive(SectorEntityToken)`;
  no memory-key scraping.
  Enumerate gates by `Entities.GATE` / `Entities.INACTIVE_GATE` or `Tags.GATE`.
  See [Gate Activation State API](research.md#gate-activation-state-api).
- **Nexerelin overlap on gates.** None at code level in Nexerelin 0.12.1e.
  See [Nexerelin Coexistence](research.md#nexerelin-coexistence).
- **`MutableStat` overlap with Nexerelin accessibility hooks.** Nexerelin patches several accessibility hooks
  but does not touch gates directly.
  Mitigation per [research](research.md#nexerelin-coexistence):
  distinct `kmu_trade_`-prefixed modifier IDs and read-only access to vanilla stats -
  the feature only *adds* its own modifiers,
  never mutates vanilla's or Nexerelin's.
- **Abyssal edge anchoring.** Every abyssal market gets one *stable* seam to the nearest non-hostile non-cut-off market
  (its link to the broader sector economy; "non-hostile" = neutral or better, not strictly friendly).
  On top of that it gets *localised* seams to its nearest non-hostile abyssal neighbours within a deep-hyperspace radius,
  so clusters of abyssal colonies form a small local trade web instead of each routing alone to a distant anchor.
  The radius / neighbour cap is bounded
  so isolated abyssal clusters cannot prop each other up without an outside link.
- **In-system neighbour effects -
  scope.** The feature has three buckets,
  not two:
  (1) cross-system stable seams
  (abyssal market to nearest non-hostile non-cut-off market),
  (2) cross-system localised seams
  (abyssal market to nearby non-hostile abyssal neighbours),
  (3) in-system neighbour effects
  (every colony: graduated bonus from non-hostile co-system markets, graduated malus from hostile co-system markets).
  Bucket (3) helps abyssal colonies that happen to have friendly neighbours in their own system,
  and applies uniformly to non-abyssal colonies for consistency.
  Fleets are not a signal source -
  only markets -
  to keep the recompute pure-monthly.
  See [research: Accessibility Composition](research.md#accessibility-composition-vanilla-and-mods)
  for vanilla / mod non-collision.
- **In-system neighbour effects -
  curve.** Per-neighbour flat modifiers,
  additive,
  counting **distinct markets** (not factions):
  five hostile stations are five threats to a freighter,
  not one.
  Starting tuning:
  - Own-faction in-system market:
    `+0.05` each.
  - Foreign non-hostile in-system market:
    `+0.1` each.
  - Hostile in-system market:
    `-0.2` each.

  The 2x malus-vs-bonus asymmetry is deliberate -
  hostility hurts commercial reach more than friendship helps it.
  Both bonus and malus are **uncapped by design**:
  overwhelming enemy presence should deter most trade
  (deep negative accessibility = "no freight company will risk this system"),
  and per-neighbour bonus values are small enough that natural system density bounds the upside without an explicit cap.
  Even a five-own-faction in-system cluster only adds `+0.25` on top of vanilla's `+0.5` same-faction flat term -
  a strong commercial-hub signal but not nonsense.

- **Gate edge weighting -
  shape.** Lore-wise the gate itself is a free,
  distance-agnostic pipe
  (sector-corner-to-corner transit is canonical; no fuel/CR cost).
  The bottleneck is destination commercial viability,
  not the gate.
  Formula:
  for each reachable gate destination D,
  the gate contributes to the observer's market additively per destination,
  reusing the **local accessibility signals of D's system only**
  (D's in-system neighbour effects plus D's own stable / localised abyssal seams - no recursion into D's gate edges, so chains do not compound):
  - **Bonuses propagate at x0.5 per destination.** Commerce attenuates with hop distance;
    being one gate hop away is less commercially intimate than being in-system.
  - **Maluses propagate at x1.0 per destination.** Gate traffic is bidirectional and uncontrollable:
    a hostile foothold at any reachable node can spill raiders or interdictors into every other node on the web.
    The threat is shared across the web,
    not diluted by alternatives.
    There is no chokepoint special case -
    the web *is* the threat surface.
  - **Both stack additively across destinations**,
    consistent with the uncapped-by-design rationale for in-system effects:
    overwhelming web-wide hostility should drive accessibility deeply negative and shut trade down,
    exactly as the in-fiction reading demands.
  - **No distance term.** Matches the lore (gates are distance-agnostic)
    and the freight-economics reading
    (the pipe is free; only the destination matters).

Still open:

- (none)

## Risks

- **Save compatibility -
  rename.** Stat modifiers persist on `MarketAPI` across saves.
  Renaming a modifier ID in code leaves the old ID orphaned on every existing save
  (stale contribution, silent double-counting once the new ID is also written).
  Mitigated by the modifier ID migration strategy in [Decisions](#decisions):
  renames go through a retired-id list that the save-load cleanup pass strips.
  A rename without updating that list is the bug,
  not the rename itself.
- **Save compatibility -
  uninstall.** Removing the mod jar without first disabling the feature leaves orphaned `kmu_trade_*` modifiers on markets
  (soft break: stale accessibility values) and causes `ClassNotFoundException` during save load for the registered route manager
  and any in-flight synthetic fleets' `RouteFleetAssignmentAI` subclass (hard break: save will not load).
  Mitigated by the uninstall strategy in [Decisions](#decisions):
  disable in settings,
  load,
  save,
  then remove the jar.
- **Performance.** Recomputing the virtual edge set
  once per month over a sector with hundreds of markets is acceptable;
  doing it per frame or per economy sub-tick is not.
  The cadence decision (see [Decisions](#decisions)) must be enforced by test,
  not by convention -
  both the seam (which listener method) and the work
  (recompute is a pure function of `(active gates, cut-off markets, faction relations)` driven by that callback).
  Abyssal- to-abyssal localised edges are bounded by a fixed neighbour cap / radius
  so the abyssal pass stays O(n) in cut-off markets rather than O(n^2).
- **Player confusion.** A cut-off colony suddenly having normal accessibility looks like a bug if the source is not surfaced.
  Tooltip attribution is required,
  not optional.
- **Synthetic fleet behaviour (option B).** Fleets spawned outside vanilla's normal route picker may take unexpected paths or interact poorly with patrol AI.
  Start with low spawn rates and monitor.
- **Scope creep.** "Make abyssal colonies feel alive" is adjacent to "rewrite the trade economy".
  Defending the line drawn in [Out of Scope](#out-of-scope) is necessary or feature 003 will not ship.
