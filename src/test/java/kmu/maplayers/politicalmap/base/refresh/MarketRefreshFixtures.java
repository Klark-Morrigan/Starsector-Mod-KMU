package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared market mocks for the tests around {@link MarketPoliticsRefresh} and the listeners that
 * funnel through it: a market seated in a star system, a market seated in none, and the entity a
 * seated market hangs on.
 *
 * <p>One home for these because the seat is the only thing the refresh reads off a market, so
 * every suite here was stubbing the same shapes by hand - and an "unseated" market that
 * drifts between suites would have them pinning two different guards under one name.
 */
public final class MarketRefreshFixtures {

    private static final String ENTITY_ID = "entity";
    private static final String MARKET_ID = "mkt";

    // Fixtures only; never instantiated.
    private MarketRefreshFixtures() {
    }

    /**
     * An entity carrying a market seated in the named star system - what a discovery announces,
     * since that event names the entity rather than the market hanging on it. The entity's own ID
     * is fixed, since it reaches only the log line.
     *
     * @param systemId the seated system's ID, which is what a mark names
     * @return an entity mock holding a market with that seat
     */
    public static SectorEntityToken mockEntityWithMarketInSystem(String systemId) {

        // The market is built out fully before it is handed to a stub: the call below stubs
        // internally, so nesting it inside when(...).thenReturn(...) would trip Mockito's
        // unfinished-stubbing guard.
        var marketMock = mockMarketInSystem(systemId);
        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.getId())
            .thenReturn(ENTITY_ID);
        when(entityMock.getMarket())
            .thenReturn(marketMock);

        return entityMock;
    }

    /**
     * A market seated in the named star system. The market's own ID is fixed, since it reaches
     * only the log line: what the refresh acts on is the seat.
     *
     * @param systemId the seated system's ID, which is what a mark names
     * @return a market mock reporting that seat
     */
    public static MarketAPI mockMarketInSystem(String systemId) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(systemId);

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getId())
            .thenReturn(MARKET_ID);
        when(marketMock.getStarSystem())
            .thenReturn(systemMock);

        return marketMock;
    }

    /**
     * A market seated in no star system - a deep-hyperspace station, the shape the seat guard
     * rejects because it seeds no cell and so can repaint nothing.
     *
     * @return a market mock reporting no star system
     */
    public static MarketAPI mockUnseatedMarket() {

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getId())
            .thenReturn(MARKET_ID);
        when(marketMock.getStarSystem())
            .thenReturn(null);

        return marketMock;
    }
}
