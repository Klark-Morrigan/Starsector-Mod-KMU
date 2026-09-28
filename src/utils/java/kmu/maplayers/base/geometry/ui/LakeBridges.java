package kmu.maplayers.base.geometry.ui;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.v3.BridgedContinents;
import kmu.maplayers.base.geometry.v3.CoastFrontages;
import kmu.maplayers.base.geometry.v3.ContinentBridges;

import java.util.List;

/**
 * v3's bridges across the lakes, found under v4's own say on thinning.
 *
 * <p>The same search as v3's lake bridges, over the same trace, under the same knobs - bar one.
 * Whether chains and fans are thinned is a rule of whichever tier lays the bridges, so each
 * construction carries its own switch for it: turning it off to see what v3's formations look
 * unthinned must not change what v4 lays, and the other way about.
 *
 * <p>Where the two switches agree, the answer is v3's own laying, searched once for both. Only
 * where they disagree is the search run a second time, with nothing changed but that switch.
 */
public final class LakeBridges {

    private LakeBridges() {
    }

    /**
     * Every bridge across the lakes of a laying.
     *
     * @param continents           the laying, whose trace and knobs the bridges are found under
     * @param shouldThinFormations whether bridges sharing an anchor are thinned
     * @return the bridges, narrowest first as the search judged them
     */
    public static List<CellGap> collectLakeBridges(
            BridgedContinents continents,
            boolean shouldThinFormations) {

        var rules = continents.bridgeRules();

        if (rules.shouldThinFormations() == shouldThinFormations) {
            return continents.layLakeSpans();
        }

        return ContinentBridges.findAnchoredBridges(
            continents.traceCoasts(),
            CoastFrontages.Shore.INTERIOR,
            continents.parameters(),
            new ContinentBridges.BridgeRules(
                rules.reachMultiple(),
                rules.coastSlack(),
                shouldThinFormations,
                rules.anchorSeparation()));
    }
}
