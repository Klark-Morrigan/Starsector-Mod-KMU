package kmu.maplayers.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.PlayerColonizationListener;

import kmu.maplayers.politicalmap.refresh.MarketPoliticsRefresh;
import kmu.maplayers.politicalmap.refresh.PoliticalMapStalenessSource;

/**
 * Marks a system's political-map holding stale when the player founds a colony
 * on one of its planets or abandons an existing one, so planting or dropping a
 * colony repaints its system live rather than only on reload.
 *
 * <p>Founding a colony brings a new weight into its system and abandoning one removes it, and
 * neither is caught by the sibling listeners: founding is not a resize (the colony is created at
 * its starting size, not grown into it) and abandoning is not a decivilisation (the market is
 * pulled from the economy without turning neutral first). Both go through
 * {@link MarketPoliticsRefresh}.
 *
 * <p>Only player colonisation fires this, since the engine fires no listener for an NPC faction
 * founding a colony; {@link PoliticalMapStalenessSource}'s holder diff catches those.
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
        // colonyless economy, dropping the faction fill.
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
