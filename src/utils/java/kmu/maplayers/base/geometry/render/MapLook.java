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

    public static final int OPAQUE_ALPHA = 255;

    public static final Color OWNED_CELL = new Color(0x4a, 0x8a, 0xd0);
    public static final Color UNOWNED_CELL = new Color(0x55, 0x55, 0x55);
    public static final Color UNBOUNDED_CELL = new Color(0x30, 0x30, 0x38);
    // The void the cells and their bridges shut in, filled. Not a colour for void "within" a
    // cell: the apron a cell leaves between its own bound and its inset fill is the unbounded
    // partition showing through from underneath, so it takes that colour and needs none of
    // its own.
    public static final Color INLAND_VOID = new Color(0x30, 0xa0, 0xb0);
    public static final Color CHANNEL = new Color(0x22, 0x22, 0x26);

    // The line down the middle of a channel: the true border two neighbouring cells share,
    // which each of them insets away from by the same distance. Its own colour because it is
    // its own thing - not the edge of anything drawn, but the line those edges were measured
    // from, and the only place the partition itself is visible once the fills are in.
    public static final Color CENTRELINE = new Color(0x50, 0x50, 0x58);

    // A line laid across void to close it off - a bridge between two cells that face each
    // other. Deliberately unlike anything else on the map: it is a proposal about where a
    // boundary could go, not a thing that has one, and reading it as an existing border is the
    // one mistake that would make the shape look right when it is not.
    public static final Color VOID_BRIDGE = new Color(0xff, 0xd0, 0x40);

    // The void a reach of the smoothed coast shut in behind it, filled. Apart from the
    // coastline's own colour so that both kinds of pocket read alike - a wall colour and a
    // fill colour each - rather than the coastal fill alone being painted in the colour of
    // the line that closed it.
    public static final Color COASTAL_VOID = new Color(0x30, 0x70, 0xb0);

    // What a name is written in, whether it names a cell or a piece of void. One colour for
    // both because a name is not a shape: it says what a reader is looking at rather than
    // marking out anything, and two colours would imply a distinction the map already makes
    // with the shapes underneath.
    public static final Color REGION_NAME = new Color(0xff, 0xff, 0xff);

    // The smoothed outer edge. Unlike anything else drawn, because it is a proposal about where
    // the edge could be rather than an edge anything has: reading it as one of the shapes
    // underneath is the one mistake that would make it look right when it is not.
    public static final Color COASTLINE = new Color(0x70, 0xe0, 0x90);

    // A rival coast being previewed: each touching-connected run of cells traced as its own
    // closed line, with no bridges laid. Deliberately unlike the settled coastline's colour -
    // the whole point of drawing it is to see where the two constructions and the bridges
    // disagree, and two greens would make agreement and disagreement look alike.
    public static final Color CONTINENT_COAST = new Color(0xc0, 0x60, 0xff);

    // A bridge offered to a sector that already has continent coastlines on it, and kept
    // because it spans open sea rather than void a coast had already taken. Its own colour
    // rather than the settled bridges': the two constructions are laid under different rules
    // and are on screen together to be compared, so one colour for both would hide the very
    // difference being looked at.
    public static final Color CONTINENT_BRIDGE = new Color(0x40, 0xd0, 0xff);

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

    // Exactly the coast's own weight, and taken from it rather than restated. What this draws
    // is a stretch of that line in another colour, so any other weight would show as a band
    // beside the coast - a second line, where there is only one line with two kinds of stretch.
    public static final float FRONTAGE_STROKE = RING_STROKE;

    // The void a continent coast shuts in, filled. Its own colour rather than the settled
    // coast's, for the reason its line has one: the two constructions are on screen together
    // to be compared, and one colour for both would hide the difference being looked at.
    public static final Color CONTINENT_COASTAL_VOID = new Color(0x90, 0x50, 0xc0);

    // The two halves of a coast crossing a cell, in colours nothing else on the map uses: the
    // run that goes where it should not, and the cell it goes into. Diagnostic rather than
    // decorative - when the construction is right, neither is ever drawn.
    public static final Color COAST_CROSSING = new Color(0xff, 0x20, 0x50);
    public static final Color PIERCED_CELL = new Color(0xff, 0x90, 0x20);

    public static final Color SITE = new Color(0x88, 0x88, 0x88);

    private MapLook() {
    }
}
