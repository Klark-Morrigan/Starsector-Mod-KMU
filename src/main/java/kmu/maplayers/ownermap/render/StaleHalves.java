package kmu.maplayers.ownermap.render;

import kmu.maplayers.ownermap.ContentInputs;

/**
 * What one frame's staleness question answered: which of the cache's two halves are stale, and the
 * values a rebuild would carry forward - the inputs its cells would be cut under, the picks it
 * would bake under, and the two revisions it would advance to.
 *
 * <p>A value crossing between two steps rather than fields on the cache, because the question is
 * asked before a reading of the sector is opened and answered after. Nearly every frame answers
 * "nothing stale" and must open no reading at all, so the two cannot be one step - and what the
 * decision found has to reach the rebuild without being re-derived, or the rebuild would sample
 * the settings a second time and could cut under one reading while the decision was taken under
 * another.
 *
 * <p>It carries this frame's sampled readings and the answers taken off them, never the baselines
 * those answers were compared against: what the standing map was built under stays behind the
 * decider that holds it, so nothing downstream can read a baseline and reach its own verdict.
 *
 * @param cellCut         what the cells would be cut from now: the reachable-set revision, the
 *                        seed knobs and the visibility rules, sampled once
 * @param isCellCutStale  whether those differ from what the standing cells were cut from
 * @param contentInputs   the sidebar preferences a rebuild would bake under, sampled once
 * @param contentRevision the fold of every input the clusters are styled under
 * @param holdingRevision the fold of only those inputs that reach the resolve of who holds
 *                        what, so a rebuild can tell a pick that moved the paint from one that
 *                        moved the holding
 * @param isContentStale  whether anything at all is owed, the cut included - false is the frame
 *                        that stops at the decision
 */
record StaleHalves(
    CellCutInputs cellCut,
    boolean isCellCutStale,
    ContentInputs contentInputs,
    int contentRevision,
    int holdingRevision,
    boolean isContentStale) {
}
