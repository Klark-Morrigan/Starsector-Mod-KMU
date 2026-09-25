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
| each system's owner, for a whole rebuild | `HolderProvider`, one per view |
| one system's owner, for an incremental refresh | `SystemHolderResolveSource` |
| whether the cursor is answered at all | `OwnerMapHoverGates`, or the tier's `SharedOwnerMapHoverGates` over the shared owner-map switches |
| what the picker's hovered row lights | `OwnerMapPreviewHighlight`, or the tier's `SpotlightPreviewHighlightRenderer` for a layer offering the spotlight picker |
| which side of the nebulae each sub-layer paints | `OwnerMapBandLayout`, supplied per frame |
| which view paints, and what it groups by | `OwnerPaintedView` |
| which blocs stand together in a contest | `OwnerPaintedView.resolveContestGrouping` |
| which views it offers, and which one each screen picked | a `MapLayerViewRegistry` the layer builds over a key of its own |
| the per-save choices its body offers, and where they are stored | an `OwnerMapBodyPreferences` the layer builds over keys of its own |
| how a view's own backdrop recedes | `OwnerPaintedView.resolveViewRecedeAdjustment` |

Only the last has a default here,
and it names no layer's answer:
a view recedes nothing of its own accord unless it says so.
Any other default would name one layer's answer in front of every layer,
including the ones it does not describe.

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
The gate is `OwnerMapStaticStateIntegrationTest`,
which fails on any static field under this package that is not final.

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
| *(top level)* | the view seam (`OwnerPaintedView`, `MapLayerViewRegistry`, `ViewGrouping`), the picker assembly behind its defaults (`BlocPickerAssembly`), the stale-spotlight heal (`FilterSelectionHeal`) and the preferences a rebuild samples (`ContentInputs`) |
| `holding` | one rebuild's reading of a sector: `HolderPass`, the grouping it folds factions by, the colony rules it reads under, and the contest sides a `BlocAffiliation` places blocs on |
| `owners` | what is read off a holding: the owner of a system (`SystemOwner`) and the spotlight's own key (`SpotlitBlocs`) |
| [`owners/holders`](owners/holders/README.md) | the ownership seam every view resolves through, the per-system resolve a refresh uses, and the three fill states |
| `picker` | the bloc picker model: its rows (`SelectableBloc`, `RankedBloc`), the metrics they carry, the sort modes ranking them, a bloc's standing with the player, and the presence index a preview lights |
| `preferences` | the per-save body choices - name format, uninhabited outline, recede - and `OwnerMapBodyPreferences`, the set a layer builds over keys of its own |
| [`render/clusters`](render/clusters/README.md) | cells into coloured, bordered clusters with their seams and split fills |
| [`render/style`](render/style/README.md) | player settings into each element's colours, widths and opacities |
| `render/labels` | what a cluster's name reads and what shade it draws in, handed to the substrate's overlay |
| [`render/ribbon`](render/ribbon/README.md) | a planned band as triangles inside its cell's ring, and the draw |
| [`ribbon`](ribbon/README.md) | what a band is made of before any geometry: the runs, their order and the count itself |
| `render` | the cache, the renderer and the frame sequence over all of it, the incremental refresh, and the band order the sub-layers stack in |
| `render/hover` | what the cursor is over on this map, the two seams a layer answers the cursor and the picker preview through, and the tier's shared answers to both |
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
