package kmu;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuStringsTest {
    @Test
    void returnsConfiguredStringFromSource() {
        String value = KmuStrings.get(
                KmuStrings.CONDITION_PICKER_LOCATION,
                key -> "Configured");

        assertThat(value).isEqualTo("Configured");
    }

    @Test
    void redactsWhenSourceReturnsBlankValue() {
        String value = KmuStrings.get(
                KmuStrings.CONDITION_PICKER_LOCATION,
                key -> "  ");

        assertThat(value).isEqualTo(KmuStrings.REDACTED);
    }

    @Test
    void redactsWhenSourceThrows() {
        String value = KmuStrings.get(
                KmuStrings.CONDITION_PICKER_LOCATION,
                key -> {
                    throw new IllegalStateException("missing settings");
                });

        assertThat(value).isEqualTo(KmuStrings.REDACTED);
    }

    @Test
    void formatsConfiguredStringUsingRootLocale() {
        String value = KmuStrings.format(
                KmuStrings.CONDITION_PICKER_SUMMARY,
                key -> "%d configured %d",
                3,
                2);

        assertThat(value).isEqualTo("3 configured 2");
    }

    @Test
    void redactsWhenConfiguredFormatIsInvalid() {
        String value = KmuStrings.format(
                KmuStrings.CONDITION_PICKER_SUMMARY,
                key -> "%q",
                3,
                2);

        assertThat(value).isEqualTo(KmuStrings.REDACTED);
    }
}
