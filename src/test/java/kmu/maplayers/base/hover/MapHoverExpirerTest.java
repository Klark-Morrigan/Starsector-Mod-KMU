package kmu.maplayers.base.hover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the tick that closes a frame's hover window, and the two things about it that are not the
 * rule it drives: that it goes on running for the session, and that it runs while the campaign is
 * paused.
 *
 * <p>Both are load-bearing rather than boilerplate. The frames this exists for are paused ones - an
 * open screen, a dialog, the pause menu - so a script that stood down under them would let go of a
 * hover only once the player was moving again, which is after the box has already been drawn over
 * the screen they were looking at.
 */
final class MapHoverExpirerTest {

    private static final float ANY_FRAME_LENGTH = 1f / 60f;

    @Nested
    class Advance {

        @Test
        void advanceParksAHoverNoPassRepublished() {
            // The frame window, driven from outside the map passes: two frames with no publication
            // between them is a hover nobody is resolving any more.
            var state = new MapHoverState();

            state.publishHover(new MapHover("system", List.of("system")));

            var expirer = new MapHoverExpirer(state);

            expirer.advance(ANY_FRAME_LENGTH);
            expirer.advance(ANY_FRAME_LENGTH);

            assertThat(state.getHover())
                .isSameAs(MapHover.NONE);
        }

        @Test
        void advanceKeepsAHoverThePassesGoOnPublishing() {
            // A map still drawing publishes every frame, so this never takes a live hover away.
            var state = new MapHoverState();
            var hover = new MapHover("system", List.of("system"));
            var expirer = new MapHoverExpirer(state);

            for (var frame = 0; frame < 3; frame++) {

                state.publishHover(hover);
                expirer.advance(ANY_FRAME_LENGTH);
            }

            assertThat(state.getHover())
                .isSameAs(hover);
        }
    }

    @Nested
    class IsDone {

        @Test
        void isDoneIsFalseSoTheTickRunsForTheSession() {
            
            assertThat(new MapHoverExpirer(new MapHoverState()).isDone())
                .isFalse();
        }
    }

    @Nested
    class RunWhilePaused {

        @Test
        void runWhilePausedIsTrueSoAHoverIsLetGoOfUnderAnOpenScreen() {
            // The map screen, a dialog and the pause menu all pause the campaign, and a hover left
            // standing under one of them is what this is here to release.
            assertThat(new MapHoverExpirer(new MapHoverState()).runWhilePaused())
                .isTrue();
        }
    }
}
