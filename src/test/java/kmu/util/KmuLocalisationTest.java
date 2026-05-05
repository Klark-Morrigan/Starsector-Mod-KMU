package kmu.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuLocalisationTest {
    @Test
    void returnsConfiguredStringFromSource() {
        String value = KmuLocalisation.get(
                KmuLocalisation.CONDITION_PICKER_LOCATION,
                key -> "Configured");

        assertThat(value).isEqualTo("Configured");
    }

    @Test
    void redactsWhenSourceReturnsBlankValue() {
        String value = KmuLocalisation.get(
                KmuLocalisation.CONDITION_PICKER_LOCATION,
                key -> "  ");

        assertThat(value).isEqualTo(KmuLocalisation.REDACTED);
    }

    @Test
    void redactsWhenSourceThrows() {
        String value = KmuLocalisation.get(
                KmuLocalisation.CONDITION_PICKER_LOCATION,
                key -> {
                    throw new IllegalStateException("missing settings");
                });

        assertThat(value).isEqualTo(KmuLocalisation.REDACTED);
    }

    @Test
    void formatsConfiguredStringUsingRootLocale() {
        String value = KmuLocalisation.format(
                KmuLocalisation.CONDITION_PICKER_SUMMARY,
                key -> "%d configured %d",
                3,
                2);

        assertThat(value).isEqualTo("3 configured 2");
    }

    @Test
    void redactsWhenConfiguredFormatIsInvalid() {
        String value = KmuLocalisation.format(
                KmuLocalisation.CONDITION_PICKER_SUMMARY,
                key -> "%q",
                3,
                2);

        assertThat(value).isEqualTo(KmuLocalisation.REDACTED);
    }
}
