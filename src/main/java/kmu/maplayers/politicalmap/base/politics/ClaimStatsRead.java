package kmu.maplayers.politicalmap.base.politics;

import java.util.Map;

/**
 * What one whole-sector claim walk yields: the totals the claims picker sorts and labels each bloc
 * by, and the systems each of those blocs claims.
 *
 * <p>The claims counterpart to {@link DominanceStatsRead}, and a separate record for the same reason
 * the two stats records are separate: the claims vocabulary has no presence to name and the
 * dominance one has no claim, so neither read may be widened by the other's answers.
 *
 * <p>The two travel together because they are one reading of the sector, taken under one set of
 * knobs. Resolving them through two entry points would walk the sector twice and let a surface hold
 * totals from one reading beside a system set from another - two answers to "what does this bloc
 * claim" for a picker row and the cells it lights.
 *
 * @param statsByBlocId       each claiming or living bloc's whole-sector totals, keyed by bloc id in
 *                            walk order
 * @param claimedSystemIndex  the systems each bloc claims - never a wider key set than the stats
 *                            beside it, a bloc listed for its colonies alone claiming nowhere
 */
public record ClaimStatsRead(
    Map<String, ClaimStats> statsByBlocId,
    BlocPresenceIndex claimedSystemIndex) {
}
