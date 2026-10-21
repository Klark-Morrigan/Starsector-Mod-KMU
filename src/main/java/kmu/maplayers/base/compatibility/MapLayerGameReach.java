package kmu.maplayers.base.compatibility;

import kmlib.starsector.compatibility.CompatibilityConsumer;
import kmlib.starsector.compatibility.GameReachReporter;

import kmu.util.KmuCompatibilityConsumers;
import kmu.util.KmuMapStringKeys;

/**
 * The map layers' reaches into the game's own code, one constant per thing a player loses when a
 * game release changes what a reach reads, each holding the reporter a failed reach is filed through.
 *
 * <p>One per loss rather than one per reach, because the loss is what a report says. Several reaches
 * can cost the player one thing - the sector map's view state and the intel screen's map both decide
 * whether the layers show - and a report per reach would tell the player the same loss twice.
 *
 * <p>Each constant holds its feature key and both of its string keys together, so a loss cannot be
 * filed with another loss's sentence: the three are stated side by side, once.
 *
 * <p>Held for the session, since each reporter files once: a reach failing every frame is reported
 * on its first failure and costs a flag read after it. The sentences are composed only on that
 * first failure, strings.json having no place on the path of a reach that holds.
 */
public enum MapLayerGameReach {

    /**
     * Whether a map is showing, on the sector map and in the intel screen's map, which every layer,
     * the sidebar and the hover gate on.
     */
    MAP_VIEW(
        "map-view",
        KmuMapStringKeys.COMPATIBILITY_LOST_MAP_VIEW,
        KmuMapStringKeys.COMPATIBILITY_UNAFFECTED_MAP_VIEW),

    /** The map layers' tick box on the game's own filter row, and the key written on it. */
    FILTER_ROW_TOGGLE(
        "filter-row-toggle",
        KmuMapStringKeys.COMPATIBILITY_LOST_FILTER_ROW_TOGGLE,
        KmuMapStringKeys.COMPATIBILITY_UNAFFECTED_FILTER_ROW_TOGGLE),

    /** The dialog the layers are arranged in, stood over the core screen it was opened from. */
    ARRANGE_DIALOG(
        "arrange-dialog",
        KmuMapStringKeys.COMPATIBILITY_LOST_ARRANGE_DIALOG,
        KmuMapStringKeys.COMPATIBILITY_UNAFFECTED_ARRANGE_DIALOG),

    /** Standing the hover and the sidebar aside for the game's own prompts and the codex. */
    SCREEN_COVERS(
        "screen-covers",
        KmuMapStringKeys.COMPATIBILITY_LOST_SCREEN_COVERS,
        KmuMapStringKeys.COMPATIBILITY_UNAFFECTED_SCREEN_COVERS),

    /** Standing aside for the game's own map tooltip, and repainting it over the sidebar. */
    MAP_TOOLTIPS(
        "map-tooltips",
        KmuMapStringKeys.COMPATIBILITY_LOST_MAP_TOOLTIPS,
        KmuMapStringKeys.COMPATIBILITY_UNAFFECTED_MAP_TOOLTIPS),

    /** The order the map draws its icons in, which lifts the upper band above the nebulae. */
    STARSCAPE_RESEAT(
        "starscape-reseat",
        KmuMapStringKeys.COMPATIBILITY_LOST_STARSCAPE_RESEAT,
        KmuMapStringKeys.COMPATIBILITY_UNAFFECTED_STARSCAPE_RESEAT),

    /** The terrain the layers draw on while the sector map's Starscape filter is on. */
    STARSCAPE_TERRAIN(
        "starscape-terrain",
        KmuMapStringKeys.COMPATIBILITY_LOST_STARSCAPE_TERRAIN,
        KmuMapStringKeys.COMPATIBILITY_UNAFFECTED_STARSCAPE_TERRAIN);

    private final String featureKey;
    private final String lostKey;
    private final String unaffectedKey;

    private final GameReachReporter reporter;

    MapLayerGameReach(String featureKey, String lostKey, String unaffectedKey) {

        this.featureKey = featureKey;
        this.lostKey = lostKey;
        this.unaffectedKey = unaffectedKey;

        // Composing the consumer only once the reach has failed.
        this.reporter = new GameReachReporter(this::describeConsumer);
    }

    /**
     * @return the reporter a failed reach behind this loss is filed through, to hand the probe
     */
    public GameReachReporter getReporter() {
        return reporter;
    }

    /**
     * KMU as the consumer of this loss, with its sentences read now.
     *
     * @return the consumer a failed reach is filed under
     */
    CompatibilityConsumer describeConsumer() {
        return KmuCompatibilityConsumers.describeConsumer(featureKey, lostKey, unaffectedKey);
    }
}
