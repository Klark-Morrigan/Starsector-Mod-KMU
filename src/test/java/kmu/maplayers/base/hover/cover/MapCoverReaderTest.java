package kmu.maplayers.base.hover.cover;

import kmlib.mods.consolecommands.ConsoleCommandsPresence;
import kmlib.mods.rat.RandomAssortmentOfThingsPresence;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the composition rule the covers are asked under: any one of them covers the map, and the
 * read stops at the first that does. The stopping is the half worth pinning - the order is stated
 * by cost, so a reader that asked them all would spend a walk of the live widget tree on a frame a
 * settled flag had already answered.
 *
 * <p>Each cover's own reading is its own class's; what is fixed here is how a set of them is read,
 * and which of them an install is given at all.
 */
final class MapCoverReaderTest {

    private static final boolean IS_COVERING = true;
    private static final boolean IS_NOT_COVERING = false;

    @Nested
    class IsMapCoveredAtCursor {

        @Test
        void isMapCoveredAtCursorAnswersCoveredWhenALaterCoverIsOverTheCursor() {
            // Any one cover is enough: they are alternatives, not conditions to meet together, so a
            // console down does not excuse the chrome the cursor is actually on.
            var reader = new MapCoverReader(List.of(
                new MapCoverFake(IS_NOT_COVERING),
                new MapCoverFake(IS_COVERING)));

            assertThat(reader.isMapCoveredAtCursor())
                .isTrue();
        }

        @Test
        void isMapCoveredAtCursorAnswersUncoveredWhileNoCoverIs() {
            // The ordinary frame: the cursor is on the map itself, so the hover read behind this
            // goes ahead.
            var reader = new MapCoverReader(List.of(
                new MapCoverFake(IS_NOT_COVERING),
                new MapCoverFake(IS_NOT_COVERING)));

            assertThat(reader.isMapCoveredAtCursor())
                .isFalse();
        }

        @Test
        void isMapCoveredAtCursorStopsAtTheFirstCoverThatAnswers() {
            // What makes the cost ordering worth stating: an answered read never reaches the dearer
            // covers behind it.
            var answeringCoverFake = new MapCoverFake(IS_COVERING);
            var unreachedCoverFake = new MapCoverFake(IS_COVERING);

            new MapCoverReader(List.of(answeringCoverFake, unreachedCoverFake))
                .isMapCoveredAtCursor();

            assertThat(answeringCoverFake.hasBeenAsked())
                .isTrue();
            assertThat(unreachedCoverFake.hasBeenAsked())
                .isFalse();
        }
    }

    @Nested
    class ComposeLiveCovers {

        @Test
        void leavesAnOptionalModsCoverOutWhereThatModIsNotInstalled() {
            // Presence cannot move within a run, so it is settled here rather than asked per frame
            // by a cover that could only ever answer no. Without the gate the two would be composed
            // on every install, and the dearest of them walks the whole core UI.
            try (var consoleMock = mockStatic(ConsoleCommandsPresence.class);
                    var minimapModMock = mockStatic(RandomAssortmentOfThingsPresence.class)) {

                consoleMock
                    .when(ConsoleCommandsPresence::isModEnabled)
                    .thenReturn(false);
                minimapModMock
                    .when(RandomAssortmentOfThingsPresence::isModEnabled)
                    .thenReturn(false);

                assertThat(MapCoverReader.composeLiveCovers())
                    .hasExactlyElementsOfTypes(
                        HeldPointerMapCover.class,
                        PauseMenuMapCover.class,
                        ModalDialogMapCover.class,
                        SidebarMapCover.class,
                        VanillaChromeMapCover.class);
            }
        }
    }
}
