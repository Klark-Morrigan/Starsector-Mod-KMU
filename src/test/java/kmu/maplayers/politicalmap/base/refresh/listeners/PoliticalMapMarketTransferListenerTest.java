package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockUnseatedMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapMarketTransferListener}: a Nexerelin market transfer
 * marks the transferred colony's star system politics-stale, while a transfer of
 * a market with no star system marks nothing. An invasion finishing is a no-op,
 * since the transfer - not the invasion outcome - is what changes the holder.
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

    // Named to match Nexerelin's interface, which misspells "transferred" with a
    // single r.
    @Nested
    class ReportMarketTransfered {

        @Test
        void marksTheTransferredColonysSystemStale() {

            listener.reportMarketTransfered(
                mockMarketInSystem("sys"),
                mockFaction("attacker"),
                mockFaction("defender"),
                true,
                true,
                List.of(),
                1.0f);

            assertThat(refreshBoard.drainStaleGroupingSystemKeys())
                .containsExactly(buildCellKey("sys"));
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {

            listener.reportMarketTransfered(
                mockUnseatedMarket(),
                mockFaction("attacker"),
                mockFaction("defender"),
                true,
                true,
                List.of(),
                1.0f);

            assertThat(refreshBoard.drainStaleGroupingSystemKeys())
                .isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {

            listener.reportMarketTransfered(
                null,
                mockFaction("attacker"),
                mockFaction("defender"),
                true,
                true,
                List.of(),
                1.0f);

            assertThat(refreshBoard.drainStaleGroupingSystemKeys())
                .isEmpty();
        }
    }

    @Nested
    class ReportInvasionFinished {

        @Test
        void marksNothingSinceTransferReportsTheHolderChange() {
            // An invasion finishing does not itself transfer holding; the refresh
            // keys off reportMarketTransferred, so this callback marks nothing.
            listener.reportInvasionFinished(
                mock(CampaignFleetAPI.class),
                mockFaction("attacker"),
                mockMarketInSystem("sys"),
                3.0f,
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
