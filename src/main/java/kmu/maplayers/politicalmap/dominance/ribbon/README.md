# Held-cell band counting (`dominance.ribbon`)

Where a band's colony counts come from on a cell the **dominance** mechanic painted, plus the rule
deciding which mechanic speaks for a cell at all. The grammar those counts are turned into is
[`base.ribbon`](../../base/ribbon/README.md)'s; the geometry is
[`base.render.ribbon`](../../base/render/ribbon/README.md)'s.

Part of [the political map](../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../README.md) for project context.

## Index

- [The count is already in hand](#the-count-is-already-in-hand)
- [The order](#the-order)
- [Held or claimed](#held-or-claimed)

## The count is already in hand

`HeldCellRibbons` performs no walk and states no second rule. The map already sampled the system's
colonies to decide who holds it, and `MarketFootprint.marketCount` is how many that sample banked
for each bloc - so every market a band reports is one the dominance score was summed over. That is
what makes a held cell's band a readout of the very sample the fill beneath it was decided from.

`HeldSystemRibbonPlanner` is the live read behind it, taking both the economy read and the winner
through the same `DominancePass` the fills were resolved under - so a band cannot be counted under a
weighting rule or a dev reveal the player has since moved. One extra walk of a system's own market
list, not of the sector's.

## The order

The bloc the cell was **painted for** leads, whatever settled that. The dominance rule can hand a
system to a bloc that leads on none of the weights - a tie settled by the market nearest the system
centre, or by id - so a band re-ranked from the weights alone would open on a bloc the cell is not
painted in.

Behind the leader: descending combined weight, then ascending id. The same two keys in the same
order the standings box ranks by, so the band and the box cannot disagree about who stands second.

## Held or claimed

`HeldOrClaimedSystemRibbonPlanner` is the composition the faction and alliance views resolve
through: each system counted by whichever mechanic painted it. It asks the held side first and falls
through to the claim contest only where the held side **declines**.

That decline is the whole hinge, and it is why `HeldSystemRibbonSource` exists as a narrow seam
beside the planner interface. A planner can only ever answer with a plan; what this composition
needs is the one thing a plan cannot say - whether the mechanic speaks for the system at all. So the
source answers `Optional`, where empty means "no bloc holds a counted market here" and a present
`RibbonPlan.NONE` means "held, but with no contest to report".

Both answers come off one economy walk, which is what keeps the distinction from costing a second
one. The composition lives on the dominance side rather than between the two mechanics because a
claim counts a cell here only where dominance paints nothing - a fact about how the
dominance-painted views extend themselves, not a mechanic of its own.
