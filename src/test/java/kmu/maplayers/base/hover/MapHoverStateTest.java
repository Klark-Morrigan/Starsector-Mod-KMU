package kmu.maplayers.base.hover;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.installation.MapLayerInstallations;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the holder that carries a frame's hover from the map render to the passes that read it:
 * that it starts and clears to "nothing hovered" rather than to a null a reader would trip over,
 * that a publish is what the next read sees, and that the seams driven with no sector reach the
 * running sector's holder - one reaching another's would leave the box reading a hover nothing
 * ever writes.
 *
 * <p>The frame window is pinned alongside them because it is what bounds every published hover: a
 * frame that resolved one keeps it, and a frame with no map pass at all - the state every other
 * park is unreachable in, no pass being run to write one - lets it go.
 *
 * <p>Each case builds its own holder rather than resolving one through an installation, so a
 * published hover cannot reach another case.
 */
final class MapHoverStateTest {

    @Nested
    class GetHover {

        @Test
        void getHoverStartsAtNone() {

            assertThat(new MapHoverState().getHover())
                .isSameAs(MapHover.NONE);
        }
    }

    @Nested
    class PublishHover {

        @Test
        void publishHoverIsWhatTheNextReadSees() {

            var state = new MapHoverState();
            var hover = new MapHover("system", List.of("system"));

            state.publishHover(hover);

            assertThat(state.getHover())
                .isSameAs(hover);
        }

        @Test
        void publishHoverReplacesTheStandingHover() {

            var state = new MapHoverState();

            state.publishHover(new MapHover("first", List.of("first")));

            var second = new MapHover("second", List.of("second"));

            state.publishHover(second);

            assertThat(state.getHover())
                .isSameAs(second);
        }
    }

    @Nested
    class ClearHover {

        @Test
        void clearHoverParksThePublishedHover() {

            var state = new MapHoverState();

            state.publishHover(new MapHover("system", List.of("system")));
            state.clearHover();

            assertThat(state.getHover())
                .isSameAs(MapHover.NONE);
            assertThat(state.getHover().isHovering())
                .isFalse();
        }
    }

    @Nested
    class ExpireHoverIfNoPassPublished {

        @Test
        void expireHoverIfNoPassPublishedKeepsAHoverAPassJustPublished() {
            // The grace this is stated over: a hover published during the frame just closed is the
            // map's current answer, and the box that reports it has yet to draw.
            var state = new MapHoverState();
            var hover = new MapHover("system", List.of("system"));

            state.publishHover(hover);
            state.expireHoverIfNoPassPublished();

            assertThat(state.getHover())
                .isSameAs(hover);
        }

        @Test
        void expireHoverIfNoPassPublishedParksAHoverNoPassRepublished() {
            // The fault this exists for: with no map on screen, no pass runs to park anything, so
            // the last cell any map resolved would be named anywhere the pointer went.
            var state = new MapHoverState();

            state.publishHover(new MapHover("system", List.of("system")));
            state.expireHoverIfNoPassPublished();
            state.expireHoverIfNoPassPublished();

            assertThat(state.getHover())
                .isSameAs(MapHover.NONE);
        }

        @Test
        void expireHoverIfNoPassPublishedKeepsAHoverThePassesGoOnPublishing() {
            // A map that is still drawing publishes every frame, so the window reopens with each of
            // them and the hover under a resting pointer never blinks.
            var state = new MapHoverState();
            var hover = new MapHover("system", List.of("system"));

            for (var frame = 0; frame < 3; frame++) {

                state.publishHover(hover);
                state.expireHoverIfNoPassPublished();
            }

            assertThat(state.getHover())
                .isSameAs(hover);
        }

        @Test
        void expireHoverIfNoPassPublishedTreatsAParkAsNoPublication() {
            // A pass that parked resolved nothing, so it holds no window open. Costless either way -
            // the hover it would expire is the one that pass already parked.
            var state = new MapHoverState();

            state.publishHover(new MapHover("system", List.of("system")));
            state.clearHover();
            state.expireHoverIfNoPassPublished();
            state.expireHoverIfNoPassPublished();

            assertThat(state.getHover())
                .isSameAs(MapHover.NONE);
        }
    }

    @Nested
    class ResolveLiveSectorHoverState {

        @Test
        void resolveLiveSectorHoverStateYieldsTheRunningSectorsHolder() {
            // The stand-in the map render hook and the tooltip listener reach through, neither being
            // told which sector is being drawn. It has to answer the running sector's holder, or a
            // pass would publish where that sector's box never reads.
            var sectorMock = mock(SectorAPI.class);

            var installation = MapLayerInstallations.installMachineryOn(sectorMock);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                assertThat(MapHoverState.resolveLiveSectorHoverState())
                    .isSameAs(installation.resolveHoverState());
            }
            MapLayerInstallations.uninstallMachineryFrom(sectorMock);
        }
    }
}
