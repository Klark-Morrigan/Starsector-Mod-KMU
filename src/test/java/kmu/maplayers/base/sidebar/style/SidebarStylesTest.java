package kmu.maplayers.base.sidebar.style;

import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.sound.StarsectorUiSound;
import kmlib.starsector.ui.sound.UiSoundScheme;

import kmu.settings.KmuMapLayerSettings;
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
 * Pins {@link SidebarStyles}: which live values each part of the sidebar's look is composed from. The
 * look is built fresh every frame from the running game's colours, so what is worth pinning is not a
 * shade but a wiring - that the box takes the black backdrop and the player accent for its frame, that
 * the controls take the player pair, and that the panel's sound scheme is the engine's own.
 *
 * <p>The sound scheme is here rather than only in KMLib because this is where the sidebar's two halves
 * meet: the widget style carries the scheme to the paint side and the host hands the same constant to
 * the panel's controller, so a look composed with some other scheme would leave the panel looking and
 * sounding from two.
 */
final class SidebarStylesTest {

    private static final Color PLAYER_BASE = new Color(170, 222, 255);
    private static final Color PLAYER_BRIGHT = new Color(255, 255, 255);
    private static final Color HIGHLIGHT_GOLD = new Color(255, 255, 175);

    // What every named engine colour key answers with. The tab palette reads several of them by name and
    // none of the assertions here turn on which - one shade for all of them keeps the setup to the fact
    // these cases actually need, that the install has a palette at all.
    private static final Color ENGINE_UI_SHADE = new Color(100, 100, 100);

    // The band height the look is composed at. Any positive height does - this factory reads none of the
    // tab style it is handed - so it is named rather than repeated.
    private static final float HEADER_BAND_HEIGHT = 19f;

    private MockedStatic<Misc> miscMock;
    private MockedStatic<KmuMapLayerSettings> settingsMock;

    @BeforeEach
    void mockLiveColoursAndSettings() {

        StarsectorSettingsFake.installSettings(key -> ENGINE_UI_SHADE);

        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getBasePlayerColor)
            .thenReturn(PLAYER_BASE);
        miscMock
            .when(Misc::getBrightPlayerColor)
            .thenReturn(PLAYER_BRIGHT);
        miscMock
            .when(Misc::getHighlightColor)
            .thenReturn(HIGHLIGHT_GOLD);

        // The vanilla tab palette reads the button label shade through Misc, so the tab-style case needs
        // it named or the mock answers null and the palette refuses to resolve.
        miscMock
            .when(Misc::getButtonTextColor)
            .thenReturn(ENGINE_UI_SHADE);

        settingsMock = Mockito.mockStatic(KmuMapLayerSettings.class);
        settingsMock
            .when(KmuMapLayerSettings::getMapSidebarChevronColour)
            .thenReturn(NotchChevronColourChoice.PANEL_ACCENT);
    }

    @AfterEach
    void closeLiveColoursAndSettings() {

        settingsMock.close();
        miscMock.close();
        
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class BuildPlayerAccentedStyle {

        @Test
        void buildPlayerAccentedStyleFillsTheBoxBlackAndFramesItInThePlayerAccent() {
            // The body stays neutral so only the header, the accents, and the notch carry colour; the
            // frame takes the accent because a panel floating free on the map has no chrome to match.
            var boxColours = buildStyle().boxColours();

            assertThat(boxColours.fill())
                .isEqualTo(Color.BLACK);
            assertThat(boxColours.border())
                .isEqualTo(PLAYER_BASE);
        }

        @Test
        void buildPlayerAccentedStyleTakesThePlayerPairForItsControls() {
            // Both steps of the one accent, and in that order - the brighter shade is what a tick has to
            // read against, so a pair handed over crossed would tick in the colour it sits on.
            var accentColours = buildStyle().accentColours();

            assertThat(accentColours.base())
                .isEqualTo(PLAYER_BASE);
            assertThat(accentColours.bright())
                .isEqualTo(PLAYER_BRIGHT);
        }

        @Test
        void buildPlayerAccentedStyleAnswersEveryControlMomentWithTheEnginesOwnSounds() {
            // Spelt out rather than compared against the constant the factory reads, so this pins both
            // that the sidebar wears the vanilla scheme and that the look is composed with it.
            assertThat(buildStyle().soundScheme())
                .isEqualTo(new UiSoundScheme(
                    StarsectorUiSound.BUTTON_PRESSED,
                    StarsectorUiSound.BUTTON_MOUSEOVER));
        }

        // The look under test. The tab style is passed as null deliberately: this factory carries it
        // through without reading it, so building a real one would drag the tab palette's own live reads
        // into cases about the panel around it.
        private static kmlib.starsector.ui.render.gl.WidgetStyle buildStyle() {
            return SidebarStyles.buildPlayerAccentedStyle(null);
        }
    }

    @Nested
    class BuildTabStyle {

        @Test
        void buildTabStyleStandsTheBandAtTheHeightItsHostAsksFor() {
            // The one dimension the two screens set apart, each matching the weight of the chrome beside
            // it - so it has to arrive from the host rather than being the factory's own.
            assertThat(SidebarStyles.buildTabStyle(HEADER_BAND_HEIGHT).headerBandHeight())
                .isEqualTo(HEADER_BAND_HEIGHT);
        }
    }
}
