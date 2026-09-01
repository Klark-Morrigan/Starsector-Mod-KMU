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
  - [Refresh](#refresh-baserefresh)
  - [Render orchestration](#render-orchestration-baserender-baserenderhover)
  - [Hover tooltips](#hover-tooltips-basetooltip)
  - [The domination box](#the-domination-box)
  - [The status line and the colony vocabulary](#the-status-line-and-the-colony-vocabulary)
  - [The claims box](#the-claims-box)
  - [The presence ribbon](#the-presence-ribbon-baseribbon-and-its-counting-rules)
  - [Sidebar controls](#sidebar-controls-basesidebar)
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
alone - most of the sector - bands too, at one width a colony, so its cell says how much is there
without out-shouting the contested ones.

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
| **Claims** | each claiming faction on its own | claimed systems, per faction | any faction that claims a system or lives in one | always |

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
  than by domination, and it takes one faction the general rule below would not reach: a claimant
  holding no colony anywhere lives nowhere and paints here all the same, so it is listed on the
  strength of its claims. Under the default claims-descending sort the rows greyed for claiming
  nothing form a tail below the claimants; sorting by name interleaves them.
  A claimant's systems already share one border and one colour with no filter on, so what
  spotlighting adds here is the contrast: the picked claimant keeps its full strength while every
  other claimant fades into the muted background. Systems the pick *lives in without claiming* keep
  their strength too - in the neutral, unclaimed paint they carry off filter, never the pick's
  colours - so a faction that claims nothing still shows where it is while showing that it claims
  none of it. Nothing hatches: a system has exactly one
  claimant, so no claim can be contested the way a held system can. Hovering a system explains its
  claim: who holds it, who stands in the holder's alliance, who is on good terms with it without
  standing in that alliance, who contests it, who is present but can never claim it, and whether
  the hold was won on market strength or imposed by decree.

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

What every picker lists is who *lives* somewhere the map draws, which is the same reading of a system
the cells are painted from and the bands counted from - not who the layer's mechanic weighed. So a
faction whose only colony the economy never registered is offered, and so is `Neutral`, which is what
a revealed dead world is owned by. A bloc whose only holding is a derelict nobody lives on is not:
a spotlight lights territory, and there is none to light.

A listed bloc that paints nothing on the layer greys, and stays pickable: it is listed because it is
present, greyed because there is nothing here to light. What "nothing" counts as is the layer's own
metric - no claim on Claims, no dominance weight on the two the contest paints - so it is one rule
read against whichever number that layer paints by. The bloc's metrics carry the answer, through
`PaintingBlocMetrics`, which a picker of painters opts into and any other picker leaves alone.

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

The rest of `base` carries the supporting parts, a subsection each below: `politics` (grouping and
the held/claim resolvers, under the ownership link above), `refresh`, `render` and `render/hover`,
`tooltip`, `ribbon`, and `sidebar`.

### Refresh (`base/refresh`)

The economy-event listeners, and `PoliticalMapStalenessSource` - what this layer counts as a change
the engine fired no event for, answered into the framework's poll - plus the one passenger that
writes rather than reads: each system's own inhabitants observing the colonies a revelation gate
holds back, recorded here because nothing in the engine announces a derelict arriving among
witnesses. The economy-event listeners record the same thing for the one system they name
(`MarketPoliticsRefresh`), the event being the moment the observation is worth dating rather than a
poll cycle later - and a decivilisation is recorded on the `aboutToBe` phase, since once the colony
has died there is nobody left to date what it vouched for. Where an event fires too late to read the
system as it was - an abandonment, a Nex transfer - the comment at that listener says so, and the
colony keeps the sighting it already had.

Beside those sits `PoliticalMapRefreshSignal`, the coarse changes only this layer can raise on the
shared board, alliance membership being the one.

### Render orchestration (`base/render`, `base/render/hover`)

`PoliticalMapOverlayRenderer` is the order the sub-layers are stacked in, bottom to top, and the
only place it is written down; `PoliticalMapBandLayout` is which side of the map's own nebulae each
of them paints on, read per pass from the player's four **Nebula draw order** settings. The order
among the sub-layers is the layer's and the side of the nebulae is the player's, which is the whole
division: what makes a sub-layer legible against the ones under it is fixed, and what a haze over it
costs is taste. Three sub-layers ride with a chosen one rather than being chosen themselves - the
contested hatch inside the fill it is half of, and the hover feedback and the debug overlays with
the base view they brighten, annotate, or replace.

In `render/hover`, `PoliticalMapHoverHighlightSource` is this layer's answers about the cell under
the cursor: the owner's border loops it might sit inside and the shade its fill draws in, over the
frame's painted shapes it hands the framework unchanged. `PoliticalMapHoverGates` is whether this
layer answers the cursor at all, its own two switches ANDed with the framework's, plus whether
either kind of feedback still needs the cursor read.
### Hover tooltips (`base/tooltip`)

What this layer says about the hovered system, each view injecting the explanation of the mechanic
its own fills were painted by into the framework's hover box: `SystemDominationTooltip` - the ranked
standings behind a faction or alliance fill - and `SystemClaimTooltip` - the scored claim contest
behind a claims fill, its claimant over the factions standing in its own alliance, those on good
terms with it outside that alliance, the rivals who could have taken the system and the factions
present that never could. Both are written from `FactionTooltipLine` (a faction as something a block
lists) and `FactionTooltipBanner` (a faction as a verdict over the whole system),
`StandingRowResolver` (the ranked groups as entries), and the core-territory heading
(`CoreTerritoryHeading`) and status lines.

All of them sit on `PoliticalMapCellTooltip`, which binds the claim read for the whole layer and
heads its boxes with the decree holding the system: any box may have to say a system is held by
decree, and a decree resolved - or drawn - one way on one view and another way on the next would
answer one hover two ways a keystroke apart. A box whose own body already states the decree says so
(`isStatingCoreClaimInBody`) and goes without the heading, so the one fact is met once rather than
twice in a single hover. Both reasons a box goes unheaded - no decree at all, and a decree the body
states itself - are settled inside `CoreTerritoryHeading` and answer alike, so no call site can drop
the heading by claiming there is no decree.

### The domination box

`SystemDominationTooltip` lists every faction holding the system over the colonies its score was
summed from, and each colony over the factors behind its weight, down to a colony's patrol tiers. It
composes one account, read to whatever depth was asked for, and the blocks cut it: who holds the
system at the shallowest, the colonies a tier down, their factors a tier below again. One tree read
to four depths rather than four bodies, so no two depths can describe one system differently.

Composition stops where the cut would, this layer's deeper tiers being the expensive ones. The
account resolver is built only where the level shows a line of one (`isAdmittingAccounts`, gated in
`SystemStandingsTooltip`), so the shallowest level makes no `readWeightBreakdownsByFaction`, no
unweighed-colony read and no
`SystemColonyReading` walk at all - the walk behind those being the most expensive thing a hover
does, and not one line off it drawn at that level. Below it the same rule runs on inside
`MarketWeightRowResolver`: a colony's factors are worked out only from `MARKET_STATS`, its patrol
tiers only at `PATROL_DETAILS`. What is *drawn* stays the cut's alone, so the box a level shows is
identical either way.

It sits on `SystemStandingsTooltip`, which settles everything but that nesting - one pass read from
the active view, the ranking, the status line, the headings and which of them a group falls under,
the lines naming the blocs and the member factions inside them. What the box adds is one answer:
what to hang beneath a faction as the account of its score (`FactionAccountResolver`), asked for
once per paint and applied by `StandingRowResolver` where the standing and the line named from it
are both in hand, so no faction's colonies can be listed under another's name. Every box on the
shape answers it rather than inheriting an empty one: a box that hung nothing would draw the same
thing at all four levels while `F1` went on offering to open it up.

Where each ranked group is listed is `StandingBlockRouting`'s one answer, taken per hover over the
closed set of blocks `StandingBlock` names - which carries each block's heading too, so the order
they read in is that declaration order rather than a sequence of calls that could drift from it. Why
the blocks nest as they do is the routing's own doc; what it costs is here.

The outer axis is `BlocCandidacy`, read off the pass's own grouping so the box bars exactly who the
fills bar - and that is the one place the box and the fill part company. A system whose only presence
is `neutral` is still painted, bordered and labelled for it, while the box drops `Dominated by:`
entirely and lists it under `Non-political:`. Naming the placeholder as holding the system is the
statement the block exists to stop making, and saying nothing about who holds a system nobody
political holds is the truer answer.

Below the holder the split is `ContestSides` - the one placement every surface reporting a contest
routes its blocks through, over the `BlocAffiliation` the bands judge their contest by
(`HolderGroupingSource`, bound by `SystemDominationTooltip` and sampled per hover) - so no two
surfaces over one system can put a bloc on different sides of it, and a box cannot disagree with the
band beneath it about who is a rival. The headline stays on the group the map painted the cell for
rather than on its alliance, which is what keeps the box an explanation of the cell beneath it - what
is taken from the alliances layer is the shape and never its grouping, which would merge allied runs,
fills and rows. Only the holder's allies are lifted out; two rivals allied with each other stay
contested, the block stating relations to the group that holds the system. It is naturally empty on
the alliances layer, where members are already one bloc, and on an install with nothing grouping
factions - dropped there by the same rule that drops any other block standing over no entries.

Inside that split, disposition sorts what alliance left standing against the holder.
`BlocFriendliness` answers whether two blocs are on good terms - every faction of the one above
`RepLevel.NEUTRAL` toward every faction of the other, read over both whole memberships
(`HolderGrouping.resolveMemberFactionIds`) rather than over who happens to stand in the hovered
system, so the same two blocs cannot come out friendly over one system and contesting over the next.
The threshold is the base game's own step from indifference to goodwill
(`StarsectorFactionRelations`, KMLib), which is what makes the block explicable: a cut taken anywhere
else in the scale is one the player is never shown. What the four rules are is `StandingBlockRules`,
bundled because a friendliness read over one grouping beside an affiliation off another would place
blocs under an alliance set no fill was resolved with.

A bloc its members do not agree about is not listed whole under either heading. It folds into both
instead, each of its rows holding only the members on that side (`RoutedStanding`) and stating a
`StandingFraction` saying how far its heading reaches - so the bloc stays one named thing under both
and nothing is orphaned from the grouping the map paints that territory by, where a majority or a
lead-member reading would put a heading over factions it is false of. Which members split a bloc is
read off those standing in the hovered system, a side with nobody in it heading a row with nothing
beneath it; where they agree, the whole-membership answer places the bloc, so a bloc whose sour member
holds nothing here still contests the system. `HOLDER` and `ALLIED` never split, being placed by
membership rather than by relation, and under the identity grouping every bloc is a singleton, so
nothing splits there at all.

One rule covers every fraction: an alliance row counts its own members, a faction row counts the
holder's, and both are counted over whole rosters so an alliance reads the same over every system it
holds. Which of the two a row states is `StandingRowResolver`'s call, that being the one side knowing
a group's kind - a lone-faction group is that faction under another name and states the faction's
reading. Both ends of the range are omitted, `0/total` and `total/total` saying exactly what the
heading above already did, which is why a faction against a lone holder never draws one and the whole
device belongs to the alliances view.

What the deeper levels offer the player is named there too, once for the whole cycle: "score
contributions", which the framework puts at the foot of the box beside the key that advances it.
Named here rather than by the framework because only this layer knows what is down there, and once
rather than per level because the account is the same thing at every press. It is offered only where
the system ranks somebody, since the deeper tiers account for the colonies behind the standings and
a system ranking none gives them nothing to account for. That is asked of the ranking and not of the
status line above it, the box reading the system through one `readRankedStandings`: the line and the
listing answer different questions of the same pass - whether anybody *runs* the place, against
everybody the player may be *told* about - so a system whose colonies have all collapsed is headed
`Decivilised` and still ranks whoever holds them, and those colonies are exactly what a deeper level
opens up. Judged off the line, that system - the one whose whole account is the collapse - is the
one the detail is withheld on.

The parts come from the very arithmetic the scores were summed over
(`KnownMarketFootprints.readBreakdownByFaction`, which folds what `MarketWeights` works out per
colony - the base size, station and patrol factors and the stability cut each takes, plus the grid a
worth in size points is rounded onto), so the lines always add up to the number the ordinary box and
the fills show. `MarketWeightRowResolver` decides which lines a colony breaks into and
`MarketFactorText` how one line's numbers read - a rating as the player set it, a weight on the grid
the rest of the box counts in, no cut that took nothing, and a patrol tier's rate stated apart from
the total it explains so the box draws the arithmetic quieter than the finding.
Every colony line leads with the glyph the sector map marks that colony's entity with. What that mark
is for and why it takes the line's own colour rather than the shade the map paints it are
`CellTooltipMark.resolveMarkForMapIcon`'s, stated there once for every surface that lists entities.
What is this layer's is where the glyph comes from: name and glyph travel as one `EntityNameplate`
(KMLib's `kmlib.starsector.entities`), read on the walk that counted the colony
(`MarketWeightBreakdown.marketNameplate`, through `Markets.readNameplate`) rather than looked up again
where the line is drawn, the same rule every other part of the account is read under: the box reads
what the pass recorded, so there is no second market lookup free to answer for a different one - and
no way for one colony's name to be drawn beside another's glyph, the pair never being apart.

The station line beneath a colony takes one on the same terms (`StationFactor.stationNameplate`,
read through `EntityNameplates.readNameplate` where the connected-entity scan answered the token
rather than beside the name, so the glyph can only be the station whose bonus is stated by it): it
is the one term of the account named for a thing on the map rather than for a piece of arithmetic,
and the mark settles more there than a level up, a system's stations being told apart on the map by
their glyph as much as by their name. Every other line beneath a colony stays unmarked - a stability
or a size has nothing on the map to point at.

Where that station shares the colony's name the line says which of the two it is about
(`MarketFactorText.formatMilitaryStationName`). A colony on a station is one place to the player and
two entries to the economy - the colony and the military station defending it - which vanilla names
alike, so the account states the same words at two levels for two different things. Both conditions
have to hold: the colony must itself be a station (`MarketWeightBreakdown.isStationMarket`, read on
the same walk that counted it, and the same reading the dominance tie-break prefers planets by) and
the two names must match. A planet colony that happens to share its station's name is two places the
player can see apart on the map, so a clarifier there would answer a question they never had. It
reads in the line's own colour rather than the qualifier's gold: the parentheses already say the run
is an aside, and the gold is reserved for findings - the words a colony's own line calls out, the
claims box's `(core)` - which a disambiguation is not.

That box lists one kind of colony no score above it accounts for: one the economy does not list,
which the weight read has nothing to weigh. It is the other half of the one colony set the weighed
read selects from - the colonies the economy does not list
(`KnownMarketFootprints.readUnweighedColoniesByFaction`), carried as an `UnweighedColony` - a
nameplate, the colony's own id, and the two facts its line calls out that no weight would carry:
what kind of place it is and whether it conceals itself - rather than as a zeroed
`MarketWeightBreakdown`. Zero weight is not absence on this side, a weightless colony still marking
presence and painting its system unopposed, so a value that could be summed into a footprint would
leave the pass one forgotten branch away from painting a system for a faction the mechanic never
counted, and a name with a glyph cannot be summed into anything. It is named at the foot of the
faction's list, led by the map's glyph like any other colony - it being the only trace of such a
colony the player has beside the name - at nought in the quiet shade, and breaks down into no
factors - the same sentence the claims box speaks for a market its own mechanic never weighed, and
for the same reason: the colony is there and it moved nothing, which is the whole of what the
account has to say about it.

Every colony line, weighed or not, says how old the box's news of it is where nobody is looking at
the colony as the box is drawn (`ColonyObservationNotes`, run onto the line as a grey remark through
`CellTooltipEntryLine.notedWith`). In sight the name stands alone; out of sight it carries
`last seen 34 days ago (c206.05.12)`, closing the line past whatever qualifier it calls out - the
remark is about the box's account rather than about the colony, and set ahead of the gold it would
break a word like `abandoned` away from the name it qualifies. Two things count as looking at it,
being the two routes an observation is ever made by: the player's fleet is in the system, or the
system's own inhabitants can see the colony - the owner-aware reading the visibility rule itself
uses. Only then is the
sighting register reached for, so the ordinary case costs a location comparison and an owner-set
read, and the elapsed span and the date are built off `CampaignClockAPI` per remarked line. That
matters most for the colonies a revelation gate admitted on the strength of an observation - a
derelict, a concealed base - which would otherwise be listed exactly as a colony the player is
standing over. No visibility rule reads the time: the moment being shown turned on how recent an
observation was, a colony would blink out of a box the player was reading it in. Which is also why
the remark is matched to its line by the colony's own id (`MarketWeightBreakdown.marketId`,
`UnweighedColony.marketId`) rather than by name - vanilla names a station colony and its defending
station alike.

A faction whose only colony in the system is one of these is listed all the same, at a nought of its
own. Presence and weight are two questions, so the ranking (`SystemStandings`) is handed both: who is
in the system (`HolderPass.readKnownColonyFactionIds` - the owners of everything the box may name,
so no two boxes over one cell can name different factions) beside each faction's
footprint, and anyone present with no footprint takes a `PresenceOnlyFactionStanding`, the sealed
other half of `FactionStanding`. Presence is read as the wider set rather than as whatever the
weighing left over, so nothing a weighed read comes to exclude can drop a faction out of the listing
while the band goes on counting it. Nothing about the fill moves: holding is resolved off the
footprints, which such a faction raises none of, so its nought can neither take a system nor tie for
one. The nought reads in the quiet shade at both tiers (`statesUncountedValue` again, and
`GroupStanding.hasWeighedMember` for the bloc line over it) - a bloc counts as weighed where any one
member was, so an alliance holding one registered colony beside two unregistered ones keeps an
aggregate somebody worked out.

### The status line and the colony vocabulary

The status line above that listing (`SystemStatusRow`) answers a different question of the same
colony rule, and the two are meant to part over one shape. It asks habitation - whether anybody
lives here - where the listing asks what the player may be told about, so a system whose only market
is an abandoned station is headed `Unpopulated` over a box that names the station's owner at nought.
That is the true reading of a system with one wreck in it rather than the contradiction the two lines
look like side by side: nobody has ever been aboard a derelict, and somebody has seen it. The cell
under the box reads habitation too, so the line and the backdrop it is drawn over always agree.
The one shape habitation admits that nobody runs is the collapsed colony
(`ColonyKind.UNGOVERNED_COLONY`), which is why the line reads the kinds out of that projection
rather than asking its emptiness: a decivilised world is still populated - drawn as settled rather
than dropped as empty space - and still headed `Decivilised`, since what it lacks is a polity and
not people, and the status row keeps its capitalised `Decivilised` for the banner it is.

Both boxes then say what they have found out about the place on the line naming it
(`ColonyQualifier`, gold, one read for the two families so neither can call a world dead the other
lists as living). Five words, in a fixed order: `claim holder`, then the kind - `abandoned` for a
hulk, `decivilised` for a collapse - then `undiscovered`, `hidden` and `unlisted`. A collapse and a
hulk reach a listing identically, unowned and off-economy and at nought, and nothing else on either
line would tell them apart; the last three are the three separate ways a colony can be out of plain
view, and they were each stated in one box and not the other before the read was shared.

Two of the five never join what stands above them. `undiscovered` displaces `hidden` - an
undiscovered colony is concealed from the player by that alone - and `unlisted` speaks only where
nothing above it held, or it would repeat itself on every derelict and every dead world, both being
off-economy by construction. The suppression is by the condition holding rather than by where a word
ends up being stated, which is what lets a station already called *Abandoned Station* say its word
inside its own name and still suppress `unlisted` below.

That name is the second place a word can be stated. Where the colony is already called one of the
five, the occurrence *in the name* is drawn in the qualifier's gold
(`CellTooltipEntryLine.callsOutInLabel`) and nothing is repeated at the end of the line - so the one
shape that most needs telling apart from an ordinary colony is not the one the box says nothing
whatever about. The resolution is untouched: the same words in the same order, and a gilded word has
qualified in every sense, drawn somewhere else. At most one stretch is gilded, the first the name
carries, and the rest close the line as usual - so an *Abandoned Station* the player has not found
gilds `Abandoned` and still reads `undiscovered`. What counts as the name saying a word is
`KmlibStrings.findWholeWordIndex`, and what is drawn is the name's own spelling of it.

`hidden` is withheld from a colony the sector openly points at - Galatia Academy, whose station is
permanently visible while the market hung on it is a stand-in vanilla never registers with the
economy and marks hidden to keep off the books, so it wears the identical flag a pirate base does
for an entirely different reason. Nothing on either market parts them, so the exemption is an
identity: `OpenlyKnownColonyRegistry` holds the entity ids `MapLayers` seeds it with beside a tag
another mod hangs on content of its own, and `OpenlyKnownColonyLookup` folds the answer by colony id
off the box's own walk. The Academy then falls through to `unlisted`, which is the separation the
word was wanted for. The exemption excuses that one word and nothing else: the Academy is a hidden
colony to `ColonyVisibility` still, gated still, and admitted still only by Ancyra settling the
system.

Each box fills in a small `ColonyQualifierFacts` from what it holds - the claims box off
`MarketClaimBreakdown`'s admission, the domination box off `MarketWeightBreakdown.isHiddenMarket` or
the `UnweighedColony` - and the kind, the discovery answer and the landmark answer come off the
box's own walk of the system (`SystemColonyReading`, whose `readConcealmentOf` gathers its two
answers with the account's own concealment fact), no row of either box carrying any of them.

### The claims box

`SystemClaimTooltip` opens every faction the contest names into the markets it holds the system with
and each market into the terms its claim score is built from - one account, read to whatever depth
was asked for and cut by the blocks, on the same terms as the domination box. It sits on
`SystemClaimContestTooltip`, which settles the one read behind it, the claimant, the decree marker,
and the five blocks, and leaves open only what hangs beneath a faction (`resolveAccountEntries`,
answered by every box on the shape). It is asked at all only where the level shows a line of one
(`isAdmittingAccounts`), so the shallowest level selects, ranks and words no faction's markets;
below it `ClaimScoreRowResolver` works out a market's terms only from `MARKET_STATS`. That account
is handed the whole `ListedClaimContest` rather than the scored read alone,
so the colony rule it draws under is the one the listing above it was projected under: read afresh
per faction, an account would be free to withhold a colony the line above it had just named, and to
answer two factions of one box under two different rules. Both relations to the claim holder travel
in that same value and for the same reason - the `BlocAffiliation` the blocks are routed against
(`HolderGroupingSource`, bound by `SystemClaimTooltip` and sampled per hover - a set held for the
session would file a faction under the alliance it left an hour ago), placed by the same
`ContestSides` split the domination box routes its own blocks through, and the `BlocFriendliness`
bound to the hovered sector's own relations.

Two axes place a faction into those blocks, and how it stands to the claim holder is the outer one:
`Allied with the claim holder:` takes everyone standing with the holder by alliance and
`Friendly with the claim holder:` everyone else above `RepLevel.NEUTRAL` with it, both whatever
their eligibility, and the two eligibility blocks divide what neither took. Where a relation heading
leaves the box unable to say which kind a line is, the line says it (`non-territorial`) - one rule
over both blocks rather than one per block. Why the relations outrank eligibility, why disposition
sorts inside alliance, and why an install without Nexerelin needs no branch are all
`SystemClaimContestTooltip`'s to state. It is also where the layer's heading is declined for both of
them: the claim line names the decreed holder and marks the hold, so these are the two boxes that
state the decree themselves.

Two blocks the domination box has do not appear here, and both absences are the claims layer pinning
the identity grouping. A standing is always a lone faction, so no bloc can be of two minds, nothing
folds into two headings, and no row states a fraction. And the placeholder owner is never admitted
to the claim mechanic, so it arrives ineligible and `Non-territorial:` is already the true statement about it -
where the fills, resolved per bloc and per candidate, needed `Non-political:` to say as much.

`ClaimScoreRowResolver` decides those lines: the faction's markets in the order the mechanic itself
would settle them - strongest first, a tie falling to the earlier place in the economy's listing -
so the one representing the faction comes out on top by that order rather than by being put there.
Exactly one market in the whole box is called out, as the `claim holder`: the one that actually took
the system. Every faction is represented by its strongest, but only one of those won anything, and a
marker on each would read as several holders of a system that can only have one; over a decree it
goes unsaid entirely, since nothing any market scored settled the matter.

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
Two kinds of market carry a score yet never compete and are never marked - a hidden one, which the
walk skips outright, and one the economy does not list, which the walk never reaches. A
non-territorial faction's markets are marked only inside their own faction: they can never take the
lead, so a tie against the claimant is not judged, while the tie deciding which of them stands for
the faction still is. Under a decree the claimant tie goes unjudged too, the system having been
settled before a market was weighed.

What the player has discovered silences no mark. A mark answers why two markets on one score are
ordered as they are, and both sides of a judged tie are markets the contest weighed - which the list
carries whatever the player knows of them (`ListedClaimMarkets.isListedMarket`) - so the
ordering a mark is about is always in front of the reader, within a faction and between two.

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
score competed with and lost on. Which of the two it was is said after the name rather than beside
the number - `hidden` or `unlisted`, in the shared vocabulary above - those being findings about the
place instead of statements about what the contest made of it.

Every market line carries the same last-seen remark the domination box's colony lines take, on the
same terms and matched to its line by the same kind of identity (`MarketClaimBreakdown.marketId`).
It reaches further on this list than on that one, for the reason `SystemClaimTooltip` gives.
The remark, the kind and the discovery answer travel together as one value (`SystemColonyReading`),
folded once for the whole box off the very walk of the system the status line comes from: all three
are read per row and none can be answered from a claim score or a dominance weight, so resolved
where an account is built they would read the system once for every faction the contest lists. The
domination box takes the same value on the same terms. That value also lays what it knows onto the
line (`SystemColonyReading.describeColony`, taking the findings the account holds and applying them
beside its own date), and the date is reachable no other way. Both are due on the same lines for the
same reason - a row naming a colony says what the arithmetic could not - so an account free to reach
them apart is one that can lay a finding and forget the date, and which it forgot is invisible: a
line missing its date reads exactly like a colony somebody is standing over.

The unlisted colony is vanilla's own doing: Galatia Academy is built as a real market on a real
station and deliberately never registered, so the mechanic's economy walk never sees it and a box
reading the economy alone reports that station as nobody's. The contest is read over
`Colonies` - KMLib's shared colony set, which covers both listings and which is also what
keeps the condition-only market every surveyed rock carries out of the account - and the sibling
count and every other term stay on the economy's own half of it, since admitting an unregistered
colony there would raise a real one's score above what the game scores it at and could hand the
system to a different faction.

A faction holding nothing but unweighed colonies takes a `PresenceOnlyClaimStanding` at nought
rather than dropping out of the contest, and the box lists it like any other. Every block below the
claim, the claimant's own line, the account beneath each of them and the `F1` hint read every
standing rather than the weighed half (`ListedClaimContest.selectFrom`), because a block says how a
faction stands to the claim and not what kind of record the contest gave it: routing by record kind
would file a pirate base's owner beside a Remnant station's, which are ineligible and eligible
respectively. A territorial faction holding only zero-claim colonies did enter the running by the
mechanic's own gate and scored nothing there, which is what `Contested by:` plus a nought says
exactly. That nought reads in the quiet shade (`statesUncountedValue`, the same treatment an
unweighed market line takes): it is the contest's statement about a faction it never weighed rather
than a score competed for and lost. The claimant's line takes the same nought where a decree holds a
system its faction is present in through unweighed colonies alone, in place of the blank column a
claimant holding nothing there gets.

A market on a colony nobody has found is listed all the same wherever the contest counted it into a
number the account states: its effect is on screen already - the claim, the faction's score, the
count of markets the faction was paid a point each for - and the row is what makes those account for
themselves. Two ways in, then. A market the contest **weighed** always qualifies. A market it merely
**counted as a sibling** qualifies under an account that goes on to state that count, which is a
weighed standing's; under a presence-only standing there is no count on screen, so there is nothing
for such a row to account for.

What is left off is the market the player knows nothing of that reaches neither
(`ListedClaimMarkets.isListedMarket`, over `MarketClaimBreakdown.isKnownToPlayer`,
`isScoredOnItsOwnAccount` and `isCountedTowardSiblings`): the sibling count walks the economy's
listing, so the market failing all three is the unregistered colony the walk never reached at all. It
accounts for nothing on screen, so a row for it would be disclosure and nothing else, and the dev
reveal is what states even those in full.

The knowledge half is the same rule the faction and alliance tabs fog by, so all three agree on what
the player knows. What that flag carries is the composed answer rather than the entity's own: the
player has discovered the market **and**, for the shapes a bare fog would leak, somebody has seen it
where it stands. Both halves of that are load-bearing here, because a listed row may be a concealed
colony as well as an undiscovered one - which is why such a row says `hidden` where the entity has
been found and `undiscovered` where it has not, the qualifier displacing one with the other.

Such a row is drawn with its name blocked out. Whether a row may name its market, and what stands in
where it may not, is `RedactedMarketLines` - the sibling of `ListedClaimMarkets`, asked straight after
it over the same market, and held apart for the same reason: a market being listed and a market being
named have no rule in common. In the name's place stands one filled block per word, as long as the
word ran (`RedactedSpan`), so the shape says how many words there were and how long each was and
nothing about which letters. The name itself never reaches the line - only the lengths, derived off
the nameplate and dropped there, since a value carrying text nothing draws is a value some later
change will draw; a market with no name at all is refused outright rather than blocked out to nothing,
which would draw as a glyph over blank space. The line opens on vanilla's
`graphics/fx/question_mark.png` rather than the map's own glyph: every other market line opens on an
image run, so a line opening on its name would be set apart twice over by the one fact about it, and
the map's glyph says what sort of place the colony is - the very thing being withheld. It reads in the
line's own colour like any other market glyph (`CellTooltipMark.resolveMarkInLineColour`).

What such a row keeps is everything that is not the name, and that part is the account's
(`ClaimScoreRowResolver`): the listing place, the tie outcome that place settled, and the word for why
the box cannot name it - off the same walk of the system every other row's words come from, so the
finding is stated as loudly here as anywhere. A claimant standing on such a colony reads
`claim holder, undiscovered`, the map already painting that system in its holder's colours. What the
row gives up besides the name is the value column and the breakdown beneath - unless it is the market
its faction stands on, where the score is stated because the faction's own line above already carries
it and withholding it there would hide nothing while leaving the block's arithmetic unaccountable, or
unless the contest never scored it, where the nought is stated because that nought is the contest's
own statement rather than anything the row withholds. It is ranked on its real score
all the same, so its neighbours bound what it scored to within a point or two: the box declines to
*state* a number for a place the player has not found, and does not go on to pretend the contest ran
in some other order.

A second rule decides the factions above the markets, asked of a standing as a whole rather than line
by line: a faction is kept where at least one of its colonies is known or was weighed, and left off
entirely where none is (`ListedClaimContest.selectFrom`, calling
`ListedClaimMarkets.isFactionNamingMarket` rather than restating it - the box states outcomes over
the very colonies it decides, so a second copy of the rule beside it would be free to disagree). It
is the tighter of the two and deliberately so: the sibling count cannot name a faction, being the
account's own working, stated beneath a market that faction was already weighed on. The two live in
one class, the row rule written as this one plus its extra term, so the nesting holds by construction
- a market only the count reaches earns a row under a faction some other market already put on the
box, and never a box of its own. Naming a faction over an
account with nothing in it would tell the player exactly what the fog is keeping back - and `F1` is
offered only where a standing survives that filter (`hasListedStanding`), so the key is never
advertised over a box the fog has emptied. Both boxes ask that through one read of the contest
(`SystemClaimContestTooltip.readListedContest`): the hint offers an account of exactly the factions
the body lists, so answering the two apart would let a box advertise a key that does nothing.

A weighed standing therefore always survives, however little of the system has been explored: the
market carrying it is scored on its own account, so it has a row - redacted where nobody has found
the colony - and dropping the faction would report the contest as something other than what decided
it. The disclosure that follows is deliberate. A rival scored on a colony nobody has found is named,
placed in the economy's listing and given its score, so a player hovering an unexplored system can
read that somebody holds something in it; what the fog takes is the colony's identity, not the fact
that a faction is there. What the filter still removes is the faction present through concealed or
unlisted colonies alone, none of which anybody has seen: the contest never weighed it, so no number
on screen is short of it and there is nothing but a name to state.

Closing the list is the presence term, which is the faction's rather than any one market's, since
the mechanic gives every market of a faction the same point per other market it holds there: stated
once beneath the very markets its count can be checked against, and worked out from that count
(`Same-faction market bonus   (3 markets) - 1 = +2`, the subtraction being the market being scored,
which is not its own sibling) rather than as a bare result nobody can check. That line is working
throughout bar the points it arrives at, so it reads in the quiet shade name and all. That the count
can be checked against the lines above it is the listing rule's doing rather than a coincidence:
every market the count counts is one the rule draws, blocked out where nobody has found it, so the
term never stands over a list short of what it counted. It is withheld for the one market that can
contradict it - an unlisted colony on the list, which the mechanic never counted and which sits among
the very lines the count invites the reader to check it against, where the term would read as short
by a market on screen.

The resolver shares the entry model and the block vocabulary with the domination pair but not their
number grammar - a claim score is a small whole number of points with no grid behind it, so no
rating-to-weight change is stated.

### The presence ribbon (`base/ribbon` and its counting rules)

The vocabulary a cell's presence band is planned in - the runs, the dividers between them, and the
two-armed gate deciding which cells band at all - held apart from where a band's colony counts come
from, since that is each painting mechanic's own business and keeping it there is what makes one
band mean one thing on every view. The band spans four packages, each with its own register: the
[plan and its gate](base/ribbon/README.md), the
[ring geometry and the draw](base/render/ribbon/README.md), and the two counting rules -
[held cells](dominance/ribbon/README.md), counted off the very footprints the fill was ranked from,
and [claims-layer cells](claims/ribbon/README.md), counted off the contest over the system - the
cells no claim covers among them, since the claim walk never sees a hidden or player-owned market
and a band is the only thing that reports those systems. Which of the two answers for a cell is the
view's own call, made through the same seam it picks its holder source and its hover box by.

### Sidebar controls (`base/sidebar`)

This layer's own body controls, neither the box they sit in nor the spotlight picker among them,
both of which are reached through [the sidebar](../base/sidebar/README.md) one level up (the picker
is KMLib's, bound to this mod's save slots there). What stays here is what that picker refuses to
know: which blocs are on offer, what makes that list stale, and where each of those blocs was found
(`SelectableBlocCache`), and the recede toggles the layer pairs with the picker's sort
(`RecedeControl`). The presence rides the same memo as the rows because it is the same sector walk's
answer, so a surface lighting a bloc's systems costs no economy read of its own.

The class that names and orders the views is `kmu.maplayers.MapLayers`, also one level up; how a
layer is picked and what each screen remembers is [map layers](../README.md).

## When the map is rebuilt

Nothing above is redrawn from scratch per frame. The overlay holds its cells, its territories, and
its labels, and a frame's normal cost is a few int compares against the revisions each was built
against. A colony changing hands re-shapes that system and its neighbours; a settings or toggle
change restyles over the standing cells; only a change to the *set* of drawn systems rebuilds the
partition.

[The caching notes](../../../../../../docs/dev/caching.md) own that model in full - the signals, the
caches, and the four rebuild paths.
