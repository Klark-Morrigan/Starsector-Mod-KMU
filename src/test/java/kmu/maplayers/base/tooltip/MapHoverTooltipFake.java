package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * A layer's hover box stood in for the real ones, stating one amount of detail and drawing nothing.
 * Which box a mode selects is decided entirely by what a tooltip offers, never by what one draws, so
 * a stand-in that draws nothing says everything a selection test needs - and a real box could not be
 * stood up here in any case, since drawing one needs a live GL context.
 *
 * <p>It leaves {@link MapHoverTooltip#resolveExpandedVariant} and
 * {@link MapHoverTooltip#isOfferingExpansionFor} unoverridden on purpose: inheriting the interface's
 * own answers is what makes this the tooltip that takes no part in the detail cycle, so a test using
 * it pins the defaults every implementation gets rather than a stand-in's imitation of them.
 */
class MapHoverTooltipFake implements MapHoverTooltip {

    @Override
    public void renderFor(SectorAPI sector, StarSystemAPI system) {
        // Drawing needs a GL context, and says nothing about which box the level picked.
    }
}
