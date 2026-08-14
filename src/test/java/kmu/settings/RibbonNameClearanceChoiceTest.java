package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link RibbonNameClearanceChoice}'s LunaLib Radio labels - the strings the CSV's options
 * must spell, since a Radio stores its selected option's label and the read matches on it.
 */
final class RibbonNameClearanceChoiceTest {

    @Nested
    class GetLabel {

        @Test
        void getLabelReturnsTheLunaLibOptionLabel() {
            assertThat(RibbonNameClearanceChoice.FITTED_BOX.getLabel())
                .isEqualTo("The name's fitted box");
            assertThat(RibbonNameClearanceChoice.WORDS.getLabel())
                .isEqualTo("The words themselves");
        }
    }
}
