# The claim mechanic (`politicalmap.claims`)

Who claims a system,
as vanilla's "system claimed by faction" mechanic answers it -
the claim the colony-survey panel warns about.
The Claims view paints these claims solid;
the Factions and Alliances views fold them onto the held map as unfilled extensions.
This package resolves them,
and holds the whole-sector numbers the Claims view's picker sorts by.

Part of [the political map](../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [Held and claimed end up equal](#held-and-claimed-end-up-equal)
- [One reader per pass](#one-reader-per-pass)
- [Wider than vanilla scores](#wider-than-vanilla-scores)
- [How vanilla resolves a claimant](#how-vanilla-resolves-a-claimant)
- [Inhabited does not imply claimed](#inhabited-does-not-imply-claimed)
- [Under a spotlight](#under-a-spotlight)
- [The picker's numbers](#the-pickers-numbers)
- [What is not here](#what-is-not-here)

## Held and claimed end up equal

`SectorClaims` is the claim twin of [the market contest](../dominance/README.md)'s `SectorPolitics`.
Where `SectorPolitics` reads who *holds* a system,
`SectorClaims` reads who *claims* it,
then routes that claimant through the same grouping and palette.
So a claimed system and a held one of the same bloc end up equal,
and fuse downstream.

## One reader per pass

The claimant comes from KMLib's `ClaimReader` port,
the usual way KM code inverts a third-party read that only answers inside a running game.
What a claim-reading source holds is not a reader but a `ClaimReaderSource`.
A reader answers off the colonies behind it,
so one is opened over the pass being resolved and discarded with it.
A reader kept for the life of the game would go on answering off a sector that has since moved on,
and would walk every system again for colonies the pass has already read.

`PassClaimReaders.openClaimReaderOver` opens one,
so the walk and the pass's own `ColonyKnowledge` travel together,
and the claim half is always shown the sector the held half was.
The reader is handed that knowledge through KMLib's `KnownColonyReader` port
rather than naming the rule itself:
what may be told of a colony is the map's judgement.

Its vanilla binding mirrors `Misc.getClaimingFaction` step for step rather than calling it,
because one computation has to answer *who* claims a system for the fills
and *why* for [the claims box](tooltip/README.md).
Sharing it is what stops the fill and the box over it naming different claimants -
on the memory-flag override,
and on the iteration-order tie the mechanic settles equal scores by.

## Wider than vanilla scores

Step for step describes the *claimant*,
which is all the fill takes.
The standings the same read carries for the hover box go wider than vanilla scores,
in two directions,
and neither can move the claimant.

The player's colonies are scored,
forced non-territorial so they can never move the winner.
A box that dropped them would report a system the player holds a colony in as one they have no presence in.

A colony the economy does not list is carried too,
as vanilla builds Galatia Academy.
A box that dropped it would leave a station the player can see on the map out of the account.
It is carried the way a concealed market is:
scored for nothing and counted toward nothing,
so it can neither become a claimant nor move the score of one.

## How vanilla resolves a claimant

Vanilla resolves a claimant two ways:
an explicit `$claimingFaction` memory flag,
or the top territorial market in the system.
The map shows exactly what the mechanic resolves and invents nothing.
A marketless system (unpopulated or decivilised) only resolves through the flag,
which is rare in practice,
so claims mostly attach to inhabited systems.

## Inhabited does not imply claimed

The reasons are easy to get wrong,
so they are named exactly.
`Misc.getClaimingFaction` walks `getEconomy().getMarkets(location)` and skips a market on three separate tests:

| Test | What it excludes |
| --- | --- |
| `curr.isHidden()` | pirate and Path bases, which `PirateBaseIntel` and `LuddicPathBaseIntel` both create with `setHidden(true)` |
| `curr.getFaction().isPlayerFaction()` | every player colony, before territoriality is even read |
| no `punitiveExpeditionData.territorial` | the Remnant, derelicts, scavengers, mercenaries, the Dweller, `neutral`, and the rest of the thirteen vanilla factions carrying no such block |

And the walk reads the *economy's listing*,
so a colony never registered with it -
as vanilla builds Galatia Academy -
is not seen at all,
whatever its owner.

A system that resolves nothing is left unheld.
That is the mechanic answering correctly,
not a gap to paper over.
What must not follow from it is the *render* reading the missing holder as an empty system:
the factionless classification is made against the pass's inhabited-system set instead,
see [`render.style`](../../ownermap/render/style/README.md).

## Under a spotlight

`FilteredClaims` decides which claims a spotlight moves,
once for both claim-reading sources.
The spotlit bloc's claims carry its one synthetic spotlight key,
and every rival's claim keeps its own,
so the rivals fade with the rest of the background.

## The picker's numbers

`ClaimStats` holds the two numbers the Claims view's picker sorts and labels a bloc by:
how many systems it claims,
and its summed colony size.
`ClaimStatsAggregator` folds them in one walk of the sector,
reading the claim half through the pass's own colony walk,
so a claim count and a colony size never describe a system at two different moments.
`ClaimStatsRead` carries them with the systems each bloc claims,
and `ClaimSortModes` is the vocabulary the picker ranks them by.
Who the picker lists is `ClaimsView`'s to state.

## What is not here

- **[`claims/ribbon`](ribbon/README.md)** -
  the band's order on a claims-layer cell.
- **[`claims/tooltip`](tooltip/README.md)** -
  the claims box,
  which explains a claim from the same read.
- **`holders`** -
  `ClaimsHolderProvider`,
  `ClaimAugmentedHolderProvider` and `ClaimSystemHolderResolve`,
  which state this mechanic to the tier's holder source;
  see [the ownership sources](../README.md#the-ownership-sources).
- **[The market contest](../dominance/README.md)** -
  the peer mechanic the held map is painted by.
