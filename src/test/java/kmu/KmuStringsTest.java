package kmu;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuStringsTest {
    @Test
    void returnsConfiguredStringFromSource() {
        String value = KmuStrings.get(
                KmuStrings.CONDITION_PICKER_TITLE,
                "Fallback",
                key -> "Configured");

        assertThat(value).isEqualTo("Configured");
    }

    @Test
    void fallsBackWhenSourceReturnsBlankValue() {
        String value = KmuStrings.get(
                KmuStrings.CONDITION_PICKER_TITLE,
                "Fallback",
                key -> "  ");

        assertThat(value).isEqualTo("Fallback");
    }

    @Test
    void fallsBackWhenSourceThrows() {
        String value = KmuStrings.get(
                KmuStrings.CONDITION_PICKER_TITLE,
                "Fallback",
                key -> {
                    throw new IllegalStateException("missing settings");
                });

        assertThat(value).isEqualTo("Fallback");
    }

    @Test
    void formatsConfiguredStringUsingRootLocale() {
        String value = KmuStrings.format(
                KmuStrings.CONDITION_PICKER_SUMMARY,
                "%d fallback %d",
                key -> "%d configured %d",
                3,
                2);

        assertThat(value).isEqualTo("3 configured 2");
    }

    @Test
    void fallsBackWhenConfiguredFormatIsInvalid() {
        String value = KmuStrings.format(
                KmuStrings.CONDITION_PICKER_SUMMARY,
                "%d fallback %d",
                key -> "%q",
                3,
                2);

        assertThat(value).isEqualTo("3 fallback 2");
    }
}
