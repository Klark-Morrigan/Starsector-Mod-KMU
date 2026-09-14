package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;

import java.util.Objects;

/**
 * What one paint of a domination box knows about the hovered system, beside the weights themselves:
 * the rule the pass weighed under, what the box may say about the colonies it weighed, and how deep
 * the player asked it to read.
 *
 * <p>All three are settled once, before any faction is accounted for, and hold for every colony line
 * in the box - so they travel as one value rather than down each call in turn. Threaded loose, a
 * later step could be handed a rule the lines above it were not drawn under, or a colony reading
 * taken from a second walk of the same system, and nothing at the call site would look wrong.
 *
 * @param rules         the weighting rule the pass resolved under, which decides whether a cause is
 *                      worth stating at all
 * @param colonyReading what the box may say about the system's colonies beyond their weights, folded
 *                      once off the pass's own walk - what sort of place each is, whether the player
 *                      has found it, and how old the news of it is
 * @param detailLevel   how deep the box was asked to read, which the arithmetic beneath a colony is
 *                      worked out only as far as
 */
public record WeightAccountReading(
    DominanceRules rules,
    SystemColonyReading colonyReading,
    HoverTooltipDetailLevel detailLevel) {

    public WeightAccountReading {

        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(colonyReading, "colonyReading");
        Objects.requireNonNull(detailLevel, "detailLevel");
    }
}
