package kmu.maplayers.base.hover.cover;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the composition rule the covers are asked under: any one of them covers the map, and the
 * read stops at the first that does. The stopping is the half worth pinning - the order is stated
 * by cost, so a reader that asked them all would spend a walk of the live widget tree on a frame a
 * settled flag had already answered.
 *
 * <p>Each cover's own reading is its own class's; what is fixed here is how a set of them is read,
 * and which of them an install is given at all.
 */
final class MapCoverReaderTests {

    private static final boolean IS_COVERING = true;
    private static final boolean IS_NOT_COVERING = false;

    @Nested
    class IsMapCoveredAtCursor {

        @Test
        void answersCoveredWhenALaterCoverIsOverTheCursor() {
            // Any one cover is enough: they are alternatives, not conditions to meet together, so a
            // console down does not excuse the chrome the cursor is actually on.
            var reader = new MapCoverReader(List.of(
                new MapCoverFake(IS_NOT_COVERING),
                new MapCoverFake(IS_COVERING)));

            assertThat(reader.isMapCoveredAtCursor())
                .isTrue();
        }

        @Test
        void answersUncoveredWhileNoCoverIs() {
            // The ordinary frame: the cursor is on the map itself, so the hover read behind this
            // goes ahead.
            var reader = new MapCoverReader(List.of(
                new MapCoverFake(IS_NOT_COVERING),
                new MapCoverFake(IS_NOT_COVERING)));

            assertThat(reader.isMapCoveredAtCursor())
                .isFalse();
        }

        @Test
        void stopsAtTheFirstCoverThatAnswers() {
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

        @AfterEach
        void forgetTheRegisteredCovers() {
            // Process state, so a case that registers covers would otherwise leave them composed
            // into every reader a later suite builds.
            MapCoverReader.registerOptionalModCovers(List.of());
        }

        @Test
        void asksTheScreenClaimBeforeTheSidebar() {
            // Load-bearing rather than incidental, and the one ordering a later cover could break by
            // being inserted between them: everything the claim reads stands the sidebar down, so
            // behind it the sidebar's cover resolves placements for a panel that is not drawn, to
            // reach a false it can no longer avoid. Pinned on its own because the full-list
            // assertion below would report a break in it as a mismatch about the whole set.
            var coverTypes = MapCoverReader.composeLiveCovers()
                .stream()
                .map(Object::getClass)
                .toList();

            assertThat(coverTypes.indexOf(ScreenClaimMapCover.class))
                .isLessThan(coverTypes.indexOf(SidebarMapCover.class));
        }

        @Test
        void composesTheFrameworksOwnCoversWhereNoOptionalModRegisteredOne() {
            // An install with no optional mod: the composition root registers nothing, so nothing
            // is asked per frame that could only ever answer no.
            assertThat(MapCoverReader.composeLiveCovers())
                .hasExactlyElementsOfTypes(
                    HeldPointerMapCover.class,
                    PauseMenuMapCover.class,
                    ScreenClaimMapCover.class,
                    SidebarMapCover.class,
                    VanillaChromeMapCover.class);
        }

        @Test
        void asksTheRegisteredCoversAfterTheFrameworksOwn() {
            // The optional mods' covers are the dearest - the minimap's walks the whole core UI - so
            // they are asked only once every cover of the framework's own has answered no.
            var registeredCoverFake = new MapCoverFake(IS_COVERING);

            MapCoverReader.registerOptionalModCovers(List.of(registeredCoverFake));

            assertThat(MapCoverReader.composeLiveCovers())
                .last()
                .isSameAs(registeredCoverFake);
        }
    }
}
