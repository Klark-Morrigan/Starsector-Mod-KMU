package kmu.maplayers.base.chrome.arrange;

import kmu.maplayers.base.layer.MapLayer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the arranging dialog is actually doing, none of which is checkable by clicking a button in
 * a running game.
 *
 * <p>That hidden layers are rows like any other. The dialog is the only way a tab comes back, so a
 * column showing only what is on the bar would be a control with no undo.
 *
 * <p>That the last visible row's box refuses. A bar with no tabs has no way back to itself, this dialog
 * being opened from that bar - the same guard the row assembly keeps against a hand-edited store, made
 * here so the player is never offered the click at all.
 *
 * <p>That a refused move and a refused toggle record nothing. The bar behind the dialog follows every
 * write, so a write that changed nothing would still be a write - and one that recorded the row order
 * as it stood would quietly place every layer the player had never arranged.
 *
 * <p>And that every accepted change records at once. There is no draft and no confirm here: what the
 * player is looking at behind the dialog is the thing they are arranging.
 */
final class MapLayerArrangementEditorTest {

    private final MapLayer alphaLayerMock = mock(MapLayer.class);
    private final MapLayer betaLayerMock = mock(MapLayer.class);
    private final MapLayer gammaLayerMock = mock(MapLayer.class);

    private final ArrangementSelectionFake arrangementSelectionFake = new ArrangementSelectionFake();

    // The roster as three mods registered it, left to right, each with the label its tab reads.
    private List<MapLayer> rosterLayers;

    @BeforeEach
    void registerThreeLayersInLoadOrder() {

        when(alphaLayerMock.getId()).thenReturn("alpha");
        when(alphaLayerMock.resolveTabLabelText()).thenReturn("Alpha");
        when(betaLayerMock.getId()).thenReturn("beta");
        when(betaLayerMock.resolveTabLabelText()).thenReturn("Beta");
        when(gammaLayerMock.getId()).thenReturn("gamma");
        when(gammaLayerMock.resolveTabLabelText()).thenReturn("Gamma");

        rosterLayers = List.of(alphaLayerMock, betaLayerMock, gammaLayerMock);
    }

    @Nested
    class GetRows {

        @Test
        void getRowsFollowsTheStoredOrderAndCarriesEachTabsLabel() {

            arrangementSelectionFake.holdArrangement(List.of("gamma", "alpha"), List.of());

            assertThat(buildEditor().getRows())
                .containsExactly(
                    new MapLayerArrangementRow("gamma", "Gamma", false),
                    new MapLayerArrangementRow("alpha", "Alpha", false),
                    new MapLayerArrangementRow("beta", "Beta", false));
        }

        @Test
        void getRowsShowsALayerWhoseTabIsOffTheBar() {
            // The dialog is the only way that tab comes back, so it has to be on show here.
            arrangementSelectionFake.holdArrangement(List.of(), List.of("beta"));

            assertThat(buildEditor().getRows())
                .contains(new MapLayerArrangementRow("beta", "Beta", true));
        }
    }

    @Nested
    class MoveRowUp {

        @Test
        void moveRowUpSwapsTheRowWithTheOneAboveIt() {

            var editor = buildEditor();

            editor.moveRowUp("beta");

            assertThat(rowIdsOf(editor))
                .containsExactly("beta", "alpha", "gamma");
        }

        @Test
        void moveRowUpRecordsTheWholeRowOrder() {

            var editor = buildEditor();

            editor.moveRowUp("gamma");

            assertThat(arrangementSelectionFake.getRecordedArrangement().orderedLayerIds())
                .containsExactly("alpha", "gamma", "beta");
        }

        @Test
        void moveRowUpLeavesTheLeadingRowWhereItIs() {

            var editor = buildEditor();

            editor.moveRowUp("alpha");

            assertThat(rowIdsOf(editor))
                .containsExactly("alpha", "beta", "gamma");
            assertThat(arrangementSelectionFake.getRecordedArrangement())
                .isNull();
        }
    }

    @Nested
    class MoveRowDown {

        @Test
        void moveRowDownSwapsTheRowWithTheOneBelowIt() {

            var editor = buildEditor();

            editor.moveRowDown("alpha");

            assertThat(rowIdsOf(editor))
                .containsExactly("beta", "alpha", "gamma");
        }

        @Test
        void moveRowDownLeavesTheLastRowWhereItIs() {

            var editor = buildEditor();

            editor.moveRowDown("gamma");

            assertThat(rowIdsOf(editor))
                .containsExactly("alpha", "beta", "gamma");
            assertThat(arrangementSelectionFake.getRecordedArrangement())
                .isNull();
        }
    }

    @Nested
    class CanMoveRowUp {

        @Test
        void canMoveRowUpIsFalseForTheLeadingRow() {
            assertThat(buildEditor().canMoveRowUp("alpha"))
                .isFalse();
        }

        @Test
        void canMoveRowUpIsTrueForARowWithSomethingAboveIt() {
            assertThat(buildEditor().canMoveRowUp("beta"))
                .isTrue();
        }
    }

    @Nested
    class CanMoveRowDown {

        @Test
        void canMoveRowDownIsFalseForTheLastRow() {
            assertThat(buildEditor().canMoveRowDown("gamma"))
                .isFalse();
        }

        @Test
        void canMoveRowDownIsTrueForARowWithSomethingBelowIt() {
            assertThat(buildEditor().canMoveRowDown("beta"))
                .isTrue();
        }
    }

    @Nested
    class ToggleRowHidden {

        @Test
        void toggleRowHiddenTakesATabOffTheBarAndRecordsIt() {

            var editor = buildEditor();

            editor.toggleRowHidden("beta");

            assertThat(arrangementSelectionFake.getRecordedArrangement().hiddenLayerIds())
                .containsExactly("beta");
        }

        @Test
        void toggleRowHiddenPutsATabBackOnTheBar() {

            arrangementSelectionFake.holdArrangement(List.of(), List.of("beta"));
            var editor = buildEditor();

            editor.toggleRowHidden("beta");

            assertThat(arrangementSelectionFake.getRecordedArrangement().hiddenLayerIds())
                .isEmpty();
        }

        @Test
        void toggleRowHiddenRefusesTheLastRowStillOnTheBar() {
            // What it would leave is a bar with no tabs, and this dialog is opened from that bar.
            arrangementSelectionFake.holdArrangement(List.of(), List.of("beta", "gamma"));
            var editor = buildEditor();

            editor.toggleRowHidden("alpha");

            assertThat(editor.getRows())
                .contains(new MapLayerArrangementRow("alpha", "Alpha", false));
            assertThat(arrangementSelectionFake.getRecordedArrangement())
                .isNull();
        }
    }

    @Nested
    class CanToggleRowHidden {

        @Test
        void canToggleRowHiddenIsFalseForTheLastRowStillOnTheBar() {

            arrangementSelectionFake.holdArrangement(List.of(), List.of("beta", "gamma"));

            assertThat(buildEditor().canToggleRowHidden("alpha"))
                .isFalse();
        }

        @Test
        void canToggleRowHiddenIsTrueForAHiddenRowWhateverElseIsHidden() {
            // Only hiding is ever refused. Putting a tab back can only ever make the bar more usable.
            arrangementSelectionFake.holdArrangement(List.of(), List.of("beta", "gamma"));

            assertThat(buildEditor().canToggleRowHidden("gamma"))
                .isTrue();
        }

        @Test
        void canToggleRowHiddenIsFalseForALayerTheDialogIsNotShowing() {
            // Reached by holding a control from a column that has since been rebuilt over a roster the
            // layer has left.
            assertThat(buildEditor().canToggleRowHidden("delta"))
                .isFalse();
        }
    }

    private MapLayerArrangementEditor buildEditor() {
        return new MapLayerArrangementEditor(arrangementSelectionFake, rosterLayers);
    }

    private static List<String> rowIdsOf(MapLayerArrangementEditor editor) {

        return editor.getRows().stream()
            .map(MapLayerArrangementRow::layerId)
            .toList();
    }
}
