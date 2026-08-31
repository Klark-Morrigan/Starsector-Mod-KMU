package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * A layer's hover box stood in for the real ones, drawing nothing. A real box could not be stood up
 * here in any case, since drawing one needs a live GL context - so what a case reads of this is that
 * it was reached and at which level, never what it painted.
 *
 * <p>It leaves {@link MapHoverTooltip#isOfferingExpansionFor} unoverridden on purpose: inheriting the
 * interface's own answer is what makes this the tooltip that takes no part in the detail cycle, so a
 * test using it pins the default every implementation gets rather than a stand-in's imitation of it.
 */
class MapHoverTooltipFake implements MapHoverTooltip {

    @Override
    public void renderFor(
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel) {

        // Drawing needs a GL context, and says nothing about which box the level picked.
    }
}
