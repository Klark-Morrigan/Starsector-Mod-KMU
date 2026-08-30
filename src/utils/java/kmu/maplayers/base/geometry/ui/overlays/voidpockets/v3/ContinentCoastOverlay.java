package kmu.maplayers.base.geometry.ui.overlays.voidpockets.v3;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.CoastFrontages;
import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.ContinentBridges;
import kmu.maplayers.base.geometry.PuddlePockets;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.VoidBridgeCache;
import kmu.maplayers.base.geometry.VoidBridgePockets;
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
    // Found whenever anything needs them rather than only when they are drawn, because they
    // are a wall before they are a mark: the void behind the coast is worked out with them
    // laid, so a walk without them runs a coast pocket through water a span is holding.
    private List<CellGap> inletSpans = List.of();

    // The spans laid across the puddles, held beside the trace whose puddles claimed them
    // for the same reason.
    private List<CellGap> puddleBridges = List.of();

    // The void the inlet spans close around, walked with the spans as the only walls - which
    // is what the settled construction does with its own bridges.
    //
    // The coast's reaches are deliberately NOT laid here, and the reason is the channel rather
    // than the shape: a wall stands both its sides half a channel off its own line, so a reach
    // laid as a wall holds the water on its seaward side back too. That strip is a whole
    // channel wide along every reach and nothing draws it, so the fill's edge stands off at
    // each reach and runs flush along the cell arcs between them - which reads as a notched
    // coastline rather than a filled sea.
    private List<List<double[]>> inletPockets = List.of();

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

        // Filtered against the coasts they were offered to, so which spans survive is a
        // question about THIS trace rather than about the cells alone. Found while either half
        // of them is wanted: the spans and the water they hold are one construction seen
        // twice, exactly as the settled bridges and their fill are.
        if (settings.showContinentBridges || settings.showContinentInletFill) {

            inletSpans = ContinentBridges.findAnchoredBridges(
                coast.getTrace(),
                settings.parameters,
                settings.resolveContinentBridgeRules());
        }

        // What those spans close around, from the construction the settled bridges' fill comes
        // from. The spans are the only walls in that walk, so what comes back is the water a
        // run of them holds, whole - and it lies over the coast's own fill where the two meet,
        // which is how the settled map composes its two fills as well.
        //
        // Only what a span actually walled. The walk finds the water the cells closed unaided
        // as well, and here that water is a lake or a puddle with a layer of its own - drawn
        // from this list too it would be painted twice, and go on being painted with its own
        // switch off.
        if (settings.showContinentInletFill && !inletSpans.isEmpty()) {

            inletPockets = VoidBridgePockets.findBridgeWalledPockets(
                fixture.getSites(),
                inletSpans,
                settings.parameters,
                settings.resolvePocketShaping());
        }

        // The void behind the coast, worked out by the same construction the settled coast's
        // fill comes from. Not a second way of arriving at the same thing: a coast reach is a
        // wall, and what a wall shuts in is one question however the coast that offered it was
        // traced.
        if (settings.showContinentCoastFill) {
            coast.findPockets(fixture);
        }

        // Read off the same trace the bridges are anchored on rather than worked out again,
        // so what is drawn as eligible is what the search was actually offered. The two
        // shores' frontages gather into one list under their own switches: they are drawn
        // identically, and which shore a stretch belongs to is told by the line it sits on.
        var eligible = new ArrayList<List<double[]>>();

        if (settings.showContinentCoastFrontages) {
            eligible.addAll(flattenFrontages(
                CoastFrontages.collectBridgeFrontages(coast.getTrace())));
        }
        if (settings.showContinentLakeFrontages) {
            eligible.addAll(flattenFrontages(
                CoastFrontages.collectLakeFrontages(coast.getTrace())));
        }
        frontages = List.copyOf(eligible);

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

        // One colour for all four, read once. Every one of them is water this construction
        // shut in - behind the outer shore, ringed by land, under a span, or too small for a
        // shore at all - and four readings of the pair is how they come to be drawn as four
        // kinds of thing when they are one.
        var water = settings.continentCoastalVoidColour;
        var edge = settings.continentCoastalVoidEdge;

        // The solid fills as ONE sheet rather than one layer over another. The coast and the
        // spans both hold the bays where a reach runs into water a span closed, and a wall
        // cannot be laid flush to keep them apart - it stands both its sides half a channel off
        // its own line, which would leave a strip along every reach that nothing draws. So the
        // overlap is kept and made free: filled once over the union, water two of them hold
        // reads exactly as water one of them holds.
        var sheet = new ArrayList<List<double[]>>();

        if (settings.showContinentCoastFill) {
            sheet.addAll(coast.collectPocketRings());
        }
        if (settings.showContinentInletFill) {
            sheet.addAll(inletPockets);
        }
        if (settings.showContinentPuddleFill) {
            sheet.addAll(coast.collectPuddleRings());
        }
        MapPainting.paintMergedRingFills(g2, sheet, water, settings.voidFillOpacity, edge);

        // The lake margins stay their own pass: a margin is an even-odd shape - the water
        // between the drawn shore and the cells' arcs, with the shore's inside left bare - and
        // that ring cannot join a sheet wound to make overlaps count.
        if (settings.showContinentLakeFill) {
            coast.paintLakeFills(g2, water, edge);
        }
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

        if (!settings.showContinentBridges && !settings.showContinentPuddleBridges) {
            return;
        }

        g2.setStroke(new BasicStroke(MapLook.SPAN_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.continentBridgeColour,
            MapLook.OPAQUE_ALPHA));

        // The puddle spans in the same stroke and colour, because they are the same kind of
        // claim - "this much void is held between these cells" - told apart by the water each
        // sits over.
        //
        // Each list under its own switch rather than on whether it is empty: the inlet spans
        // are found for the fill as well as for themselves, so a set that exists is not a set
        // that was asked to be seen.
        if (settings.showContinentBridges) {
            for (var span : inletSpans) {

                g2.draw(MapPainting.buildSpanLine(span));
            }
        }
        if (settings.showContinentPuddleBridges) {
            for (var span : puddleBridges) {

                g2.draw(MapPainting.buildSpanLine(span));
            }
        }
    }
}
