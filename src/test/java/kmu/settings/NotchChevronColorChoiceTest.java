package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link NotchChevronColorChoice}: each option round-trips through its LunaLib Radio label,
 * and an unknown or missing label falls back to the caller's default.
 */
final class NotchChevronColorChoiceTest {

    @Nested
    class FromLabel {

        @Test
        void fromLabelReturnsGoldForItsLabel() {
            assertThat(NotchChevronColorChoice.fromLabel("Gold", NotchChevronColorChoice.PANEL_ACCENT))
                    .isEqualTo(NotchChevronColorChoice.GOLD);
        }

        @Test
        void fromLabelReturnsPanelAccentForItsLabel() {
            assertThat(NotchChevronColorChoice.fromLabel("Panel accent", NotchChevronColorChoice.GOLD))
                    .isEqualTo(NotchChevronColorChoice.PANEL_ACCENT);
        }

        @Test
        void fromLabelReturnsTheFallbackForAnUnknownLabel() {
            // A stale label from an older config must not silently repaint the handle.
            assertThat(NotchChevronColorChoice.fromLabel("Highlight gold", NotchChevronColorChoice.GOLD))
                    .isEqualTo(NotchChevronColorChoice.GOLD);
        }

        @Test
        void fromLabelReturnsTheFallbackForNull() {
            assertThat(NotchChevronColorChoice.fromLabel(null, NotchChevronColorChoice.PANEL_ACCENT))
                    .isEqualTo(NotchChevronColorChoice.PANEL_ACCENT);
        }
    }
}
