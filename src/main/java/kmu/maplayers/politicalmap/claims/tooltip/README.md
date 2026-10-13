# The claims box (`claims.tooltip`)

The hover box the Claims view shows for a system:
the scored claim contest behind the fill,
its claimant over the factions standing in its alliance,
those on good terms with it outside that alliance,
the rivals who could have taken the system,
and the factions present that never could.
Beneath each faction are the markets it holds the system with,
and beneath each market the terms its claim score is built from.

What every political box shares -
the decree heading,
the relations sampled per hover,
the status line,
the colony vocabulary and the last-seen remark -
is [the shared box shape](../../tooltip/README.md).
The mechanic this box explains is [the claim mechanic](../README.md).

Part of [the political map](../../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../../README.md) for project context.

## Index

- [One account, cut to a depth](#one-account-cut-to-a-depth)
- [One listed contest](#one-listed-contest)
- [The blocks](#the-blocks)
- [What the identity grouping removes](#what-the-identity-grouping-removes)
- [The markets and the claim holder](#the-markets-and-the-claim-holder)
- [Where the economy lists a market](#where-the-economy-lists-a-market)
- [A market nobody weighed](#a-market-nobody-weighed)
- [A market nobody listed](#a-market-nobody-listed)
- [A faction with nothing weighed](#a-faction-with-nothing-weighed)
- [Markets nobody has found](#markets-nobody-has-found)
- [A row with its name blocked out](#a-row-with-its-name-blocked-out)
- [Which factions are named](#which-factions-are-named)
- [The presence term](#the-presence-term)
- [What is not here](#what-is-not-here)

## One account, cut to a depth

`SystemClaimTooltip` opens one account,
read to whatever depth was asked for and cut by the blocks,
on the same terms as [the domination box](../../dominance/tooltip/README.md#one-account-cut-to-a-depth).
The same class settles the one read behind it,
the claimant,
the decree marker,
and the five blocks.
What hangs beneath a faction is asked for only where the level shows a line of one (`isAdmittingAccounts`),
so the shallowest level selects,
ranks and words no faction's markets.
Below it `ClaimScoreRowResolver` works out a market's terms only from `MARKET_STATS`.

That is also where this box's cycle wraps (`resolveDeepestAccountLevel`).
Vanilla settles a claim on a colony's size,
its garrison and how many colonies the faction holds beside it.
No patrol enters the arithmetic,
so the level below has nothing to put in it,
and `F1` collapses from the market stats instead of offering a tier that would redraw the box unchanged.

## One listed contest

The account is handed the whole `ListedClaimContest` rather than the scored read alone,
so the colony rule it draws under is the one the listing above it was projected under.
Read afresh per faction,
an account could withhold a colony the line above it had just named,
and answer two factions of one box under two rules.

Both relations to the claim holder travel in that same value for the same reason:
the `BlocAffiliation` the blocks are routed against,
placed by the same `ContestSides` split the domination box uses,
and the `FactionSetFriendliness` bound to the hovered sector's relations.
The pair is sampled once per hover as `BlocRelations`,
by [the shape every box shares](../../tooltip/README.md#the-shape-every-box-shares).

## The blocks

Two axes place a faction,
and how it stands to the claim holder is the outer one.
`Allied with the claim holder:` takes everyone standing with the holder by alliance,
and `Friendly with the claim holder:` everyone else above `RepLevel.NEUTRAL` with it,
both whatever their eligibility.
The two eligibility blocks divide what neither took.
Why alliance is the outer axis is `ContestSides`' to state.

Where a relation heading leaves the box unable to say which kind a line is,
the line says it (`non-territorial`).
That is declared with the block rather than decided per block,
since it follows from what the block's own heading states.

The four are a closed set (`ClaimContestBlock`),
the claim family's sibling to the domination box's `StandingBlock`.
Each constant carries its heading,
the standings it selects out of the one `ListedClaimContest`,
and whether its lines carry that qualifier.
So the order they read in is a declaration rather than a sequence of calls.
The two sets stay apart because the blocks are not the same blocks:
these name factions by how they stand to a claim and end on eligibility,
those name blocs by how they stand to a holder and end on candidacy.

The claim block itself is outside the set.
It names the claimant rather than selecting over standings,
and what it lists turns on what the banner above it said.
Why an install without Nexerelin needs no branch is `SystemClaimTooltip`'s to state.
It is also where the decree heading is declined:
the claim line names the decreed holder and marks the hold,
so this box states the decree itself.

## What the identity grouping removes

Two blocks the domination box has do not appear here,
and both absences follow from the claims layer pinning the identity grouping.
A standing is always a lone faction,
so no bloc can be of two minds,
nothing folds into two headings,
and no row states a fraction.
And the placeholder owner is never admitted to the claim mechanic,
so it arrives ineligible,
and `Non-territorial:` is already the true statement about it.

## The markets and the claim holder

`ClaimScoreRowResolver` decides the market lines:
a faction's markets in the order the mechanic itself would settle them -
strongest first,
a tie falling to the earlier place in the economy's listing -
so the one representing the faction comes out on top by that order.

Exactly one market in the whole box is called out,
as the `claim holder`:
the one that actually took the system.
Every faction is represented by its strongest,
but only one of those won anything,
and a marker on each would read as several holders of a system that can only have one.
Over a decree it goes unsaid,
since nothing any market scored settled the matter.

Every market line leads with the map's glyph for that market's entity,
scored or not,
on the domination box's terms:
read off the breakdown the market arrived in
(`MarketClaimBreakdown.marketNameplate`,
resolved by `VanillaClaimBreakdownReader` through `Markets.readNameplate`)
rather than looked up again where the line is drawn.
The term lines beneath a market carry no mark -
a size or a garrison bonus has nothing on the map to point at.

## Where the economy lists a market

Every market states where the economy lists it,
as a quiet `[n]` after its name
(`MarketClaimBreakdown.listingPosition`,
numbered across the system's owned markets rather than within one faction's).
The contest is settled on a strictly greater score,
so a tie falls to whichever market the economy reached first,
and nothing else in the box says which that was.

Where the mechanic actually consulted a tie,
the place reads in vanilla's positive or negative shade:
the market reached first having won it,
the rest having lost.
Which ties those are is `ClaimTieOutcomes`,
judged over the whole contest exactly as vanilla's single `max` walk compares.
That walk consults the order at two points:
which market stands for its faction,
and which faction claims the system.
A tie at either is marked;
equal scores anywhere else are not.

Some markets carry a score yet are never marked:

- a hidden one,
  which the walk skips;
- one the economy does not list,
  which the walk never reaches;
- a non-territorial faction's markets against the claimant,
  since they can never take the lead -
  though the tie deciding which of them stands for the faction is still judged;
- the claimant tie under a decree,
  the system having been settled before a market was weighed.

What the player has discovered silences no mark.
A mark answers why two markets on one score are ordered as they are,
and both sides of a judged tie are markets the contest weighed,
which the list carries whatever the player knows of them (`ListedClaimMarkets.isListedMarket`).

## A market nobody weighed

A market the mechanic never weighed -
hidden, or unlisted -
is listed at nought
(`MarketClaimBreakdown.isScoredOnItsOwnAccount`, the one question the box asks of the pair).
It brought nothing to the contest however large it is,
and printing the score it would have carried would sort a market that took no part above the one that took the system.
It is listed rather than dropped because it is a colony the player can see in a faction's colours,
and a hidden one is also among the markets the presence term counts.
It breaks down into no terms.

The nought reads in the quiet shade (`statesUncountedValue`).
Only the number quietens;
the market is named as loudly as its neighbours.
In the list's own colour the nought would pass for a score competed with and lost on.
Which of the two it was is said after the name -
`hidden` or `unlisted`,
in [the shared vocabulary](../../tooltip/README.md#the-colony-qualifier) -
since those are findings about the place.

## A market nobody listed

The unlisted colony is vanilla's own doing:
Galatia Academy is built as a real market on a real station and deliberately never registered,
so the mechanic's economy walk never sees it,
and a box reading the economy alone reports that station as nobody's.

So the contest is read over `Colonies`,
KMLib's shared colony set,
which covers both listings
and keeps out the condition-only market every surveyed rock carries.
The sibling count and every other term stay on the economy's own half.
Admitting an unregistered colony there would raise a real one's score above what the game scores it at,
and could hand the system to a different faction.

## A faction with nothing weighed

A faction holding nothing but unweighed colonies takes a `PresenceOnlyClaimStanding` at nought,
rather than dropping out of the contest,
and the box lists it like any other.
Every block below the claim,
the claimant's own line,
the account beneath each,
and the `F1` hint read every standing rather than the weighed half (`ListedClaimContest.selectFrom`).
A block says how a faction stands to the claim,
not what kind of record the contest gave it:
routing by record kind would file a pirate base's owner beside a Remnant station's,
which are ineligible and eligible respectively.

A territorial faction holding only zero-claim colonies entered the running by the mechanic's own gate and scored nothing,
which the rival block plus a nought says exactly.
That nought reads in the quiet shade (`statesUncountedValue`).
The claimant's line takes the same nought where a decree holds a system its faction is present in through unweighed colonies alone,
in place of the blank column a claimant holding nothing there gets.

## Markets nobody has found

A market on a colony nobody has found is listed all the same wherever the contest counted it into a number the account states.
Its effect is on screen already -
the claim,
the faction's score,
the count of markets the faction was paid a point each for -
and the row is what makes those account for themselves.

Two ways in, then:

- a market the contest **weighed** always qualifies;
- a market it merely **counted as a sibling** qualifies under an account that states that count,
  which is a weighed standing's.
  A presence-only standing has no count on screen,
  so such a row would account for nothing.

What is left off is the market the player knows nothing of that reaches neither
(`ListedClaimMarkets.isListedMarket`,
over `MarketClaimBreakdown.isKnownToPlayer`, `isScoredOnItsOwnAccount` and `isCountedTowardSiblings`).
The sibling count walks the economy's listing,
so the market failing all three is the unregistered colony the walk never reached.
A row for it would be disclosure and nothing else,
and the dev reveal is what states even those in full.

The knowledge half is the rule the faction and alliance tabs fog by,
so all three agree on what the player knows.
The flag carries the composed answer rather than the entity's own:
the player has discovered the market **and**,
for the shapes a bare fog would leak,
somebody has seen it where it stands.
Both halves matter here,
because a listed row may be a concealed colony as well as an undiscovered one -
which is why such a row says **hidden** where the entity has been found and `undiscovered` where it has not.

## A row with its name blocked out

Whether a row may name its market,
and what stands in where it may not,
is `RedactedMarketLines`.
It is asked straight after `ListedClaimMarkets` over the same market,
and held apart from it,
because a market being listed and a market being named have no rule in common.

In the name's place stands one filled block per word,
as long as the word ran (`RedactedSpan`),
so the shape says how many words there were and how long each was,
and nothing about which letters.
The name itself never reaches the line -
only the lengths,
derived off the nameplate and dropped there,
since a value carrying text nothing draws is a value some later change will draw.
A market with no name at all is refused outright,
which would otherwise draw as a glyph over blank space.

The line opens on vanilla's `graphics/fx/question_mark.png` rather than the map's own glyph.
Every other market line opens on an image,
so a line opening on its name would be set apart twice over;
and the map's glyph says what sort of place the colony is -
the very thing being withheld.
It reads in the line's own colour (`CellTooltipMark.resolveMarkInLineColour`).

What such a row keeps is everything that is not the name,
and that part is the account's (`ClaimScoreRowResolver`):
the listing place,
the tie outcome that place settled,
and the word for why the box cannot name it,
off the same walk every other row's words come from.
A claimant standing on such a colony reads `claim holder, undiscovered`,
the map already painting that system in its holder's colours.

The row also gives up the value column and the breakdown,
with two exceptions.
The market its faction stands on keeps its score,
because the faction's own line above already carries it.
A market the contest never scored keeps its nought,
because that nought is the contest's own statement.
It is ranked on its real score all the same,
so its neighbours bound what it scored to within a point or two:
the box declines to *state* a number for a place the player has not found,
and does not pretend the contest ran in another order.

## Which factions are named

A second rule decides the factions above the markets,
asked of a standing as a whole:
a faction is kept where at least one of its colonies is known or was weighed,
and left off entirely where none is
(`ListedClaimContest.selectFrom`, calling `ListedClaimMarkets.isFactionNamingMarket`).
It is the tighter of the two on purpose:
the sibling count cannot name a faction,
being the account's own working,
stated beneath a market that faction was already weighed on.
The two live in one class,
the row rule written as this one plus its extra term,
so a market only the count reaches earns a row under a faction some other market already named,
and never a box of its own.

Naming a faction over an empty account would tell the player exactly what the fog is keeping back.
`F1` is offered only where a standing survives the filter (`hasListedStanding`),
and both ask it through one read of the contest (`SystemClaimTooltip.readListedContest`),
so the key is never advertised over a box the fog has emptied.

A weighed standing therefore always survives,
however little of the system has been explored:
the market carrying it is scored on its own account,
so it has a row -
redacted where nobody has found the colony.
The disclosure that follows is deliberate.
A rival scored on a colony nobody has found is named,
placed in the economy's listing and given its score;
what the fog takes is the colony's identity,
not the fact that a faction is there.
What the filter still removes is a faction present through concealed or unlisted colonies alone,
none of which anybody has seen:
no number on screen is short of it,
and there is nothing but a name to state.

## The presence term

Closing the list is the presence term,
which is the faction's rather than any one market's,
since the mechanic gives every market of a faction the same point per other market it holds there.
It is stated once beneath the markets its count can be checked against,
and worked out from that count
(`Same-faction market bonus   (3 markets) - 1 = +2`,
the subtraction being the market being scored, which is not its own sibling).
The line is working throughout bar the points it arrives at,
so it reads in the quiet shade, name and all.

The count can be checked against the lines above it because of the listing rule:
every market the count counts is one the rule draws,
blocked out where nobody has found it.
The term is withheld for the one market that can contradict it:
an unlisted colony on the list,
which the mechanic never counted,
and beside which the term would read as short by a market on screen.

The resolver shares the entry model and the block vocabulary with the domination box but not its number grammar:
a claim score is a small whole number of points with no grid behind it,
so no rating-to-weight change is stated.

## What is not here

- **[The shared box shape](../../tooltip/README.md)** -
  the decree heading,
  the relations every box samples,
  the status line,
  the colony qualifier and the last-seen remark.
- **[The claim mechanic](../README.md)** -
  the read this box explains.
- **[The domination box](../../dominance/tooltip/README.md)** -
  the same shape over the market contest.
