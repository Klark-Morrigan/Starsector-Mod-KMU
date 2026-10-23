package kmu.maplayers.base.labels;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lazywizard.lazylib.ui.LazyFont.DrawableString;

import java.awt.Color;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pins when a label recolours its string: on the first frame, and on any frame the map's alpha moved,
 * and never on a frame it held - which is nearly every frame, and the one this is about, since a
 * faded colour is a new object.
 */
final class LabelTests {

    private static final Color OWNER_SHADE = new Color(200, 100, 50, 255);

    private static final float HALF_FADED = 0.5f;
    private static final float UNFADED = 1f;

    @Nested
    class ApplyFade {

        // The string's colour is whatever it was minted in, which the label does not take on trust.
        @Test
        void recoloursTheStringOnTheFirstFrame() {

            // ARRANGE
            var textMock = mock(DrawableString.class);
            var label = buildLabelOver(textMock);
            var expectedColour = new Color(200, 100, 50, 255);

            // ACT
            label.applyFade(UNFADED);

            // ASSERT
            verify(textMock)
                .setBaseColor(expectedColour);
        }

        @Test
        void leavesTheStringAloneWhileTheAlphaHolds() {

            // ARRANGE
            var textMock = mock(DrawableString.class);
            var label = buildLabelOver(textMock);

            label.applyFade(UNFADED);

            // ACT
            label.applyFade(UNFADED);

            // ASSERT
            verify(textMock)
                .setBaseColor(any());
        }

        @Test
        void recoloursTheStringWhenTheAlphaMoves() {

            // ARRANGE
            var textMock = mock(DrawableString.class);
            var label = buildLabelOver(textMock);
            var expectedColour = new Color(200, 100, 50, 128);

            label.applyFade(UNFADED);

            // ACT
            label.applyFade(HALF_FADED);

            // ASSERT
            verify(textMock)
                .setBaseColor(expectedColour);
        }
    }

    // A label over the given string, in the owner's shade. Where it hangs is not read here.
    private static Label buildLabelOver(DrawableString text) {

        return new Label(
            text,
            OWNER_SHADE,
            0f,
            0f,
            0f);
    }
}
