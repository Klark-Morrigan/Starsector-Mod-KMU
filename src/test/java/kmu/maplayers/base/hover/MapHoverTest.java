package kmu.maplayers.base.hover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what one frame's hover reports: that {@link MapHover#NONE} reads as nothing
 * hovered while a resolved cell reads as hovering, and that a published hover cannot be edited
 * from underneath its readers - it is passed between a render pass and a later UI pass, so a
 * caller reusing its member list must not be able to rewrite what the tooltip is about to draw.
 */
final class MapHoverTest {

    @Nested
    class IsHovering {

        @Test
        void isHoveringIsFalseForNone() {
            assertThat(MapHover.NONE.isHovering()).isFalse();
            assertThat(MapHover.NONE.clusterMemberSystemKeys()).isEmpty();
        }

        @Test
        void isHoveringIsTrueWhenACellResolved() {
            var hover = new MapHover(
                buildCellKey("system"),
                buildCellKeys("system", "neighbour"));

            assertThat(hover.isHovering()).isTrue();
            assertThat(hover.hoveredSystemKey()).isEqualTo(buildCellKey("system"));
            assertThat(hover.clusterMemberSystemKeys())
                .containsExactlyElementsOf(buildCellKeys("system", "neighbour"));
        }
    }

    @Nested
    class ClusterMemberSystemKeys {

        @Test
        void clusterMemberSystemKeysIgnoreLaterEditsToTheCallersList() {
            var callersMembers = new ArrayList<>(buildCellKeys("system"));
            var hover = new MapHover(buildCellKey("system"), callersMembers);

            callersMembers.add(buildCellKey("added-after-publishing"));

            assertThat(hover.clusterMemberSystemKeys())
                .containsExactly(buildCellKey("system"));
        }

        @Test
        void clusterMemberSystemKeysCannotBeModified() {
            var hover = new MapHover(buildCellKey("system"), buildCellKeys("system"));

            assertThatThrownBy(() ->
                    hover.clusterMemberSystemKeys().add(buildCellKey("intruder")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
