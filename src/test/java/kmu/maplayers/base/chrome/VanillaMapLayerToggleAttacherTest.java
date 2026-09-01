package kmu.maplayers.base.chrome;

import kmlib.testfixtures.starsector.ui.map.controls.MapFilterButtonFake;
import kmlib.testfixtures.starsector.ui.map.controls.MapFilterRowFake;

import kmu.maplayers.base.layer.MapLayerVisibility;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

    // Where the appended control lands on a row carrying the game's own buttons.
    private static final int APPENDED_BUTTON_INDEX = ShownFilterRows.VANILLA_BUTTON_COUNT;

    @Nested
    class AttachToggleTo {

        @Test
        void attachToggleToOpensTheBoxTickedForAScreenShowingItsLayers() {

            var rowFake = ShownFilterRows.createRowFakeWithRoomToSpare();
            var visibilityMock = mockVisibility(true);

            var isAttached = new VanillaMapLayerToggleAttacher()
                .attachToggleTo(ShownFilterRows.createRowOver(rowFake), visibilityMock);

            assertThat(isAttached).isTrue();
            assertThat(readAppendedButton(rowFake).isChecked()).isTrue();
        }

        @Test
        void attachToggleToOpensTheBoxUntickedForAScreenHidingItsLayers() {

            var rowFake = ShownFilterRows.createRowFakeWithRoomToSpare();
            var visibilityMock = mockVisibility(false);

            new VanillaMapLayerToggleAttacher()
                .attachToggleTo(ShownFilterRows.createRowOver(rowFake), visibilityMock);

            // Seeded from the save rather than left at whatever a fresh button starts at, which
            // would show a ticked box over an empty map.
            assertThat(readAppendedButton(rowFake).isChecked()).isFalse();
        }

        @Test
        void attachToggleToMovesTheScreensPickToWhatTheBoxShowsWhenItIsClicked() {

            var rowFake = ShownFilterRows.createRowFakeWithRoomToSpare();
            var visibilityMock = mockVisibility(false);

            new VanillaMapLayerToggleAttacher()
                .attachToggleTo(ShownFilterRows.createRowOver(rowFake), visibilityMock);

            readAppendedButton(rowFake).click();

            // What the box now shows, read back off the button rather than assumed from the click,
            // since the button flips its own state before reporting.
            verify(visibilityMock).showLayers(true);
        }

        @Test
        void attachToggleToWritesNothingToARowWithNoRoomLeft() {

            var rowFake = ShownFilterRows.createFullRowFake();
            var visibilityMock = mock(MapLayerVisibility.class);

            var isAttached = new VanillaMapLayerToggleAttacher()
                .attachToggleTo(ShownFilterRows.createRowOver(rowFake), visibilityMock);

            // First come: a row somebody else has filled is left as it was found, and the pick is
            // not even read, there being no box to open at it.
            assertThat(isAttached).isFalse();
            assertThat(rowFake.getChildrenCopy())
                .hasSize(ShownFilterRows.VANILLA_BUTTON_COUNT);
            verifyNoInteractions(visibilityMock);
        }
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
}
