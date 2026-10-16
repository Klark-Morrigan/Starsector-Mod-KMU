# The domination box (`dominance.tooltip`)

The hover box the Factions and Alliances views show for a system:
the ranked contest behind the fill,
and beneath each faction the colonies and factors its score was summed from.
Each of the two views holds a box of its own,
bound to that view's grouping,
so the standings are ranked under the view that painted the fills beneath them.

What every political box shares -
the decree heading,
the relations sampled per hover,
the status line,
the colony vocabulary and the last-seen remark -
is [the shared box shape](../../tooltip/README.md).

Part of [the political map](../../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../../README.md) for project context.

## Index

- [One account, cut to a depth](#one-account-cut-to-a-depth)
- [Where each group is listed](#where-each-group-is-listed)
- [Who may hold the system](#who-may-hold-the-system)
- [Allies and rivals](#allies-and-rivals)
- [Contested or present](#contested-or-present)
- [Disposition](#disposition)
- [A bloc of two minds](#a-bloc-of-two-minds)
- [The deeper levels](#the-deeper-levels)
- [Colony lines](#colony-lines)
- [The station line](#the-station-line)
- [A colony nobody weighed](#a-colony-nobody-weighed)
- [Presence without weight](#presence-without-weight)
- [What is not here](#what-is-not-here)

## One account, cut to a depth

`SystemDominationTooltip` lists every faction holding the system,
the colonies its score was summed from,
and the factors behind each colony's weight,
down to a colony's patrol tiers.
It composes one account,
read to whatever depth was asked for,
and the blocks cut it:
who holds the system at the shallowest,
the colonies a tier down,
their factors a tier below again.
One tree read to four depths rather than four bodies,
so no two depths can describe one system differently.

Composition stops where the cut would,
since the deeper tiers are the expensive ones.
The account resolver is built only where the level shows a line of one
(`isAdmittingAccounts`).
So the shallowest level makes no `readWeightBreakdownsByFaction`,
no unweighed-colony read and no `SystemColonyReading` walk at all -
the walk behind those being the most expensive thing a hover does.
Below it the same rule runs on inside `MarketWeightRowResolver`:
a colony's factors are worked out only from `MARKET_STATS`,
its patrol tiers only at `PATROL_DETAILS`.
What is *drawn* stays the cut's alone,
so a level's box is identical either way.

`SystemDominationTooltip` settles the rest of the box off one pass read from the active view:
the ranking,
the status line,
the headings and which of them a group falls under,
and the lines naming the blocs and their member factions.
What hangs beneath a faction as the account of its score is a `FactionAccountResolver`,
asked for once per paint.
`StandingRowResolver` applies it where the standing and the line named from it are both in hand,
so no faction's colonies can be listed under another's name.
It also sets the two-tier shape:
a lone-faction group reads as one flat line,
an alliance bloc as a line over its member factions.
The colonies hang under the faction flying them rather than under the bloc,
since a bloc's score is the sum over its members'.

## Where each group is listed

The ranking reads as a contest rather than as a list:
the group the map fills the system for is named as dominating it,
so who holds the system is stated outright rather than inferred from which line sits on top.
Where each ranked group goes is `StandingBlockRouting`'s one answer,
taken per hover over the closed set of blocks `StandingBlock` names.
Each block carries its heading,
so the order the blocks read in is that declaration order
rather than a sequence of calls that could drift from it.
Why the blocks nest as they do is the routing's own doc;
what it costs is here.

## Who may hold the system

The outer axis is `BlocCandidacy`,
read off the pass's own grouping,
so the box bars exactly who the fills bar.
That is the one place the box and the fill part company.
A system whose only presence is `neutral` is still painted,
bordered and labelled for it,
while the box drops `Dominated by:` and lists it under `Non-political:`.
Naming the placeholder as holding the system is the statement that block exists to stop making.

## Allies and rivals

Below the holder the split is `ContestSides`,
the one placement every surface reporting a contest routes its blocks through.
It places them over the `BlocAffiliation` the bands judge their contest by:
the live alliance set,
which `PoliticalHolderView.resolveContestAffiliation()` answers for every view of this layer
and the box takes through an injected `FactionAllianceSource` sampled per hover.
So a box cannot disagree with the band beneath it about who is a rival.

The headline stays on the group the map painted the cell for rather than on its alliance,
which keeps the box an explanation of the cell beneath it.
What is taken from the alliances layer is the shape and never its grouping,
which would merge allied runs,
fills and rows.
Only the holder's allies are lifted out:
two rivals allied with each other stay contested.
The allied block is empty on the alliances layer,
where members are already one bloc,
and on an install with nothing grouping factions;
it is dropped there by the rule that drops any block standing over no entries.

## Contested or present

What the block left standing against the holder is *called* is the one thing about it an install decides (`ContestWording`).
Where a mod transfers systems between factions,
those blocs really are competing for the system,
and `Contested by:` reports something the player can watch play out.
Where nothing transfers anything,
the sector's holdings stay as generated,
the same blocs are neighbours indefinitely,
and the block says `Present:`.

The wording comes from `NexerelinContestWording`,
bound where the box is built beside the alliance gate (`ContestWordingSource`) and sampled per hover.
The boxes stand in static fields,
so a wording resolved at construction could settle on the empty mod set of a game not yet stood up,
and head every hover of the session that way.
Nothing else in the box moves with it:
which blocs land in the block,
their order and their scores are settled before the heading is drawn.

## Disposition

Inside the split,
disposition sorts what alliance left standing against the holder.
Why that nesting is the right one is `ContestSides`'s to state.

KMLib's `FactionSetFriendliness` answers whether two blocs are on good terms:
every faction of the one above `RepLevel.NEUTRAL` toward every faction of the other.
It reads both whole memberships (`HolderGrouping.resolveMemberFactionIds`),
not who happens to stand in the hovered system,
so the same two blocs cannot come out friendly over one system and contesting over the next.
The threshold is the base game's own step from indifference to goodwill (`FactionRelations`, KMLib);
a cut taken anywhere else in the scale is one the player is never shown.
`StandingBlockRules` bundles the four rules,
because a friendliness read over one grouping beside an affiliation off another would place blocs under an alliance set no fill was resolved with.

## A bloc of two minds

A bloc whose members do not agree is not listed whole under either heading.
It folds into both,
each row holding only the members on that side (`RoutedStanding`),
and each stating a `StandingFraction` saying how far its heading reaches.
So the bloc stays one named thing under both,
and nothing is orphaned from the grouping the map paints by.
A majority or a lead-member reading would put a heading over factions it is false of.

Which members split a bloc is read off those standing in the hovered system,
and a side with nobody in it heads no row.
Where they agree,
the whole-membership answer places the bloc,
so a bloc whose sour member holds nothing here still contests the system.
`HOLDER` and `ALLIED` never split,
being placed by membership rather than by relation.
Under the identity grouping every bloc is a singleton,
so nothing splits there at all.

One rule covers every fraction:
an alliance row counts its own members,
a faction row counts the holder's,
and both count over whole rosters,
so an alliance reads the same over every system it holds.
Which of the two a row states is `StandingRowResolver`'s call,
that being the one place knowing a group's kind;
a lone-faction group is that faction under another name and states the faction's reading.
Both ends of the range are omitted -
`0/total` and `total/total` say what the heading already did -
so a faction against a lone holder never draws one,
and the whole device belongs to the alliances view.

## The deeper levels

How far this box's account reaches is `resolveDeepestAccountLevel`:
`PATROL_DETAILS`,
the deepest tier the levels declare.
[The shared shape](../../tooltip/README.md#the-shape-every-box-shares) joins that with what the hovered system holds.

What the deeper levels offer is named once for the whole cycle:
"score contributions",
which the framework puts at the foot of the box beside the key that advances it.
Only this layer knows what is down there,
and the account is the same thing at every press.

It is offered only where the system ranks somebody,
since the deeper tiers account for the colonies behind the standings.
That is asked of the ranking and not of the status line above it,
which answers a different question
(`SystemStatusRow` says which).
A system whose colonies have all collapsed is headed **Decivilised** and still ranks whoever holds them -
and those colonies are exactly what a deeper level opens up.

## Colony lines

The parts come from the arithmetic the scores were summed over
(`KnownMarketFootprints.readBreakdownByFaction`,
folding what `MarketWeights` works out per colony),
so the lines always add up to the number the ordinary box and the fills show.
What those parts are is [the market contest's](../README.md#what-a-colony-weighs).
`MarketWeightRowResolver` decides which lines a colony breaks into,
and `MarketFactorText` how one line's numbers read:

- a rating as the player set it;
- a weight on the grid the rest of the box counts in;
- no cut that took nothing;
- a patrol tier's rate stated apart from the total it explains,
  so the box draws the arithmetic quieter than the finding.

Every colony line leads with the glyph the sector map marks that colony's entity with.
What that mark is for,
and why it takes the line's own colour,
is `CellTooltipMark.resolveMarkForMapIcon`'s,
stated there for every surface that lists entities.
What is this box's is where the glyph comes from.
Name and glyph travel as one `EntityNameplate` (KMLib's `kmlib.starsector.entities`),
read on the walk that counted the colony
(`MarketWeightBreakdown.marketNameplate`, through `Markets.readNameplate`)
rather than looked up again where the line is drawn.
So no second market lookup can answer for a different colony,
and one colony's name can never be drawn beside another's glyph.

## The station line

The station line beneath a colony takes a glyph on the same terms
(`StationFactor.stationNameplate`,
read through `EntityNameplates.readNameplate` where the connected-entity scan found the station).
It is the one term of the account named for a thing on the map rather than for a piece of arithmetic,
and a system's stations are told apart on the map by their glyph as much as by their name.
Every other line beneath a colony stays unmarked:
a stability or a size has nothing on the map to point at.

Where that station shares the colony's name,
the line says which of the two it is about
(`MarketFactorText.formatMilitaryStationName`).
A colony on a station is one place to the player and two entries to the economy -
the colony and the military station defending it -
which vanilla names alike.
Both conditions have to hold:
the colony must itself be a station
(`MarketWeightBreakdown.isStationMarket`, the same reading the tie-break prefers planets by),
and the two names must match.
A planet colony sharing its station's name is two places the player can see apart on the map,
so a clarifier there would answer a question they never had.
It reads in the line's own colour rather than the qualifier's gold:
the parentheses already mark the aside,
and the gold is reserved for findings.

## A colony nobody weighed

The box lists one kind of colony no score above it accounts for:
one the economy does not list.
It arrives from `KnownMarketFootprints.readUnweighedColoniesByFaction` as an `UnweighedColony` -
a nameplate,
the colony's own ID,
and the two facts its line calls out that no weight carries:
what kind of place it is and whether it conceals itself.
Why it is never a zeroed `MarketWeightBreakdown` is `KnownMarketFootprints`' to state.

It is named at the foot of the faction's list,
led by the map's glyph like any other colony,
at nought in the quiet shade,
and breaks down into no factors.
The claims box speaks the same sentence for a market its own mechanic never weighed,
for the same reason:
the colony is there and it moved nothing.

## Presence without weight

A faction whose only colony in the system is one of these is listed all the same,
at a nought of its own.
Presence and weight are two questions,
so the ranking (`SystemStandings`) is handed both:
who is in the system
(`HolderPass.readKnownColonyFactionIds`,
the owners of everything the box may name),
beside each faction's footprint.
Anyone present with no footprint takes a `PresenceOnlyFactionStanding`,
the sealed other half of `FactionStanding`.

Presence is read as the wider set rather than as whatever the weighing left over,
so nothing a weighed read comes to exclude can drop a faction out of the listing
while the band goes on counting it.
Nothing about the fill moves:
holding is resolved off the footprints,
which such a faction raises none of.

The nought reads in the quiet shade at both tiers
(`statesUncountedValue`,
and `GroupStanding.hasWeighedMember` for the bloc line over it).
A bloc counts as weighed where any one member was,
so an alliance holding one registered colony beside two unregistered ones keeps an aggregate somebody worked out.

## What is not here

- **[The shared box shape](../../tooltip/README.md)** -
  the decree heading,
  the relations every box samples,
  the status line,
  the colony qualifier and the last-seen remark.
- **[The market contest](../README.md)** -
  the weights and the ranking this box explains.
- **[The claims box](../../claims/tooltip/README.md)** -
  the same shape over the claim mechanic.
- **[The hover box](../../../base/tooltip/README.md)** -
  the framework's box,
  its levels and the cut.
