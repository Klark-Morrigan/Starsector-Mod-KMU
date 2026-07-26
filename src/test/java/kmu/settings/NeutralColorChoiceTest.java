package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link NeutralColorChoice}: its LunaLib Radio labels - the strings the CSV's options must
 * spell, since a Radio stores its selected option's label and the read matches on it - and that
 * only NONE reads as not drawn.
 */
final class NeutralColorChoiceTest {

    @Nested
    class GetLabel {

        @Test
        void getLabelReturnsTheLunaLibOptionLabel() {
            assertThat(NeutralColorChoice.NEUTRAL.getLabel()).isEqualTo("Neutral color");
            assertThat(NeutralColorChoice.NONE.getLabel()).isEqualTo("No color");
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
