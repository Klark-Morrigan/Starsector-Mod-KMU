package kmu.maplayers.base.geometry;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.util.List;

/**
 * The second construction over the void, drawn: void held between two cells that face each
 * other across a short gap.
 *
 * <p>Its own class alongside {@link VoidPocketsOverlay}, and drawn in the same colour and
 * stroke as that one's cuts on purpose. Both are a line across void saying "this much is
 * held between these two cells", arrived at from opposite ends - one by dividing a shape the
 * cells closed around, the other by finding pairs that hold something between them with no
 * notion of a shape at all. Two colours would claim they were two kinds of thing, when the
 * whole point of drawing them together is that they are not.
 *
 * <p>It has no fills to lay down. A bridge is a span and nothing else, which is most of what
 * distinguishes this construction from the other.
 */
final class VoidBridgesOverlay {

    private final ViewerSettings settings;

    private List<CellGaps.CellGap> bridges = List.of();

    VoidBridgesOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Finds the bridges again, or drops them when the overlay is switched off.
     *
     * @param fixture the sector to find them in
     */
    void refresh(SectorFixture fixture) {

        bridges = settings.showVoidBridges
            ? VoidBridges.findVoidBridges(
                fixture.getSites(),
                settings.parameters.cellRadius(),
                settings.parameters.cellRadius() * settings.bridgeReachMultiple)
            : List.of();
    }

    /**
     * Draws every bridge, over the top of everything.
     *
     * <p>Untrimmed, unlike a pocket's cuts: there is no inset fill for a bridge to stop
     * short of, so it spans the corridor it actually holds.
     *
     * @param g2 what to draw with
     */
    void paintSpans(Graphics2D g2) {

        g2.setStroke(new BasicStroke(ViewerPainting.SPAN_STROKE));
        g2.setColor(ViewerPainting.applyAlpha(
            settings.sectionCutColour, ViewerPainting.OPAQUE_ALPHA));

        for (var bridge : bridges) {
            g2.draw(ViewerPainting.buildTrimmedSpan(bridge, 0));
        }
    }
}
