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
    static final Color INLAND_VOID_DEFAULT = MapLook.INLAND_VOID;
    static final Color COASTAL_VOID_DEFAULT = MapLook.COASTAL_VOID;
    static final Color CHANNEL_DEFAULT = MapLook.CHANNEL;
    static final Color CENTRELINE_DEFAULT = MapLook.CENTRELINE;
    static final Color VOID_BRIDGE_DEFAULT = MapLook.VOID_BRIDGE;
    static final Color REGION_NAME_DEFAULT = MapLook.REGION_NAME;
    static final Color COASTLINE_DEFAULT = MapLook.COASTLINE;

    // Read off the coast's own defaults rather than restated here. What each of them means is
    // documented where it is declared; restating the NUMBER is how the sliders come to open
    // on a different map from the one the report describes, with neither of them saying so.
    static final double COAST_SKIP_DEFAULT = Coastlines.DEFAULT_RULES.skipMultiple();
    static final double COAST_SKIP_STEP_SCALE = 100.0;

    static final double COAST_MAX_SKIPS_DEFAULT = Coastlines.DEFAULT_RULES.maxSkips();

    static final Color CONTINENT_COAST_DEFAULT = MapLook.CONTINENT_COAST;

    static final Color COAST_CROSSING_DEFAULT = MapLook.COAST_CROSSING;
    static final Color PIERCED_CELL_DEFAULT = MapLook.PIERCED_CELL;

    static final Color SITE_COLOUR = MapLook.SITE;

    static final double BRIDGE_REACH_DEFAULT =
        Coastlines.DEFAULT_RULES.bridgeReachMultiple();
    static final double BRIDGE_REACH_STEP_SCALE = 100.0;

    static final float JITTER_DEFAULT = 35;
    static final double JITTER_SCALE = 100.0;

    static final int OWNER_FILL_ALPHA = 90;

    Color ownedCellColour = OWNED_CELL_DEFAULT;
    Color ownedCellEdge = OWNED_CELL_DEFAULT;
    Color unownedCellColour = UNOWNED_CELL_DEFAULT;
    Color unownedCellEdge = UNOWNED_CELL_DEFAULT;
    Color unboundedCellColour = UNBOUNDED_CELL_DEFAULT;
    Color unboundedCellEdge = UNBOUNDED_CELL_DEFAULT;
    int voidFillOpacity = OWNER_FILL_ALPHA;
    double bridgeReachMultiple = BRIDGE_REACH_DEFAULT;

    // The void, in the two kinds it comes in and the three things there are to see of each.
    //
    // Split this finely because each of the six answers a different question. A wall is a
    // proposal about where a boundary could go and the fill is what that proposal encloses, so
    // judging either means being able to see it without the other; and the two KINDS are
    // separate proposals entirely - the bridges are about the gaps between cells, the coast is
    // about the sector's outer shape - so a reader weighing one wants the other out of the way.
    boolean showInlandBridges = true;
    boolean showInlandFill = true;
    boolean showInlandNames;

    boolean showCoastline = true;
    boolean showCoastalFill = true;
    boolean showCoastalNames;

    // The cells' own names, whose system ids the void's names are built out of. Not part of the
    // void group: a cell is there whatever the void is doing.
    boolean showCellNames;

    // The per-continent coast preview. Not part of the void group either, and off by default:
    // it is a rival construction being judged against the settled coast, not a part of it.
    boolean showContinentCoasts;

    double coastSkipMultiple = COAST_SKIP_DEFAULT;
    int coastMaxSkips = (int) COAST_MAX_SKIPS_DEFAULT;

    Color coastlineColour = COASTLINE_DEFAULT;
    Color continentCoastColour = CONTINENT_COAST_DEFAULT;
    Color coastCrossingColour = COAST_CROSSING_DEFAULT;
    Color piercedCellColour = PIERCED_CELL_DEFAULT;
    Color inlandVoidColour = INLAND_VOID_DEFAULT;
    Color inlandVoidEdge = INLAND_VOID_DEFAULT;
    Color coastalVoidColour = COASTAL_VOID_DEFAULT;
    Color coastalVoidEdge = COASTAL_VOID_DEFAULT;
    Color voidBridgeColour = VOID_BRIDGE_DEFAULT;
    Color regionNameColour = REGION_NAME_DEFAULT;
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
    //
    // The void's own extent, with no choice about it. The other shaping takes the channel out
    // by re-tracing at a moved reach, which is what a section stops doing once the inset is a
    // per-edge verdict from the ownership rule - so offering it is offering a map that is on
    // its way out, and a reading taken from it is not evidence about the one being built.
    VoidPockets.PocketShaping resolvePocketShaping() {
        return VoidPockets.PocketShaping.AT_TRUE_EXTENT;
    }

    // How the coast is traced, for the same reason. More than one overlay walks the cells with
    // the coast's walls laid, and a wall set built twice from the same sliders is still two
    // answers - one of them can have a different wall crowded out of a mouth, and the two
    // drawings then describe maps that were never the same.
    Coastlines.CoastRules resolveCoastRules() {
        return new Coastlines.CoastRules(
            bridgeReachMultiple, coastSkipMultiple, coastMaxSkips);
    }

}
