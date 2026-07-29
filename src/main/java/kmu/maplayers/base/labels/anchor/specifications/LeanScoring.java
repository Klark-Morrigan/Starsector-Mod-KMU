package kmu.maplayers.base.labels.anchor.specifications;

/**
 * How the search scores a candidate line by how far it leans from the cluster's preferred
 * axis: the penalty a straying line pays, and the ceiling on the lean the score will prefer.
 *
 * @param verticalPenaltyStrength how much font height a line straying from the cluster's
 *                                preferred lean may give up and still win, 0 (pure longest)
 *                                to 1 (a perpendicular line scores zero)
 * @param verticalPenaltyExponent the exponent on the line's deviation from the lean in the
 *                                score, concentrating the penalty toward the perpendicular
 * @param maxSlantDegrees         the ceiling on the cluster-axis lean the score prefers, in
 *                                degrees; 0 forces level labels, 90 lets the lean follow a
 *                                tall cluster's axis to vertical
 */
public record LeanScoring(
        double verticalPenaltyStrength,
        double verticalPenaltyExponent,
        double maxSlantDegrees) {
}
