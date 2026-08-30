package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.installation.MapLayerInstallations;
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
 * is made fresh with the installation for each case.
 */
final class PoliticalMapColonySizeListenerTest {

    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private final MapLayerRefreshBoard refreshBoard =
        MapLayerInstallations.installMachineryOn(sectorMock).resolveRefreshBoard();

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

        @Test
        void marksOnlyTheSectorTheListenerWasInstalledOn() {
            // A listener reading the running game instead of the sector it was built against would
            // mark whichever sector the player has loaded for a resize belonging to another.
            var otherSectorMock = mock(SectorAPI.class);
            var otherInstallation = MapLayerInstallations.installMachineryOn(otherSectorMock);

            listener.reportColonySizeChanged(mockMarketInSystem("sys"), 3);

            assertThat(otherInstallation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .isEmpty();
        }
    }
}
