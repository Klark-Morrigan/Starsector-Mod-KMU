package kmu.starsector.ui;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.map.probes.EmbeddedMap;
import kmlib.testfixtures.starsector.ui.coreui.CoreUiComponentFake;
import kmlib.testfixtures.starsector.ui.layout.PositionFake;
import kmlib.testfixtures.starsector.ui.map.probes.PlacedSectorMapWidgetFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which of the two surfaces a frame belongs to, which is what anything rooting a walk at the
 * shown map is rooted at.
 *
 * <p>The precedence is the part worth pinning: a vanilla host owning the frame settles the answer
 * before a docked panel is looked at, so a panel travelling across the screen can never come to
 * stand for the map the player opened. The reverse would root a walk at one mod's panel while the
 * player is looking at the {@code M} map.
 *
 * <p>Both readings arrive as values here. Which surfaces a running game has is read through statics
 * that answer nothing outside one; what the two readings <em>mean</em> together is the rule, and it
 * is decidable over a pair of widgets.
 */
final class ShownMapSurfaceTest {

    private static final float FULLY_DRAWN = 1f;
    private static final EmbeddedMap NO_EMBEDDED_MAP = null;
    private static final Object NO_MAP_TAB_ON_SCREEN = null;

    @Nested
    class ResolveShownMapSurface {

        @Test
        void resolveShownMapSurfaceAnswersTheMapTabWhileOneIsUp() {

            var mapTabFake = new CoreUiComponentFake();

            assertThat(ShownMapSurface.resolveShownMapSurface(
                    mapTabFake, NO_EMBEDDED_MAP))
                .isSameAs(mapTabFake);
        }

        @Test
        void resolveShownMapSurfaceAnswersTheMapTabEvenWithADockedMapOnScreen() {
            // The frame is the vanilla host's, and it is settled before the docked panel is read at
            // all - a panel a mod keeps parked or slides about goes on existing while the player has
            // the map open, and it is not the surface they are pointing at.
            var mapTabFake = new CoreUiComponentFake();

            assertThat(ShownMapSurface.resolveShownMapSurface(
                    mapTabFake, createEmbeddedMapUnder(new CoreUiComponentFake())))
                .isSameAs(mapTabFake);
        }

        @Test
        void resolveShownMapSurfaceAnswersTheDockedPanelWithNoMapTabUp() {
            // Game space with a mod's minimap docked, which is the frame this reading exists for:
            // no tab is up, so the panel that mod added to the core UI is the whole of what a walk
            // rooted at the shown map can be rooted at.
            var panelFake = new CoreUiComponentFake();

            assertThat(ShownMapSurface.resolveShownMapSurface(
                    NO_MAP_TAB_ON_SCREEN, createEmbeddedMapUnder(panelFake)))
                .isSameAs(panelFake);
        }

        @Test
        void resolveShownMapSurfaceAnswersNothingWithNoSurfaceOnScreen() {
            // A vanilla install in game space. Nothing to root at is the ordinary answer rather than
            // a failure, and a caller draws its own box rather than standing aside for a walk that
            // never ran.
            assertThat(ShownMapSurface.resolveShownMapSurface(
                    NO_MAP_TAB_ON_SCREEN, NO_EMBEDDED_MAP))
                .isNull();
        }
    }

    // A docked map surface hanging under the given panel, with the walk's own root above it - the
    // ancestry shape a finder hands back, and what the owning-panel read is taken from.
    private static EmbeddedMap createEmbeddedMapUnder(Object panelFake) {
        return new EmbeddedMap(
            new PlacedSectorMapWidgetFake(
                new PositionFake(new Rectangle(20f, 30f, 200f, 150f)), FULLY_DRAWN),
            List.of(new CoreUiComponentFake(), panelFake));
    }
}
