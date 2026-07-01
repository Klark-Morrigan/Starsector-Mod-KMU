package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link NeutralColorChoice}: each option round-trips through its LunaLib
 * Radio label, an unknown or missing label falls back to the caller's default,
 * and only NONE reads as not drawn.
 */
final class NeutralColorChoiceTest {

    @Nested
    class FromLabel {

        @Test
        void fromLabelReturnsNeutralForItsLabel() {
            assertThat(NeutralColorChoice.fromLabel("Neutral color", NeutralColorChoice.NONE))
                    .isEqualTo(NeutralColorChoice.NEUTRAL);
        }

        @Test
        void fromLabelReturnsNoneForItsLabel() {
            assertThat(NeutralColorChoice.fromLabel("No color", NeutralColorChoice.NEUTRAL))
                    .isEqualTo(NeutralColorChoice.NONE);
        }

        @Test
        void fromLabelReturnsTheFallbackForAnUnknownLabel() {
            assertThat(NeutralColorChoice.fromLabel("Faction color", NeutralColorChoice.NEUTRAL))
                    .isEqualTo(NeutralColorChoice.NEUTRAL);
        }

        @Test
        void fromLabelReturnsTheFallbackForNull() {
            assertThat(NeutralColorChoice.fromLabel(null, NeutralColorChoice.NONE))
                    .isEqualTo(NeutralColorChoice.NONE);
        }
    }

    @Nested
    class IsDrawn {

        @Test
        void isDrawnIsTrueForNeutral() {
            assertThat(NeutralColorChoice.NEUTRAL.isDrawn()).isTrue();
        }

        @Test
        void isDrawnIsFalseForNone() {
            assertThat(NeutralColorChoice.NONE.isDrawn()).isFalse();
        }
    }
}
