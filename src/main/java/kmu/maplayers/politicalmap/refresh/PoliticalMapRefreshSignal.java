package kmu.maplayers.politicalmap.refresh;

import kmlib.cache.revisions.RevisionStamp;

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
     * The stamp of the alliance set alone, which is what every view of this layer answers its
     * content revisions with.
     *
     * <p>Beside the signal rather than on a view, since it reads this signal's own revision.
     *
     * <p>Answered as a stamp rather than the bare revision, so a view that grows a second live input
     * appends its revision to this one instead of changing what it returns.
     *
     * @param board the refresh board of the sector this ask is about
     * @return the stamp of that board's alliance revision
     */
    public static RevisionStamp readAllianceContentRevisions(MapLayerRefreshBoard board) {
        return RevisionStamp.of(board.getRevision(ALLIANCES));
    }

    @Override
    public String getId() {
        // The constant is the name, so there is no second identity to keep in step with it.
        return name();
    }
}
