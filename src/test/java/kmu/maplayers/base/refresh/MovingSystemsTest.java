package kmu.maplayers.base.refresh;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.refresh.MovableSystemSectorFake.FORCED_ONTO_MAP;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what this wrapper adds over the generic motion tracker it delegates to: the null-sector
 * guard, and - the load-bearing part - that the walk is scoped to the drawn-set rule, so a system
 * the map does not draw is never tracked however far it moves. Whether a given shift counts as
 * motion at all is the tracker's own contract, not re-asserted here.
 *
 * <p>Every case builds its own tracker, the way machinery does. That two of them keep their
 * observations apart is a claim about the holder, and is pinned there.
 */
class MovingSystemsTest {

    // The id the staged system reports. Nothing here turns on which id it is; the cases name it
    // only to read the moving set back.
    private static final String DRIFTER_ID = "a";

    // The key the staged system carries: the id alone, a staged system stating no centre and no
    // anchor. Written out rather than read off the system, an expectation taken from the code
    // under test being no expectation at all.
    private static final SystemKey DRIFTER_KEY = new SystemKey("a", "", "");

    @Nested
    class UpdateMovingSystems {

        @Test
        void reportsNoChangeAndObservesNothingWithoutASectorToWalk() {
            // A poll that found no sector opens a pass over none, so the walk has nothing to
            // reach - and one handed no pass at all has not even that.
            var movingSystems = new MovingSystems();

            assertThat(movingSystems.updateMovingSystems(
                    MapVisibilityPass.over(null, FORCED_ONTO_MAP)))
                .isFalse();

            assertThat(movingSystems.updateMovingSystems(null))
                .isFalse();

            assertThat(movingSystems.getMovingSystemKeys())
                .isEmpty();
        }

        @Test
        void reportsNoChangeOnTheFirstPollThatOnlySeedsABaseline() {

            var sectorFake = new MovableSystemSectorFake(DRIFTER_ID);
            var movingSystems = new MovingSystems();

            assertThat(sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP))
                .isFalse();
            assertThat(movingSystems.getMovingSystemKeys())
                .isEmpty();
        }

        @Test
        void namesADrawnSystemOnceItMovesOffItsBaseline() {

            var sectorFake = new MovableSystemSectorFake(DRIFTER_ID);
            var movingSystems = new MovingSystems();

            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();

            assertThat(sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP))
                .isTrue();
            assertThat(movingSystems.getMovingSystemKeys())
                .containsExactly(DRIFTER_KEY);
        }

        @Test
        void reportsNoChangeWhileASystemMerelyKeepsMoving() {
            // A steady drifter is already out of the partition, so a further move is no
            // transition and must not churn the map.
            var sectorFake = new MovableSystemSectorFake(DRIFTER_ID);
            var movingSystems = new MovingSystems();

            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();

            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();

            assertThat(sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP))
                .isFalse();
            assertThat(movingSystems.getMovingSystemKeys())
                .containsExactly(DRIFTER_KEY);
        }

        @Test
        void reportsTheChangeOnceAMoverComesToRest() {
            // The other half of the transition: a mover that stops rejoins the partition
            // wherever it halted, so the geometry has to rebuild around it.
            var sectorFake = new MovableSystemSectorFake(DRIFTER_ID);
            var movingSystems = new MovingSystems();

            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();
            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);

            assertThat(sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP))
                .isTrue();
            assertThat(movingSystems.getMovingSystemKeys())
                .isEmpty();
        }

        @Test
        void ignoresASystemTheMapDoesNotDrawHoweverFarItMoves() {
            // The walk is scoped to the drawn set, so an off-map system seeds no cell and has
            // no border to drag around. Tracking it would report a moving-set change - and so
            // a whole-map geometry rebuild - for a system the overlay never paints.
            var sectorFake = new MovableSystemSectorFake(DRIFTER_ID);
            var movingSystems = new MovingSystems();

            sectorFake.observePositionsInto(movingSystems, MapVisibilityRules.BASE);
            sectorFake.moveSystemClearOfItsLastPosition();

            assertThat(sectorFake.observePositionsInto(movingSystems, MapVisibilityRules.BASE))
                .isFalse();
            assertThat(movingSystems.getMovingSystemKeys())
                .isEmpty();
        }
    }
}
