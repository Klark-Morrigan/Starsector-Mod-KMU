package kmu.maplayers.base.geometry.ui.overlays.voidpockets.v3;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.CoastFrontages;
import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.ContinentBridges;
import kmu.maplayers.base.geometry.PuddlePockets;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.VoidBridgeCache;
import kmu.maplayers.base.geometry.VoidBridgePockets;
import kmu.maplayers.base.geometry.render.FillLook;
import kmu.maplayers.base.geometry.render.FillSheet;
import kmu.maplayers.base.geometry.render.MapLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.ui.overlays.voidpockets.CoastalPocketsOverlay;
import kmu.maplayers.base.geometry.ui.settings.ViewerSettings;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <b>The continent coast (v3), orchestrated.</b> Each touching-connected run of cells traced as
 * its own closed coast, and everything that reading of the sector then produces: the outer
 * shores and the void behind them, the lake shores round the water the cells closed unaided,
 * the puddles too small for a shore, the spans laid across both the inlets and the puddles,
 * and the water those spans shut in.
 *
 * <p>What makes this one the continent coast is that it traces with NO bridges laid, so a run
 * of cells a bridge would have joined comes back as several shapes rather than one, and under
 * its own coast rules. That is the whole of v3's disagreement with the settled coast. The fill
 * below it is {@link CoastalPocketsOverlay}, which the settled coast in the neighbouring
 * package runs exactly as well - which is the point: with the same code on both sides, a
 * difference on screen is a difference between the coasts.
 *
 * <p>The bridges are this construction's own. The settled coast treats a bridge as connective
 * tissue and traces one line round everything it joins; the proposal is to give each continent
 * its own smoothed coast and lay spans across the inlets those coasts leave. Whether that is
 * worth building turns on where the spans actually fall, which is only legible on screen.
 *
 * <p>The puddle spans are the settled search asked about smaller water, reused rather than
 * reinvented: water too small to deserve a shoreline is water in exactly the position the
 * settled map already fills by bridging it.
 *
 * <p>Drawn as a line over everything for the same reason the settled coast is: it is a
 * proposal, and the only way to judge it is against the bridges and the coast it would
 * replace, all on screen at once.
 */
public final class ContinentCoastOverlay {

    private final ViewerSettings settings;

    // The half of this overlay the settled construction runs identically. Held rather than
    // inherited from, because what v2 and v3 share is a sequence of steps rather than an
    // identity - neither is a kind of the other, and nothing ever asks for "a coast overlay"
    // without knowing which.
    private final CoastalPocketsOverlay coast;

    // The spans that survive those coastlines. Held beside the trace they were filtered
    // against rather than found while painting, since which ones survive is a question about
    // THAT trace and a frame that asked it again could answer about a different one.
    //
    // Found while either half of them is wanted, because the spans and the water they hold are
    // one construction seen twice - a span is a line saying "this much is held between these
    // cells", and the fill is what a run of them closes around.
    private List<CellGap> inletSpans = List.of();

    // The same search anchored on the interior coastlines instead, so these cross water the
    // cells closed around unaided. Held apart from the inlet spans rather than gathered with
    // them because each is drawn under its own switch, and which shore a span was anchored on
    // is the whole of what tells the two sets apart.
    private List<CellGap> lakeSpans = List.of();

    // The spans laid across the puddles, held beside the trace whose puddles claimed them
    // for the same reason.
    private List<CellGap> puddleBridges = List.of();

    // The void the inlet spans close around, walked with the spans as the only walls - which
    // is what the settled construction does with its own bridges.
    private List<List<double[]>> inletPockets = List.of();

    // The lake water the lake spans shut in: each crossed lake cut into the finer pockets its
    // spans hold. Only what a span actually walled - a lake nothing crosses is the lake
    // shore's own layer, and drawn from this list too it would be painted twice.
    private List<List<double[]>> lakePockets = List.of();

    // The settled bridge search, shared with the construction that also asks it. Handed in
    // rather than made here: two overlays asking one question of one sector have to be one
    // search, and a cache each would be exactly the second answer it exists to prevent.
    private final VoidBridgeCache sectorBridges;

    // The stretches of border a bridge may anchor on - exterior coasts, lake shores, or both,
    // as the switches asked - held beside the trace that decided them. One run per stretch
    // rather than one list per cell, because a cell facing the void twice is eligible in two
    // separate places and drawing them as one line would run a mark straight through the cell
    // between them.
    private List<List<double[]>> frontages = List.of();

    public ContinentCoastOverlay(ViewerSettings settings, VoidBridgeCache sectorBridges) {

        this.settings = settings;
        this.sectorBridges = sectorBridges;
        this.coast = new CoastalPocketsOverlay(settings);
    }

    /**
     * Traces the continents again, or drops them when the preview is switched off.
     *
     * <p>Under the v3 coast rules rather than the settled coast's, so a knob moved down here
     * moves this line and leaves the map alone. What the two constructions still share is the
     * cells and the parameters they are built from - they are two readings of one sector.
     *
     * @param fixture the sector to trace in
     */
    public void refresh(SectorFixture fixture) {

        coast.acceptTrace(null);
        inletSpans = List.of();
        inletPockets = List.of();
        lakeSpans = List.of();
        lakePockets = List.of();
        puddleBridges = List.of();
        frontages = List.of();

        // The coasts are traced while any of them is wanted, because each is built on them:
        // the bridges are filtered against the coasts, and the eligible frontages are read
        // off them. Switching a later one on without the lines that decide it would show a
        // set nothing on screen accounts for.
        //
        // Under the switch over the whole continent construction, which suppresses this
        // without touching any of its own - so what was showing comes back when it is lifted.
        if (!settings.isAnyContinentLayerShown()) {
            return;
        }

        // The one line that makes this v3: continent coasts, which lay no bridges.
        coast.acceptTrace(Coastlines.traceContinentCoasts(
            fixture.getSites(),
            settings.parameters,
            settings.resolveContinentCoastRules()));

        // One step asked of each shore in turn. Separate calls rather than one gathered set,
        // because the two are laid independently: a formation is thinned among the spans it
        // shares an anchor with, and a span across a lake shares no anchor with one across
        // the void outside the continent.
        var inletWater = findSpanWater(
            fixture,
            CoastFrontages.Shore.EXTERIOR,
            settings.showContinentBridges,
            settings.showContinentInletFill);

        inletSpans = inletWater.spans();
        inletPockets = inletWater.pockets();

        var lakeWater = findSpanWater(
            fixture,
            CoastFrontages.Shore.INTERIOR,
            settings.showContinentLakeBridges,
            settings.showContinentLakePocketFill);

        lakeSpans = lakeWater.spans();
        lakePockets = lakeWater.pockets();

        // The void behind the coast, worked out by the same construction the settled coast's
        // fill comes from. Not a second way of arriving at the same thing: a coast reach is a
        // wall, and what a wall shuts in is one question however the coast that offered it was
        // traced.
        if (settings.showContinentCoastFill) {
            coast.findPockets(fixture);
        }

        frontages = gatherEligibleFrontages();

        // Claimed from the settled search rather than searched for again: these ARE the
        // settled bridges asked about smaller water, at the settled reach, so the cache hands
        // back whatever the inland overlay already found for this same sector.
        if (settings.showContinentPuddleBridges) {

            puddleBridges = PuddlePockets.claimPuddleBridges(
                coast.getTrace(),
                sectorBridges.findVoidBridges(
                    fixture.getSites(),
                    settings.parameters.cellRadius(),
                    settings.parameters.cellRadius() * settings.bridgeReachMultiple));
        }
    }

    /**
     * Draws the void this construction shut in, beneath the cells.
     *
     * <p>At the same weight and in the same way as the settled coast's fill, because the two
     * are on screen to be compared: any difference between them should be the coast, not the
     * drawing.
     *
     * @param g2 what to draw with
     */
    public void paintPocketFills(Graphics2D g2) {

        if (!settings.showContinentVoid || !coast.hasTrace()) {
            return;
        }

        // One colour for all of them, read once. Every one is water this construction shut
        // in - behind the outer shore, ringed by land, under a span on either shore, or too
        // small for a shore at all - and separate readings of the pair is how they would come
        // to be drawn as several kinds of thing when they are one.
        var water = settings.continentCoastalVoidColour;
        var edge = settings.continentCoastalVoidEdge;

        // Every one of them as ONE sheet rather than one layer over another. Each pair of them
        // overlaps by construction and no wall can be laid to keep a pair apart: a wall stands
        // both its sides half a channel off its own line, so dividing them would leave a strip
        // that nothing draws. The spans hold the bays a coast reach runs into, and they hold
        // the lake water a shore conceded its margin out of. So the overlaps are kept and made
        // free: filled once over the union, water two layers hold reads exactly as water one
        // of them holds.
        var sheet = new FillSheet();

        if (settings.showContinentCoastFill) {
            sheet.addRings(coast.collectPocketRings());
        }
        if (settings.showContinentInletFill) {
            sheet.addRings(inletPockets);
        }
        if (settings.showContinentLakePocketFill) {
            sheet.addRings(lakePockets);
        }
        if (settings.showContinentPuddleFill) {
            sheet.addRings(coast.collectPuddleRings());
        }

        // The margins into that same sheet, as the one layer that takes water back OUT of it:
        // a lake's open middle stays bare unless the spans' pockets fill it, which is what the
        // two layers each mean with the other switched off.
        if (settings.showContinentLakeFill) {
            coast.addLakeMargins(sheet);
        }

        sheet.paint(g2, new FillLook(water, settings.voidFillOpacity, edge));
    }

    /**
     * Draws each continent's coast, over the top of everything.
     *
     * @param g2 what to draw with
     */
    public void paintCoasts(Graphics2D g2) {

        if (!settings.showContinentVoid || !coast.hasTrace()) {
            return;
        }

        paintBridges(g2);

        if (settings.showContinentCoastline) {

            coast.paintCoastRings(g2, settings.continentCoastColour);
            coast.paintDroppedStretches(g2);
        }

        // A lake shore is this construction's coast seen from the water's side, so it is
        // drawn in the coasts' own colour - under its own switch, since which of the two a
        // reader is judging decides which they want out of the way.
        if (settings.showContinentLakeCoastline) {
            coast.paintLakeRings(g2, settings.continentCoastColour);
        }

        // Last of all, and so over every wall rather than under them. What it marks is which
        // stretches of those walls a span could have started from, and a mark drawn beneath
        // the lines it is about is hidden by exactly the ones worth reading it against.
        paintFrontages(g2);
    }

    /**
     * The stretches of border a bridge was allowed to anchor on.
     *
     * <p>Answers the question the spans themselves cannot: whether a span that appears to have
     * ignored a nearer cell was ever offered anywhere nearer to start from. A cell is eligible
     * only where the coast runs along it - the rest of its border faces land, or water another
     * coast has already closed - and that is a fraction of each cell rather than all of it.
     *
     * <p>Drawn over every wall, at the coast's own weight: an eligible stretch is that same
     * line in another colour, so the coast reads as one line of two kinds rather than as two
     * lines. Spans are heavier, so one crossing a marked stretch still shows through.
     *
     * <p>Open runs, never closed. A stretch of frontage has two real ends, and closing it would
     * draw a chord across the cell it is drawn on - a mark that looks like an eligible span and
     * is nothing of the kind.
     *
     * <p>A frontage of ONE point gets a dot. A cell squeezed by its neighbours offers exactly
     * one place a span may start, and an open path through one point draws nothing - so spans
     * were leaving coast that showed no eligibility at all, on about a third of the eligible
     * cells. The dot is the honest mark: eligibility really is a point there, not a stretch.
     *
     * @param g2 what to draw with
     */
    private void paintFrontages(Graphics2D g2) {

        // On the list rather than on either switch: the refresh gathered exactly what the
        // switches asked for, and a guard naming one switch here would hide the other's
        // stretches whenever it alone was on.
        if (frontages.isEmpty()) {
            return;
        }

        g2.setStroke(new BasicStroke(MapLook.FRONTAGE_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.bridgeFrontageColour, MapLook.OPAQUE_ALPHA));

        for (var frontage : frontages) {

            if (frontage.size() >= 2) {
                g2.draw(MapPainting.buildOpenPath(frontage));
            } else {
                g2.fill(MapPainting.buildCircle(
                    frontage.get(0), MapLook.FRONTAGE_DOT_RADIUS));
            }
        }
    }

    // The spans this construction lays over one shore's water, and what they close around.
    // One method for both shores, because after the shore has named its lines the two are one
    // construction: the same search, the same walk over what its spans walled.
    //
    // The spans are filtered against the coasts they were offered to, so which survive is a
    // question about THIS trace rather than about the cells alone. Found while either half of
    // them is wanted, because the spans and the water they hold are one construction seen
    // twice - a span is a line saying "this much is held between these cells", and the fill
    // is what a run of them closes around.
    //
    // What they close around comes from the construction the settled bridges' fill comes from,
    // with the spans as the only walls - so what comes back is the water a run of them holds,
    // whole. Only what a span actually walled, though: that walk finds the water the cells
    // closed unaided as well, and here that water is a lake or a puddle with a layer of its
    // own, which drawn from this list too would be painted twice and go on being painted with
    // its own switch off.
    //
    // The coast's reaches are deliberately NOT laid as walls beside the spans, and the reason
    // is the channel rather than the shape: a wall stands both its sides half a channel off
    // its own line, so a reach laid as a wall holds the water on its seaward side back too.
    // That strip is a whole channel wide along every reach and nothing draws it, so the fill's
    // edge stands off at each reach and runs flush along the cell arcs between them - which
    // reads as a notched coastline rather than a filled sea.
    private SpanWater findSpanWater(
            SectorFixture fixture,
            CoastFrontages.Shore shore,
            boolean isSpanLayerShown,
            boolean isFillLayerShown) {

        if (!isSpanLayerShown && !isFillLayerShown) {
            return SpanWater.NONE;
        }

        var spans = ContinentBridges.findAnchoredBridges(
            coast.getTrace(),
            shore,
            settings.parameters,
            settings.resolveContinentBridgeRules());

        if (!isFillLayerShown || spans.isEmpty()) {
            return new SpanWater(spans, List.of());
        }

        return new SpanWater(spans, VoidBridgePockets.findBridgeWalledPockets(
            fixture.getSites(),
            spans,
            settings.parameters,
            settings.resolvePocketShaping()));
    }

    // The stretches a span was allowed to anchor on, read off the same trace the spans are
    // anchored on rather than worked out again - so what is drawn as eligible is what the
    // search was actually offered.
    //
    // The two shores' gather into one list under their own switches: they are drawn
    // identically, and which shore a stretch belongs to is told by the line it sits on.
    private List<List<double[]>> gatherEligibleFrontages() {

        var eligible = new ArrayList<List<double[]>>();

        if (settings.showContinentCoastFrontages) {
            eligible.addAll(flattenFrontages(
                CoastFrontages.Shore.EXTERIOR.collectFrontages(coast.getTrace())));
        }
        if (settings.showContinentLakeFrontages) {
            eligible.addAll(flattenFrontages(
                CoastFrontages.Shore.INTERIOR.collectFrontages(coast.getTrace())));
        }
        return List.copyOf(eligible);
    }

    // Both shores' eligible stretches as the flat list the drawing walks - by-cell grouping
    // matters to the search, not to a painter.
    private static List<List<double[]>> flattenFrontages(
            Map<Integer, List<List<double[]>>> byCell) {

        return byCell.values()
            .stream()
            .flatMap(List::stream)
            .toList();
    }

    /**
     * The bridges that survived the coastlines, drawn end to end at their true extent.
     *
     * <p>Under the coast rather than over it, because the coastline is what JUDGED them: where
     * the two meet, the line that did the refusing is the one worth being able to see.
     *
     * @param g2 what to draw with
     */
    private void paintBridges(Graphics2D g2) {

        if (!settings.showContinentBridges
                && !settings.showContinentLakeBridges
                && !settings.showContinentPuddleBridges) {

            return;
        }

        g2.setStroke(new BasicStroke(MapLook.SPAN_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.continentBridgeColour,
            MapLook.OPAQUE_ALPHA));

        // All three sets in the same stroke and colour, because they are the same kind of
        // claim - "this much water is held between these cells" - told apart by the water each
        // sits over rather than by how it is drawn.
        //
        // Each list under its own switch rather than on whether it is empty: the inlet spans
        // are found for the fill as well as for themselves, so a set that exists is not a set
        // that was asked to be seen.
        if (settings.showContinentBridges) {
            paintSpans(g2, inletSpans);
        }
        if (settings.showContinentLakeBridges) {
            paintSpans(g2, lakeSpans);
        }
        if (settings.showContinentPuddleBridges) {
            paintSpans(g2, puddleBridges);
        }
    }

    // One set of spans at whatever stroke and colour are already set, so the three read as one
    // kind of mark - which they are.
    private static void paintSpans(Graphics2D g2, List<CellGap> spans) {

        for (var span : spans) {
            g2.draw(MapPainting.buildSpanLine(span));
        }
    }

    // One shore's spans and the water they shut in, handed back together because they are
    // found together - the fill is walked with exactly the spans that came out of the search,
    // and a pair carried as two loose lists could be reassembled across shores.
    private record SpanWater(List<CellGap> spans, List<List<double[]>> pockets) {

        // What a shore neither switch asks for comes back as, so an idle shore costs nothing.
        private static final SpanWater NONE = new SpanWater(List.of(), List.of());
    }
}
