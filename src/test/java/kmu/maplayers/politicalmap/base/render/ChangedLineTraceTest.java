package kmu.maplayers.politicalmap.base.render;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two things that make a repeated reading worth logging at all: a line is reported when it
 * says something new, and the reading behind it is not even taken while nothing is listening.
 *
 * <p>The second is the half a reader would not notice going wrong. Both readings this carries are
 * walks of the live widget tree, taken from inside a render pass that runs several times a frame, so
 * a describe called ahead of the level check would be the whole cost of the diagnostic paid by every
 * player who never turns it on.
 *
 * <p>Asserted through an appender on a logger of this test's own rather than through the emitted
 * text, since what a line reads like is the caller's business and what is asked for is this one's.
 */
final class ChangedLineTraceTest {

    private static final String SUBJECT = "Map icon order";
    private static final String LINE = "slipstream, nebula, mapLayer";
    private static final String CHANGED_LINE = "nebula, mapLayer, slipstream";

    private LogAppenderFake appenderFake;
    private Logger log;

    @BeforeEach
    void setUp() {

        appenderFake = new LogAppenderFake();
        log = Logger.getLogger("kmu.test." + ChangedLineTraceTest.class.getSimpleName());

        log.setLevel(Level.DEBUG);
        log.addAppender(appenderFake);
        // Kept off the root appenders, so a run's console output carries none of what these plant.
        log.setAdditivity(false);
    }

    @AfterEach
    void tearDown() {
        log.removeAppender(appenderFake);
        log.setLevel(null);
        log.setAdditivity(true);
    }

    @Nested
    class TraceWhenChanged {

        @Test
        void traceWhenChangedReportsALineTheFirstTimeItIsSeen() {

            new ChangedLineTrace(log, SUBJECT, () -> LINE)
                .traceWhenChanged();

            assertThat(appenderFake.getMessages())
                .containsExactly(SUBJECT + ": " + LINE);
        }

        @Test
        void traceWhenChangedStaysSilentWhileTheLineIsUnchanged() {
            // The state the map is in nearly always: a still cursor over a map nobody has reopened,
            // read every frame and worth saying once.
            var trace = new ChangedLineTrace(log, SUBJECT, () -> LINE);

            trace.traceWhenChanged();
            trace.traceWhenChanged();

            assertThat(appenderFake.getMessages())
                .containsExactly(SUBJECT + ": " + LINE);
        }

        @Test
        void traceWhenChangedReportsAgainOnceTheLineChanges() {
            // The one moment the trace exists for, so suppressing repeats must not suppress the
            // change they surround.
            var reportedLines = new ArrayList<>(List.of(LINE, CHANGED_LINE));
            var trace = new ChangedLineTrace(log, SUBJECT, () -> reportedLines.remove(0));

            trace.traceWhenChanged();
            trace.traceWhenChanged();

            assertThat(appenderFake.getMessages())
                .containsExactly(SUBJECT + ": " + LINE, SUBJECT + ": " + CHANGED_LINE);
        }

        @Test
        void traceWhenChangedStaysSilentWhenTheReadingHasNoLineToGive() {
            // Every reading behind one of these answers null off the screens it does not apply to,
            // which is an ordinary state rather than something to report.
            new ChangedLineTrace(log, SUBJECT, () -> null)
                .traceWhenChanged();

            assertThat(appenderFake.getMessages())
                .isEmpty();
        }

        @Test
        void traceWhenChangedDescribesNothingWhileTheLogIsAboveDebug() {
            // What every player who never turns the trace on pays: nothing. The describe is a walk
            // of the live widget tree, so asking for one and discarding it would be the whole cost
            // of the diagnostic, several times a frame.
            var describeCount = new int[1];

            log.setLevel(Level.INFO);

            new ChangedLineTrace(log, SUBJECT, () -> {
                describeCount[0]++;
                return LINE;
            }).traceWhenChanged();

            assertThat(describeCount[0])
                .isZero();
        }
    }

    // Records what reached the log, a logged line being the one thing this trace does that leaves no
    // trace in its own state.
    private static final class LogAppenderFake extends AppenderSkeleton {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }

        List<String> getMessages() {
            return messages;
        }

        @Override
        protected void append(LoggingEvent event) {
            messages.add(String.valueOf(event.getMessage()));
        }
    }
}
