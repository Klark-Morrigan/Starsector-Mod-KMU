package kmu.maplayers.base.geometry.ui.settings;

import kmlib.math.geometry.CornerRounding;

import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.ContinentBridges;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.VoidPockets;
import kmu.maplayers.base.geometry.render.MapLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.SpikeSandingStyle;

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

    // The rounding every drawn line takes, opened at the coast's own default for the reason
    // the frontage floor is: one number, in one place, so the sliders and everything that
    // reports on a line describe the same map. Split into its three parts because that is
    // what a slider writes; put back together by resolveLineRounding.
    //
    // The threshold is asked for in degrees, which is how anyone looking at a corner thinks
    // about it, and is the knob worth reaching for first: it decides which corners on a line
    // are corners at all, and so whether the other two do anything.
    public static final double ROUNDING_RADIUS_DEFAULT = Coastlines.DEFAULT_ROUNDING.radius();
    public static final int ROUNDING_SEGMENTS_DEFAULT =
        Coastlines.DEFAULT_ROUNDING.segmentsPerCorner();
    public static final double ROUND_BELOW_DEGREES_DEFAULT =
        Math.toDegrees(Coastlines.DEFAULT_ROUNDING.roundBelowAngleRadians());

    // The spike-sanding pass that runs before the rounding. A needle whose own edges are
    // shorter than the rounding steps back by survives rounding untouched - the cut clamps to
    // those edges - so it has to come out first or not at all.
    //
    // This window's own opening numbers, not the shipped ones. They read the same today, but
    // the map keeps its behind LunaLib where nothing outside the game can reach them, so
    // these are a second answer rather than the same one - and a window that claimed
    // otherwise would go on claiming it after the map moved.
    //
    // On here, where the shipped map leaves the pass off. This window exists to look at
    // shapes, and a pass left off by default is a pass nobody looks at; what it costs is one
    // slider back to zero, which is the pass switched off.
    public static final double SPIKE_HEIGHT_DEFAULT = 150.0;
    public static final double SPIKE_BELOW_DEGREES_DEFAULT = 60.0;

    public static final Color CONTINENT_COAST_DEFAULT = MapLook.CONTINENT_COAST;

    public static final Color CONTINENT_BRIDGE_DEFAULT = MapLook.CONTINENT_BRIDGE;

    public static final Color DROPPED_STRETCH_DEFAULT = MapLook.DROPPED_STRETCH;

    public static final Color BRIDGE_FRONTAGE_DEFAULT = MapLook.BRIDGE_FRONTAGE;

    public static final Color CONTINENT_COASTAL_VOID_DEFAULT = MapLook.CONTINENT_COASTAL_VOID;

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

    // One switch over each rival construction, suppressing the whole of it whatever its own
    // switches are set to.
    //
    // Their own settings rather than roll-ups of the switches below, because what they are for
    // is putting a construction aside and coming back to it. A roll-up would take every switch
    // under it down on the way out and could only turn every one of them on to come back, so
    // whatever arrangement was being looked at is gone the moment it is set down.
    //
    // Both on by default, so what the viewer opens with is decided by the switches below as it
    // always was. Off by default, the two would instead be a pair of hidden reasons for a knob
    // to do nothing when moved.
    public boolean showSectorVoid = true;
    public boolean showContinentVoid = true;

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

    // The bridges v3 would lay once its coastlines are down - the same search the settled map
    // uses, offered the same cells, so the only difference between the two sets is which spans
    // the coastlines then refuse.
    //
    // Off by default and apart from the coasts' own switch, because it is the next proposal
    // rather than another view of this one - and it is only meaningful with the coasts traced,
    // since the coastlines are what decides which bridges survive.
    public boolean showContinentBridges;

    // The stretches of cell border a bridge may anchor on, drawn on the coast they are part of.
    //
    // Off by default and diagnostic: it answers "why did that span go THERE" rather than
    // showing anything the map proposes. A span can only reach a part of a cell the coast
    // actually runs along, so a span that looks as though it ignored a nearer cell has usually
    // been offered nowhere nearer to anchor - which is invisible until the eligible stretches
    // are on screen beside the spans that used them.
    public boolean showBridgeFrontages;

    // Every stretch of frontage the smoothing chose not to pass through, on whichever coasts
    // are being drawn. One switch rather than one per coast: it shows a DECISION rather than a
    // layer, and the answer it gives - what the rules left out - is the same question of both.
    public boolean showDroppedStretches;

    // How little of its own border a cell may face the void with before it is dropped from
    // the walk outright, as a share of the whole turn. The whole of how the settled coast is
    // smoothed: one intrinsic measure, with nothing carried from one stretch to the next.
    public double coastMinFrontageShare = COAST_MIN_FRONTAGE_DEFAULT;

    // How a drawn line is rounded where it turns sharply, for every line on the map that is
    // rounded at all - both coasts and the cluster borders alike.
    //
    // One set of knobs rather than a set per line. Rounding is a question about how a LINE is
    // drawn, not about what any of these constructions mean, and the lines are on screen to
    // be compared: rounded to different numbers, a difference between two of them would be
    // partly a difference between the sliders that drew them.
    public double roundingRadius = ROUNDING_RADIUS_DEFAULT;
    public int roundingSegments = ROUNDING_SEGMENTS_DEFAULT;
    public double roundBelowDegrees = ROUND_BELOW_DEGREES_DEFAULT;

    // How tall a protrusion may be and still be spliced out before the rounding, and how
    // sharp it has to be to count as one at all. Either at zero switches the pass off, which
    // is what the pass itself reads a non-positive threshold as.
    public double spikeHeight = SPIKE_HEIGHT_DEFAULT;
    public double spikeBelowDegrees = SPIKE_BELOW_DEGREES_DEFAULT;

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

    public Color continentBridgeColour = CONTINENT_BRIDGE_DEFAULT;
    public Color bridgeFrontageColour = BRIDGE_FRONTAGE_DEFAULT;
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
        return new Coastlines.CoastRules(
            bridgeReachMultiple, coastMinFrontageShare, resolveLineRounding());
    }

    /**
     * How sharply a drawn line has to turn to be rounded, and what it is rounded to.
     *
     * <p>Put back together here rather than at each line, so no two of them can be drawn to
     * roundings that drifted apart.
     *
     * <p>Never chamfered, whatever the sliders say: a chamfer cuts a sharp turn flat, and
     * what is wanted of one is a rounded tip.
     *
     * @return the corner shape every rounded line on the map is drawn to
     */
    /**
     * The smoothing profile the cluster borders are drawn through, in the shape the shipped
     * pass takes it.
     *
     * <p>Built here so the window can run {@code BorderSmoothing} itself rather than repeat
     * what it does. The two passes and the order they run in are that class's answer, and a
     * window that reimplemented them would be drawing a border the mod does not.
     *
     * <p>Both gates on, always. What the shipped profile gates with a switch this window
     * gates with the knobs themselves - a zero radius rounds nothing and a zero spike height
     * sands nothing, which is what each pass already reads a non-positive setting as - so a
     * gate here would be a second way to say off, and a slider that did nothing while it was
     * set.
     *
     * @return the profile, at whatever the sliders are set to
     */
    public BorderSmoothingStyle resolveBorderSmoothing() {

        var rounding = resolveLineRounding();

        return new BorderSmoothingStyle(
            new SpikeSandingStyle(true, spikeHeight, Math.toRadians(spikeBelowDegrees)),
            new CornerRoundingStyle(
                true,
                rounding.radius(),
                rounding.segmentsPerCorner(),
                rounding.bevelBelowAngleRadians(),
                rounding.roundBelowAngleRadians()));
    }

    public CornerRounding resolveLineRounding() {
        return new CornerRounding(
            roundingRadius,
            roundingSegments,
            Coastlines.DEFAULT_ROUNDING.bevelBelowAngleRadians(),
            Math.toRadians(roundBelowDegrees));
    }

    // How v3's bridges are offered and judged, asked of the settings for the same reason the
    // coast rules are: the overlay that lays them and anything that later reports on them
    // have to be describing one set, not two built from the same sliders a moment apart.
    public ContinentBridges.BridgeRules resolveContinentBridgeRules() {
        return new ContinentBridges.BridgeRules(
            continentBridgeReachMultiple,
            continentBridgeCoastSlack);
    }

    // How the v3 coast is traced. Its own method rather than the settled coast's, because a
    // separate frontage floor is only separate if something reads it - so which coast a knob
    // reaches is decided here rather than at whichever overlay happens to read it.
    //
    // The bridge reach goes unread: no bridges are found while a continent coast is traced,
    // so it is the one field of the record with nothing on the other end of it. Passed
    // through as the settled coast's rather than as some number of its own, so that if it
    // ever comes to be read the two coasts are still looking at one sector.
    public Coastlines.CoastRules resolveContinentCoastRules() {
        return new Coastlines.CoastRules(
            bridgeReachMultiple, continentMinFrontageShare, resolveLineRounding());
    }
}
