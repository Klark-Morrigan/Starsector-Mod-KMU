package kmu.maplayers.base.geometry.settings;

import kmlib.math.geometry.CornerRounding;

import kmu.maplayers.base.geometry.EdgeInsetRule;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.render.FillLook;
import kmu.maplayers.base.geometry.render.MapLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.v3.Coastlines;
import kmu.maplayers.base.geometry.v3.ContinentBridges;
import kmu.maplayers.base.geometry.v3.StraightRuns;
import kmu.maplayers.base.geometry.v3.VoidPockets;
import kmu.maplayers.base.geometry.v3.VoidSection;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.SpikeSandingStyle;

import java.awt.Color;

/**
 * What the viewer is currently set to draw with, and nothing else.
 *
 * <p>State only. No widget builds one of these, none is built from one, and it knows nothing
 * about how any of it comes to be chosen - so a reader asking "what can this map be drawn
 * with" gets a list of the answers rather than three hundred lines of Swing with the answers
 * embedded in it.
 *
 * <p>Apart from the window for that reason. What is set is not a property of the widgets that
 * happen to set it: a drawing written to a file, a measurement taken of a shape, and the
 * window itself are all asking what this map is, and only one of them has a screen. Under the
 * window, the two that do not would each have to say it again, and a report would describe a
 * different map from the one on screen with neither of them saying so.
 *
 * <p>Above the map's own look and below anything that draws: it reads the palette and the
 * stroke widths to open on, since a default colour that is not the one the map paints with is
 * a second answer to what a line is drawn in. The constructions below it stay knob-free and
 * are handed a rule record instead, which is what lets one of them be asked the same question
 * twice under different settings.
 *
 * <p>Fields rather than accessors, because something exists whose whole job is to write them.
 * Accessors would have implied a read-only view that nothing actually has, and paying sixty
 * methods for that fiction is worse than admitting this is a bag of values.
 *
 * <p>The defaults live here rather than with the widgets because a default is a property of
 * the setting - what it is when nobody has chosen - while the range a slider allows is a
 * property of the widget, and belongs with it.
 */
public final class ViewerSettings {

    // Read off the map's own look rather than restated. A colour written here as well is a
    // second answer to "what is a coastline drawn in", and the window and the SVG then mark
    // the same thing two different ways.
    public static final Color OWNED_CELL_DEFAULT = MapLook.OWNED_CELL;
    public static final Color UNOWNED_CELL_DEFAULT = MapLook.UNOWNED_CELL;
    public static final Color UNBOUNDED_CELL_DEFAULT = MapLook.UNBOUNDED_CELL;
    public static final Color CHANNEL_DEFAULT = MapLook.CHANNEL;
    public static final Color CENTRELINE_DEFAULT = MapLook.CENTRELINE;
    public static final Color REGION_NAME_DEFAULT = MapLook.REGION_NAME;

    // Read off the coast's own defaults rather than restated here. What each of them means is
    // documented where it is declared; restating the NUMBER is how the sliders come to open
    // on a different map from the one the report describes, with neither of them saying so.
    // The rule is a share of a cell's whole border; the slider asks for it as a percentage,
    // which is how anyone reading a map thinks about how far a cell sticks out.
    public static final double CONTINENT_MIN_FRONTAGE_DEFAULT =
        Coastlines.DEFAULT_RULES.minFrontageShare();
    public static final double FRONTAGE_PERCENT_SCALE = 100.0;

    // The puddle floor, read off the coast's own default for the reason the frontage floor
    // is. A share of one cell's area; the slider asks for it as a percentage.
    public static final double MIN_LAKE_SHARE_DEFAULT = Coastlines.DEFAULT_RULES.minLakeShare();

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

    public static final Color INTERCONTINENTAL_BRIDGE_DEFAULT = MapLook.INTERCONTINENTAL_BRIDGE;

    public static final Color DROPPED_STRETCH_DEFAULT = MapLook.DROPPED_STRETCH;

    public static final Color LANDABLE_FRONTAGE_DEFAULT = MapLook.LANDABLE_FRONTAGE;

    public static final Color BRIDGE_FRONTAGE_DEFAULT = MapLook.BRIDGE_FRONTAGE;

    public static final Color BARE_VOID_DEFAULT = MapLook.BARE_VOID;

    public static final Color CONTINENT_COASTAL_VOID_DEFAULT = MapLook.CONTINENT_COASTAL_VOID;

    public static final Color SITE_COLOUR = MapLook.SITE;

    // How far apart two cells may sit and still hold water between them, for the one search
    // that asks about the cells alone. Its answer is what the puddle spans are claimed from.
    public static final double BRIDGE_REACH_DEFAULT =
        Coastlines.DEFAULT_RULES.bridgeReachMultiple();
    public static final double BRIDGE_REACH_STEP_SCALE = 100.0;

    // The spans laid against the coastlines open on that same reach, so the two lists start
    // from the same offer and any difference between them is the coastline filter rather than
    // a different search.
    public static final double CONTINENT_BRIDGE_REACH_DEFAULT = BRIDGE_REACH_DEFAULT;

    // The width a wall is drawn at, so two lines closer than this are drawn overlapping -
    // which is the state a reader calls doubled.
    //
    // The full width rather than half of it. A span shadowing a shorter one at a degree's
    // divergence pulls a hundred units away by the far end, so a slack narrower than the
    // stroke leaves a gap where neither the shorter span nor the coastline quite covers, and
    // the doubled line survives on a technicality.
    public static final double CONTINENT_BRIDGE_COAST_SLACK_DEFAULT = MapLook.SPAN_STROKE;

    // How close two span feet may stand before one of them moves.
    //
    // The width a span is drawn at, which is as small as the setting can be and still mean
    // anything: two feet closer than the line they carry are one place to the eye, and there is
    // nothing under that to tell apart.
    //
    // Small on purpose, because what this buys is bought at the bottom of its range. Feet that
    // are EXACTLY coincident cost walls - the boundary walk gives a cell's mouth to one wall
    // only, so at such a place every span but one goes unlaid and the water it would have closed
    // never appears. Separating those is worth a fifth of the sector's walled pockets, measured
    // on both fixtures, and any positive setting does it.
    //
    // Above that the knob stops building and starts trading. A foot's place is where its two
    // cells come closest, so moving it off costs the span its claim to mark that crossing, and
    // moving several breaks rings that were closing pockets. From this setting up to a cell's
    // whole frontage the fixtures lose about a tenth of their pockets and gain a fiftieth on
    // every span's length, for spacing only the eye wants. Worth having as a knob, not as a
    // default.
    public static final double SPAN_ANCHOR_SEPARATION_DEFAULT = MapLook.SPAN_STROKE;

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

    // One switch over the whole void construction, suppressing all of it whatever its own
    // switches are set to.
    //
    // Its own setting rather than a roll-up of the switches below, because what it is for is
    // putting the construction aside and coming back to it. A roll-up would take every switch
    // under it down on the way out and could only turn every one of them on to come back, so
    // whatever arrangement was being looked at is gone the moment it is set down.
    //
    // On by default, so what the viewer opens with is decided by the switches below as it
    // always was. Off by default, it would instead be a hidden reason for a knob to do nothing
    // when moved.
    public boolean showContinentVoid = true;

    // v4's own master, the twin of the switch above. Each construction has a tree of its own
    // and each tree has a root, which is what makes the two comparable: a reader sets one down
    // and picks the other up without either reaching into the other's layers.
    //
    // Off, so the window opens on exactly what it opened on before v4 existed.
    public boolean showVoidV4;

    // v4's base: the void the cells close around, before any line divides it. Every later v4
    // layer is a division of this, so it is the first thing that layer stack has to agree with.
    public boolean showBareVoid = true;

    // The shore of v4's pieces a straight line from elsewhere on the same piece can arrive at.
    // A diagnostic of the bare void rather than a layer of it: nothing is laid from it yet, and
    // it is drawn so that what a span may be anchored on can be looked at before any span is
    // made to obey it. Off, like v3's, since it is read for a reason rather than looked at
    // every time.
    public boolean showLandableFrontageV4;

    // Whether v4's pieces stand off from what they face by the border channel, or are drawn on
    // their true lines. A mode of the layer above rather than a layer of its own: it is one
    // division painted two ways, and the pieces do not change when it moves.
    //
    // Off, so what is drawn is the partition itself. The true geometry is the thing being
    // judged here, and a window opening on the inset would show it already dressed.
    public boolean showBareVoidInset;

    // The cells' own names, whose system IDs the void's names are built out of. Not part of the
    // void group: a cell is there whatever the void is doing.
    public boolean showCellNames;

    // The coasts, in their two shores.
    //
    // Line, fill and frontage for each, in the same order and under the same names, because
    // the two shores are one kind of line seen from opposite sides - the interior one bounds
    // water the cells closed around, the exterior one the open void beyond them. A reader
    // judging either wants the handles they already know from the other.

    // The puddles: holes the lake floor judged too small for a shoreline, filled whole with a
    // span across the water rather than shored. Also the only reading of the slivers no shore
    // could ever show: a hole whose every frontage is under the frontage floor smooths to an
    // empty outline, and nothing but this draws it.
    public boolean showContinentPuddleBridges;
    public boolean showContinentPuddleFill;

    // The lake shores: void the continents' cells closed around unaided, drawn a shoreline of
    // its own rather than filled up to a span.
    public boolean showContinentLakeCoastline;
    public boolean showContinentLakeFill;
    public boolean showContinentLakeFrontages;

    // The outer shores, and the void each shuts in behind it. What a coast reach shuts in is
    // one question however the coast offering it was traced, so the fill is the same walk the
    // spans' water comes out of, asked about a different kind of wall.
    public boolean showContinentCoastline;
    public boolean showContinentCoastFill;
    public boolean showContinentCoastFrontages;

    // Both frontage switches are diagnostic: they answer "why did that span go THERE" rather
    // than showing anything the map proposes. A span can only reach a part of a cell its coast
    // actually runs along, so one that looks as though it ignored a nearer cell has usually
    // been offered nowhere nearer to anchor - which is invisible until the eligible stretches
    // are on screen beside the spans that used them.

    // The spans laid once the coastlines are down, across the inlets those coasts leave.
    //
    // Off by default and apart from the shores' own switches, because it is the next step
    // rather than another view of this one - and it is only meaningful with the coasts traced,
    // since the coastlines are what decides which spans survive.
    //
    // The fill is the water those spans shut in, found with the spans as the only walls. It
    // covers the exterior shores' own fill where the two meet - a bay a reach runs into is
    // water a span closed as well - and that overlap is deliberate: the coast cannot be laid
    // as a wall to divide them without holding a strip open along every reach that nothing
    // would draw. Both are painted as one sheet instead, so water two of them hold reads
    // exactly as water one of them holds.
    public boolean showContinentBridges;
    public boolean showContinentInletFill;

    // The links between the continents: the same range the cell-pair bridges are offered under,
    // asked of pairs on DIFFERENT continents and anchored on the coastlines rather than run rim
    // to rim. What they are for is seeing whether the sector can be made whole again - the trace
    // splits it at every gap however narrow, and these are what puts the pieces back in touch.
    //
    // Its own switch and apart from the inlet spans', because the two are opposite acts: an
    // inlet span rounds one outline up, a link joins two that have nothing to do with each
    // other. A reader judging either wants the other out of the way.
    //
    // Laid against the inlet spans whether or not those are drawn, so what survives here does
    // not depend on which layers happen to be on.
    //
    // The fill is the sea a run of links shuts in between two continents: one link closes
    // nothing, and a second one joining the same pair rings the void between them. Found with
    // the links and the inlet spans as the walls, and only what a link actually closed is drawn -
    // water an inlet span holds is the layer above's, and water the cells closed unaided is a
    // lake or a puddle with its own switch.
    //
    // The shores are the sector traced a SECOND time with the links laid as walls, cut down to
    // what the first line does not already carry - so what appears is the coastline a link
    // added: the two edges of the isthmus it becomes, the rim of a cell it took out of the void,
    // and wherever else the smoothing moved because a link changed what a cell is judged
    // against. Its own switch because it is another trace of the sector rather than another way
    // of drawing this one, and that is the expensive half of the layer.
    public boolean showIntercontinentalBridges;
    public boolean showIntercontinentalFill;
    public boolean showIntercontinentalShores;

    // The water the sector encloses once the links are laid that no other layer paints. A link
    // closes basins between two continents that neither closed alone, and most of the boundary a
    // reader sees round one is continent coastline that was always drawn - so a line with open
    // void inside it is the state this ends. Kept clear of every layer above rather than laid
    // over them, so switching it on shows what it alone is for.
    public boolean showIntercontinentalEnclosedFill;

    // Whether each piece of void has its name written across it, one switch per kind of piece.
    // A kind is the layer that shut the piece in, so these sit one under each layer above: a
    // reader judging that layer's water wants its names and no other's, and a map with every
    // name on at once is unreadable.
    public boolean showContinentPuddleNames;
    public boolean showContinentLakeNames;
    public boolean showContinentLakePocketNames;
    public boolean showContinentCoastNames;
    public boolean showContinentInletNames;
    public boolean showIntercontinentalNames;

    // The same spans laid over the interior coastlines instead - across water the cells closed
    // around unaided rather than across the void between continents. One search over two shores
    // rather than two searches, so the sets differ only by what the shore decides: where a span
    // may anchor, which pairs are worth offering - one lake's ring rather than one outline's
    // cells - and how the reach is read, by the water crossed rather than by the centres.
    //
    // Its own switch and not the lake shore's, because a shoreline and a span across the water
    // it bounds are two proposals: whether to draw a lake at all is one question, and whether
    // to take its water back into the continent is another.
    public boolean showContinentLakeBridges;

    // The water those spans shut in: each crossed lake cut into the finer pockets its spans
    // hold, the way the inlet fill is the water the exterior spans hold. Found with the spans
    // as the only walls, and only what a span actually walled is drawn - a lake nothing
    // crosses stays the lake shore's own layer, under its own switch above.
    public boolean showContinentLakePocketFill;

    // Every stretch of frontage the smoothing chose not to pass through, on whichever coasts
    // are being drawn. One switch rather than one per coast: it shows a DECISION rather than a
    // layer, and the answer it gives - what the rules left out - is the same question of both.
    public boolean showDroppedStretches;

    // Every stretch of exposed border a straight line could arrive at from the open void, on
    // whichever coasts are being drawn. Answered from the discs rather than from any line, so
    // it says the same thing whichever construction it is drawn beside - which is what makes it
    // worth putting under both.
    public boolean showLandableFrontage;

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

    // Whether a straight run may land only where the cell it leaves can actually see it, which
    // is what the placement clamp reads as doing and does not do. What each answer costs the
    // map is measured on StraightRuns.ReachAnchor.
    //
    // Off, and the map is drawn that way on purpose: the correct window removes nearly every
    // incursion into a cell and collapses more cells to a single point in exchange, which reads
    // worse. Here as a switch because that verdict is a judgement about how the map looks, and
    // the only way to weigh it again is to put the two side by side.
    //
    // One knob over every coast, like the rounding: it decides how a coast is placed rather
    // than which coast is being traced.
    public boolean shouldLandWhereVisible;

    // The puddle floor: how much water a hole must hold to be drawn as a lake, as a share of
    // one cell's area. One knob over every shore, like the rounding: it is a claim about what
    // counts as water worth drawing, not about how any one coast is traced.
    public double minLakeShare = MIN_LAKE_SHARE_DEFAULT;

    // How little of its own border a cell may face the void with before it is dropped from the
    // walk outright, as a share of the whole turn. The whole of how a coast is smoothed: one
    // intrinsic measure, with nothing carried from one stretch to the next.
    public double continentMinFrontageShare = CONTINENT_MIN_FRONTAGE_DEFAULT;

    // How far apart two cells may sit and still be spanned once the coastlines are down, in
    // cell radii. Its own knob rather than the bare search's above, because the two are offered
    // different maps: these are offered a sector whose coastlines have already taken some of
    // the void, so the reach that finds the right spans there is not the one that finds them
    // among the cells alone.
    public double continentBridgeReachMultiple = CONTINENT_BRIDGE_REACH_DEFAULT;

    // Whether spans sharing an anchor point are thinned once the laying is settled: chains
    // down to their end walls, fans down to one span, and nothing dropped that holds void in.
    //
    // On by default, because the thinned set is what the map lays - the switch is here to
    // see what the pass did, which cannot be read off the map otherwise since a dropped span
    // is gone rather than marked.
    public boolean shouldThinSpanFormations = true;

    // How far off a wall already down a span may run and still count as running along it,
    // in map units. A real judgement rather than rounding: a span shadowing the coast at a
    // distance is redundant or not depending on how far a reader will accept two lines being
    // apart and still call them one, and that is a matter of taste about the map.
    public double continentBridgeCoastSlack = CONTINENT_BRIDGE_COAST_SLACK_DEFAULT;

    // How close two span feet may stand before one of them moves along its frontage, in map
    // units. Zero leaves every foot where the search put it, which is the only way to see what
    // the spreading did - a moved foot is moved rather than marked.
    //
    // One knob over every span the construction lays, like the rounding: what it decides is how
    // close two feet may be before a reader calls them one place, which is a claim about the
    // drawing rather than about which set is being laid. Turned up it is a trade rather than an
    // improvement - see the default, where the measurements are.
    public double spanAnchorSeparation = SPAN_ANCHOR_SEPARATION_DEFAULT;

    public Color continentBridgeColour = CONTINENT_BRIDGE_DEFAULT;
    public Color intercontinentalBridgeColour = INTERCONTINENTAL_BRIDGE_DEFAULT;
    public Color bridgeFrontageColour = BRIDGE_FRONTAGE_DEFAULT;
    public Color continentCoastalVoidColour = CONTINENT_COASTAL_VOID_DEFAULT;
    public Color continentCoastalVoidEdge = CONTINENT_COASTAL_VOID_DEFAULT;

    public Color bareVoidColour = BARE_VOID_DEFAULT;
    public Color landableFrontageV4Colour = LANDABLE_FRONTAGE_DEFAULT;

    public Color droppedStretchColour = DROPPED_STRETCH_DEFAULT;
    public Color landableFrontageColour = LANDABLE_FRONTAGE_DEFAULT;
    public Color continentCoastColour = CONTINENT_COAST_DEFAULT;
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

    // Which cell edges take the border channel. The map's own rule to open on, so the window
    // opens on the map; the other two answer for every edge at once and are what separates the
    // cells' true partition from the channel cut into it on screen.
    //
    // The cells only. The void is not shaped through this, and will not be until the pockets
    // are pieces of one partition with edges of their own to ask the question of.
    public EdgeInsetRule cellInsetRule = EdgeInsetRule.AT_EVERY_BORDER;

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

    /**
     * How one of the map's fills is painted: a construction's own colours at the map's own
     * fill opacity.
     *
     * <p>Assembled here because the two halves are chosen in different places. The colours
     * belong to whichever construction is drawing - that is what tells two of them apart on
     * screen - while the opacity is one slider over every fill there is. An overlay pairing
     * them itself is a second answer to how solid a fill is, and the fills then stop moving
     * together when the slider does.
     *
     * @param fill what to fill with
     * @param edge what to outline it in
     * @return the pair, at whatever the opacity slider is set to
     */
    public FillLook resolveWaterLook(Color fill, Color edge) {
        return new FillLook(fill, voidFillOpacity, edge);
    }

    // Which map of the void the overlays are asking for. Asked of the settings rather than
    // worked out at each overlay, because every layer drawn in one frame has to be asked the
    // same question or they are describing different maps.
    //
    // The void's own extent, with no choice about it. The other shaping takes the channel out
    // by re-tracing at a moved reach, which is what a section stops doing once the inset is a
    // per-edge verdict from the ownership rule - so offering it is offering a map that is on
    // its way out, and a reading taken from it is not evidence about the one being built.
    public VoidPockets.PocketShaping resolvePocketShaping() {
        return VoidPockets.PocketShaping.AT_TRUE_EXTENT;
    }

    /**
     * Whether any part of the continent construction is on screen, and so whether tracing it
     * buys anything.
     *
     * <p>Asked of the settings rather than worked out at the overlay that acts on it, because
     * the answer is an enumeration of the continent switches and this is the file that
     * declares them: one added to that list and forgotten here is a feature that silently
     * never builds, and the two living together is what makes that hard to do.
     *
     * @return true where at least one of them is switched on
     */
    public boolean isAnyContinentLayerShown() {

        return showContinentVoid
            && (showContinentPuddleBridges
                || showContinentPuddleFill
                || showContinentLakeCoastline
                || showContinentLakeFill
                || showContinentLakeFrontages
                || showContinentCoastline
                || showContinentCoastFill
                || showContinentCoastFrontages
                || showContinentBridges
                || showContinentInletFill
                || showContinentLakeBridges
                || showContinentLakePocketFill
                || showIntercontinentalBridges
                || showIntercontinentalFill
                || showIntercontinentalShores
                || showIntercontinentalEnclosedFill);
    }

    /**
     * Whether a piece of void of the given kind is on screen, which is what makes it something
     * a reader could be pointing at.
     *
     * <p>On screen means its wall drawn AND its water filled. Either alone leaves nothing to
     * point at: a fill with no wall is water whose edge the map never drew, and a wall with no
     * fill is a line with nothing behind it. A puddle has no wall of its own - the cells are
     * its edge, and those are always drawn - so its fill alone puts it on screen.
     *
     * <p>Asked of the settings rather than worked out where the pointer is, for the reason
     * the continent roll-up is: the answer is a pairing of the switches declared here, and a
     * kind whose pair is named elsewhere is a kind that silently stops being pointable when
     * one of them is renamed.
     *
     * @param kind which kind of piece
     * @return true where both halves of that kind are drawn
     */
    public boolean isSectionOnScreen(VoidSection.SectionKind kind) {

        return showContinentVoid && switch (kind) {
            case PUDDLE -> showContinentPuddleFill;
            case LAKE -> showContinentLakeCoastline && showContinentLakeFill;
            case LAKE_POCKET -> showContinentLakeBridges && showContinentLakePocketFill;
            case COASTAL -> showContinentCoastline && showContinentCoastFill;
            case INLET -> showContinentBridges && showContinentInletFill;
            case INTERCONTINENTAL -> showIntercontinentalBridges && showIntercontinentalFill;
        };
    }

    /**
     * Whether v4's bare void is drawn, which is its construction's master and its own switch.
     *
     * <p>Asked here rather than at the layer, for the reason the pairings above are: the master
     * and the leaf are two switches declared together, and a layer reading only its own would go
     * on drawing after its whole construction had been set down.
     *
     * @return true where both are on
     */
    public boolean isBareVoidShown() {
        return showVoidV4 && showBareVoid;
    }

    /**
     * Whether v4's landable frontage is drawn, which is its construction's master and its own
     * switch, for the reason {@link #isBareVoidShown} gives.
     *
     * @return true where both are on
     */
    public boolean isLandableFrontageV4Shown() {
        return showVoidV4 && showLandableFrontageV4;
    }

    /**
     * Which edges of v4's pieces take the border channel.
     *
     * <p>A rule rather than a flag, because the rule is what the shaping takes and answering
     * with one keeps the two readings of a piece as one statement with a setting in it. Off is
     * {@link EdgeInsetRule#NOWHERE}, under which the shape drawn is the partition itself.
     *
     * @return the rule the pieces are shaped by
     */
    public EdgeInsetRule resolveVoidInsetRule() {
        return showBareVoidInset ? EdgeInsetRule.AT_EVERY_BORDER : EdgeInsetRule.NOWHERE;
    }

    /**
     * Whether pieces of the given kind have their names written on them.
     *
     * @param kind which kind of piece
     * @return true where that kind's name switch is on
     */
    public boolean shouldWriteSectionNames(VoidSection.SectionKind kind) {

        return showContinentVoid && switch (kind) {
            case PUDDLE -> showContinentPuddleNames;
            case LAKE -> showContinentLakeNames;
            case LAKE_POCKET -> showContinentLakePocketNames;
            case COASTAL -> showContinentCoastNames;
            case INLET -> showContinentInletNames;
            case INTERCONTINENTAL -> showIntercontinentalNames;
        };
    }

    // Where a straight run may land, for every coast on the map. One answer rather than one per
    // line, for the reason the rounding is one answer: the lines are drawn to be compared, and
    // placed differently a difference between them would be partly a difference between the
    // switches that placed them.
    public StraightRuns.ReachAnchor resolveReachAnchor() {

        return shouldLandWhereVisible
            ? StraightRuns.ReachAnchor.NEAREST_THE_STRETCH_MIDDLE
            : StraightRuns.ReachAnchor.AT_THE_STRETCH_START;
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

    // How the spans are offered and judged, asked of the settings for the same reason the coast
    // rules are: the overlay that lays them and anything that later reports on them have to be
    // describing one set, not two built from the same sliders a moment apart.
    public ContinentBridges.BridgeRules resolveContinentBridgeRules() {
        return new ContinentBridges.BridgeRules(
            continentBridgeReachMultiple,
            continentBridgeCoastSlack,
            shouldThinSpanFormations,
            spanAnchorSeparation);
    }

    // How the coasts are traced. Asked of the settings rather than assembled at each overlay,
    // because more than one thing walks the cells with these walls laid, and a wall set built
    // twice from the same sliders is still two answers - one of them can have a different wall
    // crowded out of a mouth, and the two drawings then describe maps that were never the same.
    //
    // The bridge reach in it is the search asked of the cells alone, which the coasts never
    // run: no spans are laid while a coast is traced. What reads it is the puddle claim, which
    // takes that search's answer and keeps the spans standing over water too small for a shore.
    public Coastlines.CoastRules resolveContinentCoastRules() {
        return new Coastlines.CoastRules(
            bridgeReachMultiple,
            continentMinFrontageShare,
            minLakeShare,
            resolveLineRounding(),
            resolveReachAnchor());
    }
}
