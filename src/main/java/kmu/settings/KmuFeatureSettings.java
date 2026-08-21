package kmu.settings;

/**
 * Which of the mod's features are switched on at all.
 *
 * <p>Its own class beside the knob classes {@link KmuLunaSettings} lists, and split from them by
 * the same rule: these are read by the composition root rather than by any feature, because a
 * feature cannot be asked whether it exists. A toggle here decides whether a feature's start-up
 * wiring runs; everything the feature then does is tuned by its own class.
 *
 * <p>Every toggle here defaults on. A player who has never opened the settings has the mod they
 * installed, and switching one off is a deliberate act.
 */
public final class KmuFeatureSettings {

    // Whether the sector map's overlays are wired at all: the layers, their render surfaces, the
    // sidebar and the hover box. Read on load and again whenever the settings change, so switching
    // it takes effect on the spot.
    private static final String MAP_LAYERS_FIELD = "kmu_features_toggles_mapLayers";

    private static final boolean DEFAULT_MAP_LAYERS = true;

    private KmuFeatureSettings() {
    }

    /**
     * @return whether the sector map's overlays are wired - the layers, their render surfaces, the
     *         sidebar and the hover box; on by default, and switched off leaves the sector map
     *         exactly as vanilla draws it
     */
    public static boolean areMapLayersEnabled() {
        return KmuLunaSettings.readBoolean(MAP_LAYERS_FIELD, DEFAULT_MAP_LAYERS);
    }
}
