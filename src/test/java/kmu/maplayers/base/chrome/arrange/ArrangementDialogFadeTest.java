package kmu.maplayers.base.chrome.arrange;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins that raised and painted part company, which is the whole of what this clock is for.
 *
 * <p>Input reads the first and must move on the press: a dialog that stayed raised for its fall would eat
 * the click that follows the press, and one that came up only once its rise had run would let a click
 * through to the map on the frame it opened. The paint reads the second and must not move on the press:
 * a box that vanished on the frame it was dismissed is the cut the fade exists to remove.
 *
 * <p>And that the pace is the game's own prompts'. Two dialogs over one screen at two paces is the
 * difference visible on the frame it happens, so the durations are pinned as the numbers they are rather
 * than as whatever the constant happens to hold.
 */
final class ArrangementDialogFadeTest {

    private static final float TOLERANCE = 0.0001f;

    // The pace the game's own prompts are shown at: the way in and the shorter way out.
    private static final float PROMPT_RISE_SECONDS = 0.3f;
    private static final float PROMPT_FALL_SECONDS = 0.2f;

    // Part of a fall, so a reopen mid-way has somewhere to come up from.
    private static final float HALF_A_FALL_SECONDS = 0.1f;

    // One frame's worth of time, short enough that a rise begun on it has not finished.
    private static final float A_FRAME_SECONDS = 0.02f;

    @Nested
    class IsDialogRaised {

        @Test
        void isDialogRaisedIsFalseAtRest() {

            assertThat(new ArrangementDialogFade().isDialogRaised())
                .isFalse();
        }

        @Test
        void isDialogRaisedIsTrueOnTheFrameTheDialogIsRaisedBeforeItsRiseHasBegun() {
            // Input goes the instant the dialog appears, while the paint is still at nothing.
            var fade = new ArrangementDialogFade();

            fade.raiseDialog();

            assertThat(fade.isDialogRaised())
                .isTrue();
        }

        @Test
        void isDialogRaisedIsFalseOnThePressWhileThePaintStillStands() {
            // The split itself: dismissed for input at once, with the box still fully painted.
            var fade = createFullyRaisedFade();

            fade.dismissDialog();

            assertThat(fade.isDialogRaised())
                .isFalse();
            assertThat(fade.resolveFadeFraction())
                .isCloseTo(1f, within(TOLERANCE));
        }
    }

    @Nested
    class AdvanceFade {

        @Test
        void advanceFadeReachesFullyUpAfterThePromptsOwnRise() {

            var fade = new ArrangementDialogFade();
            fade.raiseDialog();

            fade.advanceFade(PROMPT_RISE_SECONDS);

            assertThat(fade.resolveFadeFraction())
                .isCloseTo(1f, within(TOLERANCE));
        }

        @Test
        void advanceFadeIsStillRisingAFrameIn() {
            // The rise is a travel rather than a switch: one frame in, the box is part way and not whole.
            var fade = new ArrangementDialogFade();
            fade.raiseDialog();

            fade.advanceFade(A_FRAME_SECONDS);

            assertThat(fade.resolveFadeFraction())
                .isGreaterThan(0f)
                .isLessThan(1f);
        }

        @Test
        void advanceFadeReachesDownAfterThePromptsOwnFall() {

            var fade = createFullyRaisedFade();
            fade.dismissDialog();

            fade.advanceFade(PROMPT_FALL_SECONDS);

            assertThat(fade.resolveFadeFraction())
                .isCloseTo(0f, within(TOLERANCE));
        }

        @Test
        void advanceFadeComesUpFromWhereTheFallStoodOnAReopen() {
            // Retargeted rather than replayed: a dialog reopened mid-fall neither drops to nothing and
            // starts over nor jumps to whole, it turns round where it stands.
            var fade = createFullyRaisedFade();
            fade.dismissDialog();
            fade.advanceFade(HALF_A_FALL_SECONDS);
            var whereTheFallStood = fade.resolveFadeFraction();

            fade.raiseDialog();
            fade.advanceFade(A_FRAME_SECONDS);

            assertThat(fade.resolveFadeFraction())
                .isGreaterThan(whereTheFallStood)
                .isLessThan(1f);
        }
    }

    @Nested
    class ResolveFadeFraction {

        @Test
        void resolveFadeFractionIsNothingAtRest() {

            assertThat(new ArrangementDialogFade().resolveFadeFraction())
                .isCloseTo(0f, within(TOLERANCE));
        }

        @Test
        void resolveFadeFractionIsNothingOnTheFrameTheDialogIsRaised() {
            // A first open comes up from nothing: raising moves input, not the paint.
            var fade = new ArrangementDialogFade();

            fade.raiseDialog();

            assertThat(fade.resolveFadeFraction())
                .isCloseTo(0f, within(TOLERANCE));
        }
    }

    @Nested
    class IsSettledDown {

        @Test
        void isSettledDownIsTrueAtRest() {

            assertThat(new ArrangementDialogFade().isSettledDown())
                .isTrue();
        }

        @Test
        void isSettledDownIsFalseForARaisedDialogWhosePaintHasNotBegun() {
            // Down and at nothing is not settled while the dialog is up: the panel a caller would take
            // off is the one about to rise.
            var fade = new ArrangementDialogFade();

            fade.raiseDialog();

            assertThat(fade.isSettledDown())
                .isFalse();
        }

        @Test
        void isSettledDownIsFalseWhileTheFallIsStillRunning() {

            var fade = createFullyRaisedFade();
            fade.dismissDialog();
            fade.advanceFade(HALF_A_FALL_SECONDS);

            assertThat(fade.isSettledDown())
                .isFalse();
        }

        @Test
        void isSettledDownIsTrueOnceTheFallHasRunOut() {
            // Which is when the panel comes off the screen: at the end of the fall, not at the press.
            var fade = createFullyRaisedFade();
            fade.dismissDialog();
            fade.advanceFade(PROMPT_FALL_SECONDS);

            assertThat(fade.isSettledDown())
                .isTrue();
        }
    }

    @Nested
    class DropFade {

        @Test
        void dropFadeTakesARaisedDialogDownAndItsPaintWithItInOneStep() {

            var fade = createFullyRaisedFade();

            fade.dropFade();

            assertThat(fade.isDialogRaised())
                .isFalse();
            assertThat(fade.resolveFadeFraction())
                .isCloseTo(0f, within(TOLERANCE));
            assertThat(fade.isSettledDown())
                .isTrue();
        }
    }

    // A dialog up and fully painted, which is where every dismissal starts from.
    private static ArrangementDialogFade createFullyRaisedFade() {

        var fade = new ArrangementDialogFade();
        fade.raiseDialog();
        fade.advanceFade(PROMPT_RISE_SECONDS);

        return fade;
    }
}
