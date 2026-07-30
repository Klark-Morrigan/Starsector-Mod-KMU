package kmu.maplayers.base.hover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the holder that carries a frame's hover from the map render to the passes that read it:
 * that it starts and clears to "nothing hovered" rather than to a null a reader would trip over,
 * that a publish is what the next read sees, and that the shared instance is genuinely one
 * instance - two collaborators resolving different holders would leave the tooltip reading a
 * hover nothing ever writes.
 *
 * <p>Each test builds its own holder rather than using {@link MapHoverState#getInstance},
 * so a published hover cannot leak into another test through the shared one.
 */
final class MapHoverStateTest {

    @Nested
    class GetHover {

        @Test
        void getHoverStartsAtNone() {
            assertThat(new MapHoverState().getHover()).isSameAs(MapHover.NONE);
        }
    }

    @Nested
    class PublishHover {

        @Test
        void publishHoverIsWhatTheNextReadSees() {
            var state = new MapHoverState();
            var hover = new MapHover("system", List.of("system"));

            state.publishHover(hover);

            assertThat(state.getHover()).isSameAs(hover);
        }

        @Test
        void publishHoverReplacesTheStandingHover() {
            var state = new MapHoverState();
            state.publishHover(new MapHover("first", List.of("first")));
            var second = new MapHover("second", List.of("second"));

            state.publishHover(second);

            assertThat(state.getHover()).isSameAs(second);
        }
    }

    @Nested
    class ClearHover {

        @Test
        void clearHoverParksThePublishedHover() {
            var state = new MapHoverState();
            state.publishHover(new MapHover("system", List.of("system")));

            state.clearHover();

            assertThat(state.getHover()).isSameAs(MapHover.NONE);
            assertThat(state.getHover().isHovering()).isFalse();
        }
    }

    @Nested
    class GetInstance {

        @Test
        void getInstanceIsOneSharedHolder() {
            assertThat(MapHoverState.getInstance())
                    .isSameAs(MapHoverState.getInstance());
        }
    }
}
