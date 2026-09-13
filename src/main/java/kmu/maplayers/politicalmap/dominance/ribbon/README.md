# Held-cell band ranking (`dominance.ribbon`)

Where a band's painter and its order come from on a cell the **dominance** mechanic painted,
plus the rule deciding which mechanic speaks for a cell at all.
How many colonies each bloc draws is one shared rule's answer for every layer,
in [`base.ribbon`](../../base/ribbon/README.md);
the geometry is [`base.render.ribbon`](../../base/render/ribbon/README.md)'s.

Part of [the political map](../../README.md),
in Klark Morrigan's Utilities;
see the [mod README](../../../../../../../../README.md) for project context.

## Index

- [The order](#the-order)
- [Held or claimed](#held-or-claimed)

## The order

`HeldCellRibbons` performs no walk and states no count.
What it reads off the footprints the map already ranked the system by is their order,
and `HeldSystemRibbonPlanner` is the live read behind it -
taking both the weights and the winner through the same `DominancePass` the fills were resolved under,
so a band cannot be ordered under a weighting rule or a visibility setting the player has since moved.

The bloc the cell was **painted for** leads,
whatever settled that.
The dominance rule can hand a system to a bloc that leads on none of the weights -
a tie settled by the market nearest the system centre,
or by ID -
so a band re-ranked from the weights alone would open on a bloc the cell is not painted in.

Behind the leader:
descending combined weight,
then ascending id.
The same two keys in the same order the standings box ranks by,
so the band and the box cannot disagree about who stands second.

A bloc the weights never reached -
one holding nothing but colonies the economy does not list,
which have no industries,
conditions or stability for the arithmetic to read -
takes no place in this ranking.
It is present in the system and counted like any other bloc,
and draws behind the weighed ones in ID order under the shared rule's fallback.

## Held or claimed

`HeldOrClaimedSystemRibbonPlanner` is the composition the faction
and alliance views resolve through:
each system counted by whichever mechanic painted it.
It asks the held side first and falls through to the claim contest only
where the held side **declines**.

That decline is the whole hinge,
and it is why `HeldSystemRibbonSource` exists as a narrow seam beside the planner interface.
A planner can only ever answer with a plan;
what this composition needs is the one thing a plan cannot say -
whether the mechanic speaks for the system at all.
So the source answers `Optional`,
where empty means "no bloc holds a weighed market here" and a present `RibbonPlan.NONE` means "held,
but with no contest to report".

Both halves are built from the one set of inputs,
so they share the bake's single walk of each system.
The composition lives on the dominance side rather than between the two mechanics
because a claim counts a cell here only where dominance paints nothing -
a fact about how the dominance-painted views extend themselves,
not a mechanic of its own.
