package kmu.maplayers.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.politicalmap.refresh.MarketRefreshFixtures.mockMarketInSystem;
import static kmu.maplayers.politicalmap.refresh.MarketRefreshFixtures.mockUnseatedMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapMarketTransferListener}: a colony changing hands marks its star system
 * politics-stale, while a transfer of a market with no star system marks nothing.
 *
 * <p>Read off the board of the sector the listener was installed on, which is where it reports and
 * is made fresh with the machinery for each case. That board is also what says the listener
 * reports against the sector it holds rather than against the running game: a mark aimed anywhere
 * else lands on the machinery that sector has not got, and never reaches here.
 */
final class PoliticalMapMarketTransferListenerTest {

    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private final MapLayerRefreshBoard refreshBoard =
        SectorMapMachineryIndex.installMachineryOn(sectorMock).resolveRefreshBoard();

    private final PoliticalMapMarketTransferListener listener =
        new PoliticalMapMarketTransferListener(sectorMock);

    @Nested
    class ReportMarketTransferred {

        @Test
        void reportMarketTransferredMarksTheTransferredColonysSystemStale() {

            listener.reportMarketTransferred(
                mockMarketInSystem("sys"),
                mockFaction("defender"),
                mockFaction("attacker"),
                true);

            assertThat(refreshBoard.drainStaleGroupingSystemKeys())
                .containsExactly(buildCellKey("sys"));
        }

        @Test
        void reportMarketTransferredMarksNothingForMarketWithoutStarSystem() {

            listener.reportMarketTransferred(
                mockUnseatedMarket(),
                mockFaction("defender"),
                mockFaction("attacker"),
                true);

            assertThat(refreshBoard.drainStaleGroupingSystemKeys())
                .isEmpty();
        }

        @Test
        void reportMarketTransferredMarksNothingForNullMarket() {

            listener.reportMarketTransferred(
                null,
                mockFaction("defender"),
                mockFaction("attacker"),
                true);

            assertThat(refreshBoard.drainStaleGroupingSystemKeys())
                .isEmpty();
        }
    }

    private static FactionAPI mockFaction(String factionId) {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getId())
            .thenReturn(factionId);

        return factionMock;
    }
}
