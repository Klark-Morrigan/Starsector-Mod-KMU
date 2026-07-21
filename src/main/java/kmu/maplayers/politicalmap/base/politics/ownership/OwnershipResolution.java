package kmu.maplayers.politicalmap.base.politics.ownership;

import kmu.maplayers.politicalmap.base.politics.DominantOwner;

import java.util.Map;
import java.util.Set;

/**
 * The ownership a single build resolves to drive the whole render pass: who paints each
 * star system, and which of those owned systems draw as a fill exception - hatched, or with
 * no fill at all - rather than solid inside their bloc's one border.
 *
 * <p>The one value the pipeline reads ownership through, so the builder consumes this and
 * never names the concrete source that produced it - the same way it consumes the view's
 * grouping without knowing how the view derived it. That decoupling is what lets one view
 * resolve dominance from the live economy while another resolves it some other way, with no
 * fork in the pass that shapes, borders, and labels the result.
 *
 * <p>The two exception sets ride inside this one value rather than the builder resolving them
 * through a second named source, because they are co-produced with the owner map in the one
 * pass that resolves it: a system's owner and how its fill is drawn are decided together.
 *
 * @param ownerBySystemId the bloc painting each owned system, keyed by system id; a system
 *                        with no owner is absent, so it draws as uninhabited ground
 * @param contestedSystemIds the owned systems drawn hatched rather than solid - the spotlit
 *                        bloc's present-but-dominated systems under a filter; empty when the
 *                        whole resolution fills solid
 * @param unfilledSystemIds the owned systems drawn with no fill rather than solid - held by
 *                        their bloc for border and label, but painting nothing inside its one
 *                        frontier; empty when the whole resolution fills solid
 */
public record OwnershipResolution(
        Map<String, DominantOwner> ownerBySystemId,
        Set<String> contestedSystemIds,
        Set<String> unfilledSystemIds) {
}
