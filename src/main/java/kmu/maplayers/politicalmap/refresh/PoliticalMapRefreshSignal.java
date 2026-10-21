package kmu.maplayers.politicalmap.refresh;

import kmlib.math.hashing.Fingerprints;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.refresh.MapLayerRefreshSignal;

/**
 * The coarse changes only the political map can raise, held on the shared refresh board under
 * this layer's own keys. Declared beside the layer rather than in the framework because who is
 * allied with whom is political vocabulary: a layer painting hazards or trade would read every
 * common signal on that board and never one of these.
 */
public enum PoliticalMapRefreshSignal implements MapLayerRefreshSignal {

    /**
     * An alliance formed, dissolved, or gained or lost a member. Every political view reads it, for
     * two different reasons: the alliances view fuses allied factions into one bloc, so membership
     * decides what it paints, while the faction and claims views paint per faction and read the
     * alliance set only to judge a contest - which of the blocs in a cell are rivals rather than
     * partners. The sector watcher fingerprints the live alliance set each poll and raises this when
     * the fingerprint moves.
     */
    ALLIANCES;

    /**
     * The fingerprint of the alliance set alone, which is what every view of this layer answers its
     * content revision with.
     *
     * <p>Beside the signal rather than on a view, since it is arithmetic over this signal's own
     * revision.
     *
     * <p>Composed through a fingerprint rather than handed back bare, so a view that grows a second
     * live input adds a source beside this one instead of changing what it returns.
     *
     * @param board the refresh board of the sector this ask is about
     * @return the content fingerprint of that board's alliance revision
     */
    public static int computeAllianceContentRevision(MapLayerRefreshBoard board) {
        return Fingerprints.compute(() -> board.getRevision(ALLIANCES));
    }

    @Override
    public String getId() {
        // The constant is the name, so there is no second identity to keep in step with it.
        return name();
    }
}
