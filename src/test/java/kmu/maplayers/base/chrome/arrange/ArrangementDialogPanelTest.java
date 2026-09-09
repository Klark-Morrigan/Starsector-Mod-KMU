package kmu.maplayers.base.chrome.arrange;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one rule of the panel's that does not need a running game: whether there is still a map
 * under the dialog to have a bar to arrange.
 *
 * <p>It is the only cover-shaped reading in this mod that fails <b>closed</b>, and the direction is
 * what has to be pinned. Everything under {@code base/hover/cover} answers "not covering" on a
 * reading it cannot take, because a read added to refine a hover must not switch one off; this
 * answers "no map" on the same failure, because a dialog that cannot find the screen it was opened
 * over should come down rather than stand on it. Written the other way round it would strand the box
 * on screen with the reach raising every frame.
 */
final class ArrangementDialogPanelTest {

    @Nested
    class IsMapShowing {

        @Test
        void isMapShowingAnswersShowingWhileAMapTabIsOnScreen() {

            assertThat(ArrangementDialogPanel.isMapShowing(Object::new))
                .isTrue();
        }

        @Test
        void isMapShowingAnswersNoMapWhereNoTabIsOnScreen() {
            // The ordinary way out: the player left the map, so the reach answers rather than raises.
            assertThat(ArrangementDialogPanel.isMapShowing(() -> null))
                .isFalse();
        }

        @Test
        void isMapShowingFailsClosedWhereTheWidgetTreeCannotBeWalked() {
            // The reach raises rather than answering, which is a screen nothing can be placed on -
            // so the dialog comes down instead of standing over one it can no longer see.
            assertThat(ArrangementDialogPanel.isMapShowing(() -> {
                throw new IllegalStateException("the widget tree could not be walked");
            }))
                .isFalse();
        }
    }
}
