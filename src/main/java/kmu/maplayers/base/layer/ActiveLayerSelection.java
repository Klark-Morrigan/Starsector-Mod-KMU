package kmu.maplayers.base.layer;

/**
 * One screen's active-layer pick: the layer that screen's sidebar shows lit and switches to when a tab is
 * clicked. Each screen holds its own, so a switch on one screen leaves the other's pick untouched rather
 * than driving them both through a single shared value that would carry a change on one screen across to
 * the other. A pick may be persistent (read from and written to the save) or kept only for the session; the
 * implementation decides which, and a consumer reads and writes through this seam without knowing.
 */
public interface ActiveLayerSelection {

    /** @return the layer this selection currently holds as the active pick. */
    MapLayer getActiveLayer();

    /**
     * Records {@code layer} as the active pick.
     *
     * @param layer the newly picked layer
     */
    void selectLayer(MapLayer layer);
}
