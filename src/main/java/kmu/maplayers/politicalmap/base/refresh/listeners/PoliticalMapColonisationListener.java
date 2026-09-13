package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.PlayerColonizationListener;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.politicalmap.base.refresh.MarketPoliticsRefresh;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapStalenessSource;

/**
 * Marks a system's political-map holding stale when the player founds a colony
 * on one of its planets or abandons an existing one, so planting or dropping a
 * colony repaints its system live rather than only on reload.
 *
 * <p>Holder on the map is decided by combined colony size, so adding a colony
 * introduces a new holder-and-size to its system and abandoning one removes it -
 * both the same per-system dominance change a resize, a deciv, or a discovery
 * makes. Neither is caught by the sibling listeners: founding is not a resize
 * (the colony is created at its starting size, not grown into it) and abandoning
 * is not a decivilisation (the market is pulled from the economy without turning
 * neutral first). So this routes both through the same targeted refresh
 * ({@link MapLayerRefreshBoard#markSystemGroupingStale}), re-deriving only the
 * affected system and its neighbours; whether the change flips the dominant holder
 * is decided later, when the plugin re-derives that one system.
 *
 * <p>Only the changed colony's own system is marked - a colony's size affects
 * dominance in its own system alone. Whether founding or abandonment also changes
 * which systems appear on the map is a separate reachability axis owned by
 * {@link PoliticalMapStalenessSource}. Mirrors {@link PoliticalMapDecivListener} on
 * the holding axis; this covers only player colonisation, since the engine
 * fires no listener for an NPC faction founding a colony.
 *
 * <p>Holds the sector it was installed on, so a colony founded or abandoned here is reported
 * against that sector's overlay rather than against whichever sector is currently loaded.
 */
public class PoliticalMapColonisationListener implements PlayerColonizationListener {

    private final SectorAPI sector;

    /**
     * @param sector the sector this listener is installed on, whose overlay a colony founded or
     *               abandoned here repaints
     */
    public PoliticalMapColonisationListener(SectorAPI sector) {
        this.sector = sector;
    }

    @Override
    public void reportPlayerColonizedPlanet(PlanetAPI planet) {

        // The event carries the planet, not its market; the new colony's market
        // is already attached by the time this fires, so the shared refresh reads
        // it off the planet. A null market is filtered downstream. The planet ID
        // rides along in the log so a colony that does (or does not) paint on
        // founding can be traced to it.
        MarketAPI market = planet == null ? null : planet.getMarket();
        String planetId = planet == null ? "null" : planet.getId();
        MarketPoliticsRefresh.reportMarketChange(
            sector,
            market,
            "colony founded", // Event.
            "planet=" + planetId); // Context.
    }

    @Override
    public void reportPlayerAbandonedColony(MarketAPI market) {
        // Abandonment pulls the market from the economy but leaves its planet, so
        // the system still resolves post-removal and the re-derivation reads the
        // now colonyless economy, dropping the faction fill.
        //
        // The observation this records is therefore taken after the abandoned
        // colony has stopped vouching for whatever else stands in its system:
        // vanilla fires this from AbandonMarketPluginImpl after DecivTracker has
        // already removed the colony, so there is no earlier moment to read here.
        // A derelict beside it keeps the sighting it already had, and is dated
        // afresh whenever something else observes it. That is the whole of the
        // resolution, not a gap left to be filled in.
        MarketPoliticsRefresh.reportMarketChange(
            sector,
            market,
            "colony abandoned", // Event.
            ""); // Context.
    }
}
