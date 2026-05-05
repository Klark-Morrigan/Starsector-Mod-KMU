package kmu.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuLocalizationTest {
    @Test
    void returnsConfiguredStringFromSource() {
        String value = KmuLocalization.get(
                KmuLocalization.CONDITION_PICKER_LOCATION,
                key -> "Configured");

        assertThat(value).isEqualTo("Configured");
    }

    @Test
    void redactsWhenSourceReturnsBlankValue() {
        String value = KmuLocalization.get(
                KmuLocalization.CONDITION_PICKER_LOCATION,
                key -> "  ");

        assertThat(value).isEqualTo(KmuLocalization.REDACTED);
    }

    @Test
    void redactsWhenSourceThrows() {
        String value = KmuLocalization.get(
                KmuLocalization.CONDITION_PICKER_LOCATION,
                key -> {
                    throw new IllegalStateException("missing settings");
                });

        assertThat(value).isEqualTo(KmuLocalization.REDACTED);
    }

    @Test
    void formatsConfiguredStringUsingRootLocale() {
        String value = KmuLocalization.format(
                KmuLocalization.CONDITION_PICKER_SUMMARY,
                key -> "%d configured %d",
                3,
                2);

        assertThat(value).isEqualTo("3 configured 2");
    }

    @Test
    void redactsWhenConfiguredFormatIsInvalid() {
        String value = KmuLocalization.format(
                KmuLocalization.CONDITION_PICKER_SUMMARY,
                key -> "%q",
                3,
                2);

        assertThat(value).isEqualTo(KmuLocalization.REDACTED);
    }
}
