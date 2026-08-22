package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.base.visibility.MapVisibilityPass;
import kmu.maplayers.base.visibility.MapVisibilityRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static kmlib.starsector.colonies.ColonyVisibility.BASE_FOG;

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
    private static final MapVisibilityRules FORCED_ONTO_MAP =
        new MapVisibilityRules(BASE_FOG, true);

    @Nested
    class GetInstance {

        @Test
        void answersTheSameSharedTrackerEveryCall() {
            // The writer (the staleness poll) and the reader (the geometry cache) have no
            // owner between them, so they only agree about the moving set if this is one
            // instance.
            assertThat(MovingSystems.getInstance())
                .isSameAs(MovingSystems.getInstance());
        }
    }

    @Nested
    class UpdateMovingSystems {

        @Test
        void reportsNoChangeAndObservesNothingWithoutASectorToWalk() {
            // A poll that found no sector opens a pass over none, so the walk has nothing to
            // reach - and one handed no pass at all has not even that.
            var movingSystems = new MovingSystems();

            assertThat(observePositions(movingSystems, null, FORCED_ONTO_MAP))
                .isFalse();

            assertThat(movingSystems.updateMovingSystems(null))
                .isFalse();

            assertThat(movingSystems.getMovingSystemIds())
                .isEmpty();
        }

        @Test
        void reportsNoChangeOnTheFirstPollThatOnlySeedsABaseline() {

            var sectorFake = new MovableSystemSectorFake();
            var movingSystems = new MovingSystems();

            assertThat(observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP))
                .isFalse();
            assertThat(movingSystems.getMovingSystemIds())
                .isEmpty();
        }

        @Test
        void namesADrawnSystemOnceItMovesOffItsBaseline() {

            var sectorFake = new MovableSystemSectorFake();
            var movingSystems = new MovingSystems();

            observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();

            assertThat(observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP))
                .isTrue();
            assertThat(movingSystems.getMovingSystemIds())
                .containsExactly("a");
        }

        @Test
        void reportsNoChangeWhileASystemMerelyKeepsMoving() {
            // A steady drifter is already out of the partition, so a further move is no
            // transition and must not churn the map.
            var sectorFake = new MovableSystemSectorFake();
            var movingSystems = new MovingSystems();

            observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();

            observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();

            assertThat(observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP))
                .isFalse();
            assertThat(movingSystems.getMovingSystemIds())
                .containsExactly("a");
        }

        @Test
        void reportsTheChangeOnceAMoverComesToRest() {
            // The other half of the transition: a mover that stops rejoins the partition
            // wherever it halted, so the geometry has to rebuild around it.
            var sectorFake = new MovableSystemSectorFake();
            var movingSystems = new MovingSystems();

            observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();
            observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP);

            assertThat(observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP))
                .isTrue();
            assertThat(movingSystems.getMovingSystemIds())
                .isEmpty();
        }

        @Test
        void ignoresASystemTheMapDoesNotDrawHoweverFarItMoves() {
            // The walk is scoped to the drawn set, so an off-map system seeds no cell and has
            // no border to drag around. Tracking it would report a moving-set change - and so
            // a whole-map geometry rebuild - for a system the overlay never paints.
            var sectorFake = new MovableSystemSectorFake();
            var movingSystems = new MovingSystems();

            observePositions(movingSystems, sectorFake, MapVisibilityRules.BASE);
            sectorFake.moveSystemClearOfItsLastPosition();

            assertThat(observePositions(movingSystems, sectorFake, MapVisibilityRules.BASE))
                .isFalse();
            assertThat(movingSystems.getMovingSystemIds())
                .isEmpty();
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

            observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();

            observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP);
            movingSystems.reset();

            assertThat(movingSystems.getMovingSystemIds())
                .isEmpty();
            assertThat(observePositions(movingSystems, sectorFake, FORCED_ONTO_MAP))
                .isFalse();
            assertThat(movingSystems.getMovingSystemIds())
                .isEmpty();
        }
    }

    // One poll's observation, opening the reading of the sector a poll opens: a pass built fresh
    // and discarded with the call. Built per call rather than once per test because that is what
    // the walk is handed in production - a kept pass would answer the second poll off the
    // positions the first one saw.
    private static boolean observePositions(
            MovingSystems movingSystems,
            MovableSystemSectorFake sectorFake,
            MapVisibilityRules visibilityRules) {

        var sector = sectorFake == null ? null : sectorFake.getSector();

        return movingSystems.updateMovingSystems(
            MapVisibilityPass.over(sector, visibilityRules));
    }

    // A one-system sector whose system reports a position the test rewrites between polls,
    // the way a mobile system rewrites its own getLocation() every frame. The position is one
    // mutable vector handed back on every read rather than a queue of stubbed returns, so a
    // test moves the system per poll rather than per getLocation() call - the walk's call
    // count is the tracker's business, not something these tests should encode.
    //
    // Hyperspace carries no star anchor and the system no jump point, so nothing here is
    // drawn on the normal gates; each test decides admission through the rules it passes.
    private static final class MovableSystemSectorFake {

        // Comfortably past the tracker's one-unit noise floor, so each move is unambiguous
        // motion rather than something that could read as float jitter.
        private static final float CLEAR_OF_THE_NOISE_FLOOR = 500f;

        private final Vector2f livePosition = new Vector2f(0f, 0f);
        private final SectorAPI sectorMock = mock(SectorAPI.class);

        private MovableSystemSectorFake() {

            var systemMock = mock(StarSystemAPI.class);

            when(systemMock.getId())
                .thenReturn("a");
            when(systemMock.getJumpPoints())
                .thenReturn(List.of());
            when(systemMock.getLocation())
                .thenReturn(livePosition);

            var economyMock = mock(EconomyAPI.class);

            when(economyMock.getMarkets(systemMock))
                .thenReturn(List.<MarketAPI>of());

            var hyperspaceMock = mock(LocationAPI.class);

            when(hyperspaceMock.getEntities(JumpPointAPI.class))
                .thenReturn(List.of());

            when(sectorMock.getStarSystems())
                .thenReturn(List.of(systemMock));
            when(sectorMock.getEconomy())
                .thenReturn(economyMock);
            when(sectorMock.getHyperspace())
                .thenReturn(hyperspaceMock);
        }

        private SectorAPI getSector() {
            return sectorMock;
        }

        private void moveSystemClearOfItsLastPosition() {
            livePosition.x += CLEAR_OF_THE_NOISE_FLOOR;
        }
    }
}
