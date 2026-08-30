package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.visibility.colonies.SectorColonySightings;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockUnseatedMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins {@link PoliticalMapDecivListener}: a completed decivilisation marks its
 * own star system politics-stale (whether or not the colony was fully
 * destroyed), while the pre-deciv phase and a market with no star system (a
 * deep-hyperspace station) mark nothing.
 *
 * <p>Read off the board of the sector the listener was installed on, which is where it reports and
 * is made fresh with the installation for each case. That board is also what says the listener
 * reports against the sector it holds rather than against the running game: a mark aimed anywhere
 * else lands on the installation that sector has not got, and never reaches here.
 *
 * <p>The pre-deciv phase does write one thing, and it is the reason that phase is listened to at
 * all: what the dying colony still vouches for is recorded while it can still be read. That case
 * needs a real sector to write into, so it builds one and installs a listener on it rather than
 * using the mock the marking cases share.
 */
final class PoliticalMapDecivListenerTest {

    private static final int COLONY_SIZE = 5;
    private static final int DERELICT_SIZE = 3;
    private static final String SYSTEM_ID = "kumari_kandam";

    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private final MapLayerRefreshBoard refreshBoard =
        MapLayerInstallations.installMachineryOn(sectorMock).resolveRefreshBoard();

    private final PoliticalMapDecivListener listener = new PoliticalMapDecivListener(sectorMock);

    @Nested
    class ReportColonyDecivilized {

        @Test
        void marksTheDecivilisedColonysSystemStale() {

            listener.reportColonyDecivilized(mockMarketInSystem("sys"), false);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }

        @Test
        void marksTheSystemStaleEvenWhenFullyDestroyed() {

            listener.reportColonyDecivilized(mockMarketInSystem("sys"), true);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {

            listener.reportColonyDecivilized(mockUnseatedMarket(), false);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {

            listener.reportColonyDecivilized(null, false);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }
    }

    @Nested
    class ReportColonyAboutToBeDecivilized {

        @Test
        void marksNothingSinceTheColonyIsStillFactionOwned() {

            listener.reportColonyAboutToBeDecivilized(mockMarketInSystem("sys"), false);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void recordsWhatTheDyingColonyStillVouchesForInItsOwnSectorsRegister() {
            // The one write that cannot be deferred. The colony is about to stop vouching for the
            // derelict beside it, and once it has there is nobody left to date that observation
            // by - so it is taken here, while the colony can still be read as the observer it is.
            //
            // Written into the register of the sector the listener holds, which is what keeps a
            // sighting made in one sector out of another's memory.
            var sector = SectorPoliticsFixtures.buildSystemHoldingAColonyAndADerelict(
                SYSTEM_ID,
                COLONY_SIZE,
                DERELICT_SIZE);

            var system = SectorPoliticsFixtures.findSystemIn(sector, SYSTEM_ID);

            SectorPoliticsFixtures.openSectorMemory(sector);

            new PoliticalMapDecivListener(sector).reportColonyAboutToBeDecivilized(
                sector.getEconomy().getMarkets(system).get(0),
                false);

            var derelict = system.getAllEntities().get(0).getMarket();

            assertThat(SectorColonySightings
                    .readSightings(sector)
                    .readObservation(derelict.getId())
                    .locationId())
                .isEqualTo(SYSTEM_ID);
        }

        @Test
        void recordsNothingForAMarketSeatedInNoSystem() {

            listener.reportColonyAboutToBeDecivilized(mockUnseatedMarket(), false);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void recordsNothingForNullMarket() {

            listener.reportColonyAboutToBeDecivilized(null, false);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }
    }
}
