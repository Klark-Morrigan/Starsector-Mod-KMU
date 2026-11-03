package kmu.maplayers.base.ribbon;

import java.util.List;

/**
 * The banded stroke one cell draws inside its own ring, as the ordered segments it is made of:
 * each a colour and a length in band widths, starting from the cell's top centre.
 *
 * <p>Pure data, with no geometry and no rule for laying the segments, so a plan states what a band
 * says and nothing about who decided it. Whoever paints the map lays the segments by its own rule;
 * this is what every such rule hands the drawn band.
 *
 * <p>A value: two plans of the same segments are equal, and the segments are copied on the way in,
 * so a caller still holding the list it built from cannot lengthen a ribbon already planned. A rule
 * laying segments one at a time builds through {@link RibbonPlanBuilder} instead, whose list the
 * plan takes over rather than copies, since nothing else ever holds it.
 *
 * @param segments the ribbon's segments in draw order, starting from the cell's top centre; empty
 *                 when the cell draws no ribbon at all
 */
public record RibbonPlan(
    List<RibbonSegment> segments) {

    // The cell draws no band: the layer planned no segments for it, so there is nothing to report.
    public static final RibbonPlan NONE = new RibbonPlan(List.of());

    // The type is how a builder's list is recognised, and the only route there is: every way of making
    // a record runs this constructor, so a builder cannot hand its list over past the copy. Nor can it
    // hand over a list the copy would keep, since List.copyOf keeps only the JDK's own immutable lists
    // and every way of making one copies too.
    public RibbonPlan {

        if (!(segments instanceof AdoptedRibbonSegments)) {
            segments = List.copyOf(segments);
        }
    }

    /**
     * Whether the ribbon runs any length at all.
     *
     * <p>What decides whether a cell's ring is worth walking: a plan with no length has nothing to
     * lay on it, whether no segment was planned or every planned segment is empty.
     *
     * @return whether the segments add up to more than nothing
     */
    public boolean hasLength() {
        return sumLengthUnits() > 0;
    }

    /**
     * How far the whole ribbon runs, in ribbon widths.
     *
     * <p>The budget the drawn size is settled against: a cell whose perimeter cannot hold
     * this many widths shrinks what a width is worth until it can, rather than wrapping the
     * band or cutting it short.
     *
     * @return the total length of every segment, or zero where the cell draws no ribbon
     */
    public int sumLengthUnits() {

        var lengthUnits = 0;

        for (var segment : segments) {
            lengthUnits += segment.lengthUnits();
        }

        return lengthUnits;
    }
}
