package kmu.maplayers.base.geometry;

import java.awt.Color;

/**
 * What the map is painted with, whichever surface is painting it.
 *
 * <p>This map is drawn twice: live in the window, and again into an SVG that a reader can keep.
 * Both draw the same shapes and have to draw them the same way, so what they draw WITH is one
 * statement rather than one per surface. Two copies of a stroke width is how two pictures of one
 * map come to be drawn at weights that were meant to match and quietly do not, and two copies of
 * a colour is how a fault marked in one is a different fault in the other.
 *
 * <p>Not a look for the window. The strokes are not only decoration: a stroke is how far either
 * side of the true edge the drawn edge reaches, which decides what a reader can see being on the
 * wrong side of it - so the same width has to be used by the drawing and by the check that says
 * whether a run of coast is visibly inside a cell.
 *
 * <p>What the SLIDERS are set to is not here. That is {@link ViewerSettings}, which opens on
 * these and is then free to move; the values here are what the map looks like when nobody has
 * chosen.
 */
final class MapLook {

    // Wider than a cell edge, because a span is read against a fill rather than against the
    // black, and it has to stay findable at the zoom where a whole pocket fits on screen.
    static final float SPAN_STROKE = 120f;

    // How wide the mark on a run that crosses a cell is drawn. Heavier than any other line
    // here, because it has to be findable at the zoom where a whole sector fits on screen -
    // and shared, because two drawings marking the same fault at two weights read as two
    // different findings.
    static final float CROSSING_STROKE = 240f;

    // A fill's own outline is read against the fill rather than against the black, so it wants
    // a fraction of the weight a line crossing open void needs.
    static final float FILL_EDGE_STROKE = SPAN_STROKE / 4f;

    // How wide a cell's own border is drawn.
    static final float RING_STROKE = 90f;

    static final int OPAQUE_ALPHA = 255;

    static final Color OWNED_CELL = new Color(0x4a, 0x8a, 0xd0);
    static final Color UNOWNED_CELL = new Color(0x55, 0x55, 0x55);
    static final Color UNBOUNDED_CELL = new Color(0x30, 0x30, 0x38);
    static final Color VOID_CELL = new Color(0xb0, 0x8a, 0x30);
    static final Color WIDE_VOID = new Color(0x30, 0xa0, 0xb0);
    static final Color CHANNEL = new Color(0x22, 0x22, 0x26);

    // The line down the middle of a channel: the true border two neighbouring cells share,
    // which each of them insets away from by the same distance. Its own colour because it is
    // its own thing - not the edge of anything drawn, but the line those edges were measured
    // from, and the only place the partition itself is visible once the fills are in.
    static final Color CENTRELINE = new Color(0x50, 0x50, 0x58);

    // A line laid across void to close it off - a bridge between two cells that face each
    // other. Deliberately unlike anything else on the map: it is a proposal about where a
    // boundary could go, not a thing that has one, and reading it as an existing border is the
    // one mistake that would make the shape look right when it is not.
    static final Color VOID_BRIDGE = new Color(0xff, 0xd0, 0x40);

    // What a name is written in, whether it names a cell or a piece of void. One colour for
    // both because a name is not a shape: it says what a reader is looking at rather than
    // marking out anything, and two colours would imply a distinction the map already makes
    // with the shapes underneath.
    static final Color REGION_NAME = new Color(0xff, 0xff, 0xff);

    // The smoothed outer edge. Unlike anything else drawn, because it is a proposal about where
    // the edge could be rather than an edge anything has: reading it as one of the shapes
    // underneath is the one mistake that would make it look right when it is not.
    static final Color COASTLINE = new Color(0x70, 0xe0, 0x90);

    // The two halves of a coast crossing a cell, in colours nothing else on the map uses: the
    // run that goes where it should not, and the cell it goes into. Diagnostic rather than
    // decorative - when the construction is right, neither is ever drawn.
    static final Color COAST_CROSSING = new Color(0xff, 0x20, 0x50);
    static final Color PIERCED_CELL = new Color(0xff, 0x90, 0x20);

    static final Color SITE = new Color(0x88, 0x88, 0x88);

    private MapLook() {
    }
}
