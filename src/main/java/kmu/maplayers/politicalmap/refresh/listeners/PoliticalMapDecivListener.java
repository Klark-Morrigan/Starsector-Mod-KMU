package kmu.maplayers.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyDecivListener;

import kmu.maplayers.politicalmap.refresh.MarketPoliticsRefresh;
import kmu.maplayers.politicalmap.refresh.PoliticalMapStalenessSource;

/**
 * Marks a decivilised colony's system politics-stale, so a colony that dies mid
 * play drops its faction colour and repaints neutral at once rather than only on
 * reload.
 *
 * <p>Decivilisation strips the owning faction - the engine turns the market neutral and removes it
 * from the economy - so the system's dominant holder vanishes, which {@link MarketPoliticsRefresh}
 * turns into a targeted mark.
 *
 * <p>What the event adds is its two phases. The mark waits for {@code reportColonyDecivilized},
 * fired once the market is neutral and out of the economy, so the re-derive reads the post-deciv
 * state. The {@code aboutToBe} phase still sees the colony faction-owned, so it marks nothing; it
 * only records the system's sightings while the dying colony can still vouch for them.
 *
 * <p>Decivilisation can also drop the system off the map. A partial deciv leaves a revealed
 * decivilised world on its planet and keeps the system drawn; a full destroy, or any station deciv
 * (the revealed-decivilised rule scans planets only), can drop a hidden single-colony system.
 * Map membership is {@link PoliticalMapStalenessSource}'s alone, so this listener stays on the
 * holding axis.
 */
public class PoliticalMapDecivListener implements ColonyDecivListener {

    private final SectorAPI sector;

    /**
     * @param sector the sector this listener is installed on, whose overlay a decivilisation here
     *               repaints and whose memory holds the sighting register it writes
     */
    public PoliticalMapDecivListener(SectorAPI sector) {
        this.sector = sector;
    }

    // The colony is still faction-owned at this point, so re-deriving here would
    // read the pre-deciv holder. The stale mark is deferred to the completed
    // event below, which sees the neutral, economy-removed market.
    //
    // What cannot be deferred is the sighting: this colony is about to stop
    // vouching for whatever else stands in its system, and the moment it does
    // there is nobody left to date the observation by. So the system is recorded
    // here, while the dying colony can still be read as the observer it is - which
    // is what leaves a derelict beside it saying "last seen" on the day the
    // system emptied rather than on the day it was last recorded by anything else.
    @Override
    public void reportColonyAboutToBeDecivilized(MarketAPI market, boolean fullyDestroyed) {

        if (market != null) {
            MarketPoliticsRefresh.recordObservationsIn(sector, market.getStarSystem());
        }
    }

    @Override
    public void reportColonyDecivilized(MarketAPI market, boolean fullyDestroyed) {

        // The completed event sees the neutral, economy-removed market, and the
        // system still resolves post-removal since the primary entity (the planet)
        // is preserved. The full-destroy flag rides along in the log so a cell that
        // does (or does not) repaint neutral on death can be traced to this event.
        MarketPoliticsRefresh.reportMarketChange(
            sector,
            market,
            "decivilised colony", // Event.
            "fullyDestroyed=" + fullyDestroyed); // Context.
    }
}
