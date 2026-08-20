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

    // Read off the map's own look rather than restated. A colour written here as well is a
    // second answer to "what is a coastline drawn in", and the window and the SVG then mark
    // the same thing two different ways.
    static final Color OWNED_CELL_DEFAULT = MapLook.OWNED_CELL;
    static final Color UNOWNED_CELL_DEFAULT = MapLook.UNOWNED_CELL;
    static final Color UNBOUNDED_CELL_DEFAULT = MapLook.UNBOUNDED_CELL;
    static final Color VOID_CELL_DEFAULT = MapLook.VOID_CELL;
    static final Color WIDE_VOID_DEFAULT = MapLook.WIDE_VOID;
    static final Color CHANNEL_DEFAULT = MapLook.CHANNEL;
    static final Color CENTRELINE_DEFAULT = MapLook.CENTRELINE;
    static final Color SECTION_CUT_DEFAULT = MapLook.SECTION_CUT;
    static final Color COASTLINE_DEFAULT = MapLook.COASTLINE;

    // Read off the coast's own defaults rather than restated here. What each of them means is
    // documented where it is declared; restating the NUMBER is how the sliders come to open
    // on a different map from the one the report describes, with neither of them saying so.
    static final double COAST_SKIP_DEFAULT = Coastlines.DEFAULT_RULES.skipMultiple();
    static final double COAST_SKIP_STEP_SCALE = 100.0;

    static final double COAST_MAX_SKIPS_DEFAULT = Coastlines.DEFAULT_RULES.maxSkips();

    static final Color COAST_CROSSING_DEFAULT = MapLook.COAST_CROSSING;
    static final Color PIERCED_CELL_DEFAULT = MapLook.PIERCED_CELL;

    static final Color SITE_COLOUR = MapLook.SITE;

    // Read off the shipped map rather than restated. These two and the report's own were
    // the same pair of numbers written twice, which is how a window comes to divide the void
    // differently from the report describing it, with neither of them saying so.
    static final double VOID_SPAN_DEFAULT = ShippedMap.SECTION_LENGTH_IN_RADII;
    static final double VOID_SPAN_STEP_SCALE = 100.0;

    static final double BRIDGE_REACH_DEFAULT =
        Coastlines.DEFAULT_RULES.bridgeReachMultiple();
    static final double BRIDGE_REACH_STEP_SCALE = 100.0;

    // The slider reads in per cent, so the shipped share is scaled up to meet it. Why that
    // share is the one it is belongs with the share itself.
    static final double MIN_SECTION_SCALE = 100.0;
    static final double MIN_SECTION_DEFAULT = ShippedMap.MIN_SECTION_SHARE * MIN_SECTION_SCALE;

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

    // The void the cells and their bridges close around, as shapes rather than as the black
    // left showing between the fills.
    boolean showVoidBridges = true;

    // A third reading of the same map: not what the void is, but where the edge of what is
    // NOT void could be drawn.
    boolean showCoastlines = true;

    // Both constructions drawn at the void's own extent instead of a channel inside it, which
    // is the only way to see the pockets that have no room for a channel and so draw nothing
    // at all. Off by default because the map it produces is not one to keep: every fill sits
    // flush against the cells around it.
    boolean showPocketsAtTrueExtent;

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

    // One chosen colour for every owner, optionally spread in brightness so neighbours can
    // still be told apart. Brightness rather than hue on purpose: a hue jitter makes each
    // owner look like a different faction, which is what the shipped palette means, while a
    // brightness jitter reads as one thing seen in several places.
    Color resolveOwnedColour(String ownerId) {
        return jitterOwned
            ? MapPainting.jitterBrightness(
                ownedCellColour, ownerId.hashCode(), jitterStrength)
            : ownedCellColour;
    }

    // Which map of the void the overlays are asking for. Asked of the settings rather than
    // worked out at each overlay, because the two constructions drawn together have to be
    // asked the same question within one frame or they are describing different maps.
    VoidPockets.PocketShaping resolvePocketShaping() {
        return showPocketsAtTrueExtent
            ? VoidPockets.PocketShaping.AT_TRUE_EXTENT
            : VoidPockets.PocketShaping.WITH_CHANNEL;
    }
}
