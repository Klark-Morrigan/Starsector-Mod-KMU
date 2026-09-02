package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmlib.testfixtures.starsector.ui.map.controls.MapFilterButtonFake;
import kmlib.testfixtures.starsector.ui.map.controls.MapFilterRowFake;

import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the four things the live attachment owes the box it stands: it opens showing what that
 * screen already holds, a click on it writes back what it now shows, it answers the key the player
 * has bound, and it carries the hover its neighbours on the row do. The first two are what make the
 * control read as the state of the layers rather than as a switch of its own; the last two are what
 * make it read as one of the row's.
 *
 * <p>The key is pinned for being read afresh at each attachment rather than for reaching the button
 * once. Where it lands is the row's business and is pinned there; what is this one's is that a
 * rebind is picked up by the next box to go up, which is what makes the setting mean anything
 * between one screen and the next.
 */
final class VanillaMapLayerToggleAttacherTest {

    // Where the appended control lands on a row carrying the game's own buttons.
    private static final int APPENDED_BUTTON_INDEX = ShownFilterRows.VANILLA_BUTTON_COUNT;

    // Two keys a player might have bound, told apart only so a rebind between attachments is
    // visible as a change rather than as the same reading twice.
    private static final int FIRST_BOUND_KEY = Keyboard.KEY_M;
    private static final int REBOUND_KEY = Keyboard.KEY_L;

    // Every case but the two about the key leaves the box keyless, that being one less thing stated
    // where it is not what is under test.
    private static final IntSupplier NO_KEY_BOUND = () -> 0;

    @Nested
    class AttachToggleTo {

        @Test
        void attachToggleToOpensTheBoxTickedForAScreenShowingItsLayers() {

            var rowFake = ShownFilterRows.createRowFakeWithRoomToSpare();
            var visibilityMock = mockVisibility(true);

            var isAttached = createAttacher()
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

            createAttacher()
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

            createAttacher()
                .attachToggleTo(ShownFilterRows.createRowOver(rowFake), visibilityMock);

            readAppendedButton(rowFake).click();

            // What the box now shows, read back off the button rather than assumed from the click,
            // since the button flips its own state before reporting.
            verify(visibilityMock)
                .showLayers(true);
        }

        @Test
        void attachToggleToGivesTheBoxTheKeyThePlayerHasBound() {

            var rowFake = ShownFilterRows.createRowFakeWithRoomToSpare();

            createAttacherReading(() -> FIRST_BOUND_KEY)
                .attachToggleTo(ShownFilterRows.createRowOver(rowFake), mockVisibility(true));

            assertThat(readAppendedButton(rowFake).readShortcutKeycode())
                .isEqualTo(Keyboard.KEY_M);
        }

        @Test
        void attachToggleToGivesTheNextBoxTheKeyThePlayerReboundToSince() {

            var boundKey = new AtomicInteger(FIRST_BOUND_KEY);
            var attacher = createAttacherReading(boundKey::get);
            var rebuiltRowFake = ShownFilterRows.createRowFakeWithRoomToSpare();

            attacher.attachToggleTo(
                ShownFilterRows.createRowOver(ShownFilterRows.createRowFakeWithRoomToSpare()),
                mockVisibility(true));

            boundKey.set(REBOUND_KEY);
            attacher.attachToggleTo(
                ShownFilterRows.createRowOver(rebuiltRowFake), mockVisibility(true));

            // Read at each attachment rather than once, so a rebind reaches the next screen the
            // player opens instead of waiting for the next load. The box already standing keeps the
            // key it went up with, the row offering no way to take one off again.
            assertThat(readAppendedButton(rebuiltRowFake).readShortcutKeycode())
                .isEqualTo(Keyboard.KEY_L);
        }

        @Test
        void attachToggleToGivesTheBoxTheHoverItsNeighboursCarry() {

            var rowFake = ShownFilterRows.createRowFakeWithRoomToSpare();
            var elementMock = mock(TooltipMakerAPI.class);

            StarsectorSettingsFake.installSettingsWithUiElements(() -> elementMock);
            try {
                createAttacher()
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

            var isAttached = createAttacher()
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

    // The live attachment with no key bound, which is every case that is not about the key.
    private static VanillaMapLayerToggleAttacher createAttacher() {
        return createAttacherReading(NO_KEY_BOUND);
    }

    // The same with a stated key reading. The hover is built here rather than stood in for, the
    // suite's one hover case needing a real one, and it is told there are no alliances so a case
    // that did open the words would not be reading whether another mod is installed.
    private static VanillaMapLayerToggleAttacher createAttacherReading(IntSupplier shortcutKeycode) {

        return new VanillaMapLayerToggleAttacher(
            shortcutKeycode,
            new MapLayerToggleTooltip(() -> false));
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
