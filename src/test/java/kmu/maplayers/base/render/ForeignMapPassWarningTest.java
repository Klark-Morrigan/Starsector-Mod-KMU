package kmu.maplayers.base.render;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pins the three things this owes the log: it speaks when a pass drew the layers with no map on
 * screen, it stays quiet when one is showing, and it never lets a failed read reach the pass it was
 * only reporting on.
 *
 * <p>Asserted through the logger rather than through a flag, because the line is the whole product:
 * a warning that is recorded as having been said but never written is exactly the failure that
 * would make this useless in the one situation it exists for. The throwable it carries is asserted
 * as present for the same reason - the stack is the only part of the line that names the caller,
 * and the message alone would leave a reader no better off than the symptom did.
 *
 * <p>The presence read is a plain supplier here, which is what makes any of this answerable: the
 * live one walks the campaign UI's widget tree and cannot be stood up outside a running game.
 */
final class ForeignMapPassWarningTest {

    @Nested
    class WarnOnceIfNoMapIsShowing {

        @Test
        void warnOnceIfNoMapIsShowingReportsThePassWithItsStackWhenNoMapIsOnScreen() {

            var loggerMock = mock(Logger.class);

            new ForeignMapPassWarning(() -> false, loggerMock)
                .warnOnceIfNoMapIsShowing();

            // The throwable is the caller's stack, which is the only thing in the line that answers
            // the question the line is raised to ask.
            verify(loggerMock)
                .warn(any(), any(Throwable.class));
        }

        @Test
        void warnOnceIfNoMapIsShowingStaysQuietWhileAMapIsShowing() {
            // The ordinary case, and by far the common one: this runs on every frame the sector map
            // paints, so a line here would be written sixty times a second on a working setup.
            var loggerMock = mock(Logger.class);

            new ForeignMapPassWarning(() -> true, loggerMock)
                .warnOnceIfNoMapIsShowing();

            verifyNoInteractions(loggerMock);
        }

        @Test
        void warnOnceIfNoMapIsShowingReportsOneForeignPassOnlyOnce() {
            // It sits in a per-frame path, and a foreign pass is foreign on every frame it runs, so
            // an unlatched report would bury the log rather than inform it.
            var loggerMock = mock(Logger.class);
            var warning = new ForeignMapPassWarning(() -> false, loggerMock);

            warning.warnOnceIfNoMapIsShowing();
            warning.warnOnceIfNoMapIsShowing();
            warning.warnOnceIfNoMapIsShowing();

            verify(loggerMock, times(1))
                .warn(any(), any(Throwable.class));
        }

        @Test
        void warnOnceIfNoMapIsShowingStopsAskingOnceTheMapIsShownAgain() {
            // The latch is on having spoken, not on the state that prompted it: a pass that is
            // foreign once and a map that opens afterwards must not re-arm the line, or every
            // switch back and forth would add another copy of it.
            var loggerMock = mock(Logger.class);
            var isMapShowing = new boolean[] {false};
            var warning = new ForeignMapPassWarning(() -> isMapShowing[0], loggerMock);

            warning.warnOnceIfNoMapIsShowing();
            isMapShowing[0] = true;
            warning.warnOnceIfNoMapIsShowing();
            isMapShowing[0] = false;
            warning.warnOnceIfNoMapIsShowing();

            verify(loggerMock, times(1))
                .warn(any(), any(Throwable.class));
        }

        @Test
        void warnOnceIfNoMapIsShowingSwallowsAPresenceReadThatThrows() {
            // The read reaches into the live widget tree and into core classes. This is a report
            // about the frame and not part of drawing one, so a read that cannot be made must cost
            // the report alone - letting it out would take the overlay away over a log line.
            var loggerMock = mock(Logger.class);
            var warningOnBrokenRead = new ForeignMapPassWarning(
                () -> {
                    throw new IllegalStateException("no widget tree");
                },
                loggerMock);

            assertThatCode(warningOnBrokenRead::warnOnceIfNoMapIsShowing)
                .doesNotThrowAnyException();
        }

        @Test
        void warnOnceIfNoMapIsShowingStopsRetryingAReadThatThrows() {
            // A read that broke once breaks every frame, so the failure spends the warning too. The
            // reported throwable is the failure itself, which is what says which of the two lines
            // this was.
            var loggerMock = mock(Logger.class);
            var readFailure = new IllegalStateException("no widget tree");
            var warningOnBrokenRead = new ForeignMapPassWarning(
                () -> {
                    throw readFailure;
                },
                loggerMock);

            warningOnBrokenRead.warnOnceIfNoMapIsShowing();
            warningOnBrokenRead.warnOnceIfNoMapIsShowing();

            verify(loggerMock, times(1))
                .warn(anyString(), any(Throwable.class));
            verify(loggerMock, never())
                .warn(any());
        }
    }
}
