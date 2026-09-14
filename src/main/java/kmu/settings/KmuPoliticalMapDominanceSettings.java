package kmu.settings;

/**
 * What decides who holds a system: the weight a colony's size carries, what a hidden market
 * counts for, and the station and patrol bonuses, each with the penalty low stability cuts it by.
 *
 * <p>These change the map's political verdicts rather than its styling, which is the line this
 * class is drawn along - a knob here moves who a cell is coloured for, never how that colour is
 * painted. All of them fold into one rules value read once per resolution pass, so they are
 * fetched together or not at all.
 */
public final class KmuPoliticalMapDominanceSettings {

    private static final String COLONY_SIZE_WEIGHT_FIELD =
        "kmu_map_politics_domination_colonySize_weight_base";
    private static final String HIDDEN_MARKET_SCALING_FIELD =
        "kmu_map_politics_domination_hiddenMarkets_weight_scaling";
    private static final String HIDDEN_MARKET_FIXED_WEIGHT_FIELD =
        "kmu_map_politics_domination_hiddenMarkets_weight_fixed";
    private static final String STABILITY_WEIGHS_DOMINANCE_FIELD =
        "kmu_map_politics_domination_stability_isEnabled";
    private static final String NORMAL_LOW_STABILITY_PENALTY_FIELD =
        "kmu_map_politics_domination_colonySize_weight_penalty_lowStability";
    private static final String STATION_WEIGHS_DOMINANCE_FIELD =
        "kmu_map_politics_domination_stations_weight_isEnabled";
    private static final String STATION_WEIGHT_FIELD =
        "kmu_map_politics_domination_stations_weight_base";
    private static final String STATION_HIDDEN_MARKET_RATE_FIELD =
        "kmu_map_politics_domination_stations_weight_hiddenMarketRate";
    private static final String STATION_LOW_STABILITY_PENALTY_FIELD =
        "kmu_map_politics_domination_stations_weight_penalty_lowStability";
    private static final String PATROL_WEIGHS_DOMINANCE_FIELD =
        "kmu_map_politics_domination_patrols_weight_isEnabled";
    private static final String PATROL_SMALL_WEIGHT_FIELD =
        "kmu_map_politics_domination_patrols_weight_small";
    private static final String PATROL_MEDIUM_WEIGHT_FIELD =
        "kmu_map_politics_domination_patrols_weight_medium";
    private static final String PATROL_LARGE_WEIGHT_FIELD =
        "kmu_map_politics_domination_patrols_weight_large";
    private static final String PATROL_LOW_STABILITY_PENALTY_FIELD =
        "kmu_map_politics_domination_patrols_weight_penalty_lowStability";

    private static final double DEFAULT_COLONY_SIZE_WEIGHT = 1.0;
    private static final HiddenMarketScalingChoice DEFAULT_HIDDEN_MARKET_SCALING =
        HiddenMarketScalingChoice.FIXED;
    private static final double DEFAULT_HIDDEN_MARKET_FIXED_WEIGHT = 1.0;
    private static final boolean DEFAULT_STABILITY_WEIGHS_DOMINANCE = true;
    private static final double DEFAULT_NORMAL_LOW_STABILITY_PENALTY = 1.0;
    private static final boolean DEFAULT_STATION_WEIGHS_DOMINANCE = true;
    private static final double DEFAULT_STATION_WEIGHT = 1.0;
    private static final double DEFAULT_STATION_HIDDEN_MARKET_RATE = 0.5;
    private static final double DEFAULT_STATION_LOW_STABILITY_PENALTY = 0.5;
    private static final boolean DEFAULT_PATROL_WEIGHS_DOMINANCE = true;
    private static final double DEFAULT_PATROL_SMALL_WEIGHT = 0.3;
    private static final double DEFAULT_PATROL_MEDIUM_WEIGHT = 0.6;
    private static final double DEFAULT_PATROL_LARGE_WEIGHT = 1.2;
    private static final double DEFAULT_PATROL_LOW_STABILITY_PENALTY = 0.5;

    private KmuPoliticalMapDominanceSettings() {
    }

    /**
     * @return the multiplier on each colony's base size rating - a visible market's own size, or a
     *         hidden market's chosen base size - before the station and patrol bonuses and
     *         stability fold in; 1.0 by default
     */
    public static double getColonySizeWeight() {
        return KmuLunaSettings.readDouble(COLONY_SIZE_WEIGHT_FIELD, DEFAULT_COLONY_SIZE_WEIGHT);
    }

    /**
     * @return how a hidden market's base size rating is chosen: NORMAL by its real colony size,
     *         FIXED at the hidden-market fixed weight regardless of size; FIXED by default
     */
    public static HiddenMarketScalingChoice getHiddenMarketScaling() {
        return KmuLunaSettings.readChoice(
            HIDDEN_MARKET_SCALING_FIELD,
            DEFAULT_HIDDEN_MARKET_SCALING);
    }

    /**
     * @return the fixed size rating a hidden market folds in at, before the colony size weight and
     *         stability apply; 1.0 by default. Unread while the scaling is NORMAL
     */
    public static double getHiddenMarketFixedWeight() {
        return KmuLunaSettings.readDouble(
            HIDDEN_MARKET_FIXED_WEIGHT_FIELD,
            DEFAULT_HIDDEN_MARKET_FIXED_WEIGHT);
    }

    /**
     * @return the master switch for stability scaling, each dominance factor being cut at low
     *         stability by its own penalty; on by default. Off ranks colonies by their raw
     *         weighted size, station, and patrol sum
     */
    public static boolean shouldWeighDominanceByStability() {
        return KmuLunaSettings.readBoolean(
            STABILITY_WEIGHS_DOMINANCE_FIELD,
            DEFAULT_STABILITY_WEIGHS_DOMINANCE);
    }

    /**
     * @return how much a colony's own size weight is cut at zero stability, 0..1 (half at 5, full
     *         at 10); 1.0 by default. Applied only while stability weighting is on
     */
    public static double getNormalLowStabilityPenalty() {
        return KmuLunaSettings.readDouble(
            NORMAL_LOW_STABILITY_PENALTY_FIELD,
            DEFAULT_NORMAL_LOW_STABILITY_PENALTY);
    }

    /**
     * @return whether a market with an attached defensive station gains the station weight in size
     *         points toward its system's dominance; on by default
     */
    public static boolean shouldWeighDominanceByStation() {
        return KmuLunaSettings.readBoolean(
            STATION_WEIGHS_DOMINANCE_FIELD,
            DEFAULT_STATION_WEIGHS_DOMINANCE);
    }

    /**
     * @return the size points an attached defensive station adds to a visible colony's dominance
     *         contribution, before its low-stability penalty applies; 1.0 by default. A hidden
     *         market earns this scaled by the hidden-market station rate
     */
    public static double getStationWeight() {
        return KmuLunaSettings.readDouble(STATION_WEIGHT_FIELD, DEFAULT_STATION_WEIGHT);
    }

    /**
     * @return the fraction of the station weight a station on a hidden market earns, 0..1; 0.5 by
     *         default
     */
    public static double getStationHiddenMarketRate() {
        return KmuLunaSettings.readDouble(
            STATION_HIDDEN_MARKET_RATE_FIELD,
            DEFAULT_STATION_HIDDEN_MARKET_RATE);
    }

    /**
     * @return how much the station bonus is cut at zero stability, 0..1; 0.5 by default. Applied
     *         only while stability weighting is on
     */
    public static double getStationLowStabilityPenalty() {
        return KmuLunaSettings.readDouble(
            STATION_LOW_STABILITY_PENALTY_FIELD,
            DEFAULT_STATION_LOW_STABILITY_PENALTY);
    }

    /**
     * @return whether a colony gains size points toward its system's dominance for the patrols it
     *         fields, weighted by patrol size; on by default
     */
    public static boolean shouldWeighDominanceByPatrols() {
        return KmuLunaSettings.readBoolean(
            PATROL_WEIGHS_DOMINANCE_FIELD,
            DEFAULT_PATROL_WEIGHS_DOMINANCE);
    }

    /**
     * @return the size points each small (light) patrol adds to its colony's dominance
     *         contribution, before the patrol low-stability penalty applies; 0.3 by default.
     *         Unread while patrol weighting is off
     */
    public static double getPatrolSmallWeight() {
        return KmuLunaSettings.readDouble(PATROL_SMALL_WEIGHT_FIELD, DEFAULT_PATROL_SMALL_WEIGHT);
    }

    /**
     * @return the size points each medium patrol adds to its colony's dominance contribution,
     *         before the patrol low-stability penalty applies; 0.6 by default. Unread while patrol
     *         weighting is off
     */
    public static double getPatrolMediumWeight() {
        return KmuLunaSettings.readDouble(PATROL_MEDIUM_WEIGHT_FIELD, DEFAULT_PATROL_MEDIUM_WEIGHT);
    }

    /**
     * @return the size points each large (heavy) patrol adds to its colony's dominance
     *         contribution, before the patrol low-stability penalty applies; 1.2 by default.
     *         Unread while patrol weighting is off
     */
    public static double getPatrolLargeWeight() {
        return KmuLunaSettings.readDouble(PATROL_LARGE_WEIGHT_FIELD, DEFAULT_PATROL_LARGE_WEIGHT);
    }

    /**
     * @return how much a colony's patrol bonus is cut at zero stability, 0..1; 0.5 by default.
     *         Applied only while stability weighting is on
     */
    public static double getPatrolLowStabilityPenalty() {
        return KmuLunaSettings.readDouble(
            PATROL_LOW_STABILITY_PENALTY_FIELD,
            DEFAULT_PATROL_LOW_STABILITY_PENALTY);
    }
}
