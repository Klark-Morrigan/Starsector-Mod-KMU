package kmu.settings;

/**
 * Which of the mod's features are switched on at all.
 *
 * <p>Its own class beside the knob classes {@link KmuLunaSettings} lists, and split from them by
 * the same rule: these are read by the composition root rather than by any feature, because a
 * feature cannot be asked whether it exists. A toggle here decides whether a feature's start-up
 * wiring runs; everything the feature then does is tuned by its own class.
 *
 * <p>A finished feature's toggle defaults on: a player who has never opened the settings has the
 * mod they installed, and switching one off is a deliberate act. A feature still being built
 * defaults off by the same reasoning read the other way - what ships on is what the mod claims to
 * do, and one that is not ready makes no such claim until it is.
 */
public final class KmuFeatureSettings {

    // Whether the sector map's overlays are wired at all: the layers, their render surfaces, the
    // sidebar and the hover box. Read on load and again whenever the settings change, so switching
    // it takes effect on the spot.
    private static final String MAP_LAYERS_FIELD = "kmu_features_toggles_areMapLayersEnabled";

    private static final boolean DEFAULT_MAP_LAYERS = true;

    private static final String MARKET_CONDITION_MANAGER_FIELD =
        "kmu_features_toggles_isMarketConditionManagerEnabled";

    private static final boolean DEFAULT_MARKET_CONDITION_MANAGER = false;

    private KmuFeatureSettings() {
    }

    /**
     * @return whether the sector map's overlays are wired - the layers, their render surfaces, the
     *         sidebar and the hover box, for every layer registered with KMU whichever mod ships it;
     *         on by default, and switched off leaves the sector map exactly as vanilla draws it
     */
    public static boolean areMapLayersEnabled() {
        return KmuLunaSettings.readBoolean(MAP_LAYERS_FIELD, DEFAULT_MAP_LAYERS);
    }

    /**
     * @return whether the market condition manager is wired - the tracker that remembers which
     *         market a UI is open on, and the console command that opens the editor on it; off by
     *         default while the feature is unfinished, and switched on it is neither supported nor
     *         safe on a campaign the player cares about
     */
    public static boolean isMarketConditionManagerEnabled() {
        return KmuLunaSettings.readBoolean(
            MARKET_CONDITION_MANAGER_FIELD,
            DEFAULT_MARKET_CONDITION_MANAGER);
    }
}
