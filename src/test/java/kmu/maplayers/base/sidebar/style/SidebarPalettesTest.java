package kmu.maplayers.base.sidebar.style;

import com.fs.starfarer.api.util.Misc;

import kmu.settings.NotchChevronColorChoice;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SidebarPalettes}: the collapse handle's chevron shades follow the player's colour
 * choice, and the gold choice holds one shade across rest and hover while the panel-accent choice
 * steps up to the brighter accent under the pointer.
 */
final class SidebarPalettesTest {
    private static final Color GOLD = new Color(255, 255, 175);
    private static final Color ACCENT = new Color(170, 222, 255);
    private static final Color BRIGHT_ACCENT = new Color(255, 255, 255);

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void mockStarsectorThemeColors() {
        StarsectorSettingsFake.installSettings();
        miscMock = Mockito.mockStatic(Misc.class);
        miscMock.when(Misc::getHighlightColor).thenReturn(GOLD);
    }

    @AfterEach
    void closeStarsectorThemeColors() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ResolveNotchColors {

        @Test
        void resolveNotchColorsTakesTheVanillaHighlightForTheGoldChoice() {
            var colors = SidebarPalettes.resolveNotchColors(
                    NotchChevronColorChoice.GOLD, ACCENT, BRIGHT_ACCENT);
            assertThat(colors.chevron()).isEqualTo(GOLD);
        }

        @Test
        void resolveNotchColorsHoldsTheGoldAcrossRestAndHover() {
            // Gold has no brighter sibling to step to, so the notch's own accent wash answers the
            // pointer and the glyph keeps its colour.
            var colors = SidebarPalettes.resolveNotchColors(
                    NotchChevronColorChoice.GOLD, ACCENT, BRIGHT_ACCENT);
            assertThat(colors.chevronHovered()).isEqualTo(colors.chevron());
        }

        @Test
        void resolveNotchColorsTakesThePanelAccentsForThePanelAccentChoice() {
            var colors = SidebarPalettes.resolveNotchColors(
                    NotchChevronColorChoice.PANEL_ACCENT, ACCENT, BRIGHT_ACCENT);
            assertThat(colors.chevron()).isEqualTo(ACCENT);
            assertThat(colors.chevronHovered()).isEqualTo(BRIGHT_ACCENT);
        }
    }
}
