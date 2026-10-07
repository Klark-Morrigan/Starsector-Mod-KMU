package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link OwnerPaletteChoice}'s LunaLib Radio labels - the strings the CSV's options must
 * spell, since a Radio stores its selected option's label and the read matches on it.
 */
final class OwnerPaletteChoiceTests {

    @Nested
    class GetLabel {

        @Test
        void returnsTheLunaLibOptionLabel() {

            assertThat(OwnerPaletteChoice.PRIMARY.getLabel())
                .isEqualTo("Primary faction color");
            assertThat(OwnerPaletteChoice.SECONDARY.getLabel())
                .isEqualTo("Secondary faction color");
            assertThat(OwnerPaletteChoice.NONE.getLabel())
                .isEqualTo("No color");
        }
    }
}
