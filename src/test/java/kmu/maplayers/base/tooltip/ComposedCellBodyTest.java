package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a layer hands back for one paint: the blocks it composed, held apart from the list it
 * composed them in, and whether it has anything deeper to state.
 *
 * <p>The copy is the whole of what this value adds over a pair of fields. A layer composes its
 * blocks into a list of its own and may go on holding it - the same instance serves every hover -
 * so a body that kept the caller's list would let a later composition reshape a box already drawn.
 */
final class ComposedCellBodyTest {

    @Nested
    class ComposedCellBody_Constructor {

        @Test
        void constructorCopiesTheBlocksAwayFromTheListTheLayerComposedThemIn() {
            // A layer appending to its own list after handing it over, which is what a shared
            // instance rebuilding for the next hover looks like from here.
            var composedSections = new ArrayList<TooltipSection>();
            composedSections.add(buildSection("The Hegemony"));

            var body = new ComposedCellBody(composedSections, true);

            composedSections.add(buildSection("Independent"));

            assertThat(body.sections())
                .hasSize(1);
        }
    }

    @Nested
    class Nothing {

        @Test
        void nothingIsABodyWithNoBlocksAndNothingToOpenUp() {
            // The one spelling of "the layer found nothing", so a box drawn from it stays undrawn
            // and offers no key either - two spellings of that state agree only until one is edited.
            assertThat(ComposedCellBody.NOTHING.sections())
                .isEmpty();
            assertThat(ComposedCellBody.NOTHING.hasDeeperDetail())
                .isFalse();
        }
    }

    // A one-line block, this suite being about what the value does with the blocks rather than about
    // what fills them.
    private static TooltipSection buildSection(String text) {
        return TooltipSection.createSection(
            List.of(TooltipRow.createRow(new TextSpan(text, Color.LIGHT_GRAY))));
    }
}
