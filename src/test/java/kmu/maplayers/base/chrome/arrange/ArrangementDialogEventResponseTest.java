package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.input.InputEventAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the dialog's modality, which the game does not supply and nothing else checks. A panel stood
 * in the core UI is an ordinary child: nothing dims behind it and nothing stops the screen underneath
 * being dispatched to, so what is claimed here is the whole of what makes the dialog modal.
 *
 * <p>The claim is the whole screen, and the consumed skip is what parts the dialog's own controls
 * from everything under them: the engine hands a panel's events to its children before its plugin, so
 * a press a control acted on is already taken by the time the rule sees it. Excepting the box by
 * position instead is what left a star under the dim raising its own tooltip while the same star on
 * the open map answered nothing.
 *
 * <p>The consumed skip therefore carries two loads at once. It is also the guard against measuring an
 * event somebody has taken - six of {@link InputEventAPI}'s accessors throw once that has happened,
 * and the key readings below it are among them.
 *
 * <p>The dismissed case runs the opposite way: the box stays on screen for the length of its fade
 * while the screen is already the player's again, so a rule that went on claiming would eat the click
 * after the press rather than the press itself.
 */
final class ArrangementDialogEventResponseTest {

    // The dialog up for input, as against on screen but already dismissed.
    private static final boolean RAISED = true;

    private static final boolean DISMISSED = false;

    @Nested
    class ResolveResponseTo {

        @Test
        void resolveResponseToLeavesAnEventTheDialogsOwnWidgetsTookAlone() {
            // Which is the whole of what protects them, the engine dispatching a panel's children
            // before its plugin: a press a control acted on arrives here already consumed.
            var eventMock = mock(InputEventAPI.class);
            when(eventMock.isConsumed()).thenReturn(true);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(eventMock, RAISED))
                .isEqualTo(ArrangementDialogEventResponse.LEAVE_ALONE);
        }

        @Test
        void resolveResponseToMeasuresNothingOnAnEventSomethingElseConsumed() {
            // The accessors below the skip throw on a consumed event, so the skip standing ahead of
            // them is the difference between a rule and a crash out of an input pass.
            var eventMock = mock(InputEventAPI.class);
            when(eventMock.isConsumed()).thenReturn(true);
            when(eventMock.isKeyDownEvent())
                .thenThrow(new IllegalStateException("a consumed event was read"));

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(eventMock, RAISED))
                .isEqualTo(ArrangementDialogEventResponse.LEAVE_ALONE);
        }

        @Test
        void resolveResponseToClaimsAnEventNothingHasTaken() {
            // Wherever it landed. Nothing under the dialog is dispatched to, the box included - what
            // stands in the box has already had its turn.
            var eventMock = mock(InputEventAPI.class);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(eventMock, RAISED))
                .isEqualTo(ArrangementDialogEventResponse.CLAIM);
        }

        @Test
        void resolveResponseToLeavesEveryEventAloneWhileTheDialogIsOnlyFadingOut() {
            // The box is still on screen and the game still dispatches to it, but the screen went back
            // to the player at the press - so an event a raised dialog claims is left alone here.
            var eventMock = mock(InputEventAPI.class);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(eventMock, DISMISSED))
                .isEqualTo(ArrangementDialogEventResponse.LEAVE_ALONE);
        }

        @Test
        void resolveResponseToMeasuresNothingWhileTheDialogIsOnlyFadingOut() {
            // Asked before the event is touched at all, so a dialog that has let go answers for a
            // whole frame's events without reading one of them.
            var eventMock = mock(InputEventAPI.class);
            when(eventMock.isConsumed())
                .thenThrow(new IllegalStateException("an event was read for a dismissed dialog"));

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(eventMock, DISMISSED))
                .isEqualTo(ArrangementDialogEventResponse.LEAVE_ALONE);
        }

        @Test
        void resolveResponseToDoesNotCloseOnEscapeWhileTheDialogIsOnlyFadingOut() {
            // Escape belongs to whatever has the screen now, and that is no longer this dialog: a
            // second press should reach the screen behind rather than close a box already closing.
            var eventMock = mock(InputEventAPI.class);
            when(eventMock.isKeyDownEvent()).thenReturn(true);
            when(eventMock.getEventValue()).thenReturn(Keyboard.KEY_ESCAPE);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(eventMock, DISMISSED))
                .isEqualTo(ArrangementDialogEventResponse.LEAVE_ALONE);
        }

        @Test
        void resolveResponseToClosesOnEscape() {

            var eventMock = mock(InputEventAPI.class);
            when(eventMock.isKeyDownEvent()).thenReturn(true);
            when(eventMock.getEventValue()).thenReturn(Keyboard.KEY_ESCAPE);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(eventMock, RAISED))
                .isEqualTo(ArrangementDialogEventResponse.CLOSE_DIALOG);
        }

        @Test
        void resolveResponseToDoesNotCloseOnAKeyThatIsNotEscape() {
            // Escape is the only key with a meaning of its own here; every other one is claimed like
            // any other event, so no key reaches the screen underneath.
            var eventMock = mock(InputEventAPI.class);
            when(eventMock.isKeyDownEvent()).thenReturn(true);
            when(eventMock.getEventValue()).thenReturn(Keyboard.KEY_M);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(eventMock, RAISED))
                .isEqualTo(ArrangementDialogEventResponse.CLAIM);
        }
    }
}
