package kmu.maplayers.base.geometry.ui;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.CoastPockets;
import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.ContinentBridges;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.VoidFaces;
import kmu.maplayers.base.geometry.VoidPockets;
import kmu.maplayers.base.geometry.WalledPocket;
import kmu.maplayers.base.geometry.render.MapLook;
import kmu.maplayers.base.geometry.render.MapPainting;

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
 * <p>The pair to {@link SectorCoastOverlay}, which is the settled construction (v2). Between
 * them, these two classes are the only places that say which construction is which: everything
 * they call - {@link Coastlines}, {@link ContinentBridges}, {@link VoidFaces},
 * {@link CoastPockets}, {@link MapPainting} - is shared or is machinery neither owns. So the
 * difference between v2 and v3 is legible as the difference between two short files, rather
 * than as a flag threaded through the ones underneath.
 *
 * <p>What makes this one the continent coast is that it traces with NO bridges laid, so a run
 * of cells a bridge would have joined comes back as several shapes rather than one, and under
 * its own coast rules. The fill below it is the settled coast's own construction asked of this
 * trace - which is the point: with the same code on both sides, a difference on screen is a
 * difference between the coasts.
 *
 * <p>Preview of per-continent coastlines, over the settled map rather than instead of it.
 *
 * <p>A rival construction being judged, not a part of the map. The settled coast treats a
 * bridge as connective tissue and traces one line round everything it joins; the proposal is
 * to give each continent its own smoothed coast and let the bridges cross those lines where
 * they must, with each crossing cutting the pocket behind it in two. Whether that is worth
 * building turns on where and how often the crossings actually fall, which is only legible
 * on screen - so this draws the lines and nothing else. No pockets are built from it,
 * nothing downstream reads it, and switching it on moves nothing but paint.
 *
 * <p>Drawn as a line over everything for the same reason the settled coast is: it is a
 * proposal, and the only way to judge it is against the bridges and the coast it would
 * replace, all on screen at once.
 */
public final class ContinentCoastOverlay {

    // An odd multiplier, so the two coordinates of a place cannot cancel on a diagonal.
    private static final int PLACE_HASH_MIX = 31;

    private final ViewerSettings settings;

    // What the last trace found, held rather than recomputed while painting: a frame that
    // rebuilt these would be drawing lines traced against geometry the rest of the frame is
    // not being drawn from.
    //
    // The whole trace rather than the rings alone, because what the smoothing LEFT OUT is
    // part of what a reader is judging and can only be had from the walk that left it out.
    private Coastlines.TracedCoasts traced;

    // The bridges that survive those coastlines. Held beside the trace they were filtered
    // against rather than found while painting, since which ones survive is a question about
    // THAT trace and a frame that asked it again could answer about a different one.
    private List<CellGap> bridges = List.of();

    // The pieces those lines cut the map into, and the walls left standing to cut them. Held
    // for the same reason and found from the same two lists: a piece is bounded by the coast
    // and the bridges as they came out, so pieces found against any other pair of them would
    // be pieces of a different map.
    private VoidFaces.CutMap cut;

    // The void those coastlines shut in, found the way the settled coast's is. Held beside the
    // trace for the same reason as everything else here: a fill measured against one trace and
    // drawn over another is a fill of a map nobody built.
    private List<WalledPocket> pockets = List.of();

    public ContinentCoastOverlay(ViewerSettings settings) {
        this.settings = settings;
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

        traced = null;
        bridges = List.of();
        cut = null;
        pockets = List.of();

        // The coasts are traced while any of the three is wanted, because each is built on the
        // one before: the bridges are filtered against the coasts, and the pieces are cut by
        // both. Switching a later one on without the lines that decide it would show a set
        // nothing on screen accounts for.
        if (!settings.showContinentCoasts
                && !settings.showContinentCoastalFill
                && !settings.showContinentBridges
                && !settings.showVoidFaces) {

            return;
        }

        traced = Coastlines.traceContinentCoasts(
            fixture.getSites(),
            settings.parameters,
            settings.resolveContinentCoastRules());

        // The void behind the coast, worked out by the same construction the settled coast's
        // fill comes from. Not a second way of arriving at the same thing: a coast reach is a
        // wall, and what a wall shuts in is one question however the coast that offered it was
        // traced.
        if (settings.showContinentCoastalFill) {

            pockets = CoastPockets.findCoastPockets(
                traced,
                fixture.getOwnerBySite(),
                new VoidPockets.PocketRules(
                    settings.parameters, settings.resolvePocketShaping()));
        }

        // Found whenever the pieces are wanted, whether or not the bridges are DRAWN. A piece
        // is cut by every line laid, so pieces found from the coasts alone while the bridges
        // were merely hidden would be pieces of a map nobody proposed.
        if (settings.showContinentBridges || settings.showVoidFaces) {

            bridges = ContinentBridges.findAnchoredBridges(
                traced,
                settings.parameters,
                settings.resolveContinentBridgeRules());
        }

        if (settings.showVoidFaces) {

            cut = VoidFaces.cutMapIntoPieces(
                Coastlines.collectCoastRings(traced),
                bridges,
                fixture.getSites(),
                settings.resolveFoldRules());
        }
    }

    /**
     * Draws the void each continent coast shut in, beneath the cells.
     *
     * <p>Under them, and drawn at the same weight and in the same way as the settled coast's
     * fill, because the two are on screen to be compared: any difference between them should
     * be the coast, not the drawing.
     *
     * @param g2 what to draw with
     */
    public void paintPocketFills(Graphics2D g2) {

        if (!settings.showContinentCoastalFill) {
            return;
        }

        MapPainting.paintPocketFills(
            g2,
            pockets,
            settings.continentCoastalVoidColour,
            settings.voidFillOpacity,
            settings.continentCoastalVoidEdge);
    }

    /**
     * Draws each continent's coast, over the top of everything.
     *
     * @param g2 what to draw with
     */
    public void paintCoasts(Graphics2D g2) {

        if (traced == null) {
            return;
        }

        paintFaces(g2);
        paintBridges(g2);

        if (settings.showContinentCoasts) {

            MapPainting.paintLineRings(
                g2,
                Coastlines.collectCoastRings(traced),
                settings.continentCoastColour);
        }

        if (!settings.showDroppedStretches || !settings.showContinentCoasts) {
            return;
        }

        // In the same colour the settled coast marks its own drops with. The two
        // constructions are told apart by the line each drop sits beside, and a second
        // colour would imply the drops themselves differ in kind, which they do not.
        MapPainting.paintLineRuns(
            g2,
            Coastlines.collectDroppedRuns(
                traced,
                settings.parameters.measureArcSegments()),
            settings.droppedStretchColour);
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
