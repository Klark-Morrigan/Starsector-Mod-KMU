# Planetary Condition Picker Reference

Research date: 2026-04-27

Scope: local Starsector install under `mods`, excluding `mods/KMU`. The goal was
to find usable public APIs, library dependencies, and prior mod patterns for a
planetary condition picker.

## Method

- Classified installed mods from `mod_info.json`.
- Counted source-bearing mods with `rg --files` for `*.java` and `*.kt`.
- Searched source mods for market-condition and UI keywords:
  `addCondition`, `removeCondition`, `MarketCondition`, `CustomPanelAPI`,
  `TooltipMakerAPI`, `EveryFrameScript`, `CoreUITabId`, `getCore`,
  `getCurrentTab`, and `getChildrenCopy`.
- Inspected binary-only libraries and large mods with:
  - `jar tf <mod jar>` for package/class inventory;
  - `javap -public -classpath ...` for public method surfaces.

## Starsector API Surface

The condition data and mutation API is public and sufficient:

- `SettingsAPI.getAllMarketConditionSpecs()`
- `SettingsAPI.getMarketConditionSpec(String)`
- `MarketConditionSpecAPI.getId()`, `getName()`, `getIcon()`, `isPlanetary()`,
  `getTags()`
- `MarketAPI.getConditions()`
- `MarketAPI.addCondition(String)`
- `MarketAPI.removeCondition(String)`
- `MarketAPI.hasCondition(String)`
- `MarketAPI.getCondition(String)`
- `MarketAPI.getFirstCondition(String)`
- `MarketAPI.reapplyConditions()`
- `MarketAPI.suppressCondition(String)` / `unsuppressCondition(String)`
- `MarketConditionAPI.setSurveyed(boolean)`

The existing mods that modify planetary conditions generally validate the
condition spec, add or remove by id, mark the added condition surveyed, and then
reapply or let the market update naturally.

The UI API is strong for creating our own panels and dialogs:

- `Global.getSettings().createCustom(...)`
- `CustomPanelAPI.createCustomPanel(...)`
- `CustomPanelAPI.createUIElement(...)`
- `TooltipMakerAPI.addButton(...)`
- `TooltipMakerAPI.setActionListenerDelegate(...)`
- `BaseCustomUIPanelPlugin.buttonPressed(...)`
- `CustomDialogDelegate`
- `CustomVisualDialogDelegate`
- `BaseIndustryOptionProvider`
- `DialogCreatorUI.showDialog(...)`

There does not appear to be a clean public API for inserting controls into an
existing colony/survey panel. Mods that do this crawl the live UI tree with
reflection.

## Library Findings

### LunaLib

Useful public API:

- `lunalib.lunaSettings.LunaSettings`
  - `getBoolean`, `getInt`, `getFloat`, `getDouble`, `getString`, `getColor`
  - settings listeners
- `lunalib.lunaRefit.LunaRefitManager`
  - `addRefitButton`, `hasButtonOfClass`, `getFirstButtonOfClass`
- `lunalib.lunaRefit.BaseRefitButton`
  - refit button lifecycle hooks and panel hooks

Relevant source references:

- `mods/lunalib/src/lunalib/backend/ui/refit/RefitButtonAdder.kt`
- `mods/lunalib/src/lunalib/backend/util/ReflectionUtils.kt`
- `mods/lunalib/src/lunalib/lunaSettings/LunaSettings.kt`
- `mods/lunalib/src/lunalib/lunaRefit/BaseRefitButton.java`

Assessment: useful for settings and refit UI, but not directly for a colony or
survey condition picker. Its reflection/UI code is a good reference, but most of
it is internal Kotlin implementation.

### MagicLib

Useful public API:

- `org.magiclib.util.MagicSettings`
  - typed settings helpers
- `org.magiclib.util.MagicCampaign`
  - campaign helper methods, including market creation helpers
- `org.magiclib.util.ui.MagicRefreshableBaseIntelPlugin`

Relevant source references:

- `mods/MagicLib/src/org/magiclib/internalextensions/UIExtensions.kt`
- `mods/MagicLib/src/org/magiclib/paintjobs/MagicPaintjobCampaignRefitAdder.kt`
- `mods/MagicLib/src/org/magiclib/ReflectionUtils.kt`

Assessment: good examples for UI reflection and button listener wiring. Not a
strong dependency candidate for this feature unless we later need its settings
or campaign helpers for other reasons.

### LazyLib

Useful public API found:

- `org.lazywizard.lazylib.campaign.CampaignUtils`
- `org.lazywizard.lazylib.ModUtils`
- math, combat, string, and rendering helpers

`org.lazywizard.lazylib.campaign.MarketUtils` has no public methods in the jar
installed here.

Assessment: not needed for the condition picker.

### AshLib

Useful public API:

- `ashlib.data.plugins.coreui.CommandTabListener`
- `ashlib.data.plugins.coreui.CommandTabInterceptor`
- `ashlib.data.plugins.coreui.CommandTabTracker`
- `ashlib.data.plugins.coreui.CommandUIPlugin`
- UI widgets such as `BasePopUpDialog` and `CustomButton`

Assessment: reference only for this feature right now. KMU should not declare it
as a dependency unless a later step finds a concrete benefit over the local
adapters and public Starsector APIs. Its command-tab and UI widget classes are
useful implementation examples.

### BoxUtil, ParticleEngine, GraphicsLib, NebuLib, RetroLib

These are mostly rendering, particles, planet constants, or retrofit utilities.
No direct condition-editor dependency was found.

## Non-Library Reference Mods

### Random Assortment of Things

Strongest source reference for UI tree crawling and custom panel insertion.

Relevant files:

- `mods/Random Assortment of Things-3.3.1/src/assortment_of_things/artifacts/ArtifactUIScript.kt`
- `mods/Random Assortment of Things-3.3.1/src/assortment_of_things/campaign/ui/MinimapUI.kt`
- `mods/Random Assortment of Things-3.3.1/src/assortment_of_things/misc/UIExtensions.kt`
- `mods/Random Assortment of Things-3.3.1/src/assortment_of_things/misc/ReflectionUtils.kt`
- `mods/Random Assortment of Things-3.3.1/src/assortment_of_things/scripts/AtMarketListener.kt`

Patterns observed:

- Poll `AppDriver.getInstance().currentState`.
- Require `CampaignState`.
- Find core UI by invoking `getCore` or `getEncounterDialog().getCoreUI()`.
- Find the active tab via `getCurrentTab`.
- Crawl child panels via reflected `getChildrenCopy`.
- Create custom panels with `Global.getSettings().createCustom`.
- Add the custom panel into the located existing panel.
- Track market open/close with campaign listener callbacks.

### AOTD / Vaults of Knowledge

Binary-only in this install, but class names and public APIs show an established
architecture for market UI interception.

Relevant public classes:

- `data.kaysaar.aotd.vok.listeners.CoreUiInterceptor`
- `data.kaysaar.aotd.vok.scripts.coreui.listeners.ColonyUIListener`
- `data.kaysaar.aotd.vok.scripts.coreui.listeners.MarketUIListener`
- `data.kaysaar.aotd.vok.scripts.coreui.listeners.SurveyPanelContextUI`
- `data.kaysaar.aotd.vok.scripts.coreui.listeners.MarketContextListenerInjector`
- `data.kaysaar.aotd.vok.campaign.econ.listeners.TradeOutpostAndSurveyInterceptor`

Pattern implied:

- A central interceptor discovers colony/survey/cargo panels.
- It publishes context objects containing `UIPanelAPI` and `MarketAPI`.
- Feature listeners attach their UI when the relevant context is discovered.

Assessment: this is probably the cleanest architectural model to copy locally,
but not a dependency target.

### Terraforming Made Easy

Strong condition mutation reference.

Relevant files:

- `mods/terraformingmadeeasy/src/terraformingmadeeasy/industries/BaseTerraformingIndustry.java`
- `mods/terraformingmadeeasy/src/terraformingmadeeasy/Utils.java`

Patterns observed:

- Validate with `Global.getSettings().getMarketConditionSpec(id)`.
- Toggle condition presence with `market.hasCondition`, `addCondition`, and
  `removeCondition`.
- After adding, call `market.getFirstCondition(id).setSurveyed(true)`.
- Remove mutually exclusive or hated conditions explicitly.

### DIY Planets

Useful for planet-type and condition category transitions.

Relevant file:

- `mods/diyplanets/src/kentington/diyplanets/TerraformingUtilities.java`

Patterns observed:

- Modify conditions with `addCondition` and `removeCondition`.
- Mark newly added conditions surveyed.
- Apply visual planet spec changes with `planet.getPlanetEntity().applySpecChanges()`.

### Niko's More Planetary Conditions

Useful condition helper reference.

Relevant file:

- `mods/niko_morePlanetaryConditions/src/data/utilities/niko_MPC_marketUtils.kt`

Pattern observed:

- `MarketAPI.addConditionIfNotPresent(conditionId)` avoids duplicate add and
  returns the resulting `MarketConditionAPI`.

### UAF

Binary-only in this install. Useful public extension reference:

- `data.econ.listeners.RuinRestorationOptionProvider`
  extends `BaseIndustryOptionProvider`
- `kaysaar.uaf.keycardshop.newUI.GiftShopUIDelegateV2`
  implements `CustomVisualDialogDelegate`
- `kaysaar.uaf.keycardshop.scripts.UAFCCUIInMarketScript`
  implements `EveryFrameScript`

Assessment: good examples that condition/market features can be exposed through
public dialog/industry option APIs without directly patching the colony panel.

## Recommendations For KMU

1. Do not add a hard library dependency for Step 2.

   The Starsector API is enough for the condition service. Existing libraries
   help with settings, refit UI, command tabs, or rendering, but none provides a
   simple public "add button to survey panel" API.

2. Implement a small internal condition service.

   Suggested responsibilities:

   - list candidate specs from `getAllMarketConditionSpecs()`;
   - filter to planetary specs by `isPlanetary()`;
   - expose current market condition ids;
   - add condition only after spec validation;
   - avoid duplicates;
   - mark newly added condition surveyed;
   - remove condition by id;
   - reapply conditions after mutation where needed.

3. Prefer opening our own editor dialog/panel over deeply rewriting vanilla UI.

   Use `CustomDialogDelegate`, `CustomVisualDialogDelegate`, or a custom panel
   launched from a small entry point. This keeps the first implementation easier
   to test and less fragile.

4. If we must inject into an existing survey/colony panel, copy the AOTD/RAT
   architecture locally.

   Suggested internal pieces:

   - `KmuReflection` with cached method lookup;
   - `KmuCoreUiLocator` for `getCore`, `getCoreUI`, `getCurrentTab`, and
     `getChildrenCopy`;
   - `KmuMarketUiListener` interface;
   - context objects containing the discovered `UIPanelAPI` and `MarketAPI`;
   - one `EveryFrameScript` or `CoreUITabListener` that discovers contexts and
     notifies listeners.

5. Treat AshLib as code reference only for this feature.

   Do not add AshLib to `mod_info.json` or the Gradle runtime classpath unless
   KMU needs one of its public APIs directly. If its command-tab or widget
   patterns are useful, copy the relevant design locally and compile against the
   Starsector API instead.

6. Use LunaLib later for user settings if needed.

   It is appropriate for preferences such as "show editor button on survey
   panel" or "allow non-planetary conditions." It should not be pulled into the
   core condition mutation implementation.

## Open Questions

- Should the editor appear on the colony/survey screen, or is an interaction
  dialog / command-tab entry acceptable for the first version?
- Should the editor allow all planetary conditions or only conditions safe to
  add manually?
- Should condition categories be mutually exclusive in the UI, such as farmland,
  organics, volatiles, atmosphere, temperature, ruins, and hazard conditions?
- Should condition mutations be dev-only, player-available, or gated behind a
  setting?
