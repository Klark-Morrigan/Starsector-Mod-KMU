package kmu.maplayers.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.politicalmap.refresh.MarketRefreshFixtures.mockEntityWithMarketInSystem;
import static kmu.maplayers.politicalmap.refresh.MarketRefreshFixtures.mockUnseatedMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapDiscoveryListener}: discovering a market marks its
 * system politics-stale (the same targeted refresh a colony resize uses), while
 * discovering something with no market (a jump point, inert salvage) marks
 * nothing - accessibility is the sector watcher's job, not the listener's.
 *
 * <p>Read off the board of the sector the listener was installed on, which is where it reports and
 * is made fresh with the machinery for each case. That board is also what says the listener
 * reports against the sector it holds rather than against the running game: a mark aimed anywhere
 * else lands on the machinery that sector has not got, and never reaches here.
 */
final class PoliticalMapDiscoveryListenerTest {

    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private final MapLayerRefreshBoard refreshBoard =
        SectorMapMachineryIndex.installMachineryOn(sectorMock).resolveRefreshBoard();

    private final PoliticalMapDiscoveryListener listener =
        new PoliticalMapDiscoveryListener(sectorMock);

    @Nested
    class ReportEntityDiscovered {

        @Test
        void marksSystemStaleWhenDiscoveredEntityHasAMarket() {

            listener.reportEntityDiscovered(mockEntityWithMarketInSystem("sys"));

            assertThat(refreshBoard.drainStaleGroupingSystemKeys())
                .containsExactly(buildCellKey("sys"));
        }

        @Test
        void marksNothingForMarketlessEntity() {
            // A jump point has no market; accessibility is judged elsewhere, so the
            // listener marks no system and requests no geometry rebuild.
            listener.reportEntityDiscovered(mock(JumpPointAPI.class));

            assertThat(refreshBoard.drainStaleGroupingSystemKeys())
                .isEmpty();
            assertThat(refreshBoard.getRevision(MapLayerCommonRefreshSignal.GEOMETRY))
                .isZero();
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {

            var marketMock = mockUnseatedMarket();
            var entityMock = mock(SectorEntityToken.class);

            when(entityMock.getMarket())
                .thenReturn(marketMock);

            listener.reportEntityDiscovered(entityMock);

            assertThat(refreshBoard.drainStaleGroupingSystemKeys())
                .isEmpty();
        }
    }
}
