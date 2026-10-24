# Held-cell band ranking (`dominance.ribbon`)

Where a band's painter and its order come from on a cell the **dominance** mechanic painted.
How many colonies each bloc draws is one shared count for every layer painting holders,
[the holders' band count](../../../ownermap/owners/holders/README.md#the-band-count);
how its runs are laid is [`ownermap.ribbon`](../../../ownermap/ribbon/README.md)'s,
and the geometry is [`base.ribbon`](../../../base/ribbon/README.md)'s.
Which mechanic speaks for a cell on a view that folds claims onto held territory is
[`politicalmap.ribbon`](../../ribbon/README.md)'s.

Part of [the political map](../../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../../README.md) for project context.

## Index

- [The order](#the-order)

## The order

`HeldSystemRibbonPlanner` is the live read behind a held cell's band.
It takes both the weights and the winner through the same `DominancePass` the fills were resolved under,
so a band cannot be ordered under a weighting rule or a visibility setting the player has since moved.
It hands the winner and the ranked blocs to the shared count itself.

`HeldBlocRanker` performs no walk and states no count.
What it reads off the footprints the map already ranked the system by is their order.

The bloc the cell was **painted for** leads,
whatever settled that.
The dominance rule can hand a system to a bloc that leads on none of the weights -
a tie settled by the market nearest the system centre,
or by ID -
so a band re-ranked from the weights alone would open on a bloc the cell is not painted in.

Behind the leader:
descending combined weight,
then ascending ID.
The same two keys in the same order the standings box ranks by,
so the band and the box cannot disagree about who stands second.

A bloc the weights never reached -
one holding nothing but colonies the economy does not list,
which have no industries,
conditions or stability for the arithmetic to read -
takes no place in this ranking.
It is present in the system and counted like any other bloc,
and draws behind the weighed ones in ID order under the shared rule's fallback.

`HeldSystemRibbonPlanner.planHeldSystemRibbon` also says whether the mechanic speaks for a system at all:
empty where no bloc holds a weighed market there.
That answer is what [the held-or-claimed composition](../../ribbon/README.md) turns on.
