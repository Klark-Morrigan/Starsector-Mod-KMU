package kmu.maplayers.base.hover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
            assertThat(MapHover.NONE.clusterMemberSystemIds()).isEmpty();
        }

        @Test
        void isHoveringIsTrueWhenACellResolved() {
            var hover = new MapHover("system", List.of("system", "neighbour"));

            assertThat(hover.isHovering()).isTrue();
            assertThat(hover.hoveredSystemId()).isEqualTo("system");
            assertThat(hover.clusterMemberSystemIds()).containsExactly("system", "neighbour");
        }
    }

    @Nested
    class ClusterMemberSystemIds {

        @Test
        void clusterMemberSystemIdsIgnoreLaterEditsToTheCallersList() {
            var callersMembers = new ArrayList<>(List.of("system"));
            var hover = new MapHover("system", callersMembers);

            callersMembers.add("added-after-publishing");

            assertThat(hover.clusterMemberSystemIds()).containsExactly("system");
        }

        @Test
        void clusterMemberSystemIdsCannotBeModified() {
            var hover = new MapHover("system", List.of("system"));

            assertThatThrownBy(() -> hover.clusterMemberSystemIds().add("intruder"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
