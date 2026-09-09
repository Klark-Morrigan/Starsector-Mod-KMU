package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * The continents, traced, and every span the construction lays across the water they leave.
 *
 * <p>One answer to "what walls does this map put down". The coasts are traced with no bridges,
 * so a run of cells comes back as several shapes, and four searches then lay the lines that
 * put them back in touch: across the inlets each outer shore leaves, across the water the
 * cells closed around unaided, across the puddles too small for a shore, and between one
 * continent and the next. Anything asking what the map shut in is asking about those lines
 * together, and a caller running the four searches itself is a second answer that agrees only
 * until one of them is tuned.
 *
 * <p>Held apart from any drawing because the walls are not a drawing. What a piece of void is
 * called, what a report measures, and what a picture strokes are three readings of one laying,
 * and each of them arriving at its own would let the name, the number and the line describe
 * maps that were never the same.
 *
 * <p>Each search is run on the first ask and kept. A window draws a few of these layers at a
 * time and a report wants all of them, so neither an eager laying nor a search per call is
 * right: the first pays for lines nobody asked to see, and the second pays again for every one
 * that two readers want. Kept here rather than by the caller, since the point of one answer is
 * that there is nowhere else to keep it.
 *
 * <p>The order the searches depend on each other in is structural rather than remembered. The
 * links are judged against the inlet spans, so asking for the links lays those first whether
 * or not anything is drawing them - which is what stops the surviving links from changing with
 * a switch about whether the inlet spans are on screen.
 */
public final class BridgedContinents {

    private final List<double[]> sites;
    private final SectorGeometryParameters parameters;
    private final Coastlines.CoastRules coastRules;
    private final ContinentBridges.BridgeRules bridgeRules;

    // The settled bridge search, handed in rather than made here. It is the one search that
    // answers about the cells alone rather than about these coasts, so it outlives any one
    // laying, and a copy per laying would be the repeated search it exists to prevent.
    private final VoidBridgeCache sectorBridges;

    // Null until asked for, which is what tells "not yet searched" from "searched, found
    // nothing" - an empty list is a real answer here, and the two have to be told apart or a
    // sector with no lakes pays for the lake search on every frame that mentions it.
    private Coastlines.TracedCoasts traced;
    private CoastRounding.RoundedCoasts rounded;
    private List<CellGap> inletSpans;
    private List<CellGap> lakeSpans;
    private List<CellGap> puddleSpans;
    private List<CellGap> links;

    private BridgedContinents(
            List<double[]> sites,
            SectorGeometryParameters parameters,
            Coastlines.CoastRules coastRules,
            ContinentBridges.BridgeRules bridgeRules,
            VoidBridgeCache sectorBridges) {

        this.sites = sites;
        this.parameters = parameters;
        this.coastRules = coastRules;
        this.bridgeRules = bridgeRules;
        this.sectorBridges = sectorBridges;
    }

    /**
     * Opens a laying of one sector under one set of rules.
     *
     * <p>Nothing is searched for here. What the caller gets is the question settled - which
     * sites, which knobs, which rules - so that every line drawn off it afterwards is about the
     * same map however few of them are asked for.
     *
     * @param sites         the cells' own points
     * @param parameters    the geometry knobs the coasts are traced under
     * @param coastRules    how the coasts are traced, whose bridge reach the puddle spans are
     *                      claimed at
     * @param bridgeRules   how every span is offered and judged
     * @param sectorBridges the settled bridge search, shared with whatever else asks it of this
     *                      same sector
     * @return the laying, with nothing found yet
     */
    public static BridgedContinents layContinents(
            List<double[]> sites,
            SectorGeometryParameters parameters,
            Coastlines.CoastRules coastRules,
            ContinentBridges.BridgeRules bridgeRules,
            VoidBridgeCache sectorBridges) {

        return new BridgedContinents(
            sites, parameters, coastRules, bridgeRules, sectorBridges);
    }

    /**
     * The continents themselves: one closed coast per touching-connected run of cells.
     *
     * <p>Traced with no walls laid, which is the whole of what makes these continents rather
     * than one sector-wide shape. Every span below is offered against this line and refused by
     * it, so it is the first thing any of them needs.
     *
     * @return the coasts, and the water the cells closed around unaided along with them
     */
    public Coastlines.TracedCoasts traceCoasts() {

        if (traced == null) {
            traced = Coastlines.traceContinentCoasts(sites, parameters, coastRules);
        }
        return traced;
    }

    /**
     * The knobs this laying was opened under, which everything measured against it shares.
     *
     * @return the geometry knobs
     */
    public SectorGeometryParameters parameters() {
        return parameters;
    }

    /**
     * Every line of the trace with its sharp joins taken off, which is what a drawing strokes.
     *
     * <p>Rounded once for the whole laying rather than at each drawing. A sector's coasts are
     * tens of thousands of points, so a pass per drawing is paid for per drawing - and holding
     * one answer is what stops the stroke, the lake margins and an exported picture from being
     * three roundings of one border.
     *
     * @return the rounded lines, one ring for one ring and in the trace's own order
     */
    public CoastRounding.RoundedCoasts roundCoasts() {

        if (rounded == null) {
            rounded = CoastRounding.roundTracedCoasts(traceCoasts(), coastRules.rounding());
        }
        return rounded;
    }

    /**
     * The water this construction fills, under one colouring and one shaping.
     *
     * <p>Opened rather than found: what the caller gets is the question settled, so a drawing
     * showing three layers and a report asking about all six are reading one inventory.
     *
     * @param ownerBySite each site's owner, or null where it has none - which moves the shapes,
     *                    since a pocket one owner rings is pushed out into that owner's fills
     * @param shaping     whether to ask of the map as drawn or of the void's true extent
     * @return the water, with nothing found yet
     */
    public FilledWater fillWater(
            List<String> ownerBySite,
            VoidPockets.PocketShaping shaping) {

        return new FilledWater(this, ownerBySite, shaping);
    }

    /**
     * The spans across the inlets the outer shores leave: water between two cells of one
     * continent, on the side that faces the open void.
     *
     * @return the spans that survived the coastlines, in the order they were judged
     */
    public List<CellGap> layInletSpans() {

        if (inletSpans == null) {
            inletSpans = layAnchoredSpans(CoastFrontages.Shore.EXTERIOR);
        }
        return inletSpans;
    }

    /**
     * The same search anchored on the interior shores instead, so these cross water a
     * continent's own cells have already closed around.
     *
     * <p>Its own laying rather than gathered with the inlet spans, because a formation is
     * thinned among the spans it shares an anchor with and a span over a lake shares no anchor
     * with one over the void outside the continent.
     *
     * @return the spans across the lakes, in the order they were judged
     */
    public List<CellGap> layLakeSpans() {

        if (lakeSpans == null) {
            lakeSpans = layAnchoredSpans(CoastFrontages.Shore.INTERIOR);
        }
        return lakeSpans;
    }

    /**
     * The spans across the puddles: holes the lake floor judged too small to be drawn a shore.
     *
     * <p>Claimed from the settled search rather than searched for again. These ARE that search
     * asked about smaller water, at the reach the coast rules carry, so a search of their own
     * would be the same offer paid for twice and free to drift from it.
     *
     * @return the spans standing over a puddle, in the order they were offered
     */
    public List<CellGap> claimPuddleSpans() {

        if (puddleSpans == null) {

            puddleSpans = PuddlePockets.claimPuddleBridges(
                traceCoasts(),
                sectorBridges.findVoidBridges(
                    sites,
                    parameters.cellRadius(),
                    parameters.cellRadius() * coastRules.bridgeReachMultiple()));
        }
        return puddleSpans;
    }

    /**
     * The links between the continents: spans between cells of DIFFERENT shapes, which is what
     * puts back in touch what tracing without bridges took apart.
     *
     * <p>Judged against the inlet spans, which are laid here whether or not anything is drawing
     * them. Those are the lines anchored on the same shore over the same open void, so they are
     * the ones a link can double or cross; laid only when their own layer was wanted, a link
     * would be judged against a map missing every wall the inlet search put down.
     *
     * @return the links that survived, in the order they were judged
     */
    public List<CellGap> layLinks() {

        if (links == null) {

            links = IntercontinentalBridges.findIntercontinentalBridges(
                traceCoasts(), layInletSpans(), parameters, bridgeRules);
        }
        return links;
    }

    /**
     * The coasts with every wall this construction puts down, as the single value anything
     * asking what the map shut in is asking about.
     *
     * <p>All four span sets, not the ones a picture happens to be showing. What a piece of void
     * IS does not change with a switch: a span divides the water it stands over whether or not
     * anyone is drawing it, and a laying that left out the idle sets would hand back sections
     * that merge and split as layers are turned on.
     *
     * <p>Each span goes in saying which water it crossed. The walk never asks, but a hole it
     * closes is read as the kind of piece its walls say it is, and that is the only way the
     * naming can tell a bay from a lake from the sea between two continents.
     *
     * <p>Every span, and no other line. The coastline the links make of the sector - the second
     * trace with them laid, which {@link IntercontinentalCoasts} cuts down to what the first
     * does not already carry - is drawn but is not a wall here, so void only that shore shuts
     * in is read by whichever span or reach of the first trace also touches it.
     *
     * @return the coasts, their own reaches, and every span, laid together
     */
    public LaidCoast layEveryWall() {

        var spans = new ArrayList<DiscUnionBoundary.Chord>();

        spans.addAll(DiscUnionBoundary.buildChordsFrom(
            layInletSpans(), DiscUnionBoundary.WallKind.INLET_SPAN));
        spans.addAll(DiscUnionBoundary.buildChordsFrom(
            layLakeSpans(), DiscUnionBoundary.WallKind.LAKE_SPAN));
        spans.addAll(DiscUnionBoundary.buildChordsFrom(
            claimPuddleSpans(), DiscUnionBoundary.WallKind.PUDDLE_SPAN));
        spans.addAll(DiscUnionBoundary.buildChordsFrom(
            layLinks(), DiscUnionBoundary.WallKind.LINK));

        return LaidCoast.layCoast(traceCoasts(), spans, parameters);
    }

    // One shore's spans, which is the same search either way once the shore has named the
    // stretches its lines may start from.
    private List<CellGap> layAnchoredSpans(CoastFrontages.Shore shore) {

        return ContinentBridges.findAnchoredBridges(
            traceCoasts(), shore, parameters, bridgeRules);
    }
}
