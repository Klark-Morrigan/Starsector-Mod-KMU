package kmu.maplayers.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.politicalmap.refresh.MarketPoliticsRefresh;
import kmu.maplayers.politicalmap.refresh.PoliticalMapStalenessSource;
import kmu.starsector.listeners.MarketTransferListener;

/**
 * Marks a conquered colony's system politics-stale when it changes hands, so a colony taken in an
 * invasion or a war repaints its new faction colour live rather than only on reload.
 *
 * <p>A market changing holder is the central political-map event - it is how war redraws borders -
 * but vanilla fires no holding-transfer listener, so the sibling listeners cannot catch it: an
 * invasion keeps the colony's size, never decivilises it, and reveals nothing. This translates a
 * transfer into the same targeted refresh ({@link MapLayerRefreshBoard#markSystemGroupingStale})
 * the holding-axis siblings use: only the transferred colony's system and its neighbours are
 * re-derived, reading the post-transfer holder. Reachability changes remain
 * {@link PoliticalMapStalenessSource}'s axis.
 *
 * <p>Registered like its vanilla-driven siblings and reached through {@link MarketTransferListener},
 * so it names no mod's types: whatever reports transfers on this sector tells it, and on an install
 * where nothing does it is never called.
 *
 * <p>Holds the sector it was installed on, so a conquest is reported against that sector's overlay
 * rather than against whichever sector is currently loaded.
 */
public class PoliticalMapMarketTransferListener implements MarketTransferListener {

    private final SectorAPI sector;

    /**
     * @param sector the sector this listener is installed on, whose overlay a transfer here
     *               repaints
     */
    public PoliticalMapMarketTransferListener(SectorAPI sector) {
        this.sector = sector;
    }

    @Override
    public void reportMarketTransferred(
            MarketAPI market,
            FactionAPI oldHolder,
            FactionAPI newHolder,
            boolean isCapture) {

        // The from/to factions and capture flag ride along in the log so a cell
        // that does (or does not) repaint on conquest can be traced to this
        // transfer; the shared refresh filters an unseated market.
        //
        // A transfer is reported once it has happened, so the observation the
        // refresh records is taken under the new owner. Where the conquest is what
        // stopped a colony vouching for a rival's concealed base beside it - the
        // two then sharing an owner - there is no earlier moment to read from here.
        // That base keeps the sighting it already had, and is dated afresh by
        // whatever observes it next. That is the resolution, not a gap.
        MarketPoliticsRefresh.reportMarketChange(
            sector,
            market,
            "market transferred", // Event.
            "from=" + resolveFactionId(oldHolder)
                + " to=" + resolveFactionId(newHolder)
                + " capture=" + isCapture); // Context.
    }

    // Null-safe faction ID for the trace line; a transfer to or from an unowned
    // state is logged as "null" rather than crashing the callback.
    private static String resolveFactionId(FactionAPI faction) {
        return faction == null ? "null" : faction.getId();
    }
}
