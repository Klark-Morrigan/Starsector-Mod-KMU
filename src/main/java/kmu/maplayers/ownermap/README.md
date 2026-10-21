# Owner-painted map assembly (`ownermap`)

One assembly of the map substrate's primitives:
a sector's cells keyed by an owner,
fused into bordered shapes,
filled,
named,
banded and styled.
A layer that paints the map by *who a place belongs to* draws through this tier
rather than composing the primitives itself.

The tier holds no opinion about what an owner is.
Whatever a layer keys a place by,
the key is opaque here,
and the layer above says what it means.
The one kind of owner the mod paints,
a holder,
is answered from inside the tier but outside its core -
see [where each part lives](#where-each-part-lives).
It sits between the substrate and a layer because neither end could hold it:
not every layer paints by an owner,
so the assembly cannot be the substrate;
and leaving it inside one layer makes that layer the host of a pipeline it does not own.

Part of [map layers](../README.md);
see the [mod README](../../../../../../README.md) for project context.

## Index

- [What belongs here](#what-belongs-here)
- [What a layer supplies](#what-a-layer-supplies)
- [What a layer holds](#what-a-layer-holds)
- [The stages it wires](#the-stages-it-wires)
- [The arrow](#the-arrow)
- [The words it keeps](#the-words-it-keeps)
- [Where each part lives](#where-each-part-lives)

## What belongs here

Two questions decide whether a part is the tier's,
and a part has to pass both.

The first is whether it names a mechanic.
Weighing markets,
or a relation between owners -
each decides *who* paints,
and each is one layer's rule.
A part that names one belongs to that layer,
however generic the rest of it looks.

The second is the one a reader will not reconstruct from the code:
**whether a second owner-painted layer would supply it for itself.**
Plenty of a layer's parts name no mechanic and are still that layer's -
its tab,
its views and their roster,
its staleness baselines,
its band layout.
Each is one layer's answer to a question the tier asks,
so the question is kept here and the answer goes with the layer.
A part left here because it names no mechanic would make the tier the host of one layer's choices,
and every other layer would inherit them.
Where one answer serves every layer that wants it,
the tier offers it beside the seam -
the shared hover switches,
the spotlight picker's preview -
and a layer still picks it where it composes itself.

## What a layer supplies

Every place the tier needs a layer's answer is a seam the layer fills where it composes itself:

| The tier asks | Through |
| --- | --- |
| who owns each system, which owned systems draw hatched or unfilled, what stands where and where the spotlit owner lives, for a whole rebuild | an `OwnerSource`, resolved with the reading through `OwnerPaintedView.resolveViewReading` and handed the rebuild's `SectorWalk` |
| who owns one marked system, whether anything stands in it and whether the spotlit owner does, for an incremental refresh | the `SystemOwnerResolve` that same source opens over the batch's own walk |
| how a cell's presence band is counted | the same source's band planner |
| each owner's shades, name, crest, recede and category, the shade an unowned cell paints in and the pair a receded one sinks toward | the `OwnerReading` resolved beside the source |
| which systems seed a cell | a `CellSeedRule`, handed to `OwnerMapLayerRenderer.createForLiveScreen`; `SEED_DRAWN_SYSTEMS` for the systems the map draws |
| which categories cells divide into, which one is full strength, and which one an unowned cell falls to | `OwnerCategories`, through `OwnerPaintedView.resolveCategories` |
| whether the cursor is answered at all | the substrate's `MapLayerHoverGates`, or the tier's `SharedOwnerMapHoverGates` over the shared owner-map switches |
| what the picker's hovered row lights | `OwnerMapPreviewHighlight`, or the tier's `SpotlightPreviewHighlightRenderer` for a layer offering the spotlight picker |
| which side of the nebulae each sub-layer paints | `OwnerMapBandLayout`, supplied per frame |
| which view paints | `OwnerPaintedView` |
| which views it offers, and which one each screen picked | a `MapLayerViewRegistry` the layer builds over a key of its own |
| the per-save choices its body offers, and where they are stored | an `OwnerMapBodyPreferences` the layer builds over keys of its own |
| how a view's own backdrop recedes | `OwnerPaintedView.resolveViewRecedeAdjustment` |

Only the last has a default here:
a view recedes nothing of its own accord unless it says so.

**The tier holds an owner key and asks the layer everything else about it.**
Fusing, bordering and labelling never ask what an owner ID means.
[The owner seams](owners/README.md) cover the walk a rebuild hands the source,
the per-system answer a batch asks for,
and why the reading and the source come from one call.
The categories are declared the same way:
the tier states the roles a category plays
and indexes the theme by whatever category the layer answers,
as [`render/style`](render/style/README.md) describes.

The layers painting holders answer all of it through [`owners/holders`](owners/holders/README.md).
A layer painting any other kind of owner brings its own answers,
and nothing on its rebuild's path reads a holder at all.

## What a layer holds

**This tier keeps no static mutable state.**
Everything a layer's map is made of is the layer's own:

- its **view registry** -
  a `MapLayerViewRegistry` the layer builds over its roster and a sector-memory key it names,
  so two layers' picks part by key the way two screens' picks part by scope;
- its **body preferences** -
  the name format,
  the uninhabited outline and the filter recede,
  each stored under a key the layer names;
- its **renderer and the cache behind it**,
  and its **picker memo** -
  held by each sector's machinery under the layer's ID,
  so two layers on one sector draw through two of each.

A second owner-painted layer standing beside the first therefore shares none of it:
a pick on one radio,
a choice on one checkbox,
a cut in one cache,
a memoised picker in one memo -
none of it reaches the other.

The rule is written here rather than left as a property of the current code
because a layer added beside the first depends on it:
process-wide state would hand every layer the same choices.
`OwnerMapStaticStateIntegrationTests` holds the rule,
failing on any static field under this package that is not final.

## The stages it wires

The cache and the rebuild it drives are the **default wiring** of the tier's stages,
not the only way to reach them.
Each stage stands up on the values it is handed and reads nothing of the build those came from,
so a layer holding the same values gets the same answer without the pipeline:

| Stage | Stands up on |
| --- | --- |
| the style resolvers (`MapPaints`) | the `MapStyling`, the `OwnerReading` and the sampled `ContentInputs` |
| the cell builder (`PaintedCellBuilder`) | the `MapPaints` and the `SystemOccupancy` |
| the cluster-group builder (`ClusterGroupBuilder`) | the `MapPaints`, the cells' edges, their grouping under the owners, and the `SystemFillStates` |
| the border trace and smoothing | the substrate's `ClusterBorderTrace` and `BorderSmoothing`, over rings and a style |
| the label fit (`ClusterAnchorsBuilder`) | a `ClusterLabelStylingSnapshot` over the build's `MapPaints`, the cells and the standing placements |
| the band planner | the source's `SystemRibbonPlanner`, over a `SectorWalk` and the `RibbonPlanRules` |

Stating the inputs is what keeps an incremental re-shape identical to a full build.
The full build and a batch hand a stage the same values,
through the factories on the built clusters,
rather than the stage reading them off the model it fills.

## The arrow

This tier imports `kmu.maplayers.base` and no layer:
the gate closes it to `politicalmap` and to `kmu.mods`,
and a new layer's tree needs an edge of its own.
An optional mod reaches it only through a seam a layer fills.
`base` is closed to it in turn,
so the substrate cannot come to depend on one assembly of itself.
The gate is `enforcePackageLayering`,
whose edges are declared with the mod's others in [gradle/package-layering.gradle](../../../../../../gradle/package-layering.gradle).

The edge closing this tier to every layer is what makes it a tier rather than a shelf:
it proves the assembly compiles with no layer on the compile path,
which is the whole promise a second painting layer rests on.

Inside the tier,
the core may not import `owners/holders`,
the one subtree it is closed to.
The holder answers fill the core's seams from inside the tier
so both layers painting holders can reach them,
and a core that imported one would know what an owner is.
The edge leaves the subtree out with the gate's `except:`,
so a core package added later is closed to it without a line of its own.

## The words it keeps

The tier speaks the substrate's words one level up -
owner,
cluster,
cluster group,
cluster border -
and `enforcePackageVocabulary` holds it to them;
the list and the reason for it are in [the map layers README](../README.md#the-vocabulary).

The core is also closed to the words that say what a holder is and what it is counted by:
*holder*,
*faction*,
*colony*,
*market*,
*bloc*
and *alliance*.
They are the subject of `owners/holders` and nothing else here,
so the gate leaves that subtree out
and the core says *owner*.
What a presence band counts is a *stake*.

Nothing in the tier names a layer built on it.
The frozen save keys a layer's choices live under are that layer's,
and it hands them over;
the knobs the tier reads come through `KmuOwnerMap*Settings`;
and the strings it labels its controls with are `OWNER_MAP_*` keys.

The knobs are **one shared set** for every owner-painted layer,
and that is a decision rather than an accident:
a knob split per layer before a layer needs its own is a guess at what that layer needs.
A knob a layer does need its own of is split off behind a seam that layer fills,
and the rest stay shared.
Their LunaLib field IDs keep the spelling they shipped with,
since that is what a player's stored setting is keyed by.

## Where each part lives

| Package | What is in it |
| --- | --- |
| *(top level)* | the view seam (`OwnerPaintedView`, `MapLayerViewRegistry`, `ViewReading`), the stale-spotlight heal (`FilterSelectionHeal`) and the preferences a rebuild samples (`ContentInputs`) |
| [`owners`](owners/README.md) | the seams a layer answers its owners through - where they come from (`OwnerSource`, its per-system `SystemOwnerResolve`, the walk it is handed and the `ResolvedOwners` it answers), how each looks (`OwnerReading`) and who stands together in a contest (`OwnerAffiliation`) - beside the owner of a system (`SystemOwner`) and the spotlight's own key (`SpotlitOwners`) |
| [`owners/holders`](owners/holders/README.md) | the answers of the layers painting holders, outside the core: what every holder shares (`base`, with its picker in `base.picker`), one holder as one owner (`factions`), and a group of holders as one owner (`alliances`) |
| `picker` | the owner picker model: its rows (`SelectableOwner`, `RankedOwner`), the metrics they carry, the sort modes ranking them, and the presence index a preview lights |
| `preferences` | the per-save body choices - name format, uninhabited outline, recede - and `OwnerMapBodyPreferences`, the set a layer builds over keys of its own |
| [`render/clusters`](render/clusters/README.md) | cells into coloured, bordered clusters with their seams and split fills |
| [`render/style`](render/style/README.md) | player settings into each element's colours, widths and opacities, resolved per build through `MapPaints`, and the categories a layer declares (`OwnerCategories`) |
| `render/labels` | what a cluster's name reads and what shade it draws in, handed to the substrate's overlay |
| [`render/ribbon`](render/ribbon/README.md) | a planned band as triangles inside its cell's ring, and the draw |
| [`ribbon`](ribbon/README.md) | what a band is made of before any geometry: the runs, their order and the count itself |
| `render` | the cache, the compositor stacking the sub-layers in their band order, the incremental refresh, and `OwnerMapLayerRenderer`, which hands the cache and the compositor to [the substrate's frame sequence](../base/render/README.md#the-frame-sequence) |
| `render/hover` | what the cursor is over on this map, the seam a layer answers the picker preview through, and the tier's shared answers to it and to the substrate's cursor switches |
| `sidebar` | the body controls a layer built on this tier offers, and the memo behind its picker |

What is underneath all of it -
[cell geometry](../base/geometry/README.md),
the [cluster-name overlay](../base/labels/README.md),
the [theme records](../base/theme/README.md),
`base/visibility`,
`base/hover` and the [hover box](../base/tooltip/README.md) -
is the substrate's,
and works on the opaque owner this tier keys by.
