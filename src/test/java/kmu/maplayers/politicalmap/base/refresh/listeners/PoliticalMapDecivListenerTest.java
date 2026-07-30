package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.base.refresh.PoliticalMapRefresh;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapDecivListener}: a completed decivilisation marks its
 * own star system politics-stale (whether or not the colony was fully
 * destroyed), while the pre-deciv phase and a market with no star system (a
 * deep-hyperspace station) mark nothing. The stale set is drained to read it, so
 * each case clears it first.
 */
final class PoliticalMapDecivListenerTest {
    private final PoliticalMapDecivListener listener = new PoliticalMapDecivListener();

    @Nested
    class ReportColonyDecivilized {

        @Test
        void marksTheDecivilisedColonysSystemStale() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();

            listener.reportColonyDecivilized(marketInSystem("mkt", "sys"), false);

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).containsExactly("sys");
        }

        @Test
        void marksTheSystemStaleEvenWhenFullyDestroyed() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();

            listener.reportColonyDecivilized(marketInSystem("mkt", "sys"), true);

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();
            var marketMock = mock(MarketAPI.class);
            when(marketMock.getId()).thenReturn("mkt");
            when(marketMock.getStarSystem()).thenReturn(null);

            listener.reportColonyDecivilized(marketMock, false);

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();

            listener.reportColonyDecivilized(null, false);

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).isEmpty();
        }
    }

    @Nested
    class ReportColonyAboutToBeDecivilized {

        @Test
        void marksNothingSinceTheColonyIsStillFactionOwned() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();

            listener.reportColonyAboutToBeDecivilized(marketInSystem("mkt", "sys"), false);

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).isEmpty();
        }
    }

    private static MarketAPI marketInSystem(String marketId, String systemId) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(systemId);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getId()).thenReturn(marketId);
        when(marketMock.getStarSystem()).thenReturn(systemMock);
        return marketMock;
    }
}
