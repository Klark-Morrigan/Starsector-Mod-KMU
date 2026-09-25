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
     * The fingerprint of the alliance set alone, which is what every view that reads no other live
     * input answers its content revision with.
     *
     * <p>Stated once here rather than composed per view, because it is one arrangement of one
     * source and three copies of it would be three places for a later source to be added to two of.
     * What each view is answering is not shared and stays at each override: the alliances view
     * paints its blocs out of the set, the faction view's bands report contest against it, and the
     * claims view lays its runs by it - three different reasons to repaint on one number.
     *
     * <p>Beside the signal rather than on the view seam, since it is arithmetic over this signal's
     * own revision and every caller is a view of this layer. A layer reading none of these signals
     * would have had a helper about them in front of it.
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
