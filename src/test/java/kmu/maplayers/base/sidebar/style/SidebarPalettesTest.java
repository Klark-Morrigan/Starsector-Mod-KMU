package kmu.maplayers.base.sidebar.style;

import com.fs.starfarer.api.util.Misc;

import kmu.settings.NotchChevronColourChoice;
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
    void mockStarsectorThemeColours() {
        StarsectorSettingsFake.installSettings();
        miscMock = Mockito.mockStatic(Misc.class);
        miscMock.when(Misc::getHighlightColor).thenReturn(GOLD);
    }

    @AfterEach
    void closeStarsectorThemeColours() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ResolveNotchColours {

        @Test
        void resolveNotchColoursTakesTheVanillaHighlightForTheGoldChoice() {
            var colours = SidebarPalettes.resolveNotchColours(
                    NotchChevronColourChoice.GOLD, ACCENT, BRIGHT_ACCENT);
            assertThat(colours.chevron()).isEqualTo(GOLD);
        }

        @Test
        void resolveNotchColoursHoldsTheGoldAcrossRestAndHover() {
            // Gold has no brighter sibling to step to, so the notch's own accent wash answers the
            // pointer and the glyph keeps its colour.
            var colours = SidebarPalettes.resolveNotchColours(
                    NotchChevronColourChoice.GOLD, ACCENT, BRIGHT_ACCENT);
            assertThat(colours.chevronHovered()).isEqualTo(colours.chevron());
        }

        @Test
        void resolveNotchColoursTakesThePanelAccentsForThePanelAccentChoice() {
            var colours = SidebarPalettes.resolveNotchColours(
                    NotchChevronColourChoice.PANEL_ACCENT, ACCENT, BRIGHT_ACCENT);
            assertThat(colours.chevron()).isEqualTo(ACCENT);
            assertThat(colours.chevronHovered()).isEqualTo(BRIGHT_ACCENT);
        }
    }
}
