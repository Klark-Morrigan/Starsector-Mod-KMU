# Political map (`politicalmap`)

An overlay that colours the sector by who controls it. Each bloc's territory shows as a filled,
bordered, named cluster over the campaign map, on the full map screen and on the intel screen's
map preview alike.

The political map is one of the layers the tab strip offers; picking its tab opens a
view-selector radio in the body below. The radio changes *what* "controls" means. It does not
change how the map is drawn.

Part of [map layers](../README.md); see the
[mod README](../../../../../../README.md) for project context.

## Index

- [What the player sees](#what-the-player-sees)
- [The views](#the-views)
- [Claim extensions](#claim-extensions)
- [How a view drives the pipeline](#how-a-view-drives-the-pipeline)
- [Where each part lives](#where-each-part-lives)
- [When the map is rebuilt](#when-the-map-is-rebuilt)

## What the player sees

For each controlling bloc the overlay draws four things:

- a coloured cluster,
- a border around it,
- faint seams inside it,
- the bloc's name across it.

The tab body picks the view by radio, and a filter picker below it can spotlight one bloc. When a
bloc is spotlighted, the rest fade into a muted background. The radio always lists the views in
the same order: **Factions**, then **Alliances** (only with Nexerelin), then **Claims**.

The controls read and write one shared set of values, so the two screens the overlay draws on
always agree on what is painted; only which tab each screen has lit is its own. The box those
controls sit in - where it anchors on each screen, and how it folds away - is
[the sidebar](../base/sidebar/README.md).

## The views

A view is a small set of rules on top of one shared draw pipeline. Each view answers three
questions, and the pipeline paints the answer without knowing which view asked:

- How do factions group into blocs?
- Which systems does each bloc paint?
- How is each bloc styled and named?

| View | Groups by | Paints | Spotlight targets | Needs |
| --- | --- | --- | --- | --- |
| **Factions** | each faction on its own | held territory, per faction | any faction | always (default) |
| **Alliances** | allied factions fused per alliance | held territory, alliances as one bloc | alliances only | Nexerelin |
| **Claims** | each claiming faction on its own | claimed systems, per faction | none | always |

Notes on each:

- **Factions.** The plain case. Every faction is its own bloc. Only genuine independent space
  fades to the muted style. A bloc is named after its faction.
- **Alliances.** Allied factions merge into one coloured, named cluster per alliance. Unaligned
  factions keep their own border and name. Two toggles fade a non-allied faction: *Mute* dims it,
  *Desaturate* makes it read as backdrop. With both off, a lone faction looks exactly as
  it does on the Factions view.
- **Claims.** Shows the vanilla "system claimed by faction" mechanic - the same claim the
  colony-survey panel warns about. Every claimed system is painted solid in its claimant's colours.
  There is no alliance grouping here, and no spotlight. Hovering a system explains its claim: who
  holds it, who contests it, who is present but can never claim it, and whether the hold was won on
  market strength or imposed by decree.

## Claim extensions

The Factions and Alliances views do not only paint held cells. A system a faction *claims* but
does not *hold* joins that faction's territory drawn empty: inside the border and under the
faction's name, but with no fill. The Claims view instead paints those same claims solid. Why a
claim resolves this way is [ownership resolution](base/politics/holders/README.md); how the empty
fill is drawn is [territory fills and borders](base/render/territories/README.md).

## How a view drives the pipeline

Every view resolves ownership through one seam. The pipeline reads who-paints-what from that one
source, and never branches on which view is active.

```mermaid
flowchart TD
    V([Selected view]) --> G[Grouping:<br/>how factions form blocs]
    V --> O[Ownership source:<br/>which systems each bloc paints]
    G --> PIPE[Shared draw pipeline]
    O --> PIPE
    PIPE --> S[Shape cells into<br/>bordered territories]
    S --> Fi[Split the fill:<br/>solid / hatched / unfilled]
    Fi --> L[Overlay bloc names]
    L --> MAP([Coloured map])
```

What changes between views is only the two inputs, the grouping and the ownership source; from the
seam on, every view shapes, fills, and labels identically. The sources themselves and the three
fill states are [ownership resolution](base/politics/holders/README.md).

## Where each part lives

- **[Ownership resolution](base/politics/holders/README.md)** - the per-view ownership seam, the
  three sources, the three fill states, and the claim mechanic.
- **[Territory fills and borders](base/render/territories/README.md)** - how cells become each
  bloc's coloured cluster, border, seams, and split fill.
- **[Render style layer](base/render/style/README.md)** - the four categories this map divides the
  cells into, and how player settings become each territory's colours, widths, and opacities.
- **`base/render/labels/anchor`** - what a cluster's name reads and what shade it draws in: the
  active view's name for the bloc, and the outer border its group inherits. Both are resolved here
  and handed to the framework's overlay, which places and draws them.

Everything above is drawn over the systems the framework's `base/visibility` admits, shaped out of
[cell geometry](../base/geometry/README.md), styled against the
[theme records](../base/theme/README.md), named by the
[cluster-name overlay](../base/labels/README.md), and hovered through `base/hover` - all of which
belong to the framework rather than to this layer: they work on an opaque owner, and the views
decide that the key names a bloc.

The rest of `base` carries the supporting parts: `politics` (grouping and the held/claim resolvers),
`refresh` (the economy-event listeners, `PoliticalMapStalenessSource` - what this layer counts
as a change the engine fired no event for, answered into the framework's poll - and
`PoliticalMapRefreshSignal`, the coarse changes only this layer can raise on the shared board,
alliance membership being the one),
`render/hover` (`PoliticalMapHoverHighlightSource` - this layer's answers about the cell under
the cursor: the owner's border loops it might sit inside and the shade its fill draws in, over
the frame's painted shapes it hands the framework unchanged;
and `PoliticalMapHoverGates` - whether this layer answers the cursor at all, its own two switches
ANDed with the framework's, plus whether either kind of feedback still needs the cursor read),
`tooltip` (what this layer says about the hovered system, each view injecting the explanation of the
mechanic its own fills were painted by into the framework's hover box: `SystemDominationTooltip` -
the ranked standings behind a faction or alliance fill - and `SystemClaimTooltip` - the scored claim
contest behind a claims fill, its claimant over the rivals who could have taken the system and the
factions present that never could - plus what both are written from: `FactionTooltipEntry` (a faction
as something a block lists) and `FactionTooltipBanner` (a faction as a verdict over the whole system),
`StandingRowResolver` (the ranked groups as entries), and the territory and status lines. Both sit on
`PoliticalMapCellTooltip`, which binds the claim read for the whole layer and heads every one of its
boxes with the decree holding the system: either box may have to say a system is held by decree, and a
decree resolved - or drawn - one way on one view and another way on the next would answer one hover two
ways a keystroke apart.
The domination box has a second, fuller version - `ExpandedSystemDominationTooltip`, the counterpart
it offers the framework's detail mode - which lists every faction holding the system over the
colonies its score was summed from and each colony over the factors behind its weight, down to a
garrison's patrol tiers. Both sit on `SystemStandingsTooltip`, which settles everything but that
nesting - one pass read from the active view, the ranking, the status line, the two headings, the
lines naming the blocs and the member factions inside them - because two boxes over one system have
to be two amounts of detail about the same contest rather than two contests. What a box adds is one
answer: what to hang beneath a faction as the account of its score (`FactionAccountResolver`), asked
for once per paint and applied by `StandingRowResolver` where the standing and the line named from
it are both in hand, so no box can list one faction's colonies under another's name. Hanging nothing
is the shared default, so the ordinary box overrides nothing at all. What that pair offers the player is named
there too, once for both: "score contributions", which the framework puts at the foot of whichever
of the two is drawn, beside the key that switches between them. Named here rather than by the
framework because only this layer knows what its counterpart holds, and once rather than per box
because the account is the same thing whichever way the player is switching. The parts come
from the very arithmetic the scores were summed over (`KnownMarketFootprints.readBreakdownByFaction`),
so the lines always add up to the number the ordinary box and the fills show;
`MarketWeightRowResolver` decides which lines a colony breaks into and `MarketFactorText` how one
line's numbers read - a rating as the player set it, a weight on the grid the rest of the box counts
in, no cut that took nothing, and a patrol tier's rate stated apart from the total it explains so the
box draws the arithmetic quieter than the finding),
and `sidebar` - the last being this layer's own body
controls, neither the box they sit in nor the spotlight picker among them, both of which are
reached through [the sidebar](../base/sidebar/README.md) one level up (the picker is KMLib's, bound
to this mod's save slots there). What stays here is what that picker refuses
to know: which blocs are on offer and what makes that list stale (`SelectableBlocCache`), and the
recede toggles the layer pairs with the picker's sort (`RecedeControl`).
The class that names and orders the views is `kmu.maplayers.MapLayers`, also one level
up; how a layer is picked and what each screen remembers is [map layers](../README.md).

## When the map is rebuilt

Nothing above is redrawn from scratch per frame. The overlay holds its cells, its territories, and
its labels, and a frame's normal cost is a few int compares against the revisions each was built
against. A colony changing hands re-shapes that system and its neighbours; a settings or toggle
change restyles over the standing cells; only a change to the *set* of drawn systems rebuilds the
partition.

[The caching notes](../../../../../../docs/dev/caching.md) own that model in full - the signals, the
caches, and the four rebuild paths.
