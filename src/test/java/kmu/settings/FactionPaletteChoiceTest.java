package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link FactionPaletteChoice}'s LunaLib Radio labels - the strings the CSV's options must
 * spell, since a Radio stores its selected option's label and the read matches on it.
 */
final class FactionPaletteChoiceTest {

    @Nested
    class GetLabel {

        @Test
        void getLabelReturnsTheLunaLibOptionLabel() {

            assertThat(FactionPaletteChoice.PRIMARY.getLabel())
                .isEqualTo("Primary faction color");
            assertThat(FactionPaletteChoice.SECONDARY.getLabel())
                .isEqualTo("Secondary faction color");
            assertThat(FactionPaletteChoice.NONE.getLabel())
                .isEqualTo("No color");
        }
    }
}
