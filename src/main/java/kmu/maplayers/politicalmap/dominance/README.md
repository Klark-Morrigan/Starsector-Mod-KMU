# The market contest (`politicalmap.dominance`)

Who holds a system on the Factions and Alliances views:
the bloc whose colonies there weigh the most.
This package is the rule that weighs a colony,
the rule that ranks the weights,
and the whole-sector numbers the spotlight picker sorts by.

Part of [the political map](../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [What a colony weighs](#what-a-colony-weighs)
- [Only the economy's colonies are weighed](#only-the-economys-colonies-are-weighed)
- [Who holds a system](#who-holds-a-system)
- [A dead heat](#a-dead-heat)
- [One pass, one rule](#one-pass-one-rule)
- [Under a spotlight](#under-a-spotlight)
- [The picker's numbers](#the-pickers-numbers)
- [What is not here](#what-is-not-here)

## What a colony weighs

`MarketWeights`, in `weighting`, works out one colony's weight as the sum of three factors:
its base size,
any attached station,
and the patrols a functional patrol HQ fields (the `$patrol` gate).
Each factor is cut for low stability by its own penalty.
A hidden market's base size counts by its real size or by a fixed token,
as the player chooses.

The rates and penalties are LunaLib settings.
`DominanceRules` reads them once per pass,
so every system in a pass is weighed under one reading of them.

The answer is always a `MarketWeightBreakdown`, never a bare number.
The fill sums it and the hover box reads its parts,
so the box's lines always add up to the number the fill was ranked by.
The grid a worth in size points is rounded onto (`MarketWeights.roundToWeight`) belongs to the same arithmetic,
which is why every comparison downstream is exact.

The build keeps `weighting` from naming the rest of this package,
which weighs through it.

## Only the economy's colonies are weighed

Every term of a dominance weight is economy-fed:
industries,
conditions,
computed stability.
A colony the economy does not list has none of them,
so there is nothing to weigh.
Admitting one would hand its owner weight nobody worked out,
and could change who the map paints the system for.

So `KnownMarketFootprints` splits a system's known colonies on that one fact.
`readWeighedColonies` is the listed ones,
folded into each faction's `MarketFootprint`.
`readUnweighedColoniesByFaction` is the rest,
each an `UnweighedColony`:
a name and an identity,
which cannot be summed into anything.
The two are exact complements of one projection,
so neither can gain a colony the other keeps.

A bloc living in a system only through unlisted colonies therefore wins nothing there and is still present.
Everything downstream reads it that way:

- the spotlight draws it hatched ([below](#under-a-spotlight));
- the domination box lists it at nought ([the domination box](tooltip/README.md#presence-without-weight));
- the band counts it ([held-cell bands](ribbon/README.md#the-order));
- the picker lists it and greys it ([below](#the-pickers-numbers)).

## Who holds a system

`SystemDominance` compares the footprints on four levels,
each breaking a tie in the one above:

1. combined weight;
2. heaviest single market;
3. planet-only weight, so planets outrank stations;
4. a tie-break over the tied IDs.

It is a pure rule with no Starsector types.
`SectorPolitics` feeds it the footprints,
then colours the winner through the pass's palettes.

`HolderRankingRules` carries the two rules a ranking is settled by:
who may win (`BlocCandidacy`) and who wins a dead heat.
They travel as one value,
so no reader can rank a system with one rule from the painting pass and the other from somewhere else.

`BlocCandidacy` bars one owner from winning:
`neutral`,
the placeholder vanilla hands every abandoned station,
derelict and collapsed colony.
Barred,
it can neither take a system from a bloc that scores nor win a tie against one.
Barred from winning is not barred from holding.
Where nobody else is present the ranking reopens,
and a system only `neutral` lives in keeps its fill,
border and label.

The bar is on the candidate and never on the weight,
so every score the map and the picker show is the one the weighting produced.
The bloc form reads through the grouping's colour faction,
so an alliance is judged by the faction it stands as.

## A dead heat

`MarketProximityTieBreak` settles a tie by which bloc holds the market nearest the system's centre.
A bloc ID is a faction ID on one view and an alliance's own ID on the other,
so a tie falling to the lowest ID could paint one system for different holders on the two views.
A physical measure is the same on both.

Distance is read off the orbit rather than the body's position:
the circular-orbit radii summed up the focus chain to the star nearest the centre.
A planet is as far out as its orbit wherever it sits on it,
so the answer is the same every frame.
Two blocs whose nearest markets orbit at the same depth fall back to the lowest colour-faction ID,
which does not change with the grouping either.

The tie is settled among `readWeighedColonies`,
the very colonies the tied weights came from.
The comparator reads no geometry until it is asked to compare,
which happens only on a genuine tie.

## One pass, one rule

`DominancePass` is a `HolderPass` plus the `DominanceRules` it weighs under.
The holder half is what any owner-painted layer needs:
the sector,
the grouping,
the colony rule,
and the one walk of each system.
The rules are what only a layer painted by market weights adds.

Whoever opens the pass reads the rules once and hands the pass down,
so no system can drift onto another rule mid-pass.
Its per-system reads take a system rather than a sector,
so a reader holding the pass cannot walk the sector again.

## Under a spotlight

`FilteredPolitics` resolves the map while the picker spotlights one bloc.
The plain pass keeps each system's single winner and drops the rest,
which would erase the spotlit bloc from every system a rival holds.
Under a spotlight the bloc stays drawn wherever it holds a colony:
solid where it wins,
hatched where it does not.
Every other system keeps its real holder and recedes.

Presence there is read off the colonies,
not off the weights,
because [the weights only see listed colonies](#only-the-economys-colonies-are-weighed).
The weights themselves are left alone.
Widening them would put such a bloc into the ranking that hands out systems,
and a spotlight must never move a fill.

The spotlit bloc's whole footprint carries one synthetic key,
which `SpotlitOwners` mints and recognises.
The geometry fuses it into one territory under one border,
and the contested systems are reported apart (`HolderResolution.contestedSystemKeys`),
so the fill splits per cell inside that border.

## The picker's numbers

`DominanceStats` holds the four numbers the picker sorts and labels a bloc by:

- domination - how many systems it wins;
- presence - how many it lives in;
- score - its summed dominance weight;
- market size - its summed raw colony size.

Domination and score count only the colonies the contest weighed.
Presence and market size count every colony the player may be shown,
an unlisted one included.
So a bloc can be present at a score of nought,
and `isPaintingNothing` greys it in the picker.

`DominanceStatsAggregator` folds them in one walk of the sector,
over the same `DominancePass` the fills are resolved under,
and `DominanceStatsRead` carries them with the systems each bloc lives in.
`DominanceSortModes` is the vocabulary the picker ranks them by.

## What is not here

- **[`dominance/ribbon`](ribbon/README.md)** -
  the band's order on a cell this contest painted.
- **[`dominance/tooltip`](tooltip/README.md)** -
  the domination box,
  which explains a fill from the same arithmetic.
- **`dominance/standings`** -
  one hovered system's ranked standings,
  which that box lists.
- **[`claims`](../claims/README.md)** -
  the peer mechanic,
  and what the Factions and Alliances views fold onto the held map.
- **`holders`** -
  `DominanceHolderProvider` and `DominanceSystemHolderResolve`,
  which state this contest to the tier's holder source;
  see [the ownership sources](../README.md#the-ownership-sources).
