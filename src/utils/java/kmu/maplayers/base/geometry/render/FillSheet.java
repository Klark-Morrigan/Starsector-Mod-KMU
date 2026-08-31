package kmu.maplayers.base.geometry.render;

import kmlib.math.geometry.PolygonRegions;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Several layers' shapes filled as one body.
 *
 * <p>For a construction whose layers hold the same thing in the same colour. Filled layer by
 * layer, two that overlap lay one translucent body over another and the shared part comes out
 * darker - so what two layers both hold reads as a third kind of thing, and the darker patch
 * moves whenever either is switched off. Filled once over the union, the overlap costs nothing
 * and the switches become what they claim to be.
 *
 * <p>The union is taken by the non-zero winding rule rather than by boolean geometry. These
 * rings routinely share stretches - a drawn line's fillets run on the very edge the line was
 * drawn against - and a boolean union of two nearly-coincident outlines is exactly where
 * geometry libraries produce slivers and self-intersections. The winding rule gets the same
 * answer by counting crossings, which two coincident edges cannot upset.
 *
 * <p>Which is why every ring's direction is decided here rather than trusted. Under that rule
 * two rings wound opposite ways CANCEL where they overlap, and the rings arrive from traces
 * that had no reason to agree on a direction - so a sheet that took them as they came would
 * punch a hole through the fill exactly where two layers meet, which is the fault it exists to
 * remove. A {@link #addMargin margin} then works by the same arithmetic deliberately: its
 * inner ring is wound AGAINST the sheet, which leaves the space inside it bare until another
 * layer covers it and brings the count back up.
 *
 * <p>The edges stay per ring. They say where each shape begins and ends, which is what a
 * reader judging a construction is looking at; only the bodies had to merge.
 */
public final class FillSheet {

    // Which way a ring is made to wind. A body and the ring taken back out of one have to
    // disagree, since that is what drops the winding count to zero inside a hole and lifts it
    // again where another body covers the same place. Which of the two directions is which is
    // arbitrary, and only their disagreement is load-bearing.
    private static final boolean WINDS_AS_BODY = true;
    private static final boolean WINDS_AS_HOLE = false;

    // Each ring's own path, kept as it is appended rather than rebuilt from the body when the
    // edges are drawn: a path flattened a second time has to come out identical to the first
    // or the edge misses the body it belongs to.
    private final List<Path2D> edges = new ArrayList<>();

    private final Path2D.Double body = new Path2D.Double(Path2D.WIND_NON_ZERO);

    /**
     * Adds every ring of one layer, each filled whole.
     *
     * @param rings the layer's closed outlines
     */
    public void addRings(List<List<double[]>> rings) {

        for (var ring : rings) {
            addRing(ring);
        }
    }

    /**
     * Adds one closed ring, filled whole.
     *
     * @param ring the outline
     */
    public void addRing(List<double[]> ring) {
        appendRing(ring, WINDS_AS_BODY);
    }

    /**
     * Adds the fill between two rings, one inside the other, leaving the space inside the
     * inner one out of the sheet.
     *
     * <p>Left OUT rather than painted as backdrop, and the difference is the whole reason a
     * margin belongs in a sheet at all: another layer filling that same space puts it back,
     * and the two then read as one body rather than as a band of darker shade round a lighter
     * middle. Nothing else covering it, the space stays bare, which is what a margin drawn on
     * its own has always meant.
     *
     * @param outer the ring the fill runs out to
     * @param inner the ring it stops at
     */
    public void addMargin(List<double[]> outer, List<double[]> inner) {

        appendRing(outer, WINDS_AS_BODY);
        appendRing(inner, WINDS_AS_HOLE);
    }

    /**
     * Fills everything added as one translucent body, then draws each ring's own edge over it.
     *
     * <p>An empty sheet draws nothing at all, rather than drawing nothing after setting a
     * colour and a stroke that the next drawing would then inherit.
     *
     * @param g2   what to draw with
     * @param look how to paint the body, whose edge colour outlines every ring
     */
    public void paint(Graphics2D g2, FillLook look) {

        if (edges.isEmpty()) {
            return;
        }

        g2.setStroke(new BasicStroke(MapLook.FILL_EDGE_STROKE));
        g2.setColor(MapPainting.applyAlpha(look.fill(), look.alpha()));
        g2.fill(body);
        g2.setColor(MapPainting.applyAlpha(look.edge(), MapLook.OPAQUE_ALPHA));

        for (var edge : edges) {
            g2.draw(edge);
        }
    }

    // One ring turned the way the sheet needs it, then appended to the body and kept for its
    // own edge. Reversing changes where the path starts and which way it runs, neither of
    // which a stroked outline can show.
    private void appendRing(List<double[]> ring, boolean shouldWindAsBody) {

        var windsPositive = PolygonRegions.computeSignedArea(ring) >= 0;
        var path = MapPainting.buildPath(
            windsPositive == shouldWindAsBody ? ring : reverseRing(ring));

        edges.add(path);
        body.append(path, false);
    }

    // The same ring traced the other way round, as a fresh list - the corners are shared,
    // since nothing here moves one.
    private static List<double[]> reverseRing(List<double[]> ring) {

        var reversed = new ArrayList<>(ring);

        Collections.reverse(reversed);

        return reversed;
    }
}
