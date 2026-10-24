# Held-or-claimed band planning (`politicalmap.ribbon`)

Which mechanic speaks for a cell's band on a view whose held territory is extended by claims.
How each mechanic ranks its own cells is [`dominance.ribbon`](../dominance/ribbon/README.md)'s
and [`claims.ribbon`](../claims/ribbon/README.md)'s;
how many colonies each bloc draws is
[the holders' band count](../../ownermap/owners/holders/README.md#the-band-count).

Part of [the political map](../README.md),
in Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [Held or claimed](#held-or-claimed)
- [Why it sits beside the mechanics](#why-it-sits-beside-the-mechanics)

## Held or claimed

`HeldOrClaimedSystemRibbonPlanner` is the composition the Factions
and Alliances views resolve through:
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

## Why it sits beside the mechanics

A claim counts a cell here only where dominance paints nothing.
That is a fact about how the dominance-painted views extend themselves,
not a mechanic of its own,
so the rule belongs to neither mechanic.
Shelved under one,
it would make that mechanic name the other.

So it sits beside them,
as the holder sources that fold claims onto held dominance do in `holders`.
The seam is the composition's own and package-private:
`HeldSystemRibbonPlanner` answers it through a plain public method,
and the dominance mechanic names nothing of the views it is composed into.
The build closes `dominance` and `claims` to each other.
