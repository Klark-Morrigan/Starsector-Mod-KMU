package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.render.gl.style.WidgetStyle;
import kmlib.starsector.ui.sound.PointerArrivalTarget;
import kmlib.starsector.ui.sound.PointerArrivalVolumes;
import kmlib.starsector.ui.sound.StarsectorUiSound;
import kmlib.starsector.ui.sound.UiSoundCue;
import kmlib.starsector.ui.sound.UiSoundScheme;
import kmlib.starsector.ui.widgets.tabs.style.TabBox;
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
 * that the wash a pointer lifts one of those controls by comes off that same base, that the row's chrome
 * rule follows that same scheme, that each tab chrome carries the palette, the
 * face, the ring around it, and the hotkey convention belonging to it, and that the panel's sound scheme
 * names the engine's own roles at the levels the player set them to.
 *
 * <p>The sound scheme is here rather than only in KMLib because this is where the sidebar's two halves
 * meet: the widget style carries the scheme to the paint side and the host composes the same scheme for
 * the panel's controller, so a look composed with some other scheme would leave the panel looking and
 * sounding from two. The levels are here for the same reason they are read here at all - KMLib names the
 * kinds and takes the numbers, and which numbers those are is this mod's answer.
 */
final class SidebarStylesTest {

    // The band height the look is composed at. Any positive height does - this factory reads none of the
    // tab style it is handed - so it is named rather than repeated.
    private static final float HEADER_BAND_HEIGHT = 19f;

    // The one level left standing in the case about a balance silenced everywhere but one kind. Any
    // audible level does; it is named so the case's expectation and the level it set are one value.
    private static final float LAST_AUDIBLE_VOLUME = 0.25f;

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
        void buildAccentFramedStyleWashesAHoveredControlInTheSchemesOwnBase() {
            // The lift the pointer adds is more of what the control already wears, so it comes off the
            // same resolved set: a wash resolved apart from the accents is a shade the panel names
            // nowhere else, and it would show only under a pointer nothing in this file otherwise puts
            // anywhere.
            assertThat(buildAccentFramedStyle().controlHoverWash().colour())
                .isEqualTo(StarsectorUiColoursMock.BUTTON_TEXT);
        }

        @Test
        void buildAccentFramedStyleMovesItsHoverWashWithTheSchemeToo() {
            // The hover wash is a third reader of the one scheme choice, beside the frame and the
            // controls. Left on a shade resolved once, it would go on washing in the old palette after a
            // scheme change - a panel lit in one colour and ruled in another, visible only on hover.
            sidebarSettingsMock.selectColourScheme(SidebarColourSchemeChoice.PLAYER_FACTION);

            assertThat(buildAccentFramedStyle().controlHoverWash().colour())
                .isEqualTo(StarsectorUiColoursMock.PLAYER_BASE);
        }

        @Test
        void buildAccentFramedStyleAnswersEveryControlMomentWithTheEnginesOwnSoundsAtThePlayersLevels() {
            // Spelt out rather than compared against what the factory beside it returns, so this pins
            // both that the sidebar wears the vanilla roles and that the look is composed with the
            // player's own balance - a look built from the library's defaults would sound the same on a
            // fresh install and ignore the sliders thereafter.
            assertThat(buildAccentFramedStyle().soundScheme())
                .isEqualTo(new UiSoundScheme(
                    UiSoundCue.createAtFullVolume(StarsectorUiSound.BUTTON_PRESSED),
                    StarsectorUiSound.BUTTON_MOUSEOVER,
                    SidebarSettingsMock.ARRIVAL_VOLUMES,
                    new UiSoundCue(
                        StarsectorUiSound.LIST_SCROLLED,
                        SidebarSettingsMock.LIST_SCROLL_VOLUME)));
        }
    }

    @Nested
    class BuildSidebarSoundScheme {

        @Test
        void buildSidebarSoundSchemeReachesEachKindOfThingAtItsOwnSettingsLevel() {
            // The three levels are one balance, and what makes it right is the ratio between them - so
            // what is pinned is that each kind resolves the level set for it and not another's. Two of
            // the three ship at the same number, which is exactly why the stubbed levels differ: a pair
            // handed over crossed would compile, paint identically, and be wrong only to the ear. The
            // numbers are the fixture's own, spelt out rather than read back off it, so a case that
            // resolved every kind through one accessor could not agree with itself.
            var soundScheme = SidebarStyles.buildSidebarSoundScheme();

            assertThat(soundScheme.resolvePointerArrivalCueFor(PointerArrivalTarget.PANEL_CHROME))
                .isEqualTo(new UiSoundCue(StarsectorUiSound.BUTTON_MOUSEOVER, 0.8f));
            assertThat(soundScheme.resolvePointerArrivalCueFor(
                    PointerArrivalTarget.SINGLE_OPTION_CONTROL))
                .isEqualTo(new UiSoundCue(StarsectorUiSound.BUTTON_MOUSEOVER, 0.6f));
            assertThat(soundScheme.resolvePointerArrivalCueFor(PointerArrivalTarget.LISTED_ITEM))
                .isEqualTo(new UiSoundCue(StarsectorUiSound.BUTTON_MOUSEOVER, 0.3f));
        }

        @Test
        void buildSidebarSoundSchemeConfirmsAPressAtTheEnginesOwnLevel() {
            // The one moment with no slider behind it: a press is a single act the player asked for, so
            // the case for quietening it never arises and it keeps vanilla's balance whatever the
            // arrival levels are set to.
            assertThat(SidebarStyles.buildSidebarSoundScheme().pressCue())
                .isEqualTo(new UiSoundCue(StarsectorUiSound.BUTTON_PRESSED, 1f));
        }

        @Test
        void buildSidebarSoundSchemeScrollsItsListAtItsOwnSettingsLevel() {
            // The wheel's level is its own slider and not part of the arrival balance: the player turned
            // the wheel once however far the list travelled, where the arrival levels answer to how many
            // things one sweep of the pointer crosses. Its number differs from all three of those, so a
            // composition reaching into the balance for it records a level nobody set for the wheel.
            assertThat(SidebarStyles.buildSidebarSoundScheme().listScrollCue())
                .isEqualTo(new UiSoundCue(
                    StarsectorUiSound.LIST_SCROLLED,
                    SidebarSettingsMock.LIST_SCROLL_VOLUME));
        }

        @Test
        void buildSidebarSoundSchemeNamesNoScrollCueAtAllWhenItsLevelIsSilenced() {
            // Silence stated by naming no cue rather than by playing one at nothing, the rule the arrivals
            // beside it answer to - and read off the slider alone, this moment having one level of its own
            // rather than a balance to weigh.
            sidebarSettingsMock.setListScrollVolume(0f);

            assertThat(SidebarStyles.buildSidebarSoundScheme().listScrollCue())
                .isNull();
        }

        @Test
        void buildSidebarSoundSchemeNamesNoArrivalRoleAtAllWhenEveryLevelIsSilenced() {
            // A player who has pulled the whole balance down has asked for a panel that is quiet under
            // the pointer, and a look states that by naming no role - a cue at zero is still a sound
            // played, which reads as wiring that half worked rather than as a panel deliberately quiet.
            sidebarSettingsMock.silenceEveryArrival();

            assertThat(SidebarStyles.buildSidebarSoundScheme()
                    .resolvePointerArrivalCueFor(PointerArrivalTarget.PANEL_CHROME))
                .isNull();
        }

        @Test
        void buildSidebarSoundSchemeStillSoundsWhileAnyOneLevelIsAudible() {
            // The other side of the rule above, and the reason silence is read off the whole balance:
            // one role covers every arrival, so a look silenced because one slider reached the bottom
            // would take the two still set with it.
            sidebarSettingsMock.setArrivalVolumes(new PointerArrivalVolumes(0f, 0f, LAST_AUDIBLE_VOLUME));

            assertThat(SidebarStyles.buildSidebarSoundScheme()
                    .resolvePointerArrivalCueFor(PointerArrivalTarget.LISTED_ITEM))
                .isEqualTo(new UiSoundCue(StarsectorUiSound.BUTTON_MOUSEOVER, LAST_AUDIBLE_VOLUME));
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
        void buildChromeFramedStyleStillWashesAHoveredControlInTheSchemesOwnBase() {
            // The wash follows the controls rather than the frame, for the reason the accents do: what a
            // panel abuts decides how it is framed and nothing about how its own controls answer a pointer.
            assertThat(buildChromeFramedStyle().controlHoverWash().colour())
                .isEqualTo(StarsectorUiColoursMock.BUTTON_TEXT);
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
        void buildStripTabStyleLettersTheRowInTheMapsOwnCondensedOrbitronAtItsNativeSize() {
            // The face the vanilla tabs a strip sits beneath are actually set in, at the size its atlas
            // draws at. Pinned because the strip was lettered in the title orbitron on the belief that
            // vanilla used it here, and the size alone cannot catch that: both faces land at 15, and only
            // the atlas tells them apart.
            var face = SidebarStyles.buildStripTabStyle(HEADER_BAND_HEIGHT).face();

            assertThat(face.font())
                .isEqualTo(StarsectorFont.VANILLA_ORBITRON_12_CONDENSED);
            assertThat(face.size())
                .isEqualTo(15d);
        }

        @Test
        void buildStripTabStyleStandsItsTabsInTheSectorMapsOwnBox() {
            // The engine's own map tabs (com.fs.starfarer.coreui.A.G): a 130 x 18 box parted from its
            // neighbour by a pixel. Literal values rather than a reference to the constants that produced
            // them, so a box re-dimensioned in passing fails here instead of agreeing with itself.
            assertThat(SidebarStyles.buildStripTabStyle(HEADER_BAND_HEIGHT).tabBox())
                .isEqualTo(new TabBox(130f, 18f, 1f));
        }

        @Test
        void buildStripTabStyleLeavesItsLabelsUnringed() {
            // The ring answers to what the text stands over, not to what it is lettered in: this row's
            // tabs are opaque surfaces of their own, so its labels already have a fill of known shade
            // behind them and a ring would only muddy them. Both chromes letter in hard-edged atlases,
            // so a claim resting on the face would hold for the button row too and prove nothing.
            assertThat(SidebarStyles.buildStripTabStyle(HEADER_BAND_HEIGHT).textHalo().isHaloDrawn())
                .isFalse();
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
        void buildRaisedButtonTabStyleLeavesItsTabsSnappedToTheirLabels() {
            // The paired claim to the strip's fixed box: this chrome lays its buttons inside the tabs the
            // layout measured and takes its own channel from within them, so a fixed box reaching it would
            // resize a row that already had its geometry settled.
            assertThat(SidebarStyles.buildRaisedButtonTabStyle(HEADER_BAND_HEIGHT).tabBox())
                .isEqualTo(TabBox.SNAPPED);
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
            // The face the intel screen's own map toggles are lettered in, and at the size its atlas draws
            // 1:1 at - the nine its line height states, not the ten its name carries. A pixel face is crisp
            // at one size only, so a row copying those buttons at any other would read as a blurred
            // imitation of the row beside it.
            var face = SidebarStyles.buildRaisedButtonTabStyle(HEADER_BAND_HEIGHT).face();

            assertThat(face.font())
                .isEqualTo(StarsectorFont.VANILLA_VICTOR_10);
            assertThat(face.size())
                .isEqualTo(9d);
        }

        @Test
        void buildRaisedButtonTabStyleRingsItsPixelFaceInBlack() {
            // The pair the face comes in: this chrome is lettered in a hard-edged bitmap face standing
            // over whatever the visor is showing, so its strokes need an edge of their own. A row taking
            // the face without the ring reads thin against a nebula.
            var textHalo = SidebarStyles.buildRaisedButtonTabStyle(HEADER_BAND_HEIGHT).textHalo();

            assertThat(textHalo.isHaloDrawn())
                .isTrue();
            assertThat(textHalo.colour())
                .isEqualTo(Color.BLACK);
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
