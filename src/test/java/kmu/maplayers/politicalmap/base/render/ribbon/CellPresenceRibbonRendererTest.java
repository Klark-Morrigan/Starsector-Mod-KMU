package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the three conditions under which the pass emits nothing at all, which is the only decision
 * it makes: everything past the guard is a walk over the baked bands in order, and it needs a live
 * GL context to run. Every skip is observed as "the bands were never read" rather than as an
 * absence of GL calls, so the checks hold without a context and without mocking the driver.
 *
 * <p>Worth holding because no skip shows on screen when it stops happening. A sector where no cell
 * carries a band is the normal case rather than an edge one, and reaching the emission for it - or
 * for a frame the map has already faded out - costs a state push, a blend-mode switch, and a walk,
 * all for pixels that could not appear. The zoom skip is the one that shows as something rather
 * than as nothing: past it the bands are thinner than a pixel, and what they paint is a
 * discolouring of the borders they run inside.
 */
final class CellPresenceRibbonRendererTest {

    private static final float FULL_ALPHA = 1f;
    private static final float FADED_OUT_ALPHA = 0f;
    private static final float ANY_MAP_FACTOR = 1f;

    // A band 100 units wide against a two-pixel floor: at a map scale of 0.01 it draws a pixel
    // across, which is under the floor, and at 1 it draws far above it.
    private static final float ZOOMED_OUT_MAP_FACTOR = 0.01f;
    private static final RibbonStyle STYLE = new RibbonStyle(
        100.0,
        50.0,
        2.0,
        new RibbonSegmentLengths(3, 1),
        2.0);

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapReadsNoBandsWhenNoCellCarriesOne() {

            var ribbonsFake = new RibbonsFake(List.of());

            CellPresenceRibbonRenderer.renderOnMap(
                ribbonsFake,
                STYLE,
                ANY_MAP_FACTOR,
                FULL_ALPHA);

            assertThat(ribbonsFake.hasReadBands())
                .isFalse();
        }

        @Test
        void renderOnMapReadsNoBandsWhenTheOverlayIsFullyFadedOut() {

            // A cell that does carry a band, so only the fade can be what stops it: at the ends of
            // the map's zoom fade every run would emit at zero effective alpha.
            var ribbonsFake = buildRibbonsCarryingOneBand();

            CellPresenceRibbonRenderer.renderOnMap(
                ribbonsFake,
                STYLE,
                ANY_MAP_FACTOR,
                FADED_OUT_ALPHA);

            assertThat(ribbonsFake.hasReadBands())
                .isFalse();
        }

        @Test
        void renderOnMapReadsNoBandsWhenTheyWouldDrawThinnerThanTheFloor() {

            // Zoomed far enough out that the world-sized bands fall under the player's floor. The
            // map is still lit and the cells still carry bands - only the scale stops it.
            var ribbonsFake = buildRibbonsCarryingOneBand();

            CellPresenceRibbonRenderer.renderOnMap(
                ribbonsFake,
                STYLE,
                ZOOMED_OUT_MAP_FACTOR,
                FULL_ALPHA);

            assertThat(ribbonsFake.hasReadBands())
                .isFalse();
        }
    }

    private static RibbonsFake buildRibbonsCarryingOneBand() {
        return new RibbonsFake(List.of(
            new CellRibbon(List.of(new RibbonBand(Color.WHITE, new float[0])))));
    }

    // A hand-built band list that reports whether the pass got past the guard. Only the walk the
    // emission itself makes is recorded, so the guard's own emptiness check does not count as
    // having reached for something to paint.
    private static final class RibbonsFake extends AbstractCollection<CellRibbon> {

        private final Collection<CellRibbon> ribbons;

        private boolean hasReadBands;

        private RibbonsFake(Collection<CellRibbon> ribbons) {
            this.ribbons = ribbons;
        }

        @Override
        public Iterator<CellRibbon> iterator() {
            hasReadBands = true;
            return ribbons.iterator();
        }

        @Override
        public int size() {
            return ribbons.size();
        }

        private boolean hasReadBands() {
            return hasReadBands;
        }
    }
}
