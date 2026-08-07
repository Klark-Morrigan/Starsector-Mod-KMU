package kmu.starsector;

import com.fs.starfarer.api.util.Misc;

import org.mockito.MockedStatic;

import java.awt.Color;

import static org.mockito.Mockito.mockStatic;

/**
 * Stands in for the engine's live UI colours: the {@code Misc} shades a look resolves through and the
 * named settings keys behind the rest, installed together and closed together. A subject built from the
 * running game's palette cannot be reached under the test JVM without both halves, and a case that
 * installed only one gets a null shade from whichever accessor it forgot.
 *
 * <p>Every shade below is a different colour, deliberately. A case pins a wiring - which live read
 * reaches which part of the look - and it can only pin that if the two reads it is telling apart answer
 * differently: setup that hands two roles one shade passes a subject that took the wrong one. Keeping
 * them apart here rather than per case is also what stops two files disagreeing about which shade stands
 * for which role, which would leave each file's cases sound only against its own setup.
 *
 * <p>Colour reads alone. Whatever the subject makes of them - which chevron the player picked, which
 * band height a screen stands at - is the case's own to name, since those are choices a case may want to
 * vary while the palette underneath holds still.
 */
public final class StarsectorUiColoursMock implements AutoCloseable {

    /** The player faction's base accent, the shade its chrome and controls are ruled in. */
    public static final Color PLAYER_BASE = new Color(170, 222, 255);

    /** Its brighter step, taken by a lit label or a ticked box. */
    public static final Color PLAYER_BRIGHT = new Color(200, 240, 255);

    /** The fixed UI grey the engine frames its own panels in, which no player faction moves. */
    public static final Color UI_GRAY = new Color(155, 155, 155);

    /** The lighter grey the engine writes its plain body text in, a step above {@link #UI_GRAY}. */
    public static final Color UI_TEXT = new Color(220, 220, 220);

    /** The blue the engine writes its button and tab labels in. */
    public static final Color BUTTON_TEXT = new Color(130, 200, 230);

    /**
     * The near-white the engine titles its tooltips in - the fixed palette's bright step above
     * {@link #BUTTON_TEXT}. Named rather than left to {@link #ENGINE_UI_SHADE} because it is one half of
     * an accent pair, and a case pinning which half went where needs the two to differ.
     */
    public static final Color LIGHT_HIGHLIGHT = new Color(203, 245, 255);

    /** The gold an emphasised word reads in. */
    public static final Color HIGHLIGHT_GOLD = new Color(255, 255, 175);

    /**
     * What every other named engine colour key answers with - {@code buttonBgDark},
     * {@code buttonShortcut}, and the rest. One shade for all of them because a case pinning a
     * particular key names it itself; this is the floor that keeps an unnamed key from resolving null
     * and failing the whole look.
     */
    public static final Color ENGINE_UI_SHADE = new Color(100, 100, 100);

    // The engine key the light highlight is stored under, so the proxy can answer that one apart from
    // the floor above. Spelt here rather than reached for through the palette enum: this fake stands in
    // for the engine, so it answers keys the way the engine stores them.
    private static final String LIGHT_HIGHLIGHT_KEY = "tooltipTitleAndLightHighlightColor";

    private final MockedStatic<Misc> miscMock;

    private StarsectorUiColoursMock(MockedStatic<Misc> miscMock) {
        this.miscMock = miscMock;
    }

    /**
     * Installs the palette: the {@code Misc} accessors answer with the shades above and the settings
     * proxy answers every named colour key. Close the result to take both back down - a leaked static
     * mock fails the next case in the class to touch the same type.
     *
     * @return the installed palette, to be closed when the case is done with it
     */
    public static StarsectorUiColoursMock install() {

        StarsectorSettingsFake.installSettings(key -> LIGHT_HIGHLIGHT_KEY.equals(key)
            ? LIGHT_HIGHLIGHT
            : ENGINE_UI_SHADE);

        var miscMock = mockStatic(Misc.class);

        miscMock
            .when(Misc::getBasePlayerColor)
            .thenReturn(PLAYER_BASE);
        miscMock
            .when(Misc::getBrightPlayerColor)
            .thenReturn(PLAYER_BRIGHT);
        miscMock
            .when(Misc::getGrayColor)
            .thenReturn(UI_GRAY);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(UI_TEXT);
        miscMock
            .when(Misc::getButtonTextColor)
            .thenReturn(BUTTON_TEXT);
        miscMock
            .when(Misc::getHighlightColor)
            .thenReturn(HIGHLIGHT_GOLD);

        return new StarsectorUiColoursMock(miscMock);
    }

    @Override
    public void close() {

        miscMock.close();

        StarsectorSettingsFake.clearSettings();
    }
}
