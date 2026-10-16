package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.alliances.FactionAlliances;

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
    FactionAllianceSource NO_ALLIANCES = sector -> FactionAlliances.NONE;

    /**
     * The alliances standing at this moment in one sector.
     *
     * @param sector the sector whose alliances are asked for; may be null where a caller has none
     * @return which factions stand together; never null
     */
    FactionAlliances readAlliances(SectorAPI sector);
}
