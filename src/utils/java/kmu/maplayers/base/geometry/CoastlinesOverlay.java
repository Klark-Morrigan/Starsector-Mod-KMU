package kmu.maplayers.base.geometry;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.List;

/**
 * The smoothed outer edge, drawn: one line round each run of connected cells.
 *
 * <p>Its own class alongside {@link VoidPocketsOverlay} and {@link VoidBridgesOverlay}, and
 * switched on and off like them, because it is a proposal about the same map rather than a
 * settled part of it - and the only way to judge a smoothed edge is against the scalloped one
 * it replaces, both on screen at once.
 *
 * <p>Drawn as a line over everything rather than as a fill under it, for that reason. A fill
 * would hide the arcs the line is meant to be compared with, which is the one thing looking
 * at it is for. The outline it hands back is an ordinary closed ring, so filling it later
 * needs nothing this class does not already produce.
 */
final class CoastlinesOverlay {

    // What converts a count of sides round a whole circle into a count of samples per half
    // turn of arc.
    private static final int HALF_TURNS_PER_CIRCLE = 2;

    // Heavier than any other line on the map, so a crossing is findable at the zoom where a
    // whole sector fits on screen rather than only once somebody is already looking at it.
    private static final float PENETRATION_STROKES = 2f;

    // A radius either side of the centre makes the box a circle is drawn in.
    private static final int DIAMETERS = 2;

    private final ViewerSettings settings;

    private List<List<Coastlines.CoastVertex>> coasts = List.of();
    private List<Coastlines.Penetration> penetrations = List.of();

    // Held from the last trace so the marks can be drawn against the same discs the coast was
    // measured against, rather than against whatever the sliders have been moved to since.
    private DiscUnion tracedUnion;

    CoastlinesOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Traces the smoothed edges again, or drops them when the overlay is switched off.
     *
     * <p>Traced at the reach the CELLS are filled to, not the one the void shapes are drawn
     * at. Where a coast runs along a cell it should be the cell's own border and nothing
     * else: traced a channel further out it sits a channel outside every cell it hugs, and
     * the part that was only ever meant to join one cell to the next is buried in a line
     * running all the way round each of them. At the fill's own reach the hugging half lands
     * under the border already drawn there and only the reaches between cells show.
     *
     * @param fixture the sector to trace in
     */
    void refresh(SectorFixture fixture) {

        if (!settings.showCoastlines) {

            coasts = List.of();
            penetrations = List.of();
            return;
        }

        var parameters = settings.parameters;

        tracedUnion = new DiscUnion(fixture.getSites(), parameters.measureFilledReach());

        coasts = Coastlines.traceSmoothedCoasts(
            tracedUnion,
            buildWalls(fixture, parameters),
            buildSmoothingRules(),
            parameters.boundSegments() / HALF_TURNS_PER_CIRCLE);

        penetrations = Coastlines.findPenetrations(coasts, tracedUnion);
    }

    /**
     * Draws each smoothed edge, over the top of everything.
     *
     * @param g2 what to draw with
     */
    void paintCoasts(Graphics2D g2) {

        g2.setStroke(new BasicStroke(ViewerPainting.SPAN_STROKE));
        g2.setColor(ViewerPainting.applyAlpha(
            settings.coastlineColour, ViewerPainting.OPAQUE_ALPHA));

        for (var coast : coasts) {
            g2.draw(ViewerPainting.buildPath(Coastlines.collectPoints(coast)));
        }
        paintPenetrations(g2);
    }

    // The runs that go inside a cell, and the cells they go inside. Last of everything and in
    // colours nothing else uses, because the whole reason to draw them is to be able to point
    // at one - a count says there are nineteen and leaves every one of them to be hunted for.
    private void paintPenetrations(Graphics2D g2) {

        if (penetrations.isEmpty()) {
            return;
        }

        g2.setStroke(new BasicStroke(ViewerPainting.SPAN_STROKE));
        g2.setColor(ViewerPainting.applyAlpha(
            settings.piercedCellColour, ViewerPainting.OPAQUE_ALPHA));

        for (var penetration : penetrations) {
            for (var circle : penetration.circles()) {

                var centre = tracedUnion.sites().get(circle);
                var reach = tracedUnion.reach();

                g2.draw(new Ellipse2D.Double(
                    centre[0] - reach,
                    centre[1] - reach,
                    reach * DIAMETERS,
                    reach * DIAMETERS));
            }
        }

        g2.setStroke(new BasicStroke(ViewerPainting.SPAN_STROKE * PENETRATION_STROKES));
        g2.setColor(ViewerPainting.applyAlpha(
            settings.coastCrossingColour, ViewerPainting.OPAQUE_ALPHA));

        for (var penetration : penetrations) {

            g2.draw(new Line2D.Double(
                penetration.from()[0],
                penetration.from()[1],
                penetration.to()[0],
                penetration.to()[1]));
        }
    }

    private DiscUnionBoundary.Walls buildWalls(
            SectorFixture fixture,
            SectorGeometryParameters parameters) {

        var chords = new java.util.ArrayList<DiscUnionBoundary.Chord>();

        for (var bridge : VoidBridges.findVoidBridges(
                fixture.getSites(),
                parameters.cellRadius(),
                parameters.cellRadius() * settings.bridgeReachMultiple)) {

            chords.add(new DiscUnionBoundary.Chord(bridge.fromSite(), bridge.toSite()));
        }
        return new DiscUnionBoundary.Walls(chords, parameters.borderInset());
    }

    // The skip distance is set in cell radii rather than in world units, so it means the same
    // thing after the reach slider moves - "closer than a cell across" is a claim about the
    // map, where a number of units stops being one the moment the cells change size.
    private Coastlines.SmoothingRules buildSmoothingRules() {

        return new Coastlines.SmoothingRules(
            settings.coastSkipMultiple * settings.parameters.cellRadius(),
            settings.coastMaxSkips);
    }
}
