# KMU Architecture

This document tracks the current Java structure and should be updated whenever
new feature layers or runtime boundaries are added.

## Condition Feature Layers

```mermaid
flowchart LR
    subgraph Entry["Entry / Wiring Layer"]
        Plugin["KMU_ModPlugin<br/>Starsector mod entry point"]
    end

    subgraph Feature["Condition Feature"]
        direction TB

        subgraph EditorEntry["Editor Entry"]
            direction TB
            EntryPoint["KmuConditionEditorEntryPoint<br/>Open editor for current market"]
            Editor["KmuConditionEditor<br/>Future editor UI port"]
        end

        subgraph Core["Core Logic"]
            direction TB
            Service["KmuConditionService<br/>Use-case orchestration"]
        end

        subgraph MarketContext["Market UI Context"]
            direction TB
            Context["KmuMarketUiContext<br/>Resolved market and optional panel"]
            ContextSource["KmuMarketUiContextSource<br/>Context source enum"]
        end

        subgraph Domain["Domain Values"]
            direction TB
            Spec["KmuConditionSpec<br/>Condition value object"]
        end

        subgraph Results["Operation Results"]
            direction TB
            Result["KmuConditionAddResult<br/>Structured add result"]
            Status["KmuConditionAddStatus<br/>Result status enum"]
        end

        subgraph Ports["Feature Ports"]
            direction TB
            RepositoryPort["KmuConditionRepository<br/>Condition spec lookup"]
            MarketPort["KmuEditableMarket<br/>Market read/write operations"]
            ContextResolverPort["KmuMarketUiContextResolver<br/>Current market context lookup"]
        end
    end

    subgraph Shared["Shared Utilities"]
        direction TB
        ReporterPort["KmuErrorReporter<br/>Failure reporting"]
    end

    subgraph Adapters["Starsector Adapter Layer"]
        direction TB

        subgraph ContextAdapters["Market Context Adapters"]
            direction TB
            ContextResolverAdapter["StarsectorMarketUiContextResolver<br/>Sector/CampaignUI adapter"]
        end

        subgraph RepositoryAdapters["Condition Spec Adapters"]
            direction TB
            RepositoryAdapter["StarsectorConditionRepository<br/>SettingsAPI adapter"]
        end

        subgraph MarketAdapters["Market Adapters"]
            direction TB
            MarketAdapter["StarsectorEditableMarket<br/>MarketAPI adapter"]
        end
    end

    subgraph External["External Starsector API"]
        direction TB

        subgraph SettingsApiGroup["Settings / Specs"]
            direction TB
            SettingsAPI["SettingsAPI"]
            MarketConditionSpecAPI["MarketConditionSpecAPI"]
        end

        subgraph CampaignApiGroup["Campaign Context"]
            direction TB
            SectorAPI["SectorAPI"]
            CampaignUIAPI["CampaignUIAPI"]
            InteractionDialogAPI["InteractionDialogAPI"]
            CampaignFleetAPI["CampaignFleetAPI"]
            SectorEntityToken["SectorEntityToken"]
            UIPanelAPI["UIPanelAPI"]
        end

        subgraph MarketApiGroup["Markets / Conditions"]
            direction TB
            MarketAPI["MarketAPI"]
            MarketConditionAPI["MarketConditionAPI"]
        end
    end

    Plugin -. later wires .-> EntryPoint

    EntryPoint --> ContextResolverPort
    EntryPoint --> Editor
    EntryPoint --> ReporterPort

    Service --> RepositoryPort
    Service --> MarketPort
    Service --> ReporterPort
    Service --> Result
    Service --> Spec

    Result --> Status
    Context --> ContextSource

    ContextResolverAdapter -. implements .-> ContextResolverPort
    RepositoryAdapter -. implements .-> RepositoryPort
    MarketAdapter -. implements .-> MarketPort

    ContextResolverAdapter --> SectorAPI
    ContextResolverAdapter --> CampaignUIAPI
    ContextResolverAdapter --> InteractionDialogAPI
    ContextResolverAdapter --> CampaignFleetAPI
    ContextResolverAdapter --> SectorEntityToken
    ContextResolverAdapter --> Context

    RepositoryAdapter --> SettingsAPI
    RepositoryAdapter --> MarketConditionSpecAPI
    RepositoryAdapter --> Spec

    MarketAdapter --> MarketAPI
    MarketAdapter --> MarketConditionAPI
    Context --> MarketAPI
    Context --> UIPanelAPI
```

## Layer Notes

### Entry / Wiring Layer

- `KMU_ModPlugin`
- Should stay thin.
- Later it should create services and connect them to UI/runtime hooks.
- `KmuConditionEditorEntryPoint` is the feature-level opening contract for the
  future editor UI.

### Core Feature Layer

- `KmuConditionService`
- `KmuConditionEditorEntryPoint`
- `KmuConditionEditor`
- `KmuMarketUiContext`
- `KmuMarketUiContextResolver`
- `KmuMarketUiContextSource`
- `KmuConditionSpec`
- `KmuConditionAddResult`
- `KmuConditionAddStatus`
- Contains KMU's condition behavior and rules.
- Should avoid direct Starsector API dependency where practical.

### Ports / Interfaces

- `KmuConditionRepository`
- `KmuEditableMarket`
- `KmuMarketUiContextResolver`
- These are KMU-owned contracts.
- They keep core logic testable without launching Starsector.

### Starsector Adapter Layer

- `StarsectorConditionRepository`
- `StarsectorEditableMarket`
- `StarsectorMarketUiContextResolver`
- Converts Starsector APIs into KMU ports.
- This is where `SettingsAPI`, `SectorAPI`, `CampaignUIAPI`, `MarketAPI`,
  `MarketConditionAPI`, and `MarketConditionSpecAPI` are allowed.

### Shared Utilities

- `KmuErrorReporter`
- Used by feature services and adapters to report recoverable runtime failures.

### External Starsector API

- Not owned by KMU.
- Should remain at the boundary.
- Runtime failures from this layer should not crash campaign UI actions.

## Dependency Rule

The intended dependency direction is:

```text
UI / ModPlugin
    -> Service
        -> KMU ports
            <- Starsector adapters
```

Feature behavior should stay in KMU-owned core classes. Starsector-specific
objects should be wrapped by adapters before they reach core services.
