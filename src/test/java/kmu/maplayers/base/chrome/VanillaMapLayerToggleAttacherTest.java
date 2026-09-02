package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmlib.testfixtures.starsector.ui.map.controls.MapFilterButtonFake;
import kmlib.testfixtures.starsector.ui.map.controls.MapFilterRowFake;

import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the three things the live attachment owes the box it stands: it opens showing what that
 * screen already holds, a click on it writes back what it now shows, and it carries the hover its
 * neighbours on the row do. The first two are what make the control read as the state of the
 * layers rather than as a switch of its own; the third is what makes it read as one of the row's.
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

            assertThat(isAttached)
                .isTrue();
            assertThat(readAppendedButton(rowFake).isChecked())
                .isTrue();
        }

        @Test
        void attachToggleToOpensTheBoxUntickedForAScreenHidingItsLayers() {

            var rowFake = ShownFilterRows.createRowFakeWithRoomToSpare();
            var visibilityMock = mockVisibility(false);

            new VanillaMapLayerToggleAttacher()
                .attachToggleTo(ShownFilterRows.createRowOver(rowFake), visibilityMock);

            // Seeded from the save rather than left at whatever a fresh button starts at, which
            // would show a ticked box over an empty map.
            assertThat(readAppendedButton(rowFake).isChecked())
                .isFalse();
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
            verify(visibilityMock)
                .showLayers(true);
        }

        @Test
        void attachToggleToGivesTheBoxTheHoverItsNeighboursCarry() {

            var rowFake = ShownFilterRows.createRowFakeWithRoomToSpare();
            var elementMock = mock(TooltipMakerAPI.class);

            StarsectorSettingsFake.installSettings(() -> elementMock);
            try {
                new VanillaMapLayerToggleAttacher()
                    .attachToggleTo(ShownFilterRows.createRowOver(rowFake), mockVisibility(true));
            } finally {
                StarsectorSettingsFake.clearSettings();
            }

            // Six of the row's own eight buttons carry one, so a seventh without is the one thing
            // on the strip that does not behave like the rest. Pinned here rather than left to the
            // two suites either side of it: those cover hanging a hover and what it says, and this
            // is the only place that says the box gets one at all.
            verify(elementMock).addTooltipTo(
                any(TooltipMakerAPI.TooltipCreator.class),
                eq(readAppendedButton(rowFake)),
                eq(TooltipMakerAPI.TooltipLocation.ABOVE));
        }

        @Test
        void attachToggleToWritesNothingToARowWithNoRoomLeft() {

            var rowFake = ShownFilterRows.createFullRowFake();
            var visibilityMock = mock(MapLayerVisibility.class);

            var isAttached = new VanillaMapLayerToggleAttacher()
                .attachToggleTo(ShownFilterRows.createRowOver(rowFake), visibilityMock);

            // First come: a row somebody else has filled is left as it was found, and the pick is
            // not even read, there being no box to open at it.
            assertThat(isAttached)
                .isFalse();
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
