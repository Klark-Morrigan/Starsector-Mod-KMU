package kmu.maplayers.politicalmap.dominance.weighting;

/**
 * The base-size part of one market's dominance weight: the size rating that entered the
 * weight, what that rating was worth once the colony-size multiplier and stability had
 * their say, and how much of it low stability took.
 *
 * <p>Every counted market carries this part - a colony always has a size - so unlike the
 * station and patrol parts it is never absent.
 *
 * <p>The raw colony size is carried beside the rating because the two part company exactly
 * when the player pins hidden markets to a fixed token: a reader then sees both how big the
 * colony is and what it counted as, which is the difference that explains an otherwise
 * baffling weight.
 *
 * @param rawMarketSize            the colony's own size, as the economy reports it
 * @param sizeRating               the rating that entered the weight - the raw size, or the
 *                                 fixed token a hidden market folds in at under Fixed
 *                                 hidden-market scaling
 * @param contribution             this part's share of the market's weight in size points,
 *                                 after the colony-size multiplier and the stability cut
 * @param stabilityPenaltyFraction the share of its worth low stability removed, 0..1; zero
 *                                 while the master stability weighting is off
 */
public record BaseSizeFactor(
    int rawMarketSize,
    double sizeRating,
    double contribution,
    double stabilityPenaltyFraction) {
}
