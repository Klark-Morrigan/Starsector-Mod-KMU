package kmu.maplayers.base.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * Where one system's band plan comes from, so a bake asks for a band without naming the rule that
 * decides its runs.
 *
 * <p>A layer paints its cells by a rule of its own, and a band has to be planned by that same rule
 * or it contradicts the fill it sits inside: a band planned by any other could open on a run the
 * cell is not painted for. So which planner a bake uses is the layer's to supply, and this is the
 * seam it supplies one through.
 *
 * <p>The plan is asked for per system rather than per cell because everything behind it is a fact
 * about the system; the cell only decides how far round its own ring those runs are drawn.
 */
@FunctionalInterface
public interface SystemRibbonPlanner {

    /**
     * Plans the band for one system.
     *
     * @param system the system to plan for; one the layer's rule finds nothing in plans no band
     * @return the system's runs in draw order, or {@link RibbonPlan#NONE} where the cell draws
     *         no band at all
     */
    RibbonPlan planSystemRibbon(StarSystemAPI system);
}
