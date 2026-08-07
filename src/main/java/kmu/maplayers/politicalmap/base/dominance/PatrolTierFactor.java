package kmu.maplayers.politicalmap.base.dominance;

/**
 * One patrol tier's part of a market's patrol weight: how many patrols of that tier the
 * colony fields, what one of them is worth, and what the tier folded in at.
 *
 * <p>Split per tier rather than summed because one heavy patrol and four light ones can be
 * worth the same and are not the same force fielded; the tiers are what make the total
 * explicable.
 *
 * @param count        the patrols of this tier the colony fields
 * @param weight       the size points one patrol of this tier is worth
 * @param contribution this tier's share of the market's weight in size points, after the
 *                     stability cut
 */
public record PatrolTierFactor(
    int count,
    double weight,
    double contribution) {
}
