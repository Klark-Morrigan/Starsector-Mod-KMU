package kmu.maplayers.base.visibility;

/**
 * Where the live alliance set is read from - the one seam between the revelation rule and whatever
 * maintains the arrangement it reads.
 *
 * <p>Process state set once by a composition root, which is the only place in a position to know
 * what is installed. Everything below reads the port and never the installation, so the rule that
 * decides what a player may be told cannot come to depend on which mods are present.
 *
 * <p>{@link FactionAllianceSource#NO_ALLIANCES} until something registers otherwise, which is the
 * answer a registry with nothing wired should give: no arrangement anybody could keep quiet for.
 */
public final class FactionAllianceRegistry {

    // The registered supplier. Read afresh wherever a pass opens, since alliances form and dissolve
    // while a campaign runs.
    private static FactionAllianceSource allianceSource = FactionAllianceSource.NO_ALLIANCES;

    private FactionAllianceRegistry() {
        // registry of static state, no instances.
    }

    /**
     * Records where the alliances are to be read from. Called once by the composition root at
     * start-up, before any sector map can open.
     *
     * @param source the supplier to read through; null registers nothing, leaving no alliances at
     *               all
     */
    public static void registerAllianceSource(FactionAllianceSource source) {
        allianceSource = source == null ? FactionAllianceSource.NO_ALLIANCES : source;
    }

    /**
     * The alliances standing at this moment, as the registered supplier reports them.
     *
     * @return which factions stand together; never null, a supplier answering nothing reading as no
     *         alliances
     */
    public static FactionAlliances readAlliances() {

        var alliances = allianceSource.readAlliances();

        return alliances == null ? FactionAlliances.NONE : alliances;
    }
}
