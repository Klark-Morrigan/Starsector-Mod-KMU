package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link FactionPaletteChoice}: each option round-trips through its LunaLib
 * Radio label, and an unknown or missing label falls back to the caller's default
 * (a stale config or a read before LunaLib loads must not throw or mispaint).
 */
final class FactionPaletteChoiceTest {

    @Nested
    class FromLabel {

        @Test
        void fromLabelReturnsPrimaryForItsLabel() {
            assertThat(FactionPaletteChoice.fromLabel("Primary faction color",
                    FactionPaletteChoice.SECONDARY)).isEqualTo(FactionPaletteChoice.PRIMARY);
        }

        @Test
        void fromLabelReturnsSecondaryForItsLabel() {
            assertThat(FactionPaletteChoice.fromLabel("Secondary faction color",
                    FactionPaletteChoice.PRIMARY)).isEqualTo(FactionPaletteChoice.SECONDARY);
        }

        @Test
        void fromLabelReturnsNoneForItsLabel() {
            assertThat(FactionPaletteChoice.fromLabel("No color",
                    FactionPaletteChoice.PRIMARY)).isEqualTo(FactionPaletteChoice.NONE);
        }

        @Test
        void fromLabelReturnsTheFallbackForAnUnknownLabel() {
            // A label from an old config (or a typo) matches nothing, so the caller's
            // default stands rather than throwing.
            assertThat(FactionPaletteChoice.fromLabel("Tertiary faction color",
                    FactionPaletteChoice.SECONDARY)).isEqualTo(FactionPaletteChoice.SECONDARY);
        }

        @Test
        void fromLabelReturnsTheFallbackForNull() {
            // getString returns null before LunaLib has loaded the field; the
            // fallback covers that read.
            assertThat(FactionPaletteChoice.fromLabel(null, FactionPaletteChoice.PRIMARY))
                    .isEqualTo(FactionPaletteChoice.PRIMARY);
        }
    }

    @Nested
    class GetLabel {

        @Test
        void getLabelReturnsTheLunaLibOptionLabel() {
            assertThat(FactionPaletteChoice.PRIMARY.getLabel()).isEqualTo("Primary faction color");
            assertThat(FactionPaletteChoice.SECONDARY.getLabel())
                    .isEqualTo("Secondary faction color");
            assertThat(FactionPaletteChoice.NONE.getLabel()).isEqualTo("No color");
        }
    }
}
