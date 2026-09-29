package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.input.InputEventAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the dialog's surface does with a screen: the frame hooks the engine drives once a panel
 * is standing, and the panel coming down with the map under it. Whether there is still a map is
 * {@code ShownMapTab}'s read, which fails closed and is pinned there.
 *
 * <p>The hooks are reached through {@link ArrangementDialogScreenScope}, which stands in the three
 * static reaches a panel is built over. They are worth reaching because what they hold is not glue:
 * a frame that takes the panel down, a frame that must not take it down twice, the one write that
 * fades every part of the box together, and the claim that makes the dialog modal. Each of them
 * fails only in play, and only on the frame it happens.
 */
final class ArrangementDialogPanelTest {

    // Longer than the fade's own fall, so one advance settles a dismissed dialog all the way down.
    private static final float ADVANCE_PAST_FADE = 5f;

    // Short of it, so the box is still on its way somewhere.
    private static final float ADVANCE_WITHIN_FADE = 0.05f;

    // The alpha a frame hands a panel standing at full - whatever the engine multiplies in is the
    // engine's, and what matters here is that the number arrives unaltered.
    private static final float FULLY_PAINTED = 1f;

    @Nested
    class RaisePanel {

        @Test
        void standsAPanelInTheCoreUiAndTakesTheScreen() {

            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var panel = buildPanel();

                assertThat(panel.raisePanel())
                    .isTrue();
                assertThat(panel.isPanelRaised())
                    .isTrue();
                assertThat(screenScope.countBuiltPanels())
                    .isEqualTo(1);
            }
        }

        @Test
        void leavesTheScreenAloneWhereTheCoreUiCannotBeReached() {
            // Half-opening is the state this rules out: a panel built and hung from nothing would be
            // a dialog the player cannot see and cannot dismiss, with the screen already claimed.
            try (var screenScope = ArrangementDialogScreenScope.openOverAnUnreachableCoreUi()) {

                var panel = buildPanel();

                assertThat(panel.raisePanel())
                    .isFalse();
                assertThat(panel.isPanelRaised())
                    .isFalse();
            }
        }

        @Test
        void reusesTheStandingPanelWhenReopenedDuringTheFall() {
            // The panel outlives the press that dismisses it, so a reopen while the box is still
            // falling has one to take back. Standing a second over the first would leave the first
            // on screen with nothing left holding it.
            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var panel = buildPanel();

                panel.raisePanel();
                panel.dismissPanel();
                panel.raisePanel();

                assertThat(screenScope.countBuiltPanels())
                    .isEqualTo(1);
            }
        }
    }

    @Nested
    class ResolvePanelPresence {

        @Test
        void reportsTheScreenClaimAndTheFadeTogether() {

            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var panel = buildPanel();

                panel.raisePanel();

                assertThat(panel.resolvePanelPresence().isRaised())
                    .isTrue();
                assertThat(panel.resolvePanelPresence().fadeFraction())
                    .isZero();
            }
        }
    }

    @Nested
    class Advance {

        @Test
        void writesTheFadeOntoThePanelAsItsOpacity() {
            // The one write that fades the box, its controls and the fills beneath them together. A
            // frame that skipped it would leave the box at whatever it was painted at last.
            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var panel = buildPanel();

                panel.raisePanel();
                screenScope.resolvePanelPlugin().advance(ADVANCE_WITHIN_FADE);

                // Twice: once as the panel was stood up at nothing, once on the frame advanced.
                verify(screenScope.resolvePanel(), times(2))
                    .setOpacity(anyFloat());
            }
        }

        @Test
        void takesThePanelDownAndReportsTheLossWhenTheMapHasGone() {
            // The panel hangs from the core UI, which outlives the screen the dialog was opened on,
            // so leaving that screen has to be noticed rather than waited for.
            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var lostReports = new int[1];
                var panel = new ArrangementDialogPanel(() -> { }, () -> lostReports[0]++);

                panel.raisePanel();
                screenScope.settleMapShowing(false);
                screenScope.resolvePanelPlugin().advance(ADVANCE_WITHIN_FADE);

                assertThat(lostReports[0])
                    .isEqualTo(1);
            }
        }

        @Test
        void doesNothingFurtherOnceThePanelHasBeenLetGo() {
            // A detach that could not reach the core UI leaves a panel still advanced by whoever
            // holds it and no longer ours to paint, so every write below the guard would be through
            // a panel this no longer has.
            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var lostReports = new int[1];
                var panel = new ArrangementDialogPanel(() -> { }, () -> lostReports[0]++);

                panel.raisePanel();
                screenScope.settleMapShowing(false);
                screenScope.resolvePanelPlugin().advance(ADVANCE_WITHIN_FADE);
                screenScope.resolvePanelPlugin().advance(ADVANCE_WITHIN_FADE);

                assertThat(lostReports[0])
                    .isEqualTo(1);
            }
        }

        @Test
        void takesThePanelDownOnceADismissedFadeHasSettled() {

            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var panel = buildPanel();

                panel.raisePanel();
                screenScope.resolvePanelPlugin().advance(ADVANCE_PAST_FADE);
                panel.dismissPanel();
                screenScope.resolvePanelPlugin().advance(ADVANCE_PAST_FADE);

                // Reopening builds a second panel, which is what says the first was taken down
                // rather than merely faded to nothing.
                panel.raisePanel();

                assertThat(screenScope.countBuiltPanels())
                    .isEqualTo(2);
            }
        }
    }

    @Nested
    class RenderBelow {

        @Test
        void paintsTheBodysFillsUnderEveryWidget() {
            // The dim that stands the screen down and the box's own surface: the game publishes a
            // rectangle that strokes and none that fills, so nothing else paints either.
            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var panel = buildPanel();
                var bodyMock = mock(MapLayerArrangementDialogBody.class);

                panel.raisePanel();
                panel.showBody(dialogPanel -> bodyMock);
                screenScope.resolvePanelPlugin().renderBelow(FULLY_PAINTED);

                verify(bodyMock).renderFills(FULLY_PAINTED);
            }
        }

        @Test
        void paintsNothingBeforeABodyIsShown() {
            // The frame between the panel being stood up and its widgets being composed, which the
            // engine renders like any other.
            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var panel = buildPanel();

                panel.raisePanel();

                assertThatCode(() -> screenScope.resolvePanelPlugin().renderBelow(FULLY_PAINTED))
                    .doesNotThrowAnyException();
            }
        }
    }

    @Nested
    class ProcessInput {

        @Test
        void claimsAnEventNothingUnderTheDialogShouldSee() {
            // The dialog's modality is this claim: a panel added to the core UI dims nothing and
            // stops nothing, so an unclaimed event reaches the map under the box.
            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var panel = buildPanel();
                var eventMock = buildUnconsumedEvent();

                panel.raisePanel();
                screenScope.resolvePanelPlugin().processInput(List.of(eventMock));

                verify(eventMock).consume();
            }
        }

        @Test
        void reportsThePressThatDismissesTheDialog() {

            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var closeRequests = new int[1];
                var panel = new ArrangementDialogPanel(() -> closeRequests[0]++, () -> { });
                var eventMock = buildUnconsumedEvent();

                when(eventMock.isKeyDownEvent()).thenReturn(true);
                when(eventMock.getEventValue()).thenReturn(Keyboard.KEY_ESCAPE);

                panel.raisePanel();
                screenScope.resolvePanelPlugin().processInput(List.of(eventMock));

                assertThat(closeRequests[0])
                    .isEqualTo(1);
            }
        }

        @Test
        void claimsNothingWhileTheDialogIsOnlyFading() {
            // The press that dismisses the dialog is the moment the screen is the player's again, so
            // a claim held for the fall would eat the click that follows it.
            try (var screenScope = ArrangementDialogScreenScope.openOverAReachableCoreUi()) {

                var panel = buildPanel();
                var eventMock = buildUnconsumedEvent();

                panel.raisePanel();
                panel.dismissPanel();
                screenScope.resolvePanelPlugin().processInput(List.of(eventMock));

                verify(eventMock, never()).consume();
            }
        }
    }

    private static ArrangementDialogPanel buildPanel() {
        return new ArrangementDialogPanel(() -> { }, () -> { });
    }

    // An event nothing has taken yet, which is the only kind the claim rule reads past.
    private static InputEventAPI buildUnconsumedEvent() {

        var eventMock = mock(InputEventAPI.class);

        when(eventMock.isConsumed()).thenReturn(false);

        return eventMock;
    }
}
