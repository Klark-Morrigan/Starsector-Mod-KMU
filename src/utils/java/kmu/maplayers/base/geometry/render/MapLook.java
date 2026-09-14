package kmu.maplayers.base.geometry.render;

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
 * <p>What the SLIDERS are set to is not here. That is the viewer's own settings, which open on
 * these and is then free to move; the values here are what the map looks like when nobody has
 * chosen.
 */
public final class MapLook {

    // Wider than a cell edge, because a span is read against a fill rather than against the
    // black, and it has to stay findable at the zoom where a whole pocket fits on screen.
    public static final float SPAN_STROKE = 120f;

    // How wide the mark on a run that crosses a cell is drawn. Heavier than any other line
    // here, because it has to be findable at the zoom where a whole sector fits on screen -
    // and shared, because two drawings marking the same fault at two weights read as two
    // different findings.
    public static final float CROSSING_STROKE = 240f;

    // A fill's own outline is read against the fill rather than against the black, so it wants
    // a fraction of the weight a line crossing open void needs.
    public static final float FILL_EDGE_STROKE = SPAN_STROKE / 4f;

    // How wide a cell's own border is drawn.
    public static final float RING_STROKE = 90f;

    // How wide a cell's inset contour and the centreline under it are drawn. Lighter than the
    // cluster border above, because a cell edge inside a cluster is a division within one body
    // while the ring is where that body stops.
    public static final float CELL_STROKE = 30f;

    // How large the dot marking a system's own position is drawn. Big enough to find at the
    // zoom where a whole sector fits, and small enough not to cover the cell it sits in.
    public static final float SITE_RADIUS = 120f;

    public static final int OPAQUE_ALPHA = 255;

    public static final Color OWNED_CELL = new Color(0x4a, 0x8a, 0xd0);
    public static final Color UNOWNED_CELL = new Color(0x55, 0x55, 0x55);
    public static final Color UNBOUNDED_CELL = new Color(0x30, 0x30, 0x38);
    public static final Color CHANNEL = new Color(0x22, 0x22, 0x26);

    // The line down the middle of a channel: the true border two neighbouring cells share,
    // which each of them insets away from by the same distance. Its own colour because it is
    // its own thing - not the edge of anything drawn, but the line those edges were measured
    // from, and the only place the partition itself is visible once the fills are in.
    public static final Color CENTRELINE = new Color(0x50, 0x50, 0x58);

    // What a name is written in, whether it names a cell or a piece of void. One colour for
    // both because a name is not a shape: it says what a reader is looking at rather than
    // marking out anything, and two colours would imply a distinction the map already makes
    // with the shapes underneath.
    public static final Color REGION_NAME = new Color(0xff, 0xff, 0xff);

    // A coast: each touching-connected run of cells traced as its own closed line, with no
    // spans laid. Deliberately unlike anything else drawn, because it is an edge the geometry
    // derives rather than one of the cluster borders underneath - and reading it as one of the
    // shapes below is the one mistake that would make it look right when it is not.
    public static final Color CONTINENT_COAST = new Color(0xc0, 0x60, 0xff);

    // A bridge offered to a sector that already has continent coastlines on it, and kept
    // because it spans open sea rather than void a coast had already taken. Its own colour
    // rather than the coast's: a span and the line that judged it meet all over the map, and
    // one colour for both would leave a reader unable to see which of them refused the other.
    public static final Color CONTINENT_BRIDGE = new Color(0x40, 0xd0, 0xff);

    // A span joining two continents rather than tidying one. Its own colour because the two
    // sets are laid under opposite rules and are on screen together: what a reader is looking
    // for is which links the sector gains, and a colour shared with the spans that round a
    // single outline up would make the two acts look like one.
    public static final Color INTERCONTINENTAL_BRIDGE = new Color(0xa0, 0xff, 0x40);

    // A stretch of frontage the smoothing decided not to pass through, drawn on the border it
    // sits on. Diagnostic rather than decorative, and unlike either coast colour: what it is
    // for is being seen NEXT TO the line that replaced it, so the eye can judge what the drop
    // actually bought. A shade of the coast's own colour would read as part of the coast.
    public static final Color DROPPED_STRETCH = new Color(0xff, 0x60, 0xc0);

    // The stretch of a cell's border a bridge may anchor on: the part the coast actually runs
    // along, and so the only part exposed to the water a bridge would cross.
    //
    // Diagnostic, and drawn ON the coastline it is a subset of - so it has to be told from the
    // coast underneath it at a glance, which a shade of the coast's own purple would not be.
    // Warm against that purple, and distinct from the dropped stretches it will often sit
    // beside, since a stretch dropped from the coast is precisely one that is NOT eligible.
    public static final Color BRIDGE_FRONTAGE = new Color(0xff, 0xc0, 0x30);

    // The stretch of a cell's border a straight line could arrive at from the open void, drawn
    // on the border it sits on.
    //
    // Read against the two above rather than against the coast, since that is the comparison it
    // is for: what a stretch dropped from the coast would still have offered, and how much of
    // what the coast DID pass through nothing could ever have anchored on. So it is cool where
    // both of those are warm, and no shade of either, or a reader would be judging one of them
    // against a lighter copy of itself.
    public static final Color LANDABLE_FRONTAGE = new Color(0x40, 0xe0, 0xd0);

    // Exactly the coast's own weight, and taken from it rather than restated. What this draws
    // is a stretch of that line in another colour, so any other weight would show as a band
    // beside the coast - a second line, where there is only one line with two kinds of stretch.
    public static final float FRONTAGE_STROKE = RING_STROKE;

    // The mark for a frontage that is a single point: a cell squeezed by its neighbours until
    // its whole eligible stretch collapsed to the one place a span may start. An open path of
    // one point draws NOTHING, and about a third of a sector's eligible frontage is exactly
    // that - so without a mark, a span appears to leave a coast that was never marked
    // bridgeable. A touch wider than the line it sits on, so it reads as a tick rather than
    // vanishing into the stroke.
    public static final float FRONTAGE_DOT_RADIUS = FRONTAGE_STROKE * 1.5f;

    // The void a coast shuts in, filled. Its own colour rather than the coastline's, so that
    // a wall and what it encloses read as two things: sharing one would paint the water in the
    // colour of the line that closed it.
    public static final Color CONTINENT_COASTAL_VOID = new Color(0x90, 0x50, 0xc0);

    // The void the cells close around before anything divides it. No shade of any layer drawn
    // over it, because that is the comparison it exists for: what a construction did to the
    // void has to read as a difference from this rather than as a lighter copy of it.
    public static final Color BARE_VOID = new Color(0x20, 0x70, 0x60);

    // The two halves of a coast crossing a cell, in colours nothing else on the map uses: the
    // run that goes where it should not, and the cell it goes into. Diagnostic rather than
    // decorative - when the construction is right, neither is ever drawn.
    public static final Color COAST_CROSSING = new Color(0xff, 0x20, 0x50);
    public static final Color PIERCED_CELL = new Color(0xff, 0x90, 0x20);

    public static final Color SITE = new Color(0x88, 0x88, 0x88);

    private MapLook() {
    }
}
