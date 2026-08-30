package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared market mocks for the tests around {@link MarketPoliticsRefresh} and the listeners that
 * funnel through it: a market seated in a star system, and a market seated in none.
 *
 * <p>One home for these because the seat is the only thing the refresh reads off a market, so
 * every suite here was stubbing the same two shapes by hand - and an "unseated" market that
 * drifts between suites would have them pinning two different guards under one name.
 */
public final class MarketRefreshFixtures {

    private static final String MARKET_ID = "mkt";

    // Fixtures only; never instantiated.
    private MarketRefreshFixtures() {
    }

    /**
     * A market seated in the named star system. The market's own id is fixed, since it reaches
     * only the log line: what the refresh acts on is the seat.
     *
     * @param systemId the seated system's id, which is what a mark names
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
