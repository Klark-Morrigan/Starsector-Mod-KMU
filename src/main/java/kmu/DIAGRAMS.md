# KMU Diagram Style

Diagrams are written in [D2](https://d2lang.com). Each `.d2` file is
self-contained - D2 spread imports (`...@file`) cause a compiler panic in
the current installed version and are not used.

## Index

- [Palette](#palette)
- [Conventions](#conventions)
- [Known Limitations](#known-limitations)
- [Canonical Classes Block](#canonical-classes-block)

## Palette

| Class | Meaning | Fill | Stroke | Font |
| --- | --- | --- | --- | --- |
| `note` | Contextual architecture note | `#fff7d6` | `#b58b00` | `#242424` |
| `orchestration` | Entry point, UI coordinator, workflow owner | `#e8f1ff` | `#2f67b1` | `#0f2747` |
| `core` | Core/domain/model behavior | `#e9f8ef` | `#2b8a4b` | `#10351d` |
| `value` | Value object, data carrier, context object | `#eef8ff` | `#3b82a0` | `#113247` |
| `port` | Interface or boundary contract | `#fff0e6` | `#c76a24` | `#4a2108` |
| `adapter` | Starsector or external adapter | `#f3ecff` | `#7b55b7` | `#2d1c45` |
| `external` | Starsector API or other external API | `#f3f4f6` | `#6b7280` | `#1f2937` |
| `shared` | Shared KMU utility | `#ffeaf4` | `#c75186` | `#4a1830` |

## Conventions

- Top-level `direction: right` for left-to-right flow.
- Each file includes only the classes it uses from the canonical block below.
- Legend uses `grid-rows: 1` for a flat single-row layout.
- Containers use `direction: down` to stack their members vertically.
- Container display labels differ from IDs where needed:
  `Entry: "Entry / Wiring" { ... }`.
- `external` nodes carry `stroke-dash: 5` in their class style - no extra
  markup needed at the use site.
- Dashed arrows represent `implements` / structural relationships:
  ```d2
  Adapter.Node -> Port.Node: "implements" {
    style: { stroke-dash: 5 }
  }
  ```
- Label arrows with the reason for the dependency or call.
- Put architecture notes as rendered `note`-class nodes, not D2 comments.
- Add a `# Style conventions: see DIAGRAMS.md` comment at the top of each
  `.d2` file.

## Known Limitations

- **No spread imports**: `...@file` inside a `classes` block causes a nil
  pointer dereference panic in the installed D2 version. Keep all class
  definitions inline in each file.
- **No theme-overrides vars block**: `vars: { d2-config: { theme-overrides:
  ... } }` causes an "Unable to convert" compile error. Use the D2 VS Code
  extension theme or CLI `--theme` flag instead.

## Canonical Classes Block

Copy and trim to only the classes used in the diagram.

```d2
classes: {
  note: {
    style: {
      fill: "#fff7d6"
      stroke: "#b58b00"
      font-color: "#242424"
    }
  }
  orchestration: {
    style: {
      fill: "#e8f1ff"
      stroke: "#2f67b1"
      font-color: "#0f2747"
    }
  }
  core: {
    style: {
      fill: "#e9f8ef"
      stroke: "#2b8a4b"
      font-color: "#10351d"
    }
  }
  value: {
    style: {
      fill: "#eef8ff"
      stroke: "#3b82a0"
      font-color: "#113247"
    }
  }
  port: {
    style: {
      fill: "#fff0e6"
      stroke: "#c76a24"
      font-color: "#4a2108"
    }
  }
  adapter: {
    style: {
      fill: "#f3ecff"
      stroke: "#7b55b7"
      font-color: "#2d1c45"
    }
  }
  external: {
    style: {
      fill: "#f3f4f6"
      stroke: "#6b7280"
      font-color: "#1f2937"
      stroke-dash: 5
    }
  }
  shared: {
    style: {
      fill: "#ffeaf4"
      stroke: "#c75186"
      font-color: "#4a1830"
    }
  }
}
```
