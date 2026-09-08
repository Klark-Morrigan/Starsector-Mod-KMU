package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmu.maplayers.base.layer.MapLayer;
import kmu.starsector.StarsectorUiColoursMock;
import kmu.starsector.ui.ParagraphLabelMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what a row shows rather than says, which is the whole of what a reader cannot get from the
 * widget calls: both shades compile, and a box with a word in it compiles too.
 *
 * <p>The muting is held against the row's own state and never against what the editor will allow.
 * Those two part company on exactly one row - the last one still on the bar, whose box is refused
 * because taking it off would leave no way back to this dialog - and that row is the one a rule
 * reading the editor would wrongly grey out.
 */
final class ArrangementRowWidgetsTest {

    // The engine's palette, held so the muted shade can be told from the ordinary one. Which shade
    // stands for which role is settled in the fixture rather than per case.
    private StarsectorUiColoursMock uiColoursMock;

    @BeforeEach
    void installStarsectorUiColours() {

        uiColoursMock = StarsectorUiColoursMock.install();
    }

    @AfterEach
    void clearStarsectorUiColours() {

        uiColoursMock.close();
    }

    @Nested
    class AddRowLabel {

        @Test
        void addRowLabelMutesARowWhoseTabIsOffTheBar() {

            var labelCellMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(labelCellMock);

            ArrangementRowWidgets.addRowLabel(
                labelCellMock,
                new MapLayerArrangementRow("alpha", "Alpha", true));

            verify(labelCellMock)
                .addPara(eq("Alpha"), eq(StarsectorUiColoursMock.UI_GRAY), anyFloat());
        }

        @Test
        void addRowLabelDrawsARowWhoseTabIsOnTheBarInTheOrdinaryShade() {

            var labelCellMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(labelCellMock);

            ArrangementRowWidgets.addRowLabel(
                labelCellMock,
                new MapLayerArrangementRow("alpha", "Alpha", false));

            verify(labelCellMock)
                .addPara(eq("Alpha"), eq(StarsectorUiColoursMock.UI_TEXT), anyFloat());
        }

        @Test
        void addRowLabelDrawsTheLastRowLeftOnTheBarUnmuted() {
            // The one row where refused and off the bar come apart. Its box cannot be pressed, and it
            // is nonetheless the tab the player is looking at - so a rule reading the editor's refusal
            // instead of the row's own state would grey out the only tab still showing.
            var editor = buildEditorWithOnlyAlphaOnTheBar();
            var lastRowOnTheBar = editor.getRows().stream()
                .filter(row -> !row.isHidden())
                .findFirst()
                .orElseThrow();

            assertThat(editor.canToggleRowHidden(lastRowOnTheBar.layerId()))
                .isFalse();

            var labelCellMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(labelCellMock);

            ArrangementRowWidgets.addRowLabel(labelCellMock, lastRowOnTheBar);

            verify(labelCellMock)
                .addPara(eq("Alpha"), eq(StarsectorUiColoursMock.UI_TEXT), anyFloat());
        }
    }

    @Nested
    class AddShownBox {

        @Test
        void addShownBoxCarriesNoWord() {
            // The line over the column already says what the box does, so a word in the box would be
            // that sentence repeated once per row.
            var controlsCellMock = mock(TooltipMakerAPI.class);

            ArrangementRowWidgets.addShownBox(controlsCellMock);

            verify(controlsCellMock)
                .addAreaCheckbox(
                    eq(""),
                    any(),
                    any(Color.class),
                    any(Color.class),
                    any(Color.class),
                    anyFloat(),
                    anyFloat(),
                    anyFloat());
        }

        @Test
        void addShownBoxStandsSquareAtTheRowsControlHeight() {
            // With the word gone the box has nothing to be wide for, and a box wider than it is tall
            // would read as a button whose label failed to load.
            var controlsCellMock = mock(TooltipMakerAPI.class);

            ArrangementRowWidgets.addShownBox(controlsCellMock);

            verify(controlsCellMock)
                .addAreaCheckbox(
                    any(),
                    any(),
                    any(Color.class),
                    any(Color.class),
                    any(Color.class),
                    eq(20f),
                    eq(20f),
                    anyFloat());
        }
    }

    // A two-layer bar with one tab taken off it, so the tab that is left is the one the editor refuses
    // to hide. Built through the real editor rather than posed as a row, the refusal being the editor's
    // rule and the point of the case being that the two answers are read from different places.
    private MapLayerArrangementEditor buildEditorWithOnlyAlphaOnTheBar() {

        var alphaLayerMock = mock(MapLayer.class);
        when(alphaLayerMock.getId()).thenReturn("alpha");
        when(alphaLayerMock.resolveTabLabelText()).thenReturn("Alpha");

        var betaLayerMock = mock(MapLayer.class);
        when(betaLayerMock.getId()).thenReturn("beta");
        when(betaLayerMock.resolveTabLabelText()).thenReturn("Beta");

        var arrangementSelectionFake = new ArrangementSelectionFake();
        arrangementSelectionFake.holdArrangement(List.of(), List.of("beta"));

        return new MapLayerArrangementEditor(
            arrangementSelectionFake,
            List.of(alphaLayerMock, betaLayerMock));
    }
}
