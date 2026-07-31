# Cluster-name overlay (`base.labels`)

The names drawn across a map layer's regions, and the geometry that decides where each one sits. An
*independent overlay*: it is layered over whichever base view is live and is not part of the
production fills and borders, so it owns none of the territory draw packets.

Nothing here knows who holds anything. A cluster arrives as a set of systems sharing an opaque
*owner*, and the two things a name needs beyond geometry - what it reads and what shade it
draws in - arrive as plain functions of that key, resolved by whichever layer knows what the key
means. So the same overlay serves names for factions, alliances, or anything a future layer
partitions the sector on.

Part of [the map-layer framework](../../README.md); see the
[mod README](../../../../../../../README.md) for project context.

## Index

- [Layout](#layout)
- [Placement: where a name sits](#placement-where-a-name-sits)
- [Rendering: the name and its overlay](#rendering-the-name-and-its-overlay)
- [What is not here](#what-is-not-here)

## Layout

- `base.labels` (this package) - the drawn name: `Label`, `LabelsBuilder`, `LabelRenderer`,
  and the shared `LabelFonts`.
- `base.labels.anchor` - the placement subsystem that decides where each name sits and paints
  its debug overlay, plus the `DiagnosticPalette` every diagnostic grades its stages by.
- `base.labels.anchor.specifications` - the search's tuning surface, read once per rebuild:
  `LabelAnchorSpecification` and its component records (`AnchorSearch`, `LeanScoring`,
  `AnchorDiagnostics`, and KMLib's `NameFitSpecification`).

## Placement: where a name sits

A cluster's label rides on an *anchor* - the line the name is laid along, chosen to fit inside the
cluster's border and clear of the system icons. [`ClusterAnchorPlacement`](anchor/ClusterAnchorPlacement.java)
runs the search: it sweeps candidate lines across the cluster, sizes each into the largest box the
injected name measurement fits, and keeps the highest-scoring one, with
[`LabelSlantPreference`](anchor/LabelSlantPreference.java) supplying the per-cluster lean the score
is taken against. Its tuning is read into `LabelAnchorSpecification` (see `anchor.specifications`).
The anchor is computed once and shared by both consumers below, so a name and its debug dot never
disagree.

The search takes each cluster's name measurement and colour as functions of its owner, keyed
off the key its members carry. That is the whole seam between a layer and this overlay: the layer
decides who a key is and what it looks like, the search decides where its name goes.

## Rendering: the name and its overlay

[`LabelsBuilder`](LabelsBuilder.java) mints a [`Label`](Label.java) per anchor and
[`LabelRenderer`](LabelRenderer.java) draws them;
[`ClusterAnchorRenderer`](anchor/ClusterAnchorRenderer.java) draws the diagnostic anchor overlay
(the candidate axes, accepted and rejected) when that toggle is on. Both read the anchors the
placement produced rather than recomputing them. Whether names draw at all is the caller's answer,
passed into the build - the placements are also built for the debug overlay alone, which mints no
name.

A `Label` owns a GL vertex buffer, so the builder disposes the standing list whenever it rebuilds -
which is why this overlay's text is minted here rather than fetched from KMLib's shared glyph cache,
whose runs are never disposed. When a rebuild happens at all is
[the caching notes](../../../../../../../docs/dev/caching.md).

## What is not here

The *names and colours* a label reads and draws in. Those belong to the layer, which resolves them
against its own vocabulary before the search runs; the political map's half is
`politicalmap.base.render.labels.anchor`. The *fills and borders* this overlay sits over are built
and drawn by whichever layer painted them; the anchor search clips against the same border trace
those fills use, so a name stays inside the outline the player sees.
