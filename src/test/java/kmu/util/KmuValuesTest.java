package kmu.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.util.KmuValues.convertToOptionalText;
import static kmu.util.KmuValues.getTextOrEmpty;
import static kmu.util.KmuValues.hasText;
import static kmu.util.KmuValues.normaliseText;
import static kmu.util.KmuValues.requireNonBlankText;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmuValuesTest {

    @Nested
    class NormaliseText {

        @Test
        void returnsNullForNull() {
            assertThat(normaliseText(null)).isNull();
        }

        @Test
        void returnsNullForBlank() {
            assertThat(normaliseText("   ")).isNull();
        }

        @Test
        void trimsWhitespace() {
            assertThat(normaliseText("  hello  ")).isEqualTo("hello");
        }
    }

    @Nested
    class ConvertToOptionalText {

        @Test
        void returnsEmptyForBlank() {
            assertThat(convertToOptionalText("  ")).isEmpty();
        }

        @Test
        void returnsPresentForNonBlank() {
            assertThat(convertToOptionalText("hello")).contains("hello");
        }
    }

    @Nested
    class GetTextOrEmpty {

        @Test
        void returnsEmptyStringForNull() {
            assertThat(getTextOrEmpty(null)).isEqualTo("");
        }

        @Test
        void returnsEmptyStringForBlank() {
            assertThat(getTextOrEmpty("  ")).isEqualTo("");
        }

        @Test
        void returnsTrimmedText() {
            assertThat(getTextOrEmpty("  hello  ")).isEqualTo("hello");
        }
    }

    @Nested
    class HasText {

        @Test
        void returnsFalseForNull() {
            assertThat(hasText(null)).isFalse();
        }

        @Test
        void returnsFalseForBlank() {
            assertThat(hasText("  ")).isFalse();
        }

        @Test
        void returnsTrueForNonBlank() {
            assertThat(hasText("hello")).isTrue();
        }
    }

    @Nested
    class RequireNonBlankText {

        @Test
        void throwsForNull() {
            assertThatThrownBy(() -> requireNonBlankText(null, "field"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("field");
        }

        @Test
        void throwsForBlank() {
            assertThatThrownBy(() -> requireNonBlankText("  ", "field"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void returnsValueWhenPresent() {
            assertThat(requireNonBlankText("hello", "field")).isEqualTo("hello");
        }
    }
}
