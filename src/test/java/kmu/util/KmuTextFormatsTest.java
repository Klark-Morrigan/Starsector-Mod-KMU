package kmu.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static kmu.util.KmuTextFormats.joinWithParenthetical;
import static org.assertj.core.api.Assertions.assertThat;

class KmuTextFormatsTest {
    // --- joinWithParenthetical ---

    @Test
    void joinWithParentheticalReturnsBothWhenPresent() {
        assertThat(joinWithParenthetical(Optional.of("main"), Optional.of("note")))
                .contains("main (note)");
    }

    @Test
    void joinWithParentheticalReturnsMainWhenParentheticalAbsent() {
        assertThat(joinWithParenthetical(Optional.of("main"), Optional.empty()))
                .contains("main");
    }

    @Test
    void joinWithParentheticalReturnsParentheticalWhenMainAbsent() {
        assertThat(joinWithParenthetical(Optional.empty(), Optional.of("note")))
                .contains("note");
    }

    @Test
    void joinWithParentheticalReturnsEmptyWhenBothAbsent() {
        assertThat(joinWithParenthetical(Optional.empty(), Optional.empty()))
                .isEmpty();
    }
}
