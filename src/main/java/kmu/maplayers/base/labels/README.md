# Cluster-name overlay (`base.labels`)

The names drawn across a map layer's clusters, and the geometry that decides where each one sits. An
*independent overlay*: it is layered over whichever base view is live and is not part of the
production fills and borders, so it owns none of the cluster draw packets.

Nothing here knows who holds anything. A cluster arrives as a set of systems sharing an opaque
*owner*, and the two things a name needs beyond geometry - what it reads and what shade it
draws in - arrive together as [`ClusterLabelResolvers`](anchor/ClusterLabelResolvers.java), a pair
of plain functions of that key resolved by whichever layer knows what the key means. So the same
overlay serves names for factions, alliances, or anything a future layer partitions the sector on.

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

Each placement also names the cluster it was fitted to, as a
[`ClusterIdentity`](anchor/ClusterIdentity.java): the owner key plus the exact member set, compared
order-free. Nothing drawn reads it - it is what lets a standing placement be recognised as still
belonging to the cluster a later rebuild produced, where a split or a merge matches nothing and
re-fits by construction.

A whole pass carries the other half of that question as an
[`AnchorFitFingerprint`](anchor/AnchorFitFingerprint.java): the tuning it ran with and the geometry
revision it ran against, the two things that invalidate every placement at once rather than any
cluster's in particular. The layer's rebuild is what reads the live tuning, so it is what labels
the list, on every path - including the ones that fit nothing, since an unlabelled list is
indistinguishable from one made under rules that still hold.

The list and that label are one value, [`StandingClusterAnchors`](anchor/StandingClusterAnchors.java),
which the layer owns across rebuilds and hands to the rebuild whole: the rebuild reads the previous
pass off it and leaves its own in it. Nothing can move one half without the other, which is the
state every reuse decision below assumes cannot arise - a list that outran its label offers a later
rebuild placements it has no business carrying over.

Together those two make a rebuild **partial**. The standing placements go back into
`computeClusterAnchors` filed under the cluster each names, and a matched one is carried over
instead of its cluster being searched again - so it costs no candidates and no band fits, which is
the whole saving. Two things are re-checked on the way through, both of them per-cluster inputs
that are not geometry: the shade is re-resolved (it is no input to the fit, so a bloc that only
receded keeps its box and takes the new colour), and the wrap is compared against the name the box
was measured for, so a renamed bloc re-fits rather than drawing a box cut for the old name. A
collapsed placement fitted no box and so recorded no name to compare, and is always searched again.
What may be offered at all is the caller's decision, taken on the fingerprint: a mismatch offers
nothing and the rebuild is total.

What a layer has to cut before any name can be placed is one value as well:
[`ClusterPartition`](anchor/ClusterPartition.java) - the contiguous clusters, plus the cell edges,
sites and grouping they were cut from. The sweep reads all four against each other on every
cluster, so a partition assembled from two passes would fit a name inside a border traced from
cells that no longer group that way; as one value it cannot be handed over in halves.

The search takes each cluster's name measurement and colour as one `ClusterLabelResolvers`, keyed
off the key its members carry. Those two values are the whole seam between a layer and this
overlay: the layer decides what it partitions on and who a key is, the search decides where its
name goes. Both resolvers are
resolved in one step into a [`ClusterLabelSubject`](anchor/ClusterLabelSubject.java) bound to the
cluster's identity, so neither can be taken for a different owner than the other - and restyling a
carried placement is a substitution rather than three parallel values to keep in step.

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
