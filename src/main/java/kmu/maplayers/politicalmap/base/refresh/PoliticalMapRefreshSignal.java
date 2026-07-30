package kmu.maplayers.politicalmap.base.refresh;

import kmu.maplayers.base.refresh.MapLayerRefreshSignal;

/**
 * The coarse changes only the political map can raise, held on the shared refresh board under
 * this layer's own keys. Declared beside the layer rather than in the framework because who is
 * allied with whom is political vocabulary: a layer painting hazards or trade would read every
 * common signal on that board and never one of these.
 */
public enum PoliticalMapRefreshSignal implements MapLayerRefreshSignal {

    /**
     * An alliance formed, dissolved, or gained or lost a member. Only the alliances view reads
     * it - that view fuses allied factions into one bloc, so membership is one of its live
     * inputs - which leaves the faction view untouched by a change it does not render. The
     * sector watcher fingerprints the live alliance set each poll and raises this when the
     * fingerprint moves.
     */
    ALLIANCES;

    @Override
    public String getId() {
        // The constant is the name, so there is no second identity to keep in step with it.
        return name();
    }
}
