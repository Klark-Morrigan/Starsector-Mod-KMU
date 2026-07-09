package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link DesaturationProfileChoice}: each option round-trips through its LunaLib Radio
 * label, and an unknown or missing label falls back to the caller's default.
 */
final class DesaturationProfileChoiceTest {

    @Nested
    class FromLabel {

        @Test
        void fromLabelReturnsIndependentForItsLabel() {
            assertThat(DesaturationProfileChoice.fromLabel("Independent",
                    DesaturationProfileChoice.NEUTRAL))
                    .isEqualTo(DesaturationProfileChoice.INDEPENDENT);
        }

        @Test
        void fromLabelReturnsNeutralForItsLabel() {
            assertThat(DesaturationProfileChoice.fromLabel("Neutral",
                    DesaturationProfileChoice.INDEPENDENT))
                    .isEqualTo(DesaturationProfileChoice.NEUTRAL);
        }

        @Test
        void fromLabelReturnsTheFallbackForAnUnknownLabel() {
            assertThat(DesaturationProfileChoice.fromLabel("Desaturated",
                    DesaturationProfileChoice.INDEPENDENT))
                    .isEqualTo(DesaturationProfileChoice.INDEPENDENT);
        }

        @Test
        void fromLabelReturnsTheFallbackForNull() {
            assertThat(DesaturationProfileChoice.fromLabel(null, DesaturationProfileChoice.NEUTRAL))
                    .isEqualTo(DesaturationProfileChoice.NEUTRAL);
        }
    }
}
