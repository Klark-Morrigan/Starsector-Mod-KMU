package kmu.maplayers.base.ribbon;

import java.util.List;

/**
 * The banded stroke one cell draws inside its own ring, as the ordered runs it is made of: each a
 * colour and a length in band widths, starting from the cell's top centre.
 *
 * <p>Pure data, with no geometry and no rule for laying the runs, so a plan states what a band
 * says and nothing about who decided it. Whoever paints the map lays the runs by its own rule; this
 * is what every such rule hands the drawn band.
 *
 * <p>A value: two plans of the same runs are equal. A class rather than a record so the runs can
 * be copied on the way in, where a record would hold whatever list a caller still has a hand on.
 */
public final class RibbonPlan {

    // The cell draws no band: the layer planned no runs for it, so there is nothing to report.
    public static final RibbonPlan NONE = new RibbonPlan(List.of());

    private final List<RibbonSegment> segments;

    /**
     * A plan of the given runs, copied, so a caller reusing the list it built from cannot lengthen
     * a ribbon already planned.
     *
     * @param segments the ribbon's runs in draw order, starting from the cell's top centre; empty
     *                 when the cell draws no ribbon at all
     */
    public RibbonPlan(List<RibbonSegment> segments) {
        this.segments = List.copyOf(segments);
    }

    /**
     * @return the ribbon's runs in draw order, starting from the cell's top centre; empty when the
     *         cell draws no ribbon at all
     */
    public List<RibbonSegment> segments() {
        return segments;
    }

    /**
     * How far the whole ribbon runs, in ribbon widths.
     *
     * <p>The budget the drawn size is settled against: a cell whose perimeter cannot hold
     * this many widths shrinks what a width is worth until it can, rather than wrapping the
     * band or cutting it short.
     *
     * @return the total length of every run, or zero where the cell draws no ribbon
     */
    public int sumLengthUnits() {

        var lengthUnits = 0;

        for (var segment : segments) {
            lengthUnits += segment.lengthUnits();
        }

        return lengthUnits;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RibbonPlan plan && segments.equals(plan.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    @Override
    public String toString() {
        return "RibbonPlan[segments=" + segments + "]";
    }
}
