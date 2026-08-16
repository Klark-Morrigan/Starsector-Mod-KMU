package kmu.maplayers.base.render.clusters;

import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.ThemeFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two conditions under which either pass emits nothing at all, which is the only decision
 * either makes: everything past the guard is a walk over the lists in order, and it needs a live GL
 * context to run. Both skips are observed as "the draw lists were never read" rather than as an
 * absence of GL calls, so the check holds without a context and without mocking the driver.
 *
 * <p>Each entry point is held to both guards in its own right. They are separate entries precisely
 * so a caller can reach one without the other, so a guard proven on the fills says nothing about
 * what the borders do when asked for an empty frame.
 *
 * <p>Reaching the emission for an already-faded or empty frame costs a state push, a blend-mode
 * switch, and a walk over every run, all for pixels that could not appear - and nothing on screen
 * would show that it happened, which is why the guard is worth holding.
 *
 * <p>The draw lists here are hand-built rather than a layer's own built state, which is the point
 * of the seam: a framework test that reached for the political map's model would re-couple exactly
 * what {@link ClusterDrawLists} separates, and would no longer prove that a second layer can
 * satisfy it.
 */
final class ClusterRendererTest {
    
    private static final float FULL_ALPHA = 1f;
    private static final float FADED_OUT_ALPHA = 0f;
    private static final float ANY_MAP_FACTOR = 1f;

    @Nested
    class RenderFillsOnMap {

        @Test
        void renderFillsOnMapReadsNoDrawListsWhenThereIsNothingToPaint() {
            var drawListsFake = new ClusterDrawListsFake(true);

            ClusterRenderer.renderFillsOnMap(drawListsFake, ANY_MAP_FACTOR, FULL_ALPHA);

            assertThat(drawListsFake.hasReadDrawLists())
                .isFalse();
        }

        @Test
        void renderFillsOnMapReadsNoDrawListsWhenTheOverlayIsFullyFadedOut() {
            // Non-empty, so only the fade can be what stops it: at the ends of the map's zoom fade
            // every run would emit at zero effective alpha, paying the whole pass for nothing.
            var drawListsFake = new ClusterDrawListsFake(false);

            ClusterRenderer.renderFillsOnMap(drawListsFake, ANY_MAP_FACTOR, FADED_OUT_ALPHA);

            assertThat(drawListsFake.hasReadDrawLists())
                .isFalse();
        }
    }

    @Nested
    class RenderBordersOnMap {

        @Test
        void renderBordersOnMapReadsNoDrawListsWhenThereIsNothingToPaint() {
            var drawListsFake = new ClusterDrawListsFake(true);

            ClusterRenderer.renderBordersOnMap(drawListsFake, ANY_MAP_FACTOR, FULL_ALPHA);

            assertThat(drawListsFake.hasReadDrawLists())
                .isFalse();
        }

        @Test
        void renderBordersOnMapReadsNoDrawListsWhenTheOverlayIsFullyFadedOut() {
            // Non-empty, so only the fade can be what stops it: at the ends of the map's zoom fade
            // every run would emit at zero effective alpha, paying the whole pass for nothing.
            var drawListsFake = new ClusterDrawListsFake(false);

            ClusterRenderer.renderBordersOnMap(drawListsFake, ANY_MAP_FACTOR, FADED_OUT_ALPHA);

            assertThat(drawListsFake.hasReadDrawLists())
                .isFalse();
        }
    }

    // A hand-built draw list that reports whether the pass got past the guard. It answers the two
    // maps and the global tier only after recording the read, so "nothing was painted" is observed
    // where the emission would first reach for something to paint - which is checkable without a
    // GL context, unlike the emission itself.
    private static final class ClusterDrawListsFake implements ClusterDrawLists {

        private static final UiElementPaint HIDDEN_PAINT = new UiElementPaint(null, 0f);

        private final boolean isEmpty;

        private boolean hasReadDrawLists;

        private ClusterDrawListsFake(boolean isEmpty) {
            this.isEmpty = isEmpty;
        }

        @Override
        public boolean isEmpty() {
            return isEmpty;
        }

        @Override
        public GlobalStyle getGlobalStyle() {
            hasReadDrawLists = true;
            return ThemeFixtures.createInertGlobalStyle();
        }

        @Override
        public Map<String, StyledCell> getStyledCellByCellId() {
            hasReadDrawLists = true;
            return Map.of(
                "cell",
                new StyledCell.FusedCell(new float[0], HIDDEN_PAINT, 0f));
        }

        @Override
        public Map<String, StyledClusterGroup> getStyledClusterGroupByOwnerId() {
            hasReadDrawLists = true;
            return Map.of(
                "owner",
                new StyledClusterGroup(
                    List.of(new StyledCluster(
                        new float[0],
                        new float[0],
                        new float[0],
                        List.of())),
                    HIDDEN_PAINT,
                    HIDDEN_PAINT,
                    0f));
        }

        private boolean hasReadDrawLists() {
            return hasReadDrawLists;
        }
    }
}
