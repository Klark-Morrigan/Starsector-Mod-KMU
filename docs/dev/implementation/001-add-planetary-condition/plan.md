# Planetary Condition Editor Plan

## Index

- [Research Baseline](#research-baseline)
- [Step 1 - Mod Scaffold](#step-1---mod-scaffold)
- [Step 2 - Condition Service](#step-2---condition-service)
- [Step 3 - Editor Entry Point And Market Context](#step-3---editor-entry-point-and-market-context)
- [Step 4 - Condition Chooser UI](#step-4---condition-chooser-ui)
- [Step 5 - Condition Add Action](#step-5---condition-add-action)
- [Step 6 - Console Command Entry Point](#step-6---console-command-entry-point)
- [Step 7 - Console Entry Verification](#step-7---console-entry-verification)
- [Step 8 - Injected Market UI Button](#step-8---injected-market-ui-button)
- [Step 9 - Injected UI Verification](#step-9---injected-ui-verification)
- [Step 10 - Release Automation](#step-10---release-automation)

## Research Baseline

Detailed research lives in
`docs/dev/research/planetary-condition-editor-reference.md`.

Decisions from that research:

- Do not add a hard library dependency for this feature yet.
- Do not depend on AshLib for this feature yet. Current KMU needs are covered
  by Starsector's public API and small local adapters; use AshLib as code
  reference unless a later step finds a concrete dependency benefit.
- Use Starsector's public API for condition data and mutation:
  `getAllMarketConditionSpecs`, `getMarketConditionSpec`, `MarketAPI.addCondition`,
  `MarketAPI.removeCondition`, `MarketAPI.hasCondition`, `MarketAPI.getCondition`,
  `MarketAPI.getFirstCondition`, and `MarketAPI.reapplyConditions`.
- Treat LunaLib as a later settings option, not as part of the core condition
  editor.
- Treat MagicLib, LazyLib, BoxUtil, ParticleEngine, GraphicsLib, NebuLib, and
  RetroLib as reference or unrelated for this feature unless a later step finds
  a concrete need.
- Use Random Assortment of Things and AOTD/VOK as UI-architecture references:
  RAT has loose source for reflection helpers and UI tree crawling; AOTD/VOK has
  a useful interceptor/listener shape visible from its jar classes.

Implementation posture:

- Keep condition mutation isolated from UI code so it can be tested.
- Prefer public UI APIs for our own panels and dialogs.
- Use reflection only for discovering or attaching to existing Starsector UI
  panels, and keep that reflection behind small KMU-owned helpers.
- All KMU modded-code boundaries should fail closed instead of crashing the
  campaign UI: catch expected `RuntimeException` failures from Starsector or
  third-party mod APIs, return explicit failure results or empty safe state, and
  preserve enough error detail for logging or UI feedback.

## Step 1 - Mod Scaffold

Create the `KMU` mod identity, build layout, and runtime entry point.

Reason: the feature needs a stable mod id and a small runtime hook before any UI
can be attached. This step also establishes the repository release shape in
`docs/dev/release.md`: production code in `src/main/java`, tests in
`src/test/java`, and compiled runtime code in `jars/KMU.jar`.

Tests:

- confirm the production Java compiles into `jars/KMU.jar` against the local
  Starsector API jar;
- compile and run repository test classes in `src/test/java`;
- verify Gradle uses Java 17 and reads the mod version from `mod_info.json`.

## Step 2 - Condition Service

Implement KMU-owned Java services for listing condition specs and applying a
condition to a market.

Reason: the research found that Starsector's public condition APIs are enough.
This logic should not live inside the UI hook, because UI reflection will be the
fragile part and condition mutation should remain independently testable.

Implementation:

- create a condition spec query class that reads
  `Global.getSettings().getAllMarketConditionSpecs()`;
- filter to planetary specs with `MarketConditionSpecAPI.isPlanetary()`;
- expose the current market condition ids from `MarketAPI.getConditions()`;
- validate target condition ids with `Global.getSettings().getMarketConditionSpec(id)`;
- add only if absent, using `MarketAPI.addCondition(id)`;
- mark newly added conditions surveyed with
  `market.getFirstCondition(id).setSurveyed(true)` when available;
- call `MarketAPI.reapplyConditions()` after mutation;
- do not remove conflicts, incompatible conditions, or same-group conditions;
- do not check ownership;
- return structured failure results instead of allowing Starsector API
  exceptions to crash the UI action.

Tests:

- unit test candidate filtering with test doubles for condition specs;
- unit test duplicate prevention;
- unit test invalid condition handling;
- unit test that adding a valid absent condition calls add, surveyed, and
  reapply in that order;
- unit test repository and market adapter boundaries with dynamic proxies;
- unit test failure handling for repository and market API exceptions.

## Step 3 - Editor Entry Point And Market Context

Find or create a stable way to open the editor for the market the player is
inspecting.

Reason: the product goal is an in-context colony editor, but the research found
no clean public API for injecting controls into the vanilla colony or survey UI.
This step isolates market detection and UI attachment behind KMU-owned code.

Implementation:

- start with a KMU market-context abstraction that contains the `MarketAPI` and,
  when available, the discovered `UIPanelAPI`;
- support the player-present-at-market case through interaction dialog target
  and player fleet interaction target lookup;
- support the remote colony/outposts ledger case through
  `SectorAPI.getCurrentlyOpenMarket()`;
- keep ownership out of the detection logic;
- add a target-eligibility abstraction before opening the editor:
  - allow markets with a planet entity or planet-condition-only market;
  - allow a resolved `CURRENTLY_OPEN_MARKET` source so the colony/outposts
    ledger can open a remote market;
  - reject generic campaign, fleet, station, and dialog contexts that do not
    support planetary conditions;
  - fail closed and report a user-facing reason when eligibility checks throw;
- prefer opening a KMU custom dialog/panel through public APIs when possible;
- keep reflected `UIPanelAPI` discovery optional and behind
  `KmuMarketUiContext` for a later UI-injection step;
- if a vanilla screen button is required, implement a small reflection layer:
  - `KmuReflection`;
  - `KmuCoreUiLocator`;
  - `KmuMarketUiContext`;
  - `KmuMarketUiListener`;
  - `KmuMarketUiInterceptorScript`.

Reference patterns:

- RAT: `ArtifactUIScript.kt`, `MinimapUI.kt`, `UIExtensions.kt`,
  `ReflectionUtils.kt`, and `AtMarketListener.kt`.
- AOTD/VOK: `CoreUiInterceptor`, `ColonyUIListener`, `MarketUIListener`, and
  `SurveyPanelContextUI` as architecture reference only.

Tests:

- compile-time coverage for the market-context classes;
- unit test currently-open market, interaction-dialog target, and player-fleet
  interaction target resolution;
- unit test eligibility for planet markets, planet-condition-only markets, and
  currently-open market/ledger contexts;
- unit test rejection for non-planet markets and missing market context;
- unit test fail-closed behavior when one market context source throws;
- unit test fail-closed behavior when target-eligibility checks throw;
- in-game smoke test while present at a colony;
- in-game smoke test from the outposts ledger;
- in-game smoke test on a non-player-owned colony.

## Step 4 - Condition Chooser UI

Add a `Planetary Conditions` editor surface and a scrollable icon-grid chooser
listing all planetary condition specs.

Reason: the editor should be visual, discoverable, and usable with long modded
condition lists.

Implementation:

- build the chooser with Starsector `CustomPanelAPI` and `TooltipMakerAPI`;
- list every planetary condition spec returned by the condition service;
- present the picker as a grid of condition icons, not as text rows;
- keep grid cells stable while preserving the condition image's vanilla visual
  treatment, including source-image transparency and apparent image size
  (some are wide and aren't square);
- put condition names in tooltips (if tooltips don't provide one yet), not as
  always-visible grid labels;
- use grey-out filtering as the only visible present/absent state indicator;
- do not show `Present`, `Absent`, `Add`, ids, or other state text directly in
  the grid;
- clicking an absent icon is the add action;
- clicking a present icon does not mutate the market;
- for present conditions, render icons and tooltips through the live
  `MarketConditionAPI` / `MarketConditionPlugin` path so they match the planet
  condition UI;
- append a low-visibility metadata footer to every tooltip, after the primary
  live-plugin or spec/Codex-style content;
- the tooltip footer may include internal condition id, icon path, and source
  mod only;
- use a spec-based tooltip fallback only for absent conditions whose plugin
  tooltip requires a live market condition.

Tests:

- unit test chooser view-model construction from specs and current market ids;
- unit test chooser editor handoff from resolved `MarketAPI` to dialog delegate;
- unit test present conditions render icon and tooltip data through the live
  condition plugin path;
- unit test present and absent tooltips both append id, icon path, and source
  mod only in low-visibility footer metadata;
- unit test present and absent state is exposed to rendering as icon grey-out
  state, not visible text;
- in-game smoke test with vanilla and modded conditions loaded;
- compare present condition icons and tooltips against the same conditions on
  the planet condition row;
- confirm condition names are visible in tooltips and not as permanent grid
  labels;
- confirm long lists remain scrollable and selectable.

## Step 5 - Condition Add Action

Wire each chooser entry to add its condition to the active market.

Reason: this is a utility/editor feature, so it should preserve deliberate
invalid test states and allow direct editing of non-player faction colonies.

Implementation:

- clicking an absent condition icon calls the condition service;
- clicking a present condition icon does not add a duplicate;
- after mutation, refresh the chooser state from the market;
- after refresh, the newly present condition uses the live condition plugin icon
  and tooltip path;
- show success/failure feedback without replacing the grey-out state with
  permanent visible status text;
- leave removal for a later feature.

Tests:

- unit test that an absent entry adds the condition, marks it surveyed, reapplies
  conditions, refreshes the model, and records success feedback;
- unit test that a present entry does not mutate the market;
- unit test that failed add attempts refresh the model and record failure
  feedback;
- add two normally incompatible conditions and confirm both remain present after
  reapply;
- repeat on a non-player-owned colony;
- save, reload, and confirm the added condition remains.

```mermaid
sequenceDiagram
    participant User
    participant Chooser
    participant Service
    participant Market
    User->>Chooser: Click absent condition
    Chooser->>Service: addCondition(market, id)
    Service->>Market: addCondition(id)
    Service->>Market: getFirstCondition(id).setSurveyed(true)
    Service->>Market: reapplyConditions()
    Service-->>Chooser: Result
    Chooser-->>User: Refreshed condition state
```

## Step 6 - Console Command Entry Point

Add a developer-facing Console Commands entry point for opening the condition
editor.

Reason: the chooser and mutation path need an in-game entry point before full
vanilla UI injection is worth implementing. Console Commands gives KMU a stable
manual smoke-test route and will also support future KMU developer utilities.
The console command and the later injected button must call the same
`KmuConditionEditorEntryPoint`.

Implementation:

- do not declare Console Commands (`lw_console`) as a hard dependency in
  `mod_info.json`;
- add the Console Commands jar as a compile-only Gradle dependency from the
  local Starsector `mods` folder;
- add `data/console/commands.csv`;
- reserve the `kmu` console tag/category for KMU commands;
- prefix every KMU console command with `kmu_`;
- name every KMU console command with at least one verb and one noun;
- add the first command as `kmu_open_conditions`;
- implement the command outside `data/scripts` so Starsector does not try to
  compile it when Console Commands is not active;
- have `kmu_open_conditions` require campaign/market context, then rely on the
  shared editor entry point to reject unsupported locations before opening the
  chooser;
- report wrong context or open failures through Console Commands output instead
  of crashing.

Tests:

- unit test the command rejects non-campaign contexts;
- unit test the command calls the editor entry point in campaign market context;
- unit test the command surfaces unsupported-location failures from the shared
  entry point without bypassing the eligibility gate;
- unit test command failure handling when the entry point returns false or
  throws;
- compile against the local Console Commands jar and Starsector API jar.

## Step 7 - Console Entry Verification

Compile the production jar and smoke test the console entry in game.

Reason: Starsector UI hooks rely on runtime behavior and, if vanilla panel
injection is later used, internal classes. Runtime verification is required in
addition to compilation, and the console entry is the first reachable in-game
path.

Tests:

- run `gradlew test jar` against the local Starsector API jar;
- enable KMU and Console Commands in the Starsector launcher for this smoke
  test;
- open Console Commands with its configured keybind;
- run `kmu_open_conditions` while no market context is active and confirm a clear
  wrong-context/failure message;
- run `kmu_open_conditions` while a colony or market context is active;
- add a condition;
- confirm condition color/state updates in the chooser;
- save and reload;
- confirm the condition persists.

## Step 8 - Injected Market UI Button

Add the intended in-game button on the relevant market/colony UI surface.

Reason: Console Commands is the developer and smoke-test route, but the feature
should be discoverable from the market UI. The injected button must be a thin
adapter over the same editor entry point used by `kmu_open_conditions`.

Implementation:

- implement a small reflection layer only for locating and attaching to the
  relevant Starsector UI panels;
- keep reflection behind KMU-owned helpers;
- add a listener or script that detects the active colony/survey/market panel;
- attach a `Planetary Conditions` button once per relevant panel instance;
- route the button click to `KmuConditionEditorEntryPoint`;
- fail closed if the expected panel tree is unavailable.

Tests:

- unit test reflection helpers against simple proxy/dummy objects where
  possible;
- unit test that listener state prevents duplicate button insertion;
- unit test that button action calls the same editor entry point as the console
  command;
- unit test fail-closed behavior when panel lookup fails.

## Step 9 - Injected UI Verification

Smoke test the injected UI path in game.

Reason: the injected button is the highest-risk part because it depends on
runtime UI structure. It needs a separate verification pass after the console
route is already proven.

Tests:

- run `gradlew test jar` against the local Starsector API jar;
- open each target market or colony screen in game;
- confirm the `Planetary Conditions` button appears once;
- click the button and confirm it opens the same chooser as
  `kmu_open_conditions`;
- add a condition;
- confirm condition color/state updates in the chooser;
- confirm incompatible conditions remain present after reapply;
- repeat on a non-player-owned colony;
- save and reload;
- confirm the condition persists.

## Step 10 - Release Automation

Add a GitHub Actions workflow that produces the packaged release zip.

Reason: releases should be repeatable and should not depend on manually mixing
source files, tests, generated classes, and runtime assets. The workflow should
build `jars/KMU.jar`, run tests, assemble `dist/KMU`, create
`KMU-<version>.zip`, and upload it as a workflow artifact. On version tags, it
should also attach the zip to a GitHub release.

Tests:

- trigger the workflow manually once and from a version tag;
- confirm the zip has `KMU/` as its top-level folder;
- confirm the zip includes `mod_info.json` and `jars/KMU.jar`;
- confirm the zip excludes `src/`, `docs/dev/`, tests, `.git`, and build caches.

Constraints: do not commit Starsector game jars or third-party mod jars solely
for CI. Any compile-only API inputs needed by the workflow must be supplied in a
documented private or permitted way.
