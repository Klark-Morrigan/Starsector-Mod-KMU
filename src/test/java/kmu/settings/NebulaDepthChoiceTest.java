package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link NebulaDepthChoice}'s LunaLib Radio labels - the strings the CSV's options must spell,
 * since a Radio stores its selected option's label and the read matches on it.
 */
final class NebulaDepthChoiceTest {

    @Nested
    class GetLabel {

        @Test
        void getLabelReturnsTheLunaLibOptionLabel() {
            
            assertThat(NebulaDepthChoice.BELOW.getLabel())
                .isEqualTo("Below");
            assertThat(NebulaDepthChoice.ABOVE.getLabel())
                .isEqualTo("Above");
        }
    }
}
