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
A faction,
a group of factions,
or whatever else a layer keys a place by -
the key is opaque here,
and the layer above says what it means.
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
- [The arrow](#the-arrow)
- [The words it keeps](#the-words-it-keeps)
- [Where each part lives](#where-each-part-lives)

## What belongs here

Two questions decide whether a part is the tier's,
and a part has to pass both.

The first is whether it names a mechanic.
Weighing markets,
or a relation between factions -
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
| who owns each system, which owned systems draw as a fill exception, what stands where and where the spotlit owner lives, for a whole rebuild | an `OwnerSource`, resolved with the reading through `OwnerPaintedView.resolveViewReading` and handed the rebuild's `SectorWalk` |
| who owns one marked system, whether anything stands in it and whether the spotlit owner does, for an incremental refresh | the `SystemOwnerResolve` that same source opens over the batch's own walk |
| how a cell's presence band is counted | the same source's band planner |
| each owner's shades, name, crest, recede and category, and the shades an unowned or receded cell is derived from | the `OwnerReading` resolved beside the source |
| which systems seed a cell | a `CellSeedRule`, handed to `OwnerMapLayerRenderer.createForLiveScreen`; `SEED_DRAWN_SYSTEMS` for the systems the map draws |
| which categories cells divide into, which one is full strength, and which one an unowned cell falls to | `OwnerCategories`, through `OwnerPaintedView.resolveCategories` |
| whether the cursor is answered at all | the substrate's `MapLayerHoverGates`, or the tier's `SharedOwnerMapHoverGates` over the shared owner-map switches |
| what the picker's hovered row lights | `OwnerMapPreviewHighlight`, or the tier's `SpotlightPreviewHighlightRenderer` for a layer offering the spotlight picker |
| which side of the nebulae each sub-layer paints | `OwnerMapBandLayout`, supplied per frame |
| which view paints | `OwnerPaintedView` |
| which views it offers, and which one each screen picked | a `MapLayerViewRegistry` the layer builds over a key of its own |
| the per-save choices its body offers, and where they are stored | an `OwnerMapBodyPreferences` the layer builds over keys of its own |
| how a view's own backdrop recedes | `OwnerPaintedView.resolveViewRecedeAdjustment` |

Only the last has a default here,
and it names no layer's answer:
a view recedes nothing of its own accord unless it says so.
Any other default would name one layer's answer in front of every layer,
including the ones it does not describe.

**The tier reads no colony.**
A rebuild opens one `SectorWalk` -
the sector's systems and what each holds,
walked once,
beside the visibility rules the cells were cut under -
and hands it to the layer's source.
Whatever the source opens over it is the source's:
the layers painting holders open a colony pass,
a relay layer would read nothing but which systems carry a relay.
The cut, the source and the band bake all read that one walk,
so a rebuild still walks the sector once.
A batch opens a walk of its own and asks the source the standing build was resolved by,
so a marked system lands the owner its neighbours were painted under.

The reading and the source come from one call
because they have to agree about what an owner is:
a view folding factions into groups reads that fold live,
and a source folding under one sampling of it while the reading named owners under another
would paint a fold the holding never made.

**Which systems seed a cell is the layer's to state** too.
The cut asks a `CellSeedRule` per system,
and a layer seeding exactly what the map draws names `SEED_DRAWN_SYSTEMS`
rather than inheriting another layer's set without a line saying so.

**The tier holds an owner key and asks the layer everything else about an owner.**
Fusing, bordering and labelling never ask what an owner ID means,
and nor does anything else here:
the colours an owner paints in,
the name it reads by,
the crest its picker row carries,
how far it recedes and which category it draws in
are the owner reading's answers,
and so are the neutral shades an unowned cell paints in and a receded one sinks toward.
One reading per rebuild rather than per question,
because an answer may rest on a live source -
which factions stand together, say -
and every shade, name and category one rebuild paints has to come off one sampling of it.
The build retains it beside the source for every stage after,
the label fit and the incremental refresh included.

The categories are declared the same way.
The tier states the roles a category plays -
the full-strength one a desaturated owner's fill opacity is held at,
and the split of unowned cells into settled and empty -
and indexes the theme by whatever category the layer answers,
never naming one itself.

The layers painting holders answer all of it through `HolderPaintedView`,
which samples their grouping once and builds a `HolderOwnerReading` and a `HolderOwnerSource` over it,
and through `HolderCategories`,
which divides cells into the four `OwnerMapCategory` values.
A layer painting owners that are not factions brings its own,
and the build then never asks the sector for a faction or a colony at all.

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
because a layer built from a spec depends on it:
a spec that wired a process-wide holder would hand every layer built from it the same one.
`OwnerMapStaticStateIntegrationTests` holds the rule,
failing on any static field under this package that is not final.

## The arrow

This tier may import `kmu.maplayers.base` and nothing else under `kmu.maplayers`,
and nothing under `kmu.mods`:
an optional mod reaches it only through a seam a layer fills.
`base` is closed to it in turn,
so the substrate cannot come to depend on one assembly of itself.
The gate is `enforcePackageLayering`,
whose edges are declared with the mod's others
in [gradle/package-layering.gradle](../../../../../../gradle/package-layering.gradle).

The edge closing this tier to every layer is what makes it a tier rather than a shelf:
it proves the assembly compiles with no layer on the compile path,
which is the whole promise a second painting layer rests on.

## The words it keeps

The tier speaks the substrate's words one level up -
owner,
cluster,
cluster group,
cluster border -
and `enforcePackageVocabulary` holds it to them;
the list and the reason for it are in [the map layers README](../README.md#the-vocabulary).
*Bloc* is the one word it keeps that the substrate may not say,
since this is where owners are grouped,
ranked and picked.

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
| *(top level)* | the view seam (`OwnerPaintedView`, `MapLayerViewRegistry`, `ViewReading`), the picker assembly behind its defaults (`BlocPickerAssembly`), the stale-spotlight heal (`FilterSelectionHeal`) and the preferences a rebuild samples (`ContentInputs`) |
| `holding` | one rebuild's reading of a sector: `HolderPass`, the grouping it folds factions by, the colony rules it reads under, the contest sides a `BlocAffiliation` places blocs on, and `HolderOwnerReading`, the owner reading of the layers painting holders |
| [`owners`](owners/README.md) | the two seams a layer answers its owners through - where they come from (`OwnerSource`, its per-system `SystemOwnerResolve`, the walk it is handed and the `ResolvedOwners` it answers) and how each looks (`OwnerReading`) - beside the owner of a system (`SystemOwner`), the two shades it paints in (`OwnerPalette`), and the spotlight's own key (`SpotlitBlocs`) |
| [`owners/holders`](owners/holders/README.md) | the holder source over a colony pass, the view shape that assembles it, the holding rules a layer painting holders states, and the three fill states |
| `picker` | the bloc picker model: its rows (`SelectableBloc`, `RankedBloc`), the metrics they carry, the sort modes ranking them, a bloc's standing with the player, and the presence index a preview lights |
| `preferences` | the per-save body choices - name format, uninhabited outline, recede - and `OwnerMapBodyPreferences`, the set a layer builds over keys of its own |
| [`render/clusters`](render/clusters/README.md) | cells into coloured, bordered clusters with their seams and split fills |
| [`render/style`](render/style/README.md) | player settings into each element's colours, widths and opacities, and the categories a layer declares (`OwnerCategories`, with the holder layers' `HolderCategories`) |
| `render/labels` | what a cluster's name reads and what shade it draws in, handed to the substrate's overlay |
| [`render/ribbon`](render/ribbon/README.md) | a planned band as triangles inside its cell's ring, and the draw |
| [`ribbon`](ribbon/README.md) | what a band is made of before any geometry: the runs, their order and the count itself |
| `render` | the cache, the compositor stacking the sub-layers in their band order, the incremental refresh, and `OwnerMapLayerRenderer`, which hands the cache and the compositor to [the substrate's frame sequence](../base/render/README.md#the-frame-sequence) |
| `render/hover` | what the cursor is over on this map, the seam a layer answers the picker preview through, and the tier's shared answers to it and to the substrate's cursor switches |
| `tooltip` | the colony vocabulary and the line shapes every layer's hover box is written in |
| `sidebar` | the body controls a layer built on this tier offers, and the memo behind its picker |

What is underneath all of it -
[cell geometry](../base/geometry/README.md),
the [cluster-name overlay](../base/labels/README.md),
the [theme records](../base/theme/README.md),
`base/visibility`,
`base/hover` and the [hover box](../base/tooltip/README.md) -
is the substrate's,
and works on the opaque owner this tier keys by.
