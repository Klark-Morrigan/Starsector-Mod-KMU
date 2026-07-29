package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.hover.PoliticalMapHover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the dispatcher's pure draw gate - the one decision separable from the in-engine draw, the
 * live vanilla-tooltip step-aside, and the injected tooltip around it: a tooltip shows for a hovered
 * cell and nothing when no cell is hovered.
 */
final class MapLayerCellTooltipTest {

    @Nested
    class ShouldDrawTooltipFor {

        @Test
        void shouldDrawTooltipForIsTrueForAHoveredCell() {
            var hover = new PoliticalMapHover("system", List.of("system"));

            assertThat(MapLayerCellTooltip.shouldDrawTooltipFor(hover)).isTrue();
        }

        @Test
        void shouldDrawTooltipForIsFalseWhenNothingIsHovered() {
            assertThat(MapLayerCellTooltip.shouldDrawTooltipFor(PoliticalMapHover.NONE)).isFalse();
        }
    }
}
