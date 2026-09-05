package kmu.maplayers.base.layer;

import java.util.List;

/**
 * States how the player has arranged their bar, and takes the saying back.
 *
 * <p>{@link LiveMapLayerArrangement} is static and holds whatever was last bound to it, so a class that
 * arranged the bar would otherwise leave every later row in the JVM in that order - and one that ran the
 * composition root would leave a store reading the real player's own file standing behind it.
 *
 * <p>Sits in the holder's own package so the unbinding it composes stays package-private there, that being
 * an act the running game has no use for. The same arrangement {@link MapLayerRosters} uses for the roster
 * beside it.
 */
public final class MapLayerArrangements {

    private MapLayerArrangements() {
    }

    /**
     * Arranges the bar as a past session would have left it, from this point on.
     *
     * @param orderedLayerIds the ids the player placed, left to right
     * @param hiddenLayerIds  the ids whose tabs they took off the bar
     */
    public static void arrangeBarWith(List<String> orderedLayerIds, List<String> hiddenLayerIds) {

        LiveMapLayerArrangement.registerArrangementSelection(
            new FixedArrangementFake(new MapLayerArrangement(orderedLayerIds, hiddenLayerIds)));
    }

    /** Returns the bar to the reading an install whose player has never arranged one gives. */
    public static void forgetTheArrangement() {
        LiveMapLayerArrangement.forgetArrangementSelection();
    }

    // One arrangement, answered however often it is asked for. Writes are dropped rather than held: what
    // a caller poses is the bar a past session left, and a case that moved it would be posing two.
    private record FixedArrangementFake(MapLayerArrangement arrangement)
        implements MapLayerArrangementSelection {

        @Override
        public MapLayerArrangement readArrangement() {
            return arrangement;
        }

        @Override
        public void recordArrangement(MapLayerArrangement recordedArrangement) {
        }
    }
}
