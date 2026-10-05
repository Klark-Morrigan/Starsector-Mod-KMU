package kmu.maplayers.ownermap.owners;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.ribbon.RibbonPlan;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An owner source a tier case can hand over without naming a mechanic: it answers what the case
 * stated and records every walk it was asked over.
 *
 * <p>The tier resolves owners through {@link OwnerSource} and never names what fills it, so its
 * own suites cannot name one either. What a case usually pins is which walk a stage handed the
 * source, and what the stage then did with the answer - both of which this states outright rather
 * than deriving from a sector.
 *
 * <p>The whole-sector answer is one stated {@link ResolvedOwners}, empty unless a case says
 * otherwise. The per-system answers - who owns a system, whether anything stands in it, whether the
 * spotlit owner lives there - are stated per system ID, an unstated owner answering nobody and an
 * unstated inhabitation answering whatever {@link #answerEverySystemInhabited} last set. The band
 * planner plans nothing unless a case hands one over.
 */
public final class OwnerSourceFake implements OwnerSource {

    private final Map<String, SystemOwner> ownerBySystemId = new LinkedHashMap<>();
    private final Map<String, Boolean> inhabitationBySystemId = new LinkedHashMap<>();
    private final Set<String> spotlitPresenceSystemIds = new LinkedHashSet<>();

    private final List<SectorWalk> ownerWalks = new ArrayList<>();
    private final List<SectorWalk> systemResolveWalks = new ArrayList<>();
    private final List<SectorWalk> ribbonPlannerWalks = new ArrayList<>();
    private final List<RibbonPlanRules> ribbonPlanRules = new ArrayList<>();
    private final List<String> spotlitOwnerIdsAsked = new ArrayList<>();
    private final List<String> resolvedSystemIds = new ArrayList<>();

    private ResolvedOwners resolvedOwners = ResolvedOwners.createEmpty();
    private boolean isInhabitedUnlessStated;
    private SystemRibbonPlanner ribbonPlanner = system -> RibbonPlan.NONE;

    /**
     * A source answering a stated whole-sector resolution, for a case about what a build does with
     * the owners it is handed.
     *
     * @param owners what every whole-sector resolve answers with
     * @return the source
     */
    public static OwnerSourceFake createAnswering(ResolvedOwners owners) {

        var sourceFake = new OwnerSourceFake();
        sourceFake.resolvedOwners = owners;

        return sourceFake;
    }

    /**
     * States who owns one system, for every per-system resolve this source opens.
     *
     * @param systemId the system's own id
     * @param owner    the owner to answer with, or null for a system nobody owns
     */
    public void recordOwnerOf(String systemId, SystemOwner owner) {
        ownerBySystemId.put(systemId, owner);
    }

    /**
     * States whether anything stands in one system, ahead of the default.
     *
     * @param systemId    the system's own id
     * @param isInhabited the answer for that system
     */
    public void recordInhabitationOf(String systemId, boolean isInhabited) {
        inhabitationBySystemId.put(systemId, isInhabited);
    }

    /** Answers every system no case stated as inhabited, rather than empty. */
    public void answerEverySystemInhabited() {
        isInhabitedUnlessStated = true;
    }

    /**
     * States that the spotlit owner lives in one system.
     *
     * @param systemId the system's own id
     */
    public void recordSpotlitPresenceIn(String systemId) {
        spotlitPresenceSystemIds.add(systemId);
    }

    /**
     * Hands over the planner every bake counts through.
     *
     * @param planner the planner to answer with
     */
    public void answerRibbonPlanner(SystemRibbonPlanner planner) {
        ribbonPlanner = planner;
    }

    /**
     * @return every walk a whole-sector resolve was asked over, in the order asked
     */
    public List<SectorWalk> readOwnerWalks() {
        return List.copyOf(ownerWalks);
    }

    /**
     * @return every walk a per-system resolve was opened over, in the order opened
     */
    public List<SectorWalk> readSystemResolveWalks() {
        return List.copyOf(systemResolveWalks);
    }

    /**
     * @return every walk a band planner was asked over, in the order asked
     */
    public List<SectorWalk> readRibbonPlannerWalks() {
        return List.copyOf(ribbonPlannerWalks);
    }

    /**
     * @return the laying rules every band planner was asked under, in the order asked
     */
    public List<RibbonPlanRules> readRibbonPlanRules() {
        return List.copyOf(ribbonPlanRules);
    }

    /**
     * @return the spotlit owner every ask was made under, in the order asked; null where none was
     */
    public List<String> readSpotlitOwnerIdsAsked() {
        return new ArrayList<>(spotlitOwnerIdsAsked);
    }

    /**
     * @return the ID of every system a per-system resolve was asked the owner of, in the order asked
     */
    public List<String> readResolvedSystemIds() {
        return List.copyOf(resolvedSystemIds);
    }

    @Override
    public ResolvedOwners resolveOwners(SectorWalk walk, String spotlitOwnerId) {

        ownerWalks.add(walk);
        spotlitOwnerIdsAsked.add(spotlitOwnerId);

        return resolvedOwners;
    }

    @Override
    public SystemOwnerResolve openSystemResolve(SectorWalk walk, String spotlitOwnerId) {

        systemResolveWalks.add(walk);
        spotlitOwnerIdsAsked.add(spotlitOwnerId);

        return new StatedResolveFake();
    }

    @Override
    public SystemRibbonPlanner resolveRibbonPlanner(SectorWalk walk, RibbonPlanRules rules) {

        ribbonPlannerWalks.add(walk);
        ribbonPlanRules.add(rules);

        return ribbonPlanner;
    }

    // A system's own ID, or null for a system the sector no longer lists.
    private static String readIdOf(StarSystemAPI system) {
        return system == null ? null : SystemKey.readKeyOf(system).systemId();
    }

    /** The per-system answers this source states, read live so a case may state them late. */
    private final class StatedResolveFake implements SystemOwnerResolve {

        @Override
        public SystemOwner resolveOwnerOf(StarSystemAPI system) {

            var systemId = readIdOf(system);
            resolvedSystemIds.add(systemId);

            return ownerBySystemId.get(systemId);
        }

        @Override
        public boolean isSystemInhabited(StarSystemAPI system) {
            return inhabitationBySystemId.getOrDefault(readIdOf(system), isInhabitedUnlessStated);
        }

        @Override
        public boolean isSpotlitOwnerPresentIn(StarSystemAPI system) {
            return spotlitPresenceSystemIds.contains(readIdOf(system));
        }
    }
}
