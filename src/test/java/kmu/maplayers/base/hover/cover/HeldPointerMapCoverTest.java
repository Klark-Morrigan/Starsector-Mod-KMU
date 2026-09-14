package kmu.maplayers.base.hover.cover;

import kmlib.testfixtures.starsector.ui.input.PointerButtonHoldFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins both edges of the hold, which are the two the live game hides. The menu this cover exists
 * for is drawn by the map itself and takes itself down again, so a press that failed to cover looks
 * from the outside exactly like a hover the menu is sitting on top of - and a release that failed
 * to uncover looks like a hover that has simply stopped working, with nothing on screen to say a
 * button was ever involved.
 */
final class HeldPointerMapCoverTest {

    @Nested
    class IsCoveringCursor {

        @Test
        void isCoveringCursorAnswersCoveredWhileTheLeftButtonIsHeld() {
            // The frame the marker menu stands on: the map opened it under the pointer, and it
            // consumes the input the hover never sees.
            var pointerButtonHoldFake = new PointerButtonHoldFake();

            pointerButtonHoldFake.holdLeftButton();

            assertThat(new HeldPointerMapCover(pointerButtonHoldFake).isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersUncoveredOnceTheLeftButtonIsReleased() {
            // The release is what ends the menu, so it has to end the cover too - a cover outliving
            // the hold would leave the map quiet with nothing over it.
            var pointerButtonHoldFake = new PointerButtonHoldFake();

            pointerButtonHoldFake.holdLeftButton();
            pointerButtonHoldFake.releaseLeftButton();

            assertThat(new HeldPointerMapCover(pointerButtonHoldFake).isCoveringCursor())
                .isFalse();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileNoButtonHasBeenTouched() {
            // The ordinary frame, and the one the whole hover exists for: a pointer resting on the
            // map with nothing pressed is pointing at what is under it.
            assertThat(new HeldPointerMapCover(new PointerButtonHoldFake()).isCoveringCursor())
                .isFalse();
        }
    }
}
