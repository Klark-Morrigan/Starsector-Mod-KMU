package kmu.maplayers.base.geometry.v3;

import java.util.ArrayList;
import java.util.List;

/**
 * One coast as selection and placement left it: the border, and nothing else.
 *
 * <p><b>No rounded line here.</b> Rounding is what the map does to this before drawing it,
 * and a rounded ring carried alongside is a second line for a reader to pick up by mistake -
 * which is how a decision comes to be taken against presentation. Everything a trace hands
 * back is geometry, so the question does not arise: there is one line, and it is this one.
 * What the map draws is {@link CoastRounding}'s, built from a whole trace at once.
 *
 * @param vertices the line as points that each name the cell they sit on, which is what
 *                 anything reasoning about WHERE a coast runs needs
 */
public record Coast(
    List<CoastVertex> vertices) {

    /**
     * The ring on its own, for anything that only wants to draw or fill it.
     *
     * @return its points, in order
     */
    public List<double[]> collectPoints() {

        var points = new ArrayList<double[]>(vertices.size());

        for (var vertex : vertices) {
            points.add(vertex.point());
        }

        return points;
    }
}
