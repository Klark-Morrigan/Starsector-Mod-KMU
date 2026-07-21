package kmu.maplayers.politicalmap.base.politics;

import java.util.Map;
import java.util.Set;

/**
 * The ownership a single build resolves to drive the whole render pass: who paints each
 * star system, and which of those systems draw hatched rather than solid.
 *
 * <p>The one value the pipeline reads ownership through, so the builder consumes this and
 * never names the concrete source that produced it - the same way it consumes the view's
 * grouping without knowing how the view derived it. That decoupling is what lets one view
 * resolve dominance from the live economy while another resolves it some other way, with no
 * fork in the pass that shapes, borders, and labels the result.
 *
 * @param ownerBySystemId the bloc painting each owned system, keyed by system id; a system
 *                        with no owner is absent, so it draws as uninhabited ground
 * @param contestedSystemIds the owned systems drawn hatched rather than solid - the spotlit
 *                        bloc's present-but-dominated systems under a filter; empty when the
 *                        whole resolution fills solid
 */
public record OwnershipResolution(
        Map<String, DominantOwner> ownerBySystemId,
        Set<String> contestedSystemIds) {
}
