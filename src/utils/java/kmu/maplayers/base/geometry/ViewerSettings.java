package kmu.maplayers.base.geometry;

import java.awt.Color;

/**
 * What the viewer is currently set to draw with, and nothing else.
 *
 * <p>State only. No widget builds one of these, no panel is built from one, and it knows
 * nothing about how any of it comes to be chosen - so a reader asking "what can this map be
 * drawn with" gets a list of the answers rather than three hundred lines of Swing with the
 * answers embedded in it. {@link ViewerSettingsPanel} is what edits it and
 * {@link SectorGeometryViewer} is what reads it.
 *
 * <p>Fields rather than accessors, because a panel exists whose whole job is to write them.
 * Accessors would have implied a read-only view that nothing in the package actually has,
 * and paying sixty methods for that fiction is worse than admitting this is a bag of values.
 *
 * <p>The defaults live here rather than with the panel because a default is a property of
 * the setting - what it is when nobody has chosen - while the range a slider allows is a
 * property of the widget. The panel holds those.
 */
final class ViewerSettings {

    static final Color OWNED_CELL_DEFAULT = new Color(0x4a, 0x8a, 0xd0);
    static final Color UNOWNED_CELL_DEFAULT = new Color(0x55, 0x55, 0x55);
    static final Color UNBOUNDED_CELL_DEFAULT = new Color(0x30, 0x30, 0x38);
    static final Color VOID_CELL_DEFAULT = new Color(0xb0, 0x8a, 0x30);
    static final Color WIDE_VOID_DEFAULT = new Color(0x30, 0xa0, 0xb0);
    static final Color CHANNEL_DEFAULT = new Color(0x22, 0x22, 0x26);

    // The line down the middle of a channel: the true border two neighbouring cells share,
    // which each of them insets away from by the same distance. Its own colour because it is
    // its own thing - not the edge of anything drawn, but the line those edges were measured
    // from, and the only place the partition itself is visible once the fills are in.
    static final Color CENTRELINE_DEFAULT = new Color(0x50, 0x50, 0x58);

    // Where a long pocket is cut into sections. Deliberately unlike anything else on the map:
    // the cut is a proposal about where a division could go, not a thing that has been
    // divided, and reading it as an existing border is the one mistake that would make the
    // shape look right when it is not.
    static final Color SECTION_CUT_DEFAULT = new Color(0xff, 0xd0, 0x40);

    // The smoothed outer edge. Unlike anything else drawn, because it is a proposal about
    // where the edge could be rather than an edge anything has: reading it as one of the
    // shapes underneath is the one mistake that would make it look right when it is not.
    static final Color COASTLINE_DEFAULT = new Color(0x70, 0xe0, 0x90);

    // One cell across. Two cells whose frontages are nearer than that are one stretch of
    // coast as far as the eye is concerned, so joining both only redraws the scallop smaller.
    static final double COAST_SKIP_DEFAULT = 1;
    static final double COAST_SKIP_STEP_SCALE = 100.0;

    // Enough that a tightly packed run reads as a line rather than as a row of bites, few
    // enough that a whole coast cannot be swallowed and reduced to a triangle.
    static final double COAST_MAX_SKIPS_DEFAULT = 5;

    // The two halves of a coast crossing a cell, in colours nothing else on the map uses: the
    // run that goes where it should not, and the cell it goes into. Diagnostic rather than
    // decorative - when the construction is right, neither is ever drawn.
    static final Color COAST_CROSSING_DEFAULT = new Color(0xff, 0x20, 0x50);
    static final Color PIERCED_CELL_DEFAULT = new Color(0xff, 0x90, 0x20);

    static final Color SITE_COLOUR = new Color(0x88, 0x88, 0x88);

    // One cell across. A pocket no wider than a single cell has no two sides far enough
    // apart for anything to reach between them, so there is nothing in it to divide.
    static final double VOID_SPAN_DEFAULT = 2;
    static final double VOID_SPAN_STEP_SCALE = 100.0;

    static final double BRIDGE_REACH_DEFAULT = 4;
    static final double BRIDGE_REACH_STEP_SCALE = 100.0;

    // Settled by eye against the sweep at the end of the void regions dump. Above it the
    // only crossings leaving that much on both sides are chords over the open middle, which
    // read as thrown across a pocket rather than dividing it; below it the tips come back
    // into range, win on being narrowest, and leave one long piece uncut behind them.
    static final double MIN_SECTION_DEFAULT = 40;
    static final double MIN_SECTION_SCALE = 100.0;

    static final float JITTER_DEFAULT = 35;
    static final double JITTER_SCALE = 100.0;

    static final int OWNER_FILL_ALPHA = 90;

    Color ownedCellColour = OWNED_CELL_DEFAULT;
    Color ownedCellEdge = OWNED_CELL_DEFAULT;
    Color unownedCellColour = UNOWNED_CELL_DEFAULT;
    Color unownedCellEdge = UNOWNED_CELL_DEFAULT;
    Color unboundedCellColour = UNBOUNDED_CELL_DEFAULT;
    Color unboundedCellEdge = UNBOUNDED_CELL_DEFAULT;
    Color voidCellColour = VOID_CELL_DEFAULT;
    Color voidCellEdge = VOID_CELL_DEFAULT;

    int voidCellOpacity = OWNER_FILL_ALPHA;
    double voidSpanMultiple = VOID_SPAN_DEFAULT;
    double minSectionShare = MIN_SECTION_DEFAULT / MIN_SECTION_SCALE;
    double bridgeReachMultiple = BRIDGE_REACH_DEFAULT;

    // Two constructions over the same void, drawn together so one can be judged against the
    // other on the same map rather than from two screenshots taken minutes apart.
    boolean showVoidPockets = true;
    boolean showVoidBridges = true;

    // A third reading of the same map: not what the void is, but where the edge of what is
    // NOT void could be drawn.
    boolean showCoastlines = true;

    double coastSkipMultiple = COAST_SKIP_DEFAULT;
    int coastMaxSkips = (int) COAST_MAX_SKIPS_DEFAULT;

    Color coastlineColour = COASTLINE_DEFAULT;
    Color coastCrossingColour = COAST_CROSSING_DEFAULT;
    Color piercedCellColour = PIERCED_CELL_DEFAULT;
    Color wideVoidColour = WIDE_VOID_DEFAULT;
    Color wideVoidEdge = WIDE_VOID_DEFAULT;
    Color sectionCutColour = SECTION_CUT_DEFAULT;
    Color siteColour = SITE_COLOUR;
    Color centrelineColour = CENTRELINE_DEFAULT;
    Color channelColour = CHANNEL_DEFAULT;
    Color channelEdge = CHANNEL_DEFAULT;
    
    int channelOpacity = OWNER_FILL_ALPHA;
    int ownedCellOpacity = OWNER_FILL_ALPHA;
    int unownedCellOpacity = OWNER_FILL_ALPHA;
    int unboundedCellOpacity = OWNER_FILL_ALPHA;

    boolean jitterOwned = true;
    boolean jitterUnowned;

    float jitterStrength = (float) (JITTER_DEFAULT / JITTER_SCALE);

    boolean showUnboundedCells;
    
    SectorGeometryParameters parameters = SectorGeometryParameters.createDefaults();
}
