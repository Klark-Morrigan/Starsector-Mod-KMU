package kmu.maplayers.base.geometry.ui.overlays.voidpockets.v3;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.ContinentBridges;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.VoidFaces;
import kmu.maplayers.base.geometry.render.MapLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.ui.overlays.voidpockets.CoastalPocketsOverlay;
import kmu.maplayers.base.geometry.ui.settings.ViewerSettings;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.List;

/**
 * <b>The continent coast (v3), orchestrated.</b> Each touching-connected run of cells traced as
 * its own closed coast, the inlet bridges that survive those coasts, the pieces those lines cut
 * the void into, and the void each coast shuts in.
 *
 * <p>What makes this one the continent coast is that it traces with NO bridges laid, so a run
 * of cells a bridge would have joined comes back as several shapes rather than one, and under
 * its own coast rules. That is the whole of v3's disagreement with the settled coast. The fill
 * below it is {@link CoastalPocketsOverlay}, which the settled coast in the neighbouring
 * package runs exactly as well - which is the point: with the same code on both sides, a
 * difference on screen is a difference between the coasts.
 *
 * <p>The bridges and the pieces they cut are this construction's own. The settled coast treats
 * a bridge as connective tissue and traces one line round everything it joins; the proposal is
 * to give each continent its own smoothed coast and let the bridges cross those lines where
 * they must, with each crossing cutting the piece behind it in two. Whether that is worth
 * building turns on where and how often the crossings actually fall, which is only legible on
 * screen.
 *
 * <p>Drawn as a line over everything for the same reason the settled coast is: it is a
 * proposal, and the only way to judge it is against the bridges and the coast it would
 * replace, all on screen at once.
 */
public final class ContinentCoastOverlay {

    // An odd multiplier, so the two coordinates of a place cannot cancel on a diagonal.
    private static final int PLACE_HASH_MIX = 31;

    private final ViewerSettings settings;

    // The half of this overlay the settled construction runs identically. Held rather than
    // inherited from, because what v2 and v3 share is a sequence of steps rather than an
    // identity - neither is a kind of the other, and nothing ever asks for "a coast overlay"
    // without knowing which.
    private final CoastalPocketsOverlay coast;

    // The bridges that survive those coastlines. Held beside the trace they were filtered
    // against rather than found while painting, since which ones survive is a question about
    // THAT trace and a frame that asked it again could answer about a different one.
    private List<CellGap> bridges = List.of();

    // The pieces those lines cut the map into, and the walls left standing to cut them. Held
    // for the same reason and found from the same two lists: a piece is bounded by the coast
    // and the bridges as they came out, so pieces found against any other pair of them would
    // be pieces of a different map.
    private VoidFaces.CutMap cut;

    // The stretches of border the bridges were allowed to anchor on, held beside the trace
    // that decided them. One run per stretch rather than one list per cell, because a cell
    // facing the void twice is eligible in two separate places and drawing them as one line
    // would run a mark straight through the cell between them.
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
     */
    public void refresh(SectorFixture fixture) {

        coast.acceptTrace(null);
        bridges = List.of();
        cut = null;
        frontages = List.of();

        // The coasts are traced while any of the three is wanted, because each is built on the
        // one before: the bridges are filtered against the coasts, and the pieces are cut by
        // both. Switching a later one on without the lines that decide it would show a set
        // nothing on screen accounts for.
        //
        // Under the switch over the whole continent construction, which suppresses this
        // without touching any of its own - so what was showing comes back when it is lifted.
        if (!settings.showContinentVoid
                || (!settings.showContinentCoasts
                    && !settings.showContinentCoastalFill
                    && !settings.showContinentBridges
                    && !settings.showVoidFaces
                    && !settings.showBridgeFrontages)) {

            return;
        }

        // The one line that makes this v3: continent coasts, which lay no bridges.
        coast.acceptTrace(Coastlines.traceContinentCoasts(
            fixture.getSites(),
            settings.parameters,
            settings.resolveContinentCoastRules()));

        // The void behind the coast, worked out by the same construction the settled coast's
        // fill comes from. Not a second way of arriving at the same thing: a coast reach is a
        // wall, and what a wall shuts in is one question however the coast that offered it was
        // traced.
        if (settings.showContinentCoastalFill) {
            coast.findPockets(fixture);
        }

        // Read off the same trace the bridges are anchored on rather than worked out again,
        // so what is drawn as eligible is what the search was actually offered.
        if (settings.showBridgeFrontages) {

            frontages = ContinentBridges.collectBridgeFrontages(coast.getTrace())
                .values()
                .stream()
                .flatMap(List::stream)
                .toList();
        }

        // Found whenever the pieces are wanted, whether or not the bridges are DRAWN. A piece
        // is cut by every line laid, so pieces found from the coasts alone while the bridges
        // were merely hidden would be pieces of a map nobody proposed.
        if (settings.showContinentBridges || settings.showVoidFaces) {

            bridges = ContinentBridges.findAnchoredBridges(
                coast.getTrace(),
                settings.parameters,
                settings.resolveContinentBridgeRules());
        }

        if (settings.showVoidFaces) {

            cut = VoidFaces.cutMapIntoPieces(
                Coastlines.collectCoastRings(coast.getTrace()),
                bridges,
                fixture.getSites(),
                settings.resolveFoldRules());
        }
    }

    /**
     * Draws the void each continent coast shut in, beneath the cells.
     *
     * <p>At the same weight and in the same way as the settled coast's fill, because the two
     * are on screen to be compared: any difference between them should be the coast, not the
     * drawing.
     *
     * @param g2 what to draw with
     */
    public void paintPocketFills(Graphics2D g2) {

        if (!settings.showContinentVoid || !settings.showContinentCoastalFill) {
            return;
        }

        coast.paintPocketFills(
            g2, settings.continentCoastalVoidColour, settings.continentCoastalVoidEdge);
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

        paintFaces(g2);
        paintBridges(g2);

        if (settings.showContinentCoasts) {

            coast.paintCoastRings(g2, settings.continentCoastColour);
            coast.paintDroppedStretches(g2);
        }

        // Last of all, and so over every wall rather than under them. What it marks is which
        // stretches of those walls a span could have started from, and a mark drawn beneath
        // the lines it is about is hidden by exactly the ones worth reading it against.
        paintFrontages(g2);
    }

    /**
     * The water each piece covers, filled, each piece in its own shade.
     *
     * <p>Filled and not outlined. An outline of a piece is a line where the coast or a bridge
     * already runs, so outlining draws the walls a second time and says nothing the lines did
     * not already say; what is not on screen without this is which side of a wall belongs with
     * which, and only a fill can show that.
     *
     * <p>The shade comes off the piece's own place on the map, so it is the same shade on
     * every frame and on every run - a piece that changed colour when something elsewhere
     * moved would read as having changed.
     *
     * <p>Under everything, since it is a backdrop to the lines rather than a thing drawn over
     * them, and the lines are what a reader is checking it against.
     *
     * <p>Land is left out. One piece per continent comes back holding that continent's cells,
     * and filling it would paint over the cells in a colour that means "one piece of water".
     */
    private void paintFaces(Graphics2D g2) {

        if (!settings.showVoidFaces || cut == null) {
            return;
        }

        for (var face : cut.pieces()) {

            if (face.holdsCells()) {
                continue;
            }

            g2.setColor(MapPainting.applyAlpha(
                MapPainting.jitterBrightness(
                    settings.voidFaceColour,
                    hashPlace(face),
                    MapLook.VOID_FACE_JITTER),
                MapLook.VOID_FACE_ALPHA));

            g2.fill(MapPainting.buildPath(face.boundary()));
        }
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
     * @param g2 what to draw with
     */
    private void paintFrontages(Graphics2D g2) {

        if (!settings.showBridgeFrontages) {
            return;
        }

        g2.setStroke(new BasicStroke(MapLook.FRONTAGE_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.bridgeFrontageColour, MapLook.OPAQUE_ALPHA));

        for (var frontage : frontages) {
            g2.draw(MapPainting.buildOpenPath(frontage));
        }
    }

    // A piece's first corner, as a number to spread its shade from. Its place rather than its
    // index, so the shades do not all move when a piece is added or dropped somewhere else.
    private static int hashPlace(VoidFaces.Face face) {

        var corner = face.boundary().get(0);

        return Double.hashCode(corner[0]) * PLACE_HASH_MIX + Double.hashCode(corner[1]);
    }

    /**
     * The bridges that survived the coastlines, drawn end to end at their true extent.
     *
     * <p>Under the coast rather than over it, because the coastline is what JUDGED them: where
     * the two meet, the line that did the refusing is the one worth being able to see.
     *
     * <p><b>What still stands, once the pieces have been cut.</b> A fold takes out a STRETCH
     * of a bridge, so drawing the bridges as they were laid would put a line across a piece
     * nothing divides there any more - which reads as a wall that failed rather than as one
     * deliberately taken out. With no pieces asked for there is nothing to have folded, and
     * the bridges as laid are what stands.
     */
    private void paintBridges(Graphics2D g2) {

        if (!settings.showContinentBridges) {
            return;
        }

        g2.setStroke(new BasicStroke(MapLook.SPAN_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.continentBridgeColour,
            MapLook.OPAQUE_ALPHA));

        if (cut == null) {

            for (var bridge : bridges) {
                g2.draw(MapPainting.buildSpanLine(bridge));
            }
            return;
        }

        for (var standing : cut.standingWalls()) {
            g2.draw(new Line2D.Double(
                standing[0][0], standing[0][1], standing[1][0], standing[1][1]));
        }
    }
}
