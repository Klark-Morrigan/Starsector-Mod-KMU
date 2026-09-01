package kmu.maplayers.base.chrome;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.map.controls.MapFilterRow;
import kmlib.starsector.ui.map.controls.MapFilterRows;
import kmlib.starsector.ui.map.probes.EmbeddedMap;
import kmlib.testfixtures.starsector.ui.map.controls.FilteredMapWidgetFake;
import kmlib.testfixtures.starsector.ui.map.controls.MapFilterButtonFake;
import kmlib.testfixtures.starsector.ui.map.controls.MapFilterRowFake;

import kmu.maplayers.base.layer.MapLayerVisibility;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the two things the live attachment owes the pick it is handed: the box opens showing what
 * that screen already holds, and a click on it writes back what the box now shows. Both are what
 * make the control read as the state of the layers rather than as a switch of its own.
 */
final class VanillaMapLayerToggleAttacherTest {

    // How many of the game's own buttons every row here is built holding, which is also where the
    // appended control lands on it.
    private static final int VANILLA_BUTTON_COUNT = 2;
    private static final int APPENDED_BUTTON_INDEX = VANILLA_BUTTON_COUNT;

    // A row already filled to within a few units of its far edge, which is the state another mod
    // having appended to it first leaves it in.
    private static final Rectangle FULL_ROW_BOX = new Rectangle(0f, 0f, 250f, 25f);
    private static final float FULL_ROW_BUTTON_WIDTH = 120f;

    @Nested
    class AttachToggleTo {

        @Test
        void opensTheBoxTickedForAScreenShowingItsLayers() {

            var rowFake = buildSpaciousRowFake();
            var visibilityMock = mockVisibility(true);

            var isAttached = new VanillaMapLayerToggleAttacher()
                .attachToggleTo(wrapRow(rowFake), visibilityMock);

            assertThat(isAttached).isTrue();
            assertThat(readAppendedButton(rowFake).isChecked()).isTrue();
        }

        @Test
        void opensTheBoxUntickedForAScreenHidingItsLayers() {

            var rowFake = buildSpaciousRowFake();
            var visibilityMock = mockVisibility(false);

            new VanillaMapLayerToggleAttacher().attachToggleTo(wrapRow(rowFake), visibilityMock);

            // Seeded from the save rather than left at whatever a fresh button starts at, which
            // would show a ticked box over an empty map.
            assertThat(readAppendedButton(rowFake).isChecked()).isFalse();
        }

        @Test
        void movesTheScreensPickToWhatTheBoxShowsWhenItIsClicked() {

            var rowFake = buildSpaciousRowFake();
            var visibilityMock = mockVisibility(false);

            new VanillaMapLayerToggleAttacher().attachToggleTo(wrapRow(rowFake), visibilityMock);

            readAppendedButton(rowFake).click();

            // What the box now shows, read back off the button rather than assumed from the click,
            // since the button flips its own state before reporting.
            verify(visibilityMock).showLayers(true);
        }

        @Test
        void writesNothingToARowWithNoRoomLeft() {

            var rowFake = MapFilterRowFake.createRowOfSize(
                FULL_ROW_BOX, FULL_ROW_BUTTON_WIDTH, "Starscape", "Names");
            var visibilityMock = mock(MapLayerVisibility.class);

            var isAttached = new VanillaMapLayerToggleAttacher()
                .attachToggleTo(wrapRow(rowFake), visibilityMock);

            // First come: a row somebody else has filled is left as it was found, and the pick is
            // not even read, there being no box to open at it.
            assertThat(isAttached).isFalse();
            assertThat(rowFake.getChildrenCopy()).hasSize(VANILLA_BUTTON_COUNT);
            verifyNoInteractions(visibilityMock);
        }
    }

    // The game's own map strip, which has room to spare for one more control.
    private static MapFilterRowFake buildSpaciousRowFake() {
        return MapFilterRowFake.createMapScreenStrip("Starscape", "Names");
    }

    private static MapLayerVisibility mockVisibility(boolean areLayersShown) {

        var visibilityMock = mock(MapLayerVisibility.class);

        when(visibilityMock.areLayersShown())
            .thenReturn(areLayersShown);

        return visibilityMock;
    }

    private static MapFilterButtonFake readAppendedButton(MapFilterRowFake rowFake) {
        return (MapFilterButtonFake) rowFake.getChildrenCopy().get(APPENDED_BUTTON_INDEX);
    }

    // The row as the attachment meets it: reached off a map widget's own accessor, the way the
    // running game's is.
    private static MapFilterRow wrapRow(MapFilterRowFake rowFake) {

        return MapFilterRows.resolveEmbeddedMapFilterRow(
            new EmbeddedMap(new FilteredMapWidgetFake(rowFake), List.of()));
    }
}
