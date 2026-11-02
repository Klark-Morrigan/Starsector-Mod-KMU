package kmu.maplayers.ownermap.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.Map;
import java.util.Set;

/**
 * Occupancies for the suites whose subject is a fact other than how a system fills: every owned
 * system fills solid, and the rest is stated.
 */
public final class SystemOccupancyFixtures {

    private SystemOccupancyFixtures() {
    }

    /**
     * An occupancy holding nothing: nobody owns anything, nothing stands anywhere, no pick lives
     * anywhere.
     *
     * @return the empty occupancy, ready to be folded into
     */
    public static SystemOccupancy createEmptyOccupancy() {
        return SystemOccupancy.createCopyOf(ResolvedOwners.createEmpty());
    }

    /**
     * An occupancy copied out of the stated owners, inhabited systems and spotlit presence, every
     * owned system filling solid.
     *
     * @param ownerBySystemKey          who owns each system; a system nobody owns is absent
     * @param inhabitedSystemKeys       every system something stands in
     * @param spotlitPresenceSystemKeys the settled systems the spotlit owner lives in that nobody
     *                                  owns
     * @return the occupancy, ready to be read and folded
     */
    public static SystemOccupancy createSolidlyFilledOccupancyOf(
            Map<SystemKey, SystemOwner> ownerBySystemKey,
            Set<SystemKey> inhabitedSystemKeys,
            Set<SystemKey> spotlitPresenceSystemKeys) {

        return SystemOccupancy.createCopyOf(new ResolvedOwners(
            ownerBySystemKey,
            SystemFillStates.ALL_SOLID,
            inhabitedSystemKeys,
            spotlitPresenceSystemKeys));
    }
}
