package kmu.politicalmap.refresh;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link MovingSystems}'s motion detection: a first sighting cannot be judged
 * moving; a system that holds still is not moving; one whose position changed is; a
 * system that keeps moving stays in the set without re-reporting a change; and one
 * that stops leaves the set. Drives the detection core directly so the state machine
 * is pinned without a sector.
 */
final class MovingSystemsTest {

    @Nested
    class UpdateMovingSystems {

        @Test
        void aFirstSightingIsNotYetJudgedMoving() {
            var movingSystems = new MovingSystems();

            // No baseline to compare against, so nothing is moving and the set (still
            // empty) has not changed.
            var hasChanged = movingSystems.updateMovingSystems(Map.of("m", point(0, 0)));

            assertThat(hasChanged).isFalse();
            assertThat(movingSystems.getMovingSystemIds()).isEmpty();
        }

        @Test
        void aStationarySystemIsNotMoving() {
            var movingSystems = new MovingSystems();
            movingSystems.updateMovingSystems(Map.of("m", point(0, 0)));

            var hasChanged = movingSystems.updateMovingSystems(Map.of("m", point(0, 0)));

            assertThat(hasChanged).isFalse();
            assertThat(movingSystems.getMovingSystemIds()).isEmpty();
        }

        @Test
        void aSystemThatChangedPositionIsMoving() {
            var movingSystems = new MovingSystems();
            movingSystems.updateMovingSystems(Map.of("m", point(0, 0)));

            var hasChanged = movingSystems.updateMovingSystems(Map.of("m", point(500, 0)));

            assertThat(hasChanged).isTrue();
            assertThat(movingSystems.getMovingSystemIds()).containsExactly("m");
        }

        @Test
        void aSystemThatKeepsMovingStaysInTheSetWithoutReportingAChange() {
            var movingSystems = new MovingSystems();
            movingSystems.updateMovingSystems(Map.of("m", point(0, 0)));
            movingSystems.updateMovingSystems(Map.of("m", point(500, 0)));

            // Still moving, to a fresh point: already excluded, so the set is unchanged
            // and no rebuild is asked for.
            var hasChanged = movingSystems.updateMovingSystems(Map.of("m", point(1200, 0)));

            assertThat(hasChanged).isFalse();
            assertThat(movingSystems.getMovingSystemIds()).containsExactly("m");
        }

        @Test
        void aSystemThatStopsLeavesTheMovingSet() {
            var movingSystems = new MovingSystems();
            movingSystems.updateMovingSystems(Map.of("m", point(0, 0)));
            movingSystems.updateMovingSystems(Map.of("m", point(500, 0)));

            // Holds at its last point: no longer moving, so it rejoins the partition
            // and the set changes back to empty.
            var hasChanged = movingSystems.updateMovingSystems(Map.of("m", point(500, 0)));

            assertThat(hasChanged).isTrue();
            assertThat(movingSystems.getMovingSystemIds()).isEmpty();
        }

        @Test
        void aReDrawnSystemIsJudgedAfreshRatherThanAgainstAStalePosition() {
            var movingSystems = new MovingSystems();
            movingSystems.updateMovingSystems(Map.of("m", point(0, 0), "n", point(0, 0)));
            // "n" drops off the map, so its baseline is pruned.
            movingSystems.updateMovingSystems(Map.of("m", point(0, 0)));

            // "n" returns far from where it last was; without pruning that gap would
            // read as motion, but a re-drawn system is a first sighting again, so it is
            // not moving.
            var hasChanged =
                    movingSystems.updateMovingSystems(Map.of("m", point(0, 0), "n", point(5000, 0)));

            assertThat(hasChanged).isFalse();
            assertThat(movingSystems.getMovingSystemIds()).isEmpty();
        }
    }

    private static double[] point(double x, double y) {
        return new double[] {x, y};
    }
}
