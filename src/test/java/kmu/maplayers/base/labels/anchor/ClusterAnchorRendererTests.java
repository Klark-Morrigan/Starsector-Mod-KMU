package kmu.maplayers.base.labels.anchor;

import kmu.maplayers.base.render.MapFrame;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the overlay pass decides, which is only whether to emit at all: past the guard it is a
 * walk over the anchors, and it needs a live GL context to run.
 *
 * <p>Both skips are observed as "the anchors were never read", so they hold without a context. The
 * empty one is what keeps a diagnostic off the bill of every normal frame: no anchor is built while
 * the player has the overlay off, so an off toggle has to cost this pass an emptiness check alone.
 */
final class ClusterAnchorRendererTests {

    // A frame that paints, and one at the far end of the map's fade. The scale is never read: what
    // these cases are about is the guard in front of the emission.
    private static final MapFrame PAINTING_FRAME = new MapFrame(1f, 1f);
    private static final MapFrame FADED_OUT_FRAME = new MapFrame(1f, 0f);

    @Nested
    class RenderOnMap {

        @Test
        void readsNoAnchorsWhenNoneWereBuilt() {

            var anchorsFake = new AnchorsFake(List.of());

            ClusterAnchorRenderer.renderOnMap(anchorsFake, PAINTING_FRAME);

            var actualHasReadAnchors = anchorsFake.hasReadAnchors();

            assertThat(actualHasReadAnchors)
                .isFalse();
        }

        @Test
        void readsNoAnchorsWhenTheOverlayIsFullyFadedOut() {
            // An anchor that is there to draw, so only the fade can be what stops it.
            var anchorsFake = new AnchorsFake(List.of(buildAnchor()));

            ClusterAnchorRenderer.renderOnMap(anchorsFake, FADED_OUT_FRAME);

            var actualHasReadAnchors = anchorsFake.hasReadAnchors();

            assertThat(actualHasReadAnchors)
                .isFalse();
        }
    }

    // One placement with no fitted line, which is all the guard can be handed: nothing past it runs.
    private static ClusterAnchor buildAnchor() {

        return new ClusterAnchor(
            new ClusterIdentity("hegemony", Set.of(buildCellKey("corvus"))),
            0f,
            0f,
            Color.WHITE,
            List.of(),
            0f,
            null,
            null,
            null,
            0f,
            0);
    }

    // A hand-built anchor list that reports whether the pass got past the guard. Only the reads the
    // emission makes are recorded, so the guard's own emptiness check does not count as having
    // reached for something to paint.
    private static final class AnchorsFake extends AbstractList<ClusterAnchor> {

        private final List<ClusterAnchor> anchors;

        private boolean hasReadAnchors;

        private AnchorsFake(List<ClusterAnchor> anchors) {
            this.anchors = anchors;
        }

        @Override
        public ClusterAnchor get(int index) {

            hasReadAnchors = true;
            return anchors.get(index);
        }

        @Override
        public Iterator<ClusterAnchor> iterator() {

            hasReadAnchors = true;
            return anchors.iterator();
        }

        @Override
        public int size() {
            return anchors.size();
        }

        private boolean hasReadAnchors() {
            return hasReadAnchors;
        }
    }
}
