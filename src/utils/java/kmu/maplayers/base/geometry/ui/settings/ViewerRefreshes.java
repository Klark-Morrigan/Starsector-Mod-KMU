package kmu.maplayers.base.geometry.ui.settings;

/**
 * What the settings panel can ask the viewer to redo when a knob moves.
 *
 * <p>One method per unit of work rather than a single "something changed", because the units
 * cost wildly different amounts: a colour needs only a repaint, a void knob needs its own
 * layer rebuilt, and a geometry knob needs the partition itself built again over every site.
 * A panel that could only say "changed" would have to be answered with the most expensive of
 * those every time, and dragging a colour slider would rebuild the sector.
 *
 * <p>An interface rather than a bundle of {@code Runnable}s so each call reads as what it
 * does at the point a knob is wired to it, and so the viewer's obligations to its panel are
 * stated in one place instead of inferred from a constructor's argument order.
 */
public interface ViewerRefreshes {

    /** Builds the partition again and everything downstream of it. */
    public void rebuildGeometry();

    /** Traces the coasts again, and lays everything that stands on them. */
    public void refreshCoastlines();

    /** Traces the unclipped partition again. */
    public void refreshUnboundedCells();

    /** Draws the map again, with nothing recomputed. */
    public void repaintMap();
}
