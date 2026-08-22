package kmu.starsector.rat;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.map.probes.EmbeddedMap;
import kmlib.testfixtures.starsector.ui.input.CursorPositionFake;
import kmlib.testfixtures.starsector.ui.layout.PositionFake;
import kmlib.testfixtures.starsector.ui.map.presence.CampaignMinimapFake;
import kmlib.testfixtures.starsector.ui.map.probes.PlacedSectorMapWidgetFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

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
                        () -> createMinimapAt(20f, 30f, 200f, 150f),
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
        void isCoveringCursorAnswersCoveredWithNoSingleEmbeddedMapOnScreen() {
            // Failing closed. The shared reading answers nothing when the walk came back empty - the
            // reach broke, or the panel is not built yet - and when it found more than one surface,
            // which is the confinement's precondition: the frame carries one transform, so a hover
            // over the first box could be answered through the second's zoom. Opening the whole
            // screen up on either would restore the leak by way of the read meant to stop it.
            var cursorFake = new CursorPositionFake();
            cursorFake.restCursorAt(100f, 80f);

            assertThat(new RandomAssortmentOfThingsMinimapCover(
                        buildEngagedMode(),
                        NO_MAP_SHOWING,
                        () -> null,
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
                        () -> unplacedMap,
                        cursorFake)
                    .isCoveringCursor())
                .isTrue();
        }
    }

    // The ordinary arrangement: the mode engaged over the one embedded map on screen, which is the
    // only shape the confinement itself is stated for. The cases about the other shapes compose
    // their own.
    private static RandomAssortmentOfThingsMinimapCover buildCover(
            CursorPositionFake cursorFake,
            BooleanSupplier isAnyMapShowing,
            EmbeddedMap minimap) {

        return new RandomAssortmentOfThingsMinimapCover(
            buildEngagedMode(),
            isAnyMapShowing,
            () -> minimap,
            cursorFake);
    }

    // The player's switch on over a minimap standing in for the radar - both halves the mode ANDs.
    private static RandomAssortmentOfThingsCompatibilityMode buildEngagedMode() {
        return buildMode(true);
    }

    // The player's switch off over the same minimap, so what disengages the mode is the switch and
    // not the absence of a map to adapt to - the half a case about the switch has to hold still.
    private static RandomAssortmentOfThingsCompatibilityMode buildDisengagedMode() {
        return buildMode(false);
    }

    private static RandomAssortmentOfThingsCompatibilityMode buildMode(boolean isModeSwitchedOn) {

        var minimapFake = new CampaignMinimapFake();
        minimapFake.replaceRadarWithMinimap();

        return new RandomAssortmentOfThingsCompatibilityMode(() -> isModeSwitchedOn, minimapFake);
    }

    // A drawn map widget at a known box.
    private static EmbeddedMap createMinimapAt(float x, float y, float width, float height) {
        return new EmbeddedMap(
            new PlacedSectorMapWidgetFake(
                new PositionFake(new Rectangle(x, y, width, height)), FULLY_DRAWN),
            List.of());
    }
}
