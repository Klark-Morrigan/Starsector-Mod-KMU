package kmu.maplayers.base.hover.cover;

import kmlib.testfixtures.starsector.ui.input.CursorPositionFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that this cover puts the cursor's own position to the sidebar roster rather than some other
 * point, which is the one thing a cover composed of two ports can get wrong while looking right.
 *
 * <p>The verdict itself is the roster's and is pinned there. What is asserted here is the wiring:
 * the pointer the roster was asked about, and that the answer is passed through rather than
 * inverted - a cover that reported the map covered wherever the sidebar was not would silence every
 * hover on the map and would still pass a test that only tried one point.
 */
final class SidebarMapCoverTest {

    private static final float CURSOR_UI_X = 120f;
    private static final float CURSOR_UI_Y = 340f;

    @Nested
    class IsCoveringCursor {

        @Test
        void isCoveringCursorAnswersCoveredWhileASidebarIsUnderThePointer() {
            assertThat(buildCoverOverSidebarAt(true).isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileNoSidebarIsUnderThePointer() {
            // The ordinary case: a panel drawn somewhere on screen covers only where it is drawn,
            // so the rest of the map hovers as it did before this cover existed.
            assertThat(buildCoverOverSidebarAt(false).isCoveringCursor())
                .isFalse();
        }

        @Test
        void isCoveringCursorAsksTheRosterAboutTheCursorsOwnPosition() {

            var cursorFake = new CursorPositionFake();
            
            cursorFake.restCursorAt(CURSOR_UI_X, CURSOR_UI_Y);

            var askedPoint = new float[2];
            var cover = new SidebarMapCover(
                (uiX, uiY) -> {
                    askedPoint[0] = uiX;
                    askedPoint[1] = uiY;
                    return false;
                },
                cursorFake);

            cover.isCoveringCursor();

            // Both axes, and in this order: the two are the same type, so a transposition compiles
            // and would only show up over a panel that is not square.
            assertThat(askedPoint)
                .containsExactly(CURSOR_UI_X, CURSOR_UI_Y);
        }
    }

    private static SidebarMapCover buildCoverOverSidebarAt(boolean isAnySidebarOverPoint) {

        var cursorFake = new CursorPositionFake();
        cursorFake.restCursorAt(CURSOR_UI_X, CURSOR_UI_Y);

        return new SidebarMapCover((uiX, uiY) -> isAnySidebarOverPoint, cursorFake);
    }
}
