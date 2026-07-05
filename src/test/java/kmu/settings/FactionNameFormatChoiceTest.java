package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link FactionNameFormatChoice}: each option round-trips through its LunaLib
 * Radio label, and an unknown or missing label falls back to the caller's default
 * (a stale config or a read before LunaLib loads must not throw or mislabel).
 */
final class FactionNameFormatChoiceTest {

    @Nested
    class FromLabel {

        @Test
        void fromLabelReturnsFullForItsLabel() {
            assertThat(FactionNameFormatChoice.fromLabel("Full names",
                    FactionNameFormatChoice.SHORT)).isEqualTo(FactionNameFormatChoice.FULL);
        }

        @Test
        void fromLabelReturnsShortForItsLabel() {
            assertThat(FactionNameFormatChoice.fromLabel("Short names",
                    FactionNameFormatChoice.FULL)).isEqualTo(FactionNameFormatChoice.SHORT);
        }

        @Test
        void fromLabelReturnsTheFallbackForAnUnknownLabel() {
            // A label from an old config (or a typo) matches nothing, so the caller's
            // default stands rather than throwing.
            assertThat(FactionNameFormatChoice.fromLabel("Medium names",
                    FactionNameFormatChoice.FULL)).isEqualTo(FactionNameFormatChoice.FULL);
        }

        @Test
        void fromLabelReturnsTheFallbackForNull() {
            // getString returns null before LunaLib has loaded the field; the
            // fallback covers that read.
            assertThat(FactionNameFormatChoice.fromLabel(null, FactionNameFormatChoice.FULL))
                    .isEqualTo(FactionNameFormatChoice.FULL);
        }
    }

    @Nested
    class GetLabel {

        @Test
        void getLabelReturnsTheLunaLibOptionLabel() {
            assertThat(FactionNameFormatChoice.FULL.getLabel()).isEqualTo("Full names");
            assertThat(FactionNameFormatChoice.SHORT.getLabel()).isEqualTo("Short names");
        }
    }
}
