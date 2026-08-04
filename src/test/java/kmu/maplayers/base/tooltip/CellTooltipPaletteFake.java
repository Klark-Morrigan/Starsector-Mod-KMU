package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.util.Misc;

import kmu.starsector.StarsectorSettingsFake;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;

/**
 * The palette a cell tooltip resolves its colours from, stood in for the game's. Every shade a hover
 * box draws in comes from the same handful of vanilla statics, so a test asserting on any of them has
 * to stand all of them up first - and each suite writing that fixture itself restates both the values
 * and the order the statics must be installed in.
 *
 * <p>Held as one fixture because the values are expectations as well as inputs: a suite asserts that a
 * line drew in the shade it was given, so a copy of the palette per suite is a pair of numbers that can
 * drift from the pair another suite is asserting the same line against, while each copy goes on
 * agreeing with itself.
 *
 * <p>The whole palette is installed whatever a suite reads, since a box resolves colours it never
 * asserts on while building its style, and an unstubbed shade comes back null and fails the paint
 * rather than the assertion.
 */
public final class CellTooltipPaletteFake {

    /** The shade a top-tier line's label reads in. */
    public static final Color PLAYER_BRIGHT = new Color(200, 230, 255);

    /** The shade the box's frame draws in. */
    public static final Color PLAYER_BASE = new Color(100, 160, 200);

    /** The plain shade an ordinary line reads in. */
    public static final Color TEXT = Color.LIGHT_GRAY;

    /** The shade a called-out value or qualifier reads in. */
    public static final Color HIGHLIGHT = new Color(255, 200, 100);

    private static MockedStatic<Misc> miscMock;

    private CellTooltipPaletteFake() {
    }

    /**
     * Installs the settings proxy and the palette over it, in that order: {@code Misc}'s class
     * initialiser reads the settings, so mocking it against an uninstalled proxy fails on class load.
     */
    public static void installPalette() {
        StarsectorSettingsFake.installSettings();

        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getBrightPlayerColor)
            .thenReturn(PLAYER_BRIGHT);
        miscMock
            .when(Misc::getBasePlayerColor)
            .thenReturn(PLAYER_BASE);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(TEXT);
        miscMock
            .when(Misc::getHighlightColor)
            .thenReturn(HIGHLIGHT);
    }

    /** Takes the palette and the settings proxy back down, so a suite leaves no statics mocked. */
    public static void clearPalette() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }
}
