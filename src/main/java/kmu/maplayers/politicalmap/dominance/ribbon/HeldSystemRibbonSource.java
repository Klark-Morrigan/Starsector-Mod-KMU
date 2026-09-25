package kmu.maplayers.politicalmap.dominance.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.ribbon.RibbonPlan;

import java.util.Optional;

/**
 * The held mechanic's answer for one system, including the answer "this system is not mine".
 *
 * <p>Narrower than {@link kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner}, and
 * deliberately so: a planner answers what a cell draws, where this also answers whether the
 * dominance sample has any standing to speak for the cell at all. The two are different questions
 * - a lone holder's cell draws nothing and is still held - and only the second can decide which
 * mechanic a composed planner should ask next.
 *
 * <p>A port rather than the concrete planner, so the composition that reads it is a rule over two
 * answers rather than a rule bound to an economy walk.
 */
@FunctionalInterface
public interface HeldSystemRibbonSource {

    /**
     * Plans a system's band, and says whether the held mechanic paints the system at all.
     *
     * @param system the system to count
     * @return the system's plan, or empty where no bloc holds a counted market there
     */
    Optional<RibbonPlan> planHeldSystemRibbon(StarSystemAPI system);
}
