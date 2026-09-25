package kmu.maplayers.ownermap.render.ribbon;

import kmlib.opengl.GlPasses;

import kmu.maplayers.base.render.MapFrame;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what the pass decides, which is only whether to emit at all: everything past the guard is a
 * walk over the baked bands in order, and it needs a live GL context to run.
 *
 * <p>The two skips are observed as "the bands were never read", so those checks hold without a
 * context and without mocking the driver. Worth holding because neither shows on screen when it
 * stops happening. A sector where no cell carries a band is the normal case rather than an edge
 * one, and reaching the emission for it - or for a frame the map has already faded out - costs a
 * state push, a blend-mode switch, and a walk, all for pixels that could not appear.
 *
 * <p>The zoom case is the other direction and is observed at the GL entry point instead, since a
 * frame that goes on to draw cannot be watched from the band list without a context. Standing the
 * pass down over a scale is what it must never do: every size a band carries is settled in the
 * world, so how small it lands on screen is the map's own scaling of it and no judgement of this
 * pass - and a band withheld looks exactly like a system with no rival in it, which is the one
 * thing the readout exists to tell apart.
 */
final class CellPresenceRibbonRendererTest {

    // A frame that paints, and one at the far end of the map's fade. The scale is the same in
    // both and is never read: what these cases are about is the guard in front of the emission.
    private static final MapFrame PAINTING_FRAME = new MapFrame(1f, 1f);
    private static final MapFrame FADED_OUT_FRAME = new MapFrame(1f, 0f);

    // A painting frame zoomed far enough out that a two-hundred-unit band lands well under a pixel
    // across, which is the scale a screen-sized rule would have refused.
    private static final MapFrame ZOOMED_FAR_OUT_FRAME = new MapFrame(0.001f, 1f);

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapReadsNoBandsWhenNoCellCarriesOne() {

            var ribbonsFake = new RibbonsFake(List.of());

            CellPresenceRibbonRenderer.renderOnMap(
                ribbonsFake,
                PAINTING_FRAME);

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
                FADED_OUT_FRAME);

            assertThat(ribbonsFake.hasReadBands())
                .isFalse();
        }

        @Test
        void renderOnMapEmitsTheBandsHoweverFarTheMapIsZoomedOut() {

            // The pass is watched at the GL entry point rather than at the band list, since the
            // walk behind it is what needs a context: reaching the entry point at all is the whole
            // of what "the scale did not stand this frame down" means.
            try (var glPassesMock = mockStatic(GlPasses.class)) {

                CellPresenceRibbonRenderer.renderOnMap(
                    buildRibbonsCarryingOneBand(),
                    ZOOMED_FAR_OUT_FRAME);

                glPassesMock.verify(() -> GlPasses.runBlendedPass(any(), any(), any()));
            }
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
