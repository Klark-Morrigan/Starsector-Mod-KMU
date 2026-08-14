# Claimed-cell band counting (`claims.ribbon`)

Where a band's colony counts come from on a cell the **claim** mechanic painted. The grammar those
counts are turned into is [`base.ribbon`](../../base/ribbon/README.md)'s; the geometry is
[`base.render.ribbon`](../../base/render/ribbon/README.md)'s.

Part of [the political map](../../README.md), in Klark Morrigan's Utilities; see the
[mod README](../../../../../../../../README.md) for project context.

## Index

- [Counting from the contest, not from the map](#counting-from-the-contest-not-from-the-map)
- [What counts](#what-counts)
- [Blocs and their order](#blocs-and-their-order)

## Counting from the contest, not from the map

A held cell already carries its counts. A claimed one carries none: the claim path resolves one
faction id per system through vanilla's `Misc.getClaimingFaction` and says nothing about the
colonies behind it.

So `ClaimCellRibbons` reads the contest's own standings rather than counting markets on its own
account. A second walk under a second rule would be a second opinion about which markets a cell
means, and the hover box explaining a claim reads the same standings - one walk, one list, two
readers, and no way for the box and the band beside it to disagree.

`ClaimedSystemRibbonPlanner` is the live read, and the one thing it decides is which bloc counts as
the painter: the **claimant**, not the top standing. Read it off the wrong faction and a system
where the claimant is the junior standing draws no band, or a lone claimant's cell draws one the
fill never earned.

## What counts

A faction counts its standing market plus every sibling that both told on the score and is a colony
the player knows about. Two exclusions fall out of that, both the design's answer rather than an
omission:

- **A colony the economy does not list** took no part in any term - the mechanic's walk never
  reached it - so it adds no segment however plainly it sits on the map.
- **A colony the player has not found** is left out, so a band cannot count out holdings the rest of
  the map declines to show. The standing market itself withholds nothing: a market takes a standing
  only where it is held in the open.

A faction present through unweighed markets alone therefore never reaches the band at all - the
contest gives it no standing, so it contributes no run and does not open the presence gate on a cell
that is otherwise a lone claimant's.

One consequence worth naming: a concealed but discovered colony draws a segment on a **dominated**
cell, where the dominance weighting counts it, but only feeds the sibling term on a **claim** cell,
where the mechanic skips concealed markets before scoring. Two mechanics genuinely weigh it
differently and each is reported as it is.

## Blocs and their order

Allied factions fold into one run, since a bloc is what the cell was painted for and two allies'
colonies are one bloc's presence. The fold is **by count rather than by score**, so the double
counting a summed score would invite - a sibling term already counting what is being added beside it
- cannot arise.

Blocs come out in the contest's own ranking, each at the place of its best-placed member, so the
band agrees with the fill about who leads wherever a score settled the claim. Under a decree no
score settled it, and nothing is hoisted to say otherwise: the fill states the decree and the band
states the contest beneath it.
