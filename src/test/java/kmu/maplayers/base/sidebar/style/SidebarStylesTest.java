package kmu.maplayers.base.sidebar.style;

import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.render.gl.style.WidgetStyle;
import kmlib.starsector.ui.sound.StarsectorUiSound;
import kmlib.starsector.ui.sound.UiSoundScheme;
import kmlib.starsector.ui.widgets.tabs.style.TabChrome;

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
 * shade but a wiring - that the box takes the black backdrop, that a frame answers to what the panel
 * abuts while the controls stay the player's pick whichever framing is chosen, that each tab chrome
 * carries the hotkey convention belonging to it, and that the panel's sound scheme is the engine's own.
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

    // The fixed UI grey the vanilla chrome around an overlaid panel is framed in. Its own shade rather
    // than the shared engine one below, so a frame that fell back to the player accent - or to any other
    // live read - is caught rather than passing on a coincidence of the test's setup.
    private static final Color UI_GRAY = new Color(155, 155, 155);

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
        miscMock
            .when(Misc::getGrayColor)
            .thenReturn(UI_GRAY);

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
    class BuildAccentFramedStyle {

        @Test
        void buildAccentFramedStyleFillsTheBoxBlackAndFramesItInThePlayerAccent() {
            // The body stays neutral so only the header, the accents, and the notch carry colour; the
            // frame takes the accent because a panel floating free on the map has no chrome to match.
            var boxColours = buildAccentFramedStyle().boxColours();

            assertThat(boxColours.fill())
                .isEqualTo(Color.BLACK);
            assertThat(boxColours.border())
                .isEqualTo(PLAYER_BASE);
        }

        @Test
        void buildAccentFramedStyleTakesThePlayerPairForItsControls() {
            // Both steps of the one accent, and in that order - the brighter shade is what a tick has to
            // read against, so a pair handed over crossed would tick in the colour it sits on.
            var accentColours = buildAccentFramedStyle().accentColours();

            assertThat(accentColours.base())
                .isEqualTo(PLAYER_BASE);
            assertThat(accentColours.bright())
                .isEqualTo(PLAYER_BRIGHT);
        }

        @Test
        void buildAccentFramedStyleAnswersEveryControlMomentWithTheEnginesOwnSounds() {
            // Spelt out rather than compared against the constant the factory reads, so this pins both
            // that the sidebar wears the vanilla scheme and that the look is composed with it.
            assertThat(buildAccentFramedStyle().soundScheme())
                .isEqualTo(new UiSoundScheme(
                    StarsectorUiSound.BUTTON_PRESSED,
                    StarsectorUiSound.BUTTON_MOUSEOVER));
        }
    }

    @Nested
    class BuildChromeFramedStyle {

        @Test
        void buildChromeFramedStyleFramesTheBoxInTheSurroundingChromesGrey() {
            // The frame abuts another screen's own frames, so it takes the fixed UI role those answer to
            // rather than the player's accent - the whole visible point of the frame being its own knob.
            assertThat(buildChromeFramedStyle().boxColours().border())
                .isEqualTo(UI_GRAY);
        }

        @Test
        void buildChromeFramedStyleStillTakesThePlayerPairForItsControls() {
            // Only the frame answers to what the panel abuts. The controls are the player's pick wherever
            // the panel is drawn, so a screen choosing its frame must not quietly repaint its checkboxes.
            var accentColours = buildChromeFramedStyle().accentColours();

            assertThat(accentColours.base())
                .isEqualTo(PLAYER_BASE);
            assertThat(accentColours.bright())
                .isEqualTo(PLAYER_BRIGHT);
        }

        @Test
        void buildChromeFramedStyleFillsTheBoxBlackLikeEveryOtherScreens() {
            // The backdrop is not a per-screen choice: a translucent-black pane is what lets the map show
            // through under either host, so the two looks part at the frame and nowhere else.
            assertThat(buildChromeFramedStyle().boxColours().fill())
                .isEqualTo(Color.BLACK);
        }
    }

    @Nested
    class BuildStripTabStyle {

        @Test
        void buildStripTabStyleStandsTheBandAtTheHeightItsHostAsksFor() {
            // The one dimension the two screens set apart, each matching the weight of the chrome beside
            // it - so it has to arrive from the host rather than being the factory's own.
            assertThat(SidebarStyles.buildStripTabStyle(HEADER_BAND_HEIGHT).headerBandHeight())
                .isEqualTo(HEADER_BAND_HEIGHT);
        }

        @Test
        void buildStripTabStyleWearsTheSeamlessStripWithItsKeyUnderlined() {
            // The sector map's pair. The two travel together because the underline is that chrome's own
            // convention: a strip whose key was left bare would mismatch the vanilla tabs above it.
            var tabStyle = SidebarStyles.buildStripTabStyle(HEADER_BAND_HEIGHT);

            assertThat(tabStyle.chrome())
                .isEqualTo(TabChrome.STRIP);
            assertThat(tabStyle.hotkey().isKeyUnderlined())
                .isTrue();
        }
    }

    @Nested
    class BuildRaisedButtonTabStyle {

        @Test
        void buildRaisedButtonTabStyleStandsTheBandAtTheHeightItsHostAsksFor() {
            assertThat(SidebarStyles.buildRaisedButtonTabStyle(HEADER_BAND_HEIGHT).headerBandHeight())
                .isEqualTo(HEADER_BAND_HEIGHT);
        }

        @Test
        void buildRaisedButtonTabStyleWearsTheButtonsWithItsKeyLeftBare() {
            // The intel screen's pair, and the reason the two are chosen in one place: its map toggles
            // stand as buttons and light their key by colour alone, so a button row that underlined its
            // key would mismatch the very row it was drawn to match.
            var tabStyle = SidebarStyles.buildRaisedButtonTabStyle(HEADER_BAND_HEIGHT);

            assertThat(tabStyle.chrome())
                .isEqualTo(TabChrome.RAISED_BUTTON);
            assertThat(tabStyle.hotkey().isKeyUnderlined())
                .isFalse();
        }

        @Test
        void buildRaisedButtonTabStyleTakesTheSameTabPaletteTheStripDoes() {
            // The chromes differ in what the shades are painted onto, not in the shades: one palette for
            // both is what keeps a tab fading, pulsing, and blinking alike on either screen.
            assertThat(SidebarStyles.buildRaisedButtonTabStyle(HEADER_BAND_HEIGHT).palette())
                .isEqualTo(SidebarStyles.buildStripTabStyle(HEADER_BAND_HEIGHT).palette());
        }
    }

    // The look framed in its own accent. The tab style is passed as null deliberately: these factories
    // carry it through without reading it, so building a real one would drag the tab palette's own live
    // reads into cases about the panel around it.
    private static WidgetStyle buildAccentFramedStyle() {
        return SidebarStyles.buildAccentFramedStyle(null);
    }

    // The look framed in the surrounding chrome's grey; the tab style is null for the same reason.
    private static WidgetStyle buildChromeFramedStyle() {
        return SidebarStyles.buildChromeFramedStyle(null);
    }
}
