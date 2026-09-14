package kmu.conditions.ui.picker.tooltip;

import kmlib.starsector.ui.colour.StarsectorUiColour;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuTooltipSectionStyleTest {

    @Nested
    class Muted {

        @Test
        void mutedStyleUsesFrozenPlayerBlueBannerAndGrayBodyText() {
            assertThat(KmuTooltipSectionStyle.MUTED.titleRawColour())
                    .isEqualTo(StarsectorUiColour.LIGHT_BLUE);
            assertThat(KmuTooltipSectionStyle.MUTED.backgroundRawColour())
                    .isEqualTo(StarsectorUiColour.DARK_BLUE);
            assertThat(KmuTooltipSectionStyle.MUTED.bodyRawColour())
                    .isEqualTo(StarsectorUiColour.VANILLA_GRAY);
        }
    }

    @Nested
    class Warning {

        @Test
        void warningStyleUsesWarningBannerAndNormalBodyText() {
            assertThat(KmuTooltipSectionStyle.WARNING.titleRawColour())
                    .isEqualTo(StarsectorUiColour.ORANGE);
            assertThat(KmuTooltipSectionStyle.WARNING.backgroundRawColour())
                    .isEqualTo(StarsectorUiColour.DARK_RED);
            assertThat(KmuTooltipSectionStyle.WARNING.bodyRawColour())
                    .isEqualTo(StarsectorUiColour.VANILLA_TEXT);
        }
    }
}
