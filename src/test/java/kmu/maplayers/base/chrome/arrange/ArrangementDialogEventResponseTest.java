package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.input.InputEventAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the dialog's modality, which the game does not supply and nothing else checks. A panel stood
 * in the core UI is an ordinary child: nothing dims behind it and nothing stops the screen underneath
 * being dispatched to, so what is claimed here is the whole of what makes the dialog modal.
 *
 * <p>Both halves of the rule are load-bearing in opposite directions. Claim too little and a press
 * meant for the dialog reaches the map behind it; claim too much - the dialog's own box included -
 * and its buttons go dead on a build that hands events to a panel's plugin before its widgets, which
 * is an ordering the game does not publish.
 *
 * <p>And that a consumed event is never measured. Six of {@link InputEventAPI}'s accessors throw once
 * something has consumed the event, and the box test reads one of them - so the guard is not an
 * optimisation but the difference between a rule and a crash out of a render pass.
 *
 * <p>The dismissed case is the other half of the same question, and it runs the opposite way: the box
 * stays on screen for the length of its fade while the screen is already the player's again, so a rule
 * that went on claiming would eat the click after the press rather than the press itself.
 */
final class ArrangementDialogEventResponseTest {

    // A box test that fails the case if it is ever consulted, for the paths that must not reach it.
    private static final Predicate<InputEventAPI> UNREACHED_BOX_TEST =
        event -> {
            throw new AssertionError("the box was measured for an event that never reaches it");
        };

    private static final Predicate<InputEventAPI> INSIDE_THE_BOX = event -> true;

    private static final Predicate<InputEventAPI> OUTSIDE_THE_BOX = event -> false;

    // The dialog up for input, as against on screen but already dismissed.
    private static final boolean RAISED = true;

    private static final boolean DISMISSED = false;

    @Nested
    class ResolveResponseTo {

        @Test
        void resolveResponseToLeavesAnEventSomethingElseConsumedAlone() {

            var eventMock = mock(InputEventAPI.class);
            when(eventMock.isConsumed()).thenReturn(true);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(
                    eventMock, RAISED, UNREACHED_BOX_TEST))
                .isEqualTo(ArrangementDialogEventResponse.LEAVE_ALONE);
        }

        @Test
        void resolveResponseToLeavesEveryEventAloneWhileTheDialogIsOnlyFadingOut() {
            // The box is still on screen and the game still dispatches to it, but the screen went back
            // to the player at the press - so an event outside the box, which a raised dialog claims,
            // is left alone here.
            var eventMock = mock(InputEventAPI.class);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(
                    eventMock, DISMISSED, OUTSIDE_THE_BOX))
                .isEqualTo(ArrangementDialogEventResponse.LEAVE_ALONE);
        }

        @Test
        void resolveResponseToMeasuresNothingWhileTheDialogIsOnlyFadingOut() {
            // Asked before either reading, so a dialog that has let go answers for a whole frame's
            // events without touching one of them.
            var eventMock = mock(InputEventAPI.class);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(
                    eventMock, DISMISSED, UNREACHED_BOX_TEST))
                .isEqualTo(ArrangementDialogEventResponse.LEAVE_ALONE);
        }

        @Test
        void resolveResponseToDoesNotCloseOnEscapeWhileTheDialogIsOnlyFadingOut() {
            // Escape belongs to whatever has the screen now, and that is no longer this dialog: a
            // second press should reach the screen behind rather than close a box already closing.
            var eventMock = mock(InputEventAPI.class);
            when(eventMock.isKeyDownEvent()).thenReturn(true);
            when(eventMock.getEventValue()).thenReturn(Keyboard.KEY_ESCAPE);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(
                    eventMock, DISMISSED, OUTSIDE_THE_BOX))
                .isEqualTo(ArrangementDialogEventResponse.LEAVE_ALONE);
        }

        @Test
        void resolveResponseToClosesOnEscape() {

            var eventMock = mock(InputEventAPI.class);
            when(eventMock.isKeyDownEvent()).thenReturn(true);
            when(eventMock.getEventValue()).thenReturn(Keyboard.KEY_ESCAPE);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(
                    eventMock, RAISED, UNREACHED_BOX_TEST))
                .isEqualTo(ArrangementDialogEventResponse.CLOSE_DIALOG);
        }

        @Test
        void resolveResponseToLeavesAnEventInsideTheBoxToTheDialogsOwnWidgets() {
            // The one thing not claimed. Claiming it would leave the dialog's buttons dead wherever
            // the game hands the plugin its events before the panel's children.
            var eventMock = mock(InputEventAPI.class);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(
                    eventMock, RAISED, INSIDE_THE_BOX))
                .isEqualTo(ArrangementDialogEventResponse.LEAVE_ALONE);
        }

        @Test
        void resolveResponseToClaimsAnEventOutsideTheBox() {

            var eventMock = mock(InputEventAPI.class);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(
                    eventMock, RAISED, OUTSIDE_THE_BOX))
                .isEqualTo(ArrangementDialogEventResponse.CLAIM);
        }

        @Test
        void resolveResponseToDoesNotCloseOnAKeyThatIsNotEscape() {
            // Escape is the only key with a meaning of its own here. Every other one falls through to
            // the box test, which is what leaves the caller free to decide that a key is never the
            // dialog's own widgets'.
            var eventMock = mock(InputEventAPI.class);
            when(eventMock.isKeyDownEvent()).thenReturn(true);
            when(eventMock.getEventValue()).thenReturn(Keyboard.KEY_M);

            assertThat(ArrangementDialogEventResponse.resolveResponseTo(
                    eventMock, RAISED, OUTSIDE_THE_BOX))
                .isEqualTo(ArrangementDialogEventResponse.CLAIM);
        }
    }
}
