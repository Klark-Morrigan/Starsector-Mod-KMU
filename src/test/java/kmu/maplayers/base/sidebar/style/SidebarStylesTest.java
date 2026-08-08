package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.render.gl.style.WidgetStyle;
import kmlib.starsector.ui.sound.StarsectorUiSound;
import kmlib.starsector.ui.sound.UiSoundScheme;
import kmlib.starsector.ui.widgets.tabs.style.TabChrome;

import kmu.settings.SidebarColourSchemeChoice;
import kmu.settings.SidebarSettingsMock;
import kmu.starsector.StarsectorUiColoursMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SidebarStyles}: which live values each part of the sidebar's look is composed from. The
 * look is built fresh every frame from the running game's colours, so what is worth pinning is not a
 * shade but a wiring - that the box takes the black backdrop, that a frame takes the step of the chosen
 * scheme its framing names while the controls stay on that scheme's base whichever framing is chosen,
 * that the row's chrome rule follows that same scheme, that each tab chrome carries the palette, the
 * face, and the hotkey convention belonging to it, and that the panel's sound scheme is the engine's own.
 *
 * <p>The sound scheme is here rather than only in KMLib because this is where the sidebar's two halves
 * meet: the widget style carries the scheme to the paint side and the host hands the same constant to
 * the panel's controller, so a look composed with some other scheme would leave the panel looking and
 * sounding from two.
 */
final class SidebarStylesTest {

    // The band height the look is composed at. Any positive height does - this factory reads none of the
    // tab style it is handed - so it is named rather than repeated.
    private static final float HEADER_BAND_HEIGHT = 19f;

    private StarsectorUiColoursMock uiColoursMock;
    private SidebarSettingsMock sidebarSettingsMock;

    @BeforeEach
    void mockLiveColoursAndSettings() {

        uiColoursMock = StarsectorUiColoursMock.install();
        sidebarSettingsMock = SidebarSettingsMock.install();
    }

    @AfterEach
    void closeLiveColoursAndSettings() {

        sidebarSettingsMock.close();
        uiColoursMock.close();
    }

    @Nested
    class BuildAccentFramedStyle {

        @Test
        void buildAccentFramedStyleFillsTheBoxBlackAndFramesItInItsOwnAccent() {
            // The body stays neutral so only the header, the accents, and the notch carry colour; the
            // frame takes the accent because a panel floating free on the map has no chrome to match.
            var boxColours = buildAccentFramedStyle().boxColours();

            assertThat(boxColours.fill())
                .isEqualTo(Color.BLACK);
            assertThat(boxColours.border())
                .isEqualTo(StarsectorUiColoursMock.BUTTON_TEXT);
        }

        @Test
        void buildAccentFramedStyleTakesTheChosenSchemesPairForItsControls() {
            // Both steps of the one accent, and in that order - the brighter shade is what a tick has to
            // read against, so a pair handed over crossed would tick in the colour it sits on.
            var accentColours = buildAccentFramedStyle().accentColours();

            assertThat(accentColours.base())
                .isEqualTo(StarsectorUiColoursMock.BUTTON_TEXT);
            assertThat(accentColours.bright())
                .isEqualTo(StarsectorUiColoursMock.LIGHT_HIGHLIGHT);
        }

        @Test
        void buildAccentFramedStyleMovesItsFrameAndItsControlsTogetherWhenTheSchemeChanges() {
            // The frame is a separate knob from the controls, which is exactly how the two could come to
            // answer different palettes: this pins that the accent-framed look spends one resolved pair
            // on both, so a scheme change cannot leave a panel ruled in one palette and framed in another.
            sidebarSettingsMock.selectColourScheme(SidebarColourSchemeChoice.PLAYER_FACTION);

            var style = buildAccentFramedStyle();

            assertThat(style.boxColours().border())
                .isEqualTo(StarsectorUiColoursMock.PLAYER_BASE);
            assertThat(style.accentColours().base())
                .isEqualTo(StarsectorUiColoursMock.PLAYER_BASE);
            assertThat(style.accentColours().bright())
                .isEqualTo(StarsectorUiColoursMock.PLAYER_BRIGHT);
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
        void buildChromeFramedStyleFramesTheBoxInTheSchemesDarkStep() {
            // The frame abuts another screen's own frames, and those are drawn in the dark member of the
            // three-colour set the engine builds a control from - so this framing takes the dark step
            // where the other takes the base. The whole visible point of the frame being its own knob.
            assertThat(buildChromeFramedStyle().boxColours().border())
                .isEqualTo(StarsectorUiColoursMock.BUTTON_BG_DARK);
        }

        @Test
        void buildChromeFramedStyleStillTakesTheChosenSchemesPairForItsControls() {
            // Only the frame answers to what the panel abuts. The controls take the scheme's pair
            // wherever the panel is drawn, so a screen choosing its frame must not quietly repaint its
            // checkboxes.
            var accentColours = buildChromeFramedStyle().accentColours();

            assertThat(accentColours.base())
                .isEqualTo(StarsectorUiColoursMock.BUTTON_TEXT);
            assertThat(accentColours.bright())
                .isEqualTo(StarsectorUiColoursMock.LIGHT_HIGHLIGHT);
        }

        @Test
        void buildChromeFramedStyleMovesItsFrameWithTheSchemeOneStepBelowItsControls() {
            // The mirror of the accent-framed case, and the one that makes the frame worth being its own
            // knob: the two framings part by which step of the scheme they take, never by which palette,
            // so a scheme change moves the frame and the controls together and leaves them one step
            // apart. Wiring the frame to the accent - the obvious tidy, the two being one colour under
            // the other framing - would pass every other case in this file.
            sidebarSettingsMock.selectColourScheme(SidebarColourSchemeChoice.PLAYER_FACTION);

            var style = buildChromeFramedStyle();

            assertThat(style.boxColours().border())
                .isEqualTo(StarsectorUiColoursMock.PLAYER_DARK);
            assertThat(style.accentColours().base())
                .isEqualTo(StarsectorUiColoursMock.PLAYER_BASE);
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

        @Test
        void buildStripTabStyleRulesTheRowInTheChosenSchemesAccent() {
            // The third of the panel's colour reads, and the one furthest from the other two - it rides
            // in the tab style rather than the widget style - so it is the one that could quietly keep
            // answering a palette of its own while the frame and the controls moved.
            assertThat(SidebarStyles.buildStripTabStyle(HEADER_BAND_HEIGHT).palette().chromeAccent())
                .isEqualTo(StarsectorUiColoursMock.BUTTON_TEXT);
        }

        @Test
        void buildStripTabStyleLettersTheRowInTheMapsOwnOrbitronAtTheLayoutsTabSize() {
            // The face the vanilla tabs a strip sits beneath are set in, scaled to the tab size rather
            // than drawn at its atlas's own - orbitron is a smooth face and reads clean either way. The
            // claim is that the strip kept it: the face travels per chrome, so the row that took the
            // pixel face below could have taken this one with it.
            var face = SidebarStyles.buildStripTabStyle(HEADER_BAND_HEIGHT).face();

            assertThat(face.font())
                .isEqualTo(StarsectorFont.VANILLA_ORBITRON_20AA);
            assertThat(face.size())
                .isEqualTo(15d);
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
        void buildRaisedButtonTabStyleRulesTheRowInTheChosenSchemesDarkStep() {
            // Where the strip's rule is the scheme's base, a button's is its dark step: the engine frames
            // and fills its own buttons from the dark member of the accent it builds them with. The two
            // chromes sharing one palette - which they did while this chrome was built to a description of
            // the intel screen rather than to its source - is what outlined this row in a step no button
            // beside it wears.
            assertThat(SidebarStyles.buildRaisedButtonTabStyle(HEADER_BAND_HEIGHT).palette().chromeAccent())
                .isEqualTo(StarsectorUiColoursMock.BUTTON_BG_DARK);
        }

        @Test
        void buildRaisedButtonTabStyleLabelsTheShownButtonInTheSchemesBrightStep() {
            // The third of the accent's three steps, so this pins that the whole set reaches the row: a
            // button is built from all three and the palette would take the wrong one silently.
            assertThat(SidebarStyles.buildRaisedButtonTabStyle(HEADER_BAND_HEIGHT).palette()
                    .selected().label())
                .isEqualTo(StarsectorUiColoursMock.LIGHT_HIGHLIGHT);
        }

        @Test
        void buildRaisedButtonTabStyleMovesItsWholeRowWhenTheSchemeChanges() {
            // The row answers the one scheme the rest of the panel does, at every step it reads - so a
            // player pointing the sidebar elsewhere cannot leave its buttons outlined in one palette and
            // labelled from another.
            sidebarSettingsMock.selectColourScheme(SidebarColourSchemeChoice.PLAYER_FACTION);

            var palette = SidebarStyles.buildRaisedButtonTabStyle(HEADER_BAND_HEIGHT).palette();

            assertThat(palette.chromeAccent())
                .isEqualTo(StarsectorUiColoursMock.PLAYER_DARK);
            assertThat(palette.selected().label())
                .isEqualTo(StarsectorUiColoursMock.PLAYER_BRIGHT);
        }

        @Test
        void buildRaisedButtonTabStyleLettersTheRowInVanillasPixelFaceAtItsAtlasSize() {
            // The face the intel screen's own map toggles are lettered in, and at the size its atlas was
            // drawn at: a pixel face is crisp at one size only, so a row copying those buttons scaled to
            // any other would read as a blurred imitation of the row beside it.
            var face = SidebarStyles.buildRaisedButtonTabStyle(HEADER_BAND_HEIGHT).face();

            assertThat(face.font())
                .isEqualTo(StarsectorFont.VANILLA_VICTOR_10);
            assertThat(face.size())
                .isEqualTo(10d);
        }
    }

    // The look framed in its own accent. The tab style is passed as null deliberately: these factories
    // carry it through without reading it, so building a real one would drag the tab palette's own live
    // reads into cases about the panel around it.
    private static WidgetStyle buildAccentFramedStyle() {
        return SidebarStyles.buildAccentFramedStyle(null);
    }

    // The look framed in the scheme's dark step; the tab style is null for the same reason.
    private static WidgetStyle buildChromeFramedStyle() {
        return SidebarStyles.buildChromeFramedStyle(null);
    }
}
