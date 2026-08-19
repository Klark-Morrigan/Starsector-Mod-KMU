package kmu.maplayers.base.render;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.map.probes.EmbeddedMap;
import kmlib.testfixtures.starsector.ui.layout.PositionFake;
import kmlib.testfixtures.starsector.ui.map.presence.CampaignMinimapFake;
import kmlib.testfixtures.starsector.ui.map.probes.PlacedSectorMapWidgetFake;
import kmlib.testfixtures.starsector.ui.map.probes.SectorMapWidgetFake;

import kmu.maplayers.base.hover.RandomAssortmentOfThingsMode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which widget this mod may switch off on another mod's behalf, and every state in which it may
 * switch off nothing at all.
 *
 * <p>The permission is deliberately narrow: the player's mode on, and one map surface to aim it at.
 * Which frames carry a single surface is the shared reading's to answer and is pinned beside it;
 * what is pinned here is that this permission is withheld whenever it answers nothing.
 *
 * <p>Where this parts from the cover that shares its mode is pinned too. It asks nothing about
 * vanilla map hosts, those being the frames the suppression exists for rather than the frames it
 * stands down on, and it answers for a widget drawn to nothing - which the box reads beside it do
 * not, and which is what lets a widget already switched off be recognised and handed back.
 */
final class RandomAssortmentOfThingsMinimapSuppressionTest {

    private static final float DRAWN_TO_NOTHING = 0f;
    private static final float FULLY_DRAWN = 1f;

    // Faults if it is asked, so a case that must not reach the widget tree says so by construction.
    // The live walk costs a descent through the core UI, which an install without the mode must not
    // pay for.
    private static final Supplier<EmbeddedMap> MAP_NOT_TO_BE_WALKED_FOR = () -> {
        throw new AssertionError("the widget tree must not be walked while the mode is off");
    };

    @Nested
    class ResolveSuppressibleMinimap {

        @Test
        void resolveSuppressibleMinimapAnswersTheOneEmbeddedMapWidget() {
            // What the mode permits: one docked minimap, which the script beside this switches off
            // for as long as its owner keeps it parked.
            var minimapFake = createMinimapWidget(FULLY_DRAWN);

            assertThat(buildSuppression(() -> new EmbeddedMap(minimapFake, List.of()))
                    .resolveSuppressibleMinimap())
                .isSameAs(minimapFake);
        }

        @Test
        void resolveSuppressibleMinimapAnswersAMinimapAlreadyDrawnToNothing() {
            // The read is the widget rather than what shows of it, and it has to stay that way: the
            // moment the suppression lands, a widget sifted by what it is drawn at would stop being
            // reported and the minimap would be parked for good.
            var minimapFake = createMinimapWidget(DRAWN_TO_NOTHING);

            assertThat(buildSuppression(() -> new EmbeddedMap(minimapFake, List.of()))
                    .resolveSuppressibleMinimap())
                .isSameAs(minimapFake);
        }

        @Test
        void resolveSuppressibleMinimapAnswersNothingWhileTheModeIsNotEngaged() {
            // Inert without the mode, and asked before the walk, so an install that never switched
            // the mode on pays one boolean.
            assertThat(new RandomAssortmentOfThingsMinimapSuppression(
                        buildDisengagedMode(), MAP_NOT_TO_BE_WALKED_FOR)
                    .resolveSuppressibleMinimap())
                .isNull();
        }

        @Test
        void resolveSuppressibleMinimapAnswersNothingWithNoSingleEmbeddedMapOnScreen() {
            // The shared reading answers nothing when the panel is not built yet, when the reach
            // into the tree broke, and when two surfaces are on screen - which for this rule is the
            // case that matters, the mode naming one mod and the widgets naming none. All three
            // leave nothing this mod has been given leave to write into.
            assertThat(buildSuppression(() -> null).resolveSuppressibleMinimap())
                .isNull();
        }

        @Test
        void resolveSuppressibleMinimapAnswersNothingForAMapThatIsNotAWidget() {
            // A map is recognised by the map interface alone, which promises nothing about being a
            // component - and a widget is what an opacity is written to.
            var embeddedMap = new EmbeddedMap(new SectorMapWidgetFake(), List.of());

            assertThat(buildSuppression(() -> embeddedMap).resolveSuppressibleMinimap())
                .isNull();
        }
    }

    // The ordinary arrangement: the mode engaged over whichever surface a case puts on screen.
    private static RandomAssortmentOfThingsMinimapSuppression buildSuppression(
            Supplier<EmbeddedMap> findSingleEmbeddedMap) {

        return new RandomAssortmentOfThingsMinimapSuppression(
            buildEngagedMode(), findSingleEmbeddedMap);
    }

    // The player's switch on over a minimap standing in for the radar - both halves the mode ANDs.
    private static RandomAssortmentOfThingsMode buildEngagedMode() {
        return buildMode(true);
    }

    // The player's switch off over the same minimap, so what disengages the mode is the switch and
    // not the absence of a map to adapt to.
    private static RandomAssortmentOfThingsMode buildDisengagedMode() {
        return buildMode(false);
    }

    private static RandomAssortmentOfThingsMode buildMode(boolean isModeSwitchedOn) {

        var minimapFake = new CampaignMinimapFake();
        minimapFake.replaceRadarWithMinimap();

        return new RandomAssortmentOfThingsMode(() -> isModeSwitchedOn, minimapFake);
    }

    // A placed map widget, at whatever it is currently drawn at.
    private static PlacedSectorMapWidgetFake createMinimapWidget(float opacity) {
        return new PlacedSectorMapWidgetFake(
            new PositionFake(new Rectangle(20f, 30f, 200f, 150f)), opacity);
    }
}
