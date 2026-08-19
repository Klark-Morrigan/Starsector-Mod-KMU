package kmu.maplayers.politicalmap.base.dominance;

import kmlib.starsector.entities.EntityNameplate;

import java.util.Optional;

/**
 * The arithmetic behind one market's dominance weight, kept whole: which of the three weight
 * factors ran for the market, what each was worth, and what each lost to low stability.
 *
 * <p>The scalar weight {@link KnownMarketFootprints} folds into a {@link MarketFootprint} is
 * the sum over this value rather than a second computation beside it, so a reader of the
 * parts and a reader of the total can never be shown numbers that disagree. Everything the
 * weight read works out and would otherwise discard - the raw-against-weighted split, each
 * factor's own penalty, the per-tier patrol counts, the station's name - survives here.
 *
 * <p>Whether the colony is itself a station rides along for the same reason: the walk that
 * counted it has the answer to hand for the dominance tie-break, and a reader deciding how to
 * name a station would otherwise have to go back to the economy for it.
 *
 * <p>An absent factor is a factor that did not run: the market has no station, no patrol HQ
 * garrisons it, or the player has that factor switched off. Modelling that as an empty
 * {@link Optional} rather than a zeroed factor keeps "contributed nothing" and "was never
 * counted" apart, which are different answers to why a market weighs what it does.
 *
 * <p>Plain values with no Starsector types, so the whole breakdown is built and read on
 * hand-made inputs.
 *
 * @param marketNameplate  how the colony is identified to a reader - its name and the glyph the
 *                        sector map marks it with. Recorded on the walk that counted the colony
 *                        rather than looked up again by whatever draws the name, so the pair shown
 *                        can only ever belong to the colony whose weight is stated beside it
 * @param isHiddenMarket  whether the colony is hidden - a concealed base rather than one held
 *                        in the open, which changes how two of the factors rate it
 * @param isStationMarket whether the colony sits on an orbital station rather than on a planet.
 *                        The dominance rule prefers planets at an exact tie, and a box naming
 *                        the station that defends a station colony needs it too: only there can
 *                        the colony and its defending station be the same name
 * @param marketStability the colony's stability on its own 0..10 band. Carried whole rather
 *                        than left implicit in the penalties, because it is the one reading
 *                        all three of them are derived from: without it a reader is shown
 *                        three cuts and no cause
 * @param baseSize        the base-size part, which every counted market carries
 * @param station         the attached-station part, present only when the station rule ran
 * @param patrols         the fielded-patrol part, present only when the patrol rule ran
 */
public record MarketWeightBreakdown(
    EntityNameplate marketNameplate,
    boolean isHiddenMarket,
    boolean isStationMarket,
    double marketStability,
    BaseSizeFactor baseSize,
    Optional<StationFactor> station,
    Optional<PatrolFactor> patrols) {

    // A factor that did not run holds nothing, which is what an absent one adds to the sum.
    private static final double NO_CONTRIBUTION = 0.0;

    /**
     * What the market's factors are worth together, in size points, before the fixed-point
     * grid the dominance rule compares on.
     *
     * @return the summed factor contributions
     */
    public double computeTotalContribution() {
        return baseSize.contribution()
            + station.map(StationFactor::contribution).orElse(NO_CONTRIBUTION)
            + patrols.map(PatrolFactor::computeContribution).orElse(NO_CONTRIBUTION);
    }

    /**
     * The market's dominance weight: its summed contributions rounded once onto
     * {@link MarketWeights#DOMINANCE_WEIGHT_SCALE}.
     *
     * <p>Rounded once at the end rather than per factor, so the weight is exactly what the
     * factors add up to and the rule's comparisons stay exact.
     *
     * @return the weight this market folds into its faction's footprint
     */
    public int computeTotalWeight() {
        return MarketWeights.roundToWeight(computeTotalContribution());
    }
}
