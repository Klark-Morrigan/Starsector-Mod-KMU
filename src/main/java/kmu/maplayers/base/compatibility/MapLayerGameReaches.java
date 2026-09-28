package kmu.maplayers.base.compatibility;

import kmlib.starsector.compatibility.CompatibilityConsumer;
import kmlib.starsector.compatibility.GameReachReporter;

import kmu.KmuMod;
import kmu.util.KmuStringKeys;

/**
 * The map layers' reaches into the game's own code, as the reporters a failed one is filed through:
 * one per thing a player loses when a game release changes what a reach reads.
 *
 * <p>One per loss rather than one per reach, because the loss is what a report says. Several reaches
 * can cost the player one thing - the sector map's view state and the intel screen's map both decide
 * whether the layers show - and a report per reach would tell the player the same loss twice.
 *
 * <p>Held for the session, since each reporter files once: a reach failing every frame is reported
 * on its first failure and costs a flag read after it. The sentences are composed only on that
 * first failure, strings.json having no place on the path of a reach that holds.
 *
 * <p>Final class with a private constructor: a holder of constants, no instances.
 */
public final class MapLayerGameReaches {

    /**
     * Whether a map is showing, on the sector map and in the intel screen's map, which every layer,
     * the sidebar and the hover gate on.
     */
    public static final GameReachReporter MAP_VIEW = createReporter(
        "map-view",
        KmuStringKeys.COMPATIBILITY_LOST_MAP_VIEW,
        KmuStringKeys.COMPATIBILITY_UNAFFECTED_MAP_VIEW);

    /** The map layers' tick box on the game's own filter row, and the key written on it. */
    public static final GameReachReporter FILTER_ROW_TOGGLE = createReporter(
        "filter-row-toggle",
        KmuStringKeys.COMPATIBILITY_LOST_FILTER_ROW_TOGGLE,
        KmuStringKeys.COMPATIBILITY_UNAFFECTED_FILTER_ROW_TOGGLE);

    /** The dialog the layers are arranged in, stood over the core screen it was opened from. */
    public static final GameReachReporter ARRANGE_DIALOG = createReporter(
        "arrange-dialog",
        KmuStringKeys.COMPATIBILITY_LOST_ARRANGE_DIALOG,
        KmuStringKeys.COMPATIBILITY_UNAFFECTED_ARRANGE_DIALOG);

    /** Standing the hover and the sidebar aside for the game's own prompts and the codex. */
    public static final GameReachReporter SCREEN_COVERS = createReporter(
        "screen-covers",
        KmuStringKeys.COMPATIBILITY_LOST_SCREEN_COVERS,
        KmuStringKeys.COMPATIBILITY_UNAFFECTED_SCREEN_COVERS);

    /** Standing aside for the game's own map tooltip, and repainting it over the sidebar. */
    public static final GameReachReporter MAP_TOOLTIPS = createReporter(
        "map-tooltips",
        KmuStringKeys.COMPATIBILITY_LOST_MAP_TOOLTIPS,
        KmuStringKeys.COMPATIBILITY_UNAFFECTED_MAP_TOOLTIPS);

    /** The order the map draws its icons in, which lifts the upper band above the nebulae. */
    public static final GameReachReporter STARSCAPE_RESEAT = createReporter(
        "starscape-reseat",
        KmuStringKeys.COMPATIBILITY_LOST_STARSCAPE_RESEAT,
        KmuStringKeys.COMPATIBILITY_UNAFFECTED_STARSCAPE_RESEAT);

    /** The terrain the layers draw on while the sector map's Starscape filter is on. */
    public static final GameReachReporter STARSCAPE_TERRAIN = createReporter(
        "starscape-terrain",
        KmuStringKeys.COMPATIBILITY_LOST_STARSCAPE_TERRAIN,
        KmuStringKeys.COMPATIBILITY_UNAFFECTED_STARSCAPE_TERRAIN);

    private MapLayerGameReaches() {
        // holder of constants, no instances.
    }

    /**
     * KMU as the consumer of one reach: its own mod ID, the feature key, and the two sentences the
     * keys name, read now.
     *
     * @param featureKey    which of the map layers' features the reach serves
     * @param lostKey       the strings.json key of what the feature loses
     * @param unaffectedKey the strings.json key of what it does not
     * @return the consumer a failed reach is filed under
     */
    static CompatibilityConsumer describeConsumer(String featureKey, String lostKey, String unaffectedKey) {

        return new CompatibilityConsumer(
            KmuMod.MOD_ID,
            featureKey,
            KmuStringKeys.get(lostKey),
            KmuStringKeys.get(unaffectedKey));
    }

    // A reporter composing its consumer only once the reach has failed.
    private static GameReachReporter createReporter(String featureKey, String lostKey, String unaffectedKey) {

        return new GameReachReporter(() -> describeConsumer(featureKey, lostKey, unaffectedKey));
    }
}
