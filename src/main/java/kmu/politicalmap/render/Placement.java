package kmu.politicalmap.render;

import kmu.politicalmap.render.model.ClusterAnchor;

import java.util.Collection;
import java.util.List;

/**
 * One candidate line the label fit tries: an infinite line through
 * {@code (throughX, throughY)} along {@code direction}, together with the cluster
 * geometry it is measured against - the border rings it must stay inside and the icon
 * points it must clear.
 *
 * <p>Bundles the five values the fit threads together so the search and the
 * {@link LabelBoxFitter} pass one placement rather than the same five arguments down
 * every call, and owns the one mapping only a placement can do: turning a
 * {@code {tStart, tEnd}} parameter span along the direction back into world-coordinate
 * endpoints. The rings and icons are constant across a cluster while the through-point
 * and direction vary per candidate, so a placement is cheap to mint per candidate.
 *
 * @param rings     the cluster's border rings (outer ring plus any holes) the band
 *                  must stay inside
 * @param icons     the system points the band must keep its clearance from
 * @param throughX  x of a point the line passes through
 * @param throughY  y of a point the line passes through
 * @param direction the line's unit direction as {@code {x, y}}
 */
record Placement(List<List<double[]>> rings, Collection<double[]> icons, double throughX,
        double throughY, double[] direction) {

    /**
     * The world segment for a {@code {tStart, tEnd}} parameter span along this
     * placement's direction - the step that turns a fitted or near-miss span into the
     * line the anchor carries.
     *
     * @param span the parameter interval as {@code {tStart, tEnd}}
     * @return the span's endpoints in world coordinates
     */
    ClusterAnchor.AxisSegment toSegment(double[] span) {
        return new ClusterAnchor.AxisSegment(
                (float) (throughX + direction[0] * span[0]),
                (float) (throughY + direction[1] * span[0]),
                (float) (throughX + direction[0] * span[1]),
                (float) (throughY + direction[1] * span[1]));
    }
}
