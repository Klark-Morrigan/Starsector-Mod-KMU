package kmu.conditions.ui.picker.tooltip;

import kmlib.starsector.ui.color.StarsectorUiColor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuTooltipSectionStyleTest {
    @Test
    void mutedStyleUsesBasePlayerBannerAndGrayBodyText() {
        assertThat(KmuTooltipSectionStyle.MUTED.titleRawColor())
                .isEqualTo(StarsectorUiColor.BLUE);
        assertThat(KmuTooltipSectionStyle.MUTED.backgroundRawColor())
                .isEqualTo(StarsectorUiColor.DARK_BLUE);
        assertThat(KmuTooltipSectionStyle.MUTED.bodyRawColor())
                .isEqualTo(StarsectorUiColor.GRAY);
    }

    @Test
    void warningStyleUsesWarningBannerAndNormalBodyText() {
        assertThat(KmuTooltipSectionStyle.WARNING.titleRawColor())
                .isEqualTo(StarsectorUiColor.ORANGE);
        assertThat(KmuTooltipSectionStyle.WARNING.backgroundRawColor())
                .isEqualTo(StarsectorUiColor.DARK_RED);
        assertThat(KmuTooltipSectionStyle.WARNING.bodyRawColor())
                .isEqualTo(StarsectorUiColor.TEXT_WHITE);
    }
}
