package kmu.maplayers.base.sidebar.style;

import kmu.settings.NotchChevronColourChoice;
import kmu.settings.SidebarColourSchemeChoice;
import kmu.starsector.StarsectorUiColoursMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SidebarPalettes}: which live shade each of the player's colour choices means. The panel's
 * three accent steps follow the colour scheme, and the collapse handle's chevron follows its own - the
 * gold holding one shade across rest and hover while the panel-accent choice steps up under the pointer.
 *
 * <p>Every case reads through the shared palette mock rather than stubbing its own shades, because what
 * these lookups can get wrong is which role they reached for: two roles answering with one shade would
 * pass a lookup that took the other.
 */
final class SidebarPalettesTest {

    // The chevron's arguments. Literal rather than read from the mock: the notch lookup is handed the
    // panel's accents rather than resolving them, so a case pinning that it passes them through wants
    // shades no live role could have supplied.
    private static final Color ACCENT = new Color(170, 222, 255);
    private static final Color BRIGHT_ACCENT = new Color(255, 255, 255);

    private StarsectorUiColoursMock uiColoursMock;

    @BeforeEach
    void mockLiveColours() {
        uiColoursMock = StarsectorUiColoursMock.install();
    }

    @AfterEach
    void closeLiveColours() {
        uiColoursMock.close();
    }

    @Nested
    class ResolveAccentColours {

        @Test
        void resolveAccentColoursTakesTheEnginesButtonRolesForTheUiPaletteChoice() {
            // The scheme the panel ships on: the dark fill a vanilla button rests and frames in, the
            // button text every vanilla button and tab label is written in, and the near-white tooltip
            // title above it - the same three roles the engine builds its own controls from. None moves
            // when a player faction recolours, which is the whole of what this choice buys.
            var accents = SidebarPalettes.resolveAccentColours(SidebarColourSchemeChoice.UI_PALETTE);

            assertThat(accents.dark())
                .isEqualTo(StarsectorUiColoursMock.BUTTON_BG_DARK);
            assertThat(accents.base())
                .isEqualTo(StarsectorUiColoursMock.BUTTON_TEXT);
            assertThat(accents.bright())
                .isEqualTo(StarsectorUiColoursMock.LIGHT_HIGHLIGHT);
        }

        @Test
        void resolveAccentColoursTakesTheNeutralGreysForTheChromeGreyChoice() {
            // No accent hue at all: the frame grey the engine draws its own panels in, stepping up to
            // the lighter body-text grey for a tick. Its dark step is that same grey sunk toward black,
            // this being the one scheme with no engine shade to take - the fixed palette's dark role is
            // the button teal, and a tint is what this choice exists to drop. Spelt as the channel
            // values the step lands on rather than as the rule that produced them, so re-dialling the
            // depth is a decision this case makes visible instead of one it agrees with. The alpha is
            // half of what it pins: a dark step here is drawn over a live visor, so sinking the grey
            // must leave its translucency where it stood rather than returning a solid bar.
            var accents = SidebarPalettes.resolveAccentColours(SidebarColourSchemeChoice.CHROME_GREY);

            assertThat(accents.dark())
                .isEqualTo(new Color(47, 47, 47, 180));
            assertThat(accents.base())
                .isEqualTo(StarsectorUiColoursMock.UI_GRAY);
            assertThat(accents.bright())
                .isEqualTo(StarsectorUiColoursMock.UI_TEXT);
        }

        @Test
        void resolveAccentColoursTakesThePlayerTrioForThePlayerFactionChoice() {
            // What the panel wore before the scheme was a choice, kept as a taste - and the one scheme
            // whose three steps the faction supplies itself, dark included.
            var accents = SidebarPalettes.resolveAccentColours(
                SidebarColourSchemeChoice.PLAYER_FACTION);

            assertThat(accents.dark())
                .isEqualTo(StarsectorUiColoursMock.PLAYER_DARK);
            assertThat(accents.base())
                .isEqualTo(StarsectorUiColoursMock.PLAYER_BASE);
            assertThat(accents.bright())
                .isEqualTo(StarsectorUiColoursMock.PLAYER_BRIGHT);
        }

        @Test
        void resolveAccentColoursLeavesEverySchemesDarkStepBelowItsOwnBase() {
            // The other half of the ordering below: a dark step that did not sit under its base would
            // stop a frame receding behind the controls it encloses, which is the whole of what that
            // step is for. Read on green for the same reason the bright case is.
            for (var choice : SidebarColourSchemeChoice.values()) {

                var accents = SidebarPalettes.resolveAccentColours(choice);

                assertThat(accents.dark().getGreen())
                    .as("dark step of %s", choice)
                    .isLessThan(accents.base().getGreen());
            }
        }

        @Test
        void resolveAccentColoursLeavesEverySchemesBrightStepAboveItsOwnBase() {
            // The pair has to stay a pair whichever scheme is picked: a bright step that did not stand
            // above its base would leave a checkbox's tick indistinguishable from the chrome it is drawn
            // on. Green is the channel every one of these shades carries most of, so it is the one that
            // shows an ordering broken by a pair swapped in any single case.
            for (var choice : SidebarColourSchemeChoice.values()) {

                var accents = SidebarPalettes.resolveAccentColours(choice);

                assertThat(accents.bright().getGreen())
                    .as("bright step of %s", choice)
                    .isGreaterThan(accents.base().getGreen());
            }
        }
    }

    @Nested
    class ResolveNotchColours {

        @Test
        void resolveNotchColoursTakesTheVanillaHighlightForTheGoldChoice() {
            var colours = SidebarPalettes.resolveNotchColours(
                    NotchChevronColourChoice.GOLD, ACCENT, BRIGHT_ACCENT);
            assertThat(colours.chevron()).isEqualTo(StarsectorUiColoursMock.HIGHLIGHT_GOLD);
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
