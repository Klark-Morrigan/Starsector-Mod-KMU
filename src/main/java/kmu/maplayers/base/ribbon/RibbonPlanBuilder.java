package kmu.maplayers.base.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lays a plan one segment at a time, for a rule that decides each segment as it goes.
 *
 * <p>The plan takes this builder's list over rather than copying it. A rule laying every banded
 * cell's segments would otherwise hold each band twice for the moment it takes to hand it over:
 * once as it lays the segments, and once as the plan's own copy. What the copy protects - a plan nobody can
 * lengthen afterwards - this keeps by building once: after {@link #buildPlan()}, every append is
 * refused.
 */
public final class RibbonPlanBuilder {

    private final List<RibbonSegment> segments = new ArrayList<>();

    private boolean wasPlanBuilt;

    /**
     * Lays the next segment, after every segment laid so far.
     *
     * @param segment the segment, its colour and its length in band widths
     * @throws IllegalStateException where the plan has already been built, since the segment would
     *                               lengthen a ribbon already planned
     */
    public void appendSegment(RibbonSegment segment) {

        if (wasPlanBuilt) {
            throw new IllegalStateException("The plan was already built; it takes no further runs");
        }

        segments.add(Objects.requireNonNull(segment));
    }

    /**
     * Hands the segments laid so far over as the plan, ending this builder.
     *
     * @return the plan of every segment laid, in the order they were laid; one of no segments where
     *         none was, which equals {@link RibbonPlan#NONE}
     * @throws IllegalStateException where the plan has already been built, since two plans over
     *                               the one list would share it
     */
    public RibbonPlan buildPlan() {

        if (wasPlanBuilt) {
            throw new IllegalStateException("The plan was already built; a builder builds one plan");
        }

        wasPlanBuilt = true;

        return new RibbonPlan(new AdoptedRibbonSegments(segments));
    }
}
