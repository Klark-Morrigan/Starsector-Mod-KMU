# KMU Architecture

This document tracks the current Java structure and should be updated whenever
new feature layers or runtime boundaries are added.

## Condition Feature Layers

```mermaid
flowchart LR
    subgraph Entry["Entry / Wiring Layer"]
        Plugin["KMU_ModPlugin<br/>Starsector mod entry point"]
    end

    subgraph Core["Core Feature Layer"]
        Service["KmuConditionService<br/>Use-case logic"]
        Result["KmuConditionAddResult<br/>Structured operation result"]
        Status["KmuConditionAddStatus<br/>Result enum"]
        Spec["KmuConditionSpec<br/>Condition value object"]
    end

    subgraph Ports["Ports / Interfaces"]
        RepositoryPort["KmuConditionRepository<br/>Condition spec lookup port"]
        MarketPort["KmuEditableMarket<br/>Market mutation port"]
        ReporterPort["KmuErrorReporter<br/>Error reporting port"]
    end

    subgraph Adapters["Starsector Adapter Layer"]
        RepositoryAdapter["StarsectorConditionRepository<br/>SettingsAPI adapter"]
        MarketAdapter["StarsectorEditableMarket<br/>MarketAPI adapter"]
    end

    subgraph External["External Starsector API"]
        SettingsAPI["SettingsAPI"]
        MarketAPI["MarketAPI"]
        MarketConditionAPI["MarketConditionAPI"]
        MarketConditionSpecAPI["MarketConditionSpecAPI"]
    end

    Plugin -. later wires .-> Service

    Service --> RepositoryPort
    Service --> MarketPort
    Service --> ReporterPort
    Service --> Result
    Service --> Spec

    Result --> Status

    RepositoryAdapter -. implements .-> RepositoryPort
    MarketAdapter -. implements .-> MarketPort

    RepositoryAdapter --> SettingsAPI
    RepositoryAdapter --> MarketConditionSpecAPI
    RepositoryAdapter --> Spec

    MarketAdapter --> MarketAPI
    MarketAdapter --> MarketConditionAPI
```

## Layer Notes

### Entry / Wiring Layer

- `KMU_ModPlugin`
- Should stay thin.
- Later it should create services and connect them to UI/runtime hooks.

### Core Feature Layer

- `KmuConditionService`
- `KmuConditionSpec`
- `KmuConditionAddResult`
- `KmuConditionAddStatus`
- Contains KMU's condition behavior and rules.
- Should avoid direct Starsector API dependency where practical.

### Ports / Interfaces

- `KmuConditionRepository`
- `KmuEditableMarket`
- `KmuErrorReporter`
- These are KMU-owned contracts.
- They keep core logic testable without launching Starsector.

### Starsector Adapter Layer

- `StarsectorConditionRepository`
- `StarsectorEditableMarket`
- Converts Starsector APIs into KMU ports.
- This is where `SettingsAPI`, `MarketAPI`, `MarketConditionAPI`, and
  `MarketConditionSpecAPI` are allowed.

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
