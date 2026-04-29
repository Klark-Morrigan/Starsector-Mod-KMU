package kmu.ui.chooser.tooltip;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuTooltipSectionStyleTest {
    @Test
    void mutedStyleUsesStandardBlueBannerAndMutedBodyText() {
        assertThat(KmuTooltipSectionStyle.MUTED.titleColor())
                .isEqualTo(KmuTooltipSectionPalette.FALLBACK_STANDARD_BLUE_TITLE);
        assertThat(KmuTooltipSectionStyle.MUTED.backgroundColor())
                .isEqualTo(KmuTooltipSectionPalette.FALLBACK_STANDARD_SECTION_BACKDROP);
        assertThat(KmuTooltipSectionStyle.MUTED.bodyColor())
                .isEqualTo(KmuTooltipSectionPalette.FALLBACK_MUTED_TEXT);
    }

    @Test
    void warningStyleUsesWarningBannerAndNormalBodyText() {
        assertThat(KmuTooltipSectionStyle.WARNING.titleColor())
                .isEqualTo(KmuTooltipSectionPalette.FALLBACK_WARNING_TITLE);
        assertThat(KmuTooltipSectionStyle.WARNING.backgroundColor())
                .isEqualTo(KmuTooltipSectionPalette.WARNING_BACKDROP);
        assertThat(KmuTooltipSectionStyle.WARNING.bodyColor())
                .isEqualTo(KmuTooltipSectionPalette.FALLBACK_STANDARD_TEXT);
    }
}
