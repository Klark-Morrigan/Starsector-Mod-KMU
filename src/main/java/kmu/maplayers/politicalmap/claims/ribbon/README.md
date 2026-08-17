# Claimed-cell band ranking (`claims.ribbon`)

Where a band's painter and its order come from on a cell the **claim** mechanic painted. How many
colonies each bloc draws is one shared rule's answer for every layer, in
[`base.ribbon`](../../base/ribbon/README.md); the geometry is
[`base.render.ribbon`](../../base/render/ribbon/README.md)'s.

Part of [the political map](../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../README.md) for project context.

## Index

- [Reading the contest, not just its winner](#reading-the-contest-not-just-its-winner)
- [Blocs and their order](#blocs-and-their-order)

## Reading the contest, not just its winner

The claim mechanic publishes one faction id per system through vanilla's `Misc.getClaimingFaction`
and nothing else. A band drawn from that alone would know who painted the cell and nothing about who
else is standing in it.

So `ClaimedSystemRibbonPlanner` reads the whole contest - the same read the hover box explaining a
claim is written from - and takes two things out of it. The **painter** is the *claimant*, not the
top standing: read it off the wrong faction and a system where the claimant is the junior standing
draws no band, or a lone claimant's cell draws one the fill never earned. The **ranking** is the
contest's own, which `ClaimCellRibbons` folds into blocs.

The reader is built over the bake's own colony walk, so the contest and the count beneath it read
one reading of the system rather than two.

## Blocs and their order

Blocs come out in the contest's own ranking, each at the place of its **best-placed member**, so the
band agrees with the fill about who leads wherever a score settled the claim. Two allies therefore
take one place rather than two, ahead of a rival the junior of them trails.

Under a decree no score settled the claim, and nothing is hoisted to say otherwise: the fill states
the decree and the band states the contest beneath it. A decreed claimant that scored nothing sits
where the contest put it, which is the foot.

A bloc the contest never listed - present only through colonies the mechanic's walk did not reach -
is not ranked here at all. It still draws, behind the ranked blocs in id order, under the shared
rule's own fallback.
