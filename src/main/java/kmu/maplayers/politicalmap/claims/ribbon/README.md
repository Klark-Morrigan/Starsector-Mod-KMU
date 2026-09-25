# Claims-layer band ranking (`claims.ribbon`)

Where a band's painter and its order come from on the **claims** layer -
on a cell the mechanic painted,
and on one it left bare.
How many colonies each bloc draws is one shared rule's answer for every layer,
in [`ownermap.ribbon`](../../../ownermap/ribbon/README.md);
the geometry is [`ownermap.render.ribbon`](../../../ownermap/render/ribbon/README.md)'s.

Part of [the political map](../../README.md),
in Klark Morrigan's Utilities;
see the [mod README](../../../../../../../../README.md) for project context.

## Index

- [Reading the contest, not just its winner](#reading-the-contest-not-just-its-winner)
- [A system nobody claims](#a-system-nobody-claims)
- [Blocs and their order](#blocs-and-their-order)

## Reading the contest, not just its winner

The claim mechanic publishes one faction ID per system through vanilla's `Misc.getClaimingFaction` and nothing else.
A band drawn from that alone would know who painted the cell
and nothing about who else is standing in it.

So `ClaimedSystemRibbonPlanner` reads the whole contest -
the same read the hover box explaining a claim is written from -
and takes two things out of it.
The **painter** is the *claimant*,
not the top standing:
read it off the wrong faction and a system where the claimant is the junior standing draws no band,
or a lone claimant's cell draws one the fill never earned.
The **ranking** is the contest's own,
which `ClaimCellRibbons` folds into blocs.

The reader is built over the bake's own colony walk,
so the contest and the count beneath it read one reading of the system rather than two.

## A system nobody claims

Vanilla leaves a settled system unclaimed for several reasons -
a pirate haven,
a player colony and a decivilised world all resolve no claimant.
Which reasons,
and the mistake that is easy to make about them,
are [`ownermap.owners.holders`](../../../ownermap/owners/holders/README.md)'s to state.

What follows here is that this layer's fill *is* the claim,
so over those systems it says nothing at all,
and the band is the only readout there is.

`ClaimedSystemRibbonPlanner` therefore plans such a system with **no painter** rather than refusing it.
That absence is a case of its own,
not an ID no bloc happens to carry,
and what the shared rule makes of it -
there being nobody to be a rival of -
is [`ownermap.ribbon`](../../../ownermap/ribbon/README.md)'s to state.
Which cells reach the planner at all is [`ownermap.render.ribbon`](../../../ownermap/render/ribbon/README.md)'s gate,
and it reads the pass's inhabitation scan for the same reason.

A system nobody claims *and* nobody lives in counts nought for every bloc,
so it draws nothing under either arm -
the band reaches settled space and stops there.

## Blocs and their order

Blocs come out in the contest's own ranking,
each at the place of its **best-placed member**,
so the band agrees with the fill about who leads wherever a score settled the claim.
Two factions take one place only where the pass's grouping already folds them into one bloc -
the alliances view,
over a claimed cell it extends an alliance's territory with.
On the claims layer the grouping is identity,
so nothing folds:
allies stand at their own places in their own colours,
and all their standing together reaches is the length the runs are laid at.

Under a decree no score settled the claim,
and nothing is hoisted to say otherwise:
the fill states the decree and the band states the contest beneath it.
A decreed claimant that scored nothing sits where the contest put it,
which is the foot.

A bloc the contest never listed -
present only through colonies the mechanic's walk did not reach -
is not ranked here at all.
It still draws,
behind the ranked blocs in ID order,
under the shared rule's own fallback.
