package kmu.maplayers.base.sidebar.runtime;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the host-blind sidebar hit-test: what counts as being over a sidebar, the two gates a host
 * answers first, and that every host in the roster is asked. The gate order is the part worth
 * pinning - a host that is not showing can still lay a box out, so hit-testing a placement without
 * asking the gate would park the hover under a bar that is not on the screen at all.
 */
final class SidebarHostsTest {
    // A box away from the origin, so a point outside it is outside on both axes rather than by a
    // coordinate that happens to be zero.
    private static final Rectangle BODY_BOX = new Rectangle(100f, 200f, 300f, 400f);

    // On the box's right edge, the side the collapse handle rides, and clear of the body.
    private static final Rectangle NOTCH = new Rectangle(400f, 300f, 20f, 40f);

    private static final float INSIDE_BODY_X = 150f;
    private static final float INSIDE_BODY_Y = 250f;
    private static final float INSIDE_NOTCH_X = 410f;
    private static final float INSIDE_NOTCH_Y = 320f;
    private static final float OUTSIDE_X = 900f;
    private static final float OUTSIDE_Y = 900f;

    private final SidebarHost hostMock = mock(SidebarHost.class);

    @Nested
    class GetRegisteredHosts {

        @Test
        void getRegisteredHostsHoldsEveryScreenTheSidebarDrawsOn() {
            // A host missing here is a bar that excludes nothing: the hover would light a cell
            // under a panel the player is pointing at. Pinned by identity, since the hit-test must
            // reach the very singletons the render and input passes run against.
            assertThat(SidebarHosts.getRegisteredHosts())
                .containsExactly(MapSidebarHost.INSTANCE, IntelSidebarHost.INSTANCE);
        }
    }

    @Nested
    class IsPointOverAnySidebarOf {

        @Test
        void isPointOverAnySidebarOfAnswersYesInsideTheBody() {
            showSidebar(hostMock, placeSidebar(NOTCH));

            assertThat(askOneHost(INSIDE_BODY_X, INSIDE_BODY_Y))
                .isTrue();
        }

        @Test
        void isPointOverAnySidebarOfAnswersYesInsideTheNotch() {
            // The notch protrudes past the body's edge and is all that is left on screen while the
            // panel is docked, so a point on it is still over the sidebar.
            showSidebar(hostMock, placeSidebar(NOTCH));

            assertThat(askOneHost(INSIDE_NOTCH_X, INSIDE_NOTCH_Y))
                .isTrue();
        }

        @Test
        void isPointOverAnySidebarOfAnswersNoOutsideBothBoxes() {
            showSidebar(hostMock, placeSidebar(NOTCH));

            assertThat(askOneHost(OUTSIDE_X, OUTSIDE_Y))
                .isFalse();
        }

        @Test
        void isPointOverAnySidebarOfAnswersNoForAPanelWithNoNotch() {
            // A panel with nothing to collapse exposes no handle; the null is the placement's own
            // contract, and reading it as a rect would throw on a live screen.
            showSidebar(hostMock, placeSidebar(null));

            assertThat(askOneHost(INSIDE_NOTCH_X, INSIDE_NOTCH_Y))
                .isFalse();
        }

        @Test
        void isPointOverAnySidebarOfAnswersNoWhileTheHostsSidebarIsNotShowing() {
            // The gate, not the box, is what makes the question host-blind: the on-map host hangs
            // its panel from the screen corner and resolves a box wherever it is asked, so on the
            // intel screen its placement would suppress a hover under a bar not drawn there.
            when(hostMock.isOverlayShowing()).thenReturn(false);

            assertThat(askOneHost(INSIDE_BODY_X, INSIDE_BODY_Y))
                .isFalse();
            verify(hostMock, never())
                .getDrawnPlacement();
        }

        @Test
        void isPointOverAnySidebarOfLaysOutNoPanelOfItsOwnToAnswer() {
            // The cursor asks this every frame it moves, so it reads the panel the draw published rather
            // than laying one out: a hit-test that measured a whole panel would spend a layout per frame
            // per host on a question about what is already on screen.
            showSidebar(hostMock, placeSidebar(NOTCH));

            askOneHost(INSIDE_BODY_X, INSIDE_BODY_Y);

            verify(hostMock, never())
                .refreshPlacement();
        }

        @Test
        void isPointOverAnySidebarOfAnswersNoWithNothingDrawnToHit() {
            // No placement means nothing on screen this frame - the tab font could not load, or the
            // host's anchor is gone - so there is nothing to be over.
            showSidebar(hostMock, null);

            assertThat(askOneHost(INSIDE_BODY_X, INSIDE_BODY_Y))
                .isFalse();
        }

        @Test
        void isPointOverAnySidebarOfAsksEveryHostRatherThanTheFirst() {
            // The whole point of the roster: whichever screen is up, its own host is the one with a
            // box under the cursor, and the others answer for screens that are not showing.
            var quietHostMock = mock(SidebarHost.class);
            when(quietHostMock.isOverlayShowing()).thenReturn(false);
            showSidebar(hostMock, placeSidebar(NOTCH));

            assertThat(SidebarHosts.isPointOverAnySidebarOf(
                    List.of(quietHostMock, hostMock),
                    INSIDE_BODY_X,
                    INSIDE_BODY_Y))
                .isTrue();
        }

        @Test
        void isPointOverAnySidebarOfAnswersNoWhileNoHostsSidebarIsLive() {
            // Neither screen is showing a sidebar, which is every screen but the two - the hover is
            // then free to light the cell under the cursor.
            var quietHostMock = mock(SidebarHost.class);
            when(hostMock.isOverlayShowing()).thenReturn(false);
            when(quietHostMock.isOverlayShowing()).thenReturn(false);

            assertThat(SidebarHosts.isPointOverAnySidebarOf(
                    List.of(quietHostMock, hostMock),
                    INSIDE_BODY_X,
                    INSIDE_BODY_Y))
                .isFalse();
        }
    }

    // The single-host case, which is the containment rule on its own: one live host, so the answer
    // is whatever its placement covers.
    private boolean askOneHost(float uiX, float uiY) {
        return SidebarHosts.isPointOverAnySidebarOf(List.of(hostMock), uiX, uiY);
    }

    // Puts a host on screen with the given placement published by its draw, the live case every hit-test
    // needs.
    private static void showSidebar(SidebarHost sidebarHostMock, TabPanelPlacement placement) {

        when(sidebarHostMock.isOverlayShowing())
            .thenReturn(true);
        when(sidebarHostMock.getDrawnPlacement())
            .thenReturn(placement);
    }

    // A placement carrying only what the hit-test reads: the body's box and the notch. The controls,
    // header, and scroll geometry play no part in it, which is what the shared builder's shape says.
    private static TabPanelPlacement placeSidebar(Rectangle notch) {
        return SidebarPlacements.placeSidebarOverBody(BODY_BOX, notch);
    }
}
