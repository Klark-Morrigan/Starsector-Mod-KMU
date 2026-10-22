package kmu.maplayers.base.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two things a baked band promises its readers: it is fixed once baked, and it says
 * whether it draws anything without being read into.
 *
 * <p>The copy matters because a band is held across frames by the bake that laid it, while the
 * list it was laid from belongs to the builder. A band sharing that list would change under the
 * render pass the moment the builder reused it.
 */
final class CellRibbonTests {

    private static final RibbonBand WHITE_BAND = new RibbonBand(Color.WHITE, new float[0]);

    @Nested
    class Constructor {

        @Test
        void keepsItsRunsWhenTheListItWasLaidFromChanges() {

            var laidBands = new ArrayList<RibbonBand>();

            laidBands.add(WHITE_BAND);

            var ribbon = new CellRibbon(laidBands);
            var expectedBands = List.of(WHITE_BAND);

            laidBands.clear();

            var actualBands = ribbon.bands();

            assertThat(actualBands)
                .isEqualTo(expectedBands);
        }
    }

    @Nested
    class IsEmpty {

        @Test
        void answersTrueForTheCellThatDrawsNoBand() {

            var actualIsEmpty = CellRibbon.NONE.isEmpty();

            assertThat(actualIsEmpty)
                .isTrue();
        }

        @Test
        void answersFalseForACellCarryingARun() {

            var ribbon = new CellRibbon(List.of(WHITE_BAND));
            var actualIsEmpty = ribbon.isEmpty();

            assertThat(actualIsEmpty)
                .isFalse();
        }
    }
}
