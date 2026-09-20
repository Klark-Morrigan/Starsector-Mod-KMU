package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.controls.specs.ControlSpec;
import kmlib.starsector.ui.widgets.tabs.BandButtonSpec;
import kmlib.starsector.ui.widgets.tabs.style.TabBox;

import kmu.maplayers.base.sidebar.style.SidebarStyles;
import kmu.settings.SidebarSettingsMock;
import kmu.starsector.StarsectorUiColoursMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the bar's opener is asked for: no lit cell, no word, and no tint of its own - the last two
 * being what leave the mark as the whole of the control and the row's own shade as the whole of the
 * mark's colour.
 *
 * <p>And the box it stands in, which under a test is always the fallback: no sector, so the sprite never
 * resolves and the square is what the button is boxed at.
 */
final class BarOpenersTest {

    // The sector map's own tab row, which the opener is measured against: a box wide enough for the
    // longest layer name, so a button inheriting it would stand several times wider than its mark.
    private static final float HOST_BAND_HEIGHT = 19f;
    private static final float HOST_TAB_WIDTH = 130f;
    private static final float HOST_TAB_HEIGHT = 18f;
    private static final float HOST_TAB_GAP = 1f;

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
    class BuildOpenerSpec {

        @Test
        void buildOpenerSpecLightsNoCellOfItsOwn() {
            // A tabs control's lit cell is inert, so a button that was ever the lit one would stop
            // answering presses - and it is also what the mark's shade is resolved against, so a button
            // reading as selected would wear the row's shown look with nothing to be showing.
            assertThat(buildOpener().spec().selectedIndex())
                .isEqualTo(ControlSpec.NO_SELECTION);
        }

        @Test
        void buildOpenerSpecLettersNothingOnTheButton() {
            // The picture is the whole of it: the bar the button arranges is right beside it, so a word
            // would only repeat what the mark says, and it would need a bundle entry to say so in every
            // language the game ships in.
            assertThat(buildOpener().spec().labels())
                .containsExactly("");
        }

        @Test
        void buildOpenerSpecStatesNoTintOfItsOwnForTheMark() {
            // Load-bearing rather than incidental: the mark fills the button, so it is what has to answer
            // the pointer, and the shade it travels to is the row's own. A colour named here would be
            // multiplied into that shade, leaving a mark the strip could not light through.
            assertThat(buildOpener().icon().tintColour())
                .isNull();
        }

        @Test
        void buildOpenerSpecCarriesTheMarkItIsPressedFor() {

            assertThat(buildOpener().icon().spritePath())
                .isEqualTo("graphics/factions/storage.png");
        }

        @Test
        void buildOpenerSpecSquaresTheBoxForAnAssetThatWillNotResolve() {
            // A pressable control one tab-height square with nothing drawn in it, where a zero width would
            // be a control that had silently left the bar.
            assertThat(buildOpener().style().tabBox().width())
                .isEqualTo(HOST_TAB_HEIGHT);
        }
    }

    // The host row the opener is asked to stand in: the strip's own style, boxed as the sector map boxes
    // it, so the button's box is measured against a row several times wider than the mark.
    private static BandButtonSpec buildOpener() {
        return BarOpeners.buildOpenerSpec(
            SidebarStyles.buildStripTabStyle(HOST_BAND_HEIGHT)
                .withTabBox(new TabBox(HOST_TAB_WIDTH, HOST_TAB_HEIGHT, HOST_TAB_GAP)));
    }
}
