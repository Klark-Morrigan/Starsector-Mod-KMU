package kmu.politicalmap.domain.visibility;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import kmu.starsector.rat.RandomAssortmentOfThingsMatcher;

/**
 * Decides whether a star system has a normal means of access. This is one input
 * to the political-map visibility rule ({@link PoliticalMapVisibility}), which
 * also admits inhabited-but-unreachable systems - so access alone no longer
 * decides what is on the map, only whether the system is normally reachable.
 *
 * <p>Access is defined by the means of arrival a system actually offers, not by
 * trusting the {@code SYSTEM_CUT_OFF_FROM_HYPER} tag - that tag is set by
 * specific procgen paths, so a hand-built hidden system can be unreachable
 * without ever carrying it. An <em>active</em> gate always grants access.
 * Otherwise the system must be wired into hyperspace by at least one jump point
 * and not be flagged cut off. A transverse-only system - reachable solely via a
 * nascent gravity well, with no jump point - confers no broad access and stays
 * off the map, even though the engine never tags it cut off; a present but
 * inactive gate does not rescue it. Gate activation flips
 * {@link GateEntityPlugin#isActive}, so accessibility tracks the real state.
 *
 * <p>Random Assortment of Things' Abyssal Fracture is a further means of
 * arrival: it ferries fleets in with a manual hyperspace transition rather than
 * a jump point, so a system entered only through a fracture carries no jump
 * point and would otherwise read as cut off. A fracture therefore grants access
 * the same way an active gate does, bypassing both the jump-point and cut-off
 * checks. RAT is optional, so the detection is delegated to
 * {@link RandomAssortmentOfThingsMatcher}, which is inert when RAT is absent.
 */
public final class SystemAccess {

    private SystemAccess() {
    }

    /**
     * @param system the system to test
     * @return true when the player has a normal means of reaching it
     */
    public static boolean hasMapAccess(StarSystemAPI system) {
        // A lit gate or an Abyssal Fracture reaches the system regardless of
        // jump connectivity, so either overrides the cut-off flag and the
        // absence of jump points.
        if (hasActiveGate(system) || hasAbyssalFracture(system)) {
            return true;
        }
        // No gate: the system must be reachable by ordinary hyperspace travel.
        // The cut-off flag rejects a system whose jump points are disabled; an
        // empty jump-point list rejects a transverse-only system whose sole
        // entry is a nascent gravity well (not a jump point).
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) {
            return false;
        }
        return !system.getJumpPoints().isEmpty();
    }

    // Whether any gate in the system is lit. An inactive gate (unscanned, or the
    // network not yet activated) grants nothing.
    private static boolean hasActiveGate(StarSystemAPI system) {
        for (var gate : system.getEntitiesWithTag(Tags.GATE)) {
            if (GateEntityPlugin.isActive(gate)) {
                return true;
            }
        }
        return false;
    }

    // Whether the system holds a RAT Abyssal Fracture. The matcher gates itself
    // on RAT being enabled, so this scans for nothing on a RAT-free install.
    private static boolean hasAbyssalFracture(StarSystemAPI system) {
        for (var entity : system.getAllEntities()) {
            if (RandomAssortmentOfThingsMatcher.isAbyssalFracture(entity)) {
                return true;
            }
        }
        return false;
    }
}
