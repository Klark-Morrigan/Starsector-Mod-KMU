package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * Where one system's ribbon plan comes from, so the rebuild asks for a band without naming the
 * mechanic that knows what is in the system.
 *
 * <p>A view paints its cells by one mechanic and a band has to count by that same mechanic, or
 * the band contradicts the fill it sits inside: a claims fill explained by held markets would
 * lead on a bloc the cell is not painted for, and a held fill explained by a claim contest would
 * count a different set of colonies than the score it was painted from. So the choice of mechanic
 * belongs to the view, exactly as its holder source and its hover box do, and this is the seam it
 * supplies one through.
 *
 * <p>The plan is asked for per system rather than per cell because every count behind it is a
 * fact about the system; the cell only decides how far round its own ring those runs are drawn.
 */
@FunctionalInterface
public interface SystemRibbonPlanner {

    /**
     * Plans the band for one system under whichever mechanic paints it.
     *
     * @param system the system to count; a system this planner's mechanic paints nothing in
     *               plans no band
     * @return the system's runs in draw order, or {@link RibbonPlan#NONE} where the cell draws
     *         no band at all
     */
    RibbonPlan planSystemRibbon(StarSystemAPI system);
}
