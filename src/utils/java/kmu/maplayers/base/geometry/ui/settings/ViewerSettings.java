package kmu.maplayers.base.geometry.ui.settings;

import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.ContinentBridges;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.SmallPieceFolding;
import kmu.maplayers.base.geometry.VoidPockets;
import kmu.maplayers.base.geometry.render.MapLook;
import kmu.maplayers.base.geometry.render.MapPainting;

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
public final class ViewerSettings {

    // Read off the map's own look rather than restated. A colour written here as well is a
    // second answer to "what is a coastline drawn in", and the window and the SVG then mark
    // the same thing two different ways.
    public static final Color OWNED_CELL_DEFAULT = MapLook.OWNED_CELL;
    public static final Color UNOWNED_CELL_DEFAULT = MapLook.UNOWNED_CELL;
    public static final Color UNBOUNDED_CELL_DEFAULT = MapLook.UNBOUNDED_CELL;
    public static final Color INLAND_VOID_DEFAULT = MapLook.INLAND_VOID;
    public static final Color COASTAL_VOID_DEFAULT = MapLook.COASTAL_VOID;
    public static final Color CHANNEL_DEFAULT = MapLook.CHANNEL;
    public static final Color CENTRELINE_DEFAULT = MapLook.CENTRELINE;
    public static final Color VOID_BRIDGE_DEFAULT = MapLook.VOID_BRIDGE;
    public static final Color REGION_NAME_DEFAULT = MapLook.REGION_NAME;
    public static final Color COASTLINE_DEFAULT = MapLook.COASTLINE;

    // Read off the coast's own defaults rather than restated here. What each of them means is
    // documented where it is declared; restating the NUMBER is how the sliders come to open
    // on a different map from the one the report describes, with neither of them saying so.
    // The rule is a share of a cell's whole border; the slider asks for it as a percentage,
    // which is how anyone reading a map thinks about how far a cell sticks out.
    public static final double COAST_MIN_FRONTAGE_DEFAULT =
        Coastlines.DEFAULT_RULES.minFrontageShare();
    public static final double FRONTAGE_PERCENT_SCALE = 100.0;

    // The v3 coast opens on the same knobs as the settled one, so the two lines start
    // identical and any difference on screen is a knob someone deliberately moved rather
    // than a difference that was there from the first frame.
    public static final double CONTINENT_MIN_FRONTAGE_DEFAULT = COAST_MIN_FRONTAGE_DEFAULT;

    public static final Color CONTINENT_COAST_DEFAULT = MapLook.CONTINENT_COAST;

    public static final Color CONTINENT_BRIDGE_DEFAULT = MapLook.CONTINENT_BRIDGE;

    public static final Color DROPPED_STRETCH_DEFAULT = MapLook.DROPPED_STRETCH;

    public static final Color VOID_FACE_DEFAULT = MapLook.VOID_FACE;

    public static final Color CONTINENT_COASTAL_VOID_DEFAULT = MapLook.CONTINENT_COASTAL_VOID;

    // The fold opens switched off on both tests, so the pieces are first seen as the walls
    // alone make them - which is the thing every setting of either is judged against.
    public static final double LEAST_PIECE_SHARE_DEFAULT = 0;
    public static final double PIECE_SHARE_PERCENT_SCALE = 100.0;
    public static final double LEAST_PIECE_WIDTH_DEFAULT = 0;
    public static final double LEAST_WHOLE_WALL_WIDTH_DEFAULT = 0;

    public static final Color COAST_CROSSING_DEFAULT = MapLook.COAST_CROSSING;
    public static final Color PIERCED_CELL_DEFAULT = MapLook.PIERCED_CELL;

    public static final Color SITE_COLOUR = MapLook.SITE;

    public static final double BRIDGE_REACH_DEFAULT =
        Coastlines.DEFAULT_RULES.bridgeReachMultiple();
    public static final double BRIDGE_REACH_STEP_SCALE = 100.0;

    // v3's bridges open on the same reach the settled ones use, so the two lists start from
    // the same offer and any difference between them is the coastline filter rather than a
    // different search.
    public static final double CONTINENT_BRIDGE_REACH_DEFAULT = BRIDGE_REACH_DEFAULT;

    // The width a wall is drawn at, so two lines closer than this are drawn overlapping -
    // which is the state a reader calls doubled.
    //
    // The full width rather than half of it. A span shadowing a shorter one at a degree's
    // divergence pulls a hundred units away by the far end, so a slack narrower than the
    // stroke leaves a gap where neither the shorter span nor the coastline quite covers, and
    // the doubled line survives on a technicality.
    public static final double CONTINENT_BRIDGE_COAST_SLACK_DEFAULT = MapLook.SPAN_STROKE;

    public static final float JITTER_DEFAULT = 35;
    public static final double JITTER_SCALE = 100.0;

    public static final int OWNER_FILL_ALPHA = 90;

    public Color ownedCellColour = OWNED_CELL_DEFAULT;
    public Color ownedCellEdge = OWNED_CELL_DEFAULT;
    public Color unownedCellColour = UNOWNED_CELL_DEFAULT;
    public Color unownedCellEdge = UNOWNED_CELL_DEFAULT;
    public Color unboundedCellColour = UNBOUNDED_CELL_DEFAULT;
    public Color unboundedCellEdge = UNBOUNDED_CELL_DEFAULT;
    public int voidFillOpacity = OWNER_FILL_ALPHA;
    public double bridgeReachMultiple = BRIDGE_REACH_DEFAULT;

    // The void, in the two kinds it comes in and the three things there are to see of each.
    //
    // Split this finely because each of the six answers a different question. A wall is a
    // proposal about where a boundary could go and the fill is what that proposal encloses, so
    // judging either means being able to see it without the other; and the two KINDS are
    // separate proposals entirely - the bridges are about the gaps between cells, the coast is
    // about the sector's outer shape - so a reader weighing one wants the other out of the way.
    public boolean showInlandBridges = true;
    public boolean showInlandFill = true;
    public boolean showInlandNames;

    public boolean showCoastline = true;
    public boolean showCoastalFill = true;
    public boolean showCoastalNames;

    // The cells' own names, whose system ids the void's names are built out of. Not part of the
    // void group: a cell is there whatever the void is doing.
    public boolean showCellNames;

    // The per-continent coast preview. Not part of the void group either, and off by default:
    // it is a rival construction being judged against the settled coast, not a part of it.
    public boolean showContinentCoasts;

    // The void each continent coast shuts in behind it, filled. The same construction the
    // settled coast's fill comes from, asked of the other coast - which is what makes the two
    // worth putting on screen together, since the difference between them is then the coast
    // rather than the way the void behind it was worked out.
    public boolean showContinentCoastalFill;

    // Whether a v3 bridge may cross one already laid. On, they draw a grid and the void comes
    // apart into pieces bounded on every side; off, they leave a tree, which is what the
    // settled bridges do and which holds the void together as one shape with fingers.
    //
    // On by default because the grid is what the pieces were built to be read from - but the
    // two are on one switch precisely because which of them makes the better MAP is the thing
    // still being decided.
    public boolean allowBridgeCrossings = true;

    // The bridges v3 would lay once its coastlines are down - the same search the settled map
    // uses, offered the same cells, so the only difference between the two sets is which spans
    // the coastlines then refuse.
    //
    // Off by default and apart from the coasts' own switch, because it is the next proposal
    // rather than another view of this one - and it is only meaningful with the coasts traced,
    // since the coastlines are what decides which bridges survive.
    public boolean showContinentBridges;

    // The pieces the coastlines and the inlet bridges cut the void into, each filled in its
    // own shade. Off by default and apart from the lines' own switches, because it answers a
    // different question from either: the lines say where a wall was laid, and this says what
    // the walls between them ENCLOSE - which is the thing a merge rule would be acting on.
    public boolean showVoidFaces;

    // Every stretch of frontage the smoothing chose not to pass through, on whichever coasts
    // are being drawn. One switch rather than one per coast: it shows a DECISION rather than a
    // layer, and the answer it gives - what the rules left out - is the same question of both.
    public boolean showDroppedStretches;

    // How little of its own border a cell may face the void with before it is dropped from
    // the walk outright, as a share of the whole turn. The whole of how the settled coast is
    // smoothed: one intrinsic measure, with nothing carried from one stretch to the next.
    public double coastMinFrontageShare = COAST_MIN_FRONTAGE_DEFAULT;

    // The v3 coast's own frontage floor, apart from the settled coast's above.
    //
    // Apart because with no bridges laid far more of a continent's cells face the void
    // through a sliver, so the share that clears those off would take real frontage off the
    // sector coast. Shared, every attempt to tune one was a compromise with the other.
    public double continentMinFrontageShare = CONTINENT_MIN_FRONTAGE_DEFAULT;

    // How far apart two cells may sit and still be bridged on the v3 layer, in cell radii.
    // Its own knob rather than the settled bridges' because the two are laid over different
    // maps: v3's are offered to a sector whose coastlines have already taken some of the void,
    // so the reach that finds the right spans there is not the one that finds them here.
    public double continentBridgeReachMultiple = CONTINENT_BRIDGE_REACH_DEFAULT;

    // How far off a wall already down a span may run and still count as running along it,
    // in map units. A real judgement rather than rounding: a span shadowing the coast at a
    // distance is redundant or not depending on how far a reader will accept two lines being
    // apart and still call them one, and that is a matter of taste about the map.
    public double continentBridgeCoastSlack = CONTINENT_BRIDGE_COAST_SLACK_DEFAULT;

    // The smallest piece of water worth keeping, as a share of one cell's area. A piece under
    // it is folded into a neighbour by taking out the wall between them, over and over until
    // it clears - so what the knob shapes is which WALLS stand, and the pieces follow from
    // that wherever they are next found.
    //
    // A share of a cell rather than a count of units, for the reason the frontage rule is a
    // share of a border: it stays a claim about the map when the cell radius moves.
    public double leastPieceShare = LEAST_PIECE_SHARE_DEFAULT;

    // The narrowest piece of water worth keeping on its own, in map units. Its own test beside
    // the area one, and read as an OR: area cannot see shape, so a wedge four thousand long
    // and two hundred wide covers as much map as a compact blob nine hundred across, and no
    // cap that keeps the blob will ever fold the wedge.
    //
    // In map units rather than as a share, because what it asks about is a WIDTH - a thing to
    // hold against the cell radius, which is thousands of units, rather than against an area.
    public double leastPieceWidth = LEAST_PIECE_WIDTH_DEFAULT;

    // The width under which a piece is opened by taking out a WHOLE bridge rather than the
    // stretch of it between two crossings, in map units.
    //
    // Its own knob because it buys something the other two cannot. A bay crossed by several
    // bridges is cut into a fan, and every wedge of that fan is bounded by stretches of the
    // same few bridges - so opening one stretch joins two wedges and leaves the rest of that
    // bridge standing straight across what it just made. Only taking a bridge out entire
    // un-fans the bay, and only a bridge with water either side along its whole length may go:
    // one across a bay mouth has the open sea beyond it.
    public double leastWholeWallWidth = LEAST_WHOLE_WALL_WIDTH_DEFAULT;

    public Color continentBridgeColour = CONTINENT_BRIDGE_DEFAULT;
    public Color voidFaceColour = VOID_FACE_DEFAULT;
    public Color continentCoastalVoidColour = CONTINENT_COASTAL_VOID_DEFAULT;
    public Color continentCoastalVoidEdge = CONTINENT_COASTAL_VOID_DEFAULT;

    public Color coastlineColour = COASTLINE_DEFAULT;
    public Color droppedStretchColour = DROPPED_STRETCH_DEFAULT;
    public Color continentCoastColour = CONTINENT_COAST_DEFAULT;
    public Color coastCrossingColour = COAST_CROSSING_DEFAULT;
    public Color piercedCellColour = PIERCED_CELL_DEFAULT;
    public Color inlandVoidColour = INLAND_VOID_DEFAULT;
    public Color inlandVoidEdge = INLAND_VOID_DEFAULT;
    public Color coastalVoidColour = COASTAL_VOID_DEFAULT;
    public Color coastalVoidEdge = COASTAL_VOID_DEFAULT;
    public Color voidBridgeColour = VOID_BRIDGE_DEFAULT;
    public Color regionNameColour = REGION_NAME_DEFAULT;
    public Color siteColour = SITE_COLOUR;
    public Color centrelineColour = CENTRELINE_DEFAULT;
    public Color channelColour = CHANNEL_DEFAULT;
    public Color channelEdge = CHANNEL_DEFAULT;

    public int channelOpacity = OWNER_FILL_ALPHA;
    public int ownedCellOpacity = OWNER_FILL_ALPHA;
    public int unownedCellOpacity = OWNER_FILL_ALPHA;
    public int unboundedCellOpacity = OWNER_FILL_ALPHA;

    public boolean jitterOwned = true;
    public boolean jitterUnowned;

    public float jitterStrength = (float) (JITTER_DEFAULT / JITTER_SCALE);

    public boolean showUnboundedCells;

    public SectorGeometryParameters parameters = SectorGeometryParameters.createDefaults();

    // One chosen colour for every owner, optionally spread in brightness so neighbours can
    // still be told apart. Brightness rather than hue on purpose: a hue jitter makes each
    // owner look like a different faction, which is what the shipped palette means, while a
    // brightness jitter reads as one thing seen in several places.
    public Color resolveOwnedColour(String ownerId) {
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
    public VoidPockets.PocketShaping resolvePocketShaping() {
        return VoidPockets.PocketShaping.AT_TRUE_EXTENT;
    }

    // How the coast is traced, for the same reason. More than one overlay walks the cells with
    // the coast's walls laid, and a wall set built twice from the same sliders is still two
    // answers - one of them can have a different wall crowded out of a mouth, and the two
    // drawings then describe maps that were never the same.
    public Coastlines.CoastRules resolveCoastRules() {
        return new Coastlines.CoastRules(bridgeReachMultiple, coastMinFrontageShare);
    }

    // How v3's bridges are offered and judged, asked of the settings for the same reason the
    // coast rules are: the overlay that lays them and anything that later reports on them
    // have to be describing one set, not two built from the same sliders a moment apart.
    public ContinentBridges.BridgeRules resolveContinentBridgeRules() {
        return new ContinentBridges.BridgeRules(
            continentBridgeReachMultiple,
            continentBridgeCoastSlack,
            allowBridgeCrossings);
    }

    // How the v3 coast is traced. Its own method rather than the settled coast's, because a
    // separate frontage floor is only separate if something reads it - so which coast a knob
    // reaches is decided here rather than at whichever overlay happens to read it.
    //
    // The bridge reach goes unread: no bridges are found while a continent coast is traced,
    // so it is the one field of the record with nothing on the other end of it. Passed
    // through as the settled coast's rather than as some number of its own, so that if it
    // ever comes to be read the two coasts are still looking at one sector.
    // The cap the fold works to, in map units of area. Turned from a share of a cell into an
    // area here, where the cell radius is, rather than at the overlay - a knob that reads as a
    // share of a cell has to be turned into units against the SAME cell the pieces were cut
    // against, or it means something slightly different from what it says.
    public SmallPieceFolding.FoldRules resolveFoldRules() {
        return new SmallPieceFolding.FoldRules(
            leastPieceShare * Math.PI * parameters.cellRadius() * parameters.cellRadius(),
            leastPieceWidth,
            leastWholeWallWidth);
    }

    public Coastlines.CoastRules resolveContinentCoastRules() {
        return new Coastlines.CoastRules(bridgeReachMultiple, continentMinFrontageShare);
    }
}
