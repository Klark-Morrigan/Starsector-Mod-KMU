package kmu.conditions.ui.picker.render.paragraph;

import com.fs.starfarer.api.util.Misc;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;

/**
 * The game's theme colours held still, for a suite composing the picker's paragraphs outside a running game.
 * Distinct shades, so a run tinted in the wrong one can be told apart.
 */
final class PickerThemeColours {

    private PickerThemeColours() {
    }

    // The caller closes the stand-in it is handed.
    static MockedStatic<Misc> mockThemeColours() {

        var miscMock = Mockito.mockStatic(Misc.class);

        miscMock
            .when(Misc::getGrayColor)
            .thenReturn(Color.GRAY);
        miscMock
            .when(Misc::getHighlightColor)
            .thenReturn(Color.YELLOW);
        miscMock
            .when(Misc::getNegativeHighlightColor)
            .thenReturn(Color.RED);
        miscMock
            .when(Misc::getPositiveHighlightColor)
            .thenReturn(Color.GREEN);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(Color.WHITE);
        miscMock
            .when(Misc::getBasePlayerColor)
            .thenReturn(Color.BLUE);

        return miscMock;
    }
}
