# The shared box shape (`politicalmap.tooltip`)

What both political hover boxes share:
the shape each sits on,
the decree heading,
the status line,
the words a colony's line calls out,
and the last-seen remark.
Each view injects the box explaining the mechanic its own fills were painted by -
[the domination box](../dominance/tooltip/README.md) on Factions and Alliances,
[the claims box](../claims/tooltip/README.md) on Claims -
and both are written from the parts here.

Part of [the political map](../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [The shape every box shares](#the-shape-every-box-shares)
- [The lines a box is written from](#the-lines-a-box-is-written-from)
- [The status line](#the-status-line)
- [The colony qualifier](#the-colony-qualifier)
- [A word the name already says](#a-word-the-name-already-says)
- [An openly known colony](#an-openly-known-colony)
- [What a box hands the qualifier](#what-a-box-hands-the-qualifier)
- [The last-seen remark](#the-last-seen-remark)
- [What is not here](#what-is-not-here)

## The shape every box shares

Both boxes sit on `PoliticalMapCellTooltip`.
It samples per hover the `BlocRelations` pair every box places blocs by:
the alliance set off the injected `FactionAllianceSource`,
and the disposition,
both read for the sector being hovered.
The pair travels whole and answers the two questions every box asks of it:
which side of the contest a bloc is on (`selectSide`),
and whether a lone faction is on good terms with the holder (`isFactionFriendlyWith`).
A pair held for the session would file a faction under the alliance it left an hour ago,
and sampled per box the two families could disagree.

It also binds the one decree read every box heads itself with.
Any box may have to say a system is held by decree,
and a decree resolved one way on one view and another way on the next would answer one hover two ways a keystroke apart.
A box whose own body already states the decree says so (`isStatingCoreClaimInBody`) and goes without the heading,
so the fact is met once per hover rather than twice.
Both reasons a box goes unheaded -
no decree at all,
and a decree the body states itself -
are settled inside `CoreTerritoryHeading`,
so no call site can drop the heading by claiming there is no decree.

Each box states how far its account reaches (`resolveDeepestAccountLevel`).
`PoliticalMapCellTooltip` joins that constant with what its shape's own read found for the hovered system,
so a box states only the first,
and both shapes combine them the one way.

## The lines a box is written from

Both boxes are written from the same lines:

- `FactionTooltipLine` -
  a faction as something a block lists;
- `TermTooltipLine` -
  one term of a number,
  named and uncrested,
  whichever account is being broken down;
- `FactionTooltipBanner` -
  a faction as a verdict over the whole system;
- `CoreTerritoryHeading` and the status line.

The faction line and banner are the faction holders' (`ownermap.owners.holders.factions`),
and the term line the framework's (`base.tooltip.content`).
The contest wording (`ContestWording`) and the heading a contest block draws under (`ContestBlockHeading`) are here too,
since both families word their rival block the same way.

## The status line

`SystemStatusRow` heads a box over a system nobody runs:
**Decivilised** where a colony's government has collapsed,
**Unpopulated** where nobody lives at all.
It answers a different question of the colony rule from the listing beneath it,
which is why a system holding one wreck reads **Unpopulated** over a box naming the wreck's owner.
`SystemStatusRow` states the rule.
The cell under the box reads habitation too,
so the line and the backdrop it is drawn over always agree.

## The colony qualifier

Both boxes say what they have found out about a place on the line naming it
(`ColonyQualifier`, gold).
One read serves both families,
so neither can call a world decivilised that the other lists as living.

Five words,
in a fixed order:

1. `claim holder` -
   the finding the claims box leads with;
2. the kind -
   `abandoned` for a derelict,
   `decivilised` for a collapse;
3. `undiscovered`;
4. `hidden`;
5. `unlisted`.

A collapse and a derelict reach a listing identically -
unowned,
off-economy and at nought -
and nothing else on either line would tell them apart.
The last three are the three separate ways a colony can be out of plain view (`ColonyConcealment`).

Two of the five never join what stands above them.
`undiscovered` displaces `hidden`:
an undiscovered colony is concealed from the player by that alone.
`unlisted` speaks only where nothing above it held,
or it would repeat itself on every derelict and every decivilised world,
both being off-economy by construction.
The suppression is by the condition holding rather than by where a word ends up stated,
so a station already called *Abandoned Station* says its word inside its own name and still suppresses `unlisted`.

## A word the name already says

Where the colony is already called one of the five,
the occurrence *in the name* is drawn in the qualifier's gold
(`CellTooltipEntryLine.callsOutInLabel`),
and nothing is repeated at the end of the line.
The resolution is untouched:
the same words in the same order,
one of them drawn somewhere else.
At most one stretch is gilded,
the first the name carries,
and the rest close the line as usual -
so an *Abandoned Station* the player has not found gilds **Abandoned** and still reads **undiscovered**.
What counts as the name saying a word is `KmlibStrings.findWholeWordIndex`,
and what is drawn is the name's own spelling of it.

## An openly known colony

`hidden` is withheld from a colony the sector openly points at.
Galatia Academy's station is permanently visible,
while the market hung on it is a stand-in vanilla never registers with the economy and marks hidden to keep off the books -
the same flag a pirate base wears for an entirely different reason.
Nothing on either market parts them,
so the exemption is an identity.
`OpenlyKnownColonyRegistry` holds the entity IDs `MapLayers` seeds it with,
beside a tag another mod can hang on content of its own,
and `ColonyFactsLookup` folds the answer by colony ID off the box's own walk.
The Academy then falls through to `unlisted`,
which is the separation the word was wanted for.

The exemption excuses that one word and nothing else.
To `ColonyVisibility` the Academy is still a hidden colony,
still gated,
and still admitted only by Ancyra settling the system.

## What a box hands the qualifier

Each box hands over the colony a line names as an `AccountedColony`,
carrying the two facts only its own row holds:
whether the colony conceals itself,
and whether the economy lists it.
The claims box reads them off `MarketClaimBreakdown`;
the domination box off `MarketWeightBreakdown.isHiddenMarket` or the `UnweighedColony`.
The economy listing is named by the factory a box calls rather than passed beside the concealment,
since two adjacent flags about being out of view swap without failing.
The claims box also hands in its own `claim holder` wording (`POLITICAL_MAP_TOOLTIP_CLAIM_HOLDER`) as the leading finding,
since the shared vocabulary names no mechanic.

The kind,
the discovery answer and the landmark answer come off the box's own walk of the system (`SystemColonyReading`),
which gathers them with the row's two facts into what the qualifier reads (`ColonyQualifierFacts`).
No row of either box carries any of them.

## The last-seen remark

Every colony line says how old the box's news of it is,
where nobody is looking at the colony as the box is drawn
(`ColonyObservationNotes`, run onto the line as a grey remark through `CellTooltipEntryLine.notedWith`).
In sight the name stands alone;
out of sight it carries `last seen 34 days ago (c206.05.12)`,
closing the line past whatever qualifier it calls out.
The remark is about the box's account rather than about the colony,
and set ahead of the gold it would break a word like `abandoned` away from the name it qualifies.

Two things count as looking at a colony,
being the two routes an observation is ever made by:
the player's fleet is in the system,
or the system's own inhabitants can see the colony -
the owner-aware reading the visibility rule itself uses.
The notes pose that live reading and the register's recall in the shared triad's terms (`ObservationRecency.resolveRecency`),
and hand one axis,
under the colony's own `last seen` lead-in,
to the shared `ObservationNotes`,
which owns when a date is due and what it reads as.

The remark matters most for the colonies a revelation gate admitted on the strength of an observation -
a derelict,
a concealed base -
which would otherwise be listed exactly as a colony the player is standing over.
No visibility rule reads the time:
a colony would blink out of a box the player was reading it in.
The remark is matched to its line by the colony's own ID
(`MarketWeightBreakdown.marketId`, `UnweighedColony.marketId`, `MarketClaimBreakdown.marketId`)
rather than by name,
since vanilla names a station colony and its defending station alike.
It reaches further on the claims box's list than on the domination box's,
for the reason `SystemClaimTooltip` gives.

The remark,
the kind and the discovery answer travel as one value (`SystemColonyReading`),
folded once per box off the very walk of the system the status line comes from.
All three are read per row,
and none can be answered from a claim score or a dominance weight,
so resolved where an account is built they would read the system once for every faction listed.
That value also lays what it knows onto the line
(`SystemColonyReading.describeColony`,
taking the findings the account holds and applying them beside its own date),
and the date is reachable no other way.
An account free to reach the two apart could lay a finding and forget the date,
and a line missing its date reads exactly like a colony somebody is standing over.

## What is not here

- **[The domination box](../dominance/tooltip/README.md)** and
  **[the claims box](../claims/tooltip/README.md)** -
  each box's own blocks and accounts.
- **[The hover box](../../base/tooltip/README.md)** -
  the framework's box,
  its levels and the cut.
- **[What the map may say](../../base/visibility/README.md)** -
  the colony rule,
  the revelation gates and the observation register the remark reads.
