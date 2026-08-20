package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.colonies.ColonyVisibility;

import kmu.maplayers.base.visibility.MapVisibilityOverrides;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what this wrapper adds over the generic motion tracker it delegates to: the single
 * shared instance the campaign-thread writer and the render-thread reader both reach, the
 * null-sector guard, and - the load-bearing part - that the walk is scoped to the drawn-set
 * rule, so a system the map does not draw is never tracked however far it moves. Whether a
 * given shift counts as motion at all is the tracker's own contract, not re-asserted here.
 *
 * <p>Exercised on its own instance rather than the shared one, which the package-visible
 * constructor exists for: the singleton outlives any single scenario, so a test driving it
 * would leak its observations into the next.
 */
class MovingSystemsTest {

    // The force override on, so the drawn-set rule admits a system nothing else would put on
    // the map - the shortest route to a tracked system without also staging an economy that
    // owns it.
    private static final MapVisibilityOverrides FORCED_ONTO_MAP =
            new MapVisibilityOverrides(ColonyVisibility.BASE_FOG, true);

    @Nested
    class GetInstance {

        @Test
        void answersTheSameSharedTrackerEveryCall() {
            // The writer (the staleness poll) and the reader (the geometry cache) have no
            // owner between them, so they only agree about the moving set if this is one
            // instance.
            assertThat(MovingSystems.getInstance()).isSameAs(MovingSystems.getInstance());
        }
    }

    @Nested
    class UpdateMovingSystems {

        @Test
        void reportsNoChangeAndObservesNothingForANullSector() {
            var movingSystems = new MovingSystems();

            assertThat(movingSystems.updateMovingSystems(null, FORCED_ONTO_MAP)).isFalse();
            assertThat(movingSystems.getMovingSystemIds()).isEmpty();
        }

        @Test
        void reportsNoChangeOnTheFirstPollThatOnlySeedsABaseline() {
            var sectorFake = new MovableSystemSectorFake();
            var movingSystems = new MovingSystems();

            assertThat(movingSystems.updateMovingSystems(
                    sectorFake.getSector(), FORCED_ONTO_MAP)).isFalse();
            assertThat(movingSystems.getMovingSystemIds()).isEmpty();
        }

        @Test
        void namesADrawnSystemOnceItMovesOffItsBaseline() {
            var sectorFake = new MovableSystemSectorFake();
            var movingSystems = new MovingSystems();
            movingSystems.updateMovingSystems(sectorFake.getSector(), FORCED_ONTO_MAP);

            sectorFake.moveSystemClearOfItsLastPosition();

            assertThat(movingSystems.updateMovingSystems(
                    sectorFake.getSector(), FORCED_ONTO_MAP)).isTrue();
            assertThat(movingSystems.getMovingSystemIds()).containsExactly("a");
        }

        @Test
        void reportsNoChangeWhileASystemMerelyKeepsMoving() {
            // A steady drifter is already out of the partition, so a further move is no
            // transition and must not churn the map.
            var sectorFake = new MovableSystemSectorFake();
            var movingSystems = new MovingSystems();
            movingSystems.updateMovingSystems(sectorFake.getSector(), FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();
            movingSystems.updateMovingSystems(sectorFake.getSector(), FORCED_ONTO_MAP);

            sectorFake.moveSystemClearOfItsLastPosition();

            assertThat(movingSystems.updateMovingSystems(
                    sectorFake.getSector(), FORCED_ONTO_MAP)).isFalse();
            assertThat(movingSystems.getMovingSystemIds()).containsExactly("a");
        }

        @Test
        void reportsTheChangeOnceAMoverComesToRest() {
            // The other half of the transition: a mover that stops rejoins the partition
            // wherever it halted, so the geometry has to rebuild around it.
            var sectorFake = new MovableSystemSectorFake();
            var movingSystems = new MovingSystems();
            movingSystems.updateMovingSystems(sectorFake.getSector(), FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();
            movingSystems.updateMovingSystems(sectorFake.getSector(), FORCED_ONTO_MAP);

            assertThat(movingSystems.updateMovingSystems(
                    sectorFake.getSector(), FORCED_ONTO_MAP)).isTrue();
            assertThat(movingSystems.getMovingSystemIds()).isEmpty();
        }

        @Test
        void ignoresASystemTheMapDoesNotDrawHoweverFarItMoves() {
            // The walk is scoped to the drawn set, so an off-map system seeds no cell and has
            // no border to drag around. Tracking it would report a moving-set change - and so
            // a whole-map geometry rebuild - for a system the overlay never paints.
            var sectorFake = new MovableSystemSectorFake();
            var movingSystems = new MovingSystems();
            movingSystems.updateMovingSystems(
                    sectorFake.getSector(), MapVisibilityOverrides.NONE);

            sectorFake.moveSystemClearOfItsLastPosition();

            assertThat(movingSystems.updateMovingSystems(
                    sectorFake.getSector(), MapVisibilityOverrides.NONE)).isFalse();
            assertThat(movingSystems.getMovingSystemIds()).isEmpty();
        }
    }

    @Nested
    class Reset {

        @Test
        void dropsObservationsSoASystemIsJudgedAfreshAfterASaveLoad() {
            // The shared tracker outlives a save, so without this a system id reused by the
            // next save would be measured against the previous save's last-seen position and
            // read as having teleported.
            var sectorFake = new MovableSystemSectorFake();
            var movingSystems = new MovingSystems();
            movingSystems.updateMovingSystems(sectorFake.getSector(), FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();
            movingSystems.updateMovingSystems(sectorFake.getSector(), FORCED_ONTO_MAP);

            movingSystems.reset();

            assertThat(movingSystems.getMovingSystemIds()).isEmpty();
            assertThat(movingSystems.updateMovingSystems(
                    sectorFake.getSector(), FORCED_ONTO_MAP)).isFalse();
            assertThat(movingSystems.getMovingSystemIds()).isEmpty();
        }
    }

    // A one-system sector whose system reports a position the test rewrites between polls,
    // the way a mobile system rewrites its own getLocation() every frame. The position is one
    // mutable vector handed back on every read rather than a queue of stubbed returns, so a
    // test moves the system per poll rather than per getLocation() call - the walk's call
    // count is the tracker's business, not something these tests should encode.
    //
    // Hyperspace carries no star anchor and the system no jump point, so nothing here is
    // drawn on the normal gates; each test decides admission through the overrides it passes.
    private static final class MovableSystemSectorFake {
        // Comfortably past the tracker's one-unit noise floor, so each move is unambiguous
        // motion rather than something that could read as float jitter.
        private static final float CLEAR_OF_THE_NOISE_FLOOR = 500f;

        private final Vector2f livePosition = new Vector2f(0f, 0f);
        private final SectorAPI sectorMock = mock(SectorAPI.class);

        private MovableSystemSectorFake() {
            var systemMock = mock(StarSystemAPI.class);
            when(systemMock.getId()).thenReturn("a");
            when(systemMock.getJumpPoints()).thenReturn(List.of());
            when(systemMock.getLocation()).thenReturn(livePosition);
            var economyMock = mock(EconomyAPI.class);
            when(economyMock.getMarkets(systemMock)).thenReturn(List.<MarketAPI>of());
            var hyperspaceMock = mock(LocationAPI.class);
            when(hyperspaceMock.getEntities(JumpPointAPI.class)).thenReturn(List.of());
            when(sectorMock.getStarSystems()).thenReturn(List.of(systemMock));
            when(sectorMock.getEconomy()).thenReturn(economyMock);
            when(sectorMock.getHyperspace()).thenReturn(hyperspaceMock);
        }

        private SectorAPI getSector() {
            return sectorMock;
        }

        private void moveSystemClearOfItsLastPosition() {
            livePosition.x += CLEAR_OF_THE_NOISE_FLOOR;
        }
    }
}
