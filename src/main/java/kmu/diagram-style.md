# KMU Mermaid Diagram Style

Mermaid does not support importing shared style definitions into standalone
`.mmd` files, so each diagram remains self-contained. Keep the palette below
consistent when editing package diagrams.

## Palette

| Class | Meaning | Color |
| --- | --- | --- |
| `note` | Contextual architecture note | Yellow |
| `legend` | Diagram legend | White |
| `orchestration` | Entry point, UI coordinator, workflow owner | Blue |
| `core` | Core/domain/model behavior | Green |
| `value` | Value object, data carrier, context object | Cyan |
| `port` | Interface or boundary contract | Orange |
| `adapter` | Starsector or external adapter | Purple |
| `external` | Starsector API or other external API | Gray |
| `shared` | Shared KMU utility | Pink |

## Canonical Class Definitions

```mermaid
classDef note fill:#fff7d6,stroke:#b58b00,color:#242424
classDef legend fill:#ffffff,stroke:#9ca3af,color:#242424
classDef orchestration fill:#e8f1ff,stroke:#2f67b1,color:#0f2747
classDef core fill:#e9f8ef,stroke:#2b8a4b,color:#10351d
classDef value fill:#eef8ff,stroke:#3b82a0,color:#113247
classDef port fill:#fff0e6,stroke:#c76a24,color:#4a2108
classDef adapter fill:#f3ecff,stroke:#7b55b7,color:#2d1c45
classDef external fill:#f3f4f6,stroke:#6b7280,color:#1f2937
classDef shared fill:#ffeaf4,stroke:#c75186,color:#4a1830
```
