package kmu.maplayers.base.geometry.ui.overlays.voidpockets.v3;

import kmu.maplayers.base.geometry.BridgedContinents;
import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.CoastFrontages;
import kmu.maplayers.base.geometry.CoastRounding;
import kmu.maplayers.base.geometry.FilledWater;
import kmu.maplayers.base.geometry.IntercontinentalCoasts;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.render.FillSheet;
import kmu.maplayers.base.geometry.render.MapLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.maplayers.base.geometry.ui.overlays.voidpockets.CoastalPocketsOverlay;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <b>The continent coast (v3), orchestrated.</b> Each touching-connected run of cells traced as
 * its own closed coast, and everything that reading of the sector then produces: the outer
 * shores and the void behind them, the lake shores round the water the cells closed unaided,
 * the puddles too small for a shore, the spans laid across the inlets and the puddles and the
 * water those spans shut in, and the links laid between one continent and the next with the sea
 * a run of them takes in and the coastline they made drawable.
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

    // This frame's water, opened once against the fixture's own colouring. Held rather than
    // opened while painting, because a sheet gathered from a second inventory could be filling
    // shapes the rest of the frame was not drawn from - and because what each layer costs is
    // then paid once however many times the sheet is gathered.
    //
    // Null while nothing is traced, which is the one state that has no water rather than empty
    // water.
    private FilledWater water;

    // The lines each search laid, held beside the trace they were filtered against rather than
    // found while painting, since which ones survive is a question about THAT trace and a frame
    // that asked it again could answer about a different one.
    //
    // Held apart from each other because each is drawn under its own switch, and which shore a
    // span was anchored on is the whole of what tells the sets apart.
    private List<CellGap> inletSpans = List.of();
    private List<CellGap> lakeSpans = List.of();
    private List<CellGap> puddleBridges = List.of();
    private List<CellGap> links = List.of();

    // The coastline those links added: the sector traced a second time with them laid, cut down
    // to what the first line does not already carry. Held beside them rather than found while
    // painting, since it is a second trace rather than a way of drawing the first.
    private List<List<double[]>> linkShores = List.of();

    // The stretches of border a bridge may anchor on - exterior coasts, lake shores, or both,
    // as the switches asked - held beside the trace that decided them. One run per stretch
    // rather than one list per cell, because a cell facing the void twice is eligible in two
    // separate places and drawing them as one line would run a mark straight through the cell
    // between them.
    private List<List<double[]>> frontages = List.of();

    public ContinentCoastOverlay(ViewerSettings settings) {

        this.settings = settings;
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
     * @param laid    the laying to draw, shared with whatever else reads this frame's walls
     */
    public void refresh(SectorFixture fixture, BridgedContinents laid) {

        coast.acceptTrace(null, CoastRounding.RoundedCoasts.NONE);
        water = null;
        inletSpans = List.of();
        lakeSpans = List.of();
        puddleBridges = List.of();
        links = List.of();
        linkShores = List.of();
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

        coast.acceptTrace(laid.traceCoasts(), laid.roundCoasts());

        // Against the fixture's own owners, because that is the map on screen: a pocket one
        // owner rings is pushed out into that owner's fills rather than keeping the channel.
        water = laid.fillWater(fixture.getOwnerBySite(), settings.resolvePocketShaping());

        // One set asked of each search in turn, each under the switches that show it. Separate
        // asks rather than one gathered set, because they are laid independently: a formation
        // is thinned among the spans it shares an anchor with, and a span across a lake shares
        // no anchor with one across the void outside the continent.
        //
        // The inlet spans are taken while anything of the links is wanted as well as under
        // their own switch, because the links are judged against them - which the laying
        // enforces on its own, and is said here because it is also why this layer is paid for
        // with its switch off.
        if (settings.showContinentBridges || isAnyLinkLayerShown()) {
            inletSpans = laid.layInletSpans();
        }
        if (settings.showContinentLakeBridges) {
            lakeSpans = laid.layLakeSpans();
        }
        if (settings.showContinentPuddleBridges) {
            puddleBridges = laid.claimPuddleSpans();
        }

        frontages = gatherEligibleFrontages();

        // Last, because it is the one search laid against what the others left down rather than
        // against the coasts alone.
        if (isAnyLinkLayerShown()) {

            links = laid.layLinks();

            // Off the same links, so what is drawn is the coastline of the lines on screen. And
            // under the same coast rules as the first trace: the cut keeps whatever the two
            // traces disagree about, so a second smoothing would have them agreeing nowhere and
            // the whole sector would come back as new coastline.
            //
            // A second trace of the sector, which is the expensive half of this overlay - hence
            // under its own switch rather than found alongside the links.
            if (settings.showIntercontinentalShores) {

                linkShores = IntercontinentalCoasts.findLinkedShores(
                    coast.getTrace(),
                    links,
                    settings.parameters,
                    settings.resolveContinentCoastRules());
            }
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

        // In one colour, because every layer of it is water this construction shut in - behind
        // the outer shore, ringed by land, under a span on either shore, or too small for a
        // shore at all. Drawn in several, they would read as several kinds of thing when they
        // are one.
        gatherWaterSheet().paint(
            g2,
            settings.resolveWaterLook(
                settings.continentCoastalVoidColour, settings.continentCoastalVoidEdge));
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
            coast.paintLandableFrontage(g2);
        }

        // In the coasts' own colour and at their own weight, because that is what these are: the
        // same walk's answer about the same sector with the links laid. A colour of their own
        // would say an isthmus edge is a different kind of line from the coast it runs into,
        // which is the one thing it is not.
        if (settings.showIntercontinentalShores) {
            MapPainting.paintLineRuns(g2, linkShores, settings.continentCoastColour);
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

    // Every layer of water that is switched on, gathered as ONE sheet rather than painted one
    // over another. Each pair of them overlaps by construction and no wall can be laid to keep
    // a pair apart: a wall stands both its sides half a channel off its own line, so dividing
    // them would leave a strip that nothing draws. The spans hold the bays a coast reach runs
    // into, and they hold the lake water a shore conceded its margin out of. So the overlaps
    // are kept and made free: filled once over the union, water two layers hold reads exactly
    // as water one of them holds.
    private FillSheet gatherWaterSheet() {

        var sheet = new FillSheet();

        if (settings.showContinentCoastFill) {
            sheet.addRings(water.collectShoreWater());
        }
        if (settings.showContinentInletFill) {
            sheet.addRings(water.collectInletWater());
        }
        if (settings.showContinentLakePocketFill) {
            sheet.addRings(water.collectLakeWater());
        }
        if (settings.showContinentPuddleFill) {
            sheet.addRings(water.collectPuddleWater());
        }
        if (settings.showIntercontinentalFill) {
            sheet.addRings(water.collectLinkWater());
        }

        // Last, and the one layer that takes water back OUT of the sheet: a lake's open middle
        // stays bare unless the spans' pockets fill it, which is what the two layers each mean
        // with the other switched off.
        if (settings.showContinentLakeFill) {

            for (var margin : water.collectLakeMargins()) {
                sheet.addMargin(margin.waterEdge(), margin.drawnShore());
            }
        }

        return sheet;
    }

    // Whether anything the links produce is wanted, which is what decides whether they are laid
    // at all. Both their layers rather than the lines' alone: the fill is walked with the links
    // as its subject, so asking for it is asking for them.
    private boolean isAnyLinkLayerShown() {

        return settings.showIntercontinentalBridges
            || settings.showIntercontinentalFill
            || settings.showIntercontinentalShores;
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
                && !settings.showContinentPuddleBridges
                && !settings.showIntercontinentalBridges) {

            return;
        }

        g2.setStroke(new BasicStroke(MapLook.SPAN_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.continentBridgeColour,
            MapLook.OPAQUE_ALPHA));

        // The first three sets in the same stroke and colour, because they are the same kind of
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

        // In their own colour, because they are the one set doing something else: each of the
        // three above holds water between cells of one shape, while a link joins two shapes and
        // holds nothing until another link joins the same pair. Drawn in the same colour they
        // would read as more of the same, which is the one thing worth being able to tell at a
        // glance here.
        if (settings.showIntercontinentalBridges) {

            g2.setColor(MapPainting.applyAlpha(
                settings.intercontinentalBridgeColour, MapLook.OPAQUE_ALPHA));

            paintSpans(g2, links);
        }
    }

    // One set of spans at whatever stroke and colour are already set, so the three read as one
    // kind of mark - which they are.
    private static void paintSpans(Graphics2D g2, List<CellGap> spans) {

        for (var span : spans) {
            g2.draw(MapPainting.buildSpanLine(span));
        }
    }
}
