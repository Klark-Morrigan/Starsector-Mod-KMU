package kmu.maplayers.base.visibility.colonies;

import java.util.HashMap;

/**
 * The alliance every case about a partner's silence is posed against, and the taking back that has
 * to follow it.
 *
 * <p>Shared because the registry is process state: a suite that seeds it and walks away answers for
 * every suite that runs after it, so the pairing of a seeding and a clearing is the thing worth
 * having in one place rather than restated wherever an alliance is posed.
 */
public final class FactionAllianceFixture {

    // The one alliance a case ever needs to name. Which alliance two factions are in says nothing
    // to any rule here - only whether it is the same one - so a case poses the membership and never
    // the id.
    private static final String ALLIANCE_ID = "alliance_the_one_under_test";

    private FactionAllianceFixture() {
        // fixture of static builders, no instances.
    }

    /**
     * Puts these factions in one alliance and leaves every other faction standing alone.
     *
     * @param factionIds the members' faction ids
     * @return who stands with whom, for a case handing the set straight to a projection
     */
    public static FactionAlliances buildAllianceOf(String... factionIds) {

        var allianceIdByFactionId = new HashMap<String, String>();

        for (var factionId : factionIds) {
            allianceIdByFactionId.put(factionId, ALLIANCE_ID);
        }
        return new FactionAlliances(allianceIdByFactionId);
    }

    /**
     * Registers that alliance as the live one, as a composition root does at start-up. For a case
     * about a read that opens its own set rather than being handed one.
     *
     * @param factionIds the members' faction ids
     */
    public static void registerAllianceOf(String... factionIds) {

        var alliances = buildAllianceOf(factionIds);

        FactionAllianceRegistry.registerAllianceSource(() -> alliances);
    }

    /**
     * Takes every registration back. Belongs in a suite's teardown wherever one was made: the
     * registry is seeded once per launch and read for the rest of it, so a set left behind silences
     * witnesses in suites that never posed an alliance.
     */
    public static void clearRegistrations() {
        FactionAllianceRegistry.registerAllianceSource(FactionAllianceSource.NO_ALLIANCES);
    }
}
