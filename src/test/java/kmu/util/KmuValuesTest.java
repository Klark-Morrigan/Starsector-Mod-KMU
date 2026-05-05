package kmu.util;

import org.junit.jupiter.api.Test;

import static kmu.util.KmuValues.convertToOptionalText;
import static kmu.util.KmuValues.getTextOrEmpty;
import static kmu.util.KmuValues.hasText;
import static kmu.util.KmuValues.normalizeText;
import static kmu.util.KmuValues.readTextOrNull;
import static kmu.util.KmuValues.readValueOrNull;
import static kmu.util.KmuValues.requireNonBlankText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmuValuesTest {
    // --- normalizeText ---

    @Test
    void normalizeTextReturnsNullForNull() {
        assertThat(normalizeText(null)).isNull();
    }

    @Test
    void normalizeTextReturnsNullForBlank() {
        assertThat(normalizeText("   ")).isNull();
    }

    @Test
    void normalizeTextTrimsWhitespace() {
        assertThat(normalizeText("  hello  ")).isEqualTo("hello");
    }

    // --- convertToOptionalText ---

    @Test
    void convertToOptionalTextReturnsEmptyForBlank() {
        assertThat(convertToOptionalText("  ")).isEmpty();
    }

    @Test
    void convertToOptionalTextReturnsPresentForNonBlank() {
        assertThat(convertToOptionalText("hello")).contains("hello");
    }

    // --- getTextOrEmpty ---

    @Test
    void getTextOrEmptyReturnsEmptyStringForNull() {
        assertThat(getTextOrEmpty(null)).isEqualTo("");
    }

    @Test
    void getTextOrEmptyReturnsEmptyStringForBlank() {
        assertThat(getTextOrEmpty("  ")).isEqualTo("");
    }

    @Test
    void getTextOrEmptyReturnsTrimmedText() {
        assertThat(getTextOrEmpty("  hello  ")).isEqualTo("hello");
    }

    // --- hasText ---

    @Test
    void hasTextReturnsFalseForNull() {
        assertThat(hasText(null)).isFalse();
    }

    @Test
    void hasTextReturnsFalseForBlank() {
        assertThat(hasText("  ")).isFalse();
    }

    @Test
    void hasTextReturnsTrueForNonBlank() {
        assertThat(hasText("hello")).isTrue();
    }

    // --- requireNonBlankText ---

    @Test
    void requireNonBlankTextThrowsForNull() {
        assertThatThrownBy(() -> requireNonBlankText(null, "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    @Test
    void requireNonBlankTextThrowsForBlank() {
        assertThatThrownBy(() -> requireNonBlankText("  ", "field"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireNonBlankTextReturnsValueWhenPresent() {
        assertThat(requireNonBlankText("hello", "field")).isEqualTo("hello");
    }

    // --- readValueOrNull(supplier) ---

    @Test
    void readValueOrNullReturnsSupplierResult() {
        assertThat(readValueOrNull(() -> "hello")).isEqualTo("hello");
    }

    @Test
    void readValueOrNullReturnsNullOnRuntimeException() {
        assertThat((Object) readValueOrNull(() -> { throw new RuntimeException("boom"); })).isNull();
    }

    // --- readValueOrNull(value, extractor) ---

    @Test
    void readValueOrNullWithExtractorReturnsNullForNullValue() {
        assertThat(readValueOrNull((String) null, String::length)).isNull();
    }

    @Test
    void readValueOrNullWithExtractorAppliesExtractor() {
        assertThat(readValueOrNull("hello", String::length)).isEqualTo(5);
    }

    @Test
    void readValueOrNullWithExtractorReturnsNullWhenExtractorThrows() {
        assertThat((Object) readValueOrNull("hello", v -> { throw new RuntimeException("boom"); })).isNull();
    }

    // --- readTextOrNull(supplier) ---

    @Test
    void readTextOrNullReturnsNullOnRuntimeException() {
        assertThat(readTextOrNull(() -> { throw new RuntimeException("boom"); })).isNull();
    }

    @Test
    void readTextOrNullNormalizesBlankToNull() {
        assertThat(readTextOrNull(() -> "  ")).isNull();
    }

    @Test
    void readTextOrNullReturnsTrimmedText() {
        assertThat(readTextOrNull(() -> "  hello  ")).isEqualTo("hello");
    }

    // --- readTextOrNull(value, extractor) ---

    @Test
    void readTextOrNullWithExtractorReturnsNullForNullValue() {
        assertThat(readTextOrNull((Object) null, Object::toString)).isNull();
    }

    @Test
    void readTextOrNullWithExtractorNormalizesBlankToNull() {
        assertThat(readTextOrNull("anything", v -> "  ")).isNull();
    }

    @Test
    void readTextOrNullWithExtractorReturnsTrimmedText() {
        assertThat(readTextOrNull("hello", v -> "  " + v + "  ")).isEqualTo("hello");
    }

    @Test
    void readTextOrNullWithExtractorReturnsNullWhenExtractorThrows() {
        assertThat(readTextOrNull("hello", v -> { throw new RuntimeException("boom"); })).isNull();
    }
}
