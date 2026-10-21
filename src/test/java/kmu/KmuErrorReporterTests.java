package kmu;

import kmlib.testfixtures.logging.LogAppenderFake;
import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import org.apache.log4j.Level;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins where the logging reporter writes a caught failure: under the reporting class's own logger, as
 * a warning carrying its cause.
 */
class KmuErrorReporterTests {

    private static final String MESSAGE = "Failed to list market condition specs.";

    @Nested
    class CreateLoggingReporter {

        @Test
        void writesTheFailureAsAWarningUnderTheReportingClassWithItsCause() {
            // Under the reporter's own logger the line would name nowhere a reader could look, and
            // the level set for the reporting feature's package would not reach it.
            var failure = new IllegalStateException("spec list closed");

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var reporter = KmuErrorReporter.createLoggingReporter(ReportingClass.class);
                var log = LogAppenderFake.captureLogOf(
                    ReportingClass.class,
                    () -> reporter.report(MESSAGE, failure));

                assertThat(log.getMessages())
                    .containsExactly(MESSAGE);
                assertThat(log.getEvents())
                    .singleElement()
                    .extracting(event -> event.getLevel())
                    .isEqualTo(Level.WARN);
                assertThat(log.getThrowables())
                    .containsExactly(failure);
            }
        }
    }

    // A class standing in for a feature that reports, so the logger the line lands under is one
    // nothing else in the suite writes to.
    private static final class ReportingClass {
    }
}
