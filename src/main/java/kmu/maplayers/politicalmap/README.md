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

A system where somebody other than the bloc painting it also has colonies carries a fifth: a banded
stroke running inside that one cell, a run per colony in the bloc's own colours, so a cell reads as
who is there and in what proportion without the fill having to be split. A system its painter holds
alone - most of the sector - bands only where the player asks for the uncontested ones, and then at
one width a colony, so its cell says how much is there without out-shouting the contested ones.

The tab body picks the view by radio, and a filter picker below it can spotlight one bloc. When a
bloc is spotlighted, the rest fade into a muted background. The radio always lists the views in
the same order: **Factions**, then **Alliances** (only with Nexerelin), then **Claims**.

The controls read and write one shared set of values, so the two screens the overlay draws on
always agree on what is painted; only which tab each screen has lit is its own. The box those
controls sit in - where it anchors on each screen, and how it folds away - is
[the sidebar](../base/sidebar/README.md).

## The views

A view is a small set of rules on top of one shared draw pipeline. Each view answers four
questions, and the pipeline paints the answer without knowing which view asked:

- How do factions group into blocs?
- Which systems does each bloc paint?
- What is in a system, for the presence band inside each cell?
- How is each bloc styled and named?

| View | Groups by | Paints | Spotlight targets | Needs |
| --- | --- | --- | --- | --- |
| **Factions** | each faction on its own | held territory, per faction | any faction | always (default) |
| **Alliances** | allied factions fused per alliance | held territory, alliances as one bloc | alliances only | Nexerelin |
| **Claims** | each claiming faction on its own | claimed systems, per faction | any faction that claims or holds something | always |

Notes on each:

- **Factions.** The plain case. Every faction is its own bloc. Only genuine independent space
  fades to the muted style. A bloc is named after its faction.
- **Alliances.** Allied factions merge into one coloured, named cluster per alliance. Unaligned
  factions keep their own border and name. Two toggles fade a non-allied faction: *Mute* dims it,
  *Desaturate* makes it read as backdrop. *Desaturate* starts on so the alliances read as the figure
  without a settings hunt; *Mute* starts off. Turn both off and a lone faction looks exactly as
  it does on the Factions view. A sector holding no alliance yet fades nothing whatever the toggles
  say - there is no figure for a backdrop to sit behind - so the view reads as the Factions view
  until the first alliance forms.
- **Claims.** Shows the vanilla "system claimed by faction" mechanic - the same claim the
  colony-survey panel warns about. Every claimed system is painted solid in its claimant's colours.
  There is no alliance grouping here. The spotlight list ranks by claim count and market size rather
  than by domination, and it holds every faction that claims *or* holds something: one that claims
  territory while holding no colony anywhere paints here, so it is worth spotlighting, and one that
  holds colonies while claiming nowhere is listed greyed at a count of 0 rather than left out, since
  a faction the player can plainly see going missing from the list reads as an oversight. Under the
  default claims-descending sort the greyed rows form a tail below the claimants; sorting by name
  interleaves them.
  A claimant's systems already share one border and one colour with no filter on, so what
  spotlighting adds here is the contrast: the picked claimant keeps its full strength while every
  other claimant fades into the muted background. Systems the pick *lives in without claiming* keep
  their strength too - in the neutral, unclaimed paint they carry off filter, never the pick's
  colours - so a faction that claims nothing still shows where it is while showing that it claims
  none of it. Nothing hatches: a system has exactly one
  claimant, so no claim can be contested the way a held system can. Hovering a system explains its
  claim: who holds it, who
  contests it, who is present but can never claim it, and whether the hold was won on market
  strength or imposed by decree.

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
    V --> R[Ribbon planner:<br/>what each system holds]
    G --> PIPE[Shared draw pipeline]
    O --> PIPE
    R --> PIPE
    PIPE --> S[Shape cells into<br/>bordered territories]
    S --> Fi[Split the fill:<br/>solid / hatched / unfilled]
    Fi --> L[Overlay bloc names]
    L --> B[Bake each cell's presence band,<br/>around the names]
    B --> MAP([Coloured map])
```

The bands come after the names rather than before them because they are laid *around* them: a
name is fitted inside the border its cluster's cells trace, so it has no place until the cells
are shaped, and a band baked before that would run under a word. Baked last, each band carves the
names' boxes out of its cell's ring and lays its runs along what is left - or carves nothing,
where the player would rather have the whole band and let the name draw across it. Either way the
pass runs last, so the ordering is what makes the choice available rather than what settles it.

What changes between views is only those three inputs; from the seam on, every view shapes, fills,
bands, and labels identically. The sources themselves and the three fill states are
[ownership resolution](base/politics/holders/README.md).

The third input is the second one layer along: a view's cells are painted by some mechanic, and its
bands have to be *counted* by that same mechanic or they contradict the fills they sit inside. It
carries no default on the view seam, deliberately - a default would name one mechanic's planner in
front of every view, including the ones that mechanic does not paint. The contest-painted views
answer it once between them on `DominancePaintedView`, and the claims view answers with its own.

The spotlight list runs on the same principle one level down. `PoliticalMapView` asks a view for its
whole picker - the blocs on offer *and* the vocabulary that ranks them - so the metrics a view's rows
carry can never drift from the modes offered to sort them by, and the sidebar above passes the pair
on without naming either. Factions and Alliances are painted by the same contest, so
`DominancePaintedView` answers that once for both and leaves them only the one thing they differ on,
which is the Spotlight targets column above. A view painted by another mechanic implements the seam
directly and pairs its own list with its own vocabulary rather than widening theirs.

## Where each part lives

The top level is divided by *mechanic*, not by view: `dominance` holds the two views painted by the
market contest - `dominance/factions` and `dominance/alliances`, which differ only in how they group
- and `claims` is its peer, painted by the vanilla claim mechanic. A view is not a unit of anything
except its own rules, so grouping Factions beside Claims put two mechanics on one shelf and split
one mechanic across two; anything a mechanic owns beyond its views (its ribbon counting, so far)
folds in beside them rather than pooling in `base`. What stays in `base` is what every mechanic
shares: the view seam itself, the pipeline the seam feeds, and the vocabulary both sides state their
answers in.

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
`StandingRowResolver` (the ranked groups as entries), and the core-territory heading
(`CoreTerritoryHeading`) and status lines. All of
them sit on `PoliticalMapCellTooltip`, which binds the claim read for the whole layer and heads
its boxes with the decree holding the system: any box may have to say a system is held
by decree, and a decree resolved - or drawn - one way on one view and another way on the next would
answer one hover two ways a keystroke apart. A box whose own body already states the decree says so
(`isStatingCoreClaimInBody`) and goes without the heading, so the one fact is met once rather than
twice in a single hover. Both reasons a box goes unheaded - no decree at all, and a decree the body
states itself - are settled inside `CoreTerritoryHeading` and answer alike, so no call site can drop
the heading by claiming there is no decree.
The domination box has a second, fuller version - `ExpandedSystemDominationTooltip`, the counterpart
it offers the framework's detail mode - which lists every faction holding the system over the
colonies its score was summed from and each colony over the factors behind its weight, down to a
colony's patrol tiers. Both sit on `SystemStandingsTooltip`, which settles everything but that
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
box draws the arithmetic quieter than the finding).
Every colony line leads with the glyph the sector map marks that colony's entity with, which is the
one thing the box and the map can share at a glance - a name alone places a colony only for a reader
who already remembers it. Name and glyph travel as one `EntityNameplate` (KMLib's
`kmlib.starsector.entities`), read on the walk that counted the colony
(`MarketWeightBreakdown.marketNameplate`, through `Markets.readNameplate`) rather than looked up again
where the line is drawn, the same rule every other part of the account is read under: the box reads
what the pass recorded, so there is no second market lookup free to answer for a different one - and
no way for one colony's name to be drawn beside another's glyph, the pair never being apart.
It draws in the colony name's own colour and not the shade the map paints it: those shades are
authored to tell one world from another against black, and carried into a text box unchanged they
arrive brighter than the numbers the account is about, so a column of them reads as the finding when
what it is is a bullet point. The station line beneath a colony takes one on the same terms
(`StationFactor.stationNameplate`, read through `EntityNameplates.readNameplate` where the
connected-entity scan answered the token rather than beside the name, so the glyph can only be the
station whose bonus is stated by it): it is the one
term of the account named for a thing on the map rather than for a piece of arithmetic, and the mark
settles more there than a level up, a system's stations being told apart on the map by their glyph as
much as by their name. Every other line beneath a colony stays unmarked - a stability or a size has
nothing on the map to point at.
Where that station shares the colony's name the line says which of the two it is about
(`MarketFactorText.formatMilitaryStationName`). A colony on a station is one place to the player and
two entries to the economy - the colony and the military station defending it - which vanilla names
alike, so the account states the same words at two levels for two different things. Both conditions
have to hold: the colony must itself be a station (`MarketWeightBreakdown.isStationMarket`, read on
the same walk that counted it, and the same reading the dominance tie-break prefers planets by) and
the two names must match. A planet colony that happens to share its station's name is two places the
player can see apart on the map, so a clarifier there would answer a question they never had. It
reads in the line's own colour rather than the qualifier's gold: the parentheses already say the run
is an aside, and the gold is reserved for findings - the `hidden` flag, the claims box's `(core)` -
which a disambiguation is not.
That box lists one kind of colony no score above it accounts for: one the economy does not list,
which the weight read has nothing to weigh. It comes from a second walk over the same colony filter
(`KnownMarketFootprints.readUnweighedColoniesByFaction`), carried as an `EntityNameplate` alone rather
than as a zeroed `MarketWeightBreakdown` - zero weight is not absence on this side, a weightless
colony still marking presence and painting its system unopposed, so a value that could be summed into
a footprint would leave the pass one forgotten branch away from painting a system for a faction the
mechanic never counted, and a name with a glyph cannot be summed into anything. It is named at the foot of the faction's list, led by the map's glyph like any
other colony - it being the only trace of such a colony the player has beside the name - at nought in
the quiet shade, and breaks down into no factors - the same sentence the claims box speaks for a
market its own mechanic never weighed, and for the same reason: the colony is there and it moved
nothing, which is the whole of what the account has to say about it. A faction whose only colony in
the system is one of these still goes unnamed - it takes no contribution, so it takes no standing,
and there is no line to hang the colony under.
The claims box has a counterpart of its own on the same terms - `ExpandedSystemClaimTooltip`, which
opens every faction the contest names into the markets it holds the system with and each market
into the terms its claim score is built from. Both claim boxes sit on `SystemClaimContestTooltip`,
which settles the one read behind them, the claimant, the decree marker, and the three blocks, and
leaves open only what hangs beneath a faction (`resolveAccountEntries`, hanging nothing by
default). It is also where the layer's heading is declined for both of them: the claim line names
the decreed holder and marks the hold, so these are the two boxes that state the decree themselves. `ClaimScoreRowResolver` decides those lines: the faction's markets in the order the
mechanic itself would settle them - strongest first, a tie falling to the earlier place in the
economy's listing - so the one representing the faction comes out on top by that order rather than
by being put there. Exactly one market in the whole box is called out, as the `claim holder`: the
one that actually took the system. Every faction is represented by its strongest, but only one of
those won anything, and a marker on each would read as several holders of a system that can only
have one; over a decree it goes unsaid entirely, since nothing any market scored settled the matter.
Every market line leads with the glyph the sector map marks that market's entity with, scored or not,
on the same terms the domination box's colony lines take one: read off the breakdown the market
arrived in (`MarketClaimBreakdown.marketNameplate`, resolved by `VanillaClaimBreakdownReader` through
`Markets.readNameplate`) rather than looked up again where the line is drawn, so no second market
lookup can answer for a different colony, and drawn in the market name's own colour rather than the
map's. The term lines beneath a market carry no mark - a size or a garrison bonus has nothing on the
map to point at.
Every market also states where the economy lists it, as a quiet `[n]` run after its name
(`MarketClaimBreakdown.listingPosition`, numbered across the system's owned markets rather than
within one faction's): the contest is settled on a strictly greater score, so a tie - between two of
one faction's markets or between two factions' best - falls to whichever the economy reached first,
and nothing else in the box says which that was. Where a tie the mechanic actually consulted is
drawn, the place stops being a bare identifier and reads in vanilla's positive or negative shade -
the market reached first having won it, the rest having lost. Which ties those are is
`ClaimTieOutcomes`, judged over the whole contest exactly as vanilla's single `max` walk compares:
the two points that walk consults the order at are which market stands for its faction and which
faction claims the system, so a tie at either is marked and equal scores anywhere else are not.
Three kinds of market carry a score yet never compete and are never marked - a hidden one, which the
walk skips outright; one the economy does not list, which the walk never reaches; and a
non-territorial faction's, which can never take the lead - and under a decree the claimant tie goes
unjudged, the system having been settled before a market was weighed.
A market the mechanic never weighed is listed at nought, whichever of the first two it is
(`MarketClaimBreakdown.isScoredOnItsOwnAccount`, the one question the box asks of the pair): it
brought nothing to the contest however large it is, and printing the score it would have carried
would sort a market that took no part above the one that took the system. It is listed rather than
dropped because it is a colony the player can see on the map in a faction's colours, and for a
hidden one because it is also among the markets the presence term counts; it breaks down into no
terms, nothing having been computed for it. The nought reads in the quiet
shade (`statesUncountedValue` - only the number quietens, the market being named as loudly as its
neighbours, unlike the `readsAsAside` the bonus line takes): it is the contest's statement about the
market rather than anything the market scored, and in the list's own colour it would pass for a
score competed with and lost on. Nothing calls out which of the two it was - the nought is the whole
of what the contest has to say about it, and either word would raise a question about the mechanic
the box would then owe an answer to.
The unlisted colony is vanilla's own doing: Galatia Academy is built as a real market on a real
station and deliberately never registered, so the mechanic's economy walk never sees it and a box
reading the economy alone reports that station as nobody's. `StarSystems.readMarketsUnlistedByEconomy`
widens the read for the account only; the sibling count and every other term stay on the economy's
own listing, since admitting an unregistered colony there would raise a real one's score above what
the game scores it at and could hand the system to a different faction.
A market the player has not found is left off the list, since
vanilla settles a claim over colonies nobody has found and repeating what it learned there would
name something the player has no way of knowing about; the rule is
`MarketClaimBreakdown.isKnownToPlayer`, the same one the faction and alliance tabs fog by, so all
three agree on what the player knows, and the dev reveal states everything in full.
Closing the list is the presence term, which is the faction's rather than any one market's, since
the mechanic gives every market of a faction the same point per other market it holds there: stated
once beneath the very markets its count can be checked against, and worked out from that count
(`Same-faction market bonus   (3 markets) - 1 = +2`, the subtraction being the market being scored,
which is not its own sibling) rather than as a bare result nobody can check. That line is working
throughout bar the points it arrives at, so it reads in the quiet shade name and all, and it is
withheld entirely wherever the list above it is not exactly the markets the count counts - something
kept off it for being unfound, or an unlisted colony on it that the mechanic never counted - since a
count that cannot be checked against what is on screen either contradicts it, states the very number
the withholding exists to keep back, or reads as short by the market it never included. Note that a market's own line carries the
whole score the contest weighed it at, presence included, so its listed terms are what it adds that
its siblings' do not. The resolver shares the entry model and the block
vocabulary with the domination pair but not their number grammar - a claim score is a small whole
number of points with no grid behind it, so no rating-to-weight change is stated),
`ribbon` (the vocabulary a cell's presence band is planned in - the runs, the dividers between
them, and the two-armed gate deciding which cells band at all - held apart from where a band's
colony counts come from, since that is each painting mechanic's own business and keeping it there
is what makes one band mean one thing on every view. The band spans four packages, each with its
own register: the [plan and its gate](base/ribbon/README.md), the
[ring geometry and the draw](base/render/ribbon/README.md), and the two counting rules -
[held cells](dominance/ribbon/README.md), counted off the very footprints the fill was ranked
from, and [claimed cells](claims/ribbon/README.md), counted off the contest that settled the
claim. Which of the two answers for a cell is the view's own call, made through the same seam it
picks its holder source and its hover box by),
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
