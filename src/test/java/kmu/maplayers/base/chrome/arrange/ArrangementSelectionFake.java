package kmu.maplayers.base.chrome.arrange;

import kmu.maplayers.base.layer.MapLayerArrangement;
import kmu.maplayers.base.layer.MapLayerArrangementSelection;

import java.util.ArrayList;
import java.util.List;

/**
 * A store that answers whatever a case posed and keeps whatever was written to it, so a case can say
 * both what the player had arranged and what the dialog recorded over it.
 *
 * <p>A file of its own rather than a nested helper because both the editor and the row widgets are
 * built over a real arrangement, and the second of those needs an editor that actually refuses a
 * toggle - which is a thing only a store holding a particular arrangement can produce.
 */
final class ArrangementSelectionFake implements MapLayerArrangementSelection {

    private MapLayerArrangement heldArrangement = MapLayerArrangement.UNARRANGED;

    private MapLayerArrangement recordedArrangement;

    /**
     * @return the arrangement the last write recorded, or null where nothing has been written
     */
    MapLayerArrangement getRecordedArrangement() {
        return recordedArrangement;
    }

    /**
     * Poses what the player had arranged before this dialog opened.
     *
     * @param orderedLayerIds the row order, leading first
     * @param hiddenLayerIds  the ids whose tabs are off the bar
     */
    void holdArrangement(List<String> orderedLayerIds, List<String> hiddenLayerIds) {

        heldArrangement = new MapLayerArrangement(
            new ArrayList<>(orderedLayerIds),
            new ArrayList<>(hiddenLayerIds));
    }

    @Override
    public MapLayerArrangement readArrangement() {
        return heldArrangement;
    }

    @Override
    public void recordArrangement(MapLayerArrangement arrangement) {
        recordedArrangement = arrangement;
    }
}
