package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.maplayers.base.render.MapFrame;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the overlay pass decides, which is only whether to emit at all: past the guard it is a
 * walk over the traced paths, and it needs a live GL context to run.
 *
 * <p>Both skips are observed as "the paths were never read", so they hold without a context and
 * without mocking the driver. The empty one is what keeps a diagnostic off the bill of every
 * normal frame: the bake holds no path for any cell while the player has the overlay off, so an
 * off toggle has to cost this pass an emptiness check and nothing more.
 */
final class CellRibbonPathRendererTest {

    // A frame that paints, and one at the far end of the map's fade. The scale is the same in
    // both and is never read: what these cases are about is the guard in front of the emission.
    private static final MapFrame PAINTING_FRAME = new MapFrame(1f, 1f);
    private static final MapFrame FADED_OUT_FRAME = new MapFrame(1f, 0f);

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapReadsNoPathsWhenTheOverlayIsOff() {

            var ribbonPathsFake = new RibbonPathsFake(List.of());

            CellRibbonPathRenderer.renderOnMap(
                ribbonPathsFake,
                PAINTING_FRAME);

            assertThat(ribbonPathsFake.hasReadPaths())
                .isFalse();
        }

        @Test
        void renderOnMapReadsNoPathsWhenTheOverlayIsFullyFadedOut() {

            // A cell that does carry a path, so only the fade can be what stops it.
            var ribbonPathsFake = new RibbonPathsFake(List.of(
                new CellRibbonPath(
                    List.of(new float[] {0f, 0f, 1f, 1f}),
                    List.of(),
                    new float[] {0f, 0f},
                    RibbonPathVerdict.LAID_AT_PAD)));

            CellRibbonPathRenderer.renderOnMap(
                ribbonPathsFake,
                FADED_OUT_FRAME);

            assertThat(ribbonPathsFake.hasReadPaths())
                .isFalse();
        }
    }

    // A hand-built path list that reports whether the pass got past the guard. Only the walk the
    // emission itself makes is recorded, so the guard's own emptiness check does not count as
    // having reached for something to paint.
    private static final class RibbonPathsFake extends AbstractCollection<CellRibbonPath> {

        private final Collection<CellRibbonPath> ribbonPaths;

        private boolean hasReadPaths;

        private RibbonPathsFake(Collection<CellRibbonPath> ribbonPaths) {
            this.ribbonPaths = ribbonPaths;
        }

        @Override
        public Iterator<CellRibbonPath> iterator() {
            hasReadPaths = true;
            return ribbonPaths.iterator();
        }

        @Override
        public int size() {
            return ribbonPaths.size();
        }

        private boolean hasReadPaths() {
            return hasReadPaths;
        }
    }
}
