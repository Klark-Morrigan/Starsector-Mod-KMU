package kmu.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static kmu.util.KmuTextFormats.joinWithParenthetical;

import static org.assertj.core.api.Assertions.assertThat;

class KmuTextFormatsTest {

    @Nested
    class JoinWithParenthetical {

        @Test
        void returnsBothWhenPresent() {
            assertThat(joinWithParenthetical(Optional.of("main"), Optional.of("note")))
                    .contains("main (note)");
        }

        @Test
        void returnsMainWhenParentheticalAbsent() {
            assertThat(joinWithParenthetical(Optional.of("main"), Optional.empty()))
                    .contains("main");
        }

        @Test
        void returnsParentheticalWhenMainAbsent() {
            assertThat(joinWithParenthetical(Optional.empty(), Optional.of("note")))
                    .contains("note");
        }

        @Test
        void returnsEmptyWhenBothAbsent() {
            assertThat(joinWithParenthetical(Optional.empty(), Optional.empty()))
                    .isEmpty();
        }
    }
}
