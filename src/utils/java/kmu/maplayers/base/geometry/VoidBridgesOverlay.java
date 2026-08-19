package kmu.maplayers.base.geometry;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.util.List;

/**
 * The second construction over the void, drawn: void held between two cells that face each
 * other across a short gap.
 *
 * <p>Its own class so it can be switched off and drawn beside the coast without either
 * being able to disturb the other. Its fills and its spans are one construction seen twice:
 * a span is a line across void saying "this much is held between these two cells", and a
 * fill is what a run of those closes around.
 *
 * <p>See {@link VoidBridgePockets} for why the bridges can be turned back into a reach and
 * traced rather than offset.
 */
final class VoidBridgesOverlay {

    private final ViewerSettings settings;

    private List<CellGaps.CellGap> bridges = List.of();
    private List<List<double[]>> captured = List.of();

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

        captured = bridges.isEmpty()
            ? List.of()
            : VoidBridgePockets.findCapturedPockets(
                fixture.getSites(),
                bridges,
                settings.parameters,
                ViewerPainting.resolvePocketShaping(settings));
    }

    /**
     * Draws the void the bridges close around, beneath the cells.
     *
     * <p>Under them for the same reason the other construction's fills are: a stray edge
     * then reads as the mistake it is rather than painting over the shape it got wrong.
     *
     * @param g2 what to draw with
     */
    void paintFills(Graphics2D g2) {

        g2.setStroke(new BasicStroke(ViewerPainting.FILL_EDGE_STROKE));

        for (var outline : captured) {

            ViewerPainting.paintFilledShape(
                g2,
                ViewerPainting.buildPath(outline),
                settings.wideVoidColour,
                settings.voidCellOpacity,
                settings.wideVoidEdge);
        }
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
