package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockUnseatedMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins {@link PoliticalMapColonySizeListener}: a colony resize marks its own
 * star system politics-stale, while a resize on a market with no star system (a
 * deep-hyperspace station) marks nothing.
 *
 * <p>Read off the board of the sector the listener was installed on, which is where it reports and
 * is made fresh with the machinery for each case. That board is also what says the listener
 * reports against the sector it holds rather than against the running game: a mark aimed anywhere
 * else lands on the machinery that sector has not got, and never reaches here.
 */
final class PoliticalMapColonySizeListenerTest {

    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private final MapLayerRefreshBoard refreshBoard =
        SectorMapMachineryIndex.installMachineryOn(sectorMock).resolveRefreshBoard();

    private final PoliticalMapColonySizeListener listener =
        new PoliticalMapColonySizeListener(sectorMock);

    @Nested
    class ReportColonySizeChanged {

        @Test
        void marksTheResizedColonysSystemStale() {

            listener.reportColonySizeChanged(mockMarketInSystem("sys"), 3);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {

            listener.reportColonySizeChanged(mockUnseatedMarket(), 3);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {

            listener.reportColonySizeChanged(null, 3);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }
    }
}
