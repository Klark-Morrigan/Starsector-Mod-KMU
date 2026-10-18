package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.widgets.tabs.style.TabChrome;
import kmlib.starsector.ui.widgets.tabs.style.TabStyle;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ChromeConvention}: that a convention stands at the band height it is given and keeps its own
 * sharpness. What each chrome's convention holds is pinned through the tab styles {@code SidebarStylesTests}
 * builds.
 */
final class ChromeConventionTests {

    // Two floats that could cross: the band height the host hands in and the sharpness the convention
    // holds. Distinct values, so a crossing between them fails.
    private static final float HEADER_BAND_HEIGHT = 19f;
    private static final float PIXEL_FACE_SHARPNESS = 0.25f;

    @Nested
    class ComposeTabStyle {

        @Test
        void standsTheConventionAtTheGivenBandHeight() {

            assertThat(composeStripTabStyle().headerBandHeight())
                .isEqualTo(19f);
        }

        @Test
        void keepsTheConventionsOwnSharpness() {

            assertThat(composeStripTabStyle().pixelFaceSharpness())
                .isEqualTo(0.25f);
        }

        @Test
        void wearsTheConventionsChrome() {

            assertThat(composeStripTabStyle().chrome())
                .isEqualTo(TabChrome.STRIP);
        }

        // The parts between the chrome and the sharpness are left null. Each is a type of its own, so a
        // crossing among them does not compile, and building real ones would drag in the live reads they
        // are composed from.
        private TabStyle composeStripTabStyle() {

            return new ChromeConvention(TabChrome.STRIP, null, null, null, null, null, PIXEL_FACE_SHARPNESS)
                .composeTabStyle(HEADER_BAND_HEIGHT);
        }
    }
}
