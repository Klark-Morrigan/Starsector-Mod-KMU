package kmu.maplayers.base.visibility.colonies;

/**
 * A supplier of the alliances in force right now.
 *
 * <p>A port rather than a registered snapshot, because alliances form and dissolve in play: a set
 * taken once at start-up would answer for the rest of the session, and the rule reading it would go
 * on crediting a partnership that ended cycles ago.
 *
 * <p>It is also what keeps the rule ignorant of which mods are installed. What maintains an alliance
 * is nothing the visibility rule can name, so the arrangement arrives as plain data from whatever
 * knows about it.
 */
@FunctionalInterface
public interface FactionAllianceSource {

    /**
     * No alliances at all. What an installation with nothing wired reads through, leaving the
     * revelation rule to answer on faction identity alone.
     */
    FactionAllianceSource NO_ALLIANCES = () -> FactionAlliances.NONE;

    /**
     * The alliances standing at this moment.
     *
     * @return which factions stand together; never null
     */
    FactionAlliances readAlliances();
}
