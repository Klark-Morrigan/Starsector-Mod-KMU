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

    private List<CellGap> bridges = List.of();
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

        // Built while either half of it is on screen. The walls and the fills are one
        // construction seen twice, so a frame showing one of them has already paid for both.
        var wanted = settings.showInlandBridges || settings.showInlandFill;

        bridges = wanted
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
                settings.resolvePocketShaping());
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

        if (!settings.showInlandFill) {
            return;
        }

        g2.setStroke(new BasicStroke(MapLook.FILL_EDGE_STROKE));

        for (var outline : captured) {

            MapPainting.paintFilledShape(
                g2,
                MapPainting.buildPath(outline),
                settings.inlandVoidColour,
                settings.voidFillOpacity,
                settings.inlandVoidEdge);
        }
    }

    /**
     * Draws every bridge, over the top of everything.
     *
     * <p>Untrimmed: there is no inset fill for a bridge to stop short of, so it spans the
     * corridor it actually holds.
     *
     * @param g2 what to draw with
     */
    void paintSpans(Graphics2D g2) {

        if (!settings.showInlandBridges) {
            return;
        }

        g2.setStroke(new BasicStroke(MapLook.SPAN_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.voidBridgeColour, MapLook.OPAQUE_ALPHA));

        for (var bridge : bridges) {
            g2.draw(MapPainting.buildSpanLine(bridge));
        }
    }
}
