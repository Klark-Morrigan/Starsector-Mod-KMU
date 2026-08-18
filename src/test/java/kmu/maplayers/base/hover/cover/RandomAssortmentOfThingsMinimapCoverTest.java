package kmu.maplayers.base.hover.cover;

import com.fs.starfarer.api.ui.PositionAPI;

import kmlib.starsector.ui.map.probes.EmbeddedMap;
import kmlib.testfixtures.starsector.ui.input.CursorPositionFake;
import kmlib.testfixtures.starsector.ui.map.presence.CampaignMinimapFake;
import kmlib.testfixtures.starsector.ui.map.probes.PlacedSectorMapWidgetFake;

import kmu.maplayers.base.hover.RandomAssortmentOfThingsMode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the one cover that opens something up rather than shutting it down, and every way it declines
 * to.
 *
 * <p>Three rules carry the whole of it. It is inert unless the mode is engaged, so an install
 * without the minimap behaves as it always did. It stands down entirely on a frame a vanilla map
 * owns, which is the non-interference guarantee and is answered before a box is read. And where it
 * does apply it fails closed - the opposite of every other cover - so an unreadable or ambiguous
 * screen confines the cursor rather than freeing it.
 */
final class RandomAssortmentOfThingsMinimapCoverTest {

    private static final BooleanSupplier A_MAP_IS_SHOWING = () -> true;
    private static final float FULLY_DRAWN = 1f;
    private static final BooleanSupplier NO_MAP_SHOWING = () -> false;

    @Nested
    class IsCoveringCursor {

        @Test
        void isCoveringCursorAnswersUncoveredInsideTheMinimapBox() {
            // What the mode is for: a pointer resting on the minimap the player docked is pointing
            // at a map, so the cell under it is theirs to be told about.
            var cursorFake = new CursorPositionFake();
            cursorFake.restCursorAt(100f, 80f);

            assertThat(buildCover(cursorFake, NO_MAP_SHOWING, createMinimapAt(20f, 30f, 200f, 150f))
                    .isCoveringCursor())
                .isFalse();
        }

        @Test
        void isCoveringCursorAnswersCoveredOutsideTheMinimapBox() {
            // The confinement, and the leak it closes: the map geometry underneath resolves a system
            // for every pixel on screen, so without this the whole campaign view would answer.
            var cursorFake = new CursorPositionFake();
            cursorFake.restCursorAt(500f, 400f);

            assertThat(buildCover(cursorFake, NO_MAP_SHOWING, createMinimapAt(20f, 30f, 200f, 150f))
                    .isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersCoveredWithTheMinimapParkedOffScreen() {
            // A parked panel is off screen rather than absent, and its pass goes on running. Nothing
            // extra reads that state: the live box is simply somewhere no cursor can be.
            var cursorFake = new CursorPositionFake();
            cursorFake.restCursorAt(100f, 80f);

            assertThat(buildCover(
                        cursorFake,
                        NO_MAP_SHOWING,
                        createMinimapAt(-400f, 30f, 200f, 150f))
                    .isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileTheModeIsNotEngaged() {
            // Inert without the mode, whatever else is true. Asked before anything is read, so an
            // install with no minimap pays one boolean.
            var cursorFake = new CursorPositionFake();
            cursorFake.restCursorAt(500f, 400f);

            assertThat(new RandomAssortmentOfThingsMinimapCover(
                        buildDisengagedMode(),
                        NO_MAP_SHOWING,
                        () -> List.of(createMinimapAt(20f, 30f, 200f, 150f)),
                        cursorFake)
                    .isCoveringCursor())
                .isFalse();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileAVanillaMapIsShowing() {
            // The non-interference guarantee. The cursor is nowhere near the minimap's box, and the
            // frame belongs to a vanilla host, so this cover has nothing to say about it.
            var cursorFake = new CursorPositionFake();
            cursorFake.restCursorAt(500f, 400f);

            assertThat(buildCover(
                        cursorFake,
                        A_MAP_IS_SHOWING,
                        createMinimapAt(20f, 30f, 200f, 150f))
                    .isCoveringCursor())
                .isFalse();
        }

        @Test
        void isCoveringCursorAnswersCoveredWithNoEmbeddedMapFound() {
            // Failing closed. The walk can come back empty because the reach broke or because the
            // panel is not built yet, and opening the whole screen up on either would restore the
            // leak by way of the read meant to stop it.
            var cursorFake = new CursorPositionFake();
            cursorFake.restCursorAt(100f, 80f);

            assertThat(new RandomAssortmentOfThingsMinimapCover(
                        buildEngagedMode(),
                        NO_MAP_SHOWING,
                        List::of,
                        cursorFake)
                    .isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersCoveredWithMoreThanOneEmbeddedMapOnScreen() {
            // The confinement's precondition. The frame carries one transform and the hover resolves
            // through whichever pass drew last, so a hover over the first box could be answered
            // through the second's zoom - a wrong answer rather than a missing one.
            var cursorFake = new CursorPositionFake();
            cursorFake.restCursorAt(100f, 80f);

            var embeddedMaps = List.of(
                createMinimapAt(20f, 30f, 200f, 150f),
                createMinimapAt(400f, 30f, 200f, 150f));

            assertThat(new RandomAssortmentOfThingsMinimapCover(
                        buildEngagedMode(),
                        NO_MAP_SHOWING,
                        () -> embeddedMaps,
                        cursorFake)
                    .isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersCoveredWithTheMinimapDrawnNowhere() {
            // A map found in the tree but with no box to point at - never positioned, or faded out.
            // Same answer as none found, for the same reason.
            var cursorFake = new CursorPositionFake();
            cursorFake.restCursorAt(100f, 80f);

            var unplacedMap = new EmbeddedMap(
                new PlacedSectorMapWidgetFake(null, FULLY_DRAWN), List.of());

            assertThat(new RandomAssortmentOfThingsMinimapCover(
                        buildEngagedMode(),
                        NO_MAP_SHOWING,
                        () -> List.of(unplacedMap),
                        cursorFake)
                    .isCoveringCursor())
                .isTrue();
        }
    }

    // The ordinary arrangement: the mode engaged over exactly one embedded map, which is the only
    // shape the confinement itself is stated for. The cases about the other shapes compose their
    // own.
    private static RandomAssortmentOfThingsMinimapCover buildCover(
            CursorPositionFake cursorFake,
            BooleanSupplier isAnyMapShowing,
            EmbeddedMap minimap) {

        return new RandomAssortmentOfThingsMinimapCover(
            buildEngagedMode(),
            isAnyMapShowing,
            () -> List.of(minimap),
            cursorFake);
    }

    // The player's switch on and a minimap standing in for the radar - both halves the mode ANDs.
    private static RandomAssortmentOfThingsMode buildEngagedMode() {

        var minimapFake = new CampaignMinimapFake();
        minimapFake.replaceRadarWithMinimap();

        return new RandomAssortmentOfThingsMode(() -> true, minimapFake);
    }

    // The player's switch off, which is the half no install can be without.
    private static RandomAssortmentOfThingsMode buildDisengagedMode() {

        var minimapFake = new CampaignMinimapFake();
        minimapFake.replaceRadarWithMinimap();

        return new RandomAssortmentOfThingsMode(() -> false, minimapFake);
    }

    // A drawn map widget at a known box. The engine's position is a wide interface of which only the
    // four layout numbers are read, so it is mocked rather than stood up.
    private static EmbeddedMap createMinimapAt(float x, float y, float width, float height) {

        var positionMock = mock(PositionAPI.class);

        when(positionMock.getX())
            .thenReturn(x);
        when(positionMock.getY())
            .thenReturn(y);

        when(positionMock.getWidth())
            .thenReturn(width);
        when(positionMock.getHeight())
            .thenReturn(height);
        
        return new EmbeddedMap(
            new PlacedSectorMapWidgetFake(positionMock, FULLY_DRAWN),
            List.of());
    }
}
