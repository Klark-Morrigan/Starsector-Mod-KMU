package kmu.maplayers.base.geometry.ui.overlays.voidpockets;

import kmu.maplayers.base.geometry.CoastPockets;
import kmu.maplayers.base.geometry.CoastRounding;
import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.LandableFrontages;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.VoidPockets;
import kmu.maplayers.base.geometry.WalledPocket;
import kmu.maplayers.base.geometry.render.FillSheet;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/**
 * The half of a coast overlay that both rival constructions run identically: a traced coast,
 * the void it shut in, and the drawing of the two.
 *
 * <p>Not driven from the window itself. Each construction holds one of these and drives it,
 * because what a construction is IS the trace it makes, and the trace is the one thing this
 * cannot do for itself.
 *
 * <p>Two constructions are on screen to be compared, and they differ in exactly one thing -
 * how the coast is traced. Everything after that trace is one answer to one question: what a
 * coast reach shuts in is the same question however the coast offering it was arrived at. Each
 * construction holding its own copy of those steps is how the pair stops being a comparison:
 * the moment one copy is tuned and the other is not, a difference on screen is a difference in
 * the drawing rather than in the coasts, and looking at them side by side answers nothing.
 *
 * <p>So a construction owns its trace and hands it here, and what happens to a trace afterwards
 * lives in one place. What is left in each construction's own file is the part that makes it
 * that construction - which is what a reader comes to those files to find.
 *
 * <p>Colours are passed per call rather than held. They are live settings a panel writes to
 * while the window is open, and a set captured when this was built would go on drawing the map
 * in whatever the colours were at startup.
 */
public final class CoastalPocketsOverlay {

    private final ViewerSettings settings;

    // What the last trace found, held rather than recomputed while painting: a frame that
    // rebuilt either would be drawing marks measured against geometry the rest of the frame
    // is not being drawn from.
    private Coastlines.TracedCoasts traced;
    private List<WalledPocket> pockets = List.of();

    // The accepted trace's lines with their sharp joins taken off, which is what this actually
    // strokes. Rounded on acceptance rather than while painting: a sector's coasts are tens of
    // thousands of points, and a pass repeated per frame is paid for per frame.
    private CoastRounding.RoundedCoasts rounded = CoastRounding.RoundedCoasts.NONE;

    // Where a straight line could arrive on the accepted trace's cells from the open void.
    // Measured on acceptance for the reason above and more so: the answer costs a sweep over
    // every disc per sampled angle, which is the most expensive thing this overlay draws.
    //
    // Behind its own switch rather than measured whenever a trace arrives, since nothing reads
    // it - a sector nobody has asked the question of should not pay for the answer.
    private List<List<double[]>> landable = List.of();

    public CoastalPocketsOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Takes the trace a construction has just made, and forgets whatever came before it.
     *
     * <p>The pockets go with it. They are a fact about a particular trace, and keeping the old
     * ones alongside a new coast would draw the void one line shut in underneath another. So is
     * the rounding, which is this trace's borders taken to the line the map strokes.
     *
     * @param traced the trace, or null where the construction is switched off entirely
     */
    public void acceptTrace(Coastlines.TracedCoasts traced) {

        this.traced = traced;
        this.pockets = List.of();
        this.rounded = traced == null
            ? CoastRounding.RoundedCoasts.NONE
            : CoastRounding.roundTracedCoasts(traced, settings.resolveCoastRules().rounding());
        this.landable = traced == null || !settings.showLandableFrontage
            ? List.of()
            : LandableFrontages.collectLandableRuns(
                traced, settings.parameters.measureArcSegments());
    }

    /**
     * Works out the void the accepted trace shut in.
     *
     * <p>Asked for separately rather than done on acceptance, because a construction may want
     * its coast on screen without its fill - and the pockets are the expensive half.
     *
     * @param fixture the sector the trace was made in
     */
    public void findPockets(SectorFixture fixture) {

        pockets = CoastPockets.findCoastPockets(
            traced,
            fixture.getOwnerBySite(),
            new VoidPockets.PocketRules(
                settings.parameters, settings.resolvePocketShaping()));
    }

    /**
     * Whether a trace has been accepted and not since dropped.
     *
     * @return true where there is something to draw
     */
    public boolean hasTrace() {
        return traced != null;
    }

    /**
     * The accepted trace, for the marks a construction draws on its own.
     *
     * @return it, or null where none has been accepted
     */
    public Coastlines.TracedCoasts getTrace() {
        return traced;
    }

    /**
     * The void found behind the accepted trace, for the faults a construction checks it for.
     *
     * @return the pockets, empty until {@link #findPockets} has been asked for
     */
    public List<WalledPocket> getPockets() {
        return pockets;
    }

    /**
     * Draws the void the coast shut in, beneath the cells.
     *
     * <p>Under them, so a stray edge reads as the mistake it is rather than painting over the
     * shape it got wrong. Unlike the coast LINE, which goes over everything: the line is a
     * proposal to be judged against the arcs underneath it, and a fill of the space it closed
     * off hides none of them.
     *
     * @param g2   what to draw with
     * @param fill what to fill the pockets with
     * @param edge what to outline them in
     */
    public void paintPocketFills(Graphics2D g2, Color fill, Color edge) {

        MapPainting.paintPocketFills(g2, pockets, settings.resolveWaterLook(fill, edge));
    }

    /**
     * The void behind the coast as bare rings, for a construction that fills it together with
     * water of its own rather than on its own.
     *
     * @return one ring per outline, empty until {@link #findPockets} has been asked for
     */
    public List<List<double[]>> collectPocketRings() {
        return MapPainting.collectPocketOutlines(pockets);
    }

    /**
     * Every puddle's whole water, as the ring that bounds it.
     *
     * <p>The whole of it, where a lake gives up its own {@link #addLakeMargins margin}, because
     * the two say different things. A lake's open water is left to the backdrop the way the
     * sector's open void is - the margin marks what the drawn shore conceded against the cells'
     * true edge. A puddle has no shore to concede anything, and water drawn as backdrop reads
     * as open void, which is exactly what a puddle is not.
     *
     * @return one ring per puddle
     */
    public List<List<double[]>> collectPuddleRings() {

        return traced.puddles().stream().map(Coastlines.Puddle::waterEdge).toList();
    }

    /**
     * Draws each coast, over the top of everything.
     *
     * @param g2     what to draw with
     * @param colour what to draw the line in, which is what tells the two constructions apart
     */
    public void paintCoastRings(Graphics2D g2, Color colour) {

        MapPainting.paintLineRings(g2, rounded.coasts(), colour);
    }

    /**
     * Adds every lake's margin to a sheet: the water between the drawn shore and the cells'
     * own arcs, with the water inside the shore left out.
     *
     * <p>The margin rather than the whole lake, because that is what the coastal fill shows
     * of the outer shore - what the drawn line gave up against the cells' true edge - and a
     * lake filled solid answers a different question in the same colour.
     *
     * <p>Into a sheet rather than painted here, because a margin is a claim about water some
     * other layer may also be filling. Painted on its own it would lay a second translucent
     * body over that layer's, and the concession band would come out darker than the water
     * either side of it - the band reading as a third kind of thing rather than as the edge of
     * one. In the sheet the two are one body, and a lake nothing else fills keeps the bare
     * middle a margin has always meant.
     *
     * <p>Against the ROUNDED shore, which is the one place a fill is measured from a rounded
     * line rather than from a border: the margin exists to meet the stroke on screen, so a band
     * ending at the border would show a sliver of bare water wherever the rounding stepped
     * inside it.
     *
     * @param sheet the sheet to add them to
     */
    public void addLakeMargins(FillSheet sheet) {

        var lakes = traced.lakes();

        for (var index = 0; index < lakes.size(); index++) {
            sheet.addMargin(lakes.get(index).waterEdge(), rounded.lakes().get(index));
        }
    }

    /**
     * Draws each lake's shore, over the top of everything.
     *
     * <p>At the coasts' own weight and in their colour, because a lake shore IS a coast of
     * this construction: drawn any other way, the same line would read as two kinds of thing
     * depending on which side of the land it fell.
     *
     * @param g2     what to draw with
     * @param colour what to draw the line in
     */
    public void paintLakeRings(Graphics2D g2, Color colour) {

        MapPainting.paintLineRings(g2, rounded.lakes(), colour);
    }

    /**
     * Draws the frontages the smoothing left out, on the borders they sit on.
     *
     * <p>Over the coast rather than under it, because the two are read together: what a drop
     * bought is the gap between the stretch and the line that replaced it, and a mark hidden
     * beneath that line says nothing.
     *
     * <p>In one colour for both constructions. They are told apart by the line each drop sits
     * beside, and a second colour would imply the drops themselves differ in kind, which they
     * do not.
     *
     * @param g2 what to draw with
     */
    public void paintDroppedStretches(Graphics2D g2) {

        if (!settings.showDroppedStretches) {
            return;
        }

        MapPainting.paintLineRuns(
            g2,
            Coastlines.collectDroppedRuns(traced, settings.parameters.measureArcSegments()),
            settings.droppedStretchColour);
    }

    /**
     * Draws the stretches of border a straight line could arrive at, on the borders they sit on.
     *
     * <p>Beside the coast and the drops rather than instead of either, because the three only
     * mean anything read together: a drop over landable border is detail the rules gave up that
     * something could have used, and coast over border nothing can arrive at is line the map
     * draws where no wall will ever meet it.
     *
     * @param g2 what to draw with
     */
    public void paintLandableFrontage(Graphics2D g2) {

        if (!settings.showLandableFrontage) {
            return;
        }

        MapPainting.paintLineRuns(g2, landable, settings.landableFrontageColour);
    }
}
