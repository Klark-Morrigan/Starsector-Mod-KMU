package kmu.maplayers.ownermap.render.rebuild;

/**
 * What one frame's staleness question answered: how much of the cache is stale, and the values a
 * rebuild would carry forward - the inputs its cells would be cut under, and the two keys it would
 * record as built from, the content key holding the picks it would bake under.
 *
 * <p>A value crossing between two steps rather than fields on the cache, because the question is
 * asked before a reading of the sector is opened and answered after. Nearly every frame answers
 * "nothing stale" and must open no reading at all, so the two cannot be one step - and what the
 * decision found has to reach the rebuild without being re-derived, or the rebuild would sample
 * the settings a second time and could cut under one reading while the decision was taken under
 * another.
 *
 * <p>It carries this frame's sampled readings and the answers taken off them, never the baselines
 * those answers were compared against: what the standing map was built under stays behind
 * {@link OwnerMapBuildLedger}, so nothing downstream can read a baseline and reach its own
 * verdict.
 *
 * @param cellCut    what the cells would be cut from now, sampled once
 * @param contentKey everything the clusters would be styled under, the picks sampled once
 * @param ownersKey  only what reaches the resolve of who owns what, so a rebuild can tell a pick
 *                   that moved the paint from one that moved the owners
 * @param staleness  how much is owed - {@link Staleness#NOTHING} is the frame that stops at the
 *                   decision
 */
record StaleHalves(
    CellCutInputs cellCut,
    ContentKey contentKey,
    OwnersKey ownersKey,
    Staleness staleness) {
}
