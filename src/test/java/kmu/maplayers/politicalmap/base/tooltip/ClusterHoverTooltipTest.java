package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.hover.PoliticalMapHover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the tooltip's draw gate - the one decision separable from the in-engine GL/font draw around
 * it: the box shows over a hovered cell but hands the star icon back to the vanilla star tooltip,
 * so exactly one box ever shows, and shows nothing when no cell is hovered.
 */
final class ClusterHoverTooltipTest {

    @Nested
    class ShouldDrawTooltipFor {

        @Test
        void shouldDrawTooltipForIsTrueOverTheCellAwayFromTheIcon() {
            var hover = new PoliticalMapHover("system", List.of("system"), false);

            assertThat(ClusterHoverTooltip.shouldDrawTooltipFor(hover)).isTrue();
        }

        @Test
        void shouldDrawTooltipForIsFalseOnTheStarIcon() {
            // The cursor is on a cell, but on its star icon, where the vanilla star tooltip draws -
            // so our box steps aside rather than stacking a second box over the same spot.
            var hover = new PoliticalMapHover("system", List.of("system"), true);

            assertThat(ClusterHoverTooltip.shouldDrawTooltipFor(hover)).isFalse();
        }

        @Test
        void shouldDrawTooltipForIsFalseWhenNothingIsHovered() {
            assertThat(ClusterHoverTooltip.shouldDrawTooltipFor(PoliticalMapHover.NONE)).isFalse();
        }
    }
}
