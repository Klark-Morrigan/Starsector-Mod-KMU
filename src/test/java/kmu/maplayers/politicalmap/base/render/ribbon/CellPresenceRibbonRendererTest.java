package kmu.maplayers.politicalmap.base.render.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two conditions under which the pass emits nothing at all, which is the only decision it
 * makes: everything past the guard is a walk over the baked bands in order, and it needs a live GL
 * context to run. Both skips are observed as "the bands were never read" rather than as an absence
 * of GL calls, so the check holds without a context and without mocking the driver.
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
            
            var ribbonsByCellIdFake = new RibbonsByCellIdFake(Map.of());

            CellPresenceRibbonRenderer.renderOnMap(
                ribbonsByCellIdFake,
                ANY_MAP_FACTOR,
                FULL_ALPHA);

            assertThat(ribbonsByCellIdFake.hasReadBands())
                .isFalse();
        }

        @Test
        void renderOnMapReadsNoBandsWhenTheOverlayIsFullyFadedOut() {
            // A cell that does carry a band, so only the fade can be what stops it: at the ends of
            // the map's zoom fade every run would emit at zero effective alpha.
            var ribbonsByCellIdFake = new RibbonsByCellIdFake(Map.of(
                "cell",
                new CellRibbon(List.of(new RibbonBand(Color.WHITE, new float[0])))));

            CellPresenceRibbonRenderer.renderOnMap(
                ribbonsByCellIdFake,
                ANY_MAP_FACTOR,
                FADED_OUT_ALPHA);

            assertThat(ribbonsByCellIdFake.hasReadBands())
                .isFalse();
        }
    }

    // A hand-built band map that reports whether the pass got past the guard. Only the read the
    // emission itself makes - the walk over the cells' bands - is recorded, so the guard's own
    // emptiness check does not count as having reached for something to paint.
    private static final class RibbonsByCellIdFake extends AbstractMap<String, CellRibbon> {

        private final Map<String, CellRibbon> ribbonByCellId;

        private boolean hasReadBands;

        private RibbonsByCellIdFake(Map<String, CellRibbon> ribbonByCellId) {
            this.ribbonByCellId = ribbonByCellId;
        }

        @Override
        public Set<Entry<String, CellRibbon>> entrySet() {
            return ribbonByCellId.entrySet();
        }

        @Override
        public Collection<CellRibbon> values() {
            hasReadBands = true;
            return ribbonByCellId.values();
        }

        private boolean hasReadBands() {
            return hasReadBands;
        }
    }
}
