package kmu.maplayers.politicalmap.base.render.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two conditions under which the pass emits nothing at all, which is the only decision it
 * makes: everything past the guard is a walk over the baked bands in order, and it needs a live GL
 * context to run. Both skips are observed as "the bands were never read" rather than as an absence
 * of GL calls, so the checks hold without a context and without mocking the driver.
 *
 * <p>Worth holding because neither skip shows on screen when it stops happening. A sector where no
 * cell carries a band is the normal case rather than an edge one, and reaching the emission for it
 * - or for a frame the map has already faded out - costs a state push, a blend-mode switch, and a
 * walk, all for pixels that could not appear.
 */
final class CellPresenceRibbonRendererTest {

    private static final float FULL_ALPHA = 1f;
    private static final float FADED_OUT_ALPHA = 0f;
    private static final float ANY_MAP_FACTOR = 1f;

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapReadsNoBandsWhenNoCellCarriesOne() {

            var ribbonsFake = new RibbonsFake(List.of());

            CellPresenceRibbonRenderer.renderOnMap(
                ribbonsFake,
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
                ANY_MAP_FACTOR,
                FADED_OUT_ALPHA);

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
