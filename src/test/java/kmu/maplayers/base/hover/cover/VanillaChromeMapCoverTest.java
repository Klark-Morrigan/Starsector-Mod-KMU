package kmu.maplayers.base.hover.cover;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.map.probes.MapSurfaceArea;
import kmlib.testfixtures.starsector.ui.input.CursorPositionFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two halves of this cover that a live game is the worst place to see fail: that a cursor
 * off the visible map is covered, and that an unreadable surface fails open rather than silencing
 * the map.
 *
 * <p>The failing-open case is the one worth having a test for at all. It is reached by a build whose
 * map tab this rule no longer fits, which is by definition a build nobody has run this against, and
 * the wrong answer there is silence - a hover that stops working everywhere, with nothing on screen
 * to say why.
 */
final class VanillaChromeMapCoverTest {

    private static final Rectangle CHROME_BOX = new Rectangle(0f, 0f, 400f, 40f);
    private static final Rectangle SURFACE_BOX = new Rectangle(0f, 0f, 400f, 300f);

    private static final float POINT_ON_CHROME_X = 200f;
    private static final float POINT_ON_CHROME_Y = 20f;
    private static final float POINT_ON_MAP_X = 200f;
    private static final float POINT_ON_MAP_Y = 200f;
    private static final float POINT_OUTSIDE_SURFACE_X = 900f;
    private static final float POINT_OUTSIDE_SURFACE_Y = 900f;

    @Nested
    class IsCoveringCursor {

        @Test
        void isCoveringCursorAnswersUncoveredOverTheVisibleMap() {
            assertThat(buildCoverWithCursorAt(POINT_ON_MAP_X, POINT_ON_MAP_Y).isCoveringCursor())
                .isFalse();
        }

        @Test
        void isCoveringCursorAnswersCoveredOverChromeDrawnOnTheSurface() {
            // Inside the surface's own box and inside the control bar drawn across it, which is the
            // case a complement of the surface alone cannot catch - the intel visor's shape.
            assertThat(buildCoverWithCursorAt(POINT_ON_CHROME_X, POINT_ON_CHROME_Y).isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersCoveredOutsideTheSurfaceAltogether() {
            // The tab strip beside an inset surface - the M map's shape.
            assertThat(
                    buildCoverWithCursorAt(POINT_OUTSIDE_SURFACE_X, POINT_OUTSIDE_SURFACE_Y)
                        .isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileNoSurfaceCanBeRead() {
            // Fails open, per the role's rule: an unreadable tree, or no map tab up at all, restores
            // the un-suppressed behaviour rather than covering the map everywhere.
            var cursorFake = new CursorPositionFake();
            cursorFake.restCursorAt(POINT_ON_MAP_X, POINT_ON_MAP_Y);

            assertThat(new VanillaChromeMapCover(() -> null, cursorFake).isCoveringCursor())
                .isFalse();
        }
    }

    private static VanillaChromeMapCover buildCoverWithCursorAt(float uiX, float uiY) {

        var cursorFake = new CursorPositionFake();
        cursorFake.restCursorAt(uiX, uiY);

        return new VanillaChromeMapCover(
            () -> new MapSurfaceArea(SURFACE_BOX, List.of(CHROME_BOX)),
            cursorFake);
    }
}
