package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;

import kmlib.testfixtures.logging.LogAppenderFake;
import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import kmu.maplayers.base.machinery.SectorMapMachinery;

import org.apache.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the constraint's verdicts reach the log on a change and never on a repeat: one line when
 * passes start being stood down, one when one is let through again, none for a pass admitted first.
 */
final class HostConstraintLogTests {

    private static final String STOOD_DOWN_LINE =
        "Map layers stood a pass down: they are kept to the vanilla maps and none is drawn."
            + " A pass from a mod's own map draws nothing while that holds.";

    private static final String ADMITTED_LINE =
        "Map layers admitted a pass again: a vanilla map is drawn.";

    private final SectorMapMachinery machinery = new SectorMapMachinery(null);

    private MockedStatic<Global> globalMock;

    @BeforeEach
    void standInForTheRunningGame() {
        globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers();
    }

    @AfterEach
    void releaseTheRunningGame() {
        globalMock.close();
    }

    @Nested
    class RecordPassVerdict {

        // Admitting is what the layers do on any vanilla map, so a line here would say nothing.
        @Test
        void saysNothingForAPassAdmittedFirst() {

            // ARRANGE
            var log = HostConstraintLog.resolveLogIn(machinery);

            // ACT
            var capture = LogAppenderFake.captureLogOf(
                HostConstraintLog.class,
                () -> log.recordPassVerdict(true));

            // ASSERT
            assertThat(capture.getMessages())
                .isEmpty();
        }

        // A stood-down pass recurs every frame, so only the first of a run is news.
        @Test
        void saysOnceWhenPassesStartBeingStoodDown() {

            // ARRANGE
            var log = HostConstraintLog.resolveLogIn(machinery);

            // ACT
            var capture = LogAppenderFake.captureLogOf(HostConstraintLog.class, () -> {
                log.recordPassVerdict(false);
                log.recordPassVerdict(false);
            });

            // ASSERT
            assertThat(capture.getMessages())
                .containsExactly(STOOD_DOWN_LINE);
        }

        @Test
        void saysOnceWhenAPassIsAdmittedAgain() {

            // ARRANGE
            var log = HostConstraintLog.resolveLogIn(machinery);

            log.recordPassVerdict(false);

            // ACT
            var capture = LogAppenderFake.captureLogOf(HostConstraintLog.class, () -> {
                log.recordPassVerdict(true);
                log.recordPassVerdict(true);
            });

            // ASSERT
            assertThat(capture.getMessages())
                .containsExactly(ADMITTED_LINE);
        }

        // A diagnostic about why a map shows nothing, not a fault, so it stays out of a default log.
        @Test
        void saysItAtDebug() {

            // ARRANGE
            var log = HostConstraintLog.resolveLogIn(machinery);

            // ACT
            var capture = LogAppenderFake.captureLogOf(
                HostConstraintLog.class,
                () -> log.recordPassVerdict(false));

            // ASSERT
            assertThat(capture.getEvents())
                .singleElement()
                .extracting(event -> event.getLevel())
                .isEqualTo(Level.DEBUG);
        }
    }

    @Nested
    class DisposeMachinery {

        // A sector installed afresh starts with no run of stood-down passes behind it.
        @Test
        void forgetsTheRunOnceReleased() {

            // ARRANGE
            var log = HostConstraintLog.resolveLogIn(machinery);

            log.recordPassVerdict(false);
            log.disposeMachinery();

            // ACT
            var capture = LogAppenderFake.captureLogOf(
                HostConstraintLog.class,
                () -> log.recordPassVerdict(true));

            // ASSERT
            assertThat(capture.getMessages())
                .isEmpty();
        }
    }
}
