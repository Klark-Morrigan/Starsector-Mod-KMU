# Cluster-name overlay (`render.labels`)

The faction names drawn across the political map, and the geometry that decides where each one
sits. An *independent overlay*: it is layered over whichever base view is live and is not part of
the production fills and borders, so it owns none of the territory draw packets.

Part of Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../../README.md) for project context.

## Index

- [Layout](#layout)
- [Placement: where a name sits](#placement-where-a-name-sits)
- [Rendering: the name and its overlay](#rendering-the-name-and-its-overlay)
- [What is not here](#what-is-not-here)

## Layout

- `render.labels` (this package) - the drawn name: `Label`, `LabelsBuilder`, `LabelRenderer`,
  and the shared `LabelFonts`.
- `render.labels.anchor` - the placement subsystem that decides where each name sits and paints
  its debug overlay.
- `render.labels.anchor.specifications` - the search's tuning surface, read once per rebuild:
  `LabelAnchorSpecification` and its component records (`AnchorSearch`, `LeanScoring`,
  `AnchorDiagnostics`, `NameFit`, `NameGroupStyle`).

## Placement: where a name sits

A cluster's label rides on an *anchor* - the line the name is laid along, chosen to fit inside the
national border and clear of the system icons. The placement subsystem lives in `anchor`:
`ClusterAnchorsBuilder` drives the fit; `ClusterAnchorPlacement` runs the pure geometric search;
`LabelSlantPreference` supplies the per-cluster lean; `ClusterLabelStyling` resolves each label's
colour and name. Its tuning is read into `LabelAnchorSpecification` (see `anchor.specifications`).
The anchor is computed once and shared by both consumers below, so a name and its debug dot never
disagree.

## Rendering: the name and its overlay

`LabelsBuilder` mints a `Label` per anchor and `LabelRenderer` draws them; `ClusterAnchorRenderer`
draws the diagnostic anchor overlay (the candidate axes, accepted and rejected) when that toggle is
on. Both read the anchors the placement produced rather than recomputing them.

## What is not here

The *colours and names* a label draws in come from `render.style` (`MapPalettes` and the shared
style decision), so a name matches the fill it labels by construction. The *fills and borders* this
overlay sits over are built and drawn elsewhere (`render.territories.TerritoryBuilder`,
`render.territories.TerritoryRenderer`); the anchor search clips against the same border trace the
fills use (`render.PoliticalBorderTrace`), so the name stays inside the outline the player sees.
