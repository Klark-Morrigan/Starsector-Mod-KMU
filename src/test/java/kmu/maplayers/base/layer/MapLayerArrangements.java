package kmu.maplayers.base.layer;

import java.util.List;

/**
 * Builds the bar arrangement a case poses: one a past session left, one the player goes on to change, or
 * none at all.
 */
public final class MapLayerArrangements {

    private MapLayerArrangements() {
    }

    /**
     * An arrangement as a past session would have left it.
     *
     * @param orderedLayerIds the IDs the player placed, left to right
     * @param hiddenLayerIds  the IDs whose tabs they took off the bar
     * @return a bar arranged that way, which drops whatever is recorded to it
     */
    public static LiveMapLayerArrangement createArrangedBar(
            List<String> orderedLayerIds,
            List<String> hiddenLayerIds) {

        return new LiveMapLayerArrangement(
            new FixedArrangementFake(new MapLayerArrangement(orderedLayerIds, hiddenLayerIds)));
    }

    /**
     * An arrangement the case goes on to change through {@link #rearrangeBar}, the way the arranging
     * dialog changes it in play.
     *
     * @param orderedLayerIds the IDs the player placed at first, left to right
     * @param hiddenLayerIds  the IDs whose tabs were off the bar at first
     * @return a bar arranged that way, which answers whatever is recorded to it from then on
     */
    public static LiveMapLayerArrangement createRearrangeableBar(
            List<String> orderedLayerIds,
            List<String> hiddenLayerIds) {

        return new LiveMapLayerArrangement(
            new HeldArrangementFake(new MapLayerArrangement(orderedLayerIds, hiddenLayerIds)));
    }

    /** @return the bar an install whose player has never arranged one reads, bound to no store */
    public static LiveMapLayerArrangement createUnarrangedBar() {
        return new LiveMapLayerArrangement(null);
    }

    /**
     * Records a new arrangement through the bar's own store, which is the path the arranging dialog
     * writes through.
     *
     * @param layerArrangement a bar made by {@link #createRearrangeableBar}
     * @param orderedLayerIds  the IDs the player now places, left to right
     * @param hiddenLayerIds   the IDs whose tabs are now off the bar
     */
    public static void rearrangeBar(
            LiveMapLayerArrangement layerArrangement,
            List<String> orderedLayerIds,
            List<String> hiddenLayerIds) {

        layerArrangement
            .resolveArrangementSelection()
            .recordArrangement(new MapLayerArrangement(orderedLayerIds, hiddenLayerIds));
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

    // The arrangement last recorded, or the one posed at first before anything is.
    private static final class HeldArrangementFake
            implements MapLayerArrangementSelection {

        private MapLayerArrangement heldArrangement;

        private HeldArrangementFake(MapLayerArrangement initialArrangement) {
            this.heldArrangement = initialArrangement;
        }

        @Override
        public MapLayerArrangement readArrangement() {
            return heldArrangement;
        }

        @Override
        public void recordArrangement(MapLayerArrangement recordedArrangement) {
            heldArrangement = recordedArrangement;
        }
    }
}
