package kmu.maplayers.base.chrome.arrange;

/**
 * One layer as the arranging dialog shows it: where it stands in the bar, what its tab says, and
 * whether the player has taken that tab off.
 *
 * <p>Apart from the layer it stands for because the dialog outlives no frame of the roster: the label
 * is resolved once as the dialog is built rather than per frame, a dialog being a thing the player is
 * reading rather than a bar being repainted from a roster that may have grown under it. Holding the
 * layer would also let a row be moved by something other than the dialog while the dialog was showing
 * it in its old place.
 *
 * <p>The ID is what the arrangement is written in, so it is what a row is addressed by - a position
 * would be stale the moment the row above it moved, and the label is not unique.
 *
 * @param layerId    the layer's registered ID, as the stored arrangement names it
 * @param layerLabel what this layer's tab says, resolved when the dialog was built
 * @param isHidden   whether the player has taken this layer's tab off the bar
 */
public record MapLayerArrangementRow(
    String layerId,
    String layerLabel,
    boolean isHidden) {

    /**
     * @return this row with its tab taken off the bar or put back on it
     */
    public MapLayerArrangementRow toggleHidden() {
        return new MapLayerArrangementRow(layerId, layerLabel, !isHidden);
    }
}
