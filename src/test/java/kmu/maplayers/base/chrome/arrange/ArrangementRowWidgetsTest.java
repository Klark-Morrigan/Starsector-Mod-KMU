package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
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

            var labelElementMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(labelElementMock);

            ArrangementRowWidgets.addRowLabel(
                labelElementMock,
                new MapLayerArrangementRow("alpha", "Alpha", true));

            verify(labelElementMock)
                .addPara(eq("Alpha"), eq(StarsectorUiColoursMock.UI_GRAY), anyFloat());
        }

        @Test
        void addRowLabelDrawsARowWhoseTabIsOnTheBarInTheOrdinaryShade() {

            var labelElementMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(labelElementMock);

            ArrangementRowWidgets.addRowLabel(
                labelElementMock,
                new MapLayerArrangementRow("alpha", "Alpha", false));

            verify(labelElementMock)
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

            var labelElementMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(labelElementMock);

            ArrangementRowWidgets.addRowLabel(labelElementMock, lastRowOnTheBar);

            verify(labelElementMock)
                .addPara(eq("Alpha"), eq(StarsectorUiColoursMock.UI_TEXT), anyFloat());
        }
    }

    @Nested
    class AddShownBox {

        @Test
        void addShownBoxCarriesNoWord() {
            // The line over the column already says what the box does, so a word in the box would be
            // that sentence repeated once per row.
            var controlsElementMock = mock(TooltipMakerAPI.class);

            ArrangementRowWidgets.addShownBox(controlsElementMock);

            verify(controlsElementMock)
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
            var controlsElementMock = mock(TooltipMakerAPI.class);

            ArrangementRowWidgets.addShownBox(controlsElementMock);

            verify(controlsElementMock)
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

    @Nested
    class AddRowTo {

        @Test
        void addRowToDisablesUpOnTheLeadingRowAndLeavesDownPressable() {
            // The wiring from the editor's three answers to the three controls, which nothing else
            // pins: transpose the two move predicates and the buttons still build, still press, and
            // disable at the wrong ends of the column.
            var editor = buildThreeRowEditor();
            var row = editor.getRows().get(0);

            var builtRow = buildRowWidgets(editor, row);

            verify(builtRow.upButtonMock()).setEnabled(false);
            verify(builtRow.downButtonMock()).setEnabled(true);
        }

        @Test
        void addRowToDisablesDownOnTheLastRowAndLeavesUpPressable() {

            var editor = buildThreeRowEditor();
            var row = editor.getRows().get(2);

            var builtRow = buildRowWidgets(editor, row);

            verify(builtRow.upButtonMock()).setEnabled(true);
            verify(builtRow.downButtonMock()).setEnabled(false);
        }

        @Test
        void addRowToLeavesBothMoveButtonsPressableOnARowWithNeighboursEitherSide() {

            var editor = buildThreeRowEditor();
            var row = editor.getRows().get(1);

            var builtRow = buildRowWidgets(editor, row);

            verify(builtRow.upButtonMock()).setEnabled(true);
            verify(builtRow.downButtonMock()).setEnabled(true);
        }

        @Test
        void addRowToTicksTheBoxOfARowWhoseTabIsOnTheBar() {

            var editor = buildThreeRowEditor();
            var row = editor.getRows().get(0);

            var builtRow = buildRowWidgets(editor, row);

            verify(builtRow.shownBoxMock()).setChecked(true);
        }

        @Test
        void addRowToRefusesTheBoxOfTheLastRowStillOnTheBar() {
            // Taking it off would leave a bar with no tabs, and this dialog is opened from that bar -
            // so the box is built unpressable rather than the press being refused after the click.
            var editor = buildEditorWithOnlyAlphaOnTheBar();
            var lastRowOnTheBar = editor.getRows().stream()
                .filter(row -> !row.isHidden())
                .findFirst()
                .orElseThrow();

            var builtRow = buildRowWidgets(editor, lastRowOnTheBar);

            verify(builtRow.shownBoxMock()).setEnabled(false);
        }
    }

    // The three controls one call to addRowTo built, so a case can ask what each of them was told.
    private record BuiltRowMocks(
        ButtonAPI shownBoxMock,
        ButtonAPI upButtonMock,
        ButtonAPI downButtonMock) {
    }

    // Stands one row in a panel of mocks and hands back the controls it built. The row is drawn through
    // the real addRowTo rather than by calling the private wiring, so what is pinned is what a standing
    // dialog does.
    private static BuiltRowMocks buildRowWidgets(
            MapLayerArrangementEditor editor,
            MapLayerArrangementRow row) {

        var labelElementMock = mock(TooltipMakerAPI.class);
        ParagraphLabelMock.mockLabelOn(labelElementMock);
        var controlsElementMock = mock(TooltipMakerAPI.class);

        var boxMock = mock(CustomPanelAPI.class);
        when(boxMock.createUIElement(anyFloat(), anyFloat(), anyBoolean()))
            .thenReturn(labelElementMock, controlsElementMock);
        when(boxMock.addUIElement(any())).thenReturn(mock(PositionAPI.class));

        var shownBoxMock = mockButton();
        when(controlsElementMock.addAreaCheckbox(
                any(), any(), any(Color.class), any(Color.class), any(Color.class),
                anyFloat(), anyFloat(), anyFloat()))
            .thenReturn(shownBoxMock);

        // In the order the row adds them, which is also the order they stand in: Up, then Down.
        var upButtonMock = mockButton();
        var downButtonMock = mockButton();
        when(controlsElementMock.addButton(any(), any(), anyFloat(), anyFloat(), anyFloat()))
            .thenReturn(upButtonMock, downButtonMock);

        new ArrangementRowWidgets(editor, (layerId, action) -> {
        }).addRowTo(boxMock, row, 0f);

        return new BuiltRowMocks(shownBoxMock, upButtonMock, downButtonMock);
    }

    // A button that answers the placement call every control makes as it is laid beside its neighbour.
    private static ButtonAPI mockButton() {

        var buttonMock = mock(ButtonAPI.class);
        when(buttonMock.getPosition()).thenReturn(mock(PositionAPI.class));

        return buttonMock;
    }

    // Three layers, all on the bar, so the leading and last rows are the ones whose moves are refused.
    private MapLayerArrangementEditor buildThreeRowEditor() {

        return new MapLayerArrangementEditor(
            new ArrangementSelectionFake(),
            List.of(
                mockLayer("alpha", "Alpha"),
                mockLayer("beta", "Beta"),
                mockLayer("gamma", "Gamma")));
    }

    // A two-layer bar with one tab taken off it, so the tab that is left is the one the editor refuses
    // to hide. Built through the real editor rather than posed as a row, the refusal being the editor's
    // rule and the point of the case being that the two answers are read from different places.
    private MapLayerArrangementEditor buildEditorWithOnlyAlphaOnTheBar() {

        var arrangementSelectionFake = new ArrangementSelectionFake();
        arrangementSelectionFake.holdArrangement(List.of(), List.of("beta"));

        return new MapLayerArrangementEditor(
            arrangementSelectionFake,
            List.of(mockLayer("alpha", "Alpha"), mockLayer("beta", "Beta")));
    }

    // One registered layer, which a row needs for its ID and the label its tab reads.
    private static MapLayer mockLayer(String layerId, String tabLabel) {

        var layerMock = mock(MapLayer.class);
        when(layerMock.getId()).thenReturn(layerId);
        when(layerMock.resolveTabLabelText()).thenReturn(tabLabel);

        return layerMock;
    }
}
